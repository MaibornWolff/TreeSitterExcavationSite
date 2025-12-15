package de.maibornwolff.treesitter.excavationsite.languages
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.cpp.extractFromCatchClause
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.cpp.extractFromDeclaration
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.cpp.extractFromFieldDeclaration
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.cpp.extractFromForRangeLoop
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.cpp.extractFromFriendDeclaration
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.cpp.extractFromFunctionDefinition
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.cpp.extractFromLambdaCaptures
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.cpp.extractFromNamespaceDefinition
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.cpp.extractFromParameterDeclaration
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.cpp.extractFromStructuredBinding
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.cpp.extractFromTypeParameter
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.cpp.extractFromUsingDeclaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.CalculationConfig
import de.maibornwolff.treesitter.excavationsite.shared.domain.CommentFormats
import de.maibornwolff.treesitter.excavationsite.shared.domain.Extract
import de.maibornwolff.treesitter.excavationsite.shared.domain.ExtractionStrategy
import de.maibornwolff.treesitter.excavationsite.shared.domain.IgnoreRule
import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric
import de.maibornwolff.treesitter.excavationsite.shared.domain.MetricCondition
import de.maibornwolff.treesitter.excavationsite.shared.domain.StringFormats

/**
 * C++ language definition - combines metrics and extraction.
 *
 * Migrated from:
 * - metrics/CppNodeTypes.kt
 * - extraction/CppExtractionDictionary.kt
 * - extraction/CppExtractionConfig.kt
 * - extraction/CppExtractionCustomExtractors.kt
 */
object CppDefinition : LanguageDefinition {
    // ========== Node Type Constants ==========

    private const val IDENTIFIER = "identifier"
    private const val TYPE_IDENTIFIER = "type_identifier"
    private const val STRUCTURED_BINDING_DECLARATOR = "structured_binding_declarator"

    // ========== Metrics Mapping ==========

    override val nodeMetrics: Map<String, Set<Metric>> = buildMap {
        // Logic complexity - simple nodes
        put("if_statement", setOf(Metric.LogicComplexity))
        put("do_statement", setOf(Metric.LogicComplexity))
        put("for_statement", setOf(Metric.LogicComplexity))
        put("while_statement", setOf(Metric.LogicComplexity))
        put("for_range_loop", setOf(Metric.LogicComplexity))
        put("conditional_expression", setOf(Metric.LogicComplexity))
        put("case_statement", setOf(Metric.LogicComplexity))
        put("catch_clause", setOf(Metric.LogicComplexity))
        put("seh_except_clause", setOf(Metric.LogicComplexity))

        // Logic complexity - conditional (binary expressions with && || and or xor)
        put(
            "binary_expression",
            setOf(
                Metric.LogicComplexityConditional(
                    MetricCondition.ChildFieldMatches(
                        fieldName = "operator",
                        allowedValues = setOf("&&", "||", "and", "or", "xor")
                    )
                )
            )
        )

        // Function complexity
        put("lambda_expression", setOf(Metric.FunctionComplexity))
        put("function_definition", setOf(Metric.FunctionComplexity, Metric.Function))
        put("abstract_function_declarator", setOf(Metric.FunctionComplexity))
        put("function_declarator", setOf(Metric.FunctionComplexity))

        // Comment lines
        put("comment", setOf(Metric.CommentLine))

        // Number of functions - init_declarator with lambda (conditional)
        put(
            "init_declarator",
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
        put("compound_statement", setOf(Metric.FunctionBody))

        // Function parameters
        put("parameter_declaration", setOf(Metric.Parameter))

        // Message chains
        put("call_expression", setOf(Metric.MessageChain, Metric.MessageChainCall))
        put("field_expression", setOf(Metric.MessageChain))
    }

    // ========== Extraction Mapping ==========

    override val nodeExtractions: Map<String, Extract> = buildMap {
        // Identifiers - simple first child
        put("class_specifier", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(TYPE_IDENTIFIER)))
        put("struct_specifier", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(TYPE_IDENTIFIER)))
        put("enum_specifier", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(TYPE_IDENTIFIER)))
        put("alias_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(TYPE_IDENTIFIER)))
        put("enumerator", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("concept_definition", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))

        // Identifiers - custom extractors
        put("function_definition", Extract.Identifier(customSingle = ::extractFromFunctionDefinition))
        put("namespace_definition", Extract.Identifier(customSingle = ::extractFromNamespaceDefinition))
        put("declaration", Extract.Identifier(customSingle = ::extractFromDeclaration))
        put("field_declaration", Extract.Identifier(customSingle = ::extractFromFieldDeclaration))
        put("parameter_declaration", Extract.Identifier(customSingle = ::extractFromParameterDeclaration))
        put("type_parameter_declaration", Extract.Identifier(customSingle = ::extractFromTypeParameter))
        put("optional_type_parameter_declaration", Extract.Identifier(customSingle = ::extractFromTypeParameter))
        put("for_range_loop", Extract.Identifier(customSingle = ::extractFromForRangeLoop))
        put("catch_clause", Extract.Identifier(customSingle = ::extractFromCatchClause))
        put("using_declaration", Extract.Identifier(customSingle = ::extractFromUsingDeclaration))
        put("friend_declaration", Extract.Identifier(customSingle = ::extractFromFriendDeclaration))

        // Identifiers - multi extractors
        put(STRUCTURED_BINDING_DECLARATOR, Extract.Identifier(customMulti = ::extractFromStructuredBinding))
        put("lambda_capture_specifier", Extract.Identifier(customMulti = ::extractFromLambdaCaptures))

        // Comments
        put("comment", Extract.Comment(CommentFormats.AutoDetect))

        // Strings
        put("string_literal", Extract.StringLiteral(format = StringFormats.Quoted()))
        put("raw_string_literal", Extract.StringLiteral(format = StringFormats.CppRaw))
        put("char_literal", Extract.StringLiteral(format = StringFormats.Quoted(stripSingleQuotes = true)))
    }

    // ========== Calculation Configuration ==========

    private const val ABSTRACT_FUNCTION_DECLARATOR = "abstract_function_declarator"
    private const val LAMBDA_EXPRESSION = "lambda_expression"
    private const val FUNCTION_DECLARATOR = "function_declarator"
    private const val FUNCTION_DEFINITION = "function_definition"

    override val calculationConfig = CalculationConfig(
        ignoreForComplexity = listOf(
            IgnoreRule.TypeWithParentType(ABSTRACT_FUNCTION_DECLARATOR, LAMBDA_EXPRESSION),
            IgnoreRule.TypeWithParentType(FUNCTION_DECLARATOR, FUNCTION_DEFINITION)
        )
    )
}
