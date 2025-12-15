package de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.c

import org.treesitter.TSNode

/**
 * Extracts identifier from function definition.
 * C's declarator syntax requires recursive traversal through pointer/array/function declarators.
 */
internal fun extractFromFunctionDefinition(node: TSNode, sourceCode: String): String? {
    return CDeclaratorParser.extractIdentifierFromDeclaratorField(node, sourceCode)
}
