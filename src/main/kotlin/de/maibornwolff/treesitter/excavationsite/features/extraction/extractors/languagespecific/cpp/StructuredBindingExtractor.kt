package de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.cpp

import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

private const val IDENTIFIER = "identifier"

internal fun extractFromStructuredBinding(node: TSNode, sourceCode: String): List<String> {
    return node.children()
        .filter { it.type == IDENTIFIER }
        .map { TreeTraversal.getNodeText(it, sourceCode) }
        .toList()
}
