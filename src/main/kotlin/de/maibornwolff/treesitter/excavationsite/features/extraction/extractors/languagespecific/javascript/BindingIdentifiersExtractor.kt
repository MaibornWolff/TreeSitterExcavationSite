package de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.javascript

import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

private const val IDENTIFIER = "identifier"
private const val OBJECT_PATTERN = "object_pattern"
private const val ARRAY_PATTERN = "array_pattern"

/**
 * Extracts binding identifiers from for-in statements and catch clauses.
 */
internal fun extractFirstBindingIdentifiers(node: TSNode, sourceCode: String): List<String> {
    for (child in node.children()) {
        when (child.type) {
            IDENTIFIER -> {
                return listOf(TreeTraversal.getNodeText(child, sourceCode))
            }
            OBJECT_PATTERN,
            ARRAY_PATTERN -> {
                return extractIdentifiersFromPattern(child, sourceCode)
            }
        }
    }
    return emptyList()
}
