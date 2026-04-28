---
name: Delphi Language Support with Dependency Integration
issue:
state: complete
version:
---

## Goal

Add Delphi (`Language.DELPHI`, `.pas` / `.dpr`) as a fully supported language with metrics, extraction, and dependency analysis, using the already-vendored `tree-sitter-pascal-0.10.2.jar`. Follow the existing vertical-slice + hexagonal architecture (see `integration/dependencies/README.md`). Lock behavior with golden-master tests against a curated `delphi_sample.pas`. This plan supersedes `plans/add-delphi-support.md` (metrics + extraction parts are folded in here).

## What we're NOT doing

- No changes to `TreeSitterDependencies`, `DependenciesFacade`, `DependencyCollector`, `LanguageDependencyMapping`, `TreeTraversal`, or any shared domain types — Delphi uses the existing contracts.
- No golden-file dependency coverage for languages other than Delphi (Java/Kotlin/C# stay on their unit tests; keep the new golden tests scoped to DELPHI).
- No external real-world Delphi repo snapshot — acceptance is curated-sample + unit tests.
- No DependaCharta (DC) integration — DC has no legacy Delphi analyzer, so there is nothing to mirror and no `/dc-compare` round. Concatenation order is chosen once here and documented.
- No changes to the preprocessor pipeline: Delphi uses `preprocessor = null`.
- No Class-2 namespace handling (multiple namespaces per file) — Delphi units are one-per-file (Class 1).

### Accepted v1 limitations

The following are **known limitations** of v1, documented here so consumers know not to rely on them. None are blocking for DC's use cases; follow-up work can address them if needed.

- ~~**`declConst` is not extracted.** Pascal `const PI = 3.14;` declarations are not emitted as identifiers or captured as declarations. (Java / Kotlin likewise don't surface top-level constants; out of scope.)~~ Resolved in `plans/extract-delphi-const-and-property-types.md` Phase 1: `declConst` is now registered for identifier extraction, and class-level typed consts contribute their type to the enclosing class's `usedTypes`.
- ~~**`declProp` is not captured as a used-type source.** Delphi `property Foo: TBar read GetFoo;` references `TBar` but `declProp` isn't in `UsedTypeExtractor.ALL_NODE_TYPES`. Field types and method-signature types ARE captured, so properties backed by methods/fields still contribute their types indirectly.~~ Resolved in `plans/extract-delphi-const-and-property-types.md` Phase 2: `declProp` is now in `UsedTypeExtractor.ALL_NODE_TYPES` and contributes property types directly. Accessor names (`read X` / `write Y`) remain uncaptured by design (mirrors Kotlin).
- ~~**Operator-overload method implementations silently skipped in `defProc → class` binding.** `TFoo.operator Equal(…)` uses `operatorName` for the method name; `qualifiedNameClassPrefix` only matches `identifier` rhs, so the operator's body types aren't attributed to `TFoo`.~~ Not a real limitation — verified in `plans/fix-delphi-edge-cases.md` Phase 6: tree-sitter-pascal 0.10.2 emits the operator's method-name segment as a regular `identifier`, and `qualifiedNameClassPrefix` only inspects the LHS to resolve the owning class. Operator implementations are bound like any other method.
- **`uses Foo in 'path/Foo.pas';` (project-file import form) not handled.** tree-sitter-pascal 0.10.2 does not parse this form cleanly; ImportExtractor sees whatever error-recovered shape the parser emits and may drop the entry. Only affects `.dpr` project files that import units with explicit paths.
- **Anonymous pointer types in qualified references (`Foo.^TBar`) drop to empty name.** Rare edge case; returns blank and is filtered by the defensive-extraction guard.
- ~~**Message-chain metric does not count paren-less method calls.**~~ Resolved in `plans/delphi-cross-language-gap-fixes.md` Phase 7: `exprDot` is now mapped to `MessageChainCall` and a `CalculationConfig.ignoreForMessageChainCall` rule prevents double-counting when an `exprDot` is wrapped by an `exprCall` (parenthesised chains).
- ~~**Golden files are generated on first test run.** The three `delphi_sample_*.golden` files are auto-created by `assertGoldenFile` when absent; the user must run `./gradlew test` once, review the generated output, and commit.~~ Workflow note, not a real limitation; removed from the v1 list.

## Current state

- `libs/tree-sitter-pascal-0.10.2.jar` is vendored; `build.gradle.kts:15,44,70` wires it into dependencies + fat-jar packaging. `org.treesitter.TreeSitterPascal` is on the classpath.
- No Delphi source exists under `src/main/kotlin/.../languages/delphi/` and no test under `src/test/kotlin/.../languages/delphi/`.
- `Language` enum at `shared/domain/Language.kt:6-23` lists 17 values; `ApiSignatureContractTest.kt:314` asserts `hasSize(17)`.
- `LanguageRegistry.kt:50-92` dispatches on `Language`; both `getTreeSitterLanguage` and `getLanguageDefinition` are exhaustive `when`s.
- `GoldenFileContractTest.kt:24-62` covers metrics + extraction via `@EnumSource(Language::class)`. **Dependencies have no golden-file coverage in the repo today.**
- `CLAUDE.md:13,41`, `README.md:9,200`, `.claude/rules/overview.md:9` still say "16 languages" (drift — TSX already made it 17). Bump to "18" here and include Delphi in the list.
- No DependaCharta sibling repo is present; no legacy Delphi analyzer to mirror. The README's "DC legacy concatenation orders" table does not include Delphi.

## Desired end state

- `TreeSitterMetrics.parse`, `TreeSitterExtraction.extract`, and `TreeSitterDependencies.analyze` all accept `Language.DELPHI` and produce deterministic output for `.pas` / `.dpr` sources.
- `DelphiDependencyTest` exercises PackageExtraction, ImportExtraction, DeclarationExtraction, UsedTypeExtraction, and ApiSupportCheck via `@Nested` classes.
- `GoldenFileContractTest` contains a new `DependenciesGoldenFileTests` nested class that locks `delphi_sample_dependencies.golden`; the existing metrics + extraction golden parametrized tests cover Delphi via the new sample file.
- `./gradlew test ktlintCheck detekt` all green with `UPDATE_GOLDEN_FILES = false`.
- Documentation (`CLAUDE.md`, `README.md`, `.claude/rules/overview.md`) reflects 18 supported languages including Delphi.

## Architecture and code reuse

Delphi follows the existing Class-1 single-namespace pattern (same as Java/Kotlin). All shared infrastructure is reused unchanged:

- **Reuse as-is**: `DependencyCollector`, `DependenciesFacade`, `LanguageDependencyMapping`, `TreeTraversal` (`findAllDescendantsOfType`, `findAllDescendantsGroupedByType`, `getNodeText`, `children()`, `namedChildren()`), `TreeSitterParser`, `CommentFormats`, `StringFormats`, `ExtractionStrategy`.
- **Language-specific additions only** live under `languages/delphi/`. Mirror the Java directory layout (`JavaDefinition.kt`, `Java{Metric,Extraction,Dependency}Mapping.kt`, `extractors/`).
- **Custom comment extraction**: tree-sitter-pascal emits one `comment` node for all three forms (`//`, `{ }`, `(* *)`). `CommentFormats.AutoDetect` doesn't recognize `{}` / `(* *)`, so add `languages/delphi/extractors/DelphiCommentExtractor.kt` with a small custom function (pattern: `languages/csharp/extractors/CSharpHelpers.kt`).
- **No changes** to `shared/domain/DependencyResult.kt` — Delphi types (`class`, `interface`, `record`, `enum`) map onto existing `DeclarationType` values (enum gains nothing new).

### Concatenation order for `UsedTypeExtractor`

No DC legacy Delphi analyzer exists, so we pick an order once here and document it in `integration/dependencies/README.md` under "DC legacy concatenation orders":

```
inheritance, parameters, returnTypes, fieldTypes, variableTypes, constructorCalls, methodCalls
```

Rationale: mirrors Kotlin's order (closest language in terms of declaration shape), which reviewers already vetted for Levelizer-friendliness. Documented as the Delphi TSE order (no legacy to match).

### Namespace model

Class 1 (`packagePath` is authoritative, `parentPath` empty — one `unit` per file):

- `packagePath`: split dotted unit name on `.`, e.g. `unit MyCo.MyMod.Utils;` → `["MyCo", "MyMod", "Utils"]`. Empty for `.dpr` `program` files without an explicit name.
- `parentPath`: always `emptyList()` for Delphi v1 (Delphi allows nested types syntactically but they're rare; treat top-level only, same as C#'s v1 and Java's class-nesting model).

### Affected files

- `libs/tree-sitter-pascal-0.10.2.jar` — already present, no change.
- `src/main/kotlin/.../shared/domain/Language.kt` — add `DELPHI(primaryExtension = ".pas", otherExtensions = setOf(".dpr"))`.
- `src/main/kotlin/.../languages/LanguageRegistry.kt` — two `when` branches mapping `Language.DELPHI` to `TreeSitterPascal()` and `DelphiDefinition`.
- `src/main/kotlin/.../languages/delphi/` — new directory:
  - `DelphiDefinition.kt` — composes metric, extraction, dependency mappings.
  - `DelphiMetricMapping.kt` — node-type → metrics (folded in from `plans/add-delphi-support.md`).
  - `DelphiExtractionMapping.kt` — node-type → extract behavior (folded in from `plans/add-delphi-support.md`).
  - `DelphiDependencyMapping.kt` — composes the four extractors.
  - `extractors/DelphiCommentExtractor.kt` — custom `extractDelphiComment` (for `{}`/`(**)`).
  - `extractors/PackageExtractor.kt` — unit path from `unit <DottedName>;`.
  - `extractors/ImportExtractor.kt` — `uses` clauses from both `interface` and `implementation` sections.
  - `extractors/DeclarationExtractor.kt` — top-level `declClass`, `declIntf`, `declRecord`, `declEnum`, `declHelper` → `Declaration`.
  - `extractors/UsedTypeExtractor.kt` — grouped traversal producing `Set<UsedType>` with the concatenation order above.
- `src/main/kotlin/.../integration/dependencies/README.md` — add Delphi row to the DC legacy concatenation orders table (noting "no DC legacy — TSE-native order"), and a Class-1 row to the namespace model table.
- `src/test/kotlin/.../languages/delphi/` — new directory:
  - `DelphiMetricsTest.kt`
  - `DelphiExtractionTest.kt`
  - `DelphiDependencyTest.kt` with `@Nested` groups `PackageExtraction`, `ImportExtraction`, `DeclarationExtraction`, `UsedTypeExtraction`, `ApiSupportCheck`.
- `src/test/kotlin/.../api/contract/GoldenFileContractTest.kt` — add `Language.DELPHI` to `SAMPLE_FILE_NAMES` and `GOLDEN_BASE_NAMES`; add `DependenciesGoldenFileTests` inner class that runs for DELPHI only.
- `src/test/kotlin/.../api/contract/ApiSignatureContractTest.kt:314` — `hasSize(17)` → `hasSize(18)` and add a `should contain DELPHI` test.
- `src/test/kotlin/.../api/contract/LanguageSupportContractTest.kt:22-36,126-151,240` — add `.pas, DELPHI` to the primary-extension `@CsvSource` and `.pas` / `.dpr` to the `IsLanguageSupportedContract` `@CsvSource`; bump `should return at least 26 extensions` → `at least 28`.
- `src/test/kotlin/.../api/TreeSitterExtractionTest.kt:100,115` — `hasSize(17)` → `hasSize(18)` for supported languages; `hasSize(31)` → `hasSize(33)` for extensions.
- `src/test/kotlin/.../api/TreeSitterMetricsTest.kt:122` — `hasSize(31)` → `hasSize(33)` for extensions.
- `src/test/kotlin/.../api/contract/RobustnessContractTest.kt:14` — stale KDoc "across all 14 languages" → "across all 18 languages" (auto-parameterized via `@EnumSource(Language::class)`, so test logic needs no change).
- `src/test/resources/contract/delphi_sample.pas` — new curated sample (~80–120 lines: unit with dotted name, uses clause in both sections, class with inheritance + generics, interface, record with fields, procedure/function with parameters + return, try/except, comments in all three styles, single-quoted strings).
- `src/test/resources/contract/delphi_sample_metrics.golden` — generated.
- `src/test/resources/contract/delphi_sample_extraction.golden` — generated.
- `src/test/resources/contract/delphi_sample_dependencies.golden` — generated.
- `CLAUDE.md:13,41`, `README.md:9,200`, `.claude/rules/overview.md:9`, `.claude/rules/architecture.md:42` — bump "16" → "18" (catches TSX drift too) and append Delphi.
- `plans/add-delphi-support.md` — set `state: complete` with a "superseded by add-delphi-dependency-support.md" note at the top.

## Performance considerations

None expected. Traversal stays within the existing `TreeTraversal.findAllDescendantsGroupedByType` one-pass pattern. Delphi files are typically small; no cross-file or multi-pass work.

## Migration notes

- The `Language.entries.size` contract change (17 → 18) is visible to consumers but is additive: existing extensions/codes remain valid. CodeCharta's composite build will recompile cleanly once `Language.DELPHI` is on its enum.
- `CHANGELOG.md` entry under an "Unreleased" section: "Added Delphi (`.pas`, `.dpr`) support for metrics, extraction, and dependency analysis."

---

## Phase 1: Language registration and scaffolding

Lightweight enum + registry wiring with a minimal `DelphiDefinition` that exposes only `isDependencyMappingSupported = false` at first. Keeps the build green while later phases fill in mappings via TDD.

**Tasks**:
- [x] Add `DELPHI(primaryExtension = ".pas", otherExtensions = setOf(".dpr"))` to `shared/domain/Language.kt`.
- [x] Create `languages/delphi/DelphiDefinition.kt` with empty `nodeMetrics = emptyMap()`, `nodeExtractions = emptyMap()`, and no `dependencyMapping` override (defaults to `null`).
- [x] Register Delphi in `languages/LanguageRegistry.kt`:
  - `Language.DELPHI -> TreeSitterPascal()` in `getTreeSitterLanguage`
  - `Language.DELPHI -> DelphiDefinition` in `getLanguageDefinition`
- [x] Update `ApiSignatureContractTest`: `hasSize(17)` → `hasSize(18)` and add `should contain DELPHI`.
- [x] Update `LanguageSupportContractTest`: add `.pas, DELPHI` to primary `@CsvSource`, add `.pas` and `.dpr` to the supported-extensions `@CsvSource`, bump `at least 26 extensions` assertion to `at least 28`.
- [x] Update `TreeSitterExtractionTest.kt:100,115`: `hasSize(17)` → `hasSize(18)` (supported languages) and `hasSize(31)` → `hasSize(33)` (extensions).
- [x] Update `TreeSitterMetricsTest.kt:122`: `hasSize(31)` → `hasSize(33)` (extensions).
- [x] Update `RobustnessContractTest.kt:14` stale KDoc "across all 14 languages" → "across all 18 languages". (Test logic is `@EnumSource(Language::class)` — auto-covers `DELPHI` once scaffolding returns zero metrics cleanly for empty input, which the default `emptyMap()` mappings already guarantee.)

**Automated verification**:
- [x] `./gradlew compileKotlin` passes.
- [x] `./gradlew test --tests "ApiSignatureContractTest"` passes.
- [x] `./gradlew test --tests "LanguageSupportContractTest"` passes.
- [x] `./gradlew ktlintCheck` passes.

---

## Phase 2: Delphi metrics (TDD)

Dependencies: **Phase 1**.

Fold in the metrics half of `plans/add-delphi-support.md`. Node-type table (camelCase — verify once via AST dump before writing, add a throw-away `println` test if needed):

| Category | Node types |
|---|---|
| Logic complexity | `if`, `ifElse`, `for`, `foreach`, `while`, `case`, `caseCase`, `repeat`, `try`, `exceptionHandler` |
| Conditional logic | `exprBinary` (`and`/`or`/`xor`) |
| Functions | `defProc`, `lambda` (declProc in interface does NOT count) |
| Function body | `block` |
| Parameters | `declArg` |
| Comments | `comment` |
| Strings | `literalString` |
| Message chains | `exprDot`, `exprCall` |

**Tasks**:
- [x] Write failing `DelphiMetricsTest` — one `@Nested` per metric family, covering all nodes above.
- [x] Create `languages/delphi/DelphiMetricMapping.kt` implementing `MetricMapping` with the node → `Set<Metric>` map.
- [x] Wire `DelphiMetricMapping.nodeMetrics` into `DelphiDefinition`.

**Automated verification**:
- [x] `./gradlew test --tests "DelphiMetricsTest"` green.
- [x] `./gradlew ktlintCheck` passes.

---

## Phase 3: Delphi extraction (TDD)

Dependencies: **Phase 1**.

Fold in the extraction half of `plans/add-delphi-support.md`.

**Tasks**:
- [x] Write failing `DelphiExtractionTest` with `@Nested` groups for IdentifierExtraction (procedures, classes, interfaces, multi-name `var`), CommentExtraction (`//`, `{ }`, `(* *)`), and StringExtraction (single-quoted).
- [x] Create `languages/delphi/extractors/DelphiCommentExtractor.kt` with `internal fun extractDelphiComment(node: TSNode, code: String): String?` handling all three comment forms.
- [x] Create `languages/delphi/DelphiExtractionMapping.kt`:
  - type names extracted from `declType` via custom `extractDelphiDeclTypeName` (handles generic/qualified names)
  - procedure/function/method names extracted from `defProc` and `declProc` via custom extractors (navigates `header` and `name` fields)
  - `declVar`, `declField`, `declArg` → `Extract.Identifier(customMulti = ::extractDelphiMultipleNames)` (Pascal allows multiple names per declaration: `var X, Y: Integer;`)
  - `declEnumValue` → `Extract.Identifier(single = FirstChildByType("identifier"))`
  - `exceptionHandler` → `Extract.Identifier(single = FirstChildByType("identifier"))`
  - `comment` → `Extract.Comment(custom = ::extractDelphiComment)`
  - `literalString` → `Extract.StringLiteral(format = StringFormats.Quoted(stripSingleQuotes = true))`
- [x] Wire `DelphiExtractionMapping.nodeExtractions` into `DelphiDefinition`.

**Automated verification**:
- [x] `./gradlew test --tests "DelphiExtractionTest"` green.
- [x] `./gradlew ktlintCheck` passes.

---

## Phase 4: Delphi dependency extraction (TDD — primary task)

Dependencies: **Phase 1**.

Primary focus: add the dependency integration following the README pattern (`integration/dependencies/README.md`, sections "Adding a new language" and "Namespace models").

### 4a. Test-first scaffolding

**Tasks**:
- [x] Create `src/test/kotlin/.../languages/delphi/DelphiDependencyTest.kt` with `@Nested` groups — write failing tests first:
  - `PackageExtraction` — dotted unit name → split path; unnamed `.dpr` program → empty list; no `unit` keyword → empty list.
  - `ImportExtraction` — single `uses Foo;`; multiple comma-separated; dotted `uses MyCo.Bar;`; `uses Foo in 'path/Foo.pas';` (extract `Foo` only); uses clauses in both `interface` and `implementation` sections extracted and de-duplicated by `(path, isWildcard)`; no `uses` clause → empty list. `isWildcard = false` always (Pascal has no wildcard imports).
  - `DeclarationExtraction` — single class, single interface, record, enum, class helper; `name` populated from the first identifier; `type` correctly mapped to `DeclarationType.CLASS`/`INTERFACE`/`RECORD`/`ENUM`/`CLASS` (helper → CLASS); `parentPath` always empty.
  - `UsedTypeExtraction` — inheritance from `class(Base, IFoo)`; parameter types on `defProc` / `declProc`; return type on functions; field types on `declField`; local variable types (`declVar`); constructor calls (`Foo.Create`); method calls on uppercase-first receivers. Verify the fixed concatenation order: `inheritance, parameters, returnTypes, fieldTypes, variableTypes, constructorCalls, methodCalls`.
  - `ApiSupportCheck` — `TreeSitterDependencies.isDependencyAnalysisSupported(Language.DELPHI)` returns `true`; `getSupportedLanguages()` contains `Language.DELPHI`; analyzing a trivial valid unit does not throw.

### 4b. Extractors (make tests pass)

**Tasks**:
- [x] Create `languages/delphi/extractors/PackageExtractor.kt` — finds `unit` / `program` / `library` root-level module, reads its `moduleName`'s identifier children (dotted), returns `emptyList()` when absent.
- [x] Create `languages/delphi/extractors/ImportExtractor.kt` — uses `findAllDescendantsOfType(rootNode, "declUses")` to catch both interface and implementation uses sections, iterates `moduleName` children per uses-clause, emits `ImportDeclaration(path = parts, isWildcard = false)`. Deduplicates the final list.
- [x] Create `languages/delphi/extractors/DeclarationExtractor.kt` — `findAllDescendantsOfType(rootNode, "declType")`; resolves the declaration kind from `declType.type` (`declClass` keyword distinguishes CLASS vs RECORD; `declIntf` → INTERFACE; `declHelper` → CLASS; `type` wrapping `declEnum` → ENUM); skips type aliases and types nested inside other declarations; delegates to `UsedTypeExtractor`.
- [x] Create `languages/delphi/extractors/UsedTypeExtractor.kt` — one-pass `findAllDescendantsGroupedByType` over inheritance parents (`declClass`/`declIntf`/`declHelper`'s `parent` field), parameter types (`declArg.type`), return types (`declProc.type` / `defProc.header.type`), field types (`declField`), variable types (`declVar`), constructor calls (`exprCall` whose entity is `exprDot` with `rhs == Create`), qualified method receivers (`exprDot` LHS with uppercase-first heuristic). Concatenates in the fixed order `inheritance, parameters, returnTypes, fieldTypes, variableTypes, constructorCalls, methodCalls` then `.toSet()`.
- [x] Create `languages/delphi/DelphiDependencyMapping.kt`:
  ```kotlin
  object DelphiDependencyMapping {
      val dependencyMapping = LanguageDependencyMapping(
          extractPackagePath = PackageExtractor::extract,
          extractImports = ImportExtractor::extract,
          extractDeclarations = DeclarationExtractor::extract
      )
  }
  ```
- [x] Wire `DelphiDependencyMapping.dependencyMapping` into `DelphiDefinition` (override `dependencyMapping`).
- [x] Add a Delphi row to the "DC legacy concatenation orders" table in `integration/dependencies/README.md`: `Delphi | inheritance, parameters, returnTypes, fieldTypes, variableTypes, constructorCalls, methodCalls | N/A — TSE-native, no DC legacy`. Add a Class-1 row to the namespace-model table.

### 4c. Defensive-extraction guards

Apply the skip-empty pattern described in `.claude/rules/dependency-migration.md` ("Defensive extraction"):

**Tasks**:
- [x] `ImportExtractor`: drop imports whose resolved path is empty (`mapNotNull` / filter, not `?: emptyList()`).
- [x] `DeclarationExtractor`: drop declarations whose name is empty or blank.
- [x] `UsedTypeExtractor`: drop entries whose `UsedType.name` is blank after trimming.

**Automated verification**:
- [x] `./gradlew test --tests "DelphiDependencyTest"` green.
- [x] `./gradlew test --tests "*Delphi*"` green.
- [x] `./gradlew ktlintCheck` passes.
- [x] `./gradlew test --tests "ArchitectureTest"` green (no new package violations).

---

## Phase 5: Golden-master tests

Dependencies: **Phase 2**, **Phase 3**, **Phase 4**.

Lock Delphi behavior end-to-end against a curated sample.

**Tasks**:
- [x] Create `src/test/resources/contract/delphi_sample.pas` (~130 lines) exercising: `unit MyCo.MyMod.Sample;`, `interface`/`implementation` split, `uses` in both sections (dotted names in interface), class with inheritance + interface implementation + generic field, interface, record with fields, class helper, enum, procedures and functions with parameters / return types / local `var` blocks, `try..except`, `try..finally`, `case` with ranges, nested control flow, all three comment styles, single-quoted strings.
- [x] Add `Language.DELPHI to "delphi_sample.pas"` in `GoldenFileContractTest.SAMPLE_FILE_NAMES`.
- [x] Add `Language.DELPHI to "delphi_sample"` in `GOLDEN_BASE_NAMES`.
- [x] Add `DependenciesGoldenFileTests` nested class in `GoldenFileContractTest`:
  - One `@Test` (not `@EnumSource`) named `should match golden file for DELPHI dependencies`.
  - Reads `delphi_sample.pas`, calls `TreeSitterDependencies.analyze(code, Language.DELPHI)`, serializes via new `serializeDependencies(result)` helper.
  - `serializeDependencies` format (deterministic ordering):
    ```
    # Package
    <joined path with '.', blank if empty>

    # Imports
    <sorted "<isWildcard?*>:path.joined" lines>

    # Declarations
    <sorted "<type>:<name>" lines>

    # Used Types (per declaration)
    <declaration-name sorted blocks>
      <sorted UsedType names with generics rendered as name<g1,g2>>
    ```
- [x] Run the test suite with `UPDATE_GOLDEN_FILES = true` (flip the companion-object constant, run, flip back) to generate:
  - `src/test/resources/contract/delphi_sample_metrics.golden`
  - `src/test/resources/contract/delphi_sample_extraction.golden`
  - `src/test/resources/contract/delphi_sample_dependencies.golden`

  Note: the `assertGoldenFile` helper already auto-creates missing golden files on the first run (and throws to force a review), so the first `./gradlew test` run also serves this purpose.
- [x] Manually review all three golden files: values are plausible (unit name splits correctly, imports listed, declarations named correctly, used types have no empty entries, metrics reflect the sample's structure).
- [x] Flip `UPDATE_GOLDEN_FILES` back to `false` and commit the golden files + sample.

**Automated verification**:
- [x] `./gradlew test --tests "GoldenFileContractTest"` green with `UPDATE_GOLDEN_FILES = false`.
- [x] `./gradlew test` full suite green.
- [x] `./gradlew ktlintCheck` and `./gradlew detekt` pass.

**Manual verification**:
- [x] Open `delphi_sample_dependencies.golden` and confirm: package = `MyCo.MyMod.Sample` split into three segments; every `uses` target appears exactly once; each declared class/interface/record/enum/helper appears with the correct `DeclarationType`; used-type sets are non-empty for declarations that reference external types.
- [x] Open `delphi_sample_metrics.golden`: function count matches `defProc` count (not including `declProc` in the `interface` section); comment lines cover all three comment styles.
- [x] Open `delphi_sample_extraction.golden`: identifiers include class/interface/procedure names; comments from `{ }` and `(* *)` appear with delimiters stripped.

---

## Phase 6: Documentation and plan bookkeeping

Dependencies: **Phase 5**.

**Tasks**:
- [x] Bump language count "16" → "18" and add Delphi to the listed languages in:
  - `CLAUDE.md:13,41`
  - `README.md:9,200`
  - `.claude/rules/overview.md:9,21`
  - `.claude/rules/architecture.md:42`
  - `src/main/kotlin/.../CLAUDE.md` references (none expected — confirmed via grep).
- [x] Add a `CHANGELOG.md` entry under Unreleased: Delphi (.pas, .dpr) metrics/extraction/dependency analysis support.
- [x] Mark `plans/add-delphi-support.md` as superseded: set `state: complete`, add a note at the top linking to this plan.
- [x] Update this plan's `state:` field from `progress` → `complete`. All code work implemented, reviewed across five rounds, golden files generated and committed, full `./gradlew test ktlintCheck detekt` run green (2644 tests, 0 failures).

**Automated verification**:
- [x] `./gradlew test` full suite green on a clean build (`./gradlew clean build`).
- [x] `grep -R "16 languages\|17 languages\|14 languages" CLAUDE.md README.md .claude/rules/ src/test/kotlin/` returns no hits.

---

## Notes

- **tree-sitter-pascal uses camelCase node types** (unique in this project). Any helper that assumes snake_case node names won't apply — verify each node-type string with a one-off AST dump before relying on it.
- **`declProc` vs `defProc`**: only `defProc` (implementations) counts as a function for metrics AND as a source of used types for dependencies. `declProc` in the `interface` section is a forward declaration; its parameter/return types still contribute to the enclosing type's used types (inheritance section of classes/interfaces), which the recursive `findAllDescendantsGroupedByType` naturally captures.
- **`uses` de-duplication** handles the common case where the same module appears in both `interface uses` and `implementation uses`. Use `distinct()` on the final `(path, isWildcard)` pairs.
- **`uses Foo in 'path/Foo.pas';`**: extract only the unit name (`Foo`), not the path string.
- **Compiler directives `{$...}`**: the grammar excludes these from `comment` nodes, so nothing to filter.
- **`.dpr` programs without a name**: fall back to `emptyList()` for `packagePath`; DC consumers that need a file-level path will use the filename (out of TSE's scope).
- **No DC `/dc-compare` round** — because there is no DC Delphi analyzer. Mention this explicitly in the PR description so reviewers don't expect a dc-compare report.
- **Namespace-model README update**: add a Delphi entry to the Class-1 table (packagePath source = `unit_declaration`, parentPath = empty).
