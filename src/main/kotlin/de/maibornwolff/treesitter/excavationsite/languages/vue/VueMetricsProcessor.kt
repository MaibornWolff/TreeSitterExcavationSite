package de.maibornwolff.treesitter.excavationsite.languages.vue

import de.maibornwolff.treesitter.excavationsite.integration.metrics.MetricsFacade
import de.maibornwolff.treesitter.excavationsite.languages.javascript.JavascriptDefinition
import org.treesitter.TreeSitterJavascript

/**
 * Processes Vue Single File Components for metrics calculation.
 *
 * Vue SFCs are parsed by extracting the <script> section and analyzing it
 * with the JavaScript parser. The LOC metric is adjusted to reflect the
 * full Vue file size.
 */
object VueMetricsProcessor {
    private val EMPTY_METRICS = mapOf(
        "loc" to 0.0,
        "rloc" to 0.0,
        "comment_lines" to 0.0,
        "complexity" to 0.0,
        "logic_complexity" to 0.0,
        "number_of_functions" to 0.0,
        "message_chains" to 0.0
    )

    /**
     * Calculates metrics for a Vue Single File Component.
     *
     * @param content The Vue SFC content
     * @return A map of metric names to their calculated values
     */
    fun calculateMetrics(content: String): Map<String, Double> {
        val scriptContent = VueScriptExtractor.extractScriptContent(content)
        if (scriptContent.isEmpty()) {
            return createEmptyMetrics(content)
        }

        val jsMetrics = MetricsFacade.collectMetrics(
            content = scriptContent,
            treeSitterLanguage = TreeSitterJavascript(),
            definition = JavascriptDefinition
        )

        return adjustMetricsForVueFile(jsMetrics, content)
    }

    private fun createEmptyMetrics(content: String): Map<String, Double> {
        val totalLines = content.lines().size.toDouble()
        return EMPTY_METRICS.toMutableMap().apply {
            put("loc", totalLines)
        }
    }

    private fun adjustMetricsForVueFile(jsMetrics: Map<String, Double>, vueContent: String): Map<String, Double> {
        val totalLines = vueContent.lines().size.toDouble()
        return jsMetrics.toMutableMap().apply {
            put("loc", totalLines)
        }
    }
}
