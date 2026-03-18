package de.maibornwolff.treesitter.excavationsite.integration.dependencies.adapters

import de.maibornwolff.treesitter.excavationsite.integration.dependencies.ports.DependencyExtractor
import de.maibornwolff.treesitter.excavationsite.shared.domain.Declaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.ImportDeclaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.LanguageDefinition
import org.treesitter.TSNode

/**
 * Adapts a [LanguageDefinition]'s dependency mapping to the [DependencyExtractor] port.
 */
class LanguageDefinitionDependencyAdapter(definition: LanguageDefinition) : DependencyExtractor {
    private val languageDependencyMapping = definition.dependencyMapping
        ?: throw UnsupportedOperationException("Dependency analysis is not supported for the given language")

    override fun extractPackagePath(rootNode: TSNode, sourceCode: String): List<String> =
        languageDependencyMapping.extractPackagePath(rootNode, sourceCode)

    override fun extractImports(rootNode: TSNode, sourceCode: String): List<ImportDeclaration> =
        languageDependencyMapping.extractImports(rootNode, sourceCode)

    override fun extractDeclarations(rootNode: TSNode, sourceCode: String): List<Declaration> =
        languageDependencyMapping.extractDeclarations(rootNode, sourceCode)
}
