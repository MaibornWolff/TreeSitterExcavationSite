package de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.javascript

import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

private const val IDENTIFIER = "identifier"
private const val PROPERTY_IDENTIFIER = "property_identifier"
private const val PRIVATE_PROPERTY_IDENTIFIER = "private_property_identifier"
private const val CONSTRUCTOR = "constructor"

/**
 * Extracts property name from field definitions, handling private # prefix.
 * Skips 'constructor' as it's a keyword, not a meaningful identifier.
 */
internal fun extractPropertyName(node: TSNode, sourceCode: String): String? {
    for (child in node.children()) {
        when (child.type) {
            IDENTIFIER,
            PROPERTY_IDENTIFIER -> {
                val name = TreeTraversal.getNodeText(child, sourceCode)
                if (name == CONSTRUCTOR) return null
                return name
            }
            PRIVATE_PROPERTY_IDENTIFIER -> {
                return TreeTraversal.getNodeText(child, sourceCode).removePrefix("#")
            }
        }
    }
    return null
}
