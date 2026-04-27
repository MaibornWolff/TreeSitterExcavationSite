package de.maibornwolff.treesitter.excavationsite.languages.cpp.extractors

import de.maibornwolff.treesitter.excavationsite.languages.cpp.extractors.declarations.DeclarationMerger
import de.maibornwolff.treesitter.excavationsite.languages.cpp.extractors.declarations.InClassDeclarationFinder
import de.maibornwolff.treesitter.excavationsite.languages.cpp.extractors.declarations.OutOfClassMethodPromoter
import de.maibornwolff.treesitter.excavationsite.shared.domain.Declaration
import org.treesitter.TSNode

internal object DeclarationExtractor {
    fun extract(rootNode: TSNode, sourceCode: String): List<Declaration> = DeclarationMerger.merge(
        InClassDeclarationFinder.find(rootNode, sourceCode) +
            OutOfClassMethodPromoter.promote(rootNode, sourceCode)
    )
}
