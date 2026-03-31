package de.maibornwolff.treesitter.excavationsite.languages.javascript.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.Declaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.DeclarationType
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
        LEXICAL_DECLARATION
    )

    fun extract(rootNode: TSNode, sourceCode: String): List<Declaration> = TreeTraversal
        .findAllDescendantsOfType(rootNode, *DECLARATION_NODE_TYPES.toTypedArray())
        .flatMap { node -> extractFromNode(node, sourceCode) }
        .filter { it.name.isNotBlank() }

    private fun extractFromNode(node: TSNode, sourceCode: String): List<Declaration> = when (node.type) {
        LEXICAL_DECLARATION -> extractLexicalDeclarations(node, sourceCode)
        else -> {
            val name = extractName(node, sourceCode)
            val type = declarationType(node.type)
            val usedTypes = UsedTypeExtractor.extract(node, sourceCode)
            listOf(Declaration(name = name, type = type, usedTypes = usedTypes))
        }
    }

    private fun extractLexicalDeclarations(node: TSNode, sourceCode: String): List<Declaration> = node
        .children()
        .filter { it.type == VARIABLE_DECLARATOR }
        .mapNotNull { declarator ->
            val name = TreeTraversal.findFirstChildTextByType(declarator, sourceCode, IDENTIFIER)?.trim()
            if (name.isNullOrBlank()) {
                null
            } else {
                Declaration(name = name, type = DeclarationType.VARIABLE, usedTypes = UsedTypeExtractor.extract(node, sourceCode))
            }
        }.toList()

    private fun extractName(node: TSNode, sourceCode: String): String {
        val nameType = when (node.type) {
            CLASS_DECLARATION, INTERFACE_DECLARATION, TYPE_ALIAS_DECLARATION -> TYPE_IDENTIFIER
            else -> IDENTIFIER
        }
        return TreeTraversal.findFirstChildTextByType(node, sourceCode, nameType)?.trim() ?: ""
    }

    private fun declarationType(nodeType: String): DeclarationType = when (nodeType) {
        CLASS_DECLARATION, TYPE_ALIAS_DECLARATION -> DeclarationType.CLASS
        INTERFACE_DECLARATION -> DeclarationType.INTERFACE
        ENUM_DECLARATION -> DeclarationType.ENUM
        FUNCTION_DECLARATION, FUNCTION_SIGNATURE -> DeclarationType.FUNCTION
        LEXICAL_DECLARATION -> DeclarationType.VARIABLE
        else -> DeclarationType.UNKNOWN
    }
}
