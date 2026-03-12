---
name: migrate-dependency-analysis
issue:
state: complete
version: 2
---

## Goal

Migrate TreeSitter-based dependency analysis from DependaCharta (DC) to TreeSitterExcavationSite (TSE), starting with Java. This removes
DC's direct TreeSitter dependency for Java and makes the parsing logic reusable.

## Tasks

### 1. TSE: Domain Types + API + Java Package & Import Extraction

New vertical slice `integration/dependencies/` following hexagonal architecture (like metrics/extraction).

**New files** (all source paths relative to `src/main/kotlin/de/maibornwolff/treesitter/excavationsite/`):

- `shared/domain/DependencyResult.kt` — `DependencyResult`, `ImportDeclaration`, `Declaration`, `DeclarationType`, `UsedType`
- `shared/domain/DependencyMapping.kt` — `DependencyMapping` interface with optional `dependencyAnalyzer`, `DependencyAnalyzer` interface:
  `analyze(rootNode: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage) -> DependencyResult`
- `integration/dependencies/DependenciesFacade.kt` — Facade (follows `ExtractionFacade` pattern)
- `shared/infrastructure/walker/TreeSitterQueryUtils.kt` — `QueryMatch`/`QueryCapture` types and
  `TSNode.executeQuery(queryString: String, treeSitterLanguage: TSLanguage): List<QueryMatch>` extension ported from DC's
  `TreeSitterUtils.kt`
- `languages/java/JavaDependencyAnalyzer.kt` — Package + import extraction (port of `JavaPackageQuery` + `JavaImportQuery`)
- `api/TreeSitterDependencies.kt` — Public API

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

**Transition strategy: just swap internals.** Replace `JavaAnalyzer`'s body to call TSE directly. No CLI flags, no dual mode. DC's existing
`JavaAnalyzerTest` provides safety — if tests pass, the swap is correct.

**Modified files in DC:**

- `analysis/settings.gradle.kts` — `includeBuild("../TreeSitterExcavationSite")`
- `analysis/build.gradle.kts` — Add TSE dependency
- `JavaAnalyzer.kt` — Call `TreeSitterDependencies.analyze()`, map results to DC's `FileReport`/`Node`

**Delete from DC:** All Java query files (`JavaPackageQuery`, `JavaImportQuery`, `JavaDeclarationsQuery`, `SimpleJavaQueries`,
`JavaInheritanceQuery`, `JavaMethodAndConstructorQuery`, `JavaThrownTypesQuery`) + `JavaUtils.kt`

**Verify:** `JavaAnalyzerTest.kt` passes unchanged

## Steps

- [x] Complete Task 1: TSE domain types + API + Java package/import extraction
- [x] Complete Task 2: TSE Java declaration + used type extraction
- [x] Complete Task 3: DC integration — JavaAnalyzer delegates to TSE

## Verification

### JSON Correctness
- Small project (DC test fixture, 16 Java files): All runs on both branches produce **identical** output (sorted-key comparison)
- Large project (Apache Commons Lang, 259 Java files): Same leaf count (355) across all runs, but resolved dependency counts for 3 leaves vary
  between runs — this is a **pre-existing DC concurrency issue**, not caused by the migration. Details below.

### DC Concurrency Issue (Pre-existing)

**Symptom:** On large projects (259 Java files, 22-thread parallel analysis), 3 out of 355 leaves show non-deterministic resolved dependency
counts across runs: `ArrayUtils` (14 vs 15), `MethodUtils` (8 vs 9), `FastDatePrinter` (21 vs 25). The specific dependencies that
appear/disappear include `FailableFunction`, `ToStringStyle`, `Executable`, `CharUtils`, `ClassUtils`, `ExceptionUtils`, `CalendarUtils`.

**Root cause:** DC's `AnalysisPipeline` runs file analysis in parallel using Kotlin coroutines (`Dispatchers.IO` + `Semaphore` + `async/awaitAll`)
with results collected into a `ConcurrentHashMap`. While the per-file analysis (`JavaAnalyzer.analyze()`) and per-node type resolution
(`Node.resolveTypes()`) are each individually sequential and deterministic, the **project-level dependency resolution** depends on a shared
dictionary built from all analyzed files. The order in which files complete and contribute to this dictionary is non-deterministic under parallel
execution, causing the type resolver to find slightly different sets of matching types depending on timing.

**Proof this is pre-existing:** The same non-determinism reproduces on DC's `main` branch (before any TSE integration), and both branches produce
the same variance pattern. Small projects (16 files) are unaffected — likely because the parallelism window is too small for race conditions to
manifest.

### Performance
- Benchmarked with wall-clock timing, fresh JVM per run (no warmup)
- Small project (16 files, 5 runs): ~492ms both branches — no measurable difference
- Large project (259 files, 10 runs): 1–3.5s both branches — high variance from JVM cold starts dominates
- Conclusion: **no performance regression**. Variance between runs (~2x) is far larger than any difference between implementations
- A reliable benchmark would require JMH (in-process, JVM warmup, percentile reporting) — not warranted unless performance becomes a concern

## Future Improvements

- Extract record component types (`record User(String name, int age)` — `String` and `int` are currently not captured as used types). Gap
  exists in DC too. Add after Task 3 is verified.
- Filter nested declaration types from outer declarations (types from an inner class leak into the outer class's `usedTypes`). Gap exists in
  DC too. Add after Task 3 is verified.
- Fix DC concurrency issue: Separate file analysis (parallelizable) from dependency resolution (requires complete dictionary). Run all file
  analyses first, build the full project dictionary, then resolve dependencies in a second sequential pass. This guarantees deterministic output
  regardless of thread count. Alternatively, make the dictionary thread-safe and ensure all files are analyzed before resolution begins (barrier
  synchronization).

## Notes

- Architecture: New vertical slice `integration/dependencies/`, NOT extending the existing extraction feature (dependencies are structured
  semantic data, not flat text)
- Uses TSQuery-based pattern matching (different from walker-based metrics/extraction)
- `LanguageDefinition` gains optional `DependencyMapping` with default `null` — existing languages unaffected
- `DependencyAnalyzer` interface lives in `shared/domain/` (not `integration/`) because `LanguageDefinition` references it and domain
  already uses TSNode (see `Extract.kt`). The facade and orchestration still live in `integration/dependencies/`, language implementations
  in `languages/java/`.
- `DependencyMapping.isDependencyAnalysisSupported` keeps the null-check on the mapping itself — API layer only orchestrates, no business
  logic
- DC-TSE link: Gradle composite build (`includeBuild`) for development; will switch to JitPack dependency for release (matching CodeCharta's pattern)
- DC branch: `feat/tse-integration` in DependaCharta repo
- `UsageKind` skipped for now — Java defaults all types to plain USAGE; can be added when migrating languages that need it
- Type resolution (matching used types to project dictionary) stays in DC — it's project-level, not file-level
- DC's `TypeOfUsage` is excluded from `Type.equals()` — no impact from TSE not carrying usage source
- `DeclarationType.RECORD` in TSE maps to `NodeType.CLASS` in DC (same as DC's original behavior)
- Bumped `tree-sitter-vue` to `0.2.1a` in TSE to prevent composite build version conflict with DC
- JitPack repo needed in DC for TSE's transitive ABL dependency (same pattern as CodeCharta)
- Removed `tree-sitter-java` from DC dependencies (now provided transitively through TSE)
- End-to-end JSON output verified identical (sorted keys) between old and new implementation
- Branch: `feat/dependency-analysis`
