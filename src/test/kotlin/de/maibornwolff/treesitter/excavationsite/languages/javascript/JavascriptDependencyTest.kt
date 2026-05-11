package de.maibornwolff.treesitter.excavationsite.languages.javascript

import de.maibornwolff.treesitter.excavationsite.api.DeclarationType
import de.maibornwolff.treesitter.excavationsite.api.Language
import de.maibornwolff.treesitter.excavationsite.api.TreeSitterDependencies
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class JavascriptDependencyTest {
    @Nested
    inner class PackageExtraction {
        @Test
        fun `should return empty package path`() {
            // Arrange
            val code = "import { Foo } from './foo'"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVASCRIPT)

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
            val result = TreeSitterDependencies.analyze(code, Language.JAVASCRIPT)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly(".", "foo", "Foo")
            assertThat(result.imports[0].isWildcard).isFalse()
        }

        @Test
        fun `should extract ES6 default import`() {
            // Arrange
            val code = "import Foo from './foo'"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVASCRIPT)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly(".", "foo", "DEFAULT_EXPORT")
            assertThat(result.imports[0].isWildcard).isFalse()
        }

        @Test
        fun `should extract multiple named ES6 imports as separate declarations`() {
            // Arrange
            val code = "import { A, B } from './foo'"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVASCRIPT)

            // Assert
            assertThat(result.imports).hasSize(2)
            val paths = result.imports.map { it.path }
            assertThat(paths).containsExactlyInAnyOrder(listOf(".", "foo", "A"), listOf(".", "foo", "B"))
        }

        @Test
        fun `should extract ES6 wildcard import`() {
            // Arrange
            val code = "import * as Foo from './foo'"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVASCRIPT)

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
            val result = TreeSitterDependencies.analyze(code, Language.JAVASCRIPT)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly(".", "foo", "DEFAULT_EXPORT")
        }

        @Test
        fun `should extract CommonJS destructuring require as separate declarations`() {
            // Arrange
            val code = "const { A, B } = require('./foo')"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVASCRIPT)

            // Assert
            assertThat(result.imports).hasSize(2)
            val paths = result.imports.map { it.path }
            assertThat(paths).containsExactlyInAnyOrder(listOf(".", "foo", "A"), listOf(".", "foo", "B"))
        }

        @Test
        fun `should extract named re-export with alias preserving original name`() {
            // Arrange
            val code = "export { A as B } from './utils'"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVASCRIPT)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly(".", "utils", "A")
        }

        @Test
        fun `should extract CommonJS destructuring require with renamed property`() {
            // Arrange
            val code = "const { myMethod: alias } = require('myModule')"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVASCRIPT)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("myModule", "myMethod")
            assertThat(result.imports[0].isWildcard).isFalse()
        }
    }

    @Nested
    inner class DeclarationExtraction {
        @Test
        fun `should extract exported class declaration`() {
            // Arrange
            val code = "export class Foo {}"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVASCRIPT)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("Foo")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.CLASS)
        }

        @Test
        fun `should extract exported function declaration`() {
            // Arrange
            val code = "export function bar() {}"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVASCRIPT)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("bar")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.FUNCTION)
        }

        @Test
        fun `should extract exported const declaration`() {
            // Arrange
            val code = "export const baz = 42"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVASCRIPT)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("baz")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.VARIABLE)
        }

        @Test
        fun `should keep original declaration and add DEFAULT_EXPORT REEXPORT when local var is default-exported`() {
            // Arrange
            val code = """
                const buildFunction = () => { return "hello"; };
                export default buildFunction;
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVASCRIPT)

            // Assert
            val byName = result.declarations.associateBy { it.name }
            assertThat(byName.keys).containsExactlyInAnyOrder("buildFunction", "DEFAULT_EXPORT")
            assertThat(byName["buildFunction"]?.type).isEqualTo(DeclarationType.VARIABLE)
            assertThat(byName["DEFAULT_EXPORT"]?.type).isEqualTo(DeclarationType.REEXPORT)
            assertThat(byName["DEFAULT_EXPORT"]?.usedTypes?.map { it.name }).containsExactlyInAnyOrder("buildFunction")
        }

        // JS intentionally produces both the named declaration AND a DEFAULT_EXPORT copy (same DeclarationType)
        // for `export default function/class Foo` — matching DC main's legacy JS analyzer behavior.
        // TypeScript does NOT add the DEFAULT_EXPORT copy; see JavascriptDependencyMapping.extractJsDeclarations.
        @Test
        fun `should add DEFAULT_EXPORT node alongside original for export default function`() {
            // Arrange
            val code = "export default function IssueList(props) {}"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVASCRIPT)

            // Assert — both the named function AND a DEFAULT_EXPORT node (DC main produces both)
            val byName = result.declarations.associateBy { it.name }
            assertThat(byName.keys).containsExactlyInAnyOrder("IssueList", "DEFAULT_EXPORT")
            assertThat(byName["IssueList"]?.type).isEqualTo(DeclarationType.FUNCTION)
            assertThat(byName["DEFAULT_EXPORT"]?.type).isEqualTo(DeclarationType.FUNCTION)
        }

        @Test
        fun `should add DEFAULT_EXPORT node alongside original for export default class`() {
            // Arrange
            val code = "export default class Charts extends Component {}"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVASCRIPT)

            // Assert — both the named class AND a DEFAULT_EXPORT node
            val byName = result.declarations.associateBy { it.name }
            assertThat(byName.keys).containsExactlyInAnyOrder("Charts", "DEFAULT_EXPORT")
            assertThat(byName["Charts"]?.type).isEqualTo(DeclarationType.CLASS)
            assertThat(byName["DEFAULT_EXPORT"]?.type).isEqualTo(DeclarationType.CLASS)
        }

        @Test
        fun `should add DEFAULT_EXPORT REEXPORT for anonymous export default class`() {
            // Arrange
            val code = "export default class {}"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVASCRIPT)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("DEFAULT_EXPORT")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.REEXPORT)
            assertThat(result.declarations[0].usedTypes).isEmpty()
        }

        @Test
        fun `should add DEFAULT_EXPORT REEXPORT for anonymous export default function`() {
            // Arrange
            val code = "export default function() {}"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVASCRIPT)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("DEFAULT_EXPORT")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.REEXPORT)
            assertThat(result.declarations[0].usedTypes).isEmpty()
        }

        @Test
        fun `should extract wildcard re-export as REEXPORT declaration`() {
            // Arrange
            val code = "export * from './module'"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVASCRIPT)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("*")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.REEXPORT)
            assertThat(result.declarations[0].usedTypes).isEmpty()
        }
    }

    @Nested
    inner class JsxSmokeTest {
        @Test
        fun `should extract JSX component as usedType when parsing jsx file content`() {
            // Arrange
            val code = """
                export class App {
                    render() { return <MyComponent /> }
                }
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVASCRIPT)

            // Assert
            val appDeclaration = result.declarations.first { it.name == "App" }
            assertThat(appDeclaration.usedTypes.map { it.name }).containsExactlyInAnyOrder("App", "MyComponent")
        }
    }

    @Nested
    inner class ApiSupportCheck {
        @Test
        fun `should support JavaScript dependency analysis`() {
            assertThat(TreeSitterDependencies.isDependencyAnalysisSupported(Language.JAVASCRIPT)).isTrue()
        }
    }
}
