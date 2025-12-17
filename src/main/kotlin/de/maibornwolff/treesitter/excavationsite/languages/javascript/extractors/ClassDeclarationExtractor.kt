package de.maibornwolff.treesitter.excavationsite.languages.javascript.extractors

import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

private const val IDENTIFIER = "identifier"
private const val TYPE_IDENTIFIER = "type_identifier"
private const val DECORATOR = "decorator"

/**
 * Extracts identifiers from class declaration including decorators.
 */
internal fun extractIdentifiersFromClassDeclaration(node: TSNode, sourceCode: String): List<String> {
    val decorators = node.children()
        .filter { it.type == DECORATOR }
        .mapNotNull { extractDecoratorName(it, sourceCode) }
        .toList()
    val className = TreeTraversal.findFirstChildTextByType(
        node,
        sourceCode,
        IDENTIFIER,
        TYPE_IDENTIFIER
    )
    return decorators + listOfNotNull(className)
}
