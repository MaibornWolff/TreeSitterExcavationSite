package de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.treesitter.TreeSitterJava

class TreeSitterQueryUtilsTest {
    @Test
    fun `should preserve multiple captures per match in executeQuery`() {
        // Arrange
        val code = """
            public class MyClass {
                public ReturnType myMethod(ParamType p) {}
            }
        """.trimIndent()
        val java = TreeSitterJava()
        val rootNode = TreeSitterParser.parse(code, java)
        val query = "(method_declaration type: (_) @return_type parameters: (formal_parameters) @params) @method"

        // Act
        val matches = rootNode.executeQuery(query, java)

        // Assert — captures are ordered by source position, use index for query-order lookup
        assertThat(matches).hasSize(1)
        assertThat(matches[0].captures).hasSize(3)
        val capturesByIndex = matches[0].captures.sortedBy { it.index }
        assertThat(TreeTraversal.getNodeText(capturesByIndex[0].node, code)).isEqualTo("ReturnType")
        assertThat(TreeTraversal.getNodeText(capturesByIndex[1].node, code)).isEqualTo("(ParamType p)")
        assertThat(capturesByIndex[2].node.type).isEqualTo("method_declaration")
    }
}
