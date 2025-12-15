package de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec

import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.c.CDeclaratorParser
import org.treesitter.TSNode

internal fun extractFromFunctionDefinition(node: TSNode, sourceCode: String): String? {
    return CDeclaratorParser.extractIdentifierFromDeclaratorField(node, sourceCode)
}
