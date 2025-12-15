package de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.python

import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

/**
 * Extracts all identifiers from a node (used for global/nonlocal statements).
 */
internal fun extractAllIdentifiers(node: TSNode, sourceCode: String): List<String> {
    return node.children()
        .filter { it.type == "identifier" }
        .map { TreeTraversal.getNodeText(it, sourceCode) }
        .toList()
}
