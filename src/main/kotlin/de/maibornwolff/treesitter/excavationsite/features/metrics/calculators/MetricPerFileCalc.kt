package de.maibornwolff.treesitter.excavationsite.features.metrics.calculators

import de.maibornwolff.treesitter.excavationsite.features.metrics.domain.CalculationContext

interface MetricPerFileCalc {
    fun calculateMetricForNode(nodeContext: CalculationContext): Int
}
