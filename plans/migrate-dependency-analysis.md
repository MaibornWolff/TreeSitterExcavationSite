---
name: migrate-dependency-analysis
issue:
state: progress
version: 2
---

## Goal

Migrate TreeSitter-based dependency analysis from DependaCharta (DC) to TreeSitterExcavationSite (TSE), starting with Java. This removes DC's direct TreeSitter dependency for Java and makes the parsing logic reusable.

## Tasks

### 1. TSE: Domain Types + API + Java Package & Import Extraction

New vertical slice `integration/dependencies/` following hexagonal architecture (like metrics/extraction).

**New files** (all source paths relative to `src/main/kotlin/de/maibornwolff/treesitter/excavationsite/`):
- `shared/domain/DependencyResult.kt` — `DependencyResult`, `ImportDeclaration`, `Declaration`, `DeclarationType`, `UsedType`
- `shared/domain/DependencyMapping.kt` — Interface with optional `dependencyAnalyzer`
- `integration/dependencies/DependencyAnalyzer.kt` — Interface: `analyze(rootNode, sourceCode) -> DependencyResult`
- `integration/dependencies/DependenciesFacade.kt` — Facade (follows `ExtractionFacade` pattern)
- `shared/infrastructure/walker/TreeSitterQueryUtils.kt` — New file with two extensions ported from DC's `TreeSitterUtils.kt`:
  - `TSNode.executeQuery(query: TSQuery): List<TSQueryMatch>` — Runs TSQuery pattern matching
  - `TSNode.namedChildren(): Sequence<TSNode>` — Named children only, skipping punctuation
  - NOT ported: `nodeAsString` (already `TreeTraversal.getNodeText`), `getChildren` (already `TSNode.children()`)
- `languages/java/JavaDependencyAnalyzer.kt` — Package + import extraction (port of `JavaPackageQuery` + `JavaImportQuery`)
- `api/TreeSitterDependencies.kt` — Public API
- `api/DependencyTypes.kt` — Public type re-exports

**Modified files:**
- `shared/domain/LanguageDefinition.kt` — Extend with `DependencyMapping`
- `languages/java/JavaDefinition.kt` — Override `dependencyAnalyzer`

**Tests:** `languages/java/JavaDependencyTest.kt` — package path, imports, wildcards, static imports

**DC source references to port from:**
- `JavaPackageQuery.kt` — TSQuery `(package_declaration) @package`
- `JavaImportQuery.kt` — TSQuery `(import_declaration) @import`, wildcard via asterisk child
- `TreeSitterUtils.kt` — `execute()`, `nodeAsString()`, `getNamedChildren()`

### 2. TSE: Java Declaration + Used Type Extraction

Expand `JavaDependencyAnalyzer` with declaration finding and all used type queries.

**Add to `JavaDependencyAnalyzer.kt`:**
- `extractDeclarations()` — TSQuery for class/record/interface/enum/annotation declarations
- `extractType()` — Recursive generic type extraction (port of `JavaUtils.extractType()`)
- Used type queries ported from DC:
  - Field types (`field_declaration`), variable types (`local_variable_declaration`)
  - Annotations (`annotation`, `marker_annotation`)
  - Inheritance (`super_interfaces`, `extends_interfaces`, `superclass`)
  - Constructor calls (`object_creation_expression`)
  - Method/field access (`method_invocation`, `field_access`) — uppercase only
  - Thrown types (`throws`)
  - Method/constructor parameter + return types

**Tests:** Expand `JavaDependencyTest.kt` matching DC's `JavaAnalyzerTest` scenarios

### 3. DC Integration: JavaAnalyzer Delegates to TSE

**Transition strategy: just swap internals.** Replace `JavaAnalyzer`'s body to call TSE directly. No CLI flags, no dual mode. DC's existing `JavaAnalyzerTest` provides safety — if tests pass, the swap is correct.

**Modified files in DC:**
- `analysis/settings.gradle.kts` — `includeBuild("../TreeSitterExcavationSite")`
- `analysis/build.gradle.kts` — Add TSE dependency
- `JavaAnalyzer.kt` — Call `TreeSitterDependencies.analyze()`, map results to DC's `FileReport`/`Node`

**Delete from DC:** All Java query files (`JavaPackageQuery`, `JavaImportQuery`, `JavaDeclarationsQuery`, `SimpleJavaQueries`, `JavaInheritanceQuery`, `JavaMethodAndConstructorQuery`, `JavaThrownTypesQuery`) + `JavaUtils.kt`

**Verify:** `JavaAnalyzerTest.kt` passes unchanged

## Steps

- [x] Complete Task 1: TSE domain types + API + Java package/import extraction
- [ ] Complete Task 2: TSE Java declaration + used type extraction
- [ ] Complete Task 3: DC integration — JavaAnalyzer delegates to TSE

## Notes

- Architecture: New vertical slice `integration/dependencies/`, NOT extending the existing extraction feature (dependencies are structured semantic data, not flat text)
- Uses TSQuery-based pattern matching (different from walker-based metrics/extraction)
- `LanguageDefinition` gains optional `DependencyMapping` with default `null` — existing languages unaffected
- `DependencyAnalyzer` interface lives in `shared/domain/` (not `integration/`) because `LanguageDefinition` references it and domain already uses TSNode (see `Extract.kt`). The facade and orchestration still live in `integration/dependencies/`, language implementations in `languages/java/`.
- `DependencyMapping.isDependencyAnalysisSupported` keeps the null-check on the mapping itself — API layer only orchestrates, no business logic
- DC-TSE link: Gradle composite build (`includeBuild`)
- `UsageKind` skipped for now — Java defaults all types to plain USAGE; can be added when migrating languages that need it
- Type resolution (matching used types to project dictionary) stays in DC — it's project-level, not file-level
- Branch: `feat/dependency-analysis`
