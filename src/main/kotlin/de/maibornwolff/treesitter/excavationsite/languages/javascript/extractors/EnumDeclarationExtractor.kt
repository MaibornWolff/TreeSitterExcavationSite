package de.maibornwolff.treesitter.excavationsite.languages.javascript.extractors

import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

private const val IDENTIFIER = "identifier"
private const val TYPE_IDENTIFIER = "type_identifier"
private const val ENUM_BODY = "enum_body"
private const val ENUM_ASSIGNMENT = "enum_assignment"
private const val PROPERTY_IDENTIFIER = "property_identifier"

/**
 * Extracts enum name and all member names from enum declaration.
 */
internal fun extractIdentifiersFromEnumDeclaration(node: TSNode, sourceCode: String): List<String> {
    val enumName = TreeTraversal.findFirstChildTextByType(
        node,
        sourceCode,
        IDENTIFIER,
        TYPE_IDENTIFIER
    )
    val enumMembers = node.children()
        .filter { it.type == ENUM_BODY }
        .flatMap { it.children() }
        .mapNotNull { child ->
            when (child.type) {
                PROPERTY_IDENTIFIER -> {
                    TreeTraversal.getNodeText(child, sourceCode)
                }
                ENUM_ASSIGNMENT -> {
                    TreeTraversal.findFirstChildTextByType(
                        child,
                        sourceCode,
                        PROPERTY_IDENTIFIER,
                        IDENTIFIER
                    )
                }
                else -> null
            }
        }.toList()
    return listOfNotNull(enumName) + enumMembers
}
