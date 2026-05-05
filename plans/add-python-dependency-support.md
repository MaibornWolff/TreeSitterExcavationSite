---
name: add-python-dependency-support
issue: TBD
state: progress
version: 2
tse_branch: feat/python-dependency-support
dc_branch: TBD
---

## Goal

Migrate DependaCharta's legacy Python dependency analyzer to TSE. Python is a Class 1 (single-namespace-per-file) language with several quirks not seen in prior migrations: no in-source package declaration (file path IS the module path), `__init__.py` synthetic-twin imports, alias resolution at the use site, relative imports requiring filesystem context, and module-level functions/variables emitted as Declarations alongside classes.

## Grill state

This plan was started via `/grill-with-docs`. Decisions locked in so far are recorded as ADRs in `docs/adr/`. Q1–Q11 resolved. Resume at Q12 (reference corpus for dc-compare) — last grill branch.

### Decisions locked in

- **ADR-0001** — Python `packagePath` is empty from TSE; DC adapter derives `modulePath` from `FileInfo.physicalPath`. Filesystem state stays out of TSE.
- **Q2 / Option α (no ADR)** — Extend `DeclarationType` with `FUNCTION` and `VARIABLE` (bare names, not `MODULE_FUNCTION` etc.). Python emits all three top-level shapes (class / function / variable assignment), only direct children of `module` — no nested extraction.
- **ADR-0002** — Alias resolution lives inside TSE's Python `UsedTypeExtractor`. `ImportDeclaration` carries no alias field. The `__init__` synthetic-dependency duplication for aliased imports stays in the DC adapter.
- **ADR-0003** — Relative imports use leading-dots encoding in `ImportDeclaration.path`: `from .foo import X` → `path = [".", "foo", "X"]`. DC adapter detects `path[0].all { it == '.' }`, computes `modulePath.dropLast(prefix.length) + path.drop(1)`, then synthesizes the `__init__` twin.
- **ADR-0004** — TSE emits canonical `ImportDeclaration` paths tagged with `ImportKind`; DC adapter synthesizes the `__init__` twin for `IMPORT_FROM`/wildcard entries and uses `STANDARD` entries (`import X.Y`) as a lookup table for attribute matching. Multi-name imports → one `ImportDeclaration` per name; unresolvable module name → zero `ImportDeclaration`s. New enum variant `ImportKind.IMPORT_FROM` discriminates `from`-imports from plain `import X.Y`.
- **ADR-0005** — Python `UsedTypeExtractor` runs two passes: every `(identifier)` becomes `UsedType(name=id, namespacePrefix=[])`; every `(attribute)` becomes `UsedType(name=lastSegment, namespacePrefix=allButLast)`. Adapter demultiplexes on `namespacePrefix.isEmpty()` — empty → identifier stream → `Node.usedTypes`; non-empty → attribute stream → matched against `STANDARD` imports/aliases → `Node.dependencies`. Expands the documented purpose of `namespacePrefix` to "qualifier segments at the use site, interpretation per language."
- **Q6 / no ADR** — Identifier stream mirrors DC's `(identifier) @identifier` query verbatim, no extractor-time filtering. Per `dependency-migration.md` "match DC main's output" rule. Filtering (uppercase-first heuristic, AST-context exclusion) was rejected because lowercase imports — decorators, type aliases, function imports — would be silently dropped, losing real dependency edges. Output noise is harmless: `Set` deduplication + resolver-drops-no-matches.
- **Q8 / no ADR** — `DeclarationExtractor` unwraps `decorated_definition` via `getChildByFieldName("definition")` for `Declaration.name` and `Declaration.type` (CLASS/FUNCTION). `UsedTypeExtractor` runs over the **outer** decorated subtree so decorator identifiers (`@dataclass`, `@app.route`, etc.) contribute to `usedTypes`. Stacked decorators handled naturally — outer subtree contains all decorator nodes.
- **ADR-0006** — Adds `isAliased: Boolean = false` to `ImportDeclaration`. Partially revises ADR-0002. For `from M import X as Y`, TSE emits `ImportDeclaration(path=["M","X"], kind=IMPORT_FROM, isAliased=true)`; the alias *name* `Y` stays dropped (TSE rewrites at use site per ADR-0002), but the *fact-of-aliasing* lets the DC adapter pre-load non-aliased FROM-imports unconditionally and conditionally pre-load aliased ones based on `usedTypes` scan. Closes the false-edge divergence ADR-0002 + ADR-0004 created for project-internal aliased imports.
- **Q9 confirmations (no ADR)** — Adapter pre-loads non-aliased `IMPORT_FROM` twins on every CLASS/FUNCTION Declaration's dependencies (mirrors DC's `mutableImportFromDependencies` initialization). Adapter detects `modulePath.last() == "__init__"` from `FileInfo.physicalPath` and creates one Node per re-export (filesystem-state in adapter per ADR-0001). STANDARD-import attribute matching uses `usedTypes` with non-empty `namespacePrefix` per ADR-0005.
- **Q10 / no ADR** — Module-level VARIABLE Declarations have empty `usedTypes` and empty `dependencies` (no pre-loaded import twins). Mirrors DC's early return at `PythonAnalyzer.kt:94-103`. Only simple `IDENTIFIER = ...` form captured — tuple unpacking, subscripts, attribute LHS, augmented and annotated assignments are all skipped. The RHS expression is never extracted. Adapter must check `declaration.type == VARIABLE` and skip pre-loading (codified in ADR-0006's algorithm).
- **Q11 / no ADR** — Mirror DC's four-category concatenation order in adapter's `Node.dependencies`: (1) non-aliased FROM-import twins in source order, (2) wildcard FROM-import twins in source order, (3) aliased FROM-import twins in identifier-stream order (conditional on use), (4) STANDARD-import + STANDARD-alias attribute matches in attribute-stream order. TSE's `PythonUsedTypeExtractor` builds `(identifierStream + attributeStream).toSet()` mirroring Java's pattern at `JavaUsedTypeExtractor.kt:63-66`. `LinkedHashSet` preserves insertion order. Pin within-stream order with an extractor unit test. Update `integration/dependencies/README.md` to correct the "imports only, no concatenation" claim.

### Pitfalls flagged in ADR-0004 / ADR-0005 (re-surface during Q6 and implementation)

- `ImportKind` must come straight from the AST node type — heuristic on `path.size` would mis-twin `import X.Y`.
- `import X.Y` parity (ADR-0004 → ADR-0005): `UsedTypeExtractor` must populate `namespacePrefix` for every `(attribute)` AST node so the adapter can match the joined prefix against `STANDARD` imports.
- Aliased-import twins are synthesized in the adapter at use site (per ADR-0002 follow-through). Adapter must mutate `Node.dependencies` when a `UsedType` resolves to a previously-aliased name.
- TSE's `UsedTypeExtractor` scope must not exceed DC's `attributeQuery` scope (annotations, comprehensions, lambdas), or extra twin pairs will appear in the dc-compare delta.
- `namespacePrefix.isEmpty()` is the adapter's structural discriminator between identifier stream and attribute stream. Future changes to TSE's Python `UsedTypeExtractor` must not populate `namespacePrefix` for non-attribute reasons — pin this with an extractor unit test.

### Documentation seeded

- `CONTEXT.md` — created at repo root with terms: **package path** (canonical, vs DC's `modulePath`), **Declaration**, **Used type**, **Class 1 / Class 2 namespace models**.

## Pending — Question 12 (reference corpus for dc-compare)

- **Q12 — Reference corpus.** Pick a medium-sized open-source Python repo for `dc-compare` against DC main. Per `dependency-migration.md`: must have packages (not flat), use both relative and absolute imports, and ideally exercise the quirks (aliased FROM-imports, `__init__.py` re-exports, decorators, attribute access on imported modules). Candidates: `requests`, `flask`, `httpie`, `black`, `pytest`. Picking a corpus settles the dc-compare iteration target before implementation begins.

## Tasks (sketch — finalize once Q12 settles)

Convert grill outcomes into concrete implementation steps. Order follows the dependency: structural changes first, language extractors next, adapter migration last.

### 1. Domain-type changes (TSE)
- Add `IMPORT_FROM` to `ImportKind` enum (ADR-0004)
- Add `isAliased: Boolean = false` to `ImportDeclaration` (ADR-0006)
- Add `FUNCTION` and `VARIABLE` to `DeclarationType` enum (Q2)
- Update KDoc on `UsedType.namespacePrefix` to reflect dual interpretation (ADR-0005)

### 2. Python language module (TSE)
- `languages/python/PythonDefinition.kt` — register dependency mapping
- `languages/python/PythonDependencyMapping.kt` — compose extractors
- `languages/python/extractors/PackageExtractor.kt` — returns empty path (ADR-0001)
- `languages/python/extractors/ImportExtractor.kt` — emits `IMPORT_FROM` / `STANDARD` / wildcards / aliased flag / leading-dots relative encoding / multi-name split / empty-name skip (ADR-0003, ADR-0004, ADR-0006, Q5b/c/e)
- `languages/python/extractors/DeclarationExtractor.kt` — class/function/variable, decorated_definition unwrap (Q2, Q8, Q10)
- `languages/python/extractors/UsedTypeExtractor.kt` — two-stream (identifier + attribute), alias rewriting, source-order preservation (ADR-0002, ADR-0005, Q6, Q11)

### 3. Tests (TSE)
- `PythonDependencyTest` with `@Nested` groups per quirk
- Pin within-stream order in `UsedTypeExtractor` unit test (Q11)
- Pin `namespacePrefix.isEmpty()` discriminator invariant (ADR-0005)

### 4. README correction (TSE)
- Update `integration/dependencies/README.md` Python section: four-category concatenation order, `IMPORT_FROM`/`STANDARD` discriminator, aliased-FROM-import flag

### 5. DC adapter migration
- Branch DC; rewrite `PythonAnalyzer` as a thin adapter
- Implement: `__init__.py` Node-per-export, twin synthesis for `IMPORT_FROM`/wildcards/aliased-on-use, attribute matching against `STANDARD` imports + STANDARD-aliases, four-category concatenation order, VARIABLE-as-leaf exception
- Delete legacy queries (`PythonImportQuery`, `PythonTypeIdentifierQuery`, `PythonTypeAttributeQuery`, `PythonDefinitionsQuery`)

### 6. dc-compare iteration
- Clone `requests` locally
- Run `/dc-compare` after basic extractors compile
- Iterate fix → rebuild → re-compare until parity
- Document any accepted deviations

### 7. Release
- Merge TSE, tag release
- Update DC's JitPack dep to new TSE tag
- Merge DC

## Steps

- [x] Complete grill Q1–Q11
- [x] Write ADRs 0001–0006
- [ ] Resolve Q12 (reference corpus)
- [ ] Domain-type changes (Task 1)
- [ ] Python language module (Task 2)
- [ ] Tests (Task 3)
- [ ] README correction (Task 4)
- [ ] DC adapter migration (Task 5)
- [ ] dc-compare iteration to parity (Task 6)
- [ ] Release (Task 7)

- **Q8 — Decorated definition unwrapping.** Strip `decorated_definition` wrapper to reach the inner class/function definition.
- **Q9 — `usedTypes` vs `dependencies` split.** DC PythonAnalyzer puts some things into `Node.usedTypes` (resolve-at-runtime) and some into `Node.dependencies` (already-resolved). TSE only has `Declaration.usedTypes`. The DC adapter has to demultiplex.
- **Q10 — Module-level variable Declarations have empty `usedTypes`.** DC sets `dependencies = setOf(), usedTypes = setOf()` for module-level assignments. Mirror in TSE? Or extract identifiers from the RHS expression?
- **Q11 — Concatenation order.** README claims Python is "imports only, no multi-category concatenation" — but DC actually has multiple categories (importsFrom, wildcardImportsFrom, importFromTypes, importDependencies). What's the canonical order for TSE's UsedTypeExtractor?
- **Q12 — Reference corpus for `dc-compare`.** Pick a medium-sized open-source Python repo. Candidates: `requests`, `flask`, `httpie`, `black`, `pytest`. Need packages (not flat) and both relative and absolute imports.

## Tasks (placeholder — fill in once grill completes)

### 1. (TBD)

## Steps

- [ ] Complete grill (resume at Q5)
- [ ] Write ADR-0004 once Q5 is resolved
- [ ] Continue grill through Q6–Q12
- [ ] Convert grill outcomes into Tasks section
- [ ] Implement
