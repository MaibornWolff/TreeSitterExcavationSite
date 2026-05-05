# Python alias resolution lives inside TSE's `UsedTypeExtractor`

> **Partially revised by ADR-0006.** The alias *name* is still dropped (per this ADR), but `ImportDeclaration` now carries an `isAliased: Boolean` flag so the DC adapter can replicate DC's "pre-load non-aliased FROM-imports unconditionally; conditionally pre-load aliased FROM-imports on use" behavior. See ADR-0006 for context.

For Python `import X as Y` and `from M import X as Y`, TSE's Python `UsedTypeExtractor` resolves aliases internally: the alias map is built from the AST during import extraction, and use-site occurrences of `Y` are rewritten to the original tail name before being emitted as `UsedType`s. `ImportDeclaration` itself carries no alias *name* — the public output's `path` field is identical to a non-aliased file with the same imports.

This mirrors DC legacy semantics (`PythonAnalyzer.checkAliasImportFrom`), which also resolves at the use site. The `__init__.py` synthetic-dependency duplication that DC legacy emits alongside resolved aliases stays in the DC adapter (filesystem-state concern, per ADR-0001).

## Considered Options

- **A. (Chosen) Resolve aliases inside `UsedTypeExtractor`.** `usedTypes` set is byte-for-byte equivalent to DC main; `dc-compare` is unaffected. Keeps Python-quirk logic inside the Python language module, not in the DC adapter.
- **B. Add `alias: String?` to `ImportDeclaration`; resolve in DC.** Rejected: pushes alias resolution onto the DC adapter (the opposite of the migration goal), and adds a field that only one language populates.
- **C. Drop alias-only imports entirely.** Rejected: would diverge from DC main's `imports` list, which the resolver consults for scope hints — would tank dc-compare on alias-heavy files.

## Consequences

- TSE's `ImportDeclaration` is lossy for Python aliases (the alias name `Y` is dropped). No current consumer needs it; if one ever does, this ADR will need to be revisited.
- Future Python migrations of similar alias-heavy languages (TypeScript, JavaScript) should follow the same pattern unless there's a reason their consumers need the alias preserved.
