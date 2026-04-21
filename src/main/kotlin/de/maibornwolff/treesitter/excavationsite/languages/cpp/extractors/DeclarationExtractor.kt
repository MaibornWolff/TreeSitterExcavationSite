package de.maibornwolff.treesitter.excavationsite.languages.cpp.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.Declaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.DeclarationType
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

internal object DeclarationExtractor {
    private const val CLASS_SPECIFIER = "class_specifier"
    private const val STRUCT_SPECIFIER = "struct_specifier"
    private const val UNION_SPECIFIER = "union_specifier"
    private const val ENUM_SPECIFIER = "enum_specifier"
    private const val TYPE_IDENTIFIER = "type_identifier"
    private const val FIELD_DECLARATION_LIST = "field_declaration_list"
    private const val ENUMERATOR_LIST = "enumerator_list"
    private const val NAMESPACE_DEFINITION = "namespace_definition"
    private const val NAMESPACE_IDENTIFIER = "namespace_identifier"
    private const val NESTED_NAMESPACE_SPECIFIER = "nested_namespace_specifier"
    private const val NAMESPACE_SEPARATOR = "::"

    private val DECLARATION_NODE_TYPES = setOf(CLASS_SPECIFIER, STRUCT_SPECIFIER, UNION_SPECIFIER, ENUM_SPECIFIER)

    fun extract(rootNode: TSNode, sourceCode: String): List<Declaration> {
        val allDeclarationNodes = TreeTraversal
            .findAllDescendantsOfType(rootNode, *DECLARATION_NODE_TYPES.toTypedArray())
        val nameByStartByte = allDeclarationNodes.associate { node ->
            node.startByte to TreeTraversal.findFirstChildTextByType(node, sourceCode, TYPE_IDENTIFIER)
        }
        return allDeclarationNodes.mapNotNull { toDeclaration(it, sourceCode, nameByStartByte) }
    }

    private fun toDeclaration(node: TSNode, sourceCode: String, nameByStartByte: Map<Int, String?>): Declaration? {
        if (!hasBody(node)) return null
        val name = nameByStartByte[node.startByte] ?: return null
        return Declaration(
            name = name,
            type = mapType(node.type),
            usedTypes = emptySet(),
            parentPath = findNamespacePath(node, sourceCode) + findParentClassPath(node, nameByStartByte)
        )
    }

    private fun hasBody(node: TSNode): Boolean {
        val bodyType = if (node.type == ENUM_SPECIFIER) ENUMERATOR_LIST else FIELD_DECLARATION_LIST
        return node.children().any { it.type == bodyType }
    }

    private fun findNamespacePath(node: TSNode, sourceCode: String): List<String> {
        val segments = mutableListOf<List<String>>()
        var current = node.parent
        while (current != null && !current.isNull) {
            if (current.type == NAMESPACE_DEFINITION) {
                segments.add(0, extractNamespaceSegments(current, sourceCode))
            }
            current = current.parent
        }
        return segments.flatten()
    }

    private fun findParentClassPath(node: TSNode, nameByStartByte: Map<Int, String?>): List<String> {
        val parents = mutableListOf<String>()
        var current = node.parent
        while (current != null && !current.isNull) {
            if (current.type in DECLARATION_NODE_TYPES) {
                val parentName = nameByStartByte[current.startByte]
                if (!parentName.isNullOrBlank()) {
                    parents.add(0, parentName)
                }
            }
            current = current.parent
        }
        return parents
    }

    private fun extractNamespaceSegments(namespaceNode: TSNode, sourceCode: String): List<String> {
        val text = TreeTraversal.findFirstChildTextByType(
            namespaceNode,
            sourceCode,
            NAMESPACE_IDENTIFIER,
            NESTED_NAMESPACE_SPECIFIER
        ) ?: return emptyList()
        return text.split(NAMESPACE_SEPARATOR)
    }

    private fun mapType(nodeType: String): DeclarationType = when (nodeType) {
        ENUM_SPECIFIER -> DeclarationType.ENUM
        else -> DeclarationType.CLASS
    }
}
