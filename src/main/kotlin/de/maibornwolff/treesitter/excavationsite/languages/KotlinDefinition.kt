package de.maibornwolff.treesitter.excavationsite.languages
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.kotlin.extractAnnotationClassFromInfixExpression
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.kotlin.extractLabelIdentifier
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.kotlin.extractLambdaParameterIdentifiers
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.kotlin.extractPropertyIdentifiers
import de.maibornwolff.treesitter.excavationsite.shared.domain.CommentFormats
import de.maibornwolff.treesitter.excavationsite.shared.domain.Extract
import de.maibornwolff.treesitter.excavationsite.shared.domain.ExtractionStrategy
import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric
import de.maibornwolff.treesitter.excavationsite.shared.domain.MetricCondition
import de.maibornwolff.treesitter.excavationsite.shared.domain.StringFormats

/**
 * Unified Kotlin language definition combining metrics and extraction.
 *
 * Metrics:
 * - Logic complexity: if, for, while, elvis, conjunctions, when entries, catch blocks
 * - Function complexity: function/lambda/constructor declarations
 * - Comments: line and multiline comments
 * - Functions: counted via nested matching on function_declaration with function_body
 * - Function bodies: for RLOC per function calculation
 * - Parameters: function parameter nodes
 * - Message chains: call and navigation expressions
 *
 * Extraction:
 * - Identifiers: classes, functions, properties, parameters, type aliases, labels, etc.
 * - Comments: line (//) and multiline (/* */) comments
 * - Strings: string literals via string_content child
 *
 * Special handling:
 * - Underscore identifiers (_) are filtered out (represent unused values)
 * - Annotation classes parsed as infix_expression are handled via custom extractor
 * - Labels have @ suffix removed
 * - Lambda parameters and destructuring declarations use custom multi-extractors
 */
object KotlinDefinition : LanguageDefinition {
    // ========== Node Type Constants ==========

    private const val SIMPLE_IDENTIFIER = "simple_identifier"
    private const val TYPE_IDENTIFIER = "type_identifier"
    private const val VARIABLE_DECLARATION = "variable_declaration"
    private const val MULTI_VARIABLE_DECLARATION = "multi_variable_declaration"

    // ========== Metrics Configuration ==========

    override val nodeMetrics: Map<String, Set<Metric>> = buildMap {
        // Logic complexity nodes
        listOf(
            "if_expression",
            "for_statement",
            "while_statement",
            "do_while_statement",
            "elvis_expression",
            "conjunction_expression",
            "disjunction_expression",
            "when_entry",
            "catch_block"
        ).forEach { put(it, setOf(Metric.LogicComplexity)) }

        // Function complexity nodes (only complexity, not counted as functions)
        listOf(
            "anonymous_function",
            "anonymous_initializer",
            "lambda_literal"
        ).forEach { put(it, setOf(Metric.FunctionComplexity)) }

        // Function nodes that count for BOTH complexity AND number_of_functions
        listOf(
            "secondary_constructor",
            "setter",
            "getter"
        ).forEach { put(it, setOf(Metric.FunctionComplexity, Metric.Function)) }

        // Comment line nodes
        listOf("line_comment", "multiline_comment").forEach { put(it, setOf(Metric.CommentLine)) }

        // Function nodes with conditional matching requirements
        // property_declaration with 4 children and lambda/function/initializer at position 3
        put(
            "property_declaration",
            setOf(
                Metric.FunctionConditional(
                    MetricCondition.ChildPositionMatches(
                        position = 3,
                        requiredChildCount = 4,
                        allowedTypes = setOf("lambda_literal", "anonymous_function", "anonymous_initializer")
                    )
                )
            )
        )

        // function_declaration with function_body at various positions
        // Includes FunctionComplexity for complexity calculation plus conditional matching for function counting
        val functionDeclarationMetrics = setOf(
            Metric.FunctionComplexity,
            Metric.FunctionConditional(
                MetricCondition.ChildPositionMatches(
                    position = 3,
                    requiredChildCount = 4,
                    allowedTypes = setOf("function_body")
                )
            ),
            Metric.FunctionConditional(
                MetricCondition.ChildPositionMatches(
                    position = 4,
                    requiredChildCount = 5,
                    allowedTypes = setOf("function_body")
                )
            ),
            Metric.FunctionConditional(
                MetricCondition.ChildPositionMatches(
                    position = 5,
                    requiredChildCount = 6,
                    allowedTypes = setOf("function_body")
                )
            ),
            Metric.FunctionConditional(
                MetricCondition.ChildPositionMatches(
                    position = 6,
                    requiredChildCount = 7,
                    allowedTypes = setOf("function_body")
                )
            )
        )
        put("function_declaration", functionDeclarationMetrics)

        // Function body nodes
        put("function_body", setOf(Metric.FunctionBody))

        // Parameter nodes
        put("parameter", setOf(Metric.Parameter))

        // Message chain nodes
        put("call_expression", setOf(Metric.MessageChain, Metric.MessageChainCall))
        put("navigation_expression", setOf(Metric.MessageChain))
    }

    // ========== Extraction Configuration ==========

    override val nodeExtractions: Map<String, Extract> = buildMap {
        // Identifiers with generic extraction strategies
        put(
            "type_alias",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByType(TYPE_IDENTIFIER))
        )
        put(
            "type_parameter",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByType(TYPE_IDENTIFIER))
        )
        put(
            "function_declaration",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByType(SIMPLE_IDENTIFIER))
        )
        put(
            "class_parameter",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByType(SIMPLE_IDENTIFIER))
        )
        put(
            "parameter",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByType(SIMPLE_IDENTIFIER))
        )
        put(
            "enum_entry",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByType(SIMPLE_IDENTIFIER))
        )
        put(
            "parameter_with_optional_type",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByType(SIMPLE_IDENTIFIER))
        )
        put(
            "catch_block",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByType(SIMPLE_IDENTIFIER))
        )
        put(
            "class_declaration",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByTypes(SIMPLE_IDENTIFIER, TYPE_IDENTIFIER))
        )
        put(
            "object_declaration",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByTypes(SIMPLE_IDENTIFIER, TYPE_IDENTIFIER))
        )
        put(
            "interface_declaration",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByTypes(SIMPLE_IDENTIFIER, TYPE_IDENTIFIER))
        )
        put(
            "when_subject",
            Extract.Identifier(single = ExtractionStrategy.NestedInChild(VARIABLE_DECLARATION, SIMPLE_IDENTIFIER))
        )

        // Identifiers with custom extraction (filtering underscores)
        put(
            "infix_expression",
            Extract.Identifier(customSingle = ::extractAnnotationClassFromInfixExpression)
        )
        put(
            "label",
            Extract.Identifier(customSingle = ::extractLabelIdentifier)
        )
        put(
            "property_declaration",
            Extract.Identifier(
                single = ExtractionStrategy.NestedInChild(VARIABLE_DECLARATION, SIMPLE_IDENTIFIER),
                customMulti = ::extractPropertyIdentifiers
            )
        )
        put(
            "lambda_parameters",
            Extract.Identifier(customMulti = ::extractLambdaParameterIdentifiers)
        )
        put(
            MULTI_VARIABLE_DECLARATION,
            Extract.Identifier(customMulti = ::extractLambdaParameterIdentifiers)
        )
        put(
            "for_statement",
            Extract.Identifier(customMulti = ::extractPropertyIdentifiers)
        )

        // Comments
        put("line_comment", Extract.Comment(CommentFormats.Line("//")))
        put("multiline_comment", Extract.Comment(CommentFormats.Block))

        // Strings
        put("string_literal", Extract.StringLiteral(format = StringFormats.FromChild("string_content")))
    }
}
