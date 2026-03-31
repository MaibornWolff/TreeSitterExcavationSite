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

### No boundary exclusion (TypeScript)
Start without boundary exclusion — match DC's re-parsing type leakage behavior. Add only if dc-compare reveals issues.

### Used type concatenation order (TypeScript)
From `integration/dependencies/README.md`: `typeIdentifiers, constructorCalls, memberAccesses, methodCalls, extensions, relevantIdentifiers`

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

### 6. Set up early dc-compare loop

Create DC branch `feat/tse-typescript-javascript-integration` pointing to TSE feature branch via JitPack. Run after basic extractors work — don't wait until feature-complete.

Test repos:
- **TypeScript primary**: medium-sized open-source TypeScript project (e.g., `microsoft/TypeScript-Node-Starter` or `prisma/prisma`)
- **JavaScript primary**: medium-sized JS project with ES6 + CommonJS mix

When to run:
- After ImportExtractor + DeclarationExtractor basics work
- After UsedTypeExtractor is complete
- After each fix

### 7. Remaining tests + final iteration

- Edge cases: empty file, imports-only, declarations without type annotations, deeply nested declarations
- Completeness check: realistic file exercising all features
- Iterate on dc-compare differences until match

### 8. Final verification

- `./gradlew test` — all tests pass
- `./gradlew ktlintCheck` — clean
- Architecture tests pass
- Final dc-compare confirms match for both TypeScript and JavaScript

## Steps

- [x] Explore TypeScript + JavaScript AST (dump samples, verify all node type assumptions)
- [x] Extend `DeclarationType` with FUNCTION and VARIABLE (TDD)
- [ ] Write ImportExtraction tests → implement shared `ImportExtractor` (ES6 + CommonJS)
- [ ] Write TypeScript PackageExtraction tests → implement (returns empty list)
- [ ] Write TypeScript DeclarationExtractor tests → implement (find + classify all declaration types)
- [ ] Write TypeScript UsedTypeExtractor tests (incremental, 6 categories) → implement
- [ ] Create `TypescriptDependencyMapping`, register in `TypescriptDefinition`
- [ ] Create `JavascriptDependencyMapping`, register in `JavascriptDefinition`
- [ ] Set up DC branch, run first dc-compare (TypeScript project) — iterate
- [ ] Run first dc-compare (JavaScript project) — iterate
- [ ] Write edge case and completeness tests
- [ ] Final verification: full test suite + ktlintCheck + architecture tests

## Notes

- All new files live in `languages/javascript/` (TypeScript and JavaScript share the folder)
- TypeScript concatenation order: typeIdentifiers, constructorCalls, memberAccesses, methodCalls, extensions, relevantIdentifiers
- JavaScript: imports only — no `UsedTypeExtractor` needed
- `type_alias_declaration` → CLASS (matches DC legacy behavior)
- Each language owns its own `extractType` helper — no shared utility (per Kotlin plan decision)
- DC follow-up (separate PR): rewrite DC's `TypescriptAnalyzer` + `JavascriptAnalyzer` to use TSE
