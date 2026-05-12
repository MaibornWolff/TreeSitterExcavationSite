---
name: Fix JS/TS declaration extraction gaps (dc-compare React/Prisma findings)
issue:
state: complete
version:
---

## Goal

Fix two JS/TS dependency extraction gaps surfaced by dc-compare against React (JS) and Prisma (TS).
TS gaps 1/2/3 (interface extends, namespace, type alias RHS, generic constraints) are already fixed in
`fix-typescript-dependency-extraction-gaps.md`. This plan covers the remaining issues.

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

## Findings

**Issue 2 outcome**: TSE already emits named-import value usages correctly. Targeted tests confirmed:
- `<ContextMenu />` JSX → `ContextMenu` in usedTypes ✓
- `new Transform()` constructor → `Transform` in usedTypes ✓
- `TYPES.something` member access → `TYPES` in usedTypes ✓
- `processData(x)` lowercase call → correctly excluded from usedTypes ✓

Root cause of DC's ~3,744 missing deps is in DC's own pipeline (adapter mapping or resolver). The fix belongs in DC, not TSE.

Note: JS class names use `identifier` nodes (not `type_identifier` like TS), so the class name itself (e.g. "App") is captured as a usedType by `extractRelevantIdentifiers`. Tests reflect this accurately.

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