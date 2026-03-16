package de.maibornwolff.treesitter.excavationsite.languages.java.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.Declaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.DeclarationType
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.executeQuery
import org.treesitter.TSLanguage
import org.treesitter.TSNode

internal object DeclarationExtractor {
    private const val DECLARATION_QUERY =
        "[" +
            "(class_declaration)" +
            "(record_declaration)" +
            "(interface_declaration)" +
            "(enum_declaration)" +
            "(annotation_type_declaration)" +
            "] @declaration"

    fun extract(rootNode: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): List<Declaration> {
        val matches = rootNode.executeQuery(DECLARATION_QUERY, treeSitterLanguage)
        return matches.map { match ->
            val declarationNode = match.capture("declaration").node
            val name = TreeTraversal.getNodeText(declarationNode.getChildByFieldName("name"), sourceCode)
            val type = declarationType(declarationNode)
            val usedTypes = UsedTypeExtractor.extract(declarationNode, sourceCode, treeSitterLanguage)
            Declaration(name = name, type = type, usedTypes = usedTypes)
        }
    }

    private fun declarationType(node: TSNode): DeclarationType = when (node.type) {
        "class_declaration" -> DeclarationType.CLASS
        "record_declaration" -> DeclarationType.RECORD
        "interface_declaration" -> DeclarationType.INTERFACE
        "enum_declaration" -> DeclarationType.ENUM
        "annotation_type_declaration" -> DeclarationType.ANNOTATION
        else -> DeclarationType.UNKNOWN
    }
}
