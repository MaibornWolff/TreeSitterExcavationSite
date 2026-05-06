package de.maibornwolff.treesitter.excavationsite.languages.python.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.UsedType
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

internal object UsedTypeExtractor {
    private const val IDENTIFIER = "identifier"
    private const val ATTRIBUTE = "attribute"
    private const val SEPARATOR = "."

    fun extract(
        declaration: TSNode,
        sourceCode: String,
        fromImportAliases: Map<String, String>,
        standardImportAliases: Map<String, String>
    ): Set<UsedType> {
        val identifierStream = mutableListOf<UsedType>()
        val attributeStream = mutableListOf<UsedType>()
        collect(declaration, sourceCode, fromImportAliases, standardImportAliases, identifierStream, attributeStream)
        return (identifierStream + attributeStream).toCollection(LinkedHashSet())
    }

    private fun collect(
        node: TSNode,
        sourceCode: String,
        fromImportAliases: Map<String, String>,
        standardImportAliases: Map<String, String>,
        identifierStream: MutableList<UsedType>,
        attributeStream: MutableList<UsedType>
    ) {
        for (child in node.children()) {
            if (child.isNull) continue
            when (child.type) {
                IDENTIFIER -> {
                    val text = TreeTraversal.getNodeText(child, sourceCode)
                    val resolved = fromImportAliases[text] ?: text
                    identifierStream.add(UsedType(name = resolved))
                }
                ATTRIBUTE -> attributeStream.add(buildAttributeUsedType(child, sourceCode, standardImportAliases))
            }
            collect(child, sourceCode, fromImportAliases, standardImportAliases, identifierStream, attributeStream)
        }
    }

    private fun buildAttributeUsedType(attributeNode: TSNode, sourceCode: String, standardImportAliases: Map<String, String>): UsedType {
        val parts = TreeTraversal.getNodeText(attributeNode, sourceCode).split(SEPARATOR)
        val name = parts.last()
        val rawPrefix = parts.dropLast(1)
        val rewrittenPrefix = rewriteStandardAliasPrefix(rawPrefix, standardImportAliases)
        return UsedType(name = name, namespacePrefix = rewrittenPrefix)
    }

    private fun rewriteStandardAliasPrefix(prefix: List<String>, standardImportAliases: Map<String, String>): List<String> {
        if (prefix.isEmpty()) return prefix
        val first = prefix.first()
        val replacement = standardImportAliases[first] ?: return prefix
        return replacement.split(SEPARATOR) + prefix.drop(1)
    }
}
