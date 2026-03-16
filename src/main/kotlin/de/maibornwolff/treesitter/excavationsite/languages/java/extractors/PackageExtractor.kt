package de.maibornwolff.treesitter.excavationsite.languages.java.extractors

import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.executeQuery
import org.treesitter.TSLanguage
import org.treesitter.TSNode

internal object PackageExtractor {
    private const val PACKAGE_QUERY = "(package_declaration) @package"

    fun extract(rootNode: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): List<String> {
        val matches = rootNode.executeQuery(PACKAGE_QUERY, treeSitterLanguage)
        if (matches.isEmpty()) return emptyList()

        val packageNode = matches.first().capture("package").node
        val packageText = TreeTraversal.findFirstChildTextByType(packageNode, sourceCode, "scoped_identifier", "identifier")
            ?: return emptyList()
        return packageText.split(".")
    }
}
