package de.maibornwolff.treesitter.excavationsite.languages.vue

import de.maibornwolff.treesitter.excavationsite.shared.domain.CalculationConfig
import de.maibornwolff.treesitter.excavationsite.shared.domain.Extract
import de.maibornwolff.treesitter.excavationsite.shared.domain.IgnoreRule
import de.maibornwolff.treesitter.excavationsite.shared.domain.LanguageDefinition
import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric

/**
 * Unified Vue language definition combining metrics and extraction.
 *
 * Composes VueMetricMapping and VueExtractionMapping.
 */
object VueDefinition : LanguageDefinition {
    override val nodeMetrics: Map<String, Set<Metric>> = VueMetricMapping.nodeMetrics
    override val nodeExtractions: Map<String, Extract> = VueExtractionMapping.nodeExtractions

    private const val IDENTIFIER = "identifier"
    private const val FUNCTION_DECLARATION = "function_declaration"

    override val calculationConfig = CalculationConfig(
        ignoreForParameters = listOf(
            IgnoreRule.TypeWithParentType(IDENTIFIER, FUNCTION_DECLARATION)
        )
    )
}
