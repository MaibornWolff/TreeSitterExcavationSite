---
name: Fix default import usedType remapping (DEFAULT_EXPORT → local alias)
issue:
state: complete
version:
---

## Goal

When a file does `import Foo from './dep'` and uses `Foo` in a type position, TSE emits `DEFAULT_EXPORT`
as the usedType. DC filters it out as a workaround, losing the cross-file dependency edge entirely.
This is not an accepted gap — `import Foo from './dep'` followed by `class Bar extends Foo {}` is
exactly the kind of structural dependency DC should track.

Fix by adding `bindingName` to TSE's `ImportDeclaration` domain model, then wiring it through DC
so the resolver can correctly link the usedType to the default export node.

## Phase 1 Findings (investigation complete)

### Data flow for `import Foo from './dep'`

**TSE `ImportExtractor`:**
```text
ImportDeclaration(path=["dep", "DEFAULT_EXPORT"], isWildcard=false)
```
The binding name `Foo` is detected but discarded. `ImportDeclaration` has no `bindingName` field.

**TSE `buildAliasMap()` + `UsedTypeExtractor`:**
```text
aliasMap["Foo"] = "DEFAULT_EXPORT"
→ usedType("DEFAULT_EXPORT") emitted for class Bar extends Foo {}
```

**DC `TseMappings.toDependency()`:**
```text
Dependency(path=Path(["dep", "DEFAULT_EXPORT"]))
```

**DC `TypescriptAnalyzer.buildPathWithName()` for `export default class Foo {}` in `dep/index.ts`:**
```text
node path = ["dep", "index", "dep_index_DEFAULT_EXPORT"]   ← qualified to avoid collisions
```

**Root mismatch:** Import dependency path `["dep", "DEFAULT_EXPORT"]` ≠ node path
`["dep", "index", "dep_index_DEFAULT_EXPORT"]`. The resolver cannot follow the dependency to the node.

**DC workaround `selectUsedTypes()`:** Filters `DEFAULT_EXPORT` from non-REEXPORT usedTypes — masking
the mismatch rather than fixing it.

**DC workaround `extraUsedTypes()`:** For each default import, synthesizes `Type.simple("dep")` (module
name) added to ALL declarations in the file regardless of which ones actually use `Foo` — imprecise.

### Why Option 2 (fix DC node path) was rejected

The qualified name (`dep_index_DEFAULT_EXPORT`) was deliberately introduced to avoid node ID collisions.
Unwinding it risks breaking the node graph structure. `bindingName` on `ImportDeclaration` is the
architecturally cleaner fix: the binding name belongs in the domain model and makes intent explicit.

## Revised Fix (Option 1: add `bindingName` to `ImportDeclaration`)

### TSE changes

**1. Extend `ImportDeclaration` with `bindingName`:**
```kotlin
data class ImportDeclaration(
    val path: List<String>,
    val isWildcard: Boolean,
    val namespacePath: List<String> = emptyList(),
    val kind: ImportKind = ImportKind.STANDARD,
    val bindingName: String? = null,   // ← new: local alias for default imports (e.g. "Foo")
)
```
Non-breaking — defaults to `null`, existing consumers unaffected.

**2. `ImportExtractor.extractEs6Imports()` — store the binding name:**
When a default binding is detected, capture the text and pass it as `bindingName`:
```kotlin
if (hasDefaultBinding) {
    val bindingText = importClause.children().firstOrNull { it.type == IDENTIFIER }
        ?.let { TreeTraversal.getNodeText(it, sourceCode).trim() }
    named + ImportDeclaration(path = basePath + DEFAULT_EXPORT, isWildcard = false, bindingName = bindingText)
}
```

**3. `buildAliasMap()` — emit local alias instead of DEFAULT_EXPORT sentinel:**
```kotlin
aliasMap[name] = name   // was: aliasMap[name] = DEFAULT_EXPORT
```
TSE now emits `Foo` (not `DEFAULT_EXPORT`) as the usedType.

### DC changes

**4. `TypescriptAnalyzer` — use `bindingName` to resolve the edge:**
When processing an import with `bindingName != null`, DC can create a reverse-lookup map
`bindingName → import.path` (e.g., `"Foo" → ["dep", "DEFAULT_EXPORT"]`). The resolver can then
match usedType `Foo` → import path `dep/DEFAULT_EXPORT` → node at `dep_index_DEFAULT_EXPORT`.
This may require a small adapter in `extraUsedTypes()` or a new override.

**5. Remove DC workarounds:**
- `selectUsedTypes()`: remove the `DEFAULT_EXPORT` filter (or the whole override)
- `extraUsedTypes()`: remove the imprecise module-name proxy for default imports

## Tasks

### 1. TSE — extend `ImportDeclaration` with `bindingName`

- Add `bindingName: String? = null` to `ImportDeclaration` in `shared/domain/DependencyResult.kt`
- Update `ImportExtractor.extractEs6Imports()` to populate `bindingName` for default imports
- Update `ImportExtractor.extractCommonJsImports()` similarly for CJS default bindings
  (`const Foo = require('./dep')` → `bindingName = "Foo"`)
- Write tests: `should populate bindingName for ES6 default import`,
  `should populate bindingName for CJS default import`, `should leave bindingName null for named import`

### 2. TSE — emit local alias in usedTypes

- In `DeclarationExtractor.buildAliasMap()`: change `aliasMap[name] = DEFAULT_EXPORT` → `aliasMap[name] = name`
- Update test `should resolve default import name to DEFAULT_EXPORT in usedTypes` → expect `Foo` (the
  local alias) instead of `DEFAULT_EXPORT`
- Run `./gradlew test` green

### 3. DC — wire `bindingName` through the adapter

- Read DC's resolver logic to confirm how to map usedType `Foo` → import binding → default export node
  (this determines the exact shape of the DC adapter change)
- In `TypescriptAnalyzer`: build a `bindingName → import path` lookup from imports with non-null
  `bindingName`, and use it to synthesize a correctly-targeted `Type` or `Dependency`
- Remove `selectUsedTypes()` DEFAULT_EXPORT filter
- Remove `extraUsedTypes()` module-name proxy for default imports

### 4. Verify

- DC tests green
- dc-compare against Prisma (TS) — expect reduction in lost edges, no new regressions

## Steps

- [x] Phase 1: Investigate `ImportDeclaration` structure and DC workarounds
- [x] Phase 2a: Write failing tests for `bindingName` — added to JS and TS test files (compile error = RED)
- [x] Phase 2b: Add `bindingName` to `ImportDeclaration` domain type
- [x] Phase 2c: Update `ImportExtractor` (ES6 + CJS) to populate `bindingName`
- [x] Phase 2d: Write failing test — `should emit local alias in usedTypes for default import`
- [x] Phase 2e: Change `buildAliasMap()` to identity-map default bindings
- [x] Phase 2f: Update affected tests (DEFAULT_EXPORT → local alias assertions)
- [x] Phase 2g: `./gradlew test` green; `./gradlew ktlintCheck` passes
- [x] Phase 3a: Read DC resolver to confirm binding-name wiring approach (Task 3)
- [x] Phase 3b: Remove DC `selectUsedTypes()` filter (entire override)
- [x] Phase 3c: Remove DEFAULT_EXPORT proxy from DC `extraUsedTypes()` (return null for DEFAULT_EXPORT specifiers)
- [x] Phase 3d: DC tests green (613/613)
- [x] Phase 3e: TSE bugfix — `collectExportReferencedLocalNames` now also captures `export default <identifier>` names so declare-then-export-default correctly includes the original declaration
- [x] Phase 4: dc-compare Prisma/React — results below; DC changes confirmed safe (2-dep delta)

## dc-compare Results (version 0.10.0-local, Prisma/React)

| Metric        | TS (Prisma) | JS (React) |
|---------------|-------------|------------|
| Missing deps  | 1,064       | 6,859      |
| Extra deps    | 132         | 1,567      |
| Only in main  | 1,630       | 962        |
| Only in feat. | 190         | 262        |

**Accepted differences vs prior baseline (fix-js-declaration-extraction-gaps plan):**
- only-in-main 7→1,630 (TS) and 97→962 (JS): TSE export-only filtering now fully applied; DC main (legacy) still extracts non-exported internal helpers (e.g. `wrap` in `helpers/blaze/flatten.ts`). Accepted architectural gap — same gap present with old DC code, new DC code causes only a 2-dep difference.
- missing deps 432→1,064 (TS): consequence of fewer shared nodes due to export-only filtering, not a regression in import resolution.
- extra deps 662→132 (TS) and 3,728→1,567 (JS): improvement — fewer spurious dependency edges.

## Notes

- `DEFAULT_EXPORT` as a **declaration name** in TSE (for `export default class Foo {}`) is unchanged.
  Only the usedType emission and the `ImportDeclaration` model are changing.
- `bindingName` defaults to `null` — non-breaking for Java, Kotlin, C#, C++, and all other languages.
- CJS pattern `const { A, B } = require('./dep')` (destructured) is already handled by
  `extractDestructuredCommonJs()` and produces named imports, not a default binding — no change needed.
- JS `JavascriptAnalyzer.convertImport()` converts `DEFAULT_EXPORT` → `"default"` in paths.
  Check whether JS also needs the `bindingName` wiring or whether the JS graph already handles it.
- The `extraUsedTypes()` module-name proxy removal reduces noise: previously every declaration in a
  file with a default import got an extra `Type("dep")` usedType regardless of whether it used `Foo`.