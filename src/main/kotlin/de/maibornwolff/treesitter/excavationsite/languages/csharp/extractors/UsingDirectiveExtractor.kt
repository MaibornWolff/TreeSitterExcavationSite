package de.maibornwolff.treesitter.excavationsite.languages.csharp.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.ImportDeclaration
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

internal object UsingDirectiveExtractor {
    private const val USING_DIRECTIVE = "using_directive"
    private const val QUALIFIED_NAME = "qualified_name"
    private const val IDENTIFIER = "identifier"
    private const val NAMESPACE_SEPARATOR = "."

    fun extract(rootNode: TSNode, sourceCode: String): List<ImportDeclaration> {
        val usingNodes = TreeTraversal.findAllDescendantsOfType(rootNode, USING_DIRECTIVE)
        return usingNodes.mapNotNull { extractUsingDirective(it, sourceCode) }
    }

    private fun extractUsingDirective(node: TSNode, sourceCode: String): ImportDeclaration? {
        val nameText = extractNamespacePath(node, sourceCode) ?: return null
        return ImportDeclaration(
            path = nameText.split(NAMESPACE_SEPARATOR),
            isWildcard = true
        )
    }

    private fun extractNamespacePath(node: TSNode, sourceCode: String): String? {
        val qualifiedName = TreeTraversal.findFirstChildTextByType(node, sourceCode, QUALIFIED_NAME)
        if (qualifiedName != null) return qualifiedName

        val identifiers = node
            .children()
            .filter { it.type == IDENTIFIER }
            .map { TreeTraversal.getNodeText(it, sourceCode) }
            .toList()
        return identifiers.lastOrNull()
    }
}
