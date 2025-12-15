package de.maibornwolff.treesitter.excavationsite.languages
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.php.collectVariables
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.php.extractAssignmentTargetName
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.php.extractAttributeName
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.php.extractConstName
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.php.extractForeachValueVariable
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.php.extractGlobalVariables
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.php.extractPropertyName
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.php.extractUseClauseName
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.php.findFirstVariableName
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.php.findNameAfterAs
import de.maibornwolff.treesitter.excavationsite.shared.domain.CommentFormats
import de.maibornwolff.treesitter.excavationsite.shared.domain.Extract
import de.maibornwolff.treesitter.excavationsite.shared.domain.ExtractionStrategy
import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric
import de.maibornwolff.treesitter.excavationsite.shared.domain.MetricCondition
import de.maibornwolff.treesitter.excavationsite.shared.domain.StringFormats

/**
 * PHP language definition - combines metrics and extraction.
 *
 * Migrated from:
 * - metrics/PhpNodeTypes.kt
 * - extraction/PhpExtractionDictionary.kt
 * - extraction/PhpExtractionConfig.kt
 * - extraction/PhpExtractionCustomExtractors.kt
 */
object PhpDefinition : LanguageDefinition {
    // ========== Node Type Constants ==========

    private const val NAME = "name"
    private const val NAMESPACE_NAME = "namespace_name"
    private const val PAIR = "pair"

    // ========== Metrics Mapping ==========

    override val nodeMetrics: Map<String, Set<Metric>> = buildMap {
        // Logic complexity - simple
        put("if_statement", setOf(Metric.LogicComplexity))
        put("else_if_clause", setOf(Metric.LogicComplexity))
        put("do_statement", setOf(Metric.LogicComplexity))
        put("for_statement", setOf(Metric.LogicComplexity))
        put("while_statement", setOf(Metric.LogicComplexity))
        put("foreach_statement", setOf(Metric.LogicComplexity))
        put("conditional_expression", setOf(Metric.LogicComplexity))
        put("case_statement", setOf(Metric.LogicComplexity))
        put("default_statement", setOf(Metric.LogicComplexity))
        put("match_conditional_expression", setOf(Metric.LogicComplexity))
        put("match_default_expression", setOf(Metric.LogicComplexity))
        put("catch_clause", setOf(Metric.LogicComplexity))

        // Logic complexity - conditional (binary expressions with logical operators)
        put(
            "binary_expression",
            setOf(
                Metric.LogicComplexityConditional(
                    MetricCondition.ChildFieldMatches(
                        fieldName = "operator",
                        allowedValues = setOf("&&", "||", "??", "and", "or", "xor")
                    )
                )
            )
        )

        // Function complexity
        put("method_declaration", setOf(Metric.FunctionComplexity, Metric.Function))
        put("lambda_expression", setOf(Metric.FunctionComplexity))
        put("arrow_function", setOf(Metric.FunctionComplexity))
        put("anonymous_function", setOf(Metric.FunctionComplexity))
        put("function_definition", setOf(Metric.FunctionComplexity, Metric.Function))
        put("function_static_declaration", setOf(Metric.FunctionComplexity, Metric.Function))

        // Number of functions - assignment with function (conditional)
        put(
            "assignment_expression",
            setOf(
                Metric.FunctionConditional(
                    MetricCondition.ChildFieldMatches(
                        fieldName = "right",
                        allowedValues = setOf("anonymous_function", "arrow_function", "lambda_expression")
                    )
                )
            )
        )

        // Function body
        put("compound_statement", setOf(Metric.FunctionBody))

        // Function parameters
        put("simple_parameter", setOf(Metric.Parameter))

        // Message chains
        put("member_call_expression", setOf(Metric.MessageChain, Metric.MessageChainCall))
        put("scoped_call_expression", setOf(Metric.MessageChain, Metric.MessageChainCall))
        put("member_access_expression", setOf(Metric.MessageChain))

        // Comment lines
        put("comment", setOf(Metric.CommentLine))
    }

    // ========== Extraction Mapping ==========

    override val nodeExtractions: Map<String, Extract> = buildMap {
        // Identifiers - simple first child
        put("class_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(NAME)))
        put("interface_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(NAME)))
        put("trait_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(NAME)))
        put("enum_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(NAME)))
        put("function_definition", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(NAME)))
        put("method_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(NAME)))
        put("namespace_definition", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(NAMESPACE_NAME)))

        // Identifiers - custom single extractors
        put("property_declaration", Extract.Identifier(customSingle = ::extractPropertyName))
        put("const_declaration", Extract.Identifier(customSingle = ::extractConstName))
        put("simple_parameter", Extract.Identifier(customSingle = ::findFirstVariableName))
        put("property_promotion_parameter", Extract.Identifier(customSingle = ::findFirstVariableName))
        put("static_variable_declaration", Extract.Identifier(customSingle = ::findFirstVariableName))
        put("catch_clause", Extract.Identifier(customSingle = ::findFirstVariableName))
        put("assignment_expression", Extract.Identifier(customSingle = ::extractAssignmentTargetName))
        put("foreach_statement", Extract.Identifier(customSingle = ::extractForeachValueVariable))
        put("namespace_use_clause", Extract.Identifier(customSingle = ::extractUseClauseName))
        put("attribute", Extract.Identifier(customSingle = ::extractAttributeName))
        put("use_as_clause", Extract.Identifier(customSingle = ::findNameAfterAs))

        // Identifiers - custom multi extractors
        put(PAIR, Extract.Identifier(customMulti = ::collectVariables))
        put("anonymous_function_use_clause", Extract.Identifier(customMulti = ::collectVariables))
        put("list_literal", Extract.Identifier(customMulti = ::collectVariables))
        put("global_declaration", Extract.Identifier(customMulti = ::extractGlobalVariables))

        // Comments
        put("comment", Extract.Comment(CommentFormats.AutoDetect))

        // Strings - standard formats
        put("string", Extract.StringLiteral(format = StringFormats.Quoted(stripSingleQuotes = true)))
        put("encapsed_string", Extract.StringLiteral(format = StringFormats.Quoted()))

        // Strings - heredoc/nowdoc
        put("heredoc", Extract.StringLiteral(format = StringFormats.PhpHeredoc))
        put("nowdoc", Extract.StringLiteral(format = StringFormats.PhpHeredoc))
    }
}
