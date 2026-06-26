package de.maibornwolff.treesitter.excavationsite.languages.rust

import de.maibornwolff.treesitter.excavationsite.shared.domain.Extract
import de.maibornwolff.treesitter.excavationsite.shared.domain.LanguageDefinition
import de.maibornwolff.treesitter.excavationsite.shared.domain.LanguageDependencyMapping
import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric

/**
 * Unified Rust language definition.
 *
 * Rust supports text extraction and dependency analysis. Metrics are out of
 * scope, so [nodeMetrics] is intentionally empty.
 */
object RustDefinition : LanguageDefinition {
    override val nodeMetrics: Map<String, Set<Metric>> = emptyMap()
    override val nodeExtractions: Map<String, Extract> = RustExtractionMapping.nodeExtractions
    override val dependencyMapping: LanguageDependencyMapping = RustDependencyMapping.dependencyMapping
}
