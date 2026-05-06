# TreeSitterExcavationSite

A standalone Kotlin library that runs tree-sitter over source files and emits structural data — metrics, extracted text, and dependency information — for downstream consumers (primarily DependaCharta and CodeCharta).

## Language

**Package path**:
The list of segments that locate a source file inside its project (e.g. `["com", "example", "service"]`). For Java, Kotlin, C#, and C++ this is read from an in-source `package` / `namespace` token; for Python it is the file's path *relative to the project source root* with `.py` stripped (e.g. `src/flask/app.py` → `["flask", "app"]`), derived by the consumer (DependaCharta), not by TSE.
_Avoid_: module path (DC's term in Python code — same concept), namespace path (overloaded with `UsedType.namespacePrefix`).

**Declaration**:
A top-level structural item that becomes a node in the dependency graph: a class/interface/enum/record/annotation in C-family languages, plus top-level functions and module-level variable assignments in Python. Nested declarations are extracted recursively in languages where DC's legacy analyzer did so (Java, Kotlin, C++); in Python only direct children of `module` are extracted.
_Avoid_: definition (DC's Python query name — same concept), node (overloaded with tree-sitter AST node and DC's `Node` type).

**Used type**:
A type referenced inside a declaration's body. Stored verbatim from source — no semantic normalization (primitives, implicit ints, etc. are preserved as written).
_Avoid_: type reference, dependency (the latter is the post-resolution edge in DC, not the pre-resolution name).

**Namespace model — Class 1 vs Class 2**:
**Class 1** = one namespace per file (`packagePath` is authoritative): Java, Kotlin, Go, PHP, Python, JavaScript, Vue, Delphi.
**Class 2** = multiple/nested namespaces per file (`Declaration.parentPath` is authoritative; `packagePath` is informational at best): C#, C++, TypeScript ambient modules.

## Relationships

- A **Declaration** contains zero or more **Used types** in its body
- A **Package path** locates the file containing the **Declaration**s; in Class 2 languages each **Declaration** carries its own namespace chain via `parentPath`
- DC's downstream resolver turns each **Used type** into zero or more dependency edges by matching its name against project **Declaration**s (using imports as scope hints)

## Flagged ambiguities

- "module path" (DC vocabulary) and "package path" (TSE field name) refer to the same concept — canonical term: **package path**. This came up in the Python dependency migration where DC's `PythonAnalyzer` uses `modulePath` for what TSE calls `packagePath`.
