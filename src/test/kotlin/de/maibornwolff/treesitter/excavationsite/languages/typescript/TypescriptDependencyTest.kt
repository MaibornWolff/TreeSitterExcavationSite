package de.maibornwolff.treesitter.excavationsite.languages.typescript

import de.maibornwolff.treesitter.excavationsite.api.DeclarationType
import de.maibornwolff.treesitter.excavationsite.api.Language
import de.maibornwolff.treesitter.excavationsite.api.TreeSitterDependencies
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TypescriptDependencyTest {
    @Nested
    inner class DeclarationTypeSupport {
        @Test
        fun `should have FUNCTION declaration type`() {
            // Assert — verifies DeclarationType.FUNCTION exists (compile-time) and has the expected name
            assertThat(DeclarationType.FUNCTION.name).isEqualTo("FUNCTION")
        }

        @Test
        fun `should have VARIABLE declaration type`() {
            // Assert — verifies DeclarationType.VARIABLE exists (compile-time) and has the expected name
            assertThat(DeclarationType.VARIABLE.name).isEqualTo("VARIABLE")
        }
    }

    @Nested
    inner class PackageExtraction {
        @Test
        fun `should return empty package path`() {
            // Arrange
            val code = "import { Foo } from './foo'"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TYPESCRIPT)

            // Assert
            assertThat(result.packagePath).isEmpty()
        }
    }

    @Nested
    inner class ImportExtraction {
        @Test
        fun `should extract ES6 named import`() {
            // Arrange
            val code = "import { Foo } from './foo'"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TYPESCRIPT)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly(".", "foo")
            assertThat(result.imports[0].isWildcard).isFalse()
        }

        @Test
        fun `should extract ES6 default import`() {
            // Arrange
            val code = "import Foo from './foo'"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TYPESCRIPT)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly(".", "foo")
            assertThat(result.imports[0].isWildcard).isFalse()
        }

        @Test
        fun `should extract ES6 wildcard import`() {
            // Arrange
            val code = "import * as Foo from './foo'"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TYPESCRIPT)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly(".", "foo")
            assertThat(result.imports[0].isWildcard).isTrue()
        }

        @Test
        fun `should extract CommonJS require`() {
            // Arrange
            val code = "const foo = require('./foo')"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TYPESCRIPT)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly(".", "foo")
            assertThat(result.imports[0].isWildcard).isFalse()
        }

        @Test
        fun `should extract multiple imports`() {
            // Arrange
            val code = """
                import { Foo } from './foo'
                import Bar from 'bar'
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TYPESCRIPT)

            // Assert
            assertThat(result.imports).hasSize(2)
        }

        @Test
        fun `should return empty imports when no imports present`() {
            // Arrange
            val code = "class Foo {}"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TYPESCRIPT)

            // Assert
            assertThat(result.imports).isEmpty()
        }

        @Test
        fun `should split multi-segment import path by slash`() {
            // Arrange
            val code = "import { Foo } from '@scope/package'"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TYPESCRIPT)

            // Assert
            assertThat(result.imports[0].path).containsExactly("@scope", "package")
        }

        @Test
        fun `should extract side-effect import`() {
            // Arrange
            val code = "import './styles.css'"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TYPESCRIPT)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly(".", "styles.css")
            assertThat(result.imports[0].isWildcard).isFalse()
        }
    }

    @Nested
    inner class DeclarationExtraction {
        @Test
        fun `should extract class declaration`() {
            // Arrange
            val code = "class Foo {}"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TYPESCRIPT)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("Foo")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.CLASS)
        }

        @Test
        fun `should extract interface declaration`() {
            // Arrange
            val code = "interface IFoo {}"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TYPESCRIPT)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("IFoo")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.INTERFACE)
        }

        @Test
        fun `should extract enum declaration`() {
            // Arrange
            val code = "enum Color { RED, GREEN, BLUE }"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TYPESCRIPT)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("Color")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.ENUM)
        }

        @Test
        fun `should extract function declaration`() {
            // Arrange
            val code = "function greet(name: string): void {}"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TYPESCRIPT)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("greet")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.FUNCTION)
        }

        @Test
        fun `should extract type alias declaration as CLASS`() {
            // Arrange
            val code = "type Id = string"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TYPESCRIPT)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("Id")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.CLASS)
        }

        @Test
        fun `should extract const variable declaration`() {
            // Arrange
            val code = "const greeting: string = 'hello'"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TYPESCRIPT)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("greeting")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.VARIABLE)
        }

        @Test
        fun `should extract multiple declarations`() {
            // Arrange
            val code = """
                class Foo {}
                interface IBar {}
                enum Baz { A, B }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TYPESCRIPT)

            // Assert
            assertThat(result.declarations).hasSize(3)
            val byName = result.declarations.associateBy { it.name }
            assertThat(byName["Foo"]?.type).isEqualTo(DeclarationType.CLASS)
            assertThat(byName["IBar"]?.type).isEqualTo(DeclarationType.INTERFACE)
            assertThat(byName["Baz"]?.type).isEqualTo(DeclarationType.ENUM)
        }

        @Test
        fun `should extract nested class declarations`() {
            // Arrange
            val code = """
                class Outer {
                    inner = class Inner {}
                }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TYPESCRIPT)

            // Assert
            val names = result.declarations.map { it.name }
            assertThat(names).containsExactlyInAnyOrder("Outer")
        }
    }
}
