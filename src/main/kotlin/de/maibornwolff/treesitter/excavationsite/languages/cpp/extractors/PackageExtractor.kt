package de.maibornwolff.treesitter.excavationsite.languages.cpp.extractors

import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import org.treesitter.TSNode

internal object PackageExtractor {
    private const val NAMESPACE_DEFINITION = "namespace_definition"
    private const val NAMESPACE_IDENTIFIER = "namespace_identifier"
    private const val NESTED_NAMESPACE_SPECIFIER = "nested_namespace_specifier"
    private const val NAMESPACE_SEPARATOR = "::"

    fun extract(rootNode: TSNode, sourceCode: String): List<String> {
        val namespaceNode = TreeTraversal
            .findAllDescendantsOfType(rootNode, NAMESPACE_DEFINITION)
            .firstOrNull() ?: return emptyList()
        val name = TreeTraversal.findFirstChildTextByType(
            namespaceNode,
            sourceCode,
            NAMESPACE_IDENTIFIER,
            NESTED_NAMESPACE_SPECIFIER
        ) ?: return emptyList()
        return name.split(NAMESPACE_SEPARATOR)
    }
}
