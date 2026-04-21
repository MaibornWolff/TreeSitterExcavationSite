package de.maibornwolff.treesitter.excavationsite.languages.cpp.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.ImportDeclaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.ImportKind
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import org.treesitter.TSNode

internal object ImportExtractor {
    private const val PREPROC_INCLUDE = "preproc_include"
    private const val SYSTEM_LIB_STRING = "system_lib_string"
    private const val PATH_SEPARATOR = "/"

    fun extract(rootNode: TSNode, sourceCode: String): List<ImportDeclaration> = TreeTraversal
        .findAllDescendantsOfType(rootNode, PREPROC_INCLUDE)
        .mapNotNull { toIncludeImport(it, sourceCode) }

    private fun toIncludeImport(node: TSNode, sourceCode: String): ImportDeclaration? {
        val pathText = TreeTraversal.findFirstChildTextByType(node, sourceCode, SYSTEM_LIB_STRING)
            ?: return null
        val segments = pathText.removePrefix("<").removeSuffix(">").split(PATH_SEPARATOR)
        return ImportDeclaration(path = segments, isWildcard = false, kind = ImportKind.INCLUDE)
    }
}
