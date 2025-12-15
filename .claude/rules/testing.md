---
paths: src/test/**/*.kt
---

# Testing Conventions

## Test File Structure

Tests are located in `src/test/kotlin/.../languages/<lang>/`:
- `<Lang>MetricsTest.kt` for metrics
- `<Lang>ExtractionTest.kt` for extraction

## Test Structure

Use Arrange-Act-Assert pattern with comments:

```kotlin
@Test
fun `should count if statements for complexity`() {
    // Arrange
    val code = """if (x > 0) { return 1 }"""

    // Act
    val result = TreeSitterMetrics.parse(code, Language.KOTLIN)

    // Assert
    assertThat(result.complexity).isEqualTo(1.0)
}
```

## Test Naming

Always start with "should" (e.g., `should count lambda expressions for complexity`)

## Collection Assertions

Prefer `containsExactly` over `contains` in extraction tests for precise verification of extracted elements.

## Running Tests

```bash
# Run all tests
./gradlew test

# Run tests for a specific language
./gradlew test --tests "*java*"
./gradlew test --tests "*kotlin*"

# Run extraction tests
./gradlew test --tests "*ExtractionTest"

# Run metrics tests
./gradlew test --tests "*MetricsTest"
```
