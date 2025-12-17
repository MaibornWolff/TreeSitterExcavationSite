package de.maibornwolff.treesitter.excavationsite.languages.vue

import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric
import de.maibornwolff.treesitter.excavationsite.shared.domain.MetricCondition
import de.maibornwolff.treesitter.excavationsite.shared.domain.MetricMapping

/**
 * Vue metric definitions.
 *
 * Vue Single File Components embed JavaScript in <script> sections.
 * The tree-sitter-vue grammar parses JavaScript nodes within script_element.
 */
object VueMetricMapping : MetricMapping {
    override val nodeMetrics: Map<String, Set<Metric>> = buildMap {
        // Logic complexity nodes (JavaScript nodes within script section)
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
}
