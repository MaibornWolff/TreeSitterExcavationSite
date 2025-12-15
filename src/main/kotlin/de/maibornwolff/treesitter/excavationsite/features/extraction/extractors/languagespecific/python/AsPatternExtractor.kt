package de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.python

import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import org.treesitter.TSNode

/**
 * Extracts as-pattern alias: `with open(f) as file` -> "file".
 * Returns null for as_pattern inside case_clause (handled by pattern matching).
 */
internal fun extractFromAsPattern(
    node: TSNode,
    sourceCode: String,
    findFirstIdentifier: (TSNode, String) -> String?,
    findLastIdentifier: (TSNode, String) -> String?
): String? {
    if (TreeTraversal.hasAncestorOfType(node, "case_clause")) return null

    val aliasNode = node.getChildByFieldName("alias")
    if (aliasNode == null || aliasNode.isNull) {
        return findLastIdentifier(node, sourceCode)
    }

    return if (aliasNode.type == "identifier") {
        TreeTraversal.getNodeText(aliasNode, sourceCode)
    } else {
        findFirstIdentifier(aliasNode, sourceCode)
    }
}
