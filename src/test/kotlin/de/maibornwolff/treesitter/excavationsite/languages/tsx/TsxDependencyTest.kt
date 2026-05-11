package de.maibornwolff.treesitter.excavationsite.languages.tsx

import de.maibornwolff.treesitter.excavationsite.api.DeclarationType
import de.maibornwolff.treesitter.excavationsite.api.Language
import de.maibornwolff.treesitter.excavationsite.api.TreeSitterDependencies
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TsxDependencyTest {
    @Nested
    inner class ImportExtraction {
        @Test
        fun `should extract ES6 named import`() {
            // Arrange
            val code = "import { Routes } from 'react-router-dom'"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TSX)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("react-router-dom", "Routes")
        }

        @Test
        fun `should extract default import with DEFAULT_EXPORT path segment`() {
            // Arrange
            val code = "import MyComponent from './MyComponent'"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TSX)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly(".", "MyComponent", "DEFAULT_EXPORT")
        }

        @Test
        fun `should extract wildcard re-export as wildcard import`() {
            // Arrange
            val code = "export * from './utils'"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TSX)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly(".", "utils")
            assertThat(result.imports[0].isWildcard).isTrue()
        }
    }

    @Nested
    inner class DeclarationExtraction {
        @Test
        fun `should extract class declaration`() {
            // Arrange
            val code = "export class MyComponent {}"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TSX)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("MyComponent")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.CLASS)
        }
    }

    @Nested
    inner class JsxUsedTypeExtraction {
        @Test
        fun `should extract uppercase JSX self-closing element as usedType`() {
            // Arrange
            val code = """
                class Foo {
                    render() { return <Routes /> }
                }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TSX)

            // Assert
            val fooDeclaration = result.declarations.first { it.name == "Foo" }
            assertThat(fooDeclaration.usedTypes.map { it.name }).containsExactlyInAnyOrder("Routes")
        }

        @Test
        fun `should extract only root identifier for member JSX element`() {
            // Arrange
            val code = """
                class Foo {
                    render() { return <Form.Input /> }
                }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TSX)

            // Assert
            val fooDeclaration = result.declarations.first { it.name == "Foo" }
            assertThat(fooDeclaration.usedTypes.map { it.name }).containsExactlyInAnyOrder("Form")
        }

        @Test
        fun `should not extract lowercase HTML element as usedType`() {
            // Arrange
            val code = """
                class Foo {
                    render() { return <div /> }
                }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TSX)

            // Assert
            val fooDeclaration = result.declarations.first { it.name == "Foo" }
            assertThat(fooDeclaration.usedTypes.map { it.name }).doesNotContain("div")
        }

        @Test
        fun `should extract uppercase JSX opening element as usedType`() {
            // Arrange
            val code = """
                class Foo {
                    render() { return <MyComponent>child</MyComponent> }
                }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.TSX)

            // Assert
            val fooDeclaration = result.declarations.first { it.name == "Foo" }
            assertThat(fooDeclaration.usedTypes.map { it.name }).containsExactlyInAnyOrder("MyComponent")
        }
    }

    @Nested
    inner class ApiSupportCheck {
        @Test
        fun `should support TSX dependency analysis`() {
            assertThat(TreeSitterDependencies.isDependencyAnalysisSupported(Language.TSX)).isTrue()
        }
    }
}
