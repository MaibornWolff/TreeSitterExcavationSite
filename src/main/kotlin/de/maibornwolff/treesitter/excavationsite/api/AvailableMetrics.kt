package de.maibornwolff.treesitter.excavationsite.api

import de.maibornwolff.treesitter.excavationsite.features.metrics.domain.AvailableFileMetrics as BaseAvailableFileMetrics
import de.maibornwolff.treesitter.excavationsite.features.metrics.domain.AvailableFunctionMetrics as BaseAvailableFunctionMetrics

// Re-export from shared package for public API backward compatibility
typealias AvailableFileMetrics = BaseAvailableFileMetrics
typealias AvailableFunctionMetrics = BaseAvailableFunctionMetrics
