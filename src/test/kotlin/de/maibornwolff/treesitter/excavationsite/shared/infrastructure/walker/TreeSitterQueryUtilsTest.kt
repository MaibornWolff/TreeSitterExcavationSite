package de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.treesitter.TreeSitterJava

class TreeSitterQueryUtilsTest {
    @Test
    fun `should resolve captures by name in executeQuery`() {
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

        // Assert
        assertThat(matches).hasSize(1)
        assertThat(matches[0].captures).hasSize(3)
        assertThat(TreeTraversal.getNodeText(matches[0].capture("return_type").node, code)).isEqualTo("ReturnType")
        assertThat(TreeTraversal.getNodeText(matches[0].capture("params").node, code)).isEqualTo("(ParamType p)")
        assertThat(matches[0].capture("method").node.type).isEqualTo("method_declaration")
    }
}
