package de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec

import de.maibornwolff.treesitter.excavationsite.features.extraction.parsers.StringParser
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import org.treesitter.TSNode

private const val STRING_EXPRESSION = "string_expression"

internal fun extractCStringLiteral(node: TSNode, sourceCode: String): String? {
    val parent = node.parent
    if (parent != null && parent.type == STRING_EXPRESSION) {
        return null
    }
    return StringParser.stripQuotes(TreeTraversal.getNodeText(node, sourceCode))
}
