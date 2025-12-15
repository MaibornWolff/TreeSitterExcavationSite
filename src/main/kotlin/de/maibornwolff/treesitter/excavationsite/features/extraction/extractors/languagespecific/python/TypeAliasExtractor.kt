package de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.python

import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import org.treesitter.TSNode

/**
 * Extracts type alias name: `type Point = tuple[int, int]`.
 */
internal fun extractFromTypeAlias(node: TSNode, sourceCode: String, findFirstIdentifier: (TSNode, String) -> String?): String? {
    val nameNode = node.getChildByFieldName("name")
    if (nameNode != null && !nameNode.isNull) {
        return TreeTraversal.getNodeText(nameNode, sourceCode)
    }
    return findFirstIdentifier(node, sourceCode)
}
