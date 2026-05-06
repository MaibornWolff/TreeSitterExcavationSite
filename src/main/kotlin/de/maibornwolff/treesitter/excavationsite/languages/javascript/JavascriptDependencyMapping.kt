package de.maibornwolff.treesitter.excavationsite.languages.javascript

import de.maibornwolff.treesitter.excavationsite.languages.javascript.extractors.DeclarationExtractor
import de.maibornwolff.treesitter.excavationsite.languages.javascript.extractors.ImportExtractor
import de.maibornwolff.treesitter.excavationsite.shared.domain.Declaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.LanguageDependencyMapping
import org.treesitter.TSNode

internal object JavascriptDependencyMapping {
    val dependencyMapping = LanguageDependencyMapping(
        extractPackagePath = { _, _ -> emptyList() }, // JS/TS have no package declarations
        extractImports = ImportExtractor::extract,
        extractDeclarations = ::extractJsDeclarations
    )
}

private fun extractJsDeclarations(rootNode: TSNode, sourceCode: String): List<Declaration> {
    val declarations = DeclarationExtractor.extract(rootNode, sourceCode)
    val inlineDefaultName = DeclarationExtractor.findInlineDefaultExportName(rootNode, sourceCode)
        ?: return declarations
    val inlineDecl = declarations.firstOrNull { it.name == inlineDefaultName }
        ?: return declarations
    return declarations + inlineDecl.copy(name = DEFAULT_EXPORT)
}
