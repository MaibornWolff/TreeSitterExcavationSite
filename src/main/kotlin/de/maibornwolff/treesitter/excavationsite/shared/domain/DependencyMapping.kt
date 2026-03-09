package de.maibornwolff.treesitter.excavationsite.shared.domain

import org.treesitter.TSLanguage
import org.treesitter.TSNode

/**
 * Analyzes an AST to extract dependency information.
 *
 * Each language provides its own implementation with language-specific
 * TreeSitter queries for package, import, and type extraction.
 */
interface DependencyAnalyzer {
    fun analyze(rootNode: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): DependencyResult
}

/**
 * Interface for language-specific dependency analysis definitions.
 *
 * Languages that support dependency analysis provide a [DependencyAnalyzer].
 * Languages without dependency support use the default (null).
 */
interface DependencyMapping {
    val dependencyAnalyzer: DependencyAnalyzer?
        get() = null

    val isDependencyAnalysisSupported: Boolean
        get() = dependencyAnalyzer != null
}
