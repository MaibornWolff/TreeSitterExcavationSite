package de.maibornwolff.treesitter.excavationsite.languages.cpp

import de.maibornwolff.treesitter.excavationsite.api.Language
import de.maibornwolff.treesitter.excavationsite.api.TreeSitterDependencies
import de.maibornwolff.treesitter.excavationsite.shared.domain.Declaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.DeclarationType
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
            fun `should extract include wrapped in ifdef preprocessor directive`() {
                // Arrange
                val code = """
                    #ifdef ENABLE_FEATURE
                    #include "feature.h"
                    #endif
                """.trimIndent()

                // Act
                val result = TreeSitterDependencies.analyze(code, Language.CPP)

                // Assert
                assertThat(result.imports).containsExactly(
                    ImportDeclaration(path = listOf("feature.h"), isWildcard = false, kind = ImportKind.INCLUDE)
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

        @Nested
        inner class UsingDirectives {
            @Test
            fun `should extract using namespace as wildcard STANDARD import`() {
                // Arrange
                val code = """
                    using namespace std;
                """.trimIndent()

                // Act
                val result = TreeSitterDependencies.analyze(code, Language.CPP)

                // Assert
                assertThat(result.imports).containsExactly(
                    ImportDeclaration(path = listOf("std"), isWildcard = true, kind = ImportKind.STANDARD)
                )
            }

            @Test
            fun `should skip in-class using enum directive`() {
                // Arrange
                val code = """
                    enum class Color { RED, GREEN, BLUE };

                    class Widget {
                    public:
                        using enum Color;
                    };
                """.trimIndent()

                // Act
                val result = TreeSitterDependencies.analyze(code, Language.CPP)

                // Assert
                assertThat(result.imports).isEmpty()
            }

            @Test
            fun `should skip inheriting constructor using directive inside class body`() {
                // Arrange
                val code = """
                    class Base {
                    public:
                        Base(int);
                    };

                    class Derived : public Base {
                    public:
                        using Base::Base;
                    };
                """.trimIndent()

                // Act
                val result = TreeSitterDependencies.analyze(code, Language.CPP)

                // Assert
                assertThat(result.imports).isEmpty()
            }

            @Test
            fun `should populate namespacePath for using directive inside a namespace`() {
                // Arrange
                val code = """
                    namespace Outer::Middle {
                        using namespace Utils;
                    }
                """.trimIndent()

                // Act
                val result = TreeSitterDependencies.analyze(code, Language.CPP)

                // Assert
                assertThat(result.imports).containsExactly(
                    ImportDeclaration(
                        path = listOf("Utils"),
                        isWildcard = true,
                        namespacePath = listOf("Outer", "Middle"),
                        kind = ImportKind.STANDARD
                    )
                )
            }

            @Test
            fun `should extract qualified using enum declaration as non-wildcard import`() {
                // Arrange
                val code = """
                    using enum Outer::Color;
                """.trimIndent()

                // Act
                val result = TreeSitterDependencies.analyze(code, Language.CPP)

                // Assert
                assertThat(result.imports).containsExactly(
                    ImportDeclaration(path = listOf("Outer", "Color"), isWildcard = false, kind = ImportKind.STANDARD)
                )
            }

            @Test
            fun `should extract qualified using declaration as non-wildcard import`() {
                // Arrange
                val code = """
                    using A::B::Symbol;
                """.trimIndent()

                // Act
                val result = TreeSitterDependencies.analyze(code, Language.CPP)

                // Assert
                assertThat(result.imports).containsExactly(
                    ImportDeclaration(path = listOf("A", "B", "Symbol"), isWildcard = false, kind = ImportKind.STANDARD)
                )
            }

            @Test
            fun `should split using namespace qualified path on double colon`() {
                // Arrange
                val code = """
                    using namespace A::B::C;
                """.trimIndent()

                // Act
                val result = TreeSitterDependencies.analyze(code, Language.CPP)

                // Assert
                assertThat(result.imports).containsExactly(
                    ImportDeclaration(path = listOf("A", "B", "C"), isWildcard = true, kind = ImportKind.STANDARD)
                )
            }
        }
    }

    @Nested
    inner class DeclarationExtraction {
        @Test
        fun `should extract single class_specifier as CLASS declaration`() {
            // Arrange
            val code = "class Foo {};"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CPP)

            // Assert
            assertThat(result.declarations).containsExactly(
                Declaration(name = "Foo", type = DeclarationType.CLASS, usedTypes = emptySet(), parentPath = emptyList())
            )
        }

        @Test
        fun `should extract struct_specifier as CLASS declaration`() {
            // Arrange
            val code = "struct Bar {};"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CPP)

            // Assert
            assertThat(result.declarations).containsExactly(
                Declaration(name = "Bar", type = DeclarationType.CLASS, usedTypes = emptySet(), parentPath = emptyList())
            )
        }
    }
}
