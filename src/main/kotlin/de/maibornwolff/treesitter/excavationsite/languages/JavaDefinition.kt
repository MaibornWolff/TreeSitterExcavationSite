package de.maibornwolff.treesitter.excavationsite.languages
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.java.extractInstanceofPatternVariable
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.java.extractLambdaSingleParameter
import de.maibornwolff.treesitter.excavationsite.shared.domain.CommentFormats
import de.maibornwolff.treesitter.excavationsite.shared.domain.Extract
import de.maibornwolff.treesitter.excavationsite.shared.domain.ExtractionStrategy
import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric
import de.maibornwolff.treesitter.excavationsite.shared.domain.MetricCondition
import de.maibornwolff.treesitter.excavationsite.shared.domain.StringFormats

/**
 * Java language definition - combines metrics and extraction.
 *
 * Migrated from:
 * - metrics/JavaNodeTypes.kt
 * - extraction/JavaExtractionDictionary.kt
 * - extraction/JavaExtractionConfig.kt
 * - extraction/JavaExtractionCustomExtractors.kt
 */
object JavaDefinition : LanguageDefinition {
    // ========== Node Type Constants ==========

    private const val IDENTIFIER = "identifier"
    private const val VARIABLE_DECLARATOR = "variable_declarator"
    private const val TYPE_IDENTIFIER = "type_identifier"

    // ========== Metrics Mapping ==========

    override val nodeMetrics: Map<String, Set<Metric>> = buildMap {
        // Logic complexity
        put("if_statement", setOf(Metric.LogicComplexity))
        put("do_statement", setOf(Metric.LogicComplexity))
        put("for_statement", setOf(Metric.LogicComplexity))
        put("while_statement", setOf(Metric.LogicComplexity))
        put("enhanced_for_statement", setOf(Metric.LogicComplexity))
        put("ternary_expression", setOf(Metric.LogicComplexity))
        put("switch_label", setOf(Metric.LogicComplexity))
        put("catch_clause", setOf(Metric.LogicComplexity))

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

        // Function complexity
        put("constructor_declaration", setOf(Metric.FunctionComplexity, Metric.Function))
        put("method_declaration", setOf(Metric.FunctionComplexity, Metric.Function))
        put("lambda_expression", setOf(Metric.FunctionComplexity))
        put("static_initializer", setOf(Metric.FunctionComplexity))
        put("compact_constructor_declaration", setOf(Metric.FunctionComplexity, Metric.Function))

        // Number of functions - variable declarator with lambda (conditional)
        put(
            "variable_declarator",
            setOf(
                Metric.FunctionConditional(
                    MetricCondition.ChildFieldMatches(
                        fieldName = "value",
                        allowedValues = setOf("lambda_expression")
                    )
                )
            )
        )

        // Function body
        put("block", setOf(Metric.FunctionBody))

        // Function parameters
        put("formal_parameter", setOf(Metric.Parameter))

        // Message chains
        put("method_invocation", setOf(Metric.MessageChain, Metric.MessageChainCall))
        put("field_access", setOf(Metric.MessageChain))

        // Comment lines
        put("block_comment", setOf(Metric.CommentLine))
        put("line_comment", setOf(Metric.CommentLine))
    }

    // ========== Extraction Mapping ==========

    override val nodeExtractions: Map<String, Extract> = buildMap {
        // Identifiers - simple first child
        put("class_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("interface_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("enum_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("record_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("annotation_type_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("method_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("constructor_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("annotation_type_element_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("formal_parameter", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("catch_formal_parameter", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("enum_constant", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("resource", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("enhanced_for_statement", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("type_pattern", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("record_pattern_component", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))

        // Identifiers - nested in variable declarator
        put("field_declaration", Extract.Identifier(single = ExtractionStrategy.NestedInChild(VARIABLE_DECLARATOR, IDENTIFIER)))
        put("local_variable_declaration", Extract.Identifier(single = ExtractionStrategy.NestedInChild(VARIABLE_DECLARATOR, IDENTIFIER)))
        put("spread_parameter", Extract.Identifier(single = ExtractionStrategy.NestedInChild(VARIABLE_DECLARATOR, IDENTIFIER)))

        // Identifiers - first child by multiple types
        put("type_parameter", Extract.Identifier(single = ExtractionStrategy.FirstChildByTypes(TYPE_IDENTIFIER, IDENTIFIER)))

        // Identifiers - multiple extraction
        put("inferred_parameters", Extract.Identifier(multi = ExtractionStrategy.AllChildrenByType(IDENTIFIER)))

        // Identifiers - custom extractors
        put("lambda_expression", Extract.Identifier(customSingle = ::extractLambdaSingleParameter))
        put("instanceof_expression", Extract.Identifier(customSingle = ::extractInstanceofPatternVariable))

        // Comments
        put("line_comment", Extract.Comment(CommentFormats.Line("//")))
        put("block_comment", Extract.Comment(CommentFormats.Block))

        // Strings
        put("string_literal", Extract.StringLiteral(format = StringFormats.JavaTextBlock))
    }
}
