package de.maibornwolff.treesitter.excavationsite.languages.javascript.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.ImportDeclaration
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

internal object ImportExtractor {
    private const val IMPORT_STATEMENT = "import_statement"
    private const val EXPORT_STATEMENT = "export_statement"
    private const val CALL_EXPRESSION = "call_expression"
    private const val IDENTIFIER = "identifier"
    private const val STRING = "string"
    private const val NAMESPACE_IMPORT = "namespace_import"
    private const val IMPORT_CLAUSE = "import_clause"
    private const val NAMED_IMPORTS = "named_imports"
    private const val IMPORT_SPECIFIER = "import_specifier"
    private const val EXPORT_CLAUSE = "export_clause"
    private const val EXPORT_SPECIFIER = "export_specifier"
    private const val ARGUMENTS = "arguments"
    private const val VARIABLE_DECLARATOR = "variable_declarator"
    private const val OBJECT_PATTERN = "object_pattern"
    private const val SHORTHAND_PROPERTY_IDENTIFIER_PATTERN = "shorthand_property_identifier_pattern"
    private const val PAIR_PATTERN = "pair_pattern"
    private const val PROPERTY_IDENTIFIER = "property_identifier"
    private const val REQUIRE = "require"
    private const val IMPORT_KEYWORD = "import"
    private const val PATH_SEPARATOR = "/"
    private const val DEFAULT_EXPORT = "DEFAULT_EXPORT"

    fun extract(rootNode: TSNode, sourceCode: String): List<ImportDeclaration> {
        val es6Imports = extractEs6Imports(rootNode, sourceCode)
        val commonJsImports = extractCommonJsImports(rootNode, sourceCode)
        val namedReexports = extractNamedReexports(rootNode, sourceCode)
        val wildcardReexports = extractWildcardReexports(rootNode, sourceCode)
        val dynamicImports = extractDynamicImports(rootNode, sourceCode)
        return es6Imports + commonJsImports + namedReexports + wildcardReexports + dynamicImports
    }

    private fun extractEs6Imports(rootNode: TSNode, sourceCode: String): List<ImportDeclaration> = TreeTraversal
        .findAllDescendantsOfType(rootNode, IMPORT_STATEMENT)
        .flatMap { importNode ->
            val pathText = extractStringText(importNode, sourceCode) ?: return@flatMap emptyList()
            val basePath = pathText.split(PATH_SEPARATOR)
            val importClause = importNode.children().firstOrNull { it.type == IMPORT_CLAUSE }
                ?: return@flatMap listOf(ImportDeclaration(path = basePath, isWildcard = false))
            when {
                TreeTraversal.containsNodeOfType(importClause, NAMESPACE_IMPORT) ->
                    listOf(ImportDeclaration(path = basePath, isWildcard = true))
                else -> {
                    val named = extractNamedSpecifiers(importClause, basePath, sourceCode)
                    val hasDefaultBinding = importClause.children().any { it.type == IDENTIFIER }
                    if (hasDefaultBinding) {
                        named + ImportDeclaration(path = basePath + DEFAULT_EXPORT, isWildcard = false)
                    } else {
                        named
                    }
                }
            }
        }

    private fun extractNamedSpecifiers(importClause: TSNode, basePath: List<String>, sourceCode: String): List<ImportDeclaration> {
        val namedImports = importClause.children().firstOrNull { it.type == NAMED_IMPORTS }
            ?: return emptyList()
        return namedImports
            .children()
            .filter { it.type == IMPORT_SPECIFIER }
            .mapNotNull { specifier ->
                val name = specifier
                    .children()
                    .firstOrNull { it.type == IDENTIFIER }
                    ?.let { TreeTraversal.getNodeText(it, sourceCode).trim() }
                    ?: return@mapNotNull null
                ImportDeclaration(path = basePath + name, isWildcard = false)
            }.toList()
    }

    private fun extractCommonJsImports(rootNode: TSNode, sourceCode: String): List<ImportDeclaration> = TreeTraversal
        .findAllDescendantsOfType(rootNode, CALL_EXPRESSION)
        .flatMap { callNode ->
            val callee = callNode.children().firstOrNull { it.type == IDENTIFIER }
                ?: return@flatMap emptyList()
            val calleeName = TreeTraversal.getNodeText(callee, sourceCode).trim()
            if (calleeName != REQUIRE) return@flatMap emptyList()
            val args = callNode.children().firstOrNull { it.type == ARGUMENTS }
                ?: return@flatMap emptyList()
            val pathText = extractStringText(args, sourceCode) ?: return@flatMap emptyList()
            val basePath = pathText.split(PATH_SEPARATOR)
            val declarator = callNode.parent
            if (declarator == null || declarator.isNull || declarator.type != VARIABLE_DECLARATOR) {
                return@flatMap listOf(ImportDeclaration(path = basePath + DEFAULT_EXPORT, isWildcard = false))
            }
            val nameChild = declarator.children().firstOrNull { it.type == OBJECT_PATTERN || it.type == IDENTIFIER }
            when (nameChild?.type) {
                OBJECT_PATTERN ->
                    nameChild
                        .children()
                        .flatMap { prop ->
                            when (prop.type) {
                                SHORTHAND_PROPERTY_IDENTIFIER_PATTERN -> {
                                    val name = TreeTraversal.getNodeText(prop, sourceCode).trim()
                                    if (name.isBlank()) emptyList() else listOf(ImportDeclaration(path = basePath + name, isWildcard = false))
                                }
                                PAIR_PATTERN -> {
                                    val key = prop.children().firstOrNull { it.type == IDENTIFIER || it.type == PROPERTY_IDENTIFIER }
                                        ?.let { TreeTraversal.getNodeText(it, sourceCode).trim() }
                                    if (key.isNullOrBlank()) emptyList() else listOf(ImportDeclaration(path = basePath + key, isWildcard = false))
                                }
                                else -> emptyList()
                            }
                        }.toList()
                else -> listOf(ImportDeclaration(path = basePath + DEFAULT_EXPORT, isWildcard = false))
            }
        }

    private fun extractNamedReexports(rootNode: TSNode, sourceCode: String): List<ImportDeclaration> = TreeTraversal
        .findAllDescendantsOfType(rootNode, EXPORT_STATEMENT)
        .filter { exportNode -> TreeTraversal.containsNodeOfType(exportNode, EXPORT_CLAUSE) }
        .flatMap { exportNode ->
            val pathText = extractStringText(exportNode, sourceCode) ?: return@flatMap emptyList()
            val basePath = pathText.split(PATH_SEPARATOR)
            val exportClause = exportNode.children().firstOrNull { it.type == EXPORT_CLAUSE }
                ?: return@flatMap emptyList()
            exportClause
                .children()
                .filter { it.type == EXPORT_SPECIFIER }
                .mapNotNull { specifier ->
                    val rawName = specifier
                        .children()
                        .firstOrNull { it.type == IDENTIFIER }
                        ?.let { TreeTraversal.getNodeText(it, sourceCode).trim() }
                        ?: return@mapNotNull null
                    val name = if (rawName == "default") DEFAULT_EXPORT else rawName
                    ImportDeclaration(path = basePath + name, isWildcard = false)
                }.toList()
        }

    private fun extractWildcardReexports(rootNode: TSNode, sourceCode: String): List<ImportDeclaration> = TreeTraversal
        .findAllDescendantsOfType(rootNode, EXPORT_STATEMENT)
        .filter { exportNode ->
            exportNode.children().any { it.type == STRING } &&
                !TreeTraversal.containsNodeOfType(exportNode, EXPORT_CLAUSE)
        }.mapNotNull { exportNode ->
            val pathText = extractStringText(exportNode, sourceCode) ?: return@mapNotNull null
            ImportDeclaration(path = pathText.split(PATH_SEPARATOR), isWildcard = true)
        }

    private fun extractDynamicImports(rootNode: TSNode, sourceCode: String): List<ImportDeclaration> = TreeTraversal
        .findAllDescendantsOfType(rootNode, CALL_EXPRESSION)
        .mapNotNull { callNode ->
            val callee = callNode.children().firstOrNull { it.type == IMPORT_KEYWORD }
                ?: return@mapNotNull null
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
