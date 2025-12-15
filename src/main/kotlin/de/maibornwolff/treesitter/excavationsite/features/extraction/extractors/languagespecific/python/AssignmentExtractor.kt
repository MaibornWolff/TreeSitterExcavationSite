package de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.python

import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

/**
 * Extracts identifier from simple assignments only.
 * Tuple unpacking and other complex assignments are not extracted.
 */
internal fun extractFromAssignment(node: TSNode, sourceCode: String): String? {
    return node.children().firstOrNull()
        ?.takeIf { it.type == "identifier" }
        ?.let { TreeTraversal.getNodeText(it, sourceCode) }
}
