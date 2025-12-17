package de.maibornwolff.treesitter.excavationsite.languages.vue

import de.maibornwolff.treesitter.excavationsite.shared.domain.CommentFormats
import de.maibornwolff.treesitter.excavationsite.shared.domain.Extract
import de.maibornwolff.treesitter.excavationsite.shared.domain.ExtractionMapping
import de.maibornwolff.treesitter.excavationsite.shared.domain.ExtractionStrategy
import de.maibornwolff.treesitter.excavationsite.shared.domain.StringFormats

/**
 * Vue extraction definitions.
 *
 * Extracts identifiers, comments, and strings from Vue Single File Components.
 * Focuses on the <script> section which contains JavaScript code.
 */
object VueExtractionMapping : ExtractionMapping {
    private const val IDENTIFIER = "identifier"

    override val nodeExtractions: Map<String, Extract> = buildMap {
        // Identifiers with generic extraction strategies
        put(
            "function_declaration",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER))
        )
        put(
            "generator_function_declaration",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER))
        )
        put(
            "function_expression",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER))
        )
        put(
            "arrow_function",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER))
        )
        put(
            "method_definition",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByType("property_identifier"))
        )
        put(
            "class_declaration",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER))
        )
        put(
            "variable_declarator",
            Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER))
        )

        // Comments (auto-detect format)
        put("comment", Extract.Comment(CommentFormats.AutoDetect))
        put("html_comment", Extract.Comment(CommentFormats.AutoDetect))

        // Strings
        put("string", Extract.StringLiteral(format = StringFormats.Quoted(stripSingleQuotes = true)))
        put("template_string", Extract.StringLiteral(format = StringFormats.Template))
    }
}
