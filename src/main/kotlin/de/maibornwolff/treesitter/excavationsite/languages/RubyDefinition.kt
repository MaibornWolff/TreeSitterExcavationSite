package de.maibornwolff.treesitter.excavationsite.languages
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.ruby.extractAliasIdentifiers
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.ruby.extractAttrSymbols
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.ruby.extractFromAssignment
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.ruby.extractFromClassOrModule
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.ruby.extractFromClassOrModuleMultiple
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.ruby.extractFromHashKeySymbol
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.ruby.extractFromIdentifierInContext
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.ruby.extractFromRescue
import de.maibornwolff.treesitter.excavationsite.shared.domain.CalculationConfig
import de.maibornwolff.treesitter.excavationsite.shared.domain.CommentFormats
import de.maibornwolff.treesitter.excavationsite.shared.domain.Extract
import de.maibornwolff.treesitter.excavationsite.shared.domain.ExtractionStrategy
import de.maibornwolff.treesitter.excavationsite.shared.domain.IgnoreRule
import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric
import de.maibornwolff.treesitter.excavationsite.shared.domain.MetricCondition
import de.maibornwolff.treesitter.excavationsite.shared.domain.StringFormats

/**
 * Ruby language definition - combines metrics and extraction.
 *
 * Migrated from:
 * - metrics/RubyNodeTypes.kt
 * - extraction/RubyExtractionDictionary.kt
 * - extraction/RubyExtractionConfig.kt
 * - extraction/RubyExtractionCustomExtractors.kt
 */
object RubyDefinition : LanguageDefinition {
    // ========== Node Type Constants ==========

    private const val IDENTIFIER = "identifier"
    private const val STRING_CONTENT = "string_content"
    private const val SIMPLE_SYMBOL = "simple_symbol"
    private const val KEYWORD_PATTERN = "keyword_pattern"
    private const val CALL = "call"
    private const val CLASS = "class"
    private const val MODULE = "module"
    private const val METHOD = "method"
    private const val SINGLETON_METHOD = "singleton_method"
    private const val ASSIGNMENT = "assignment"
    private const val KEYWORD_PARAMETER = "keyword_parameter"
    private const val OPTIONAL_PARAMETER = "optional_parameter"
    private const val SPLAT_PARAMETER = "splat_parameter"
    private const val HASH_SPLAT_PARAMETER = "hash_splat_parameter"
    private const val BLOCK_PARAMETER = "block_parameter"
    private const val RESCUE = "rescue"
    private const val ALIAS = "alias"
    private const val FOR = "for"
    private const val REST_ASSIGNMENT = "rest_assignment"
    private const val HASH_KEY_SYMBOL = "hash_key_symbol"
    private const val COMMENT = "comment"
    private const val STRING = "string"
    private const val DELIMITED_SYMBOL = "delimited_symbol"
    private const val HEREDOC_BODY = "heredoc_body"
    private const val REGEX = "regex"
    private const val BARE_STRING = "bare_string"
    private const val BARE_SYMBOL = "bare_symbol"

    // ========== Metrics Mapping ==========

    override val nodeMetrics: Map<String, Set<Metric>> = buildMap {
        // Logic complexity - simple
        put("if", setOf(Metric.LogicComplexity))
        put("elsif", setOf(Metric.LogicComplexity))
        put("for", setOf(Metric.LogicComplexity))
        put("until", setOf(Metric.LogicComplexity))
        put("while", setOf(Metric.LogicComplexity))
        put("do_block", setOf(Metric.LogicComplexity))
        put("conditional", setOf(Metric.LogicComplexity))
        put("when", setOf(Metric.LogicComplexity))
        put("else", setOf(Metric.LogicComplexity))
        put("rescue", setOf(Metric.LogicComplexity))

        // Logic complexity - conditional (binary with &&, ||, and, or)
        put(
            "binary",
            setOf(
                Metric.LogicComplexityConditional(
                    MetricCondition.ChildFieldMatches(
                        fieldName = "operator",
                        allowedValues = setOf("&&", "||", "and", "or")
                    )
                )
            )
        )

        // Function complexity
        put("lambda", setOf(Metric.FunctionComplexity))
        put("method", setOf(Metric.FunctionComplexity, Metric.Function))
        put("singleton_method", setOf(Metric.FunctionComplexity, Metric.Function))

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

        // Comment lines
        put("comment", setOf(Metric.CommentLine))

        // Function body
        put("body_statement", setOf(Metric.FunctionBody))

        // Function parameters
        put("identifier", setOf(Metric.Parameter))

        // Message chains
        put("call", setOf(Metric.MessageChain, Metric.MessageChainCall))
    }

    // ========== Extraction Mapping ==========

    override val nodeExtractions: Map<String, Extract> = buildMap {
        // Identifiers - simple first child
        put(METHOD, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put(SINGLETON_METHOD, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put(KEYWORD_PARAMETER, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put(OPTIONAL_PARAMETER, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put(SPLAT_PARAMETER, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put(HASH_SPLAT_PARAMETER, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put(BLOCK_PARAMETER, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put(REST_ASSIGNMENT, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put(KEYWORD_PATTERN, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
        put(FOR, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))

        // Identifiers - custom single extractors
        put(
            CLASS,
            Extract.Identifier(
                customSingle = ::extractFromClassOrModule,
                customMulti = ::extractFromClassOrModuleMultiple
            )
        )
        put(
            MODULE,
            Extract.Identifier(
                customSingle = ::extractFromClassOrModule,
                customMulti = ::extractFromClassOrModuleMultiple
            )
        )
        put(ASSIGNMENT, Extract.Identifier(customSingle = ::extractFromAssignment))
        put(IDENTIFIER, Extract.Identifier(customSingle = ::extractFromIdentifierInContext))
        put(RESCUE, Extract.Identifier(customSingle = ::extractFromRescue))
        put(HASH_KEY_SYMBOL, Extract.Identifier(customSingle = ::extractFromHashKeySymbol))

        // Identifiers - custom multi extractors
        put(ALIAS, Extract.Identifier(customMulti = ::extractAliasIdentifiers))
        put(CALL, Extract.Identifier(customMulti = ::extractAttrSymbols))

        // Comments
        put(COMMENT, Extract.Comment(CommentFormats.AutoDetect))

        // Strings
        put(STRING, Extract.StringLiteral(format = StringFormats.FromChild(STRING_CONTENT)))
        put(SIMPLE_SYMBOL, Extract.StringLiteral(format = StringFormats.RubySymbol))
        put(DELIMITED_SYMBOL, Extract.StringLiteral(format = StringFormats.FromChild(STRING_CONTENT)))
        put(HEREDOC_BODY, Extract.StringLiteral(format = StringFormats.Trimmed))
        put(REGEX, Extract.StringLiteral(format = StringFormats.Regex))
        put(BARE_STRING, Extract.StringLiteral(format = StringFormats.FromChild(STRING_CONTENT)))
        put(BARE_SYMBOL, Extract.StringLiteral(format = StringFormats.FromChild(STRING_CONTENT)))
    }

    // ========== Calculation Configuration ==========

    private val nestedControlStructures = setOf("if", "elsif", "for", "until", "while", "when", "else", "rescue")

    override val calculationConfig = CalculationConfig(
        hasFunctionBodyStartOrEndNode = false,
        ignoreForComplexity = listOf(
            IgnoreRule.TypeEqualsParentTypeWhenInSet(nestedControlStructures),
            IgnoreRule.TypeWhenParentTypeIsNot("else", "case")
        ),
        ignoreForRloc = listOf(
            IgnoreRule.TypeInSet(setOf("then"))
        ),
        ignoreForParameters = listOf(
            IgnoreRule.TypeWithParentType(IDENTIFIER, METHOD)
        )
    )
}
