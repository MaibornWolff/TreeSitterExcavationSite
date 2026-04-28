---
name: Add Delphi DPK Package File Support
issue: ~
state: skipped
version: ~
---

## Decision (2026-04-28): skipped

Implementation deferred. AST probe against `Spring.Base.dpk` confirmed the
worst-case scenario: tree-sitter-pascal 0.10.2 does not recognise `.dpk` files
at all. The whole file collapses into a single top-level ERROR with raw
`identifier` tokens for `package` / `requires` / `contains` (no `kPackage`
keyword, no `declRequires` / `declContains` nodes). `kIn` is preserved but the
path literal is silently dropped.

A token-level recovery (state machine over leaf tokens in document order) does
work for the real Spring4D file, but synthetic minimal tests are fragile —
module names that collide with Pascal keywords (`Unit`, `Program`, `Library`,
`End`) trigger phantom container wrappers. The cost (~150 lines of bespoke
recovery code, regression tied to a single real file) wasn't worth the benefit
right now. Revisit if tree-sitter-pascal upstream adds `.dpk` grammar support
or if BPL-level architecture views become a priority.

## Goal

Support `.dpk` (Delphi Package) files in TSE's Delphi extractors so that DC can analyze inter-package dependencies. Today TSE handles `unit` / `program` / `library` containers but not `package`. This means projects like Spring4D, mORMot, and any other multi-package Delphi codebase produce no edges between their compiled BPLs (run-time and design-time packages) — an entire architectural layer is invisible.

A `.dpk` file's structure is:

```pascal
package Spring.Base;

{$DESCRIPTION 'Spring4D Base package'}
{$RUNONLY}

requires
  rtl;

contains
  Spring in '..\..\Source\Base\Spring.pas',
  Spring.Collections in '..\..\Source\Base\Collections\Spring.Collections.pas',
  …
end.
```

There are no `interface` / `implementation` sections and no type declarations. The two new clauses are:

- **`requires`** — package-level dependencies (e.g. on `rtl`, `vcl`, other `.dpk` packages).
- **`contains`** — units that this package compiles in. Same `Unit in 'path'` shape as `.dpr` programs, which TSE already extracts via `ImportExtractor`.

Both are real dependencies in DC's graph: a package's node has edges to the packages it requires and to the units it contains.

## Tasks

### 1. Verify the tree-sitter-pascal AST for `.dpk` files

Write a probing test in `DelphiDependencyTest.kt` that parses a small `.dpk` snippet and dumps the relevant node types — specifically: what's the top-level container type (`package` vs reusing `unit`)?, what's the `requires` clause node type (`declRequires`, `declUses`, or something else)?, what's the `contains` clause node type (`declContains`, `declUses`, or reused)?

Use `Spring.Base.dpk` from `spring4d/Packages/Delphi13/Spring.Base.dpk` as the realistic input. Once the AST shape is known, the rest of the plan can be implemented precisely. Three plausible outcomes:

- **Best case**: tree-sitter-pascal reuses `unit` for the container and `declUses` for both `requires` and `contains`. No code change needed in `PackageExtractor` — just add `dpk` extension awareness in DC. `ImportExtractor` already works.
- **Likely case**: a distinct `package` container node and distinct `declRequires` / `declContains` clause nodes. Requires extending both extractors (Tasks 2 and 3).
- **Worst case**: parser flips into ERROR-wrap mode (as it does for some `.pas` files). The fallback path added by `recover-delphi-package-path-on-parse-error.md` should still recover the package name; the `requires` / `contains` clauses may need their own keyword-fallback.

### 2. Extend `PackageExtractor` for the `package` container

`languages/delphi/extractors/PackageExtractor.kt`.

- Add `"package"` to the `CONTAINERS` set (line 41) if Task 1 confirms a distinct node type.
- Add `"kPackage"` to the `KEYWORD_FALLBACK` set (line 42) for parse-error robustness.
- Update the KDoc (lines 7–33) to mention `.dpk` files alongside `unit` / `program` / `library`.

If Task 1's findings show tree-sitter-pascal reuses the existing `unit` node type for `.dpk`, this task collapses to a docstring update only.

### 3. Extend `ImportExtractor` for `requires` and `contains` clauses

`languages/delphi/extractors/ImportExtractor.kt`.

Currently the extractor only finds `declUses` nodes. Extend it to also find `declRequires` and `declContains` nodes (or whatever Task 1 identifies as their actual node-type names):

- Iterate `findAllDescendantsOfType(rootNode, DECL_USES, DECL_REQUIRES, DECL_CONTAINS)` instead of just `DECL_USES`.
- For each, extract `moduleName` children the same way and emit `ImportDeclaration` entries.
- Both `requires` and `contains` should produce non-wildcard imports — they're concrete dependencies on specific packages or units, not glob patterns.
- The existing `.distinct()` at the end keeps duplicates from collapsing wrongly.

If `contains` entries use the `Unit in 'path'` form (they do in `Spring.Base.dpk`), no extra handling is needed — `ImportExtractor` already discards the `in 'path'` part because it only reads the `moduleName` children, and tree-sitter-pascal's error recovery keeps `moduleName` intact even when the path string confuses the parser.

Update the KDoc to mention `.dpk` files and the two new clause types.

### 4. Add tests in `DelphiDependencyTest.kt`

Add a new `@Nested inner class DpkPackageSupport` (positioned next to existing `PackageExtraction` / `ImportExtraction` groups) with the following tests:

- **`should extract package name from dpk file`** — minimal snippet with `package Foo.Bar; requires rtl; contains MyUnit; end.` Asserts `packagePath == ["Foo", "Bar"]`.
- **`should extract requires clause as imports`** — asserts entries from `requires` show up in `result.imports`.
- **`should extract contains clause as imports`** — asserts entries from `contains` show up in `result.imports`. Cover both bare-name (`MyUnit`) and `in 'path'` (`MyUnit in 'src/MyUnit.pas'`) forms.
- **`should produce zero declarations for dpk file`** — `.dpk` files have no type declarations, so `result.declarations` should be empty.
- **`should extract from real Spring Base dpk file`** — end-to-end regression using `spring4d/Packages/Delphi13/Spring.Base.dpk`. Asserts `packagePath == ["Spring", "Base"]`, `imports` contains `rtl` and at least 5 of the contained units, declarations is empty.

### 5. Update documentation

- `CHANGELOG.md`: add a new entry under `[Unreleased]` → `Added`: "Support for Delphi `.dpk` package files: extracts the package name from `package Foo.Bar;`, the `requires` clause, and the `contains` clause as dependency-graph imports."
- `KNOWN_ISSUES.md`: if it currently mentions `.dpk` not being supported, update; otherwise no change.
- `dependencies/README.md` (if it lists supported Delphi file shapes): add `.dpk` alongside `.pas` and `.dpr`.

## Steps

- [x] Write AST-probing test against `Spring.Base.dpk` and identify node-type names for `package` / `requires` / `contains` (Task 1)
  - **Finding**: tree-sitter-pascal 0.10.2 does NOT recognize `.dpk` files. Whole file is wrapped in a single top-level ERROR. `package` / `requires` / `contains` are emitted as raw `identifier` tokens (no `kPackage` keyword, no `declRequires` / `declContains` nodes). `kIn` keyword is preserved but the `'path'` literal after it is silently dropped. Module names appear as flat `identifier`/`.` token sequences inside the ERROR. This is the worst-case scenario — token-level recovery is required.
- [-] Add `.dpk` to `Language.DELPHI.otherExtensions` (added so TSE recognizes the file shape)
- [ ] Add a shared `DpkRecovery` helper that token-walks the AST and extracts package name / requires / contains (Task 2 + 3 combined)
- [ ] Wire `DpkRecovery` into `PackageExtractor` (Task 2)
- [ ] Wire `DpkRecovery` into `ImportExtractor` (Task 3)
- [ ] Update KDocs of both extractors to mention `.dpk` files
- [ ] Add tests in new `@Nested inner class DpkPackageSupport` (Task 4)
- [ ] Run `./gradlew test --tests "*DelphiDependencyTest*"` — all green
- [ ] Run `./gradlew test` — full TSE suite green
- [ ] Run `./gradlew ktlintCheck` — passes
- [ ] Update `CHANGELOG.md` `[Unreleased]` → `Added` (Task 5)
- [ ] Update `dependencies/README.md` and `KNOWN_ISSUES.md` if relevant (Task 5)
- [ ] Remove temporary `DpkAstProbeTest.kt`

## Notes

- This is a TSE-only change. The DC-side follow-up is a one-liner: add `"dpk"` to `SupportedLanguage.DELPHI.suffixes` in `analysis/src/main/kotlin/.../pipeline/shared/SupportedLanguage.kt`. Out of scope for this plan but noted for the integration step.
- `.dpk` files produce zero declarations — they're pure metadata. In DC's tree they will appear as **empty namespace nodes** with edges out (to required packages and contained units) and edges in (from no one — packages aren't imported by Pascal source). This is intentional and useful: the package node aggregates the architectural intent of a BPL.
- Separating `requires` from `contains` semantically (e.g. tagging the `ImportDeclaration` with a "kind") is **out of scope**. Both are dependencies; DC's resolver handles them uniformly. Add this only if a real use case appears.
- `.dpk` files do **not** typically have `uses` clauses (no `interface` / `implementation` sections to host them). If tree-sitter-pascal produces stray `declUses` nodes inside an ERROR-wrapped `.dpk` parse, the existing `findAllDescendantsOfType` will harmlessly include them — no extra guard needed.
- Spring4D ships parallel `.dpk` files for multiple Delphi versions (`Delphi13/`, `Delphi14/`, etc.), all with identical contents. Analyzing all of them produces duplicate package nodes — this is a DC-side filtering concern, not a TSE concern.
- Once this lands and DC is updated, re-running `mise run analyze` over `spring4d/` (with `Packages/Delphi13/` included) should produce package-level dependency edges in addition to the unit-level edges — a noticeably richer architectural view.
