package de.maibornwolff.treesitter.excavationsite.languages.python

import de.maibornwolff.treesitter.excavationsite.api.Language
import de.maibornwolff.treesitter.excavationsite.api.TreeSitterDependencies
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PythonDependencyTest {
    @Nested
    inner class PackageExtraction {
        @Test
        fun `should return empty package path`() {
            // Arrange
            val code = """
            class MyService:
                pass
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.packagePath).isEmpty()
        }
    }
}
