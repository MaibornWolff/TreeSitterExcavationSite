package de.maibornwolff.treesitter.excavationsite.languages.javascript.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.Declaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.DeclarationType
import de.maibornwolff.treesitter.excavationsite.shared.domain.UsedType
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

internal object DeclarationExtractor {
    private const val CLASS_DECLARATION = "class_declaration"
    private const val INTERFACE_DECLARATION = "interface_declaration"
    private const val ENUM_DECLARATION = "enum_declaration"
    private const val FUNCTION_DECLARATION = "function_declaration"
    private const val FUNCTION_SIGNATURE = "function_signature"
    private const val TYPE_ALIAS_DECLARATION = "type_alias_declaration"
    private const val LEXICAL_DECLARATION = "lexical_declaration"
    private const val VARIABLE_DECLARATION = "variable_declaration"
    private const val EXPORT_STATEMENT = "export_statement"

    private const val EXPORT_CLAUSE = "export_clause"
    private const val EXPORT_SPECIFIER = "export_specifier"
    private const val STRING = "string"
    private const val STRING_FRAGMENT = "string_fragment"
    private const val DEFAULT_EXPORT = "DEFAULT_EXPORT"

    private const val AMBIENT_DECLARATION = "ambient_declaration"
    private const val MODULE_DECLARATION = "module"
    private const val STATEMENT_BLOCK = "statement_block"

    private const val TYPE_IDENTIFIER = "type_identifier"
    private const val IDENTIFIER = "identifier"
    private const val VARIABLE_DECLARATOR = "variable_declarator"

    private val DECLARATION_NODE_TYPES = setOf(
        CLASS_DECLARATION,
        INTERFACE_DECLARATION,
        ENUM_DECLARATION,
        FUNCTION_DECLARATION,
        FUNCTION_SIGNATURE,
        TYPE_ALIAS_DECLARATION,
        LEXICAL_DECLARATION,
        VARIABLE_DECLARATION
    )

    fun extract(rootNode: TSNode, sourceCode: String): List<Declaration> = rootNode
        .children()
        .flatMap { child ->
            when (child.type) {
                EXPORT_STATEMENT -> extractFromExportStatement(child, sourceCode)
                AMBIENT_DECLARATION -> extractFromAmbientDeclaration(child, sourceCode)
                in DECLARATION_NODE_TYPES -> extractFromNode(child, sourceCode)
                else -> emptyList()
            }
        }.filter { it.name.isNotBlank() }
        .toList()

    private fun extractFromAmbientDeclaration(node: TSNode, sourceCode: String): List<Declaration> {
        val moduleNode = node.children().firstOrNull { it.type == MODULE_DECLARATION } ?: return emptyList()
        val moduleName = moduleNode
            .children()
            .firstOrNull { it.type == STRING }
            ?.children()
            ?.firstOrNull { it.type == STRING_FRAGMENT }
            ?.let { TreeTraversal.getNodeText(it, sourceCode).trim() }
            ?: return emptyList()
        if (moduleName.contains("*")) return emptyList()
        val parentPath = moduleName.split("/")
        val body = moduleNode.children().firstOrNull { it.type == STATEMENT_BLOCK } ?: return emptyList()
        return body
            .children()
            .flatMap { child ->
                when (child.type) {
                    EXPORT_STATEMENT -> extractFromExportStatement(child, sourceCode, parentPath)
                    in DECLARATION_NODE_TYPES -> extractFromNode(child, sourceCode, parentPath)
                    else -> emptyList()
                }
            }.filter { it.name.isNotBlank() }
            .toList()
    }

    private fun extractFromExportStatement(node: TSNode, sourceCode: String, parentPath: List<String> = emptyList()): List<Declaration> {
        val hasExportClause = node.children().any { it.type == EXPORT_CLAUSE }
        val hasSource = node.children().any { it.type == STRING }
        return if (hasExportClause && hasSource) {
            extractReexportDeclarations(node, sourceCode)
        } else {
            node
                .children()
                .filter { it.type in DECLARATION_NODE_TYPES }
                .flatMap { extractFromNode(it, sourceCode, parentPath) }
                .toList()
        }
    }

    private fun extractReexportDeclarations(node: TSNode, sourceCode: String): List<Declaration> {
        val exportClause = node.children().firstOrNull { it.type == EXPORT_CLAUSE } ?: return emptyList()
        return exportClause
            .children()
            .filter { it.type == EXPORT_SPECIFIER }
            .mapNotNull { specifier ->
                val identifiers = specifier
                    .children()
                    .filter { it.type == IDENTIFIER }
                    .map { TreeTraversal.getNodeText(it, sourceCode).trim() }
                    .toList()
                when (identifiers.size) {
                    1 -> {
                        val name = identifiers[0]
                        Declaration(name = name, type = DeclarationType.REEXPORT, usedTypes = setOf(UsedType(name)))
                    }
                    2 -> {
                        val originalName = identifiers[0]
                        val alias = identifiers[1]
                        val usedTypeName = if (originalName == "default") DEFAULT_EXPORT else originalName
                        Declaration(name = alias, type = DeclarationType.REEXPORT, usedTypes = setOf(UsedType(usedTypeName)))
                    }
                    else -> null
                }
            }.toList()
    }

    private fun extractFromNode(node: TSNode, sourceCode: String, parentPath: List<String> = emptyList()): List<Declaration> =
        when (node.type) {
            LEXICAL_DECLARATION, VARIABLE_DECLARATION -> extractVariableDeclarations(node, sourceCode, parentPath)
            else -> {
                val name = extractName(node, sourceCode)
                val type = declarationType(node.type)
                val usedTypes = UsedTypeExtractor.extract(node, sourceCode)
                listOf(Declaration(name = name, type = type, usedTypes = usedTypes, parentPath = parentPath))
            }
        }

    private fun extractVariableDeclarations(node: TSNode, sourceCode: String, parentPath: List<String> = emptyList()): List<Declaration> =
        node
            .children()
            .filter { it.type == VARIABLE_DECLARATOR }
            .mapNotNull { declarator ->
                val name = TreeTraversal.findFirstChildTextByType(declarator, sourceCode, IDENTIFIER)?.trim()
                if (name.isNullOrBlank()) {
                    null
                } else {
                    Declaration(
                        name = name,
                        type = DeclarationType.VARIABLE,
                        usedTypes = UsedTypeExtractor.extract(node, sourceCode),
                        parentPath = parentPath
                    )
                }
            }.toList()

    private fun extractName(node: TSNode, sourceCode: String): String {
        val nameTypes = when (node.type) {
            CLASS_DECLARATION, INTERFACE_DECLARATION, TYPE_ALIAS_DECLARATION -> arrayOf(TYPE_IDENTIFIER, IDENTIFIER)
            else -> arrayOf(IDENTIFIER)
        }
        return TreeTraversal.findFirstChildTextByType(node, sourceCode, *nameTypes)?.trim() ?: ""
    }

    private fun declarationType(nodeType: String): DeclarationType = when (nodeType) {
        CLASS_DECLARATION, TYPE_ALIAS_DECLARATION -> DeclarationType.CLASS
        INTERFACE_DECLARATION -> DeclarationType.INTERFACE
        ENUM_DECLARATION -> DeclarationType.ENUM
        FUNCTION_DECLARATION, FUNCTION_SIGNATURE -> DeclarationType.FUNCTION
        LEXICAL_DECLARATION, VARIABLE_DECLARATION -> DeclarationType.VARIABLE
        else -> DeclarationType.UNKNOWN
    }
}
