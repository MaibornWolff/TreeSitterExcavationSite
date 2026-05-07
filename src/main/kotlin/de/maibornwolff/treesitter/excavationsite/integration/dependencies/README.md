# Dependencies Feature

## Overview

The dependencies feature extracts structural dependency information from source files: package declarations, imports, class/interface/enum declarations, and the types each declaration uses. This data is consumed by DependaCharta (DC) to build dependency graphs, detect cycles, and assign architectural levels.

Java, Kotlin, C#, C++, Delphi, and Python are implemented. Other languages will be migrated from DC's legacy analyzers over time.

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
    val kind: ImportKind = STANDARD,        // STANDARD / INCLUDE / IMPORT_FROM — see "Import kinds" below
    val isAliased: Boolean = false          // true for Python `from M import X as Y` and `import M as N`; alias name itself is dropped
)

enum class ImportKind {
    STANDARD,    // e.g., Java `import`, Kotlin `import`, C# `using`, C++ `using namespace`/`using X::Y`, Python `import M`
    INCLUDE,     // C++ `#include` — downstream adapter applies path resolution + extension normalization
    IMPORT_FROM  // Python `from M import X` — module path and imported name combined into a single `path`
}

data class Declaration(
    val name: String,                       // "MyService"
    val type: DeclarationType,              // CLASS, INTERFACE, ENUM, RECORD, ANNOTATION, FUNCTION, VARIABLE
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
| `STANDARD` (default) | Java `import`, Kotlin `import`, C# `using`, C++ `using namespace` / `using X::Y`, Python `import os.path` | Consumed as-is — the `path` is already the canonical reference |
| `INCLUDE` | C++ `#include "…"` or `#include <…>` only | Adapter resolves relative paths (`./`, `../`) against the file's physical path and rewrites the final segment's `.` → `_` to match DC's node-naming convention |
| `IMPORT_FROM` | Python `from M import X` only | Adapter synthesizes the `__init__` twin (`["M", "__init__", "X"]`) alongside the canonical entry — see ADR-0004 in `docs/adr/`. Discriminates from plain `import M.X` (which would produce the same `path` shape) so twin synthesis fires only on `from`-imports. |

Without these tags an adapter cannot tell `#include "foo"` (needs path normalization) from `using foo;` (must not be normalized), nor `from M import X` (needs `__init__` twin) from `import M.X` (must not be twinned) — all three produce structurally similar `(path, isWildcard)` shapes. TSE extractors tag the AST source; adapters branch on `kind`.

The `isAliased` flag on `ImportDeclaration` is a separate, orthogonal discriminator used by the Python adapter (ADR-0006). For `from M import X as Y` TSE emits `ImportDeclaration(path=["M","X"], kind=IMPORT_FROM, isAliased=true)`; the alias name `Y` itself is dropped because TSE's Python `UsedTypeExtractor` rewrites use-site occurrences of `Y` to `X` internally (ADR-0002). Only the *fact* of aliasing is preserved, so the DC adapter can match DC main's "pre-load non-aliased twins unconditionally; conditionally pre-load aliased twins on use" behavior.

All non-Python, non-C++ languages leave `kind` at its default (`STANDARD`) and `isAliased` at its default (`false`), so the fields are source- and binary-compatible with existing callers.

### Namespace-prefix handling

`UsedType.namespacePrefix` captures the qualifier segments that appear *before* a type name at the use site. For `A::B::Settings` it is `["A", "B"]`; for an unqualified `Settings` it is `[]`. The exact meaning of "qualifier" is language-specific (namespace scopes in C++, attribute-access chains in Python).

**Per-language interpretation:**

| Language | Typical style | Does `namespacePrefix` carry info? |
|---|---|---|
| Java, Kotlin, C# | Import types at the top, use short names inline. Qualified inline usage (`com.other.Settings s`) is rare and idiomatically discouraged. | No — always empty. The import list already carries the neighborhood info the resolver needs. |
| C++ | `using namespace` is discouraged in headers because it pollutes scope. Writing `cppcheck::Settings` inline is **the normal way** to reference cross-namespace types. | Yes — namespace/scope segments populated on every qualified inline reference. |
| Python | Attribute access on imported modules (`os.path.join`, `httpx.AsyncClient`) is the idiomatic way to reach into an imported package; the segments before the final identifier are the qualifier. | Yes — attribute-access segments populated for every `(attribute)` AST node. `namespacePrefix.isEmpty()` discriminates identifier-stream usages from attribute-stream usages. |
| TypeScript, JavaScript, Go, Vue | Import-aliased usage (`pkg.Type`) is idiomatic but stored as a dotted string in `UsedType.name`; the resolver splits on `.`. | No — always empty. |
| PHP | Has namespaces similar to C++; could opt in if the same resolver gap appears. | Optional; opt-in per extractor. |

**How a DC-side adapter consumes it:** consumption is language-specific.

- **C++** — for every `UsedType` with a non-empty prefix, emit a synthetic `Dependency(Path(namespacePrefix), isWildcard = true)` into the node's `dependencies` set. This mirrors what DC's legacy C++ analyzer did implicitly via `TypeExtractionService.extractTypeWithFoundNamespacesAsDependencies`: for every qualified usage, add a wildcard pointing at the type's neighborhood so the resolver can match the short name against classes declared there. The resolver itself needs no changes — the existing wildcard-matching loop in `Node.resolveTypeImport` already prepends wildcards to type names and looks for project matches.
- **Python** — demultiplex on `namespacePrefix.isEmpty()`: empty → identifier-stream usage, route into `Node.usedTypes`; non-empty → attribute-stream usage, match the joined prefix against the file's `STANDARD`-kind imports and STANDARD-aliases (`import os.path`, `import os.path as op`) and route resolved hits into `Node.dependencies`.

### Primitive and sized-type representation

For languages with built-in primitive types (currently C++; relevant whenever a new language adds support), TSE emits the trimmed source text of the AST node verbatim — no semantic normalization.

| AST shape | Emitted `UsedType.name` |
|---|---|
| `void`, `int`, `bool`, `char`, `double`, `size_t`, `int64_t`, … (`primitive_type`) | `"void"`, `"int"`, `"bool"`, … (the literal source token) |
| `unsigned int`, `signed long` (`sized_type_specifier` with primitive child) | `"unsigned int"`, `"signed long"` (the full source span) |
| Bare `unsigned`, bare `signed` (`sized_type_specifier` without primitive child) | `"unsigned"`, `"signed"` |

**What TSE does not do:** the DC-legacy C++ analyzer normalized bare `unsigned` and `signed` to `"int"` (the C standard's implicit type). TSE deliberately doesn't replicate that — semantic normalization is a language-quirk concern that doesn't belong in a generic AST extractor, and the resolver doesn't need it (no project class is named `int`/`unsigned` so primitives don't produce dependency edges anyway).

**Resolver impact:** primitives don't resolve to project nodes in any realistic codebase, so they're effectively invisible at the `.cg.json` layer. They do appear in `usedTypes` though, which means test assertions over `usedTypes` need to account for them — write `containsExactlyInAnyOrder(...)` with the primitive included, or filter the collection before asserting if the test only cares about user-defined types.

### Generic types representation

`UsedType` stores generic type arguments **nested only** inside the parameterized type, never duplicated as standalone entries. For `List<String>` an extractor produces a single entry:

```kotlin
UsedType(name = "List", genericTypes = listOf(UsedType(name = "String")))
```

— not two parallel entries (`List` plus a flat `String`). The same rule applies recursively: `Map<Key, Container<Item>>` is one nested tree; the inner `Key`, `Container`, and `Item` do not appear as standalone `UsedType`s in the set.

**Why this differs from DC legacy:** some DC legacy analyzers (notably C++) emitted both forms — the wrapped type **and** flat duplicates of its generic arguments — so their `usedTypes` sets were larger and had redundant entries. TSE represents the AST shape directly.

**Why it's not a behavioral regression:** when DC's pipeline turns `usedTypes` into `Dependency` edges, `Node.resolveTypes` walks each `Type` via `Type.containedTypes()`, which recursively descends into `genericTypes` and treats every contained name as a candidate for resolution. The dependency graph is computed from the flattened view; the storage shape is just the AST one. So `List[String]` still resolves to a dep on a project class `String` (if one exists) — flattening is deferred from extract-time to resolve-time.

**What this means for adapters and tests:**

- DC adapters: nothing to do — the existing `Node.resolveTypes` flattening handles both shapes transparently. Don't add a pre-flattening pass; it would create duplicate edges.
- Test assertions: when checking `usedTypes` in a TSE-backed analyzer test, write the assertion against the nested shape (`Type.generic("List", listOf(Type.simple("String")))`), not against flat-duplicate expectations carried over from DC legacy.


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
- **ImportExtractor** — extracts imports as `List<ImportDeclaration>`
- **DeclarationExtractor** — finds class/interface/enum declarations, delegates to UsedTypeExtractor for each
- **UsedTypeExtractor** — extracts all types used within a declaration

Optional:
- **PackageExtractor** — extracts the package/module path as `List<String>`. Omit when the language has no in-source package declaration (e.g. Python — see ADR-0001); `LanguageDependencyMapping.extractPackagePath` defaults to empty.

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
| **Python** | identifier stream, attribute stream — `LinkedHashSet` preserves source order within each. Adapter then re-orders into a four-category sequence at `Node.dependencies` build time: (1) non-aliased FROM-import twins in source order, (2) wildcard FROM-import twins in source order, (3) aliased FROM-import twins in identifier-stream order (conditional on use), (4) STANDARD-import + STANDARD-alias attribute matches in attribute-stream order. | `PythonAnalyzer.kt` (legacy queries: `PythonTypeIdentifierQuery` + `PythonTypeAttributeQuery`) |
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
