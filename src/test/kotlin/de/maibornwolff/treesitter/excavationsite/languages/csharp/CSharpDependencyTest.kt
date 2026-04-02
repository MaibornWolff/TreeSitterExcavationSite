package de.maibornwolff.treesitter.excavationsite.languages.csharp

import de.maibornwolff.treesitter.excavationsite.api.Language
import de.maibornwolff.treesitter.excavationsite.api.TreeSitterDependencies
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CSharpDependencyTest {
    @Nested
    inner class NamespaceExtraction {
        @Test
        fun `should extract file-scoped namespace path`() {
            // Arrange
            val code = """
                namespace My.Great.Namespace;

                public class MyClass {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CSHARP)

            // Assert
            assertThat(result.packagePath).containsExactly("My", "Great", "Namespace")
        }

        @Test
        fun `should extract single-segment file-scoped namespace`() {
            // Arrange
            val code = """
                namespace MyNamespace;

                public class MyClass {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CSHARP)

            // Assert
            assertThat(result.packagePath).containsExactly("MyNamespace")
        }

        @Test
        fun `should return empty package path when no namespace declaration`() {
            // Arrange
            val code = "public class MyClass {}"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CSHARP)

            // Assert
            assertThat(result.packagePath).isEmpty()
        }

        @Test
        fun `should extract file-scoped namespace when traditional namespaces also present`() {
            // Arrange
            val code = """
                namespace My.FileScoped;

                public class MyClass {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CSHARP)

            // Assert
            assertThat(result.packagePath).containsExactly("My", "FileScoped")
        }

        @Test
        fun `should return empty package path for traditional namespace only`() {
            // Arrange
            val code = """
                namespace My.Traditional.Namespace {
                    public class MyClass {}
                }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CSHARP)

            // Assert
            assertThat(result.packagePath).isEmpty()
        }
    }
}
