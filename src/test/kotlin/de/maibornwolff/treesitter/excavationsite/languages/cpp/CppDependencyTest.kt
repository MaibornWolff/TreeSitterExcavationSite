package de.maibornwolff.treesitter.excavationsite.languages.cpp

import de.maibornwolff.treesitter.excavationsite.api.Language
import de.maibornwolff.treesitter.excavationsite.api.TreeSitterDependencies
import de.maibornwolff.treesitter.excavationsite.shared.domain.ImportDeclaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.ImportKind
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
        fun `should return empty package path for anonymous namespace`() {
            // Arrange
            val code = """
                namespace {
                    class Foo {};
                }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CPP)

            // Assert
            assertThat(result.packagePath).isEmpty()
        }

        @Test
        fun `should return empty package path for file with no namespace`() {
            // Arrange
            val code = "class Foo {};"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CPP)

            // Assert
            assertThat(result.packagePath).isEmpty()
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

        @Test
        fun `should find namespace wrapped in ifdef preprocessor directive`() {
            // Arrange
            val code = """
                #ifdef ENABLE_FEATURE
                namespace MyApp {
                    class Foo {};
                }
                #endif
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CPP)

            // Assert
            assertThat(result.packagePath).containsExactly("MyApp")
        }
    }

    @Nested
    inner class ImportExtraction {
        @Nested
        inner class IncludeDirectives {
            @Test
            fun `should extract system include as INCLUDE-kind import`() {
                // Arrange
                val code = """
                    #include <vector>
                """.trimIndent()

                // Act
                val result = TreeSitterDependencies.analyze(code, Language.CPP)

                // Assert
                assertThat(result.imports).containsExactly(
                    ImportDeclaration(path = listOf("vector"), isWildcard = false, kind = ImportKind.INCLUDE)
                )
            }

            @Test
            fun `should extract quoted include as INCLUDE-kind import`() {
                // Arrange
                val code = """
                    #include "my/header.h"
                """.trimIndent()

                // Act
                val result = TreeSitterDependencies.analyze(code, Language.CPP)

                // Assert
                assertThat(result.imports).containsExactly(
                    ImportDeclaration(path = listOf("my", "header.h"), isWildcard = false, kind = ImportKind.INCLUDE)
                )
            }

            @Test
            fun `should extract multiple includes in source order`() {
                // Arrange
                val code = """
                    #include <vector>
                    #include "local.h"
                    #include <memory>
                """.trimIndent()

                // Act
                val result = TreeSitterDependencies.analyze(code, Language.CPP)

                // Assert
                assertThat(result.imports).containsExactly(
                    ImportDeclaration(path = listOf("vector"), isWildcard = false, kind = ImportKind.INCLUDE),
                    ImportDeclaration(path = listOf("local.h"), isWildcard = false, kind = ImportKind.INCLUDE),
                    ImportDeclaration(path = listOf("memory"), isWildcard = false, kind = ImportKind.INCLUDE)
                )
            }
        }
    }
}
