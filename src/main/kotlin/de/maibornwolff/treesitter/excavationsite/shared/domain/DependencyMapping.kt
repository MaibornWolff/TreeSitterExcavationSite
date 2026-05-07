package de.maibornwolff.treesitter.excavationsite.shared.domain

import org.treesitter.TSNode

/**
 * Holds language-specific dependency extraction functions.
 *
 * Each language provides its own instance with language-specific
 * extractors for package, import, and type extraction.
 * Functions are stored as data (lambdas), consistent with how
 * [Extract] stores custom extraction functions.
 */
data class LanguageDependencyMapping(
    val extractImports: (TSNode, String) -> List<ImportDeclaration>,
    val extractDeclarations: (TSNode, String) -> List<Declaration>,
    // Defaults to empty for languages without an in-source package declaration (e.g. Python — see
    // ADR-0001 in plans/add-python-dependency-support.md). Languages that do declare packages
    // override this with their own extractor.
    val extractPackagePath: (TSNode, String) -> List<String> = { _, _ -> emptyList() }
)

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
