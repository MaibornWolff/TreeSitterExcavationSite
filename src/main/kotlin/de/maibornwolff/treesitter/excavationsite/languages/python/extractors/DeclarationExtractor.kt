package de.maibornwolff.treesitter.excavationsite.languages.python.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.Declaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.DeclarationType
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

internal object DeclarationExtractor {
    private const val CLASS_DEFINITION = "class_definition"
    private const val FUNCTION_DEFINITION = "function_definition"
    private const val DECORATED_DEFINITION = "decorated_definition"
    private const val EXPRESSION_STATEMENT = "expression_statement"
    private const val ASSIGNMENT = "assignment"
    private const val IDENTIFIER = "identifier"
    private const val IMPORT_STATEMENT = "import_statement"
    private const val IMPORT_FROM_STATEMENT = "import_from_statement"
    private const val ALIASED_IMPORT = "aliased_import"
    private const val DOTTED_NAME = "dotted_name"
    private const val FIELD_DEFINITION = "definition"
    private const val FIELD_NAME = "name"
    private const val FIELD_LEFT = "left"
    private const val FIELD_ALIAS = "alias"
    private const val SEPARATOR = "."

    fun extract(rootNode: TSNode, sourceCode: String): List<Declaration> {
        val fromImportAliases = collectFromImportAliases(rootNode, sourceCode)
        val standardImportAliases = collectStandardImportAliases(rootNode, sourceCode)

        return rootNode
            .children()
            .mapNotNull { child ->
                when (child.type) {
                    CLASS_DEFINITION ->
                        classOrFunctionDeclaration(
                            child,
                            child,
                            sourceCode,
                            DeclarationType.CLASS,
                            fromImportAliases,
                            standardImportAliases
                        )
                    FUNCTION_DEFINITION ->
                        classOrFunctionDeclaration(
                            child,
                            child,
                            sourceCode,
                            DeclarationType.FUNCTION,
                            fromImportAliases,
                            standardImportAliases
                        )
                    DECORATED_DEFINITION ->
                        unwrappedDecoratedDeclaration(child, sourceCode, fromImportAliases, standardImportAliases)
                    EXPRESSION_STATEMENT -> simpleVariableDeclaration(child, sourceCode)
                    else -> null
                }
            }.toList()
    }

    private fun classOrFunctionDeclaration(
        nameNodeContainer: TSNode,
        usedTypesScope: TSNode,
        sourceCode: String,
        type: DeclarationType,
        fromImportAliases: Map<String, String>,
        standardImportAliases: Map<String, String>
    ): Declaration? {
        val nameNode = nameNodeContainer.getChildByFieldName(FIELD_NAME).takeIf { !it.isNull } ?: return null
        val name = TreeTraversal.getNodeText(nameNode, sourceCode).trim()
        if (name.isEmpty()) return null
        val usedTypes = UsedTypeExtractor.extract(usedTypesScope, sourceCode, fromImportAliases, standardImportAliases)
        return Declaration(name = name, type = type, usedTypes = usedTypes)
    }

    private fun unwrappedDecoratedDeclaration(
        decorated: TSNode,
        sourceCode: String,
        fromImportAliases: Map<String, String>,
        standardImportAliases: Map<String, String>
    ): Declaration? {
        val inner = decorated.getChildByFieldName(FIELD_DEFINITION).takeIf { !it.isNull } ?: return null
        val type = when (inner.type) {
            CLASS_DEFINITION -> DeclarationType.CLASS
            FUNCTION_DEFINITION -> DeclarationType.FUNCTION
            else -> return null
        }
        return classOrFunctionDeclaration(inner, decorated, sourceCode, type, fromImportAliases, standardImportAliases)
    }

    private fun simpleVariableDeclaration(expressionStatement: TSNode, sourceCode: String): Declaration? {
        val assignment = expressionStatement.children().firstOrNull { it.type == ASSIGNMENT } ?: return null
        val left = assignment.getChildByFieldName(FIELD_LEFT).takeIf { !it.isNull } ?: return null
        if (left.type != IDENTIFIER) return null
        val name = TreeTraversal.getNodeText(left, sourceCode).trim()
        if (name.isEmpty()) return null
        return Declaration(name = name, type = DeclarationType.VARIABLE, usedTypes = emptySet())
    }

    private fun collectFromImportAliases(rootNode: TSNode, sourceCode: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        rootNode
            .children()
            .filter { it.type == IMPORT_FROM_STATEMENT }
            .flatMap { collectAliasedNameFieldChildren(it) }
            .forEach { aliased ->
                val alias = aliased.getChildByFieldName(FIELD_ALIAS).takeIf { !it.isNull } ?: return@forEach
                val name = aliased.children().firstOrNull { it.type == DOTTED_NAME } ?: return@forEach
                val originalLast = TreeTraversal.getNodeText(name, sourceCode).split(SEPARATOR).last()
                result[TreeTraversal.getNodeText(alias, sourceCode)] = originalLast
            }
        return result
    }

    private fun collectStandardImportAliases(rootNode: TSNode, sourceCode: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        rootNode
            .children()
            .filter { it.type == IMPORT_STATEMENT }
            .flatMap { it.children().filter { child -> child.type == ALIASED_IMPORT } }
            .forEach { aliased ->
                val alias = aliased.getChildByFieldName(FIELD_ALIAS).takeIf { !it.isNull } ?: return@forEach
                val name = aliased.children().firstOrNull { it.type == DOTTED_NAME } ?: return@forEach
                result[TreeTraversal.getNodeText(alias, sourceCode)] = TreeTraversal.getNodeText(name, sourceCode)
            }
        return result
    }

    private fun collectAliasedNameFieldChildren(importFromStatement: TSNode): List<TSNode> {
        val result = mutableListOf<TSNode>()
        for (i in 0 until importFromStatement.childCount) {
            if (importFromStatement.getFieldNameForChild(i) == FIELD_NAME) {
                val child = importFromStatement.getChild(i)
                if (child.type == ALIASED_IMPORT) result.add(child)
            }
        }
        return result
    }
}
