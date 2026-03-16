package de.maibornwolff.treesitter.excavationsite.languages.java

import de.maibornwolff.treesitter.excavationsite.languages.java.extractors.DeclarationExtractor
import de.maibornwolff.treesitter.excavationsite.languages.java.extractors.ImportExtractor
import de.maibornwolff.treesitter.excavationsite.languages.java.extractors.PackageExtractor
import de.maibornwolff.treesitter.excavationsite.shared.domain.Declaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.DependencyAnalyzer
import de.maibornwolff.treesitter.excavationsite.shared.domain.ImportDeclaration
import org.treesitter.TSLanguage
import org.treesitter.TSNode

object JavaDependencyAnalyzer : DependencyAnalyzer {
    override fun extractPackagePath(rootNode: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): List<String> =
        PackageExtractor.extract(rootNode, sourceCode, treeSitterLanguage)

    override fun extractImports(rootNode: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): List<ImportDeclaration> =
        ImportExtractor.extract(rootNode, sourceCode, treeSitterLanguage)

    override fun extractDeclarations(rootNode: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): List<Declaration> =
        DeclarationExtractor.extract(rootNode, sourceCode, treeSitterLanguage)
}
