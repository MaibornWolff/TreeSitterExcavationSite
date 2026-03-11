package de.maibornwolff.treesitter.excavationsite.languages.java

import de.maibornwolff.treesitter.excavationsite.shared.domain.DependencyAnalyzer
import de.maibornwolff.treesitter.excavationsite.shared.domain.DependencyResult
import de.maibornwolff.treesitter.excavationsite.shared.domain.ImportDeclaration
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.executeQuery
import org.treesitter.TSLanguage
import org.treesitter.TSNode

object JavaDependencyAnalyzer : DependencyAnalyzer {
    private const val PACKAGE_QUERY = "(package_declaration) @package"
    private const val IMPORT_QUERY = "(import_declaration) @import"

    override fun analyze(rootNode: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): DependencyResult {
        val packagePath = extractPackagePath(rootNode, sourceCode, treeSitterLanguage)
        val imports = extractImports(rootNode, sourceCode, treeSitterLanguage)
        return DependencyResult(
            packagePath = packagePath,
            imports = imports,
            declarations = emptyList()
        )
    }

    private fun extractPackagePath(rootNode: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): List<String> {
        val matches = rootNode.executeQuery(PACKAGE_QUERY, treeSitterLanguage)
        if (matches.isEmpty()) return emptyList()

        val packageNode = matches.first().captures[0].node
        val scopedIdentifier = packageNode.children().firstOrNull { it.type == "scoped_identifier" } ?: return emptyList()
        val packageText = TreeTraversal.getNodeText(scopedIdentifier, sourceCode)
        return packageText.split(".")
    }

    private fun extractImports(rootNode: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): List<ImportDeclaration> {
        val matches = rootNode.executeQuery(IMPORT_QUERY, treeSitterLanguage)
        return matches.map { match ->
            val importNode = match.captures[0].node
            val children = importNode.children().toList()
            val isWildcard = children.any { it.type == "asterisk" }
            val identifierNode = children.firstOrNull {
                it.type == "scoped_identifier" || it.type == "identifier"
            }
            val path = if (identifierNode != null) {
                TreeTraversal.getNodeText(identifierNode, sourceCode).split(".")
            } else {
                emptyList()
            }
            ImportDeclaration(path = path, isWildcard = isWildcard)
        }
    }
}
