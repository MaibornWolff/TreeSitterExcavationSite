# Dependencies Feature

## Overview

The dependencies feature extracts structural dependency information from source files: package declarations, imports, class/interface/enum declarations, and the types each declaration uses. This data is consumed by DependaCharta (DC) to build dependency graphs, detect cycles, and assign architectural levels.

Currently only Java is implemented. Other languages will be migrated from DC's legacy analyzers over time.

## Architecture

Follows the vertical slice pattern used by metrics and extraction, but without a port/adapter layer since no transformation is needed between the domain mapping and the collector:

```text
API                          Integration                    Languages
┌──────────────────────┐     ┌────────────────────────┐     ┌──────────────────────────┐
│ TreeSitterDependencies│────>│ DependenciesFacade     │     │ languages/java/          │
│   .analyze()         │     │   ↓                    │     │   JavaDependencyMapping   │
│                      │     │ DependencyCollector     │     │   extractors/            │
│ Validates language   │     │   calls lambdas from   │     │     PackageExtractor      │
│ support, delegates   │     │   LanguageDependency-  │<────│     ImportExtractor       │
│ to facade            │     │   Mapping directly     │     │     DeclarationExtractor  │
└──────────────────────┘     └────────────────────────┘     │     UsedTypeExtractor     │
                                                            └──────────────────────────┘
```

### Data flow

```text
Source code string
  → TreeSitterDependencies.analyze(code, Language.JAVA)
    → DependenciesFacade applies preprocessor, passes mapping to collector
      → DependencyCollector parses AST via TreeSitterParser
        → LanguageDependencyMapping lambdas called:
          1. extractPackagePath()  → ["com", "example", "service"]
          2. extractImports()      → [ImportDeclaration(path, isWildcard)]
          3. extractDeclarations() → [Declaration(name, type, usedTypes)]
  → DependencyResult
```

### Key files

| File | Purpose |
|---|---|
| `api/TreeSitterDependencies.kt` | Public API — validates language support, delegates to facade |
| `integration/dependencies/DependenciesFacade.kt` | Entry point — applies preprocessor, passes mapping to collector |
| `integration/dependencies/DependencyCollector.kt` | Parses AST, calls mapping lambdas, assembles result |
| `shared/domain/DependencyMapping.kt` | `LanguageDependencyMapping` data class + `DependencyMapping` wrapper |
| `shared/domain/DependencyResult.kt` | Result types: `DependencyResult`, `Declaration`, `ImportDeclaration`, `UsedType` |

## Domain model

```kotlin
data class DependencyResult(
    val packagePath: List<String>,          // ["com", "example", "service"]
    val imports: List<ImportDeclaration>,
    val declarations: List<Declaration>
)

data class ImportDeclaration(
    val path: List<String>,                 // ["java", "util", "List"]
    val isWildcard: Boolean                 // true for "import java.util.*"
)

data class Declaration(
    val name: String,                       // "MyService"
    val type: DeclarationType,              // CLASS, INTERFACE, ENUM, RECORD, ANNOTATION
    val usedTypes: Set<UsedType>            // types referenced inside this declaration
)

data class UsedType(
    val name: String,                       // "List"
    val genericTypes: List<UsedType> = []   // [UsedType("String")] for List<String>
)
```

## Adding a new language

### 1. Create the dependency mapping

```kotlin
// languages/newlang/NewLangDependencyMapping.kt
object NewLangDependencyMapping {
    val dependencyMapping = LanguageDependencyMapping(
        extractPackagePath = PackageExtractor::extract,
        extractImports = ImportExtractor::extract,
        extractDeclarations = DeclarationExtractor::extract
    )
}
```

### 2. Create extractors in `languages/newlang/extractors/`

Each extractor is an `internal object` with an `extract` function. Use direct tree traversal (`TreeTraversal.findAllDescendantsOfType`, `findAllDescendantsGroupedByType`, etc.) — not TSQuery.

Required extractors:
- **PackageExtractor** — extracts the package/module path as `List<String>`
- **ImportExtractor** — extracts imports as `List<ImportDeclaration>`
- **DeclarationExtractor** — finds class/interface/enum declarations, delegates to UsedTypeExtractor for each
- **UsedTypeExtractor** — extracts all types used within a declaration

See `languages/java/extractors/` for the reference implementation.

### 3. Register in the language definition

```kotlin
// languages/newlang/NewLangDefinition.kt
object NewLangDefinition : LanguageDefinition {
    override val nodeMetrics = NewLangMetricMapping.nodeMetrics
    override val nodeExtractions = NewLangExtractionMapping.nodeExtractions
    override val dependencyMapping = NewLangDependencyMapping.dependencyMapping
}
```

### 4. Add tests

Create `src/test/kotlin/.../languages/newlang/NewLangDependencyTest.kt` with `@Nested` inner classes for each extraction aspect:

```kotlin
class NewLangDependencyTest {
    @Nested
    inner class PackageExtraction { ... }

    @Nested
    inner class ImportExtraction { ... }

    @Nested
    inner class DeclarationExtraction { ... }

    @Nested
    inner class ApiSupportCheck { ... }
}
```

### 5. Verify with dc-compare

Run dc-compare between DC main (legacy analyzer) and the DC branch integrating TSE for that language. Use the `/dc-compare` command (defined in `.claude/commands/dc-compare.md`).

## Used type concatenation order

When the `UsedTypeExtractor` collects used types for a declaration, it gathers them by category (inheritance, fields, annotations, etc.) and concatenates them into a list before calling `.toSet()`. The **concatenation order must match DC's legacy analyzer** for that language.

### Why order matters

DC's Levelizer breaks dependency cycles by picking the node with the fewest incoming edges. When nodes are tied, `minByOrNull` picks whichever it encounters first. Kotlin's `.toSet()` creates a `LinkedHashSet` that preserves insertion order, so the concatenation order propagates through DC's pipeline and affects which cycle edge gets cut, which determines `level` and `isCyclic` assignments.

A different concatenation order with the same types produces identical dependency data but can change DC's levelization output.

### How to match the order

1. Find DC's legacy analyzer in `DependaCharta/analysis/.../analyzers/<language>/`
2. Look for the `extractUsedTypes` method (or equivalent) where categories are concatenated
3. Use the same concatenation order in TSE's `UsedTypeExtractor`
4. If DC has no legacy analyzer for the language, use whatever order makes sense

### DC legacy concatenation orders

| Language | Order | DC file |
|---|---|---|
| **Java** | inheritance, variables, annotations, methodInvocations, constructorCalls, thrownTypes, fields, methods | `JavaAnalyzer.kt` |
| **Kotlin** | inheritance, properties, parameters, returnTypes, annotations, constructorCalls, callExpressions | `KotlinAnalyzer.kt` |
| **TypeScript** | typeIdentifiers, constructorCalls, memberAccesses, methodCalls, extensions, relevantIdentifiers | `TypeScriptAnalyzer.kt` |
| **PHP** | constants, inherited, implemented, const, return, argument, instantiations, properties, staticAccess, traits | `UsedTypesExtractor.kt` |
| **C#** | constructors, methods, casts, genericParams, genericConstraints, inherited, variables, objectCreations, memberAccesses, attributes, isTypeChecks | `CsharpAnalyzer.kt` |
| **C++** | processor list order: typeDecl, inheritance, methods, typeDef, alias, generic, define | `BodyProcessor.kt` |
| **Go** | functionQuery, typeQuery | `GoAnalyzer.kt` |
| **Python** | N/A — imports only, no multi-category concatenation | `PythonAnalyzer.kt` |
| **JavaScript** | N/A — imports only, no multi-category concatenation | `JavascriptAnalyzer.kt` |
| **Vue** | script imports, template components | `VueAnalyzer.kt` |

## How DC consumes TSE output

DC's `DependencyResolverService` takes the `DependencyResult` and:

1. Builds a project dictionary mapping type names to full paths
2. Calls `Node.resolveTypes()` on each node — resolves used type names to actual project nodes via imports
3. Classifies resolved dependencies as internal vs external
4. Feeds resolved nodes into `CycleAnalyzer` (Tarjan's SCC) for cycle detection
5. Feeds the graph into `Levelizer` for architectural level assignment
6. Exports as cc.json

TSE's job is steps 1-2 of the legacy pipeline (parse + extract). Steps 3-6 remain in DC.
