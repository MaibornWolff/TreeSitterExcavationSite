package de.maibornwolff.treesitter.excavationsite.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TreeSitterExtractionTest {
    @Test
    fun `should extract text from simple Java class`() {
        // Arrange
        val javaCode = """
            public class HelloWorld {
                // A simple greeting method
                public void sayHello() {
                    String message = "Hello, World!";
                    System.out.println(message);
                }
            }
        """.trimIndent()

        // Act
        val result = TreeSitterExtraction.extract(javaCode, Language.JAVA)

        // Assert
        assertThat(result.identifiers).contains("HelloWorld", "sayHello", "message")
        assertThat(result.comments).isNotEmpty
        assertThat(result.strings).contains("Hello, World!")
    }

    @Test
    fun `should extract text from Kotlin file`() {
        // Arrange
        val kotlinCode = """
            // Main entry point
            fun greet(name: String): String {
                val greeting = "Hello"
                return "${'$'}greeting, ${'$'}name!"
            }
        """.trimIndent()

        // Act
        val result = TreeSitterExtraction.extract(kotlinCode, Language.KOTLIN)

        // Assert
        assertThat(result.identifiers).contains("greet", "name", "greeting")
        assertThat(result.comments).isNotEmpty
        assertThat(result.strings).isNotEmpty
    }

    @Test
    fun `should extract text from Python file`() {
        // Arrange
        val pythonCode = """
            # Calculate the sum of two numbers
            def add(a, b):
                result = a + b
                return result

            message = "Sum calculated"
        """.trimIndent()

        // Act
        val result = TreeSitterExtraction.extract(pythonCode, Language.PYTHON)

        // Assert
        assertThat(result.identifiers).contains("add", "a", "b", "result", "message")
        assertThat(result.comments).isNotEmpty
        assertThat(result.strings).contains("Sum calculated")
    }

    @Test
    fun `should check extraction support by language`() {
        // Assert
        assertThat(TreeSitterExtraction.isExtractionSupported(Language.JAVA)).isTrue()
        assertThat(TreeSitterExtraction.isExtractionSupported(Language.KOTLIN)).isTrue()
        assertThat(TreeSitterExtraction.isExtractionSupported(Language.PYTHON)).isTrue()
        assertThat(TreeSitterExtraction.isExtractionSupported(Language.TYPESCRIPT)).isTrue()
    }

    @Test
    fun `should check extraction support by extension`() {
        // Assert
        assertThat(TreeSitterExtraction.isExtractionSupported(".java")).isTrue()
        assertThat(TreeSitterExtraction.isExtractionSupported(".kt")).isTrue()
        assertThat(TreeSitterExtraction.isExtractionSupported(".py")).isTrue()
        assertThat(TreeSitterExtraction.isExtractionSupported(".unknown")).isFalse()
    }

    @Test
    fun `should return supported languages`() {
        // Act
        val languages = TreeSitterExtraction.getSupportedLanguages()

        // Assert
        assertThat(languages).contains(Language.JAVA, Language.KOTLIN, Language.PYTHON, Language.GO)
        assertThat(languages.size).isGreaterThanOrEqualTo(14)
    }

    @Test
    fun `should return supported extensions`() {
        // Act
        val extensions = TreeSitterExtraction.getSupportedExtensions()

        // Assert
        assertThat(extensions).contains(".java", ".kt", ".ts", ".py", ".go", ".rb", ".swift")
        assertThat(extensions.size).isGreaterThan(14)
    }

    @Test
    fun `should provide extracted texts with context`() {
        // Arrange
        val javaCode = """
            public class Test {
                // A comment
                String value = "text";
            }
        """.trimIndent()

        // Act
        val result = TreeSitterExtraction.extract(javaCode, Language.JAVA)

        // Assert
        assertThat(result.extractedTexts).isNotEmpty
        assertThat(result.extractedTexts.map { it.context }).contains(
            ExtractionContext.IDENTIFIER,
            ExtractionContext.COMMENT,
            ExtractionContext.STRING
        )
    }
}
