# Python `__init__.py` twin synthesis lives in the DC adapter; `ImportKind.IMPORT_FROM` discriminator added

For every `from M import X`, DC legacy emits two `Dependency` paths into `Node.dependencies`: the canonical `["M", "X"]` and the synthetic twin `["M", "__init__", "X"]`. The twin exists because Python packages may re-export a name through `__init__.py`, and DC's resolver picks whichever path matches a real node in the project graph. The same twinning applies to `from M import *` (wildcard) and to aliased `from`-imports resolved at use site.

By contrast, `import X.Y` produces **zero** Dependencies directly in DC. It is stored as a `List<String>` lookup table and only emits a Dependency when an attribute access (e.g., `X.Y.something`) at a use site matches a prefix in that table — at which point the matching attribute is twinned (`["X","Y","something"]` + `["X","Y","__init__","something"]`).

TSE's Python `ImportExtractor` emits one canonical `ImportDeclaration` per imported name (no twin), tagged with `ImportKind.IMPORT_FROM` for `from M import X` / `from M import *` and `ImportKind.STANDARD` for `import X.Y`. The DC `PythonAnalyzer` adapter:

1. For `IMPORT_FROM` entries — synthesizes the `__init__` twin and adds both paths to `Node.dependencies`.
2. For `STANDARD` entries — does **not** add to `Node.dependencies`; instead, uses them as a lookup table to match attribute access surfaced by TSE's `UsedTypeExtractor` (Q7), then twins each match.

Multi-name imports (`from M import X, Y, Z` and `import X, Y`) produce one `ImportDeclaration` per name; wildcards collapse to a single `ImportDeclaration` per statement with `isWildcard = true`. Statements whose module name cannot be resolved (relative-above-root or malformed) emit zero `ImportDeclaration`s, mirroring DC's `extractModuleName == ""` skip.

This keeps Python's filesystem convention (`__init__.py` as package init) out of TSE, consistent with ADR-0001, while preserving enough syntactic information for the adapter to faithfully reproduce DC's three-bucket algorithm.

## Considered Options

- **A. (Chosen) TSE emits canonical `ImportDeclaration` paths tagged with `ImportKind`; adapter twins for `IMPORT_FROM` and uses `STANDARD` as a lookup table.** Mirrors how the C++ adapter resolves `INCLUDE` paths after TSE tags the AST source. Preserves the clean public API: a TSE-only consumer sees `import os.path` as `ImportDeclaration(["os","path"], kind=STANDARD)` rather than nothing at all.
- **B. TSE emits both twin paths verbatim (no adapter synthesis).** Rejected: encodes the `__init__.py` filesystem convention into TSE, contradicting ADR-0001. Doubles the size of `imports` for a Python-only quirk and pushes filesystem reasoning into the wrong layer.
- **C. Mirror DC's three-bucket structure on `DependencyResult` (separate `imports`, `importHints`, `aliasedImports` fields).** Rejected: bloats the public type for a Python-only quirk; other languages would carry empty fields. The kind enum gives the same discrimination at lower public-API cost.
- **D. Heuristic on `path.size` to discriminate `from`-imports from `import X.Y`.** Rejected: both produce `path.size == 2`. Would silently mis-twin `import os.path` into `["os", "__init__", "path"]`, polluting the dependency graph.
- **E. Move attribute matching into TSE's `UsedTypeExtractor` so `STANDARD` imports need not appear in `imports` at all.** Rejected: leaks Python-specific lookup logic (`import X.Y` ↔ attribute-prefix matching) into a generic extractor. Also hides the import from non-DC consumers, who would reasonably expect it in `result.imports`.

## Consequences

- **`ImportKind` must be set off the AST node type, not heuristically.** TSE's `ImportExtractor` maps `import_from_statement` → `IMPORT_FROM` and `import_statement` → `STANDARD`. Mismatch silently changes the dependency graph (mis-twins one direction, drops twins the other).
- **Adapter must implement two distinct processing paths.** `IMPORT_FROM` entries flow to `Node.dependencies` with the twin; `STANDARD` entries flow to a lookup table for attribute matching. Wildcards behave like `IMPORT_FROM` (twinned); the adapter checks `kind` regardless of `isWildcard`.
- **Parity for `import X.Y` depends on Q7.** DC produces no Dependency for `import os.path` until an attribute access like `os.path.join` matches the prefix. TSE's `UsedTypeExtractor` must surface enough information about attribute chains for the adapter to reconstruct that match. If the extractor emits only the leftmost identifier (`UsedType("os")`), every attribute-style dependency is lost. Concretely: the extractor must either expose the full attribute path (`os.path.join`) or expose the longest-prefix-then-tail split that DC's `attributeQuery` performs. Q7 is where this gets resolved.
- **Aliased-import twins are synthesized at use site by the adapter, not by TSE.** Per ADR-0002, TSE's `UsedTypeExtractor` resolves the alias and emits the real name. When the adapter sees a `UsedType` whose name matches a previously-aliased `from`-import, it must add `Dependency(aliasPath)` and `Dependency(aliasPath.dropLast(1) + ["__init__", aliasedType])` to `Node.dependencies`. This mirrors DC's `checkAliasImportFrom` mutation pattern (`PythonImportQuery.kt:148-162`).
- **Extractor scope must not exceed DC's `attributeQuery` scope.** DC matches `(attribute)` AST nodes for the lookup-table comparison. If TSE's `UsedTypeExtractor` surfaces attribute chains from contexts DC's TSQuery missed (annotations, comprehensions, lambdas), the adapter will emit additional twin pairs DC never produced — a `dc-compare` divergence in the "more dependencies" direction.
- **Relative imports compose with `IMPORT_FROM`.** `from .foo import X` (ADR-0003) produces `ImportDeclaration(path=[".", "foo", "X"], kind=IMPORT_FROM)`. The adapter resolves the relative prefix first, then synthesizes the `__init__` twin from the resolved absolute path.
- **Empty-module-name skip is a TSE-side responsibility.** When the module name cannot be resolved (relative-above-root, malformed `import_from_statement`), TSE emits zero `ImportDeclaration`s rather than a sentinel. The adapter never sees the broken statement.
- **`ImportKind.IMPORT_FROM` is generic enough to reuse.** The variant is named after the AST construct (`import_from_statement`), but the same shape exists in JavaScript/TypeScript ES6 (`import { X } from 'M'`) and PHP (`use M\X`). Future Class 1 migrations with from-style imports can reuse `IMPORT_FROM` rather than introducing per-language variants.
