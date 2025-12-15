package de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.javascript

import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

private const val IDENTIFIER = "identifier"
private const val OBJECT_PATTERN = "object_pattern"
private const val ARRAY_PATTERN = "array_pattern"
private const val REST_PATTERN = "rest_pattern"
private const val REQUIRED_PARAMETER = "required_parameter"

/**
 * Extracts identifiers from formal parameters, handling rest patterns.
 */
internal fun extractIdentifiersFromFormalParameters(node: TSNode, sourceCode: String): List<String> {
    return node.children().flatMap { child ->
        when (child.type) {
            REST_PATTERN -> {
                listOfNotNull(
                    TreeTraversal.findFirstChildTextByType(
                        child,
                        sourceCode,
                        IDENTIFIER
                    )
                )
            }
            OBJECT_PATTERN,
            ARRAY_PATTERN -> extractIdentifiersFromPattern(child, sourceCode)
            REQUIRED_PARAMETER -> {
                val restPattern = child.children().firstOrNull {
                    it.type == REST_PATTERN
                }
                listOfNotNull(
                    restPattern?.let {
                        TreeTraversal.findFirstChildTextByType(
                            it,
                            sourceCode,
                            IDENTIFIER
                        )
                    }
                )
            }
            else -> emptyList()
        }
    }.toList()
}
