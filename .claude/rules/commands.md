# Development Commands

```bash
# Build the project
./gradlew build

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "JavaMetricsTest"

# Run tests matching a pattern
./gradlew test --tests "*MetricsTest"

# Check code style
./gradlew ktlintCheck

# Auto-format code
./gradlew ktlintFormat

# Clean and rebuild
./gradlew clean build

# Publish to local Maven repository
./gradlew publishToMavenLocal

# Run tests for a specific language
./gradlew test --tests "*java*"
./gradlew test --tests "*kotlin*"

# Run extraction tests
./gradlew test --tests "*ExtractionTest"

# Run with test output
./gradlew test --info
```

Test reports are generated at: `build/reports/tests/test/index.html`