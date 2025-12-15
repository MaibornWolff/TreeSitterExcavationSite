package de.maibornwolff.treesitter.excavationsite.languages
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.c.extractAllDeclarators
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.c.extractFromDeclaration
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.c.extractFromFieldDeclaration
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.c.extractFromFunctionDefinition
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.c.extractFromInitializerPair
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.c.extractFromParameterDeclaration
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.c.extractFromTypedef
import de.maibornwolff.treesitter.excavationsite.shared.domain.CalculationConfig
import de.maibornwolff.treesitter.excavationsite.shared.domain.CommentFormats
import de.maibornwolff.treesitter.excavationsite.shared.domain.Extract
import de.maibornwolff.treesitter.excavationsite.shared.domain.ExtractionStrategy
import de.maibornwolff.treesitter.excavationsite.shared.domain.IgnoreRule
import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric
import de.maibornwolff.treesitter.excavationsite.shared.domain.MetricCondition
import de.maibornwolff.treesitter.excavationsite.shared.domain.StringFormats

/**
 * C language definition - combines metrics and extraction.
 *
 * Migrated from:
 * - metrics/CNodeTypes.kt
 * - extraction/CExtractionDictionary.kt
 * - extraction/CExtractionConfig.kt
 * - extraction/CExtractionCustomExtractors.kt
 */
object CDefinition : LanguageDefinition {
    // ========== Node Type Constants ==========

    private const val IDENTIFIER = "identifier"
    private const val TYPE_IDENTIFIER = "type_identifier"
    private const val STATEMENT_IDENTIFIER = "statement_identifier"

    // ========== Metrics Mapping ==========

    override val nodeMetrics: Map<String, Set<Metric>> = buildMap {
        // Logic complexity
        put("if_statement", setOf(Metric.LogicComplexity))
        put("do_statement", setOf(Metric.LogicComplexity))
        put("for_statement", setOf(Metric.LogicComplexity))
        put("while_statement", setOf(Metric.LogicComplexity))
        put("conditional_expression", setOf(Metric.LogicComplexity))
        put("case_statement", setOf(Metric.LogicComplexity))
        put("seh_except_clause", setOf(Metric.LogicComplexity))

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
        put("function_definition", setOf(Metric.FunctionComplexity, Metric.Function))
        put("abstract_function_declarator", setOf(Metric.FunctionComplexity))
        put("function_declarator", setOf(Metric.FunctionComplexity))

        // Function body
        put("compound_statement", setOf(Metric.FunctionBody))

        // Function parameters
        put("parameter_declaration", setOf(Metric.Parameter))

        // Message chains
        put("call_expression", setOf(Metric.MessageChain, Metric.MessageChainCall))
        put("field_expression", setOf(Metric.MessageChain))

        // Comment lines
        put("comment", setOf(Metric.CommentLine))
    }

    // ========== Extraction Mapping ==========

    override val nodeExtractions: Map<String, Extract> = buildMap {
        // Identifiers - simple first child
        put("struct_specifier", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(TYPE_IDENTIFIER)))
        put("enum_specifier", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(TYPE_IDENTIFIER)))
        put("union_specifier", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(TYPE_IDENTIFIER)))
        put("enumerator", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("preproc_def", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("preproc_function_def", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("preproc_ifdef", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("preproc_ifndef", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("labeled_statement", Extract.Identifier(single = ExtractionStrategy.FirstChildByTypes(STATEMENT_IDENTIFIER, IDENTIFIER)))

        // Identifiers - custom extractors for complex declarator syntax
        put("function_definition", Extract.Identifier(customSingle = ::extractFromFunctionDefinition))
        put(
            "declaration",
            Extract.Identifier(
                customSingle = ::extractFromDeclaration,
                customMulti = ::extractAllDeclarators
            )
        )
        put("parameter_declaration", Extract.Identifier(customSingle = ::extractFromParameterDeclaration))
        put("field_declaration", Extract.Identifier(customSingle = ::extractFromFieldDeclaration))
        put("type_definition", Extract.Identifier(customSingle = ::extractFromTypedef))
        put("initializer_pair", Extract.Identifier(customSingle = ::extractFromInitializerPair))

        // Comments
        put("comment", Extract.Comment(CommentFormats.AutoDetect))

        // Strings
        put("string_literal", Extract.StringLiteral(format = StringFormats.Quoted()))
        put("char_literal", Extract.StringLiteral(format = StringFormats.Quoted(stripSingleQuotes = true)))
    }

    // ========== Calculation Configuration ==========

    private const val FUNCTION_DECLARATOR = "function_declarator"
    private const val FUNCTION_DEFINITION = "function_definition"

    override val calculationConfig = CalculationConfig(
        ignoreForComplexity = listOf(
            IgnoreRule.TypeWithParentType(FUNCTION_DECLARATOR, FUNCTION_DEFINITION)
        )
    )
}
