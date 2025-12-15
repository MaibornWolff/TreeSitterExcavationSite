package de.maibornwolff.treesitter.excavationsite.languages

import de.maibornwolff.treesitter.excavationsite.shared.domain.CalculationConfig
import de.maibornwolff.treesitter.excavationsite.shared.domain.Extract
import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric

/**
 * Unified language definition that combines metrics, extraction, and dependencies.
 *
 * Each language defines simple maps from node types to behaviors.
 * Languages only define what they have - no forced categories.
 *
 * Example usage:
 * ```kotlin
 * object KotlinDefinition : LanguageDefinition {
 *     override val nodeMetrics = mapOf(
 *         "if_expression" to setOf(Metric.LogicComplexity),
 *         "function_declaration" to setOf(Metric.FunctionComplexity, Metric.Function),
 *         "line_comment" to setOf(Metric.CommentLine)
 *     )
 *
 *     override val nodeExtractions = mapOf(
 *         "class_declaration" to Extract.Identifier(single = ExtractionStrategy.FirstChildByType("type_identifier")),
 *         "line_comment" to Extract.Comment(CommentFormats.Line("//"))
 *     )
 * }
 * ```
 */
interface LanguageDefinition {
    /**
     * Maps node types to their metric contributions.
     *
     * A node can contribute to multiple metrics (e.g., function_declaration
     * contributes to both FunctionComplexity and Function).
     */
    val nodeMetrics: Map<String, Set<Metric>>

    /**
     * Maps node types to their extraction behavior.
     *
     * Each node type can have one extraction behavior (identifier, comment, or string).
     */
    val nodeExtractions: Map<String, Extract>

    /**
     * Language-specific calculation configuration.
     *
     * Used for special cases like Python's indentation-based functions
     * or Ruby's special complexity rules. This declarative config is
     * converted to CalculationExtensions by the metrics adapter.
     */
    val calculationConfig: CalculationConfig
        get() = CalculationConfig()
}
