package de.maibornwolff.treesitter.excavationsite.languages
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.javascript.extractFirstBindingIdentifiers
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.javascript.extractIdentifiersFromClassDeclaration
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.javascript.extractIdentifiersFromEnumDeclaration
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.javascript.extractIdentifiersFromFormalParameters
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.javascript.extractIdentifiersFromMethodDefinition
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.javascript.extractIdentifiersFromVariableDeclarator
import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.javascript.extractPropertyName
import de.maibornwolff.treesitter.excavationsite.shared.domain.CalculationConfig
import de.maibornwolff.treesitter.excavationsite.shared.domain.CommentFormats
import de.maibornwolff.treesitter.excavationsite.shared.domain.Extract
import de.maibornwolff.treesitter.excavationsite.shared.domain.ExtractionStrategy
import de.maibornwolff.treesitter.excavationsite.shared.domain.IgnoreRule
import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric
import de.maibornwolff.treesitter.excavationsite.shared.domain.MetricCondition
import de.maibornwolff.treesitter.excavationsite.shared.domain.StringFormats

/**
 * Unified JavaScript language definition combining metrics and extraction.
 *
 * Metrics:
 * - Logic complexity: if, for, while, ternary, switch cases, catch, binary operators (&&, ||, ??)
 * - Function complexity: function declarations, arrow functions, generators, methods, static blocks
 * - Comments: line and HTML comments
 * - Functions: function declarations, methods, arrow functions in variable declarators
 * - Function bodies: statement blocks
 * - Parameters: identifiers
 * - Message chains: member expressions and call expressions
 *
 * Extraction:
 * - Identifiers: classes, functions, methods, properties, parameters, type aliases, enums, etc.
 * - Comments: auto-detected format (//, block, HTML)
 * - Strings: regular quoted strings and template strings
 *
 * Special handling:
 * - Function names are filtered from parameter counting (calculationExtensions)
 * - Private property identifiers have # prefix removed
 * - Decorators on classes and methods are extracted as identifiers
 * - Destructuring patterns in variables, parameters, and control flow are handled
 * - Constructor keyword is filtered out as it's not a meaningful identifier
 *
 * Note: This definition is also used by TypeScript for extraction, as they share AST structure.
 */
object JavascriptDefinition : LanguageDefinition {
    // ========== Node Type Constants ==========

    private const val IDENTIFIER = "identifier"
    private const val TYPE_IDENTIFIER = "type_identifier"

    // ========== Metrics Configuration ==========

    override val nodeMetrics: Map<String, Set<Metric>> = buildMap {
        // Logic complexity nodes
        listOf(
            "if_statement",
            "do_statement",
            "for_statement",
            "while_statement",
            "for_in_statement",
            "ternary_expression",
            "switch_case",
            "switch_default",
            "catch_clause"
        ).forEach { put(it, setOf(Metric.LogicComplexity)) }

        // Logic complexity with conditional matching (binary operators)
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

        // Function complexity nodes (only complexity, not counted as functions)
        listOf(
            "arrow_function",
            "generator_function",
            "class_static_block"
        ).forEach { put(it, setOf(Metric.FunctionComplexity)) }

        // Function nodes that count for BOTH complexity AND number_of_functions
        listOf(
            "function_declaration",
            "generator_function_declaration",
            "method_definition",
            "function_expression"
        ).forEach { put(it, setOf(Metric.FunctionComplexity, Metric.Function)) }

        // Comment line nodes
        listOf("comment", "html_comment").forEach { put(it, setOf(Metric.CommentLine)) }

        // Function nodes with conditional matching (arrow functions in variable declarators)
        put(
            "variable_declarator",
            setOf(
                Metric.FunctionConditional(
                    MetricCondition.ChildFieldMatches(
                        fieldName = "value",
                        allowedValues = setOf("arrow_function")
                    )
                )
            )
        )

        // Function body nodes
        put("statement_block", setOf(Metric.FunctionBody))

        // Parameter nodes
        put("identifier", setOf(Metric.Parameter))

        // Message chain nodes
        put("member_expression", setOf(Metric.MessageChain))
        put("call_expression", setOf(Metric.MessageChain, Metric.MessageChainCall))
    }

    // ========== Extraction Configuration ==========

    override val nodeExtractions: Map<String, Extract> = buildMap {
        // Identifiers with generic extraction strategies
        put(
            "function_declaration",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByTypes(IDENTIFIER, TYPE_IDENTIFIER))
        )
        put(
            "generator_function_declaration",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByTypes(IDENTIFIER, TYPE_IDENTIFIER))
        )
        put(
            "interface_declaration",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByTypes(IDENTIFIER, TYPE_IDENTIFIER))
        )
        put(
            "type_alias_declaration",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByTypes(IDENTIFIER, TYPE_IDENTIFIER))
        )
        put(
            "internal_module",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByTypes(IDENTIFIER, TYPE_IDENTIFIER))
        )
        put(
            "function_expression",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER))
        )
        put(
            "arrow_function",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER))
        )
        put(
            "required_parameter",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER))
        )
        put(
            "optional_parameter",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER))
        )
        put(
            "type_parameter",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByTypes(TYPE_IDENTIFIER, IDENTIFIER))
        )

        // Identifiers with custom extraction (property names)
        put("field_definition", Extract.Identifier(customSingle = ::extractPropertyName))
        put("public_field_definition", Extract.Identifier(customSingle = ::extractPropertyName))
        put("property_signature", Extract.Identifier(customSingle = ::extractPropertyName))

        // Identifiers with custom multi-extraction
        put(
            "variable_declarator",
            Extract.Identifier(customMulti = ::extractIdentifiersFromVariableDeclarator)
        )
        put("formal_parameters", Extract.Identifier(customMulti = ::extractIdentifiersFromFormalParameters))
        put("class_declaration", Extract.Identifier(customMulti = ::extractIdentifiersFromClassDeclaration))
        put("class", Extract.Identifier(customMulti = ::extractIdentifiersFromClassDeclaration))
        put("method_definition", Extract.Identifier(customMulti = ::extractIdentifiersFromMethodDefinition))
        put("for_in_statement", Extract.Identifier(customMulti = ::extractFirstBindingIdentifiers))
        put("catch_clause", Extract.Identifier(customMulti = ::extractFirstBindingIdentifiers))
        put("enum_declaration", Extract.Identifier(customMulti = ::extractIdentifiersFromEnumDeclaration))

        // Comments (auto-detect format)
        put("comment", Extract.Comment(CommentFormats.AutoDetect))
        put("html_comment", Extract.Comment(CommentFormats.AutoDetect))

        // Strings
        put("string", Extract.StringLiteral(format = StringFormats.Quoted(stripSingleQuotes = true)))
        put("template_string", Extract.StringLiteral(format = StringFormats.Template))
    }

    // ========== Calculation Configuration ==========

    private const val FUNCTION_DECLARATION = "function_declaration"

    override val calculationConfig = CalculationConfig(
        ignoreForParameters = listOf(
            IgnoreRule.TypeWithParentType(IDENTIFIER, FUNCTION_DECLARATION)
        )
    )
}
