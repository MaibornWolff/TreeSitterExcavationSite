package de.maibornwolff.treesitter.excavationsite.languages.csharp

import de.maibornwolff.treesitter.excavationsite.languages.csharp.extractors.NamespaceExtractor
import de.maibornwolff.treesitter.excavationsite.languages.csharp.extractors.UsingDirectiveExtractor
import de.maibornwolff.treesitter.excavationsite.shared.domain.LanguageDependencyMapping

object CSharpDependencyMapping {
    val dependencyMapping = LanguageDependencyMapping(
        extractPackagePath = NamespaceExtractor::extract,
        extractImports = UsingDirectiveExtractor::extract,
        extractDeclarations = { _, _ -> emptyList() }
    )
}
