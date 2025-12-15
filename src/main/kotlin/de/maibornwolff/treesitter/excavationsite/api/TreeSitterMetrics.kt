package de.maibornwolff.treesitter.excavationsite.api

import de.maibornwolff.treesitter.excavationsite.features.metrics.MetricsFacade
import de.maibornwolff.treesitter.excavationsite.languages.LanguageRegistry
import java.io.File

/**
 * Result of parsing a file for metrics.
 *
 * @property metrics Map of metric names to their values
 * @property perFunctionMetrics Map of per-function metric aggregations (min, max, mean, median)
 */
data class MetricsResult(
    val metrics: Map<String, Double>,
    val perFunctionMetrics: Map<String, Double> = emptyMap()
) {
    val complexity: Double get() = metrics["complexity"] ?: 0.0
    val logicComplexity: Double get() = metrics["logic_complexity"] ?: 0.0
    val commentLines: Double get() = metrics["comment_lines"] ?: 0.0
    val realLinesOfCode: Double get() = metrics["rloc"] ?: 0.0
    val linesOfCode: Double get() = metrics["loc"] ?: 0.0
    val numberOfFunctions: Double get() = metrics["number_of_functions"] ?: 0.0
    val longMethod: Double get() = metrics["long_method"] ?: 0.0
    val longParameterList: Double get() = metrics["long_parameter_list"] ?: 0.0
    val excessiveComments: Double get() = metrics["excessive_comments"] ?: 0.0
    val commentRatio: Double get() = metrics["comment_ratio"] ?: 0.0
    val messageChains: Double get() = metrics["message_chains"] ?: 0.0
}

/**
 * Main entry point for calculating code metrics using TreeSitter.
 *
 * Usage:
 * ```kotlin
 * // Parse a file
 * val result = TreeSitterMetrics.parse(File("Main.java"))
 *
 * // Parse content with explicit language
 * val result = TreeSitterMetrics.parse("class Foo {}", Language.JAVA)
 *
 * // Check if language is supported
 * val isSupported = TreeSitterMetrics.isLanguageSupported(".kt")
 * ```
 */
object TreeSitterMetrics {
    /**
     * Parses a file and returns its code metrics.
     *
     * @param file The file to parse
     * @return MetricsResult containing all calculated metrics
     * @throws IllegalArgumentException if the file extension is not supported
     */
    fun parse(file: File): MetricsResult {
        val language = Language.fromFilename(file.name)
            ?: throw IllegalArgumentException("Unsupported file extension: ${file.extension}")

        return parse(file.readText(), language)
    }

    /**
     * Parses source code content with an explicit language.
     *
     * @param content The source code to parse
     * @param language The programming language of the content
     * @return MetricsResult containing all calculated metrics
     */
    fun parse(content: String, language: Language): MetricsResult {
        val definition = LanguageRegistry.getLanguageDefinition(language)
        val treeSitterLanguage = LanguageRegistry.getTreeSitterLanguage(language)

        val metrics = MetricsFacade.collectMetrics(
            content = content,
            treeSitterLanguage = treeSitterLanguage,
            definition = definition
        )

        val perFunctionMetrics = metrics.filterKeys { key ->
            key.endsWith("_per_function")
        }

        val fileMetrics = metrics.filterKeys { key ->
            !key.endsWith("_per_function")
        }

        return MetricsResult(
            metrics = fileMetrics,
            perFunctionMetrics = perFunctionMetrics
        )
    }

    /**
     * Returns whether the given file extension is supported.
     *
     * @param extension The file extension including the dot (e.g., ".java")
     */
    fun isLanguageSupported(extension: String): Boolean {
        return Language.fromExtension(extension) != null
    }

    /**
     * Returns the language for the given file extension, or null if not supported.
     *
     * @param extension The file extension including the dot (e.g., ".java")
     */
    fun getLanguage(extension: String): Language? {
        return Language.fromExtension(extension)
    }

    /**
     * Returns all supported file extensions.
     */
    fun getSupportedExtensions(): Set<String> {
        val extensions = mutableSetOf<String>()
        Language.entries.forEach { lang ->
            extensions.add(lang.primaryExtension)
            extensions.addAll(lang.otherExtensions)
        }
        return extensions
    }
}
