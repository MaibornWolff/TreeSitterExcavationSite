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
    private const val QUALIFIED_IDENTIFIER = "qualified_identifier"
    private const val NAME_FIELD = "name"
    private const val SCOPE_FIELD = "scope"

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

    fun extractRightmostSegment(qualifiedId: TSNode, sourceCode: String): UsedType? {
        var node = qualifiedId
        while (node.type == QUALIFIED_IDENTIFIER) {
            val nameField = node.getChildByFieldName(NAME_FIELD)
            if (nameField.isNull) return null
            node = nameField
        }
        val text = TreeTraversal.getNodeText(node, sourceCode).trim()
        return if (text.isEmpty()) null else UsedType(name = text)
    }

    fun extractSecondToLastSegment(qualifiedId: TSNode, sourceCode: String): UsedType? {
        val scopeSegments = mutableListOf<String>()
        var node = qualifiedId
        while (node.type == QUALIFIED_IDENTIFIER) {
            val scope = node.getChildByFieldName(SCOPE_FIELD)
            if (!scope.isNull) {
                scopeSegments.add(TreeTraversal.getNodeText(scope, sourceCode).trim())
            }
            node = node.getChildByFieldName(NAME_FIELD)
            if (node.isNull) break
        }
        return scopeSegments.lastOrNull()?.let { UsedType(name = it) }
    }
}
