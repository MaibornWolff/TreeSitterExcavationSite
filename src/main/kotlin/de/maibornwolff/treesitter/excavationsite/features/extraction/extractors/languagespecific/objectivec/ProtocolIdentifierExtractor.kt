package de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.objectivec

import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import org.treesitter.TSNode

private const val IDENTIFIER = "identifier"

internal fun extractProtocolIdentifier(node: TSNode, sourceCode: String): String? {
    return TreeTraversal.findChildByType(node, IDENTIFIER, sourceCode)
}
