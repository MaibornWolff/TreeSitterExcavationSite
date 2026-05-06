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
    private const val FIELD_DEFINITION = "definition"
    private const val FIELD_NAME = "name"
    private const val FIELD_LEFT = "left"

    fun extract(rootNode: TSNode, sourceCode: String): List<Declaration> = rootNode
        .children()
        .mapNotNull { child ->
            when (child.type) {
                CLASS_DEFINITION -> classOrFunctionDeclaration(child, sourceCode, DeclarationType.CLASS)
                FUNCTION_DEFINITION -> classOrFunctionDeclaration(child, sourceCode, DeclarationType.FUNCTION)
                DECORATED_DEFINITION -> unwrappedDecoratedDeclaration(child, sourceCode)
                EXPRESSION_STATEMENT -> simpleVariableDeclaration(child, sourceCode)
                else -> null
            }
        }.toList()

    private fun classOrFunctionDeclaration(node: TSNode, sourceCode: String, type: DeclarationType): Declaration? {
        val nameNode = node.getChildByFieldName(FIELD_NAME).takeIf { !it.isNull } ?: return null
        val name = TreeTraversal.getNodeText(nameNode, sourceCode).trim()
        if (name.isEmpty()) return null
        return Declaration(name = name, type = type, usedTypes = emptySet())
    }

    private fun unwrappedDecoratedDeclaration(decorated: TSNode, sourceCode: String): Declaration? {
        val inner = decorated.getChildByFieldName(FIELD_DEFINITION).takeIf { !it.isNull } ?: return null
        val type = when (inner.type) {
            CLASS_DEFINITION -> DeclarationType.CLASS
            FUNCTION_DEFINITION -> DeclarationType.FUNCTION
            else -> return null
        }
        return classOrFunctionDeclaration(inner, sourceCode, type)
    }

    private fun simpleVariableDeclaration(expressionStatement: TSNode, sourceCode: String): Declaration? {
        val assignment = expressionStatement.children().firstOrNull { it.type == ASSIGNMENT } ?: return null
        val left = assignment.getChildByFieldName(FIELD_LEFT).takeIf { !it.isNull } ?: return null
        if (left.type != IDENTIFIER) return null
        val name = TreeTraversal.getNodeText(left, sourceCode).trim()
        if (name.isEmpty()) return null
        return Declaration(name = name, type = DeclarationType.VARIABLE, usedTypes = emptySet())
    }
}
