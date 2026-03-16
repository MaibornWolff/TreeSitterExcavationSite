package de.maibornwolff.treesitter.excavationsite.languages.java.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.ImportDeclaration
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.executeQuery
import org.treesitter.TSLanguage
import org.treesitter.TSNode

internal object ImportExtractor {
    private const val IMPORT_QUERY = "(import_declaration) @import"

    fun extract(rootNode: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): List<ImportDeclaration> {
        val matches = rootNode.executeQuery(IMPORT_QUERY, treeSitterLanguage)
        return matches.map { match ->
            val importNode = match.capture("import").node
            val isWildcard = importNode.children().any { it.type == "asterisk" }
            val identifierText = TreeTraversal.findFirstChildTextByType(importNode, sourceCode, "scoped_identifier", "identifier")
            val path = identifierText?.split(".") ?: emptyList()
            ImportDeclaration(path = path, isWildcard = isWildcard)
        }
    }
}
