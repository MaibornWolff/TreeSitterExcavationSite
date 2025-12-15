package de.maibornwolff.treesitter.excavationsite.languages
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.swift.extractAllBoundVariables
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.swift.extractClassOrExtensionIdentifier
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.swift.extractDeinitKeyword
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.swift.extractFromPattern
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.swift.extractInitKeyword
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.swift.extractParameterName
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.swift.extractSubscriptKeyword
import de.maibornwolff.treesitter.excavationsite.shared.domain.CommentFormats
import de.maibornwolff.treesitter.excavationsite.shared.domain.Extract
import de.maibornwolff.treesitter.excavationsite.shared.domain.ExtractionStrategy
import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric
import de.maibornwolff.treesitter.excavationsite.shared.domain.StringFormats

/**
 * Swift language definition - combines metrics and extraction.
 *
 * Migrated from:
 * - metrics/SwiftNodeTypes.kt
 * - extraction/SwiftExtractionDictionary.kt
 * - extraction/SwiftExtractionConfig.kt
 * - extraction/SwiftExtractionCustomExtractors.kt
 */
object SwiftDefinition : LanguageDefinition {
    // ========== Node Type Constants ==========

    private const val SIMPLE_IDENTIFIER = "simple_identifier"
    private const val TYPE_IDENTIFIER = "type_identifier"
    private const val USER_TYPE = "user_type"
    private const val PATTERN = "pattern"
    private const val EXTENSION = "extension"
    private const val VALUE_BINDING_PATTERN = "value_binding_pattern"

    private const val CLASS_DECLARATION = "class_declaration"
    private const val STRUCT_DECLARATION = "struct_declaration"
    private const val PROTOCOL_DECLARATION = "protocol_declaration"
    private const val FUNCTION_DECLARATION = "function_declaration"
    private const val PROTOCOL_FUNCTION_DECLARATION = "protocol_function_declaration"
    private const val INIT_DECLARATION = "init_declaration"
    private const val DEINIT_DECLARATION = "deinit_declaration"
    private const val SUBSCRIPT_DECLARATION = "subscript_declaration"
    private const val PROPERTY_DECLARATION = "property_declaration"
    private const val PROTOCOL_PROPERTY_DECLARATION = "protocol_property_declaration"
    private const val TYPEALIAS_DECLARATION = "typealias_declaration"
    private const val ASSOCIATEDTYPE_DECLARATION = "associatedtype_declaration"
    private const val PARAMETER = "parameter"
    private const val LAMBDA_PARAMETER = "lambda_parameter"
    private const val TYPE_PARAMETER = "type_parameter"
    private const val ENUM_ENTRY = "enum_entry"
    private const val ENUM_TYPE_PARAMETERS = "enum_type_parameters"
    private const val GUARD_STATEMENT = "guard_statement"
    private const val IF_STATEMENT = "if_statement"
    private const val COMMENT = "comment"
    private const val MULTILINE_COMMENT = "multiline_comment"
    private const val LINE_STRING_LITERAL = "line_string_literal"
    private const val MULTI_LINE_STRING_LITERAL = "multi_line_string_literal"
    private const val REGEX_LITERAL = "regex_literal"

    // ========== Metrics Mapping ==========

    override val nodeMetrics: Map<String, Set<Metric>> = buildMap {
        // Logic complexity
        put(IF_STATEMENT, setOf(Metric.LogicComplexity))
        put(GUARD_STATEMENT, setOf(Metric.LogicComplexity))
        put("for_statement", setOf(Metric.LogicComplexity))
        put("while_statement", setOf(Metric.LogicComplexity))
        put("repeat_while_statement", setOf(Metric.LogicComplexity))
        put("switch_entry", setOf(Metric.LogicComplexity))
        put("catch_block", setOf(Metric.LogicComplexity))
        put("defer_statement", setOf(Metric.LogicComplexity))
        put("nil_coalescing_expression", setOf(Metric.LogicComplexity))
        put("conjunction_expression", setOf(Metric.LogicComplexity))
        put("disjunction_expression", setOf(Metric.LogicComplexity))
        put("ternary_expression", setOf(Metric.LogicComplexity))
        put("willset_clause", setOf(Metric.LogicComplexity))
        put("didset_clause", setOf(Metric.LogicComplexity))

        // Function complexity and number of functions
        put(FUNCTION_DECLARATION, setOf(Metric.FunctionComplexity, Metric.Function))
        put(INIT_DECLARATION, setOf(Metric.FunctionComplexity, Metric.Function))
        put(DEINIT_DECLARATION, setOf(Metric.FunctionComplexity, Metric.Function))
        put("lambda_literal", setOf(Metric.FunctionComplexity))
        put(SUBSCRIPT_DECLARATION, setOf(Metric.FunctionComplexity))
        put("computed_getter", setOf(Metric.FunctionComplexity, Metric.Function))
        put("computed_setter", setOf(Metric.FunctionComplexity, Metric.Function))

        // Function body
        put("function_body", setOf(Metric.FunctionBody))
        put("code_block", setOf(Metric.FunctionBody))

        // Function parameters
        put(PARAMETER, setOf(Metric.Parameter))

        // Message chains
        put("call_expression", setOf(Metric.MessageChain, Metric.MessageChainCall))
        put("navigation_expression", setOf(Metric.MessageChain))

        // Comment lines
        put(COMMENT, setOf(Metric.CommentLine))
        put(MULTILINE_COMMENT, setOf(Metric.CommentLine))
    }

    // ========== Extraction Mapping ==========

    override val nodeExtractions: Map<String, Extract> = buildMap {
        // Identifiers - simple first child strategies
        put(STRUCT_DECLARATION, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(TYPE_IDENTIFIER)))
        put(PROTOCOL_DECLARATION, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(TYPE_IDENTIFIER)))
        put(TYPEALIAS_DECLARATION, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(TYPE_IDENTIFIER)))
        put(ASSOCIATEDTYPE_DECLARATION, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(TYPE_IDENTIFIER)))
        put(TYPE_PARAMETER, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(TYPE_IDENTIFIER)))
        put(FUNCTION_DECLARATION, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(SIMPLE_IDENTIFIER)))
        put(PROTOCOL_FUNCTION_DECLARATION, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(SIMPLE_IDENTIFIER)))
        put(ENUM_ENTRY, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(SIMPLE_IDENTIFIER)))
        put(ENUM_TYPE_PARAMETERS, Extract.Identifier(single = ExtractionStrategy.FirstChildByType(SIMPLE_IDENTIFIER)))

        // Identifiers - custom extractors
        put(CLASS_DECLARATION, Extract.Identifier(customSingle = ::extractClassOrExtensionIdentifier))
        put(INIT_DECLARATION, Extract.Identifier(customSingle = ::extractInitKeyword))
        put(DEINIT_DECLARATION, Extract.Identifier(customSingle = ::extractDeinitKeyword))
        put(SUBSCRIPT_DECLARATION, Extract.Identifier(customSingle = ::extractSubscriptKeyword))
        put(PROPERTY_DECLARATION, Extract.Identifier(customSingle = ::extractFromPattern))
        put(PROTOCOL_PROPERTY_DECLARATION, Extract.Identifier(customSingle = ::extractFromPattern))
        put(PARAMETER, Extract.Identifier(customSingle = ::extractParameterName))
        put(LAMBDA_PARAMETER, Extract.Identifier(customSingle = ::extractParameterName))
        put(GUARD_STATEMENT, Extract.Identifier(customMulti = ::extractAllBoundVariables))
        put(IF_STATEMENT, Extract.Identifier(customMulti = ::extractAllBoundVariables))

        // Comments
        put(COMMENT, Extract.Comment(CommentFormats.AutoDetect))
        put(MULTILINE_COMMENT, Extract.Comment(CommentFormats.Block))

        // Strings
        put(LINE_STRING_LITERAL, Extract.StringLiteral(format = StringFormats.Quoted()))
        put(MULTI_LINE_STRING_LITERAL, Extract.StringLiteral(format = StringFormats.TripleQuoted))
        put(REGEX_LITERAL, Extract.StringLiteral(format = StringFormats.Regex))
    }
}
