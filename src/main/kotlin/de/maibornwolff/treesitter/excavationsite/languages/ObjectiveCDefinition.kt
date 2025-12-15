package de.maibornwolff.treesitter.excavationsite.languages
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec.extractAllBlockParameters
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec.extractAllForwardClasses
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec.extractCStringLiteral
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec.extractCategoryIdentifier
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec.extractClassIdentifier
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec.extractFromCatchClause
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec.extractFromClassForwardDeclaration
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec.extractFromDeclaration
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec.extractFromFieldDeclaration
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec.extractFromForInStatement
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec.extractFromFunctionDefinition
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec.extractFromParameterDeclaration
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec.extractFromPreprocessorDef
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec.extractFromPreprocessorFunctionDef
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec.extractFromProtocolForwardDeclaration
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec.extractFromSynthesizeOrDynamic
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec.extractKeywordDeclaratorParameter
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec.extractMethodSelector
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec.extractObjCStringExpression
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec.extractPropertyIdentifier
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec.extractProtocolIdentifier
import de.maibornwolff.treesitter.excavationsite.shared.domain.CommentFormats
import de.maibornwolff.treesitter.excavationsite.shared.domain.Extract
import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric
import de.maibornwolff.treesitter.excavationsite.shared.domain.MetricCondition

/**
 * Objective-C language definition - combines metrics and extraction.
 *
 * Migrated from:
 * - metrics/ObjectiveCNodeTypes.kt
 * - extraction/ObjectiveCExtractionDictionary.kt
 * - extraction/ObjectiveCExtractionConfig.kt
 * - extraction/ObjectiveCExtractionCustomExtractors.kt
 */
object ObjectiveCDefinition : LanguageDefinition {
    // ========== Node Type Constants ==========

    private const val IDENTIFIER = "identifier"
    private const val TYPE_IDENTIFIER = "type_identifier"
    private const val FIELD_IDENTIFIER = "field_identifier"

    // Class and protocol declarations
    private const val CLASS_INTERFACE = "class_interface"
    private const val CLASS_IMPLEMENTATION = "class_implementation"
    private const val CATEGORY_INTERFACE = "category_interface"
    private const val CATEGORY_IMPLEMENTATION = "category_implementation"
    private const val PROTOCOL_DECLARATION = "protocol_declaration"

    // Method declarations
    private const val METHOD_DEFINITION = "method_definition"
    private const val METHOD_DECLARATION = "method_declaration"
    private const val FUNCTION_DEFINITION = "function_definition"
    private const val KEYWORD_DECLARATOR = "keyword_declarator"
    private const val KEYWORD_SELECTOR = "keyword_selector"
    private const val SELECTOR = "selector"

    // Variable declarations
    private const val DECLARATION = "declaration"
    private const val PROPERTY_DECLARATION = "property_declaration"
    private const val PARAMETER_DECLARATION = "parameter_declaration"
    private const val FIELD_DECLARATION = "field_declaration"
    private const val INIT_DECLARATOR = "init_declarator"
    private const val POINTER_DECLARATOR = "pointer_declarator"
    private const val ARRAY_DECLARATOR = "array_declarator"
    private const val FUNCTION_DECLARATOR = "function_declarator"

    // Control flow and exceptions
    private const val FOR_IN_STATEMENT = "for_in_statement"
    private const val CATCH_CLAUSE = "@catch"

    // Blocks
    private const val BLOCK_PARAMETERS = "block_parameters"

    // Synthesize/Dynamic
    private const val SYNTHESIZE_DEFINITION = "synthesize_definition"
    private const val DYNAMIC_DEFINITION = "dynamic_definition"
    private const val SYNTHESIZE_PROPERTY = "synthesize_property"

    // Forward declarations
    private const val CLASS_FORWARD_DECLARATION = "class_forward_declaration"
    private const val PROTOCOL_FORWARD_DECLARATION = "protocol_forward_declaration"

    // Preprocessor
    private const val PREPROC_DEF = "preproc_def"
    private const val PREPROC_FUNCTION_DEF = "preproc_function_def"

    // Strings
    private const val STRING_LITERAL = "string_literal"
    private const val STRING_EXPRESSION = "string_expression"

    // Field names for getChildByFieldName
    private const val FIELD_DECLARATOR = "declarator"
    private const val FIELD_KEYWORD = "keyword"
    private const val FIELD_NAME = "name"

    // ========== Metrics Mapping ==========

    override val nodeMetrics: Map<String, Set<Metric>> = buildMap {
        // Logic complexity
        put("if_statement", setOf(Metric.LogicComplexity))
        put("for_statement", setOf(Metric.LogicComplexity))
        put("while_statement", setOf(Metric.LogicComplexity))
        put("do_statement", setOf(Metric.LogicComplexity))
        put("case_statement", setOf(Metric.LogicComplexity))
        put(CATCH_CLAUSE, setOf(Metric.LogicComplexity))
        put("conditional_expression", setOf(Metric.LogicComplexity))

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
        put(FUNCTION_DEFINITION, setOf(Metric.FunctionComplexity, Metric.Function))
        put("block_expression", setOf(Metric.FunctionComplexity))
        put(METHOD_DEFINITION, setOf(Metric.Function))

        // Function body
        put("compound_statement", setOf(Metric.FunctionBody))

        // Function parameters
        put(PARAMETER_DECLARATION, setOf(Metric.Parameter))
        put(KEYWORD_DECLARATOR, setOf(Metric.Parameter))

        // Message chains
        put("call_expression", setOf(Metric.MessageChain, Metric.MessageChainCall))
        put("field_expression", setOf(Metric.MessageChain))
        put("message_expression", setOf(Metric.MessageChain, Metric.MessageChainCall))

        // Comment lines
        put("comment", setOf(Metric.CommentLine))
    }

    // ========== Extraction Mapping ==========

    override val nodeExtractions: Map<String, Extract> = buildMap {
        // Identifiers - with custom extractors
        put(CLASS_INTERFACE, Extract.Identifier(customSingle = ::extractClassIdentifier))
        put(CLASS_IMPLEMENTATION, Extract.Identifier(customSingle = ::extractClassIdentifier))
        put(CATEGORY_INTERFACE, Extract.Identifier(customSingle = ::extractCategoryIdentifier))
        put(CATEGORY_IMPLEMENTATION, Extract.Identifier(customSingle = ::extractCategoryIdentifier))
        put(PROTOCOL_DECLARATION, Extract.Identifier(customSingle = ::extractProtocolIdentifier))
        put(METHOD_DEFINITION, Extract.Identifier(customSingle = ::extractMethodSelector))
        put(METHOD_DECLARATION, Extract.Identifier(customSingle = ::extractMethodSelector))
        put(FUNCTION_DEFINITION, Extract.Identifier(customSingle = ::extractFromFunctionDefinition))
        put(DECLARATION, Extract.Identifier(customSingle = ::extractFromDeclaration))
        put(PROPERTY_DECLARATION, Extract.Identifier(customSingle = ::extractPropertyIdentifier))
        put(KEYWORD_DECLARATOR, Extract.Identifier(customSingle = ::extractKeywordDeclaratorParameter))
        put(PARAMETER_DECLARATION, Extract.Identifier(customSingle = ::extractFromParameterDeclaration))
        put(FIELD_DECLARATION, Extract.Identifier(customSingle = ::extractFromFieldDeclaration))
        put(FOR_IN_STATEMENT, Extract.Identifier(customSingle = ::extractFromForInStatement))
        put(CATCH_CLAUSE, Extract.Identifier(customSingle = ::extractFromCatchClause))
        put(SYNTHESIZE_DEFINITION, Extract.Identifier(customSingle = ::extractFromSynthesizeOrDynamic))
        put(DYNAMIC_DEFINITION, Extract.Identifier(customSingle = ::extractFromSynthesizeOrDynamic))
        // Note: synthesize_property is handled by synthesize_definition/dynamic_definition, not extracted separately
        put(
            CLASS_FORWARD_DECLARATION,
            Extract.Identifier(
                customSingle = ::extractFromClassForwardDeclaration,
                customMulti = ::extractAllForwardClasses
            )
        )
        put(
            PROTOCOL_FORWARD_DECLARATION,
            Extract.Identifier(customSingle = ::extractFromProtocolForwardDeclaration)
        )
        put(PREPROC_DEF, Extract.Identifier(customSingle = ::extractFromPreprocessorDef))
        put(PREPROC_FUNCTION_DEF, Extract.Identifier(customSingle = ::extractFromPreprocessorFunctionDef))
        put(BLOCK_PARAMETERS, Extract.Identifier(customMulti = ::extractAllBlockParameters))

        // Comments
        put("comment", Extract.Comment(CommentFormats.AutoDetect))

        // Strings - with custom extractors
        put(STRING_LITERAL, Extract.StringLiteral(custom = ::extractCStringLiteral))
        put(STRING_EXPRESSION, Extract.StringLiteral(custom = ::extractObjCStringExpression))
    }
}
