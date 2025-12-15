package de.maibornwolff.treesitter.excavationsite.features.extraction.extractors.languagespecific.go

import org.treesitter.TSNode

private const val VAR_SPEC = "var_spec"

/**
 * Extracts all identifiers from var declaration.
 * Handles both single spec and spec_list (block) patterns.
 */
internal fun extractAllFromVarDeclaration(node: TSNode, sourceCode: String): List<String> {
    return extractAllFromVarOrConstDeclaration(node, sourceCode, VAR_SPEC)
}
