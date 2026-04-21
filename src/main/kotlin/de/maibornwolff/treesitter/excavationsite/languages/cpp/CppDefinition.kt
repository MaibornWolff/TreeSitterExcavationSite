package de.maibornwolff.treesitter.excavationsite.languages.cpp

import de.maibornwolff.treesitter.excavationsite.shared.domain.CalculationConfig
import de.maibornwolff.treesitter.excavationsite.shared.domain.Extract
import de.maibornwolff.treesitter.excavationsite.shared.domain.IgnoreRule
import de.maibornwolff.treesitter.excavationsite.shared.domain.LanguageDefinition
import de.maibornwolff.treesitter.excavationsite.shared.domain.LanguageDependencyMapping
import de.maibornwolff.treesitter.excavationsite.shared.domain.Metric

/**
 * Unified C++ language definition combining metrics, extraction, and dependencies.
 *
 * Composes CppMetricMapping, CppExtractionMapping, and CppDependencyMapping.
 */
object CppDefinition : LanguageDefinition {
    override val nodeMetrics: Map<String, Set<Metric>> = CppMetricMapping.nodeMetrics
    override val nodeExtractions: Map<String, Extract> = CppExtractionMapping.nodeExtractions
    override val dependencyMapping: LanguageDependencyMapping = CppDependencyMapping.dependencyMapping

    private const val ABSTRACT_FUNCTION_DECLARATOR = "abstract_function_declarator"
    private const val LAMBDA_EXPRESSION = "lambda_expression"
    private const val FUNCTION_DECLARATOR = "function_declarator"
    private const val FUNCTION_DEFINITION = "function_definition"

    override val calculationConfig = CalculationConfig(
        ignoreForComplexity = listOf(
            IgnoreRule.TypeWithParentType(ABSTRACT_FUNCTION_DECLARATOR, LAMBDA_EXPRESSION),
            IgnoreRule.TypeWithParentType(FUNCTION_DECLARATOR, FUNCTION_DEFINITION)
        )
    )
}
