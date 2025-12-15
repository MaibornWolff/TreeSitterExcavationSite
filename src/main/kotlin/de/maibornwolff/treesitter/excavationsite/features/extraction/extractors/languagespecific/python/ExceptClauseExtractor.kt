package de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.python

import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import org.treesitter.TSNode

/**
 * Extracts exception variable from except clause: `except ValueError as e`.
 */
internal fun extractFromExceptClause(node: TSNode, sourceCode: String): String? {
    val nameNode = node.getChildByFieldName("name")
    if (nameNode == null || nameNode.isNull) return null
    return TreeTraversal.getNodeText(nameNode, sourceCode)
}
