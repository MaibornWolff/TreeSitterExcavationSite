package de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.cpp

import de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.c.CDeclaratorParser
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

private const val IDENTIFIER = "identifier"
private const val QUALIFIED_IDENTIFIER = "qualified_identifier"

internal fun extractFromUsingDeclaration(node: TSNode, sourceCode: String): String? {
    for (child in node.children()) {
        when (child.type) {
            QUALIFIED_IDENTIFIER -> return CDeclaratorParser.extractFromQualifiedIdentifier(child, sourceCode)
            IDENTIFIER -> return TreeTraversal.getNodeText(child, sourceCode)
        }
    }
    return null
}
