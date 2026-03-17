package de.maibornwolff.treesitter.excavationsite.integration.dependencies.adapters

import de.maibornwolff.treesitter.excavationsite.integration.dependencies.ports.DependencyExtractor
import de.maibornwolff.treesitter.excavationsite.shared.domain.Declaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.ImportDeclaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.LanguageDefinition
import org.treesitter.TSLanguage
import org.treesitter.TSNode

/**
 * Binds the TSLanguage at construction time so that the port methods
 * do not need to carry it as a parameter.
 */
class LanguageDefinitionDependencyAdapter(definition: LanguageDefinition, private val treeSitterLanguage: TSLanguage) :
    DependencyExtractor {
    private val languageDependencyMapping = definition.dependencyMapping
        ?: throw UnsupportedOperationException("Dependency analysis is not supported for the given language")

    override fun extractPackagePath(rootNode: TSNode, sourceCode: String): List<String> =
        languageDependencyMapping.extractPackagePath(rootNode, sourceCode, treeSitterLanguage)

    override fun extractImports(rootNode: TSNode, sourceCode: String): List<ImportDeclaration> =
        languageDependencyMapping.extractImports(rootNode, sourceCode, treeSitterLanguage)

    override fun extractDeclarations(rootNode: TSNode, sourceCode: String): List<Declaration> =
        languageDependencyMapping.extractDeclarations(rootNode, sourceCode, treeSitterLanguage)
}
