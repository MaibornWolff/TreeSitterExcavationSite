package de.maibornwolff.treesitter.excavationsite.features.extraction

import de.maibornwolff.treesitter.excavationsite.features.extraction.adapters.LanguageDefinitionExtractionAdapter
import de.maibornwolff.treesitter.excavationsite.features.extraction.model.ExtractedText
import de.maibornwolff.treesitter.excavationsite.languages.LanguageDefinition
import org.treesitter.TSLanguage

/**
 * Public entry point for the extraction feature.
 *
 * This facade provides the interface for extracting text from source code.
 * It adapts LanguageDefinition to the internal ExtractionNodeTypes port.
 */
object ExtractionFacade {
    /**
     * Extracts text from the given source code.
     *
     * @param content The source code to extract from
     * @param treeSitterLanguage The TreeSitter language parser
     * @param definition The language definition containing extraction mappings
     * @return A list of extracted text items
     */
    fun extract(content: String, treeSitterLanguage: TSLanguage, definition: LanguageDefinition): List<ExtractedText> {
        val extractionNodeTypes = LanguageDefinitionExtractionAdapter(definition)
        val extractor = DirectTextExtractor(
            treeSitterLanguage = treeSitterLanguage,
            extractionNodeTypes = extractionNodeTypes
        )
        return extractor.extract(content)
    }
}
