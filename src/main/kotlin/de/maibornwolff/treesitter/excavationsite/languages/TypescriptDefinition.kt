package de.maibornwolff.treesitter.excavationsite.languages
import de.maibornwolff.treesitter.excavationsite.shared.domain.Extract
import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric
import de.maibornwolff.treesitter.excavationsite.shared.domain.MetricCondition

/**
 * TypeScript language definition combining metrics and extraction.
 *
 * Metrics:
 * - Logic complexity: if, do, for, while, for-in, ternary, conditional_type, switch cases, catch
 * - Logic complexity (nested): binary_expression with &&, ||, ??
 * - Function complexity: function declarations, generators, arrows, methods, expressions, static blocks
 * - Comments: line and html comments
 * - Functions: counted via simple and nested matching (arrow functions in variable declarators)
 * - Function bodies: statement_block
 * - Parameters: required_parameter (TypeScript-specific, differs from JavaScript's identifier)
 * - Message chains: member and call expressions
 *
 * Extraction:
 * - Shares extraction configuration with JavaScript by referencing JavascriptDefinition.nodeExtractions
 * - Both languages share the same AST structure for identifier extraction purposes
 *
 * TypeScript-specific nodes:
 * - conditional_type: unique to TypeScript type system
 * - required_parameter: TypeScript uses this instead of JavaScript's identifier for parameters
 */
object TypescriptDefinition : LanguageDefinition {
    // ========== Metrics Configuration ==========

    override val nodeMetrics: Map<String, Set<Metric>> = buildMap {
        // Logic complexity - simple nodes
        listOf(
            "if_statement",
            "do_statement",
            "for_statement",
            "while_statement",
            "for_in_statement",
            "ternary_expression",
            // TypeScript-specific
            "conditional_type",
            "switch_case",
            "switch_default",
            "catch_clause"
        ).forEach { put(it, setOf(Metric.LogicComplexity)) }

        // Logic complexity - conditional (binary_expression with logical operators)
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

        // Parameter nodes - TypeScript uses required_parameter
        put("required_parameter", setOf(Metric.Parameter))

        // Message chain nodes
        put("member_expression", setOf(Metric.MessageChain))
        put("call_expression", setOf(Metric.MessageChain, Metric.MessageChainCall))
    }

    // ========== Extraction Configuration ==========

    /**
     * TypeScript shares extraction configuration with JavaScript.
     *
     * Both languages share the same AST structure for identifier extraction purposes,
     * so we directly reference JavascriptDefinition's nodeExtractions.
     */
    override val nodeExtractions: Map<String, Extract>
        get() {
            return JavascriptDefinition.nodeExtractions
        }
}
