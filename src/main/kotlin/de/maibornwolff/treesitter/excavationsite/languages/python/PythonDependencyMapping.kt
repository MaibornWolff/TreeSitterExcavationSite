package de.maibornwolff.treesitter.excavationsite.languages.python

import de.maibornwolff.treesitter.excavationsite.languages.python.extractors.DeclarationExtractor
import de.maibornwolff.treesitter.excavationsite.languages.python.extractors.ImportExtractor
import de.maibornwolff.treesitter.excavationsite.shared.domain.LanguageDependencyMapping

object PythonDependencyMapping {
    val dependencyMapping = LanguageDependencyMapping(
        extractImports = ImportExtractor::extract,
        extractDeclarations = DeclarationExtractor::extract
    )
}
