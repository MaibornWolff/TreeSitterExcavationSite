package de.maibornwolff.treesitter.excavationsite.languages.csharp.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.ImportDeclaration
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

internal object UsingDirectiveExtractor {
    private const val USING_DIRECTIVE = "using_directive"
    private const val NAMESPACE_DECLARATION = "namespace_declaration"
    private const val DECLARATION_LIST = "declaration_list"
    private const val QUALIFIED_NAME = "qualified_name"
    private const val IDENTIFIER = "identifier"
    private const val NAMESPACE_SEPARATOR = "."

    fun extract(rootNode: TSNode, sourceCode: String): List<ImportDeclaration> {
        val globalDirectives = extractGlobalDirectives(rootNode, sourceCode)
        val namespaceScopedDirectives = extractNamespaceScopedDirectives(rootNode, sourceCode)
        return globalDirectives + namespaceScopedDirectives
    }

    private fun extractGlobalDirectives(rootNode: TSNode, sourceCode: String): List<ImportDeclaration> = rootNode
        .children()
        .filter { it.type == USING_DIRECTIVE }
        .mapNotNull { toImportDeclaration(it, sourceCode, namespacePath = emptyList()) }
        .toList()

    private fun extractNamespaceScopedDirectives(rootNode: TSNode, sourceCode: String): List<ImportDeclaration> {
        return rootNode
            .children()
            .filter { it.type == NAMESPACE_DECLARATION }
            .flatMap { namespaceNode ->
                val namespacePath = extractNamespacePath(namespaceNode, sourceCode)
                val body = namespaceNode.children().firstOrNull { it.type == DECLARATION_LIST }
                    ?: return@flatMap emptySequence()
                body
                    .children()
                    .filter { it.type == USING_DIRECTIVE }
                    .mapNotNull { toImportDeclaration(it, sourceCode, namespacePath) }
            }.toList()
    }

    private fun toImportDeclaration(node: TSNode, sourceCode: String, namespacePath: List<String>): ImportDeclaration? {
        val nameText = extractImportPath(node, sourceCode) ?: return null
        return ImportDeclaration(
            path = nameText.split(NAMESPACE_SEPARATOR),
            isWildcard = true,
            namespacePath = namespacePath
        )
    }

    private fun extractImportPath(node: TSNode, sourceCode: String): String? = extractQualifiedOrAliasedPath(node, sourceCode)
        ?: extractSimpleIdentifierPath(node, sourceCode)

    private fun extractQualifiedOrAliasedPath(node: TSNode, sourceCode: String): String? =
        TreeTraversal.findFirstChildTextByType(node, sourceCode, QUALIFIED_NAME)

    private fun extractSimpleIdentifierPath(node: TSNode, sourceCode: String): String? = node
        .children()
        .filter { it.type == IDENTIFIER }
        .map { TreeTraversal.getNodeText(it, sourceCode) }
        .toList()
        .lastOrNull()

    private fun extractNamespacePath(node: TSNode, sourceCode: String): List<String> {
        val text = TreeTraversal.findFirstChildTextByType(node, sourceCode, QUALIFIED_NAME, IDENTIFIER)
            ?: return emptyList()
        return text.split(NAMESPACE_SEPARATOR)
    }
}
