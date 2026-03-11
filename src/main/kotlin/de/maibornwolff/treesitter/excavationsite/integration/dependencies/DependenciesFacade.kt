package de.maibornwolff.treesitter.excavationsite.integration.dependencies

import de.maibornwolff.treesitter.excavationsite.shared.domain.DependencyResult
import de.maibornwolff.treesitter.excavationsite.shared.domain.LanguageDefinition
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeSitterParser
import org.treesitter.TSLanguage

object DependenciesFacade {
    fun analyze(content: String, treeSitterLanguage: TSLanguage, definition: LanguageDefinition): DependencyResult {
        val analyzer = definition.dependencyAnalyzer
            ?: throw UnsupportedOperationException("Dependency analysis is not supported for the given language")

        val rootNode = TreeSitterParser.parse(content, treeSitterLanguage)
        return analyzer.analyze(rootNode, content, treeSitterLanguage)
    }
}
