package de.maibornwolff.treesitter.excavationsite.languages.python.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.ImportDeclaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.ImportKind
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

internal object ImportExtractor {
    private const val IMPORT_STATEMENT = "import_statement"
    private const val IMPORT_FROM_STATEMENT = "import_from_statement"
    private const val DOTTED_NAME = "dotted_name"
    private const val ALIASED_IMPORT = "aliased_import"
    private const val WILDCARD_IMPORT = "wildcard_import"
    private const val RELATIVE_IMPORT = "relative_import"
    private const val IMPORT_PREFIX = "import_prefix"
    private const val FIELD_MODULE_NAME = "module_name"
    private const val FIELD_NAME = "name"
    private const val SEPARATOR = "."

    fun extract(rootNode: TSNode, sourceCode: String): List<ImportDeclaration> = rootNode
        .children()
        .flatMap { node ->
            when (node.type) {
                IMPORT_STATEMENT -> extractStandardImports(node, sourceCode)
                IMPORT_FROM_STATEMENT -> extractFromImports(node, sourceCode)
                else -> emptySequence()
            }
        }.toList()

    private fun extractStandardImports(node: TSNode, sourceCode: String): Sequence<ImportDeclaration> =
        node.children().mapNotNull { child ->
            when (child.type) {
                DOTTED_NAME -> standardImport(child, sourceCode, isAliased = false)
                ALIASED_IMPORT -> {
                    val name = child.children().firstOrNull { it.type == DOTTED_NAME } ?: return@mapNotNull null
                    standardImport(name, sourceCode, isAliased = true)
                }
                else -> null
            }
        }

    private fun standardImport(dottedName: TSNode, sourceCode: String, isAliased: Boolean): ImportDeclaration = ImportDeclaration(
        path = TreeTraversal.getNodeText(dottedName, sourceCode).split(SEPARATOR),
        isWildcard = false,
        kind = ImportKind.STANDARD,
        isAliased = isAliased
    )

    private fun extractFromImports(node: TSNode, sourceCode: String): Sequence<ImportDeclaration> {
        val moduleNode = node.getChildByFieldName(FIELD_MODULE_NAME).takeIf { !it.isNull } ?: return emptySequence()
        val modulePath = extractModulePath(moduleNode, sourceCode) ?: return emptySequence()

        val isWildcard = node.children().any { it.type == WILDCARD_IMPORT }
        if (isWildcard) {
            return sequenceOf(
                ImportDeclaration(path = modulePath, isWildcard = true, kind = ImportKind.IMPORT_FROM)
            )
        }

        val nameNodes = nameFieldChildren(node)
        if (nameNodes.isEmpty()) return emptySequence()

        return nameNodes.asSequence().mapNotNull { nameNode ->
            when (nameNode.type) {
                DOTTED_NAME -> fromImport(modulePath, nameNode, sourceCode, isAliased = false)
                ALIASED_IMPORT -> {
                    val original = nameNode.children().firstOrNull { it.type == DOTTED_NAME } ?: return@mapNotNull null
                    fromImport(modulePath, original, sourceCode, isAliased = true)
                }
                else -> null
            }
        }
    }

    private fun extractModulePath(moduleNode: TSNode, sourceCode: String): List<String>? = when (moduleNode.type) {
        DOTTED_NAME -> TreeTraversal.getNodeText(moduleNode, sourceCode).split(SEPARATOR)
        RELATIVE_IMPORT -> extractRelativeModulePath(moduleNode, sourceCode)
        else -> null
    }

    private fun extractRelativeModulePath(relativeNode: TSNode, sourceCode: String): List<String>? {
        val prefixNode = relativeNode.children().firstOrNull { it.type == IMPORT_PREFIX } ?: return null
        val prefixText = TreeTraversal.getNodeText(prefixNode, sourceCode)
        val dottedName = relativeNode.children().firstOrNull { it.type == DOTTED_NAME }
        val tail = dottedName?.let { TreeTraversal.getNodeText(it, sourceCode).split(SEPARATOR) }.orEmpty()
        return listOf(prefixText) + tail
    }

    private fun fromImport(modulePath: List<String>, nameNode: TSNode, sourceCode: String, isAliased: Boolean): ImportDeclaration {
        val nameSegments = TreeTraversal.getNodeText(nameNode, sourceCode).split(SEPARATOR)
        return ImportDeclaration(
            path = modulePath + nameSegments,
            isWildcard = false,
            kind = ImportKind.IMPORT_FROM,
            isAliased = isAliased
        )
    }

    private fun nameFieldChildren(node: TSNode): List<TSNode> {
        val result = mutableListOf<TSNode>()
        for (i in 0 until node.childCount) {
            if (node.getFieldNameForChild(i) == FIELD_NAME) {
                result.add(node.getChild(i))
            }
        }
        return result
    }
}
