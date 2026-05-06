# Python aliased FROM-imports carry an `isAliased: Boolean` flag on `ImportDeclaration`

Revises ADR-0002.

For `from M import X as Y`, TSE's Python `ImportExtractor` emits `ImportDeclaration(path = ["M", "X"], kind = IMPORT_FROM, isAliased = true)`. For non-aliased `from M import X`, `isAliased = false` (the field's default). The alias name `Y` is still dropped at extraction (per ADR-0002 — TSE's `UsedTypeExtractor` rewrites use-site occurrences of `Y` to `X` internally), but the *fact* that the import was aliased is preserved as a Boolean discriminator the DC adapter consults during pre-loading.

## Why this revision was needed

ADR-0002 dropped the alias name from `ImportDeclaration` and let TSE's `UsedTypeExtractor` rewrite use sites. ADR-0004 separately decided that the DC adapter pre-loads `IMPORT_FROM` twins onto every `Declaration`'s dependencies set (mirroring DC's `mutableImportFromDependencies` initialization).

Combined, these two ADRs created a divergence from DC main: DC pre-loads non-aliased FROM-import twins on every Node but only adds aliased-FROM-import twins to a Node when the alias is actually used at the use site (`PythonAnalyzer.checkAliasImportFrom` lines 148-162). Without an aliasing discriminator on `ImportDeclaration`, the adapter treats aliased and non-aliased FROM-imports identically and pre-loads both — over-counting twins on declarations that don't use the alias.

For aliases targeting standard-library modules (`from os import path as p`), the over-count is harmless because the resolver finds no project node at `["os", "path"]` and emits no edge. For aliases targeting project-internal modules (`from .helpers import build_url as build`), the over-count produces **false dependency edges** in declarations that don't actually use the alias.

ADR-0002 explicitly anticipated this revisit:

> TSE's `ImportDeclaration` is lossy for Python aliases (the alias name `Y` is dropped). No current consumer needs it; if one ever does, this ADR will need to be revisited.

The DC adapter is now that consumer.

## DC adapter algorithm with this flag

```kotlin
val (aliasedFromImports, regularFromImports) = imports
    .filter { it.kind == ImportKind.IMPORT_FROM }
    .partition { it.isAliased }
val standardImportPaths = imports
    .filter { it.kind == ImportKind.STANDARD }
    .map { it.path.joinToString(".") }

declaration.dependencies = if (declaration.type == DeclarationType.VARIABLE) {
    emptySet()                                                         // VARIABLE Nodes are isolated leaves in DC
} else {
    regularFromImports.flatMap { twin(it) } +                          // pre-loaded for every CLASS/FUNCTION Declaration
    aliasedFromImports.flatMap { imp ->
        if (declaration.usedTypes.any { it.name == imp.path.last() })  // conditionally pre-loaded
            twin(imp) else emptyList()
    } +
    attributeMatches(declaration.usedTypes, standardImportPaths)       // matches usedTypes with non-empty namespacePrefix
}
```

`twin(imp)` synthesizes the canonical and `__init__` Dependencies per ADR-0004. `attributeMatches` filters `usedTypes` to those with non-empty `namespacePrefix` (per ADR-0005), joins the prefix with `"."`, and emits a twin pair per match against `standardImportPaths`. **No separate `aliasedStandardImports` parameter is needed**: per ADR-0002 TSE's `UsedTypeExtractor` rewrites `import os.path as op; op.join` → `UsedType("join", ["os","path"])` at the use site, so the adapter sees the post-rewrite prefix and matches directly against the canonical (non-aliased) `standardImportPaths` entry. The `isAliased` flag is only consulted on `IMPORT_FROM` kinds. The `VARIABLE` exception mirrors DC's early return at `PythonAnalyzer.kt:94-103`, which discards the pre-loaded `mutableImportFromDependencies` for top-level identifier captures and returns `dependencies = setOf(), usedTypes = setOf()`.

## Considered Options

- **A. Accept the divergence; document as accepted deviation.** Rejected: real false edges in alias-heavy project-internal code (test suites, internal package re-exports). Could meaningfully tank `dc-compare` parity for projects using `from .module import Symbol as S` patterns.
- **B. (Chosen) Add `isAliased: Boolean = false` to `ImportDeclaration`.** Preserves the discriminator the adapter needs while keeping ADR-0002's "TSE handles alias resolution" intent. The alias *name* stays dropped; only the *fact-of-aliasing* is preserved. Default `false` means no breakage for other languages.
- **C. Full ADR-0002 reversal — add `alias: String?` to `ImportDeclaration`, move alias resolution to DC adapter.** Rejected: pushes Python-quirk logic into the DC adapter (the opposite of the migration goal). ADR-0002's reasoning still holds for resolution; only the pre-loading discriminator was missing.
- **D. Expose alias map as a separate `aliasedFromImports: Map<String, List<String>>` field on `DependencyResult`.** Rejected: bloats `DependencyResult` with a Python-specific field; the Boolean flag is sufficient and lives on the type that already exists.

## Consequences

- **Adapter pre-loading splits into two passes.** Non-aliased FROM-imports are pre-loaded unconditionally; aliased FROM-imports are conditionally pre-loaded based on a `usedTypes` scan for the resolved tail name. Mirrors DC's `importsFrom` vs `aliasedImportsFrom` distinction at lines 33–35 of `PythonAnalyzer`.
- **Name-collision edge case.** If a file has both `from os import path` (non-aliased) and `from posixpath import path as p` (aliased to the same tail name `path`), the adapter cannot tell which use site of `path` corresponds to which import without re-introducing alias-name tracking. Both twin pairs are added in this case — over-count in the same direction as DC's behavior would be (DC also adds both because both source statements are visible to its respective passes). Acceptable for parity, documented here so it's not surprising.
- **`isAliased` semantics generalize.** The flag is named for the migration's needs but represents "this import has a discriminator that affects adapter pre-loading." Future migrations of TypeScript (`import { X as Y } from 'M'`) and JavaScript can reuse the field if they encounter the same pre-loading-vs-conditional-twin split.
- **ADR-0002 is partially superseded** for the case it explicitly anticipated. ADR-0002's alias-resolution-in-TSE design is preserved; only the `ImportDeclaration` field set is amended.
- **Default value `false` keeps the field non-breaking.** Other languages' extractors don't need to populate it. Java / Kotlin / C# / C++ continue working unchanged.
- **`VARIABLE` declarations are isolated leaves.** The pre-loading algorithm above explicitly skips `DeclarationType.VARIABLE`. This mirrors DC's `PythonAnalyzer.kt:94-103` early return, which discards `mutableImportFromDependencies` for top-level identifier captures (the LHS of `MY_CONST = ...` assignments) and returns empty `dependencies` and `usedTypes`. The captured identifier becomes a `VARIABLE` Node in the project graph with no edges in either direction; the RHS expression is never extracted. Adapter implementations must replicate this exception or every variable Declaration will gain pre-loaded import edges DC never emitted, producing `dc-compare` drift on every file with module-level constants.
- **Aliased-FROM-import-with-attribute-access** (e.g., `from os import path as p; p.join(...)`): adapter sees `UsedType("join", namespacePrefix=["path"])` (TSE rewrites the alias prefix per ADR-0002). Because `path` doesn't match a STANDARD import string (no `import os.path` in this file), the attribute match in `buildImportDependencies` won't fire. The dependency comes solely from the conditional pre-loading via `isAliased=true` — which also fires because `path` (the tail) appears in `usedTypes`. Single twin pair added; no double-count.
