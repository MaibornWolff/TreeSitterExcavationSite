package de.maibornwolff.treesitter.excavationsite.languages
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.bash.extractAliasName
import de.maibornwolff.treesitter.excavationsite.shared.domain.CommentFormats
import de.maibornwolff.treesitter.excavationsite.shared.domain.Extract
import de.maibornwolff.treesitter.excavationsite.shared.domain.ExtractionStrategy
import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric
import de.maibornwolff.treesitter.excavationsite.shared.domain.MetricCondition
import de.maibornwolff.treesitter.excavationsite.shared.domain.StringFormats

/**
 * Bash language definition - combines metrics and extraction.
 *
 * Migrated from:
 * - metrics/BashNodeTypes.kt
 * - extraction/BashExtractionDictionary.kt
 * - extraction/BashExtractionConfig.kt
 * - extraction/BashExtractionCustomExtractors.kt
 */
object BashDefinition : LanguageDefinition {
    // ========== Node Type Constants ==========

    private const val WORD = "word"
    private const val VARIABLE_NAME = "variable_name"

    // ========== Metrics Mapping ==========

    override val nodeMetrics: Map<String, Set<Metric>> = buildMap {
        // Logic complexity
        put("if_statement", setOf(Metric.LogicComplexity))
        put("elif_clause", setOf(Metric.LogicComplexity))
        put("for_statement", setOf(Metric.LogicComplexity))
        put("while_statement", setOf(Metric.LogicComplexity))
        put("c_style_for_statement", setOf(Metric.LogicComplexity))
        put("ternary_expression", setOf(Metric.LogicComplexity))
        put("list", setOf(Metric.LogicComplexity))
        put("case_item", setOf(Metric.LogicComplexity))

        // Logic complexity - conditional (binary expressions with && or ||)
        put(
            "binary_expression",
            setOf(
                Metric.LogicComplexityConditional(
                    MetricCondition.ChildFieldMatches(
                        fieldName = "operator",
                        allowedValues = setOf("&&", "||")
                    )
                )
            )
        )

        // Function complexity and number of functions
        put("function_definition", setOf(Metric.FunctionComplexity, Metric.Function))

        // Function body
        put("compound_statement", setOf(Metric.FunctionBody))

        // Comment lines
        put("comment", setOf(Metric.CommentLine))
    }

    // ========== Extraction Mapping ==========

    override val nodeExtractions: Map<String, Extract> = buildMap {
        // Identifiers - function definitions
        put("function_definition", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(WORD)))

        // Identifiers - variable assignments and loop variables
        put("variable_assignment", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(VARIABLE_NAME)))
        put("for_statement", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(VARIABLE_NAME)))
        put("select_statement", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(VARIABLE_NAME)))

        // Identifiers - custom extractor for alias definitions
        put("command", Extract.Identifier(customSingle = ::extractAliasName))

        // Comments
        put("comment", Extract.Comment(CommentFormats.Line("#")))

        // Strings
        put("string", Extract.StringLiteral(format = StringFormats.Quoted()))
        put("raw_string", Extract.StringLiteral(format = StringFormats.Quoted(stripSingleQuotes = true)))
        put("heredoc_body", Extract.StringLiteral(format = StringFormats.Trimmed))
    }
}
