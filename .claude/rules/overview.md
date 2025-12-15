# Project Overview

TreeSitterLibrary is a standalone Kotlin library for calculating code metrics using TreeSitter. It was extracted from CodeCharta's UnifiedParser to be reusable across projects.

## Key Features

- **Metrics API**: `TreeSitterMetrics.parse(code, language) -> MetricsResult`
- **Extraction API**: `TreeSitterExtraction.extract(code, language) -> ExtractionResult`
- Support for 14 programming languages (metrics and extraction)
- Metrics: complexity, lines of code, comment lines, function counts, per-function aggregations
- Extraction: identifiers, comments, string literals with context
- No external dependencies beyond TreeSitter

## Requirements

- Java >= 11, <= 21
- Gradle 8.x (wrapper included)

## Supported Languages

- Java, Kotlin, TypeScript, JavaScript, Python, Go, PHP, Ruby, Swift, Bash, C#, C++, C, Objective-C

## Relationship to CodeCharta

This library was extracted from CodeCharta's UnifiedParser. CodeCharta uses it via Gradle composite build:

```kotlin
// CodeCharta's settings.gradle.kts
includeBuild("../TreeSitterLibrary")

// CodeCharta's UnifiedParser uses the library
val result = TreeSitterMetrics.parse(fileContent, language)
```

The library maintains API compatibility with CodeCharta's metric expectations.
