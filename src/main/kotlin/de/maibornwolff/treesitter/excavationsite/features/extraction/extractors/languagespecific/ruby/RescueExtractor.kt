package de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.ruby

import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

private const val IDENTIFIER = "identifier"
private const val EXCEPTION_VARIABLE = "exception_variable"

/**
 * Extracts exception variable from rescue clause.
 */
internal fun extractFromRescue(node: TSNode, sourceCode: String): String? {
    return node.children()
        .firstOrNull { it.type == EXCEPTION_VARIABLE }
        ?.let { TreeTraversal.findFirstChildTextByType(it, sourceCode, IDENTIFIER) }
}
