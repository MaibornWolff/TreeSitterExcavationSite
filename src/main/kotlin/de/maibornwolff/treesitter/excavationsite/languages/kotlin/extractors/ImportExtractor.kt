package de.maibornwolff.treesitter.excavationsite.languages.kotlin.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.ImportDeclaration
import org.treesitter.TSNode

internal object ImportExtractor {
    fun extract(rootNode: TSNode, sourceCode: String): List<ImportDeclaration> {
        return emptyList()
    }
}
