package de.maibornwolff.treesitter.excavationsite.languages.vue

import de.maibornwolff.treesitter.excavationsite.integration.extraction.ExtractionFacade
import de.maibornwolff.treesitter.excavationsite.languages.javascript.JavascriptDefinition
import de.maibornwolff.treesitter.excavationsite.shared.domain.ExtractedText
import org.treesitter.TreeSitterJavascript

/**
 * Processes Vue Single File Components for text extraction.
 *
 * Vue SFCs are processed by extracting the <script> section and analyzing it
 * with the JavaScript parser.
 */
object VueExtractionProcessor {
    /**
     * Extracts text from a Vue Single File Component.
     *
     * @param content The Vue SFC content
     * @return A list of extracted text items from the script section
     */
    fun extract(content: String): List<ExtractedText> {
        val scriptContent = VueScriptExtractor.extractScriptContent(content)
        if (scriptContent.isEmpty()) {
            return emptyList()
        }

        return ExtractionFacade.extract(
            content = scriptContent,
            treeSitterLanguage = TreeSitterJavascript(),
            definition = JavascriptDefinition
        )
    }
}
