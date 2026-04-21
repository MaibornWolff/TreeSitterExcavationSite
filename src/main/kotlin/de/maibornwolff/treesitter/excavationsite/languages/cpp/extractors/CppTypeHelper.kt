package de.maibornwolff.treesitter.excavationsite.languages.cpp.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.UsedType
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.namedChildren
import org.treesitter.TSNode

internal object CppTypeHelper {
    private const val TYPE_IDENTIFIER = "type_identifier"
    private const val TEMPLATE_TYPE = "template_type"
    private const val TEMPLATE_ARGUMENT_LIST = "template_argument_list"
    private const val TYPE_DESCRIPTOR = "type_descriptor"

    private val TYPE_NODE_TYPES = setOf(TYPE_IDENTIFIER, TEMPLATE_TYPE)

    fun isTypeNode(node: TSNode): Boolean = node.type in TYPE_NODE_TYPES

    fun extractType(typeNode: TSNode, sourceCode: String): UsedType? {
        if (typeNode.isNull) return null
        return when (typeNode.type) {
            TYPE_IDENTIFIER -> UsedType(name = TreeTraversal.getNodeText(typeNode, sourceCode).trim())
            TEMPLATE_TYPE -> extractTemplateType(typeNode, sourceCode)
            else -> null
        }
    }

    private fun extractTemplateType(templateNode: TSNode, sourceCode: String): UsedType? {
        val nameNode = templateNode.children().firstOrNull { it.type == TYPE_IDENTIFIER } ?: return null
        val argList = templateNode.children().firstOrNull { it.type == TEMPLATE_ARGUMENT_LIST }
        val genericTypes = argList
            ?.namedChildren()
            ?.mapNotNull { extractGenericArgument(it, sourceCode) }
            ?.toList()
            ?: emptyList()
        return UsedType(
            name = TreeTraversal.getNodeText(nameNode, sourceCode).trim(),
            genericTypes = genericTypes
        )
    }

    private fun extractGenericArgument(argNode: TSNode, sourceCode: String): UsedType? {
        if (argNode.type == TYPE_DESCRIPTOR) {
            val innerType = argNode.namedChildren().firstOrNull { isTypeNode(it) }
            return innerType?.let { extractType(it, sourceCode) }
        }
        return extractType(argNode, sourceCode)
    }
}
