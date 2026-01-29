package de.maibornwolff.treesitter.excavationsite.languages.java

import de.maibornwolff.treesitter.excavationsite.shared.domain.Extract
import de.maibornwolff.treesitter.excavationsite.shared.domain.LanguageDefinition
import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric

/**
 * Unified Java language definition combining metrics and extraction.
 *
 * Composes JavaMetricMapping and JavaExtractionMapping.
 */
object JavaDefinition : LanguageDefinition {
    override val nodeMetrics: Map<String, Set<Metric>> = JavaMetricMapping.nodeMetrics
    override val nodeExtractions: Map<String, Extract> = JavaExtractionMapping.nodeExtractions
}
