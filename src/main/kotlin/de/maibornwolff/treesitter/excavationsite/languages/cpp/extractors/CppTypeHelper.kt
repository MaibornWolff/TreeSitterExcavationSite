package de.maibornwolff.treesitter.excavationsite.languages.cpp.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.UsedType
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import org.treesitter.TSNode

internal object CppTypeHelper {
    private const val TYPE_IDENTIFIER = "type_identifier"

    private val TYPE_NODE_TYPES = setOf(TYPE_IDENTIFIER)

    fun isTypeNode(node: TSNode): Boolean = node.type in TYPE_NODE_TYPES

    fun extractType(typeNode: TSNode, sourceCode: String): UsedType? {
        if (typeNode.isNull) return null
        return when (typeNode.type) {
            TYPE_IDENTIFIER -> UsedType(name = TreeTraversal.getNodeText(typeNode, sourceCode).trim())
            else -> null
        }
    }
}
