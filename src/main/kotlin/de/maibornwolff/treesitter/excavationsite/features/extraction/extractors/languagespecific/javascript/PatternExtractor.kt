package de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.javascript

import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

private const val IDENTIFIER = "identifier"
private const val OBJECT_PATTERN = "object_pattern"
private const val ARRAY_PATTERN = "array_pattern"
private const val REST_PATTERN = "rest_pattern"
private const val PAIR_PATTERN = "pair_pattern"
private const val OBJECT_ASSIGNMENT_PATTERN = "object_assignment_pattern"
private const val ASSIGNMENT_PATTERN = "assignment_pattern"
private const val SHORTHAND_PROPERTY_IDENTIFIER_PATTERN = "shorthand_property_identifier_pattern"

/**
 * Recursively extracts identifiers from destructuring patterns.
 */
internal fun extractIdentifiersFromPattern(node: TSNode, sourceCode: String): List<String> {
    return when (node.type) {
        SHORTHAND_PROPERTY_IDENTIFIER_PATTERN,
        IDENTIFIER -> {
            listOf(TreeTraversal.getNodeText(node, sourceCode))
        }
        PAIR_PATTERN -> extractIdentifiersFromPairPattern(node, sourceCode)
        OBJECT_ASSIGNMENT_PATTERN,
        ASSIGNMENT_PATTERN -> {
            extractIdentifiersFromAssignmentPattern(node, sourceCode)
        }
        REST_PATTERN -> {
            listOfNotNull(
                TreeTraversal.findFirstChildTextByType(
                    node,
                    sourceCode,
                    IDENTIFIER
                )
            )
        }
        else -> node.children().flatMap { extractIdentifiersFromPattern(it, sourceCode) }.toList()
    }
}

/**
 * Extracts identifiers from pair patterns in object destructuring.
 */
internal fun extractIdentifiersFromPairPattern(node: TSNode, sourceCode: String): List<String> {
    return node.children().flatMap { child ->
        when (child.type) {
            IDENTIFIER -> {
                listOf(TreeTraversal.getNodeText(child, sourceCode))
            }
            OBJECT_PATTERN,
            ARRAY_PATTERN -> extractIdentifiersFromPattern(child, sourceCode)
            else -> emptyList()
        }
    }.toList()
}

/**
 * Extracts identifiers from assignment patterns with default values.
 */
internal fun extractIdentifiersFromAssignmentPattern(node: TSNode, sourceCode: String): List<String> {
    return node.children().firstOrNull()?.let {
        extractIdentifiersFromPattern(it, sourceCode)
    } ?: emptyList()
}
