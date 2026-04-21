package de.maibornwolff.treesitter.excavationsite.languages.cpp.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.Declaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.DeclarationType
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import org.treesitter.TSNode

internal object DeclarationExtractor {
    private const val CLASS_SPECIFIER = "class_specifier"
    private const val TYPE_IDENTIFIER = "type_identifier"

    fun extract(rootNode: TSNode, sourceCode: String): List<Declaration> = TreeTraversal
        .findAllDescendantsOfType(rootNode, CLASS_SPECIFIER)
        .mapNotNull { toDeclaration(it, sourceCode) }

    private fun toDeclaration(node: TSNode, sourceCode: String): Declaration? {
        val name = TreeTraversal.findFirstChildTextByType(node, sourceCode, TYPE_IDENTIFIER)
            ?: return null
        return Declaration(
            name = name,
            type = DeclarationType.CLASS,
            usedTypes = emptySet()
        )
    }
}
