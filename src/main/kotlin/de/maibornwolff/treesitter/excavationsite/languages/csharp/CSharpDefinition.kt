package de.maibornwolff.treesitter.excavationsite.languages.csharp

import de.maibornwolff.treesitter.excavationsite.shared.domain.Extract
import de.maibornwolff.treesitter.excavationsite.shared.domain.LanguageDefinition
import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric

/**
 * Unified C# language definition combining metrics and extraction.
 *
 * Composes CSharpMetricMapping and CSharpExtractionMapping.
 */
object CSharpDefinition : LanguageDefinition {
    override val nodeMetrics: Map<String, Set<Metric>> = CSharpMetricMapping.nodeMetrics
    override val nodeExtractions: Map<String, Extract> = CSharpExtractionMapping.nodeExtractions
}
