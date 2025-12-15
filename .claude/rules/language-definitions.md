---
paths: src/main/kotlin/**/languages/**/*.kt
---

# Language Definitions

## Adding a New Language

1. **Create language definition** in `languages/NewLangDefinition.kt`:

```kotlin
import de.maibornwolff.treesitter.excavationsite.features.metrics.domain.Metric
import de.maibornwolff.treesitter.excavationsite.features.extraction.model.Extract

object NewLangDefinition : LanguageDefinition {
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

3. **Add tests** in `src/test/kotlin/.../languages/newlang/`

## Metric Types (from `features/metrics/domain/Metric.kt`)

- `LogicComplexity` - Control flow (if, for, while, etc.)
- `LogicComplexityConditional` - Conditional matching (e.g., binary expressions with &&, ||)
- `FunctionComplexity` - Function definitions that add to complexity
- `Function` / `FunctionConditional` - Function declarations for counting
- `FunctionBody` - For RLOC per function calculation
- `CommentLine` / `CommentLineConditional` - Comment nodes
- `Parameter` - Function parameters
- `MessageChain` / `MessageChainCall` - Method chain detection

## Extract Types (from `features/extraction/model/Extract.kt`)

- `Identifier(single, multi, customSingle, customMulti)` - Identifier extraction
- `Comment(format, custom)` - Comment text extraction
- `StringLiteral(format, custom)` - String content extraction

## Language-Specific Calculation Behavior

Use `CalculationExtensions` (from `features/metrics/domain/CalculationExtensions.kt`) for special cases:

```kotlin
override val calculationExtensions = CalculationExtensions(
    hasFunctionBodyStartOrEndNode = false,  // Python uses indentation
    ignoreNodeForComplexity = { node, nodeType -> false }
)
```