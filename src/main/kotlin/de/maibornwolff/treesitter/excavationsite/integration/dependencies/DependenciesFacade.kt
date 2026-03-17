package de.maibornwolff.treesitter.excavationsite.integration.dependencies

import de.maibornwolff.treesitter.excavationsite.integration.dependencies.adapters.LanguageDefinitionDependencyAdapter
import de.maibornwolff.treesitter.excavationsite.shared.domain.DependencyResult
import de.maibornwolff.treesitter.excavationsite.shared.domain.LanguageDefinition
import org.treesitter.TSLanguage

object DependenciesFacade {
    fun analyze(content: String, treeSitterLanguage: TSLanguage, definition: LanguageDefinition): DependencyResult {
        val processedContent = definition.preprocessor?.invoke(content) ?: content
        val extractor = LanguageDefinitionDependencyAdapter(definition, treeSitterLanguage)
        val collector = DependencyCollector(treeSitterLanguage, extractor)
        return collector.collectDependencies(processedContent)
    }
}
