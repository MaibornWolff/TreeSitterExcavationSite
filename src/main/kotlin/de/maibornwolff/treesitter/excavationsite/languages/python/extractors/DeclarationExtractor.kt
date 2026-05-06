package de.maibornwolff.treesitter.excavationsite.languages.python.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.Declaration
import org.treesitter.TSNode

internal object DeclarationExtractor {
    fun extract(rootNode: TSNode, sourceCode: String): List<Declaration> = emptyList()
}
