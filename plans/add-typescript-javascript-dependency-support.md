---
name: add-typescript-javascript-dependency-support
issue:
state: progress
version: 1
---

## Goal

Add TypeScript and JavaScript dependency support to TSE, following the migration pattern established by Java and Kotlin. TypeScript gets full support (imports + declarations + used types); JavaScript gets imports only (matching DC's legacy behavior).

**Done when:**
- All new and existing TSE tests pass
- ktlint and architecture tests pass
- dc-compare against a real TypeScript project matches DC main
- dc-compare against a real JavaScript project matches DC main

**DC follow-up (separate PR, out of scope):** After TSE is merged and tagged, DC's `TypescriptAnalyzer` and `JavascriptAnalyzer` need to be rewritten to call `TreeSitterDependencies.analyze()` — same pattern as Java/Kotlin.

**DC prerequisite for TSX:** DC's TypescriptAnalyzer must dispatch `.tsx` files to `Language.TSX` (not `Language.TYPESCRIPT`) — the TypeScript parser does not emit JSX nodes. This is a DC-side change coordinated alongside TSE task 15.

## Design Decisions

### Package path — empty list
TypeScript and JavaScript have no package declaration. Both `PackageExtractor`s return `emptyList()`.

### DeclarationType — extend with FUNCTION and VARIABLE
Add `FUNCTION` and `VARIABLE` to `DeclarationType` in `shared/domain/DependencyResult.kt`. Backward-compatible (no existing `when` branches enforce exhaustive coverage). Enables DC to map them correctly during migration.

### Import path representation — split by `/`
TypeScript/JavaScript imports use file paths (`'./utils/helper'`, `'react'`, `'@scope/package'`). Split each import path by `/` into segments: `[".", "utils", "helper"]`, `["react"]`, `["@scope", "package"]`.

### CommonJS support
`require('./module')` calls are treated as imports. Detect via `call_expression` with `require` identifier as callee, extract string argument as path.

### Shared ImportExtractor
TypeScript is a superset of JavaScript — both share identical import grammar. A single `ImportExtractor` in `languages/javascript/extractors/` serves both `TypescriptDependencyMapping` and `JavascriptDependencyMapping`.

### JavaScript — full extraction (correction)
Initial assumption "JavaScript: imports only" was wrong. DC's `JavascriptAnalyzer` produces nodes for exported declarations (classes, functions, constants). `JavascriptDependencyMapping` must use `DeclarationExtractor::extract` — identical to `TypescriptDependencyMapping`. `DeclarationExtractor` and `UsedTypeExtractor` already work for JavaScript since JS is a subset of the TypeScript grammar.

### No boundary exclusion (TypeScript)
Start without boundary exclusion — match DC's re-parsing type leakage behavior. Add only if dc-compare reveals issues.

### Used type concatenation order (TypeScript)
From `integration/dependencies/README.md`: `typeIdentifiers, constructorCalls, memberAccesses, methodCalls, extensions, relevantIdentifiers`

### TSX dependency mapping — reuse TypeScript extractors
TSX is a superset of TypeScript. `TsxDependencyMapping` reuses `PackageExtractor`, `ImportExtractor`, and `DeclarationExtractor` unchanged. `UsedTypeExtractor` is extended to also handle `jsx_opening_element` and `jsx_self_closing_element` — these nodes are simply absent when called via the TypeScript parser, so the extension is backward-compatible and safe to share.

## Tasks

Tasks follow TDD: write failing test → implement → verify green → next test.

### 0. Explore TypeScript and JavaScript AST

Before writing code, dump AST for sample code covering all relevant constructs. Verify node type assumptions.

Key unknowns to verify:
- `import_statement` structure: `import_clause`, `named_imports`, `namespace_import`, `import_specifier`
- `require()` call: which node type wraps the string argument
- TypeScript declaration node types: `class_declaration`, `interface_declaration`, `enum_declaration`, `function_declaration`, `function_signature`, `type_alias_declaration`, `lexical_declaration`
- Type annotation nodes: `type_annotation`, `type_identifier`, `generic_type`, `predefined_type`
- Extends/implements: `extends_clause`, `implements_clause`, `class_heritage`
- Whether JavaScript has a separate `Language` enum value or shares TypeScript's definition

### 1. Extend DeclarationType (TDD)

- Write test asserting `DeclarationType.FUNCTION` and `DeclarationType.VARIABLE` are accessible (add to `TypescriptDependencyTest`)
- Add `FUNCTION` and `VARIABLE` to `DeclarationType` in `src/main/kotlin/.../shared/domain/DependencyResult.kt`
- Check any existing `when` expressions for exhaustiveness issues

### 2. Shared ImportExtractor (TDD)

- Write `ImportExtraction` tests first: ES6 named, ES6 default, ES6 wildcard (`import * as x`), CommonJS `require()`, multiple imports, no imports
- Implement `ImportExtractor` in `languages/javascript/extractors/`:
  - ES6: find `import_statement` nodes, extract source string, detect wildcard via `namespace_import` child
  - CommonJS: find `call_expression` nodes where callee identifier text is `"require"`, extract string argument
  - Path: strip quotes, split by `/`
- Use `mapNotNull` for defensive extraction (skip malformed nodes)

### 3. TypeScript DeclarationExtractor + UsedTypeExtractor (TDD)

**DeclarationExtractor:**
- Recursively find: `class_declaration`, `interface_declaration`, `enum_declaration`, `function_declaration`, `function_signature`, `type_alias_declaration`, `lexical_declaration`
- Map to `DeclarationType`: CLASS (class + type_alias), INTERFACE, ENUM, FUNCTION (function_declaration + function_signature), VARIABLE (lexical_declaration)
- Extract name via `getChildByFieldName("name")`
- Delegate to `UsedTypeExtractor` per declaration
- TDD: start with single class, add other declaration types one at a time

**UsedTypeExtractor** (must follow DC concatenation order):
1. `typeIdentifiers` — `type_identifier` nodes in type annotations
2. `constructorCalls` — `new_expression` → constructor type identifier
3. `memberAccesses` — `member_expression` identifiers (uppercase-first filter)
4. `methodCalls` — called method identifiers (uppercase-first filter)
5. `extensions` — `extends_clause` and `implements_clause` type identifiers
6. `relevantIdentifiers` — all `identifier` nodes (uppercase-first filter)

TDD: start with simple type annotations, incrementally add generics, inheritance, constructor calls, etc.

### 4. TypescriptDependencyMapping + register in TypescriptDefinition

- Create `TypescriptDependencyMapping.kt` in `languages/javascript/`
- Compose `PackageExtractor`, `ImportExtractor`, `DeclarationExtractor`
- Override `dependencyMapping` in `TypescriptDefinition.kt`

### 5. JavascriptDependencyMapping + register in JavascriptDefinition

- Create `JavascriptDependencyMapping.kt` in `languages/javascript/` — uses shared `ImportExtractor`, `extractDeclarations` returns `emptyList()`
- Find `JavascriptDefinition.kt` (verify location) and override `dependencyMapping`

### 6. Named re-exports as imports (TDD)

DC treats `export { Foo } from './module'` as a dependency source. TSE's `ImportExtractor` currently only scans `import_statement` nodes; `export_statement` nodes with a source string are missed.

- Write test: `export { Foo } from './utils'` → one import with path `[".", "utils"]`, `isWildcard = false`
- Write test: `export { Foo, Bar } from './utils'` → one import (same source, deduplication not needed)
- Implement: in `ImportExtractor`, add `extractNamedReexports` — find all `export_statement` nodes that contain a `string` child but no `*`
- Applies to both TypeScript and JavaScript (shared extractor)

### 7. Wildcard re-exports as imports (TDD)

DC treats `export * from './module'` as a wildcard dependency. TSE misses these entirely.

- Write test: `export * from './utils'` → one import with path `[".", "utils"]`, `isWildcard = true`
- Write test: `export * as ns from './utils'` → one import, `isWildcard = true`
- Implement: in `ImportExtractor`, add `extractWildcardReexports` — find `export_statement` nodes that contain `*` and a `string` child
- Applies to both TypeScript and JavaScript (shared extractor)

### 8. Dynamic imports (TDD)

DC handles `import('./module')` as an import. TSE's CommonJS handler only matches `require` identifier calls; dynamic `import()` uses a different node type (`import` keyword, not `identifier`).

- Write test: `import('./utils')` → one import with path `[".", "utils"]`, `isWildcard = false`
- Write test: dynamic import inside async function → still extracted
- Implement: in `ImportExtractor`, add `extractDynamicImports` — find `call_expression` nodes where the function child type is `import` (not `identifier`)
- Applies to both TypeScript and JavaScript (shared extractor)

### 9. Set up early dc-compare loop

Create DC branch `feat/tse-typescript-javascript-integration` pointing to TSE feature branch via JitPack. Run after basic extractors work — don't wait until feature-complete.

Test repos:
- **TypeScript primary**: medium-sized open-source TypeScript project (e.g., `microsoft/TypeScript-Node-Starter` or `prisma/prisma`)
- **JavaScript primary**: medium-sized JS project with ES6 + CommonJS mix

When to run:
- After ImportExtractor + DeclarationExtractor basics work
- After UsedTypeExtractor is complete
- After each fix

### 10. Remaining tests + final iteration

- Edge cases: empty file, imports-only, declarations without type annotations, deeply nested declarations
- Completeness check: realistic file exercising all features
- Iterate on dc-compare differences until match

### 11. Final verification

- `./gradlew test` — all tests pass
- `./gradlew ktlintCheck` — clean
- Architecture tests pass
- Final dc-compare confirms match for both TypeScript and JavaScript

### 12. Fix: CommonJS pair_pattern destructuring (TDD)

`const { myMethod: alias } = require('myModule')` currently extracts nothing because `ImportExtractor` only handles `shorthand_property_identifier_pattern` inside `OBJECT_PATTERN`. In a `pair_pattern`, the first child is the key (original name) and the second is the alias.

- Write failing tests in `JavascriptDependencyTest` and `TypescriptDependencyTest`:
  - `const { myMethod: alias } = require('myModule')` → `ImportDeclaration(path=["myModule", "myMethod"], isWildcard=false)`
- In `ImportExtractor.extractCommonJsImports`: also filter `pair_pattern` children of `OBJECT_PATTERN`; extract first child text as import name

### 12b. Fix: JavaScript declaration extraction (TDD)

`JavascriptDependencyMapping` incorrectly used `{ _, _ -> emptyList() }` for declarations, based on the wrong assumption that DC's JavascriptAnalyzer is imports-only. DC actually extracts exported declarations (classes, functions, constants), so 0/22 DC JavascriptAnalyzer tests pass after migration.

- Write failing test in `JavascriptDependencyTest.DeclarationExtraction`:
  - `export class Foo {}` → `Declaration(name="Foo", type=CLASS)`
  - `export function bar() {}` → `Declaration(name="bar", type=FUNCTION)`
  - `export const baz = 42` → `Declaration(name="baz", type=VARIABLE)`
- Remove existing `should return empty declarations` test (was asserting wrong behavior)
- Update `JavascriptDependencyMapping`: replace `{ _, _ -> emptyList() }` with `DeclarationExtractor::extract`
- Add missing `DeclarationExtractor` import

### 13. Fix: REEXPORT declarations (TDD)

`export { A } from './foo'` should produce `Declaration` objects in addition to imports. Currently `DeclarationExtractor` ignores `export_statement` nodes with a source string.

- Add `REEXPORT` to `DeclarationType` in `shared/domain/DependencyResult.kt`
- Write failing tests in `TypescriptDependencyTest.DeclarationExtraction`:
  - `export { MyReexportedClass } from './MyInternalClass'` → `Declaration(name="MyReexportedClass", type=REEXPORT, usedTypes=[UsedType("MyReexportedClass")])`
  - `export { MyReexportedClass as MRC } from './MyInternalClass'` → `Declaration(name="MRC", type=REEXPORT, usedTypes=[UsedType("MyReexportedClass")])`
  - `export { default as validationMixin } from './mixins/validation.mixin'` → `Declaration(name="validationMixin", type=REEXPORT, usedTypes=[UsedType("DEFAULT_EXPORT")])`
  - multiple specifiers → one `Declaration` per specifier
- Extend `DeclarationExtractor.extract()`: add branch for `export_statement` nodes with both an `export_clause` child and a source `string` child; iterate `export_specifier` children:
  - single identifier → name = identifier, usedType = same
  - two identifiers (`A as B`) → name = second, usedType = first
  - `default` keyword → usedType = `"DEFAULT_EXPORT"`

### 14. Fix: declare module declarations (TDD)

`declare module "MyModule" { export class MyClass {} }` should produce `Declaration`s with `parentPath=["MyModule"]`. Currently ignored.

- **AST dump first**: parse a `declare module "MyModule" { ... }` snippet with the TypeScript parser and log the node tree to confirm node type (likely `ambient_module_declaration`) and child structure before writing any code
- Write failing tests in `TypescriptDependencyTest.DeclarationExtraction`:
  - `declare module "MyModule" { export function myFunction(): void; }` → `Declaration(name="myFunction", type=FUNCTION, parentPath=["MyModule"])`
  - `declare module "MyModule" { export class MyClass {} }` → `Declaration(name="MyClass", type=CLASS, parentPath=["MyModule"])`
  - `declare module "*.md" {}` (glob pattern) → no declarations
  - empty body → no declarations
- Extend `DeclarationExtractor.extract()`: scan root for `ambient_module_declaration` nodes; extract module name string as `parentPath`; reuse `extractFromNode` for each declaration in the body, passing `parentPath` through

### 15. Fix: JSX elements as usedTypes + TsxDependencyMapping (TDD)

DC already supported JSX/TSX; DC regression tests go red without it. TSX uses `Language.TSX` (DC fix: dispatch `.tsx` files accordingly). The TSX parser emits `jsx_opening_element` and `jsx_self_closing_element` nodes; TypeScript does not — so JSX support lives in `UsedTypeExtractor` as an additive, backward-compatible extension.

- Write failing tests in a new `TsxDependencyTest.kt`:
  - `class Foo { render() { return <Routes /> } }` → `UsedType("Routes")` in Foo's usedTypes
  - `class Foo { render() { return <Form.Input /> } }` → `UsedType("Form")` (root identifier only)
  - `class Foo { render() { return <div /> } }` → `div` NOT included (lowercase)
  - Verify imports and class declarations also work (reuse of TS extractors)
- Extend `UsedTypeExtractor`:
  - Add `jsx_opening_element` and `jsx_self_closing_element` to `ALL_NODE_TYPES`
  - Add `extractJsxComponents`: extract tag's root identifier if uppercase-first; for member expressions (`Form.Input`), include only the root object
- Create `TsxDependencyMapping` in `languages/tsx/`:
  - Reuses `PackageExtractor::extract`, `ImportExtractor::extract`, `DeclarationExtractor::extract`
- Override `dependencyMapping` in `TsxDefinition`

### 16. Fix: Import alias → original usedType (TDD)

`import { MyType as MyRenamedType } from './MyType'` + `private myType: MyRenamedType` should produce `UsedType("MyType")`, not `UsedType("MyRenamedType")`.

- Write failing test in `TypescriptDependencyTest.UsedTypeExtraction`:
  - class with aliased import used as field type → usedTypes contains original name, not alias
- Add private `buildAliasMap(rootNode: TSNode, sourceCode: String): Map<String, String>` to `DeclarationExtractor`:
  - scan `import_statement` → `named_imports` → `import_specifier` with 2 identifier children
  - map alias (second identifier) → original (first identifier)
- Change `UsedTypeExtractor.extract()` to accept `aliasMap: Map<String, String> = emptyMap()`
- After collecting all usedTypes in `UsedTypeExtractor`, replace names present in `aliasMap` with their originals
- Call from `DeclarationExtractor.extract()` with the pre-built alias map

## Steps

- [x] Explore TypeScript + JavaScript AST (dump samples, verify all node type assumptions)
- [x] Extend `DeclarationType` with FUNCTION and VARIABLE (TDD)
- [x] Write ImportExtraction tests → implement shared `ImportExtractor` (ES6 + CommonJS)
- [x] Write TypeScript PackageExtraction tests → implement (returns empty list)
- [x] Write TypeScript DeclarationExtractor tests → implement (find + classify all declaration types)
- [x] Write TypeScript UsedTypeExtractor tests (incremental, 6 categories) → implement
- [x] Create `TypescriptDependencyMapping`, register in `TypescriptDefinition`
- [x] Create `JavascriptDependencyMapping`, register in `JavascriptDefinition`
- [x] Write edge case and completeness tests
- [x] Add named re-exports as imports (`export { Foo } from '...'`) to `ImportExtractor` (TDD)
- [x] Add wildcard re-exports as imports (`export * from '...'`) to `ImportExtractor` (TDD)
- [x] Add dynamic imports (`import('...')`) to `ImportExtractor` (TDD)
- [x] Refine ImportExtractor: named ES6 → path+name, default ES6 → DEFAULT_EXPORT, CommonJS destructuring → per-name, CommonJS default → DEFAULT_EXPORT, named re-exports → per-specifier with original name (alias preserved)
- [x] Refine DeclarationExtractor: add variable_declaration → VARIABLE, scope to direct children only (skip nested const inside class/function bodies)
- [x] Fix: CommonJS pair_pattern destructuring → pair_pattern key as import name (TDD)
- [x] Fix: JavaScript declaration extraction → use DeclarationExtractor in JavascriptDependencyMapping (TDD)
- [x] Fix: REEXPORT declarations → add REEXPORT to DeclarationType, extend DeclarationExtractor (TDD)
- [ ] Fix: declare module declarations → AST dump, then implement with parentPath (TDD)
- [ ] Fix: JSX elements as usedTypes → extend UsedTypeExtractor + create TsxDependencyMapping (TDD)
- [ ] Fix: Import alias → original usedType → buildAliasMap + thread through UsedTypeExtractor (TDD)
- [ ] Set up DC branch, run first dc-compare (TypeScript project) — iterate
- [ ] Run first dc-compare (JavaScript project) — iterate
- [ ] Final verification: full test suite + ktlintCheck + architecture tests

## Session Notes

### 2026-04-21 — DC integration fixes (5 failing test groups)

After integrating TSE into DC's TypescriptAnalyzer, 17 DC tests remained failing. Root causes identified and planned as tasks 12–16:

- **Fix 12** (CommonJS pair_pattern): `const { key: alias } = require(...)` not extracting the original key name
- **Fix 13** (REEXPORT declarations): `export { A } from '...'` and `export { A as B } from '...'` producing no Declaration entries
- **Fix 14** (declare module): ambient module blocks not producing any declarations; need AST dump to confirm node type first
- **Fix 15** (JSX usedTypes + TsxDependencyMapping): DC regression tests require JSX support; approach is extend `UsedTypeExtractor` with JSX nodes (backward-compatible) + create `TsxDependencyMapping` reusing TS extractors; DC must dispatch `.tsx` to `Language.TSX`
- **Fix 12b** (JavaScript declarations): `JavascriptDependencyMapping` used `emptyList()` for declarations — wrong; DC's JavascriptAnalyzer extracts exported declarations. Fix: use `DeclarationExtractor::extract`, same as TypeScript.
- **Fix 16** (import alias): aliased imports like `MyType as MyRenamedType` leaking local alias name into usedTypes instead of original

## Notes

- All new files live in `languages/javascript/` (TypeScript and JavaScript share the folder)
- TypeScript concatenation order: typeIdentifiers, constructorCalls, memberAccesses, methodCalls, extensions, relevantIdentifiers
- JavaScript: full extraction — `JavascriptDependencyMapping` uses same extractors as TypeScript (correction: initial "imports only" assumption was wrong)
- `type_alias_declaration` → CLASS (matches DC legacy behavior)
- Each language owns its own `extractType` helper — no shared utility (per Kotlin plan decision)
- DC follow-up (separate PR): rewrite DC's `TypescriptAnalyzer` + `JavascriptAnalyzer` to use TSE
