package de.maibornwolff.treesitter.excavationsite.languages.csharp

import de.maibornwolff.treesitter.excavationsite.languages.csharp.extractors.NamespaceExtractor
import de.maibornwolff.treesitter.excavationsite.shared.domain.LanguageDependencyMapping

object CSharpDependencyMapping {
    val dependencyMapping = LanguageDependencyMapping(
        extractPackagePath = NamespaceExtractor::extract,
        extractImports = { _, _ -> emptyList() },
        extractDeclarations = { _, _ -> emptyList() }
    )
}
