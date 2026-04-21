package de.maibornwolff.treesitter.excavationsite.languages.cpp.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.ImportDeclaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.ImportKind
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

internal object ImportExtractor {
    private const val PREPROC_INCLUDE = "preproc_include"
    private const val USING_DECLARATION = "using_declaration"
    private const val NAMESPACE_DEFINITION = "namespace_definition"
    private const val SYSTEM_LIB_STRING = "system_lib_string"
    private const val STRING_LITERAL = "string_literal"
    private const val IDENTIFIER = "identifier"
    private const val QUALIFIED_IDENTIFIER = "qualified_identifier"
    private const val NAMESPACE_IDENTIFIER = "namespace_identifier"
    private const val NESTED_NAMESPACE_SPECIFIER = "nested_namespace_specifier"
    private const val NAMESPACE_KEYWORD = "namespace"
    private const val NAMESPACE_SEPARATOR = "::"
    private const val PATH_SEPARATOR = "/"

    fun extract(rootNode: TSNode, sourceCode: String): List<ImportDeclaration> = TreeTraversal
        .findAllDescendantsOfType(rootNode, PREPROC_INCLUDE, USING_DECLARATION)
        .mapNotNull { node ->
            when (node.type) {
                PREPROC_INCLUDE -> toIncludeImport(node, sourceCode)
                USING_DECLARATION -> toUsingImport(node, sourceCode)
                else -> null
            }
        }

    private fun toIncludeImport(node: TSNode, sourceCode: String): ImportDeclaration? {
        val rawPath = TreeTraversal.findFirstChildTextByType(node, sourceCode, SYSTEM_LIB_STRING, STRING_LITERAL)
            ?: return null
        val segments = stripPathDelimiters(rawPath).split(PATH_SEPARATOR)
        return ImportDeclaration(path = segments, isWildcard = false, kind = ImportKind.INCLUDE)
    }

    private fun toUsingImport(node: TSNode, sourceCode: String): ImportDeclaration? {
        val hasNamespaceKeyword = node.children().any { it.type == NAMESPACE_KEYWORD }
        val qualifiedName = TreeTraversal.findFirstChildTextByType(node, sourceCode, QUALIFIED_IDENTIFIER)
        val namespacePath = aggregateNamespacePath(node, sourceCode)
        if (hasNamespaceKeyword) {
            val nameText = qualifiedName
                ?: TreeTraversal.findFirstChildTextByType(node, sourceCode, IDENTIFIER)
                ?: return null
            return ImportDeclaration(
                path = nameText.split(NAMESPACE_SEPARATOR),
                isWildcard = true,
                namespacePath = namespacePath
            )
        }
        if (qualifiedName != null) {
            return ImportDeclaration(
                path = qualifiedName.split(NAMESPACE_SEPARATOR),
                isWildcard = false,
                namespacePath = namespacePath
            )
        }
        return null
    }

    private fun aggregateNamespacePath(node: TSNode, sourceCode: String): List<String> {
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

    private fun extractNamespaceSegments(namespaceDef: TSNode, sourceCode: String): List<String> {
        val name = TreeTraversal.findFirstChildTextByType(
            namespaceDef,
            sourceCode,
            NAMESPACE_IDENTIFIER,
            NESTED_NAMESPACE_SPECIFIER
        ) ?: return emptyList()
        return name.split(NAMESPACE_SEPARATOR)
    }

    private fun stripPathDelimiters(raw: String): String = raw.trim('<', '>', '"')
}
