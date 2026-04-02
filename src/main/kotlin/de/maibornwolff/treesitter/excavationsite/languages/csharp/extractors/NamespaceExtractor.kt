package de.maibornwolff.treesitter.excavationsite.languages.csharp.extractors

import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

internal object NamespaceExtractor {
    private const val FILE_SCOPED_NAMESPACE = "file_scoped_namespace_declaration"
    private const val QUALIFIED_NAME = "qualified_name"
    private const val IDENTIFIER = "identifier"
    private const val NAMESPACE_SEPARATOR = "."

    fun extract(rootNode: TSNode, sourceCode: String): List<String> {
        val namespaceNode = rootNode.children().firstOrNull { it.type == FILE_SCOPED_NAMESPACE }
            ?: return emptyList()
        val namespaceText = TreeTraversal.findFirstChildTextByType(namespaceNode, sourceCode, QUALIFIED_NAME, IDENTIFIER)
            ?: return emptyList()
        return namespaceText.split(NAMESPACE_SEPARATOR)
    }
}
