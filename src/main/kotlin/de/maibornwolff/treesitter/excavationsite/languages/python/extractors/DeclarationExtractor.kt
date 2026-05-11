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
        val aliases = AliasContext(
            fromImports = collectFromImportAliases(rootNode, sourceCode),
            standardImports = collectStandardImportAliases(rootNode, sourceCode)
        )
        val topLevel = rootNode.children().toList()

        // Categorical order (basic → decorated → assignments) is parity-critical: DC's adapter does
        // associateBy { pathWithName }.values, keeping the *last* duplicate per name. Overload sets
        // (e.g. flask.helpers.stream_with_context) need this ordering. Do not flatten into one pass.
        val basic = topLevel.mapNotNull { child ->
            when (child.type) {
                CLASS_DEFINITION -> classOrFunctionDeclaration(child, child, sourceCode, DeclarationType.CLASS, aliases)
                FUNCTION_DEFINITION -> classOrFunctionDeclaration(child, child, sourceCode, DeclarationType.FUNCTION, aliases)
                else -> null
            }
        }
        val decorated = topLevel.mapNotNull { child ->
            if (child.type == DECORATED_DEFINITION) unwrappedDecoratedDeclaration(child, sourceCode, aliases) else null
        }
        val assignments = topLevel.mapNotNull { child ->
            if (child.type == EXPRESSION_STATEMENT) simpleVariableDeclaration(child, sourceCode) else null
        }
        return basic + decorated + assignments
    }

    private fun classOrFunctionDeclaration(
        nameNodeContainer: TSNode,
        usedTypesScope: TSNode,
        sourceCode: String,
        type: DeclarationType,
        aliases: AliasContext
    ): Declaration? {
        val nameNode = nameNodeContainer.getChildByFieldName(FIELD_NAME).takeIf { !it.isNull } ?: return null
        val name = TreeTraversal.getNodeText(nameNode, sourceCode).trim()
        if (name.isEmpty()) return null
        val usedTypes = UsedTypeExtractor.extract(usedTypesScope, sourceCode, aliases)
        return Declaration(name = name, type = type, usedTypes = usedTypes)
    }

    private fun unwrappedDecoratedDeclaration(decorated: TSNode, sourceCode: String, aliases: AliasContext): Declaration? {
        val inner = decorated.getChildByFieldName(FIELD_DEFINITION).takeIf { !it.isNull } ?: return null
        val type = when (inner.type) {
            CLASS_DEFINITION -> DeclarationType.CLASS
            FUNCTION_DEFINITION -> DeclarationType.FUNCTION
            else -> return null
        }
        return classOrFunctionDeclaration(inner, decorated, sourceCode, type, aliases)
    }

    private fun simpleVariableDeclaration(expressionStatement: TSNode, sourceCode: String): Declaration? {
        val assignment = expressionStatement.children().firstOrNull { it.type == ASSIGNMENT } ?: return null
        val left = assignment.getChildByFieldName(FIELD_LEFT).takeIf { !it.isNull } ?: return null
        if (left.type != IDENTIFIER) return null
        val name = TreeTraversal.getNodeText(left, sourceCode).trim()
        if (name.isEmpty()) return null
        return Declaration(name = name, type = DeclarationType.VARIABLE, usedTypes = emptySet())
    }

    private fun collectFromImportAliases(rootNode: TSNode, sourceCode: String): Map<String, String> =
        collectAliases(rootNode, sourceCode, IMPORT_FROM_STATEMENT) { it.split(SEPARATOR).last() }

    private fun collectStandardImportAliases(rootNode: TSNode, sourceCode: String): Map<String, String> =
        collectAliases(rootNode, sourceCode, IMPORT_STATEMENT) { it }

    // `aliased_import` only appears as a direct child of `import_statement` / `import_from_statement`
    // (its grammar shape is `dotted_name "as" identifier`, so it can't nest). Filtering direct
    // children by type is therefore equivalent to walking by the `name:` field.
    private fun collectAliases(
        rootNode: TSNode,
        sourceCode: String,
        statementType: String,
        valueOf: (String) -> String
    ): Map<String, String> {
        val result = mutableMapOf<String, String>()
        TreeTraversal
            .findAllDescendantsOfType(rootNode, statementType)
            .flatMap { stmt -> stmt.children().filter { it.type == ALIASED_IMPORT }.toList() }
            .forEach { aliased ->
                val alias = aliased.getChildByFieldName(FIELD_ALIAS).takeIf { !it.isNull } ?: return@forEach
                val name = aliased.children().firstOrNull { it.type == DOTTED_NAME } ?: return@forEach
                result[TreeTraversal.getNodeText(alias, sourceCode)] = valueOf(TreeTraversal.getNodeText(name, sourceCode))
            }
        return result
    }
}
