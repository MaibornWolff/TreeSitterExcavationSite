# TDD Workflow

## Red → Green → Refactor

1. Write one failing test
2. Write minimum code to pass
3. Run all tests (must be green)
4. Refactor if needed
5. Commit
6. Repeat

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

## Refactoring Rules

Only when tests are green, one change at a time, run tests after each step.

## Avoid

- Test names without "should"
- Tests without Arrange/Act/Assert comments