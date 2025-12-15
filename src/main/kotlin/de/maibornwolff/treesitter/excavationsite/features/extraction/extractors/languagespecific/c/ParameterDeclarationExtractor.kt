package de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.c

import org.treesitter.TSNode

/**
 * Extracts identifier from parameter declaration.
 */
internal fun extractFromParameterDeclaration(node: TSNode, sourceCode: String): String? {
    return CDeclaratorParser.extractIdentifierFromDeclaratorField(node, sourceCode)
}
