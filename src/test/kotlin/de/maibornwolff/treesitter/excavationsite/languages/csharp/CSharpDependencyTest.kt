package de.maibornwolff.treesitter.excavationsite.languages.csharp

import de.maibornwolff.treesitter.excavationsite.api.DeclarationType
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

    @Nested
    inner class DeclarationExtraction {
        @Test
        fun `should extract class declaration`() {
            // Arrange
            val code = """
                namespace MyNamespace;

                public class MyClass {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CSHARP)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("MyClass")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.CLASS)
        }

        @Test
        fun `should extract struct declaration`() {
            // Arrange
            val code = """
                namespace MyNamespace;

                public struct MyStruct {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CSHARP)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("MyStruct")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.CLASS)
        }

        @Test
        fun `should extract record declaration`() {
            // Arrange
            val code = """
                namespace MyNamespace;

                public record MyRecord(string Name);
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CSHARP)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("MyRecord")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.RECORD)
        }

        @Test
        fun `should extract interface declaration`() {
            // Arrange
            val code = """
                namespace MyNamespace;

                public interface IMyInterface {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CSHARP)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("IMyInterface")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.INTERFACE)
        }

        @Test
        fun `should extract enum declaration`() {
            // Arrange
            val code = """
                namespace MyNamespace;

                public enum MyEnum { A, B }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CSHARP)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("MyEnum")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.ENUM)
        }

        @Test
        fun `should extract delegate declaration`() {
            // Arrange
            val code = """
                namespace MyNamespace;

                public delegate void MyDelegate(int x);
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CSHARP)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("MyDelegate")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.INTERFACE)
        }

        @Test
        fun `should extract multiple declarations`() {
            // Arrange
            val code = """
                namespace MyNamespace;

                public class MyClass {}
                public interface IMyInterface {}
                public enum MyEnum { A }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CSHARP)

            // Assert
            assertThat(result.declarations).hasSize(3)
            assertThat(result.declarations.map { it.name }).containsExactly("MyClass", "IMyInterface", "MyEnum")
        }

        @Test
        fun `should extract declarations from traditional namespace with parentPath`() {
            // Arrange
            val code = """
                namespace My.Traditional.Namespace {
                    public class MyClass {}
                }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CSHARP)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("MyClass")
            assertThat(result.declarations[0].parentPath).containsExactly("My", "Traditional", "Namespace")
        }

        @Test
        fun `should extract declarations from file-scoped namespace with parentPath`() {
            // Arrange
            val code = """
                namespace My.FileScoped;

                public class MyClass {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CSHARP)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("MyClass")
            assertThat(result.declarations[0].parentPath).containsExactly("My", "FileScoped")
        }

        @Test
        fun `should extract declarations from multiple namespaces with correct parentPaths`() {
            // Arrange
            val code = """
                namespace NamespaceA {
                    public class ClassA {}
                }

                namespace NamespaceB {
                    public class ClassB {}
                }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CSHARP)

            // Assert
            assertThat(result.declarations).hasSize(2)
            assertThat(result.declarations[0].name).isEqualTo("ClassA")
            assertThat(result.declarations[0].parentPath).containsExactly("NamespaceA")
            assertThat(result.declarations[1].name).isEqualTo("ClassB")
            assertThat(result.declarations[1].parentPath).containsExactly("NamespaceB")
        }

        @Test
        fun `should not extract nested declarations`() {
            // Arrange
            val code = """
                namespace MyNamespace;

                public class Outer {
                    public class Inner {}
                    public enum InnerEnum { X }
                }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CSHARP)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("Outer")
        }

        @Test
        fun `should return empty declarations when no type declarations`() {
            // Arrange
            val code = """
                namespace MyNamespace;
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.CSHARP)

            // Assert
            assertThat(result.declarations).isEmpty()
        }
    }
}
