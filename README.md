# TreeSitter ExcavationSite

A Kotlin library for calculating code metrics and extracting text from source code using [TreeSitter](https://tree-sitter.github.io/tree-sitter/).

## Features

- **Code Metrics**: Complexity, lines of code, comment lines, function counts, code smells, per-function aggregations
- **Text Extraction**: Identifiers, comments, and string literals with context
- **16 Languages and Frameworks**: Java, Kotlin, TypeScript, JavaScript, Python, Go, PHP, Ruby, Swift, Bash, C#, C++, C, Objective-C, Vue, ABL
- **Zero External Dependencies**: Only TreeSitter bindings required

## Requirements

- Java 11-21
- Gradle 8.x (wrapper included)

## Installation

### Gradle (Composite Build) Not yet on maven
```kotlin
// settings.gradle.kts
includeBuild("/path/to/TreeSitterLibrary")

// build.gradle.kts
dependencies {
    implementation("de.maibornwolff.treesitter.excavationsite:treesitter-excavationsite:0.1.0")
}
```

## Quick Start

### Code Metrics

```kotlin
import de.maibornwolff.treesitter.excavationsite.api.TreeSitterMetrics
import de.maibornwolff.treesitter.excavationsite.api.Language

val code = """
    fun example(x: Int): Int {
        return if (x > 0) x else -x
    }
""".trimIndent()

val result = TreeSitterMetrics.parse(code, Language.KOTLIN)

println(result.complexity)        // Total complexity
println(result.logicComplexity)   // Control flow complexity
println(result.linesOfCode)       // Total lines
println(result.realLinesOfCode)   // Non-empty, non-comment lines
println(result.commentLines)      // Comment lines
println(result.numberOfFunctions) // Function count
println(result.messageChains)     // Method chains (4+ calls)
println(result.longMethod)        // Functions exceeding length threshold
println(result.longParameterList) // Functions with many parameters
println(result.excessiveComments) // Excessive comment indicators
println(result.commentRatio)      // Comment to code ratio

// Per-function metrics
println(result.perFunctionMetrics["max_complexity_per_function"])
println(result.perFunctionMetrics["mean_parameters_per_function"])
```

### Text Extraction

```kotlin
import de.maibornwolff.treesitter.excavationsite.api.TreeSitterExtraction
import de.maibornwolff.treesitter.excavationsite.api.Language

val code = """
    // Calculate sum of two numbers
    fun add(a: Int, b: Int): Int {
        return a + b
    }
""".trimIndent()

val result = TreeSitterExtraction.extract(code, Language.KOTLIN)

println(result.identifiers)  // [add, a, b]
println(result.comments)     // [Calculate sum of two numbers]
println(result.strings)      // []

// All items with context
result.extractedTexts.forEach { item ->
    println("${item.context}: ${item.text}")
}
```

## Supported Languages and Frameworks

| Language | Extension(s) | Metrics | Extraction | Status |
|----------|-------------|---------|------------|--------|
| Java | `.java` | Yes | Yes | Stable |
| Kotlin | `.kt`, `.kts` | Yes | Yes | Stable |
| TypeScript | `.ts`, `.tsx` | Yes | Yes | Stable |
| JavaScript | `.js`, `.jsx`, `.mjs`, `.cjs` | Yes | Yes | Stable |
| Python | `.py` | Yes | Yes | Stable |
| Go | `.go` | Yes | Yes | Stable |
| PHP | `.php` | Yes | Yes | Stable |
| Ruby | `.rb` | Yes | Yes | Stable |
| Swift | `.swift` | Yes | Yes | Stable |
| Bash | `.sh`, `.bash` | Yes | Yes | Stable |
| C# | `.cs` | Yes | Yes | Stable |
| C++ | `.cpp`, `.cc`, `.cxx`, `.hpp`, `.hxx`, `.h` | Yes | Yes | Stable |
| C | `.c` | Yes | Yes | Stable |
| Objective-C | `.m`, `.mm` | Yes | Yes | Stable |
| Vue | `.vue` | Yes | Yes | Stable |
| ABL | `.p`, `.cls`, `.w` | Yes | Yes | Experimental |

## Available Metrics

### File-Level Metrics

| Metric | Description |
|--------|-------------|
| `complexity` | Total cyclomatic complexity (logic + function) |
| `logic_complexity` | Control flow complexity (if, for, while, etc.) |
| `loc` | Total lines including blanks |
| `rloc` | Real lines of code (non-blank, non-comment) |
| `comment_lines` | Lines containing comments |
| `number_of_functions` | Function/method count |
| `message_chains` | Method chains with 4+ calls |
| `long_method` | Functions exceeding length threshold |
| `long_parameter_list` | Functions with excessive parameters |
| `excessive_comments` | Files with excessive commenting |
| `comment_ratio` | Comments to code ratio |

### Per-Function Metrics

Aggregations (max, min, mean, median) for:
- `complexity_per_function`
- `parameters_per_function`
- `rloc_per_function`

## Development

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run specific tests
./gradlew test --tests "JavaMetricsTest"
./gradlew test --tests "*ExtractionTest"

# Code style
./gradlew ktlintCheck
./gradlew ktlintFormat

# Publish locally
./gradlew publishToMavenLocal
```

## Project Structure

```
src/main/kotlin/de/maibornwolff/treesitter/excavationsite/
├── api/                           # Public API (TreeSitterMetrics, TreeSitterExtraction)
├── integration/                   # Feature integration (vertical slice architecture)
│   ├── metrics/                   # Metrics feature
│   │   ├── domain/                # Feature-specific models
│   │   ├── ports/                 # Interfaces (MetricNodeTypes)
│   │   ├── adapters/              # Language definition adapters
│   │   └── calculators/           # Individual metric calculators
│   └── extraction/                # Extraction feature
│       ├── ports/                 # Interfaces (ExtractionNodeTypes)
│       ├── adapters/              # Language definition adapters
│       └── extractors/common/     # Shared extractors
├── languages/                     # Language definitions (16 languages and frameworks)
│   └── <lang>/                    # Per-language directory
│       ├── *Definition.kt         # Combines metric and extraction mappings
│       ├── *MetricMapping.kt      # Metric node mappings
│       ├── *ExtractionMapping.kt  # Extraction node mappings
│       └── extractors/            # Language-specific extractors
└── shared/                        # Cross-cutting concerns
    ├── domain/                    # Core types (Metric, Extract, ExtractionResult, etc.)
    └── infrastructure/walker/     # Tree traversal utilities
```

## License

BSD-3-Clause
