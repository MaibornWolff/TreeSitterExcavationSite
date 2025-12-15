---
paths: src/main/kotlin/**/api/**/*.kt
---

# API Usage Patterns

## Metrics API

```kotlin
import de.maibornwolff.treesitter.excavationsite.api.TreeSitterMetrics
import de.maibornwolff.treesitter.excavationsite.api.Language

val result = TreeSitterMetrics.parse(code, Language.KOTLIN)

// Access metrics via convenience properties
result.complexity        // Total complexity (logic + function)
result.logicComplexity   // Control flow only
result.linesOfCode       // Total lines
result.realLinesOfCode   // Non-empty, non-comment lines
result.commentLines      // Comment lines
result.numberOfFunctions // Function count
result.messageChains     // Long method chains (4+)

// Per-function metrics
result.perFunctionMetrics["max_complexity_per_function"]
result.perFunctionMetrics["mean_parameters_per_function"]
```

## Extraction API

```kotlin
import de.maibornwolff.treesitter.excavationsite.api.TreeSitterExtraction
import de.maibornwolff.treesitter.excavationsite.api.Language

val result = TreeSitterExtraction.extract(code, Language.KOTLIN)

result.identifiers  // [add, a, b]
result.comments     // [Calculate the sum...]
result.strings      // []

result.extractedTexts.forEach { item ->
    println("${item.context}: ${item.text}")
}
```

## File-Level Metrics

| Metric | Description |
|--------|-------------|
| `complexity` | Total complexity (logic + function) |
| `logic_complexity` | Control flow only (if, for, while, etc.) |
| `lines_of_code` | Total lines including blanks |
| `real_lines_of_code` | Non-blank, non-comment lines |
| `comment_lines` | Lines with comments |
| `number_of_functions` | Function/method count |
| `message_chains` | Method chains with 4+ calls |

## Per-Function Metrics

Aggregations (max, min, mean, median) for:
- `complexity_per_function`
- `parameters_per_function`
- `rloc_per_function`
