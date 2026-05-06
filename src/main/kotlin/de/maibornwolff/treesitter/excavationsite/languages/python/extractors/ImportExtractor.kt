package de.maibornwolff.treesitter.excavationsite.languages.python.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.ImportDeclaration
import org.treesitter.TSNode

internal object ImportExtractor {
    fun extract(rootNode: TSNode, sourceCode: String): List<ImportDeclaration> = emptyList()
}
