# Python `packagePath` is derived by the DC adapter, not by TSE

For Java, Kotlin, C#, and C++ the package/namespace is an in-source token, so TSE's `PackageExtractor` reads it from the AST. Python has no such token — the module path is the file's location on disk. Rather than extend TSE's API to accept a file path (which would force every other language's `LanguageDependencyMapping` lambda to gain an unused parameter), TSE's Python `PackageExtractor` returns an empty `packagePath` and the DC `PythonAnalyzer` adapter derives the module path from `FileInfo.physicalPath` itself.

This means TSE's `DependencyResult` for a Python file is intentionally incomplete in isolation: the package path is filesystem state, and TSE deliberately doesn't ingest filesystem state. The `__init__.py` special-case logic (synthesizing one Node per `import_from` when the file is `__init__`) lives in the DC adapter for the same reason — it is keyed off the file path's last segment.

## Considered Options

- **A. Extend TSE's API with an optional `filePath` parameter.** Rejected: pollutes the `LanguageDependencyMapping` lambda shape for one language's quirk and breaks the "TSE = AST work, DC = filesystem work" separation that already underpins the C++ `ImportKind.INCLUDE` precedent.
- **B. (Chosen) Empty `packagePath` from TSE; DC adapter fills it in.** Mirrors how the C++ adapter resolves `#include` paths against `FileInfo.physicalPath` after TSE tags the AST source.
- **C. Treat Python as a Class 2 namespace language and push the path into `Declaration.parentPath`.** Rejected: Python is conceptually one module per file (Class 1); the same value would be repeated on every declaration, which is wasteful and misrepresents the model.

## Consequences

- TSE-only consumers cannot reconstruct a Python file's module path from `DependencyResult` alone. Today the only consumer is DC, which already holds the file path; if a second consumer ever lands, they will need to provide their own equivalent.
- `dc-compare` parity is unaffected — DC's adapter produces the same `Path` it always did.
