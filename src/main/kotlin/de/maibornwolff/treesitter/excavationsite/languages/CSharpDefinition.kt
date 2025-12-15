package de.maibornwolff.treesitter.excavationsite.languages
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.csharp.extractAllTuplePatternVariables
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.csharp.extractForLoopVariable
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.csharp.extractForeachVariable
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.csharp.extractLambdaSingleParameter
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.csharp.extractParamsParametersInOrder
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.csharp.extractUsingVariable
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.csharp.extractVarPatternVariable
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.csharp.findIdentifierInVariableDeclaration
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.csharp.findLastIdentifier
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.csharp.findMethodName
import de.maibornwolff.treesitter.excavationsite.shared.domain.CommentFormats
import de.maibornwolff.treesitter.excavationsite.shared.domain.Extract
import de.maibornwolff.treesitter.excavationsite.shared.domain.ExtractionStrategy
import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric
import de.maibornwolff.treesitter.excavationsite.shared.domain.MetricCondition
import de.maibornwolff.treesitter.excavationsite.shared.domain.StringFormats

/**
 * C# language definition - combines metrics and extraction.
 *
 * Migrated from:
 * - metrics/CSharpNodeTypes.kt
 * - extraction/CSharpExtractionDictionary.kt
 * - extraction/CSharpExtractionConfig.kt
 * - extraction/CSharpExtractionCustomExtractors.kt
 */
object CSharpDefinition : LanguageDefinition {
    // ========== Node Type Constants ==========

    private const val IDENTIFIER = "identifier"

    // ========== Metrics Mapping ==========

    override val nodeMetrics: Map<String, Set<Metric>> = buildMap {
        // Logic complexity
        put("if_statement", setOf(Metric.LogicComplexity))
        put("do_statement", setOf(Metric.LogicComplexity))
        put("foreach_statement", setOf(Metric.LogicComplexity))
        put("for_statement", setOf(Metric.LogicComplexity))
        put("while_statement", setOf(Metric.LogicComplexity))
        put("conditional_expression", setOf(Metric.LogicComplexity))
        put("is_expression", setOf(Metric.LogicComplexity))
        put("and_pattern", setOf(Metric.LogicComplexity))
        put("or_pattern", setOf(Metric.LogicComplexity))
        put("switch_section", setOf(Metric.LogicComplexity))
        put("switch_expression_arm", setOf(Metric.LogicComplexity))
        put("catch_clause", setOf(Metric.LogicComplexity))

        // Logic complexity - conditional (binary expressions with &&, ||, ??)
        put(
            "binary_expression",
            setOf(
                Metric.LogicComplexityConditional(
                    MetricCondition.ChildFieldMatches(
                        fieldName = "operator",
                        allowedValues = setOf("&&", "||", "??")
                    )
                )
            )
        )

        // Function complexity
        put("constructor_declaration", setOf(Metric.FunctionComplexity, Metric.Function))
        put("method_declaration", setOf(Metric.FunctionComplexity, Metric.Function))
        put("lambda_expression", setOf(Metric.FunctionComplexity))
        put("local_function_statement", setOf(Metric.FunctionComplexity, Metric.Function))
        put("accessor_declaration", setOf(Metric.FunctionComplexity, Metric.Function))

        // Number of functions - variable declarator with lambda (conditional)
        put(
            "variable_declarator",
            setOf(
                Metric.FunctionConditional(
                    MetricCondition.ChildPositionMatches(
                        position = 2,
                        requiredChildCount = 3,
                        allowedTypes = setOf("lambda_expression")
                    )
                )
            )
        )

        // Function body
        put("block", setOf(Metric.FunctionBody))

        // Function parameters
        put("parameter", setOf(Metric.Parameter))

        // Message chains
        put("invocation_expression", setOf(Metric.MessageChain, Metric.MessageChainCall))
        put("member_access_expression", setOf(Metric.MessageChain))

        // Comment lines
        put("comment", setOf(Metric.CommentLine))
    }

    // ========== Extraction Mapping ==========

    override val nodeExtractions: Map<String, Extract> = buildMap {
        // Identifiers - simple first child
        put("class_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("interface_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("struct_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("enum_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("record_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("delegate_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("constructor_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("property_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("enum_member_declaration", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put("type_parameter", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))

        // Identifiers - custom extractors
        put("method_declaration", Extract.Identifier(customSingle = ::findMethodName))
        put("local_function_statement", Extract.Identifier(customSingle = ::findMethodName))
        put("field_declaration", Extract.Identifier(customSingle = ::findIdentifierInVariableDeclaration))
        put(
            "local_declaration_statement",
            Extract.Identifier(customSingle = ::findIdentifierInVariableDeclaration)
        )
        put("event_field_declaration", Extract.Identifier(customSingle = ::findIdentifierInVariableDeclaration))
        put("parameter", Extract.Identifier(customSingle = ::findLastIdentifier))
        put("catch_declaration", Extract.Identifier(customSingle = ::findLastIdentifier))
        put("declaration_pattern", Extract.Identifier(customSingle = ::findLastIdentifier))
        put("lambda_expression", Extract.Identifier(customSingle = ::extractLambdaSingleParameter))
        put("foreach_statement", Extract.Identifier(customSingle = ::extractForeachVariable))
        put("using_statement", Extract.Identifier(customSingle = ::extractUsingVariable))
        put("for_statement", Extract.Identifier(customSingle = ::extractForLoopVariable))
        put("var_pattern", Extract.Identifier(customSingle = ::extractVarPatternVariable))

        // Identifiers - multiple extraction
        put("parameter_list", Extract.Identifier(customMulti = ::extractParamsParametersInOrder))
        put("tuple_pattern", Extract.Identifier(customMulti = ::extractAllTuplePatternVariables))

        // Comments
        put("comment", Extract.Comment(CommentFormats.AutoDetect))

        // Strings
        put("string_literal", Extract.StringLiteral(format = StringFormats.Quoted()))
        put("verbatim_string_literal", Extract.StringLiteral(format = StringFormats.CSharpVerbatim))
        put("interpolated_string_expression", Extract.StringLiteral(format = StringFormats.CSharpInterpolated))
        put("raw_string_literal", Extract.StringLiteral(format = StringFormats.CSharpRaw))
    }
}
