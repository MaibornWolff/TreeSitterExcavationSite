package de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.cpp

import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.c.CDeclaratorParser
import org.treesitter.TSNode

internal fun extractFromParameterDeclaration(node: TSNode, sourceCode: String): String? {
    return CDeclaratorParser.extractIdentifierFromDeclaratorField(node, sourceCode)
}
