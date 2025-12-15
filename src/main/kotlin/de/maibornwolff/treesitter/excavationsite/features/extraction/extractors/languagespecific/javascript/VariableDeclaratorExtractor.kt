package de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.javascript

import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

private const val IDENTIFIER = "identifier"
private const val OBJECT_PATTERN = "object_pattern"
private const val ARRAY_PATTERN = "array_pattern"

/**
 * Extracts identifiers from variable declarator, handling destructuring patterns.
 */
internal fun extractIdentifiersFromVariableDeclarator(node: TSNode, sourceCode: String): List<String> {
    val firstChild = node.children().firstOrNull() ?: return emptyList()
    return when (firstChild.type) {
        OBJECT_PATTERN,
        ARRAY_PATTERN -> extractIdentifiersFromPattern(firstChild, sourceCode)
        IDENTIFIER -> {
            listOf(TreeTraversal.getNodeText(firstChild, sourceCode))
        }
        else -> emptyList()
    }
}
