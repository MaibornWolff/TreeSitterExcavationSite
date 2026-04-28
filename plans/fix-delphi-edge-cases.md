---
name: Fix Delphi dependency-extraction edge cases
issue:
state: complete
version:
---

## Goal

Address the seven Delphi dependency-extraction issues catalogued in `delphi-edge-cases.md` (discovered via Spring4D analysis): one bug, two fragile/known-limitation fixes, one documentation bug, one architectural alignment with Kotlin/C#, and two verification gaps. Behaviour and tests live in `languages/delphi/extractors/` and `DelphiDependencyTest.kt`; no shared-domain or facade changes.

## What we're NOT doing

- No `parentPath` boundary exclusion in `UsedTypeExtractor` — TSE-wide pattern (Java/Kotlin/C#) is recursive traversal with leakage; Delphi mirrors that.
- No `defProc → class` binding changes — the existing `GENERIC_DOT` LHS branch in `qualifiedNameClassPrefix` already returns the innermost class name for `TOuter.TInner.Method`.
- No grammar / `tree-sitter-pascal` upgrade. We work around 0.10.2 quirks where present.
- No DC `/dc-compare` round (no DC Delphi analyzer exists).
- No new golden file — the existing `delphi_sample.pas` will be extended in place; the corresponding `*_dependencies.golden` is regenerated.

## Current state

- Live extractors: `languages/delphi/extractors/{ImportExtractor,DeclarationExtractor,UsedTypeExtractor,PackageExtractor}.kt`.
- `DeclarationExtractor.isNestedInsideDeclaration()` filters every nested `declType`; `parentPath` is always `emptyList()`.
- No forward-declaration filter; deduplication relies on top-down emission order in `ReportService.associateBy`.
- No `declDispIntf` case in `resolveDeclarationType()`.
- Misleading KDoc on `qualifiedNameClassPrefix` (DeclarationExtractor.kt:83-88) describes behaviour the function does not actually have.
- `DelphiDependencyTest.kt` has no coverage for: `uses … in 'path'`, `{$IFDEF}`-gated uses, forward decls, dispinterface, nested types, record-implements-interface.

## Desired end state

- `uses X in 'path'` form is either captured cleanly OR documented + locked with a test.
- Forward declarations (`TFoo = class;`) are filtered before reaching `UsedTypeExtractor`.
- `dispinterface` declarations emit `DeclarationType.INTERFACE`.
- Nested type declarations are emitted with `parentPath = [outer-class-chain]`, mirroring Kotlin/C#.
- Records implementing interfaces are covered by a regression test.
- `{$IFDEF}`-gated uses entries are covered by a regression test (whatever the parser does).
- Misleading operator-overload KDoc is corrected.
- `./gradlew test ktlintCheck detekt` green; `delphi_sample_dependencies.golden` regenerated and reviewed.

## Architecture and code reuse

All work stays inside `languages/delphi/`. No new files except possibly `extractors/NestedTypePathResolver.kt` if `findParentPath` doesn't fit cleanly inside `DeclarationExtractor.kt` (decide during phase 4).

Affected files:

- `src/main/kotlin/.../languages/delphi/extractors/ImportExtractor.kt` — phase 1 (raw-text fallback, only if AST-dump shows it's needed).
- `src/main/kotlin/.../languages/delphi/extractors/DeclarationExtractor.kt` — phases 2, 3, 4, 6.
- `src/test/kotlin/.../languages/delphi/DelphiDependencyTest.kt` — every phase adds `@Nested` cases.
- `src/test/resources/contract/delphi_sample.pas` — phase 4 extends it with a nested-type fixture; golden is regenerated.
- `src/test/resources/contract/delphi_sample_dependencies.golden` — regenerated after phase 4.
- `delphi-edge-cases.md` — moved into `plans/` or deleted as each item is closed (decide at the end).

## Performance considerations

None expected. Each phase touches at most one extra AST walk per declaration (parent chain in phase 4); declaration counts per file are small.

## Migration notes

- Phase 4 changes `parentPath` from always-empty to populated for nested types — visible to DC consumers. Aligns with the Kotlin/C# contract; CodeCharta's UnifiedParser already handles non-empty `parentPath`. No grace period needed.

---

## Phase 1: ImportExtractor edge cases — `uses … in 'path'` (#1) and `{$IFDEF}` in uses (#7)

Both issues hinge on what `tree-sitter-pascal` 0.10.2 actually produces. Investigate first; only add code if the parser drops imports.

**Tasks**:
- [x] Add an ImportExtraction test for `uses Foo in 'Source\Foo.pas', Bar in 'Source\Bar.pas';`. Run it; observe whether the extractor returns `[Foo, Bar]`, partial, or empty. → Captured cleanly: parser emits ERROR for `in '<path>'` but keeps the moduleName children; both modules extracted.
- [x] Add an ImportExtraction test for an IFDEF-gated uses block (the example from `delphi-edge-cases.md` §7). Run it; observe parser behaviour (single declUses with all idents / multiple declUses / error nodes). → Captured cleanly: a single `declUses` containing all moduleName children plus `pp` directive nodes.
- [x] Decide per case based on observed behaviour:
   - **Captured cleanly** → keep the test as a regression lock; close issue.
- [x] Update `delphi-edge-cases.md` (issues #1, #7) with the verified outcome.

**Automated verification**:
- [x] `./gradlew test --tests "DelphiDependencyTest.ImportExtraction"` green.
- [ ] `./gradlew ktlintCheck detekt` pass.

---

## Phase 2: Filter forward declarations (#2)

Skip `declClass` nodes whose body is empty (`TFoo = class;`) so the full definition is the one that lands in the result map.

**Tasks**:
- [x] Add a DeclarationExtraction test: a unit declaring `TAggregatedInterfaceProxy = class;` (forward) followed by the full definition. Assert exactly one declaration, with the full definition's `usedTypes`.
- [x] In `DeclarationExtractor`, add `isForwardDeclaration(declTypeNode)`: a `declClass` whose only meaningful named children are the kind keyword (`kClass` / `kRecord` / …) and a semicolon, no `declSection` / `declField` / `declProc` / parent clause. Skip via `mapNotNull` in `extract()`. → Implemented as "no `kEnd` child" — simpler and matches the AST shape directly.
- [x] Verify the same filter applies to nested forward decls (covered organically once phase 4 lands; add a follow-up assertion there).

**Automated verification**:
- [x] `./gradlew test --tests "DelphiDependencyTest.DeclarationExtraction"` green.

---

## Phase 3: `dispinterface` support (#4)

**Tasks**:
- [x] Dump the AST for a `dispinterface` snippet (one-off `println` test or `tree-sitter parse`) and confirm the node-type name (expected: `declDispIntf`). → Actual: `declIntf` with `kDispInterface` keyword. No separate `declDispIntf` node exists in 0.10.2.
- [x] Add a DeclarationExtraction test for the example in `delphi-edge-cases.md` §4. Expect `DeclarationType.INTERFACE`.
- [x] ~~Add `DECL_DISPINTF` constant and the `DECL_DISPINTF -> DeclarationType.INTERFACE` branch to `resolveDeclarationType()`.~~ Not needed — existing `DECL_INTF -> INTERFACE` already covers `dispinterface`. Skipped to avoid dead code.

**Automated verification**:
- [x] `./gradlew test --tests "DelphiDependencyTest"` green.

---

## Phase 4: Nested type extraction with `parentPath` (#5)

Mirror Kotlin's `findParentPath` and C#'s `parentPath = namespacePath + parentClassPath` shape — Delphi's namespace path is empty (Class-1 single-namespace), so `parentPath` is just the parent-class chain.

**Tasks**:
- [x] Add a `NestedDeclarations` `@Nested` test group in `DelphiDependencyTest`:
   - `should extract nested class with parentPath of enclosing class`
   - `should extract nested record / interface / enum with parentPath`
   - `should produce parentPath in outer-to-inner order for two-level nesting`
   - `should still bind defProc bodies to nested classes via TOuter.TInner.Method` (verify existing `GENERIC_DOT` branch in `qualifiedNameClassPrefix`)
   - `should keep existing top-level declarations with empty parentPath` (regression)
- [x] Remove `isNestedInsideDeclaration()` from `DeclarationExtractor` and the call site in `extract()`.
- [x] Add `findParentPath(declTypeNode, namesByStartByte)` that walks `parent` collecting names of ancestor `declType` nodes (via the existing `extractDelphiDeclTypeName` helper). Pre-build `namesByStartByte` once for the whole file, like Kotlin does.
- [x] Pass `parentPath` to the `Declaration` constructor.
- [x] Extend `delphi_sample.pas` with one nested-type fixture (e.g., `TOuter` containing `private type TInner = class … end;`).
- [x] Regenerate `delphi_sample_dependencies.golden` (flip `UPDATE_GOLDEN_FILES`, run, flip back, review diff). Also extended the dependencies golden serializer to include `parentPath` so the nested-type structure is visible in the file.

**Automated verification**:
- [x] `./gradlew test --tests "DelphiDependencyTest"` green.
- [x] `./gradlew test --tests "GoldenFileContractTest"` green with `UPDATE_GOLDEN_FILES = false`.

**Manual verification**:
- [x] Inspect the regenerated golden: nested declarations appear with non-empty `parentPath`; outer declarations still appear with `parentPath = []`.

---

## Phase 5: Records implementing interfaces — verification only (#6)

**Tasks**:
- [x] Add a UsedTypeExtraction test: `TMyRecord = record(IInterface) … end;` — assert `IInterface` is in `usedTypes`.
- [x] If the assertion fails, file as a follow-up (currently expected to pass per `extractInheritance` walking `DECL_CLASS` buckets, which receive `record` shapes too). → Passed first run; no follow-up needed.

**Automated verification**:
- [x] `./gradlew test --tests "DelphiDependencyTest.UsedTypeExtraction"` green.

---

## Phase 6: Correct misleading operator-overload comment (#3)

**Tasks**:
- [x] Replace the KDoc on `qualifiedNameClassPrefix` (DeclarationExtractor.kt:83-88) and the matching note in `plans/add-delphi-dependency-support.md` "Accepted v1 limitations" with the actual behaviour: the function reads the LHS only, which is an `identifier` for `class operator TAny.Implicit(…)`, so operator implementations ARE attributed to their declaring class. The earlier "operator-overload methods silently dropped" claim is wrong.
- [x] Add a `defProc`-binding test: a class whose only method body is `class operator TAny.Implicit(...)` — assert the operator's used types appear in `TAny`'s `usedTypes` set.

**Automated verification**:
- [x] `./gradlew test --tests "DelphiDependencyTest"` green.
- [ ] `./gradlew ktlintCheck` passes.

---

## Phase 7: Bookkeeping

**Tasks**:
- [x] Update `delphi-edge-cases.md` so each issue is annotated with its resolution (test reference / commit / decision).
- [x] Decide whether to delete `delphi-edge-cases.md` (replace with the closed-out test names) or keep it as a historical record under `plans/`. → Moved to `plans/delphi-edge-cases.md` as historical record.
- [x] Set this plan's `state:` to `complete` once the full suite is green on `./gradlew clean build`.

**Automated verification**:
- [x] `./gradlew clean build` green.
- [x] `grep -R "operator implementation" src/main/kotlin/de/maibornwolff/treesitter/excavationsite/languages/delphi` returns no stale wording. → Only hit is the new, correct sentence stating operators ARE bound like any other method.

---

## Notes

- **Verify before coding (phases 1, 3).** Both phases hinge on `tree-sitter-pascal` 0.10.2's actual output. Run a one-off `println` test or `tree-sitter parse` against the relevant snippet first.
- **Forward-decl filter must reject empty bodies, not arbitrary children.** A class with only `private` and `end;` would be a real (if useless) declaration, not a forward decl. Key: `class;` ends with semicolon directly after the kind keyword, no `end` token.
- **Nested types: no boundary exclusion.** Outer class's `usedTypes` will continue to include inner class's types. This matches Kotlin/C# in TSE today; DC's resolver dedups across declarations.
- **`namesByStartByte` reuse (phase 4).** Build it once per `extract()` call from the *unfiltered* `findAllDescendantsOfType(rootNode, declType)` list (before forward-decl skip), so every ancestor — including any forward decl that we'd skip emitting — still resolves to a name when walking the parent chain.
- **No `LanguageRegistry` / `Definition` changes.** All edits stay inside `languages/delphi/extractors/` and the test directory.
