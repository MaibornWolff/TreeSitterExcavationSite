package de.maibornwolff.treesitter.excavationsite.languages.csharp.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.Declaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.DeclarationType
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

internal object DeclarationExtractor {
    private const val FILE_SCOPED_NAMESPACE = "file_scoped_namespace_declaration"
    private const val NAMESPACE_DECLARATION = "namespace_declaration"
    private const val DECLARATION_LIST = "declaration_list"
    private const val QUALIFIED_NAME = "qualified_name"
    private const val IDENTIFIER = "identifier"
    private const val NAMESPACE_SEPARATOR = "."

    private val DECLARATION_TYPES = setOf(
        "class_declaration",
        "struct_declaration",
        "record_declaration",
        "interface_declaration",
        "enum_declaration",
        "delegate_declaration"
    )

    fun extract(rootNode: TSNode, sourceCode: String): List<Declaration> {
        val fileScopedNamespace = extractFileScopedNamespace(rootNode, sourceCode)
        val topLevelDeclarations = extractTopLevelDeclarations(rootNode, sourceCode, fileScopedNamespace)
        val namespaceDeclarations = extractNamespaceDeclarations(rootNode, sourceCode)
        return topLevelDeclarations + namespaceDeclarations
    }

    private fun extractTopLevelDeclarations(rootNode: TSNode, sourceCode: String, namespacePath: List<String>): List<Declaration> = rootNode
        .children()
        .filter { it.type in DECLARATION_TYPES }
        .mapNotNull { toDeclaration(it, sourceCode, namespacePath) }
        .toList()

    private fun extractNamespaceDeclarations(rootNode: TSNode, sourceCode: String): List<Declaration> {
        return rootNode
            .children()
            .filter { it.type == NAMESPACE_DECLARATION }
            .flatMap { namespaceNode ->
                val namespacePath = extractNamespacePath(namespaceNode, sourceCode)
                val body = namespaceNode.children().firstOrNull { it.type == DECLARATION_LIST }
                    ?: return@flatMap emptySequence()
                body
                    .children()
                    .filter { it.type in DECLARATION_TYPES }
                    .mapNotNull { toDeclaration(it, sourceCode, namespacePath) }
            }.toList()
    }

    private fun toDeclaration(node: TSNode, sourceCode: String, namespacePath: List<String>): Declaration? {
        val name = TreeTraversal.findChildByType(node, IDENTIFIER, sourceCode) ?: return null
        return Declaration(
            name = name,
            type = mapDeclarationTypeToClosestEquivalent(node.type),
            usedTypes = UsedTypeExtractor.extract(node, sourceCode),
            parentPath = namespacePath
        )
    }

    private fun mapDeclarationTypeToClosestEquivalent(nodeType: String): DeclarationType = when (nodeType) {
        "class_declaration", "struct_declaration" -> DeclarationType.CLASS
        "record_declaration" -> DeclarationType.RECORD
        "interface_declaration", "delegate_declaration" -> DeclarationType.INTERFACE
        "enum_declaration" -> DeclarationType.ENUM
        else -> DeclarationType.UNKNOWN
    }

    private fun extractFileScopedNamespace(rootNode: TSNode, sourceCode: String): List<String> {
        val namespaceNode = rootNode.children().firstOrNull { it.type == FILE_SCOPED_NAMESPACE }
            ?: return emptyList()
        return extractNamespacePath(namespaceNode, sourceCode)
    }

    private fun extractNamespacePath(node: TSNode, sourceCode: String): List<String> {
        val text = TreeTraversal.findFirstChildTextByType(node, sourceCode, QUALIFIED_NAME, IDENTIFIER)
            ?: return emptyList()
        return text.split(NAMESPACE_SEPARATOR)
    }
}
