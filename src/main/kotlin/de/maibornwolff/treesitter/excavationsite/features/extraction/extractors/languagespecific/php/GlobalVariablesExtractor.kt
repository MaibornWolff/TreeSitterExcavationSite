package de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.php

import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

private const val VARIABLE_NAME = "variable_name"

/**
 * Extracts all variable names from global declaration.
 * Example: `global $x, $y, $z;` -> ["x", "y", "z"]
 */
internal fun extractGlobalVariables(node: TSNode, sourceCode: String): List<String> {
    return node.children()
        .filter { it.type == VARIABLE_NAME }
        .map { stripDollarPrefix(TreeTraversal.getNodeText(it, sourceCode)) }
        .toList()
}
