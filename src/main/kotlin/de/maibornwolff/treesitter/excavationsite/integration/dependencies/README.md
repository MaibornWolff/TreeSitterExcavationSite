# Dependencies Feature

## Overview

The dependencies feature extracts structural dependency information from source files: package declarations, imports, class/interface/enum declarations, and the types each declaration uses. This data is consumed by DependaCharta (DC) to build dependency graphs, detect cycles, and assign architectural levels.

Java, Kotlin, and C# are implemented. Other languages will be migrated from DC's legacy analyzers over time.

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
          2. extractImports()      → [ImportDeclaration(path, isWildcard, namespacePath, kind)]
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
    val isWildcard: Boolean,                // true for "import java.util.*"
    val namespacePath: List<String> = [],   // namespace scope: [] = global, ["My", "Namespace"] = scoped to that namespace
    val kind: ImportKind = STANDARD         // INCLUDE for C++ #include; STANDARD for everything else (Java/Kotlin/C#/C++ `using`)
)

enum class ImportKind {
    STANDARD,  // e.g., Java `import`, Kotlin `import`, C# `using`, C++ `using namespace`/`using X::Y`
    INCLUDE    // C++ `#include` — downstream adapter applies path resolution + extension normalization
}

data class Declaration(
    val name: String,                       // "MyService"
    val type: DeclarationType,              // CLASS, INTERFACE, ENUM, RECORD, ANNOTATION
    val usedTypes: Set<UsedType>,           // types referenced inside this declaration
    val parentPath: List<String> = []       // namespace/package path: ["com", "example"]
)

data class UsedType(
    val name: String,                       // "List"
    val genericTypes: List<UsedType> = [],  // [UsedType("String")] for List<String>
    val namespacePrefix: List<String> = [], // ["A", "B"] for A::B::Settings; [] for unqualified
)
```

### Import kinds

`ImportDeclaration.kind` distinguishes imports that share the same shape but require different downstream handling:

| Kind | Source | Adapter handling |
|---|---|---|
| `STANDARD` (default) | Java `import`, Kotlin `import`, C# `using`, C++ `using namespace` / `using X::Y` | Consumed as-is — the `path` is already the canonical reference |
| `INCLUDE` | C++ `#include "…"` or `#include <…>` only | Adapter resolves relative paths (`./`, `../`) against the file's physical path and rewrites the final segment's `.` → `_` to match DC's node-naming convention |

Without this tag, a C++ DC adapter cannot tell `#include "foo"` (needs path normalization) from `using foo;` (must not be normalized) — both produce the same `(path, isWildcard, namespacePath)` triple. TSE extractors tag the AST source; adapters branch on `kind`.

All non-C++ languages leave the field at its default (`STANDARD`), so adding the field is source- and binary-compatible with existing callers.

### Namespace-prefix handling

`UsedType.namespacePrefix` captures the scope segments that appear *before* a type name at the use site. For `A::B::Settings` it is `["A", "B"]`; for an unqualified `Settings` it is `[]`.

**Why it exists — and why it's essentially a C++ concern:**

| Language | Typical style | Does `namespacePrefix` carry info? |
|---|---|---|
| Java, Kotlin, C# | Import types at the top, use short names inline. Qualified inline usage (`com.other.Settings s`) is rare and idiomatically discouraged. | No — always empty. The import list already carries the neighborhood info the resolver needs. |
| C++ | `using namespace` is discouraged in headers because it pollutes scope. Writing `cppcheck::Settings` inline is **the normal way** to reference cross-namespace types. | Yes — populated on every qualified inline reference. |
| TypeScript, JavaScript, Python, Go, Vue | Import-aliased usage (`pkg.Type`) is idiomatic but stored as a dotted string in `UsedType.name`; the resolver splits on `.`. | No — always empty. |
| PHP | Has namespaces similar to C++; could opt in if the same resolver gap appears. | Optional; opt-in per extractor. |

**How a DC-side adapter consumes it:** when building a node's `dependencies` set, emit a synthetic `Dependency(Path(namespacePrefix), isWildcard = true)` per `UsedType` that has a non-empty prefix. This mirrors what DC's legacy C++ analyzer did implicitly via `TypeExtractionService.extractTypeWithFoundNamespacesAsDependencies`: for every qualified usage, add a wildcard pointing at the type's neighborhood so the resolver can match the short name against classes declared there.

The resolver itself needs no changes — the existing wildcard-matching loop in `Node.resolveTypeImport` already prepends wildcards to type names and looks for project matches.


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
| **Delphi** | inheritance, parameters, returnTypes, fieldTypes, propertyTypes, constTypes, variableTypes, constructorCalls, methodCalls, castTypes, attributeTypes, genericConstraintTypes | N/A — TSE-native, no DC legacy |

## Namespace models: single-namespace vs multi-namespace languages

TSE's `DependencyResult` exposes two namespace-related fields: file-level `packagePath` (one list per file) and per-declaration `Declaration.parentPath`. How the two relate depends on the language, because DependaCharta (the sole consumer of the dependencies output) has two distinct models across its analyzers.

### Class 1 — single-namespace languages

One namespace per file. `packagePath` is authoritative; `parentPath` carries only in-file nesting (e.g., Kotlin inner classes) or is unused.

| Language | `packagePath` source | `parentPath` content |
|---|---|---|
| Java | `package_declaration` | empty |
| Kotlin | `package_header` | parent class chain only (package not duplicated) |
| Go | package declaration or file path | empty (planned) |
| PHP | `namespace` declaration or file path | empty (planned) |
| Python / JavaScript / Vue | file path | empty (planned) |
| Delphi | `unit` declaration | empty (top-level types only in v1) |

DC adapters for Class 1 build the node path as `packagePath + parentPath + name` (Kotlin) or `packagePath + name` (Java). The self-wildcard import is emitted once per file from `packagePath`.

### Class 2 — multi-namespace languages

Multiple top-level namespaces per file are legal, and nested namespaces scope their inner members. A single file-level `packagePath` cannot represent this — each declaration needs its own namespace chain.

| Language | `packagePath` source | `parentPath` content |
|---|---|---|
| C# | first declared namespace (informational only) | full namespace chain + parent class chain |
| C++ (planned) | first declared namespace (informational only) | full namespace chain + parent class chain |
| TypeScript ambient modules (planned) | file path | ambient module path per declaration |

DC's C# adapter (`CSharpAnalyzer` on `feat/tse-csharp-integration`) **ignores `result.packagePath`** and derives everything from `declaration.parentPath`: the node's `pathWithName` is `parentPath + name`, scoped imports match on `parentPath`, and the self-wildcard import is emitted **per declaration** from its own `parentPath`. C++ will follow the same pattern when migrated.

### Guidance when adding a new language

- If the language has one namespace per file (Class 1), populate `packagePath`; leave `parentPath` for in-file class nesting only.
- If the language allows multiple or nested namespaces per file (Class 2), populate `parentPath` with the full namespace-plus-parent-class chain. `packagePath` may still be filled in as a best-effort first-namespace hint, but consumers should treat it as informational.
- The DC adapter for a Class 2 language should read `declaration.parentPath` rather than `result.packagePath` for path construction and for emitting the per-declaration self-wildcard import.

## How DC consumes TSE output

DC's `DependencyResolverService` takes the `DependencyResult` and:

1. Builds a project dictionary mapping type names to full paths
2. Calls `Node.resolveTypes()` on each node — resolves used type names to actual project nodes via imports
3. Classifies resolved dependencies as internal vs external
4. Feeds resolved nodes into `CycleAnalyzer` (Tarjan's SCC) for cycle detection
5. Feeds the graph into `Levelizer` for architectural level assignment
6. Exports as cc.json

TSE's job is steps 1-2 of the legacy pipeline (parse + extract). Steps 3-6 remain in DC.
