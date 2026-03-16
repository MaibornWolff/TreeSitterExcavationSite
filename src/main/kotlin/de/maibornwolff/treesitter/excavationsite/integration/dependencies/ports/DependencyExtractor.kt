package de.maibornwolff.treesitter.excavationsite.integration.dependencies.ports

import de.maibornwolff.treesitter.excavationsite.shared.domain.Declaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.ImportDeclaration
import org.treesitter.TSNode

/**
 * Port interface for language-specific dependency extraction.
 *
 * Each method extracts one aspect of dependency information from an AST.
 * Implementations are language-specific and live in the languages/ directory.
 *
 * TSLanguage is intentionally excluded from the method signatures — it is an
 * implementation detail of query-based extractors and should be provided at
 * construction time.
 */
interface DependencyExtractor {
    fun extractPackagePath(rootNode: TSNode, sourceCode: String): List<String>

    fun extractImports(rootNode: TSNode, sourceCode: String): List<ImportDeclaration>

    fun extractDeclarations(rootNode: TSNode, sourceCode: String): List<Declaration>
}
