package de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.python

import de.maibornwolff.treesitter.excavationsite.features.extraction.parsers.StringParser
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import org.treesitter.TSNode

/**
 * Extracts string content, but returns null for docstrings.
 * A docstring is a string that is the only child of an expression_statement.
 */
internal fun extractString(node: TSNode, sourceCode: String): String? {
    if (isDocstring(node)) return null
    val text = TreeTraversal.getNodeText(node, sourceCode)
    return StringParser.stripPythonString(text)
}

private fun isDocstring(node: TSNode): Boolean {
    val parent = node.parent
    return parent?.type == "expression_statement" && parent.childCount == 1
}
