---
name: add-python-dependency-support
issue: TBD
state: progress
version: 2
tse_branch: feat/python-dependency-support
dc_branch: feat/python-dependency-integration
---

## Goal

Migrate DependaCharta's legacy Python dependency analyzer to TSE. Python is a Class 1 (single-namespace-per-file) language with several quirks not seen in prior migrations: no in-source package declaration (file path IS the module path), `__init__.py` synthetic-twin imports, alias resolution at the use site, relative imports requiring filesystem context, and module-level functions/variables emitted as Declarations alongside classes.

## Grill state

This plan was started via `/grill-with-docs`. Decisions locked in are recorded as ADRs in `docs/adr/`. Q1–Q12 resolved — grill complete. Implementation starts at Task 1.

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
- **Q10 / no ADR** — Module-level VARIABLE Declarations have empty `usedTypes` and empty `dependencies` (no pre-loaded import twins). Mirrors DC's early return at `PythonAnalyzer.kt:94-103`. Captured when the assignment's `left:` field is structurally an `identifier` — i.e. plain `IDENTIFIER = ...` *and* annotated `IDENTIFIER: T = ...` (matching DC's `(assignment left: (identifier))` query, which doesn't filter on the `type:` field). Skipped: tuple unpacking (`pattern_list`), subscripts, attribute LHS, augmented assignment — DC's query also skips these structurally. The RHS expression is never extracted. Adapter must check `declaration.type == VARIABLE` and skip pre-loading (codified in ADR-0006's algorithm).
- **Q11 / no ADR** — Mirror DC's four-category concatenation order in adapter's `Node.dependencies`: (1) non-aliased FROM-import twins in source order, (2) wildcard FROM-import twins in source order, (3) aliased FROM-import twins in identifier-stream order (conditional on use), (4) STANDARD-import + STANDARD-alias attribute matches in attribute-stream order. TSE's `PythonUsedTypeExtractor` builds `(identifierStream + attributeStream).toSet()` mirroring Java's pattern at `JavaUsedTypeExtractor.kt:63-66`. `LinkedHashSet` preserves insertion order. Pin within-stream order with an extractor unit test. Update `integration/dependencies/README.md` to correct the "imports only, no concatenation" claim.
- **Q12 / no ADR** — Reference corpus is **flask** (single corpus, sibling-cloned as `../flask`). Mid-sized (~50 .py), exercises decorators heavily, strong `__init__.py` re-exports, mix of relative/absolute imports, qualified attribute access on werkzeug. No vendored-deps pollution (unlike `requests`). Aliased-FROM density is light in flask — accepted gap, covered by `PythonDependencyTest` unit tests for ADR-0006's `isAliased` path rather than corpus diff. Single-corpus playbook mirrors C++ (cppcheck only). `pytest` held as stretch validation if flask's diff goes empty before all four quirks have been exercised in the wild.

### Pitfalls flagged in ADR-0004 / ADR-0005 (re-surface during implementation)

- `ImportKind` must come straight from the AST node type — heuristic on `path.size` would mis-twin `import X.Y`.
- `import X.Y` parity (ADR-0004 → ADR-0005): `UsedTypeExtractor` must populate `namespacePrefix` for every `(attribute)` AST node so the adapter can match the joined prefix against `STANDARD` imports.
- Aliased-import twins are synthesized in the adapter at use site (per ADR-0002 follow-through). Adapter must mutate `Node.dependencies` when a `UsedType` resolves to a previously-aliased name.
- TSE's `UsedTypeExtractor` scope must not exceed DC's `attributeQuery` scope (annotations, comprehensions, lambdas), or extra twin pairs will appear in the dc-compare delta.
- `namespacePrefix.isEmpty()` is the adapter's structural discriminator between identifier stream and attribute stream. Future changes to TSE's Python `UsedTypeExtractor` must not populate `namespacePrefix` for non-attribute reasons — pin this with an extractor unit test.

### Documentation seeded

- `CONTEXT.md` — created at repo root with terms: **package path** (canonical, vs DC's `modulePath`), **Declaration**, **Used type**, **Class 1 / Class 2 namespace models**.

## Tasks

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
- `languages/python/extractors/ImportExtractor.kt` — emits `IMPORT_FROM` / `STANDARD` / wildcards / aliased flag / leading-dots relative encoding / multi-name split / empty-name skip (ADR-0003, ADR-0004, ADR-0006)
- `languages/python/extractors/DeclarationExtractor.kt` — class/function/variable, decorated_definition unwrap (Q2, Q8, Q10)
- `languages/python/extractors/UsedTypeExtractor.kt` — two-stream (identifier + attribute), alias rewriting, source-order preservation (ADR-0002, ADR-0005, Q6, Q11)

### 3. Tests (TSE)
- `PythonDependencyTest` with `@Nested` groups per quirk
- Pin within-stream order in `UsedTypeExtractor` unit test (Q11)
- Pin `namespacePrefix.isEmpty()` discriminator invariant (ADR-0005)
- Pin nested-attribute emission: `os.path.join` source must produce both `UsedType("join", ["os","path"])` and `UsedType("path", ["os"])` (ADR-0005 Consequences — emitting only the outermost would silently drop dependencies)

### 4. README correction (TSE)
- Update `integration/dependencies/README.md` Python section: four-category concatenation order, `IMPORT_FROM`/`STANDARD` discriminator, aliased-FROM-import flag

### 5. DC adapter migration
DC branch already exists: `feat/python-dependency-integration`. Two distinct pieces of work, both required:

- **(a) Wire DC to local TSE via composite build for the whole iteration phase** — in DC's `analysis/settings.gradle.kts` add `includeBuild("../../TreeSitterExcavationSite")` and switch DC's TSE coordinate to `de.maibornwolff.treesitter.excavationsite:treesitter-excavationsite`. TSE changes flow into DC immediately, no JitPack publish needed. **Must be reverted before the DC PR is opened** (see `.claude/rules/dependency-migration.md` "Composite Build" section). Reverted in Task 7 once TSE is tagged.
- **(b) Rewrite `PythonAnalyzer` as a thin adapter** that calls `TreeSitterDependencies.analyze(content, Language.PYTHON)` and maps `DependencyResult` → DC's `FileReport`. Implement: `__init__.py` Node-per-export, twin synthesis for `IMPORT_FROM`/wildcards/aliased-on-use, attribute matching against `STANDARD` imports + STANDARD-aliases, four-category concatenation order, VARIABLE-as-leaf exception (per `Q9`/`Q10`/`Q11` decisions in this plan).
- Delete legacy extraction code: `PythonImportQuery`, `PythonTypeIdentifierQuery`, `PythonTypeAttributeQuery`, `PythonDefinitionsQuery`, plus any helper extractors made redundant by the rewrite.

### 6. dc-compare iteration
- Clone `flask` as a sibling (`../flask`)
- Run `/dc-compare ../flask` after basic extractors compile (don't wait for all extractors complete — see `dependency-migration.md` "Set up dc-compare before you think you're ready")
- Iterate fix → rebuild → re-compare until parity
- Document any accepted deviations
- If flask's diff goes empty before all four quirks have fired in the wild, add `pytest` as stretch validation
- **At parity, capture the golden dependency contract test for Python.** Until now, `GoldenFileContractTest.DependenciesGoldenFileTests` only covered Delphi. Python is the first migration to ship one as a baseline, establishing the pattern for future migrations. Add `python_sample_dependencies.golden` next to the existing `python_sample_extraction.golden` and `python_sample_metrics.golden`, and a `should match golden file for PYTHON dependencies` test in the existing inner class. Pre-existing `python_sample.py` fixture is reused. Captured here (not earlier) because the golden is a stabilization tool, not a development tool — the unit tests in `PythonDependencyTest` are the per-iteration feedback loop, and the golden becomes useful only once output stops churning.

### 7. Release
- Merge TSE PR, tag the release (e.g. `v0.9.0`)
- In DC: revert the composite-build wiring from Task 5 (drop `includeBuild` line in `analysis/settings.gradle.kts`, change TSE coordinate back to `com.github.MaibornWolff:TreeSitterExcavationSite:<new-tag>`)
- Merge DC PR

### 8. Backfill dependency goldens for already-migrated languages
- Scope: add `*_dependencies.golden` for Java, Kotlin, C#, C++ (Delphi already has one); extend `DependenciesGoldenFileTests` with one test per language; reuse each language's existing `<lang>_sample.<ext>` fixture.
- Why follow-up, not blocking: the Python golden alone establishes the test pattern and exercises `DependenciesGoldenFileTests` for non-Delphi shapes. Backfill is mechanical and orthogonal to the Python migration's correctness story, so it should not gate Python's release.
- Can be split into its own follow-up plan/PR if the cross-language fixtures need adjustment beyond the boilerplate.

## Steps

- [x] Complete grill Q1–Q12
- [x] Write ADRs 0001–0006
- [x] Domain-type changes (Task 1)
- [x] Python language module (Task 2)
- [x] Tests (Task 3)
- [ ] README correction (Task 4)
- [ ] DC adapter migration (Task 5)
- [ ] dc-compare iteration to parity (Task 6)
- [ ] Release (Task 7)
- [ ] Backfill dependency goldens for Java/Kotlin/C#/C++ (Task 8 — follow-up)
