package de.maibornwolff.treesitter.excavationsite.shared.domain

import org.treesitter.TSLanguage
import org.treesitter.TSNode

/**
 * Defines language-specific dependency extraction behavior.
 *
 * Each language provides its own implementation with language-specific
 * TreeSitter queries for package, import, and type extraction.
 */
interface LanguageDependencyMapping {
    fun extractPackagePath(rootNode: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): List<String>

    fun extractImports(rootNode: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): List<ImportDeclaration>

    fun extractDeclarations(rootNode: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): List<Declaration>
}

/**
 * Interface for language-specific dependency mapping definitions.
 *
 * Languages that support dependency mapping provide a [LanguageDependencyMapping].
 * Languages without dependency support use the default (null).
 */
interface DependencyMapping {
    val dependencyMapping: LanguageDependencyMapping?
        get() = null

    val isDependencyMappingSupported: Boolean
        get() = dependencyMapping != null
}
