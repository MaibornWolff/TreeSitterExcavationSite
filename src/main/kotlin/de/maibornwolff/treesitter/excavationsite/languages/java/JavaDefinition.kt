package de.maibornwolff.treesitter.excavationsite.languages.java

import de.maibornwolff.treesitter.excavationsite.shared.domain.DependencyAnalyzer
import de.maibornwolff.treesitter.excavationsite.shared.domain.Extract
import de.maibornwolff.treesitter.excavationsite.shared.domain.LanguageDefinition
import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric

/**
 * Unified Java language definition combining metrics, extraction, and dependencies.
 *
 * Composes JavaMetricMapping, JavaExtractionMapping, and JavaDependencyAnalyzer.
 */
object JavaDefinition : LanguageDefinition {
    override val nodeMetrics: Map<String, Set<Metric>> = JavaMetricMapping.nodeMetrics
    override val nodeExtractions: Map<String, Extract> = JavaExtractionMapping.nodeExtractions
    override val dependencyAnalyzer: DependencyAnalyzer = JavaDependencyAnalyzer
}
