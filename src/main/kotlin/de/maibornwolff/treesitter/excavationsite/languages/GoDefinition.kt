package de.maibornwolff.treesitter.excavationsite.languages
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.go.extractAllFieldDeclarationIdentifiers
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.go.extractAllFromConstDeclaration
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.go.extractAllFromExpressionList
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.go.extractAllFromVarDeclaration
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.go.extractFieldDeclarationIdentifier
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.go.extractFromConstDeclaration
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.go.extractFromExpressionList
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.go.extractFromVarDeclaration
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.go.extractIdentifiersFromExpressionListChild
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.go.extractTypeDeclarationIdentifier
import de.maibornwolff.treesitter.excavationsite.shared.domain.CommentFormats
import de.maibornwolff.treesitter.excavationsite.shared.domain.Extract
import de.maibornwolff.treesitter.excavationsite.shared.domain.ExtractionStrategy
import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric
import de.maibornwolff.treesitter.excavationsite.shared.domain.MetricCondition
import de.maibornwolff.treesitter.excavationsite.shared.domain.StringFormats

/**
 * Go language definition - combines metrics and extraction.
 *
 * Migrated from:
 * - metrics/GoNodeTypes.kt
 * - extraction/GoExtractionDictionary.kt
 * - extraction/GoExtractionConfig.kt
 * - extraction/GoExtractionCustomExtractors.kt
 */
object GoDefinition : LanguageDefinition {
    // ========== Node Type Constants ==========

    private const val IDENTIFIER = "identifier"
    private const val FIELD_IDENTIFIER = "field_identifier"

    // ========== Metrics Mapping ==========

    override val nodeMetrics: Map<String, Set<Metric>> = buildMap {
        // Logic complexity
        put("if_statement", setOf(Metric.LogicComplexity))
        put("for_statement", setOf(Metric.LogicComplexity))
        put("communication_case", setOf(Metric.LogicComplexity))
        put("expression_case", setOf(Metric.LogicComplexity))
        put("type_case", setOf(Metric.LogicComplexity))
        put("default_case", setOf(Metric.LogicComplexity))

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
        put("method_declaration", setOf(Metric.FunctionComplexity, Metric.Function))
        put("func_literal", setOf(Metric.FunctionComplexity, Metric.Function))
        put("function_declaration", setOf(Metric.FunctionComplexity, Metric.Function))
        put("method_spec", setOf(Metric.FunctionComplexity, Metric.Function))

        // Function body
        put("block", setOf(Metric.FunctionBody))

        // Function parameters
        put("parameter_declaration", setOf(Metric.Parameter))

        // Message chains
        put("call_expression", setOf(Metric.MessageChain, Metric.MessageChainCall))
        put("selector_expression", setOf(Metric.MessageChain))

        // Comment lines
        put("comment", setOf(Metric.CommentLine))
    }

    // ========== Extraction Mapping ==========

    override val nodeExtractions: Map<String, Extract> = buildMap {
        // Identifiers - simple first child
        put("function_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put(
            "parameter_declaration",
            Extract.Identifier(
                single = ExtractionStrategy.FirstChildByType(IDENTIFIER),
                multi = ExtractionStrategy.AllChildrenByType(IDENTIFIER)
            )
        )
        put(
            "type_parameter_declaration",
            Extract.Identifier(
                single = ExtractionStrategy.FirstChildByType(IDENTIFIER),
                multi = ExtractionStrategy.AllChildrenByType(IDENTIFIER)
            )
        )
        put("method_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(FIELD_IDENTIFIER)))
        put("method_elem", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(FIELD_IDENTIFIER)))

        // Identifiers - custom extractors
        put("type_declaration", Extract.Identifier(customSingle = ::extractTypeDeclarationIdentifier))
        put(
            "var_declaration",
            Extract.Identifier(
                customSingle = ::extractFromVarDeclaration,
                customMulti = ::extractAllFromVarDeclaration
            )
        )
        put(
            "const_declaration",
            Extract.Identifier(
                customSingle = ::extractFromConstDeclaration,
                customMulti = ::extractAllFromConstDeclaration
            )
        )
        put(
            "short_var_declaration",
            Extract.Identifier(
                customSingle = ::extractFromExpressionList,
                customMulti = ::extractAllFromExpressionList
            )
        )
        put(
            "field_declaration",
            Extract.Identifier(
                customSingle = ::extractFieldDeclarationIdentifier,
                customMulti = ::extractAllFieldDeclarationIdentifiers
            )
        )
        put("receive_statement", Extract.Identifier(customMulti = ::extractAllFromExpressionList))
        put("range_clause", Extract.Identifier(customMulti = ::extractIdentifiersFromExpressionListChild))
        put("type_switch_statement", Extract.Identifier(customMulti = ::extractIdentifiersFromExpressionListChild))

        // Comments
        put("comment", Extract.Comment(CommentFormats.AutoDetect))

        // Strings
        put("raw_string_literal", Extract.StringLiteral(format = StringFormats.Template))
        put("interpreted_string_literal", Extract.StringLiteral(format = StringFormats.Quoted()))
        put("rune_literal", Extract.StringLiteral(format = StringFormats.Quoted(stripSingleQuotes = true)))
    }
}
