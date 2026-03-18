package de.maibornwolff.treesitter.excavationsite.languages.java

import de.maibornwolff.treesitter.excavationsite.languages.java.extractors.DeclarationExtractor
import de.maibornwolff.treesitter.excavationsite.languages.java.extractors.ImportExtractor
import de.maibornwolff.treesitter.excavationsite.languages.java.extractors.PackageExtractor
import de.maibornwolff.treesitter.excavationsite.shared.domain.Declaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.ImportDeclaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.LanguageDependencyMapping
import org.treesitter.TSNode

object JavaDependencyMapping : LanguageDependencyMapping {
    override fun extractPackagePath(rootNode: TSNode, sourceCode: String): List<String> = PackageExtractor.extract(rootNode, sourceCode)

    override fun extractImports(rootNode: TSNode, sourceCode: String): List<ImportDeclaration> =
        ImportExtractor.extract(rootNode, sourceCode)

    override fun extractDeclarations(rootNode: TSNode, sourceCode: String): List<Declaration> =
        DeclarationExtractor.extract(rootNode, sourceCode)
}
