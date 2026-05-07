package de.maibornwolff.treesitter.excavationsite.languages.python.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.UsedType
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

internal object UsedTypeExtractor {
    private const val IDENTIFIER = "identifier"
    private const val ATTRIBUTE = "attribute"
    private const val SEPARATOR = "."

    fun extract(declaration: TSNode, sourceCode: String, aliases: AliasContext): Set<UsedType> {
        val collector = Collector(sourceCode, aliases)
        collector.collect(declaration)
        return (collector.identifierStream + collector.attributeStream).toCollection(LinkedHashSet())
    }

    private class Collector(val sourceCode: String, val aliases: AliasContext) {
        val identifierStream = mutableListOf<UsedType>()
        val attributeStream = mutableListOf<UsedType>()

        fun collect(node: TSNode) {
            for (child in node.children()) {
                if (child.isNull) continue
                when (child.type) {
                    IDENTIFIER -> {
                        val text = TreeTraversal.getNodeText(child, sourceCode)
                        val resolved = aliases.fromImports[text] ?: text
                        identifierStream.add(UsedType(name = resolved))
                    }
                    ATTRIBUTE -> attributeStream.add(buildAttributeUsedType(child))
                }
                // ADR-0005: recurse unconditionally so nested attribute usages are emitted too.
                // Source "os.path.join" must produce both UsedType("join", ["os","path"]) and
                // UsedType("path", ["os"]); skipping recursion on attribute nodes drops dependencies
                // on intermediate modules.
                collect(child)
            }
        }

        private fun buildAttributeUsedType(attributeNode: TSNode): UsedType {
            val parts = TreeTraversal.getNodeText(attributeNode, sourceCode).split(SEPARATOR)
            val name = parts.last()
            val rawPrefix = parts.dropLast(1)
            return UsedType(name = name, namespacePrefix = rewriteStandardAliasPrefix(rawPrefix))
        }

        private fun rewriteStandardAliasPrefix(prefix: List<String>): List<String> {
            if (prefix.isEmpty()) return prefix
            val first = prefix.first()
            val replacement = aliases.standardImports[first] ?: return prefix
            return replacement.split(SEPARATOR) + prefix.drop(1)
        }
    }
}
