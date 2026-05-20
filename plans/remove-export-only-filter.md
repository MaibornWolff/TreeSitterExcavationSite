---
name: remove-export-only-filter
issue:
state: todo
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

- [ ] Complete Task 1: Add failing test for non-exported declaration extraction
- [ ] Complete Task 2: Remove filter gate + expand `extractLocalDeclarationNames`
- [ ] Complete Task 3: Update tests asserting non-exported exclusion
- [ ] Complete Task 4: Remove dead code

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

- [ ] Add the test
- [ ] Run `./gradlew test --tests "*TypescriptDependency*"` — must FAIL with `logVisit not found`

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

- [ ] Apply both changes
- [ ] Run `./gradlew test --tests "*TypescriptDependency*"` — Task 1 test must now PASS
- [ ] Run `./gradlew test --tests "*JavascriptDependency*"` — two tests will FAIL (expected — fixed in Task 3)

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

- [ ] Apply changes
- [ ] Run `./gradlew test` — all tests must be GREEN
- [ ] Commit: `fix(js,ts): include non-exported top-level declarations in dependency graph`

### Task 4 — Remove dead code

**File:** `src/main/kotlin/de/maibornwolff/treesitter/excavationsite/languages/javascript/extractors/DeclarationExtractor.kt`

Delete the following functions from `DeclarationPrepass`:
- `collectExportReferencedLocalNames()`
- `exportClauseOriginalNames()` (private, only called by the above)
- `extractCJSExportedNames()` (private, only called by the above)

Delete from `DeclarationExtractor`:
- `declarationNamesIncludes()` (private, now unreachable)

- [ ] Delete all four functions
- [ ] Run `./gradlew ktlintFormat`
- [ ] Run `./gradlew test` — all tests must remain GREEN
- [ ] Commit: `refactor(js,ts): remove dead export-filter code after filter removal`

## Notes

- The bare `namespace Foo {}` TS test (`should not extract namespace declaration without export keyword`) still passes after this change: tree-sitter wraps it in `expression_statement → internal_module`, so the top-level child seen by `extract()` is `expression_statement`, which is not in `DECLARATION_NODE_TYPES`.
- `extractNamesFromExportStatement` and `extractNamesFromNode` in `DeclarationPrepass` are NOT dead — they remain used by the updated `extractLocalDeclarationNames`.
- The 1630/962 "only-in-main" nodes in dc-compare should largely close after this fix. A dc-compare rerun is recommended after merging.
- The 3483/14351 "true regressions" (missing edges) are a separate body-scan gap unrelated to this filter — do not expect those to close.