# Python relative imports use leading-dots encoding in `ImportDeclaration.path`

For `from .foo import X`, TSE's Python `ImportExtractor` emits `ImportDeclaration(path = [".", "foo", "X"])`; for `from ..foo.bar import Y`, `path = ["..", "foo", "bar", "Y"]`; for `from . import X`, `path = [".", "X"]`. The leading dot-only segment signals "this is a relative import" — Python module names cannot consist solely of dots, so `path[0].all { it == '.' }` is an unambiguous discriminator.

The DC `PythonAnalyzer` adapter detects the dot-only first segment, computes `modulePath.dropLast(prefix.length) + path.drop(1)` to produce the absolute path, and emits the resolved `Dependency` (plus DC legacy's `__init__` synthetic twin). Filesystem state stays in DC, per ADR-0001.

## Considered Options

- **A. (Chosen) Leading-dots segment in the existing `path` list.** Self-describing, no new fields, piggybacks on field every language already uses, mirrors the C++ `INCLUDE` precedent of "TSE tags the AST source; DC adapter resolves the path."
- **B. Add `relativePrefixDepth: Int = 0` to `ImportDeclaration`.** Rejected: would also need `tailPath: List<String>` (depth alone loses the segments after the dots), at which point we've reinvented `path` with a prefix. Adds two fields exactly one language populates.
- **C. Add `ImportKind.RELATIVE`.** Rejected: insufficient on its own — still needs depth + tail-path encoding somewhere.
- **D. Resolve relative imports inside TSE by extending the API with `filePath`.** Rejected for the same reason as ADR-0001 Option A.

## Consequences

- The DC `PythonAnalyzer` adapter must check `path[0].all { it == '.' }` before treating any Python `ImportDeclaration` as fully-qualified.
- A relative import that points above the project root (e.g., `from ..foo` from a top-level module) will produce a path that, after `dropLast`, becomes empty or negative-length. DC legacy silently dropped these (`extractModuleName` returns `""`); the adapter must mirror this — no `Dependency` emitted.
- Future alias-heavy languages with relative imports (TypeScript, JavaScript) can reuse the same encoding.
