package de.maibornwolff.treesitter.excavationsite.languages.tsx

import de.maibornwolff.treesitter.excavationsite.api.Language
import de.maibornwolff.treesitter.excavationsite.api.TreeSitterExtraction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TsxExtractionTest {
    @Test
    fun `should extract function component name as identifier`() {
        // Arrange
        val code = """
            const Button = () => {
                return <button>Click</button>;
            };
        """.trimIndent()

        // Act
        val result = TreeSitterExtraction.extract(code, Language.TSX)

        // Assert
        assertThat(result.identifiers).contains("Button")
    }

    @Test
    fun `should extract class component name as identifier`() {
        // Arrange
        val code = """
            class MyComponent extends React.Component {
                render() {
                    return <div />;
                }
            }
        """.trimIndent()

        // Act
        val result = TreeSitterExtraction.extract(code, Language.TSX)

        // Assert
        assertThat(result.identifiers).contains("MyComponent", "render")
    }

    @Test
    fun `should extract line comment text`() {
        // Arrange
        val code = """
            // renders a loading spinner
            const Spinner = () => <div className="spinner" />;
        """.trimIndent()

        // Act
        val result = TreeSitterExtraction.extract(code, Language.TSX)

        // Assert
        assertThat(result.comments).containsExactly("renders a loading spinner")
    }

    @Test
    fun `should extract block comment text`() {
        // Arrange
        val code = """
            /* accessible icon button */
            const IconButton = () => <button aria-label="close" />;
        """.trimIndent()

        // Act
        val result = TreeSitterExtraction.extract(code, Language.TSX)

        // Assert
        assertThat(result.comments).containsExactly("accessible icon button")
    }

    @Test
    fun `should extract string literal from props`() {
        // Arrange
        val code = """
            const App = () => <div className="container">Hello</div>;
        """.trimIndent()

        // Act
        val result = TreeSitterExtraction.extract(code, Language.TSX)

        // Assert
        assertThat(result.strings).contains("container")
    }

    @Test
    fun `should extract interface name as identifier`() {
        // Arrange
        val code = """
            interface ButtonProps {
                label: string;
            }
        """.trimIndent()

        // Act
        val result = TreeSitterExtraction.extract(code, Language.TSX)

        // Assert
        assertThat(result.identifiers).contains("ButtonProps")
    }

    @Test
    fun `should not extract JSX text content as string`() {
        // Arrange - "Hello World" is JSX text, not a string literal
        val code = """
            const Hello = () => <p>Hello World</p>;
        """.trimIndent()

        // Act
        val result = TreeSitterExtraction.extract(code, Language.TSX)

        // Assert
        assertThat(result.strings).doesNotContain("Hello World")
    }

}
