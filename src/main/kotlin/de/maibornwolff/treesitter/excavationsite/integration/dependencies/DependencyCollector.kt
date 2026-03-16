package de.maibornwolff.treesitter.excavationsite.integration.dependencies

import de.maibornwolff.treesitter.excavationsite.integration.dependencies.ports.DependencyExtractor
import de.maibornwolff.treesitter.excavationsite.shared.domain.DependencyResult
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeSitterParser
import org.treesitter.TSLanguage

/**
 * Orchestrates dependency extraction by parsing source code and
 * delegating to a [DependencyExtractor] for language-specific extraction.
 */
class DependencyCollector(private val treeSitterLanguage: TSLanguage, private val extractor: DependencyExtractor) {
    fun collectDependencies(content: String): DependencyResult {
        val rootNode = TreeSitterParser.parse(content, treeSitterLanguage)
        return DependencyResult(
            packagePath = extractor.extractPackagePath(rootNode, content),
            imports = extractor.extractImports(rootNode, content),
            declarations = extractor.extractDeclarations(rootNode, content)
        )
    }
}
