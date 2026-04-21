package de.maibornwolff.treesitter.excavationsite.languages.cpp

import de.maibornwolff.treesitter.excavationsite.api.Language
import de.maibornwolff.treesitter.excavationsite.api.TreeSitterDependencies
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CppDependencyTest {
    @Nested
    inner class PackageExtraction {
        @Test
        fun `should extract single namespace as package path`() {
            // Arrange
            val code = """
                namespace MyApp {
                    class Foo {};
                }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CPP)

            // Assert
            assertThat(result.packagePath).containsExactly("MyApp")
        }

        @Test
        fun `should use outermost namespace when physically nested`() {
            // Arrange
            val code = """
                namespace Outer {
                    namespace Inner {
                        class Foo {};
                    }
                }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CPP)

            // Assert
            assertThat(result.packagePath).containsExactly("Outer")
        }

        @Test
        fun `should split C++17 nested namespace into segments`() {
            // Arrange
            val code = """
                namespace Outer::Middle::Inner {
                    class Foo {};
                }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CPP)

            // Assert
            assertThat(result.packagePath).containsExactly("Outer", "Middle", "Inner")
        }
    }
}
