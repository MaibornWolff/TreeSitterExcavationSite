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

    @Nested
    inner class UsingDirectiveExtraction {
        @Test
        fun `should extract single using directive`() {
            // Arrange
            val code = """
                using System;

                public class MyClass {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CSHARP)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("System")
            assertThat(result.imports[0].isWildcard).isTrue()
        }

        @Test
        fun `should extract multiple using directives`() {
            // Arrange
            val code = """
                using System;
                using System.Collections.Generic;
                using Microsoft.Extensions.Options;

                public class MyClass {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CSHARP)

            // Assert
            assertThat(result.imports).hasSize(3)
            assertThat(result.imports[0].path).containsExactly("System")
            assertThat(result.imports[1].path).containsExactly("System", "Collections", "Generic")
            assertThat(result.imports[2].path).containsExactly("Microsoft", "Extensions", "Options")
            assertThat(result.imports).allMatch { it.isWildcard }
        }

        @Test
        fun `should extract namespace-scoped using directives`() {
            // Arrange
            val code = """
                namespace MyNamespace {
                    using Microsoft.Extensions.Options;

                    public class MyClass {}
                }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CSHARP)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("Microsoft", "Extensions", "Options")
            assertThat(result.imports[0].isWildcard).isTrue()
        }

        @Test
        fun `should merge global and namespace-scoped using directives`() {
            // Arrange
            val code = """
                using System;

                namespace MyNamespace {
                    using Microsoft.Extensions.Options;

                    public class MyClass {}
                }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CSHARP)

            // Assert
            assertThat(result.imports).hasSize(2)
            assertThat(result.imports[0].path).containsExactly("System")
            assertThat(result.imports[1].path).containsExactly("Microsoft", "Extensions", "Options")
        }

        @Test
        fun `should extract aliased using directives`() {
            // Arrange
            val code = """
                using Alias = System.Collections.Generic;

                public class MyClass {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CSHARP)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("System", "Collections", "Generic")
            assertThat(result.imports[0].isWildcard).isTrue()
        }

        @Test
        fun `should return empty imports when no using directives`() {
            // Arrange
            val code = "public class MyClass {}"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CSHARP)

            // Assert
            assertThat(result.imports).isEmpty()
        }
    }
}
