package de.maibornwolff.treesitter.excavationsite.languages.javascript.extractors

import org.treesitter.TSNode

private const val DECORATOR = "decorator"

/**
 * Extracts identifiers from method definition including preceding decorators.
 */
internal fun extractIdentifiersFromMethodDefinition(node: TSNode, sourceCode: String): List<String> {
    val decorators = generateSequence(node.prevSibling) { it.prevSibling }
        .takeWhile { it.type == DECORATOR }
        .mapNotNull { extractDecoratorName(it, sourceCode) }
        .toList()
        .reversed()
    val propertyName = extractPropertyName(node, sourceCode)
    return decorators + listOfNotNull(propertyName)
}
