---
name: remove-export-only-filter
issue:
state: complete
version: 0.10.0
---

## Goal

Remove the export-only filter from `DeclarationExtractor` so that non-exported top-level declarations (functions, classes, variables, etc.) are included in the dependency graph. A non-exported function that uses an imported type creates a real file-level dependency that is currently invisible.

## Background

Commit `3a7b9526` introduced export-only filtering to match DC main's node count. This was the wrong trade-off: DC main included non-exported declarations as a side-effect of having no filter, which was actually the *correct* behavior for cycle detection. Removing the filter closes the 1630 (TS) / 962 (JS) "only-in-main" node gap without reintroducing DC's over-attribution bugs (those are a separate body-scan issue).

## Tasks

### 1. Add failing test for non-exported declaration extraction

Add a positive test to `TypescriptDependencyTest.kt` asserting that a non-exported function is extracted with its used types. This test must fail before the implementation change.

### 2. Remove the filter gate and expand `extractLocalDeclarationNames`

Two tightly-coupled changes in `DeclarationExtractor.kt`:
- In `extract()`: remove the `exportReferencedLocalNames` pre-pass and the `if/else` guard in the `in DECLARATION_NODE_TYPES` branch — replace with a direct `extractFromNode(...)` call
- In `extractLocalDeclarationNames()`: add an `in DECLARATION_NODE_TYPES` branch alongside the existing `EXPORT_STATEMENT` branch so non-exported names are also available for intra-file cross-reference tracking in `UsedTypeExtractor`

### 3. Update tests that assert non-exported exclusion

Two tests in `JavascriptDependencyTest.kt` explicitly assert that non-exported functions/classes produce empty results. These must be flipped to positive assertions. Run the full test suite to catch any other broken tests.

### 4. Remove dead code

After the filter is gone, the following become unreachable:
- `DeclarationPrepass.collectExportReferencedLocalNames()` and its two helpers (`exportClauseOriginalNames`, `extractCJSExportedNames`)
- `DeclarationExtractor.declarationNamesIncludes()`
- The `val exportReferencedLocalNames = ...` line in `extract()`

Delete all of them and run `./gradlew ktlintFormat`.

## Steps

- [x] Complete Task 1: Add failing test for non-exported declaration extraction
- [x] Complete Task 2: Remove filter gate + expand `extractLocalDeclarationNames`
- [x] Complete Task 3: Update tests asserting non-exported exclusion
- [x] Complete Task 4: Remove dead code
- [x] Complete Task 5: Investigate extra nodes using mini repo (ts-dc-test)
- [x] Complete Task 6: Run full dc-compare on Prisma/React and classify remaining extras
- [x] Complete Task 7: Classify JS only-in-feat extras and decide on surgical filtering
- [x] Complete Task 8: Investigate and fix CLASS vs REEXPORT NodeType mismatches in JS

## Detailed Steps

### Task 1 — Failing test

**File:** `src/test/kotlin/de/maibornwolff/treesitter/excavationsite/languages/typescript/TypescriptDependencyTest.kt`

Find the `@Nested` class covering declaration extraction (search for `inner class Declaration`). Add inside it:

```kotlin
@Test
fun `should extract non-exported function declaration with its used types`() {
    // Arrange
    val code = """
        import { Logger } from './logger'

        function logVisit(visitor: string): void {
            const logger = new Logger()
            logger.log(visitor)
        }
    """.trimIndent()

    // Act
    val result = TreeSitterDependencies.analyze(code, Language.TYPESCRIPT)

    // Assert
    assertThat(result.declarations).extracting("name").contains("logVisit")
    val logVisit = result.declarations.first { it.name == "logVisit" }
    assertThat(logVisit.usedTypes).extracting("name").containsExactlyInAnyOrder("Logger")
}
```

- [x] Add the test
- [x] Run `./gradlew test --tests "*TypescriptDependency*"` — must FAIL with `logVisit not found`

### Task 2 — Remove filter + expand extractLocalDeclarationNames

**File:** `src/main/kotlin/de/maibornwolff/treesitter/excavationsite/languages/javascript/extractors/DeclarationExtractor.kt`

**Change 1** — in `DeclarationPrepass.extractLocalDeclarationNames()`, replace:

```kotlin
internal fun extractLocalDeclarationNames(rootNode: TSNode, sourceCode: String): Set<String> = rootNode
    .children()
    .flatMap { child ->
        when (child.type) {
            EXPORT_STATEMENT -> extractNamesFromExportStatement(child, sourceCode)
            else -> emptyList()
        }
    }.filter { it.isNotBlank() }
    .toSet()
```

with:

```kotlin
internal fun extractLocalDeclarationNames(rootNode: TSNode, sourceCode: String): Set<String> = rootNode
    .children()
    .flatMap { child ->
        when (child.type) {
            EXPORT_STATEMENT -> extractNamesFromExportStatement(child, sourceCode)
            in DECLARATION_NODE_TYPES -> extractNamesFromNode(child, sourceCode)
            else -> emptyList()
        }
    }.filter { it.isNotBlank() }
    .toSet()
```

**Change 2** — in `DeclarationExtractor.extract()`, remove the `val exportReferencedLocalNames = ...` line and replace the `in DECLARATION_NODE_TYPES` branch:

Before:
```kotlin
val exportReferencedLocalNames = DeclarationPrepass.collectExportReferencedLocalNames(rootNode, sourceCode)
val declarations = rootNode
    .children()
    .flatMap { child ->
        when (child.type) {
            EXPORT_STATEMENT -> extractFromExportStatement(
                child, sourceCode, aliasMap = aliasMap, localDeclarationNames = localDeclarationNames
            )
            AMBIENT_DECLARATION -> extractFromAmbientDeclaration(
                child, sourceCode, aliasMap = aliasMap, localDeclarationNames = localDeclarationNames
            )
            in DECLARATION_NODE_TYPES -> {
                if (declarationNamesIncludes(child, sourceCode, exportReferencedLocalNames)) {
                    extractFromNode(child, sourceCode, aliasMap = aliasMap, localDeclarationNames = localDeclarationNames)
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }
```

After:
```kotlin
val declarations = rootNode
    .children()
    .flatMap { child ->
        when (child.type) {
            EXPORT_STATEMENT -> extractFromExportStatement(
                child, sourceCode, aliasMap = aliasMap, localDeclarationNames = localDeclarationNames
            )
            AMBIENT_DECLARATION -> extractFromAmbientDeclaration(
                child, sourceCode, aliasMap = aliasMap, localDeclarationNames = localDeclarationNames
            )
            in DECLARATION_NODE_TYPES -> extractFromNode(
                child, sourceCode, aliasMap = aliasMap, localDeclarationNames = localDeclarationNames
            )
            else -> emptyList()
        }
    }
```

- [x] Apply both changes
- [x] Run `./gradlew test --tests "*TypescriptDependency*"` — Task 1 test must now PASS
- [x] Run `./gradlew test --tests "*JavascriptDependency*"` — two tests will FAIL (expected — fixed in Task 3)

### Task 3 — Update tests asserting exclusion

**File:** `src/test/kotlin/de/maibornwolff/treesitter/excavationsite/languages/javascript/JavascriptDependencyTest.kt`

Find and replace:

```kotlin
@Test
fun `should not extract non-exported function declaration`() {
    // Arrange
    val code = "function foo() {}"

    // Act
    val result = TreeSitterDependencies.analyze(code, Language.JAVASCRIPT)

    // Assert
    assertThat(result.declarations).isEmpty()
}

@Test
fun `should not extract non-exported class declaration`() {
    // Arrange
    val code = "class Foo {}"

    // Act
    val result = TreeSitterDependencies.analyze(code, Language.JAVASCRIPT)

    // Assert
    assertThat(result.declarations).isEmpty()
}
```

with:

```kotlin
@Test
fun `should extract non-exported function declaration`() {
    // Arrange
    val code = "function foo() {}"

    // Act
    val result = TreeSitterDependencies.analyze(code, Language.JAVASCRIPT)

    // Assert
    assertThat(result.declarations).extracting("name").containsExactly("foo")
}

@Test
fun `should extract non-exported class declaration`() {
    // Arrange
    val code = "class Foo {}"

    // Act
    val result = TreeSitterDependencies.analyze(code, Language.JAVASCRIPT)

    // Assert
    assertThat(result.declarations).extracting("name").containsExactly("Foo")
}
```

- [x] Apply changes
- [x] Run `./gradlew test` — all tests must be GREEN
- [x] Commit: `fix(js,ts): include non-exported top-level declarations in dependency graph`

### Task 4 — Remove dead code

**File:** `src/main/kotlin/de/maibornwolff/treesitter/excavationsite/languages/javascript/extractors/DeclarationExtractor.kt`

Delete the following functions from `DeclarationPrepass`:
- `collectExportReferencedLocalNames()`
- `exportClauseOriginalNames()` (private, only called by the above)
- `extractCJSExportedNames()` (private, only called by the above)

Delete from `DeclarationExtractor`:
- `declarationNamesIncludes()` (private, now unreachable)

- [x] Delete all four functions
- [x] Run `./gradlew ktlintFormat`
- [x] Run `./gradlew test` — all tests must remain GREEN
- [x] Commit: `refactor(js,ts): remove dead export-filter code after filter removal`

## Notes

- The bare `namespace Foo {}` TS test (`should not extract namespace declaration without export keyword`) still passes after this change: tree-sitter wraps it in `expression_statement → internal_module`, so the top-level child seen by `extract()` is `expression_statement`, which is not in `DECLARATION_NODE_TYPES`.
- The 1630/962 "only-in-main" nodes closed to 7/96 after this fix + DEFAULT_EXPORT type fix (see Task 6 results).
- The "true regressions" (missing edges) are a separate body-scan gap unrelated to this filter — they increased in absolute count because more nodes now exist, but the gap per node did not worsen.

### Task 5 — Investigate extra nodes using mini repo (ts-dc-test)

Before running full dc-compare on large repos, use the mini repo to identify which non-exported declaration types become "extra" nodes in TSE that DC main doesn't produce. This is faster and gives clearer root-cause visibility.

Add the following non-exported declarations to `C:\Development\CodeChartaEtc\ts-dc-test\src\zoo.ts` (or a new file), covering the likely "extra" categories:

```typescript
// non-exported const arrow function
const helper = (x: string): Logger => new Logger()

// non-exported plain const (object literal)
const CONFIG = { maxSize: 10 }

// non-exported type alias
type LocalType = string

// non-exported interface
interface LocalInterface { x: number }
```

Then:
1. Rebuild DC feature JAR: `./gradlew fatJar` in `C:\Development\CodeChartaEtc\DependaCharta\analysis`
2. Re-run dc-main on mini repo: `java -jar C:\Development\CodeChartaEtc\dc-compare\dc-main.jar -d C:\Development\CodeChartaEtc\ts-dc-test -o C:\Development\CodeChartaEtc\dc-compare\mini-main -f mini-main -c`
3. Re-run dc-feature on mini repo: `java -jar C:\Development\CodeChartaEtc\DependaCharta\analysis\build\libs\dependacharta.jar -d C:\Development\CodeChartaEtc\ts-dc-test -o C:\Development\CodeChartaEtc\dc-compare\mini-feature -f mini-feature -c`
4. Compare the two `.cg.json` files — for each added declaration, check if it appears in feature but not main

Note: TSE publish is NOT needed here since we're not changing TSE code, only the mini repo test fixtures.

- [x] Add targeted non-exported declarations to ts-dc-test
- [x] Rebuild DC and re-run both analyses
- [x] For each declaration type: note whether it appears in feature only, main only, or both
- [x] Document findings in Notes section below

**Findings (2026-05-21):** All 4 new non-exported declaration types appear in BOTH main and feature — the node set is now identical (17 nodes each). DC main already extracted them via whole-file body scan. Differences are edge-only (DC main over-attributes all file imports to every declaration; TSE correctly scopes each). No "extra" nodes introduced by removing the filter.

| Category | In MAIN? | In FEATURE? | nodeType | FEATURE deps |
|---|---|---|---|---|
| `helper` (const arrow fn) | ✅ | ✅ | VARIABLE | Logger (return type) |
| `CONFIG` (plain const) | ✅ | ✅ | VARIABLE | none (no type refs) |
| `LocalType` (type alias) | ✅ | ✅ | CLASS | none (= string primitive) |
| `LocalInterface` (interface) | ✅ | ✅ | INTERFACE | none (field: number) |

### Task 6 — Run full dc-compare on Prisma/React and classify remaining extras

After Task 5 establishes which categories are extras, run full dc-compare to get real-world numbers:

```bash
# Publish TSE to local Maven first
./gradlew publishToMavenLocal  # in TreeSitterExcavationSite

# Run dc-compare for TS (Prisma) and JS (React) — see dc-compare workflow in memory
```

Expected direction of change vs current numbers:
- "only-in-main" nodes (1630 TS / 962 JS): should drop significantly
- "only-in-feature" nodes (190 TS / 262 JS): will increase due to extra declaration types identified in Task 5

- [x] Publish TSE to local Maven (already published from prior session)
- [x] Run dc-compare for TS (Prisma) and JS (React) (2026-05-21)
- [x] Record new numbers in Notes
- [x] Confirm extra categories match Task 5 findings

**Results (2026-05-21, TSE 0.10.0-local — filter removal only, before DEFAULT_EXPORT fix):**

TS (Prisma): only-in-main 1630→116 (−93%), only-in-feat 190→265 (+75), missing edges 3483→12048 (body-scan gap now visible on more nodes), extra edges 251→673, NodeType mismatches 14 (UNKNOWN→CLASS improvements for abstract classes)

JS (React): only-in-main 962→96 (−90%), only-in-feat 262→4448 (+4186!), missing edges 14351→25952, extra edges 1435→8832, NodeType mismatches 116 (CLASS/FUNCTION/VARIABLE→REEXPORT)

JS only-in-feat spike (+4186): non-exported consts/functions/interfaces from config and script files (_eslintrc, babel_config-*, next_config, playwright_config, scripts/*.ts). DC main didn't extract these because it only scanned exported declarations. TSE now extracts them with filter removed.

Remaining only-in-main (116 TS, 96 JS): destructured-import VARIABLE nodes (DC main creates named nodes for destructured bindings like `{ cwd }`, `{ builtins: ScalarColumnType }`) and REEXPORT chains from barrel index files.

**Updated results (2026-05-22, TSE 0.10.0-local — filter removal + DEFAULT_EXPORT type fix + wildcard reexport collision fix):**

TS (Prisma): only-in-main 116→**7**, only-in-feat 265, true regressions 12048→**11938**, extra edges 672, NodeType mismatches 14→**23** (all accepted: REEXPORT→VARIABLE/CLASS, UNKNOWN→CLASS/FUNCTION, CLASS→VARIABLE)

JS (React): only-in-main 96, only-in-feat 4448, true regressions 25952→**25950**, extra edges **8831**, NodeType mismatches 116→**20** (all accepted: UNKNOWN→FUNCTION ×11, REEXPORT→VARIABLE ×7, REEXPORT→FUNCTION ×2)

Key improvements vs previous run: TS only-in-main closed 116→7 (DEFAULT_EXPORT fix); JS NodeType mismatches dropped 116→20 (REEXPORT type fix + wildcard name-collision fix). The remaining NodeType diffs are all improvements (TSE correctly identifies types DC main couldn't distinguish).

**Note on "true regressions":** `analyze2.js` uses this term for edges present in DC main but absent in TSE feature, where both source and target nodes are shared. It does NOT mean TSE is wrong. Almost all of these are DC main's whole-file body-scan over-attribution: DC attributes every import in a file to every declaration in that file regardless of actual usage. TSE correctly scopes dependencies per declaration. The increase (11923 TS, 25419 JS) vs v0.9.0 is expected — more declaration nodes now exist (filter removed), so DC main's over-attribution is visible on more nodes.

### Task 7 — Classify JS only-in-feat extras and decide on surgical filtering

The JS only-in-feat jumped from 262 to 4448 after removing the export filter. DC main's legacy JS TSQuery patterns have implicit export-only behavior, so these extras are nodes TSE now extracts that DC main never did.

Sample the extras to classify by category:

- **Config/script files** (eslintrc, babel.config, next.config, playwright.config): non-exported consts — likely improvements
- **node_modules types** (notistack, hermes-parser): check if these are from files in the project root or actually in node_modules
- **Bare VARIABLE/FUNCTION declarations** in app code: check whether DC main's JS analyzer simply never extracted non-exported declarations

For each category: decide accept (improvement over DC main) or filter (genuine noise).

- [x] Sample only-in-feat JS nodes across categories
- [x] Classify each category as accept or filter
- [x] Update Notes with accepted differences and rationale

**Findings (2026-05-21):**

| Category | Count (est.) | Verdict |
|---|---|---|
| Config/tooling files (eslintrc, babel.config, next.config, playwright.config) | ~800–900 | Accept — DC TSQuery missed these |
| Application code (src/, packages/, non-exported fns/classes) | ~2000–2200 | Accept — correct improvement |
| DEFAULT_EXPORT/REEXPORT patterns | ~150–200 | Accept |
| Script files (scripts/) | ~150–200 | Accept (borderline, but legitimate source) |
| node_modules leakage (notistack, hermes-parser) | 3 | Accept — DC's walker already excludes `node_modules` (see `IgnoredDirectories.kt`); these 3 nodes come from project source files with package-like directory names, not from `node_modules` itself. No fix needed. |

All categories accepted. No surgical filtering needed. The ~600–700 node_modules leakage estimate was incorrect — DC's `RootDirectoryWalker` already excludes `node_modules` via `ignoredDirectories()`. Actual count is 3 nodes, all from project source paths that happen to resemble package names.

### Task 8 — Investigate CLASS vs REEXPORT NodeType mismatches in JS

116 NodeType mismatches in JS (React), breakdown:
- `CLASS→REEXPORT`: 46
- `FUNCTION→REEXPORT`: 31
- `VARIABLE→REEXPORT`: 28
- `UNKNOWN→FUNCTION`: 11 — TSE correctly identifies these (accepted improvement)

- [x] Identify source pattern behind CLASS/FUNCTION/VARIABLE→REEXPORT mismatches
- [x] Determine correct nodeType
- [x] Fix: resolve DEFAULT_EXPORT type from locally declared identifier
- [x] Run dc-compare on mini repo to confirm improvement

**Findings (2026-05-21):**

All 105 CLASS/FUNCTION/VARIABLE→REEXPORT mismatches are `name=default` nodes from the `export default <identifier>` pattern (e.g. `export default Visitor` where `Visitor` is a class declared earlier). Added `src/visitor.js` to mini repo to demonstrate.

Root cause and decision: TSE discarded type information it already had. At the point where the `DefaultExport.Reexport` branch runs, the `declarations` list already contains `Visitor:CLASS`. Passing REEXPORT unconditionally was a bug — the `default` node in the graph represents *what the module's default export is*, which is a CLASS/FUNCTION/VARIABLE, not a re-export binding.

Fix (2026-05-21): In `DeclarationExtractor.extract()`, `DefaultExport.Reexport` branch now looks up `shape.name` in `declarations` and uses that type; falls back to REEXPORT when the identifier is not locally declared (e.g. `export default SomeExternalThing`). Three tests updated (JS + 2 TS). All tests green.

The extra `Visitor:CLASS` node (only-in-feat) is intentionally kept — it is a real declaration and removing it would require fragile cross-reference checking. Accepted as improvement over DC main.