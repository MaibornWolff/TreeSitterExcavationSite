---
name: Fix JS/TS declaration extraction gaps (dc-compare React/Prisma findings)
issue:
state: complete
version:
---

## Goal

Fix JS/TS dependency extraction gaps surfaced by dc-compare against React (JS) and Prisma (TS).
TS gaps 1/2/3 (interface extends, namespace, type alias RHS, generic constraints) are already fixed in
`fix-typescript-dependency-extraction-gaps.md`. This plan covers the remaining issues:
- Issue 1: export-only filtering (✅ done)
- Issue 2: named import value-position usages investigation (✅ done — DC-side)
- Issue 3: lowercase/camelCase named import usages not tracked (✅ done)

## Context

- **Issue 1**: TSE extracts ALL top-level declarations regardless of export status. DC's old analyzer
  only extracted exported symbols. This produces +4,448 extra nodes for React (JS) and +91 for Prisma (TS).
- **Issue 2**: Named import bindings used in value position may be missing from usedTypes in JS.
  DC reports ~3,204 PascalCase and ~540 SCREAMING_SNAKE missing deps in React. DC notes the fix
  may be on their side ("If TSE already emits these but DC's pipeline drops them..."). Requires
  investigation before any fix.

## Root cause for Issue 1

`DeclarationExtractor.extract()` has two branches that pick up non-exported declarations:

```kotlin
in DECLARATION_NODE_TYPES -> extractFromNode(child, ...)      // ← extracts bare functions/classes
EXPRESSION_STATEMENT -> { /* unwraps bare namespace Foo {} */ } // ← extracts unexported namespaces
```

Remove both branches. Exported declarations still flow through:
- `EXPORT_STATEMENT -> extractFromExportStatement(...)` — handles all `export X` forms
- `AMBIENT_DECLARATION -> extractFromAmbientDeclaration(...)` — handles `declare module "X" { }` blocks

Exported namespaces (`export namespace Foo {}`) still work because `extractFromExportStatement`'s `else`
branch filters for `it.type in DECLARATION_NODE_TYPES`, and `INTERNAL_MODULE` is in that set.
Only bare `namespace Foo {}` (wrapped in `expression_statement`) disappears — which is the right outcome.

**`extractFromAmbientDeclaration` retains its `in DECLARATION_NODE_TYPES` branch by design.** Inside
`declare module "X" { ... }`, TypeScript treats all declarations as implicitly ambient; there is no
export keyword to require. This is an intentional exception to the export-only rule.

## Tasks

### 1. Export-only filtering (Issue 1)

Update `DeclarationExtractor.extract()`:
- Remove the `in DECLARATION_NODE_TYPES -> extractFromNode(...)` branch
- Remove the `EXPRESSION_STATEMENT -> { ... }` branch (bare namespace unwrapper)

#### Affected tests — update fixtures to add `export` keyword

Most tests below only need `export` prepended to the fixture. The usedType assertions are unchanged.

**`TypescriptDependencyTest.DeclarationExtraction`** — all use bare declarations:
- `should extract class declaration` — `class Foo {}` → `export class Foo {}`
- `should extract interface declaration` — `interface IFoo {}` → `export interface IFoo {}`
- `should extract enum declaration` — `enum Color { ... }` → `export enum Color { ... }`
- `should extract function declaration` — `function greet(...)` → `export function greet(...)`
- `should extract type alias declaration as CLASS` — `type Id = string` → `export type Id = string`
- `should extract const variable declaration` — `const greeting: string = ...` → `export const greeting: ...`
- `should extract var variable declaration` — `var counter = 0` → `export var counter = 0`
- `should extract multiple declarations` — all three bare → add `export` to each
- `should extract nested class declarations` — `class Outer { ... }` → `export class Outer { ... }`
- `should extract function signature as FUNCTION type` (EdgeCases) — `function greet(name: string): void;` → `export function greet(...)`

**`TypescriptDependencyTest.UsedTypeExtraction`** — all use bare `class Foo` / `interface Foo` as scaffolding:
- `should extract type from type annotation` — `class Foo { field: MyService }` → `export class Foo ...`
- `should extract type from constructor call` — `class Foo { bar() { ... } }` → `export class Foo ...`
- `should extract uppercase object from member access` — `export class Foo ...`
- `should not extract lowercase object from member access` — `export class Foo ...`
- `should extract extends clause type from interface declaration` — `interface Foo extends Bar {}` → `export interface Foo ...`
- `should extract extends clause type` — `class Foo extends Bar {}` → `export class Foo ...`
- `should extract implements clause types` — `export class Foo ...`
- `should extract uppercase identifier` — `export class Foo ...`
- `should not extract lowercase identifiers` — `export class Foo ...`
- `should extract generic type argument` — `export class Foo ...`
- `should resolve import alias to original type name in usedTypes` — `export class Foo ...`
- `should extract type constraint from generic type parameter` — `class Foo<T extends Bar> {}` → `export class Foo<T extends Bar> {}`
- `should extract type reference from type alias right-hand side` — `type Foo = ...` → `export type Foo = ...`
- `should extract multiple types from union type alias` — `export type Foo = Bar | Baz`
- `should resolve default import name to DEFAULT_EXPORT in usedTypes` — `export class Foo extends Dep {}`

**`TypescriptDependencyTest.EdgeCases`:**
- `should extract multiple variable declarators from one const statement` — `const a: TypeA = 1, b: TypeB = 2` → `export const a: TypeA = 1, b: TypeB = 2`
- `should extract used types from function declaration parameters and return type` — `function process(...)` → `export function process(...)`

#### Affected tests — require logic change (not just `export` prepend)

**`TypescriptDependencyTest.DeclarationExtraction.should keep original variable and add DEFAULT_EXPORT REEXPORT when local var is default-exported`**
```kotlin
val code = "const events = { EventEmitter }\nexport default events"
```
After filtering, `const events` (non-exported) is no longer a declaration. The `DEFAULT_EXPORT` REEXPORT
is produced by `classifyDefaultExport` returning `DefaultExport.Reexport("events")`, not by
`extractJsDeclarations` (that path is TS, not JS anyway). Result: only `DEFAULT_EXPORT` with
`usedTypes = {events}`.
→ Update assertion: `declarations.hasSize(1)`, name `DEFAULT_EXPORT`, type `REEXPORT`,
  usedTypes contain `events`.

**`TypescriptDependencyTest.DeclarationExtraction.should extract abstract class without export as CLASS declaration`**
```kotlin
val code = "abstract class Base {}\nexport default Base"
```
After filtering, `Base` is not extracted as a standalone declaration. Only `DEFAULT_EXPORT` with
`usedTypes = {Base}` remains.
→ Update: remove assertion that `byName.containsKey("Base")`; instead assert only `DEFAULT_EXPORT` present.

**`TypescriptDependencyTest.NamespaceDeclaration.should extract namespace declaration without export keyword`**
```kotlin
val code = "namespace Foo {}"
```
→ Update: assert `result.declarations.isEmpty()`.

#### Affected tests — JavaScript

**`JavascriptDependencyTest.DeclarationExtraction.should keep original declaration and add DEFAULT_EXPORT REEXPORT when local var is default-exported`**
```kotlin
val code = "const buildFunction = () => { return \"hello\"; };\nexport default buildFunction;"
```
After filtering, `buildFunction` (non-exported) is not a declaration. `DEFAULT_EXPORT` is produced by
`classifyDefaultExport → DefaultExport.Reexport("buildFunction")` in `DeclarationExtractor.extract()`.
Note: `extractJsDeclarations` in `JavascriptDependencyMapping` would do `firstOrNull { it.name == "buildFunction" }`,
find nothing, and return `declarations` unchanged — `DEFAULT_EXPORT` was already appended by `extract()`.
→ Update: assert `declarations.hasSize(1)`, name `DEFAULT_EXPORT`, type `REEXPORT`, usedTypes contain `buildFunction`.

#### New tests to add

In `JavascriptDependencyTest.DeclarationExtraction`:
- `should not extract non-exported function declaration`
- `should not extract non-exported class declaration`

### 2. Named import value usages investigation (Issue 2)

DC reports ~3,744 missing usedTypes for patterns like:
- `import { ContextMenu } from './menu'` + `<ContextMenu />` in JSX (component usage)
- `import { Transform } from './transform'` + `new Transform()` (constructor call)
- `import { TYPES } from './constants'` + `TYPES.SOMETHING` as member access
- `import { processData } from './utils'` + `processData(x)` used as a plain function argument

DC's note: "If TSE already emits these as usedTypes but DC's pipeline drops them, the fix is in DC."

`UsedTypeExtractor` already captures: uppercase `IDENTIFIER` nodes (covers PascalCase and SCREAMING_SNAKE
first-char), `JSX_OPENING_ELEMENT`/`JSX_SELF_CLOSING_ELEMENT` (covers component JSX), `NEW_EXPRESSION`
(covers constructors), `MEMBER_EXPRESSION` (covers member access). The existing `JsxSmokeTest` already
proves JSX works. TSE may already emit these correctly.

Write targeted tests in `JavascriptDependencyTest` (`@Nested class NamedImportValueUsages`) for all
four patterns. Do NOT fix anything yet — write the tests and run them.

- If tests **pass**: usedTypes are present in TSE → the issue is in DC's pipeline. Document the
  finding in the plan Notes and close Phase 2.
- If tests **fail**: TSE is genuinely not emitting these. Diagnose and implement fix in
  `UsedTypeExtractor` or `JavascriptDependencyMapping`.

## Issue 3: Named import usages not tracked (affects both TS and JS)

DC's old analyzers tracked every reference to an imported name anywhere in a declaration body —
including lowercase function calls, camelCase method calls, and PascalCase/SCREAMING_SNAKE usages.
TSE currently applies a capitalisation heuristic (`name.firstOrNull()?.isUpperCase()`) so all
lowercase and camelCase usages silently disappear from `usedTypes`.

**Scale:**
- TS (Prisma): ~1,422 missing deps — e.g. `debug`, `createHash`, `formatError`
- JS (React): ~3,483 missing camelCase deps — e.g. `releaseCache`, `retainCache`, `commitClassCallbacks`

**Pattern in old DC analyzers:**
1. Parse import statements → collect the set of locally bound names (e.g. `{releaseCache, retainCache}`)
2. Scan the full declaration body for any `identifier` whose text matches a name in that set
3. Emit each match as a `usedType` → DC resolver links it back to the source node

**Scope:** import-scoped only — not all identifiers, only those that appear as named bindings in the
file's import declarations (`import { A, B }` or `const { A, B } = require(...)`).

**Architectural question:** The fix requires correlating two pieces of data that are currently
computed independently:
- The set of imported names (from `ImportExtractor` or re-derived from the root AST)
- The identifier nodes inside each declaration body (currently only visible to `UsedTypeExtractor`)

`DeclarationExtractor.buildAliasMap()` already walks import statements and collects a subset of this
data (alias mappings). Extending it — or running a parallel pass — to collect ALL named bindings is
the most likely fix path. Whether to thread the resulting `Set<String>` through to `UsedTypeExtractor`
as a new parameter, or to compute it inside `DeclarationExtractor.extract()` and handle the emission
there, needs investigation.

### Phase 3a: Investigation

**Step 0 — Verify the gap exists in DC legacy before assuming TSE must change.**

Read DC's `analyzers/typescript/TypescriptAnalyzer.kt` and `analyzers/javascript/JavascriptAnalyzer.kt`
(or their query files under `analyzers/<lang>/queries/`) and answer:

- Does the legacy TS analyzer emit usedType entries for lowercase/camelCase identifiers that match
  an imported name (e.g. `debug`, `createHash`)? Or does it apply a capitalisation filter of its own?
- Does the legacy JS analyzer emit usedType entries for camelCase identifiers that match an imported
  name (e.g. `releaseCache`, `commitClassCallbacks`)?
- If yes: confirm the pattern with a concrete example from the analyzer source (quote the relevant code).
- If no: the dc-compare diff for these names has a different cause (DC pipeline, resolver, or
  something else). Do NOT proceed to Phase 3b.

This step mirrors the due-diligence done for every other language migration (dependency-migration.md:
"Read DC's legacy analyzer... understand how it extracts usedTypes"). Issue 2 showed that dc-compare
can report "missing" deps that TSE already emits correctly — applying the same scepticism here.

Before implementing, also answer the following questions by reading the code and/or adding a throwaway
exploration test:

1. **What named-binding names does `buildAliasMap()` currently collect?** It collects `alias → original`
   pairs for aliased named imports (`import { A as B }` → maps B→A) and default bindings (`import Foo`
   → maps Foo→DEFAULT_EXPORT). It does NOT collect unaliased named imports (`import { foo }` → `foo`
   is NOT in the alias map). Confirm this.

2. **Where is the cleanest place to compute the imported-names set?**
   - Option A: Extend `buildAliasMap()` to additionally return `Set<String>` of all named bindings
     (including unaliased), then pass both to `UsedTypeExtractor.extract(importedNames)`.
   - Option B: Add a separate `buildImportedNames(rootNode, sourceCode): Set<String>` function
     in `DeclarationExtractor` and pass to `UsedTypeExtractor`.
   - Option C: Compute the set inside `JavascriptDependencyMapping` from `ImportExtractor.extract()`
     results (path.last() for non-wildcard imports) and pass down to `DeclarationExtractor.extract()`.
   - Option D: Move the whole import-scan into `UsedTypeExtractor.extract()` by accepting `rootNode`.

3. **Does `UsedTypeExtractor.extract()` need a new parameter, or can the imported-names set be
   passed via the existing `aliasMap`?** The alias map currently maps local name → canonical name;
   a different mechanism is needed for plain unaliased names (their canonical name equals their local name).

4. **Are there identifier nodes inside declaration bodies that should be excluded even if they match
   an imported name?** E.g. variable declarations inside function bodies (`const foo = ...` where
   `foo` happens to be an imported name) — should those count? DC's legacy probably did count them
   (whole-body scan), so TSE should too.

### Phase 3b: Implementation (to be detailed after Phase 3a)

Placeholder — update this section after the investigation confirms the approach:

- [ ] Add a failing test in `TypescriptDependencyTest.NamedImportValueUsages` (new nested class) for
  a lowercase named import used in a function call (TS pattern)
- [ ] Add a failing test in `JavascriptDependencyTest.NamedImportValueUsages` for a camelCase named
  import used in a function call (JS pattern) — existing `NamedImportValueUsages` class covers uppercase
  only; add a lowercase case that currently fails
- [ ] Implement the fix in `UsedTypeExtractor` (or `DeclarationExtractor`) per the approach confirmed
  in Phase 3a
- [ ] Run `./gradlew test` green
- [ ] Run `./gradlew ktlintCheck` passes

## Steps

- [x] Phase 1a: Write failing tests for new `should not extract non-exported...` cases in `JavascriptDependencyTest`
- [x] Phase 1b: Remove non-exported branches from `DeclarationExtractor.extract()`
- [x] Phase 1c: Update all affected TS tests (add `export` to fixtures) — also updated `TsxDependencyTest.JsxUsedTypeExtraction` (bare `class Foo` fixtures, missed from plan)
- [x] Phase 1d: Update TS/JS logic-change tests (default-export, abstract-class, bare-namespace)
- [x] Phase 1e: `./gradlew test` green
- [x] Phase 1f: `./gradlew ktlintCheck` passes
- [x] Phase 2a: Write targeted tests for all four Issue 2 patterns in `JavascriptDependencyTest`
- [x] Phase 2b: Run tests — all 4 PASSED → TSE already emits these usedTypes correctly
- [x] Phase 2c: DC-side issue confirmed — no TSE code changes needed
- [x] Phase 2d: N/A (no code changes in Phase 2c)
- [x] Phase 3a-0: Both analyzers already migrated to TSE; `TypescriptAnalyzer.extraUsedTypes()` compensates at adapter level — gap confirmed real in TSE
- [x] Phase 3a: Answered all four investigation questions (see Findings)
- [x] Phase 3b: Added failing tests, implemented fix, green suite, ktlintCheck passes

## Findings

**Issue 2 outcome**: TSE already emits named-import value usages correctly. Targeted tests confirmed:
- `<ContextMenu />` JSX → `ContextMenu` in usedTypes ✓
- `new Transform()` constructor → `Transform` in usedTypes ✓
- `TYPES.something` member access → `TYPES` in usedTypes ✓
- `processData(x)` lowercase call → correctly excluded from usedTypes ✓

Root cause of DC's ~3,744 missing deps is in DC's own pipeline (adapter mapping or resolver). The fix belongs in DC, not TSE.

Note: JS class names use `identifier` nodes (not `type_identifier` like TS), so the class name itself (e.g. "App") is captured as a usedType by `extractRelevantIdentifiers`. Tests reflect this accurately.

**Issue 3 outcome**: Fixed via two minimal changes:
- `buildAliasMap()` extended to self-map unaliased named imports (`import { foo }` → `foo → foo`), making aliasMap a complete registry of all locally bound import names.
- `extractRelevantIdentifiers()` extended to accept `aliasMap` and emit identifiers in aliasMap regardless of capitalisation. Existing aliasMap rename step handles aliased imports (`import { Foo as bar }` → `bar` renamed to `Foo`) for free.
- DC's `TypescriptAnalyzer.extraUsedTypes()` — which was added as a workaround for this gap — becomes redundant but can stay harmlessly.

## Notes

- No changes to `TypescriptDependencyMapping` or `JavascriptDependencyMapping` needed for Phase 1 —
  the fix is entirely in `DeclarationExtractor.extract()`.
- `extractFromAmbientDeclaration` intentionally retains its `in DECLARATION_NODE_TYPES` branch;
  inside `declare module "X" { }` all declarations are implicitly ambient regardless of `export` keyword.
- Golden files (metrics/extraction) are unaffected — no dependency golden files exist for JS/TS.
- DC workaround context (for DC team, once these fixes land):
  - `selectUsedTypes()` override (filters DEFAULT_EXPORT) — unaffected by this plan
  - `expandWildcardReexport()` / `resolveSourceFile()` — unaffected; TSE still returns `Declaration(name="*")`
  - Default-import module-name proxy — unaffected; aliasMap still maps default bindings → DEFAULT_EXPORT

## dc-compare Results (version 0.10.0-local, Prisma/React)

Final numbers after all fixes in this plan:

| Metric         | TS (Prisma) | JS (React) |
|----------------|-------------|------------|
| Missing deps   | 432         | 6,843      |
| Extra deps     | 662         | 3,728      |
| Only in main   | 7           | 97         |
| Only in feat.  | 218         | 209        |

### Accepted differences

**Intra-file non-exported symbol dependencies (432 TS / 6,843 JS missing):**
TSE intentionally excludes non-exported top-level symbols from the dependency graph. DC main included
them as a side-effect of having no export filter — e.g. `const debug = createDebugger(...)` (not
exported) was a graph node in DC main, enabling `StdClient → debug` edges. TSE's export-only
filtering removes these nodes and their edges. This is an accepted improvement, not a regression:
- Intra-file references to non-exported helpers are implementation details, not architectural edges
- The meaningful dep (`StdClient → "debug"` npm package) is still captured via the import
- Adding these back would require an `includeNonExported` mode and would re-introduce the same
  non-exported noise that Issue 1 (export-only filtering) was designed to remove

**Remaining "extra" deps (662 TS / 3,728 JS):** Improvements over DC main — better extraction of
types TSE finds that DC's legacy analyzers missed. Accepted as improvements.

**Note on `localDeclarationNames`:** The `extractLocalDeclarationNames` pass (which collects exported
symbol names to allow lowercase cross-references between declarations in the same file) is NOT
related to `includeNonExported`. It only collects names from `export` statements, so it never adds
non-exported locals to the usedType set. The `export default X` case (where `X` is a locally-defined
non-exported const) was separately fixed: `X` is not added to `exportReferencedLocalNames`, so it
does not appear as a node — only the `DEFAULT_EXPORT` REEXPORT entry is emitted.