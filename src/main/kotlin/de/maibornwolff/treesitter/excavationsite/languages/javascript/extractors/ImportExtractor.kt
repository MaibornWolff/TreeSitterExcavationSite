package de.maibornwolff.treesitter.excavationsite.languages.javascript.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.ImportDeclaration
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

internal object ImportExtractor {
    private const val IMPORT_STATEMENT = "import_statement"
    private const val CALL_EXPRESSION = "call_expression"
    private const val IDENTIFIER = "identifier"
    private const val STRING = "string"
    private const val NAMESPACE_IMPORT = "namespace_import"
    private const val ARGUMENTS = "arguments"
    private const val REQUIRE = "require"
    private const val PATH_SEPARATOR = "/"

    fun extract(rootNode: TSNode, sourceCode: String): List<ImportDeclaration> {
        val es6Imports = extractEs6Imports(rootNode, sourceCode)
        val commonJsImports = extractCommonJsImports(rootNode, sourceCode)
        return es6Imports + commonJsImports
    }

    private fun extractEs6Imports(rootNode: TSNode, sourceCode: String): List<ImportDeclaration> = TreeTraversal
        .findAllDescendantsOfType(rootNode, IMPORT_STATEMENT)
        .mapNotNull { importNode ->
            val pathText = extractStringText(importNode, sourceCode) ?: return@mapNotNull null
            val isWildcard = TreeTraversal.containsNodeOfType(importNode, NAMESPACE_IMPORT)
            ImportDeclaration(path = pathText.split(PATH_SEPARATOR), isWildcard = isWildcard)
        }

    private fun extractCommonJsImports(rootNode: TSNode, sourceCode: String): List<ImportDeclaration> = TreeTraversal
        .findAllDescendantsOfType(rootNode, CALL_EXPRESSION)
        .mapNotNull { callNode ->
            val callee = callNode.children().firstOrNull { it.type == IDENTIFIER }
                ?: return@mapNotNull null
            val calleeName = TreeTraversal.getNodeText(callee, sourceCode).trim()
            if (calleeName != REQUIRE) return@mapNotNull null
            val args = callNode.children().firstOrNull { it.type == ARGUMENTS }
                ?: return@mapNotNull null
            val pathText = extractStringText(args, sourceCode) ?: return@mapNotNull null
            ImportDeclaration(path = pathText.split(PATH_SEPARATOR), isWildcard = false)
        }

    private fun extractStringText(node: TSNode, sourceCode: String): String? {
        val stringNode = node.children().firstOrNull { it.type == STRING || it.type == TEMPLATE_STRING }
            ?: return null
        val raw = TreeTraversal.getNodeText(stringNode, sourceCode).trim()
        return raw.removeSurrounding("\"").removeSurrounding("'").removeSurrounding("`")
    }
}
