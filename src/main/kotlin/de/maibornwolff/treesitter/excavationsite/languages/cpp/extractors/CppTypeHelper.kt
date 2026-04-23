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
    private const val NAMESPACE_IDENTIFIER = "namespace_identifier"
    private const val NAME_FIELD = "name"
    private const val SCOPE_FIELD = "scope"
    private const val TYPE_FIELD = "type"

    private val TYPE_NODE_TYPES = setOf(TYPE_IDENTIFIER, TEMPLATE_TYPE, QUALIFIED_IDENTIFIER)

    fun isTypeNode(node: TSNode): Boolean = node.type in TYPE_NODE_TYPES

    fun extractType(typeNode: TSNode, sourceCode: String): UsedType? {
        if (typeNode.isNull) return null
        return when (typeNode.type) {
            TYPE_IDENTIFIER -> UsedType(name = TreeTraversal.getNodeText(typeNode, sourceCode).trim())
            TEMPLATE_TYPE -> extractTemplateType(typeNode, sourceCode)
            QUALIFIED_IDENTIFIER -> extractRightmostSegment(typeNode, sourceCode)
            else -> null
        }
    }

    fun extractTypeFromTypeField(node: TSNode, sourceCode: String): UsedType? {
        val typeField = node.getChildByFieldName(TYPE_FIELD).takeIf { !it.isNull }
            ?: node.namedChildren().firstOrNull { isTypeNode(it) || it.type == TYPE_DESCRIPTOR }
            ?: return null
        val unwrapped = if (typeField.type == TYPE_DESCRIPTOR) {
            typeField.namedChildren().firstOrNull { isTypeNode(it) } ?: return null
        } else {
            typeField
        }
        if (!isTypeNode(unwrapped)) return null
        return extractType(unwrapped, sourceCode)
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
        val (segments, leaf) = walkQualified(qualifiedId, sourceCode)
        val text = leaf ?: return null
        return if (text.isEmpty()) null else UsedType(name = text, namespacePrefix = segments)
    }

    fun extractSingleSegmentScope(qualifiedId: TSNode, sourceCode: String): UsedType? {
        val scope = qualifiedId.getChildByFieldName(SCOPE_FIELD).takeIf { !it.isNull } ?: return null
        if (scope.type != NAMESPACE_IDENTIFIER) return null
        val text = TreeTraversal.getNodeText(scope, sourceCode).trim()
        return if (text.isEmpty()) null else UsedType(name = text)
    }

    fun extractSecondToLastSegment(qualifiedId: TSNode, sourceCode: String): UsedType? {
        val (segments, _) = walkQualified(qualifiedId, sourceCode)
        val name = segments.lastOrNull() ?: return null
        return UsedType(name = name, namespacePrefix = segments.dropLast(1))
    }

    private fun walkQualified(qualifiedId: TSNode, sourceCode: String): Pair<List<String>, String?> {
        val scopeSegments = mutableListOf<String>()
        var node = qualifiedId
        while (node.type == QUALIFIED_IDENTIFIER) {
            val scope = node.getChildByFieldName(SCOPE_FIELD)
            if (!scope.isNull) {
                scopeSegments.add(TreeTraversal.getNodeText(scope, sourceCode).trim())
            }
            val nameField = node.getChildByFieldName(NAME_FIELD)
            if (nameField.isNull) return scopeSegments to null
            node = nameField
        }
        return scopeSegments to TreeTraversal.getNodeText(node, sourceCode).trim()
    }
}
