package de.maibornwolff.treesitter.excavationsite.languages
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.python.extractAllIdentifiers
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.python.extractDecoratorName
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.python.extractDocstring
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.python.extractFromAsPattern
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.python.extractFromAssignment
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.python.extractFromExceptClause
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.python.extractFromParameterIdentifier
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.python.extractFromTypeAlias
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.python.extractLoopVariables
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.python.extractPatternCaptureVariables
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.python.extractString
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.python.findFirstIdentifier
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.python.findLastIdentifier
import de.maibornwolff.treesitter.excavationsite.shared.domain.CalculationConfig
import de.maibornwolff.treesitter.excavationsite.shared.domain.CommentFormats
import de.maibornwolff.treesitter.excavationsite.shared.domain.Extract
import de.maibornwolff.treesitter.excavationsite.shared.domain.ExtractionStrategy
import de.maibornwolff.treesitter.excavationsite.shared.domain.IgnoreRule
import de.maibornwolff.treesitter.excavationsite.shared.domain.LeafNodeRule
import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric
import de.maibornwolff.treesitter.excavationsite.shared.domain.MetricCondition

/**
 * Unified Python language definition combining metrics and extraction.
 *
 * Migrated from:
 * - metrics/PythonNodeTypes.kt
 * - extraction/PythonExtractionDictionary.kt
 * - extraction/PythonExtractionConfig.kt
 * - extraction/PythonExtractionCustomExtractors.kt
 *
 * Metrics:
 * - Logic complexity: if/elif, for, while, lambda, boolean operators, case patterns, exceptions
 * - Function complexity: function definitions
 * - Comments: line comments and docstrings (expression_statement with string)
 * - Functions: function definitions and lambda assignments
 * - Function bodies: code blocks
 * - Parameters: identifier nodes
 * - Message chains: call and attribute access
 *
 * Extraction:
 * - Identifiers: classes, functions, variables, parameters, decorators, pattern matching, etc.
 * - Comments: line comments (#) and docstrings
 * - Strings: string literals with Python quote handling
 *
 * Special handling:
 * - Python uses indentation-based functions (hasFunctionBodyStartOrEndNode = false)
 * - Docstrings are extracted as comments, not strings
 * - String components (string_start, string_content, string_end) are ignored for RLOC
 * - Function names are excluded from parameter counting
 * - Underscore (_) identifiers in pattern matching are filtered out
 */
object PythonDefinition : LanguageDefinition {
    // ========== Node Type Constants ==========

    private const val IDENTIFIER = "identifier"
    private const val CLASS_DEFINITION = "class_definition"
    private const val FUNCTION_DEFINITION = "function_definition"
    private const val ASSIGNMENT = "assignment"
    private const val TYPED_PARAMETER = "typed_parameter"
    private const val DEFAULT_PARAMETER = "default_parameter"
    private const val LIST_SPLAT_PATTERN = "list_splat_pattern"
    private const val DICTIONARY_SPLAT_PATTERN = "dictionary_splat_pattern"
    private const val FOR_IN_CLAUSE = "for_in_clause"
    private const val FOR_STATEMENT = "for_statement"
    private const val NAMED_EXPRESSION = "named_expression"
    private const val EXCEPT_CLAUSE = "except_clause"
    private const val AS_PATTERN = "as_pattern"
    private const val DECORATOR = "decorator"
    private const val TYPE_ALIAS_STATEMENT = "type_alias_statement"
    private const val GLOBAL_STATEMENT = "global_statement"
    private const val NONLOCAL_STATEMENT = "nonlocal_statement"
    private const val CASE_CLAUSE = "case_clause"
    private const val COMMENT = "comment"
    private const val EXPRESSION_STATEMENT = "expression_statement"
    private const val STRING = "string"

    // ========== Metrics Configuration ==========

    override val nodeMetrics: Map<String, Set<Metric>> = buildMap {
        // Logic complexity - simple nodes
        listOf(
            "if_statement",
            "elif_clause",
            "if_clause",
            "for_statement",
            "while_statement",
            "for_in_clause",
            "conditional_expression",
            "list",
            "boolean_operator",
            "case_pattern",
            "except_clause"
        ).forEach { put(it, setOf(Metric.LogicComplexity)) }

        // Logic complexity - conditional lambda with 4 children
        put(
            "lambda",
            setOf(
                Metric.LogicComplexityConditional(
                    MetricCondition.ChildPositionMatches(
                        position = 0,
                        requiredChildCount = 4,
                        allowedTypes = setOf("lambda")
                    )
                )
            )
        )

        // Function complexity and number of functions
        put("function_definition", setOf(Metric.FunctionComplexity, Metric.Function))

        // Comment lines - simple comment
        put("comment", setOf(Metric.CommentLine))

        // Comment lines - docstring (expression_statement with string child)
        put(
            "expression_statement",
            setOf(
                Metric.CommentLineConditional(
                    MetricCondition.ChildPositionMatches(
                        position = 0,
                        requiredChildCount = 1,
                        allowedTypes = setOf("string")
                    )
                )
            )
        )

        // Number of functions - assignment with lambda (conditional)
        put(
            "assignment",
            setOf(
                Metric.FunctionConditional(
                    MetricCondition.ChildFieldMatches(
                        fieldName = "right",
                        allowedValues = setOf("lambda")
                    )
                )
            )
        )

        // Function body
        put("block", setOf(Metric.FunctionBody))

        // Function parameters
        put("identifier", setOf(Metric.Parameter))

        // Message chains
        put("call", setOf(Metric.MessageChain, Metric.MessageChainCall))
        put("attribute", setOf(Metric.MessageChain))
    }

    // ========== Extraction Configuration ==========

    override val nodeExtractions: Map<String, Extract> = buildMap {
        // Identifiers - generic extraction strategies
        put(CLASS_DEFINITION, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put(FUNCTION_DEFINITION, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put(TYPED_PARAMETER, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put(DEFAULT_PARAMETER, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put(LIST_SPLAT_PATTERN, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put(DICTIONARY_SPLAT_PATTERN, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put(NAMED_EXPRESSION, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))

        // Identifiers - custom extractors for context-dependent patterns
        put(ASSIGNMENT, Extract.Identifier(customSingle = ::extractFromAssignment))
        put(IDENTIFIER, Extract.Identifier(customSingle = ::extractFromParameterIdentifier))
        put(EXCEPT_CLAUSE, Extract.Identifier(customSingle = ::extractFromExceptClause))
        put(
            TYPE_ALIAS_STATEMENT,
            Extract.Identifier(customSingle = {
                    n,
                    s ->
                extractFromTypeAlias(n, s, ::findFirstIdentifier)
            })
        )
        put(
            AS_PATTERN,
            Extract.Identifier(customSingle = {
                    n,
                    s ->
                extractFromAsPattern(
                    n,
                    s,
                    ::findFirstIdentifier,
                    ::findLastIdentifier
                )
            })
        )
        put(
            DECORATOR,
            Extract.Identifier(customSingle = {
                    n,
                    s ->
                extractDecoratorName(n, s, ::findFirstIdentifier)
            })
        )

        // Identifiers - multi extractors for nodes that can contain multiple identifiers
        put(
            FOR_IN_CLAUSE,
            Extract.Identifier(
                customSingle = { n, s -> extractLoopVariables(n, s).firstOrNull() },
                customMulti = ::extractLoopVariables
            )
        )
        put(
            FOR_STATEMENT,
            Extract.Identifier(
                customSingle = { n, s -> extractLoopVariables(n, s).firstOrNull() },
                customMulti = ::extractLoopVariables
            )
        )
        put(
            GLOBAL_STATEMENT,
            Extract.Identifier(
                customSingle = { n, s -> extractAllIdentifiers(n, s).firstOrNull() },
                customMulti = ::extractAllIdentifiers
            )
        )
        put(
            NONLOCAL_STATEMENT,
            Extract.Identifier(
                customSingle = { n, s -> extractAllIdentifiers(n, s).firstOrNull() },
                customMulti = ::extractAllIdentifiers
            )
        )
        put(
            CASE_CLAUSE,
            Extract.Identifier(customMulti = {
                    n,
                    s ->
                extractPatternCaptureVariables(n, s, ::findFirstIdentifier)
            })
        )

        // Comments - line comments and docstrings
        put(COMMENT, Extract.Comment(format = CommentFormats.Line("#")))
        put(EXPRESSION_STATEMENT, Extract.Comment(custom = ::extractDocstring))

        // Strings - Python string literals
        put(STRING, Extract.StringLiteral(custom = ::extractString))
    }

    // ========== Calculation Configuration ==========

    private val stringComponentTypes = setOf("string_start", "string_content", "string_end")

    override val calculationConfig = CalculationConfig(
        hasFunctionBodyStartOrEndNode = false,
        ignoreForCommentLines = listOf(
            IgnoreRule.TypeInSet(stringComponentTypes),
            IgnoreRule.SingleChildOfParentWithType(STRING)
        ),
        ignoreForRloc = listOf(
            IgnoreRule.TypeInSet(stringComponentTypes),
            IgnoreRule.SingleChildOfParentWithType(STRING),
            IgnoreRule.FirstChildIsDocstring
        ),
        ignoreForParameters = listOf(
            IgnoreRule.TypeWithParentType(IDENTIFIER, FUNCTION_DEFINITION)
        ),
        countAsLeafNode = LeafNodeRule.WhenParentHasMultipleChildren(STRING)
    )
}
