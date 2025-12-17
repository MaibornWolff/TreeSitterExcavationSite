---
paths: src/main/kotlin/**/languages/**/*.kt
---

# Language Definitions

## Adding a New Language

1. **Create language directory** in `languages/newlang/` with three files:

```kotlin
// languages/newlang/NewLangMetricMapping.kt
package ...languages.newlang

import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric
import de.maibornwolff.treesitter.excavationsite.shared.domain.MetricMapping

object NewLangMetricMapping : MetricMapping {
    override val nodeMetrics: Map<String, Set<Metric>> = buildMap {
        // Logic complexity nodes
        listOf("if_statement", "for_statement", "while_statement")
            .forEach { put(it, setOf(Metric.LogicComplexity)) }

        // Function nodes
        put("function_definition", setOf(Metric.FunctionComplexity, Metric.Function))

        // Comment nodes
        put("comment", setOf(Metric.CommentLine))

        // Function body for RLOC per function
        put("block", setOf(Metric.FunctionBody))

        // Parameters
        put("parameter", setOf(Metric.Parameter))

        // Message chains
        put("call_expression", setOf(Metric.MessageChain, Metric.MessageChainCall))
    }
}
```

```kotlin
// languages/newlang/NewLangExtractionMapping.kt
package ...languages.newlang

import de.maibornwolff.treesitter.excavationsite.shared.domain.*

object NewLangExtractionMapping : ExtractionMapping {
    override val nodeExtractions: Map<String, Extract> = buildMap {
        // Identifiers
        put("function_definition", Extract.Identifier(
            single = ExtractionStrategy.FirstChildByType("identifier")
        ))

        // Comments
        put("comment", Extract.Comment(CommentFormats.Line("//")))

        // Strings
        put("string", Extract.StringLiteral(format = StringFormats.Quoted()))
    }
}
```

```kotlin
// languages/newlang/NewLangDefinition.kt
package ...languages.newlang

import de.maibornwolff.treesitter.excavationsite.shared.domain.*

object NewLangDefinition : LanguageDefinition {
    override val nodeMetrics = NewLangMetricMapping.nodeMetrics
    override val nodeExtractions = NewLangExtractionMapping.nodeExtractions
}
```

2. **Add to Language enum** in `languages/Language.kt` (internal) and update `api/Language.kt` (public):

```kotlin
// languages/Language.kt (internal)
NEW_LANG(
    primaryExtension = ".ext",
    otherExtensions = setOf(".ext2"),
    treeSitterLanguageProvider = { TreeSitterNewLang() },
    languageDefinitionProvider = { NewLangDefinition }
)
```

3. **Add language-specific extractors** (if needed) in `languages/newlang/extractors/`

4. **Add tests** in `src/test/kotlin/.../languages/newlang/`

## Metric Types (from `shared/domain/Metric.kt`)

- `LogicComplexity` - Control flow (if, for, while, etc.)
- `LogicComplexityConditional` - Conditional matching (e.g., binary expressions with &&, ||)
- `FunctionComplexity` - Function definitions that add to complexity
- `Function` / `FunctionConditional` - Function declarations for counting
- `FunctionBody` - For RLOC per function calculation
- `CommentLine` / `CommentLineConditional` - Comment nodes
- `Parameter` - Function parameters
- `MessageChain` / `MessageChainCall` - Method chain detection

## Extract Types (from `shared/domain/Extract.kt`)

- `Identifier(single, multi, customSingle, customMulti)` - Identifier extraction
- `Comment(format, custom)` - Comment text extraction
- `StringLiteral(format, custom)` - String content extraction

## Language-Specific Calculation Behavior

Use `CalculationExtensions` (from `shared/domain/CalculationExtensions.kt`) for special cases:

```kotlin
override val calculationExtensions = CalculationExtensions(
    hasFunctionBodyStartOrEndNode = false,  // Python uses indentation
    ignoreNodeForComplexity = { node, nodeType -> false }
)
```

## Directory Structure

Each language follows this pattern:

```
languages/
└── java/
    ├── JavaDefinition.kt         # Combines mappings
    ├── JavaMetricMapping.kt      # Metric node mappings
    ├── JavaExtractionMapping.kt  # Extraction node mappings
    └── extractors/               # Language-specific extractors
        └── SomeExtractor.kt
```
