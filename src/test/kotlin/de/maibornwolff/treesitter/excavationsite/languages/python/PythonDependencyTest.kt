package de.maibornwolff.treesitter.excavationsite.languages.python

import de.maibornwolff.treesitter.excavationsite.api.DeclarationType
import de.maibornwolff.treesitter.excavationsite.api.ImportKind
import de.maibornwolff.treesitter.excavationsite.api.Language
import de.maibornwolff.treesitter.excavationsite.api.TreeSitterDependencies
import de.maibornwolff.treesitter.excavationsite.api.UsedType
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

    @Nested
    inner class ImportExtraction {
        @Test
        fun `should extract single-segment standard import`() {
            // Arrange
            val code = """
            import os
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("os")
            assertThat(result.imports[0].kind).isEqualTo(ImportKind.STANDARD)
            assertThat(result.imports[0].isWildcard).isFalse()
            assertThat(result.imports[0].isAliased).isFalse()
        }

        @Test
        fun `should extract dotted standard import`() {
            // Arrange
            val code = """
            import os.path
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("os", "path")
            assertThat(result.imports[0].kind).isEqualTo(ImportKind.STANDARD)
        }

        @Test
        fun `should split multi-name standard import into separate declarations`() {
            // Arrange
            val code = """
            import os, sys
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.imports).hasSize(2)
            assertThat(result.imports[0].path).containsExactly("os")
            assertThat(result.imports[1].path).containsExactly("sys")
        }

        @Test
        fun `should mark aliased standard import as aliased and drop the alias name`() {
            // Arrange
            val code = """
            import numpy as np
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("numpy")
            assertThat(result.imports[0].kind).isEqualTo(ImportKind.STANDARD)
            assertThat(result.imports[0].isAliased).isTrue()
        }

        @Test
        fun `should extract from-import with module and name combined into path`() {
            // Arrange
            val code = """
            from typing import List
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("typing", "List")
            assertThat(result.imports[0].kind).isEqualTo(ImportKind.IMPORT_FROM)
            assertThat(result.imports[0].isWildcard).isFalse()
            assertThat(result.imports[0].isAliased).isFalse()
        }

        @Test
        fun `should extract from-import with dotted module path`() {
            // Arrange
            val code = """
            from os.path import join
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("os", "path", "join")
            assertThat(result.imports[0].kind).isEqualTo(ImportKind.IMPORT_FROM)
        }

        @Test
        fun `should split multi-name from-import into separate declarations`() {
            // Arrange
            val code = """
            from typing import List, Optional, Dict
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.imports).hasSize(3)
            assertThat(result.imports[0].path).containsExactly("typing", "List")
            assertThat(result.imports[1].path).containsExactly("typing", "Optional")
            assertThat(result.imports[2].path).containsExactly("typing", "Dict")
            assertThat(result.imports.map { it.kind }).containsExactly(
                ImportKind.IMPORT_FROM,
                ImportKind.IMPORT_FROM,
                ImportKind.IMPORT_FROM
            )
        }

        @Test
        fun `should mark aliased from-import as aliased and drop the alias name`() {
            // Arrange
            val code = """
            from numpy import ndarray as Array
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("numpy", "ndarray")
            assertThat(result.imports[0].kind).isEqualTo(ImportKind.IMPORT_FROM)
            assertThat(result.imports[0].isAliased).isTrue()
        }

        @Test
        fun `should extract wildcard from-import with module-only path`() {
            // Arrange
            val code = """
            from os.path import *
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("os", "path")
            assertThat(result.imports[0].kind).isEqualTo(ImportKind.IMPORT_FROM)
            assertThat(result.imports[0].isWildcard).isTrue()
            assertThat(result.imports[0].isAliased).isFalse()
        }

        @Test
        fun `should encode relative from-import with leading dots`() {
            // Arrange
            val code = """
            from .foo import Bar
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly(".", "foo", "Bar")
            assertThat(result.imports[0].kind).isEqualTo(ImportKind.IMPORT_FROM)
        }

        @Test
        fun `should encode multiple-dot relative from-import`() {
            // Arrange
            val code = """
            from ..pkg.sub import Baz
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("..", "pkg", "sub", "Baz")
        }

        @Test
        fun `should encode bare relative from-import without module name`() {
            // Arrange
            val code = """
            from . import sibling
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly(".", "sibling")
        }

        @Test
        fun `should extract from-import nested inside a function body`() {
            // Arrange
            val code = """
            def create_app():
                from . import views
                return views
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly(".", "views")
            assertThat(result.imports[0].kind).isEqualTo(ImportKind.IMPORT_FROM)
        }

        @Test
        fun `should extract aliased standard import nested inside a function body`() {
            // Arrange
            val code = """
            def load():
                import numpy as np
                return np
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("numpy")
            assertThat(result.imports[0].kind).isEqualTo(ImportKind.STANDARD)
            assertThat(result.imports[0].isAliased).isTrue()
        }
    }

    @Nested
    inner class DeclarationExtraction {
        @Test
        fun `should extract top-level class as CLASS declaration`() {
            // Arrange
            val code = """
            class MyService:
                pass
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("MyService")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.CLASS)
            assertThat(result.declarations[0].parentPath).isEmpty()
        }

        @Test
        fun `should extract top-level function as FUNCTION declaration`() {
            // Arrange
            val code = """
            def compute():
                return 1
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("compute")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.FUNCTION)
        }

        @Test
        fun `should extract simple module-level assignment as VARIABLE declaration`() {
            // Arrange
            val code = """
            MAX_RETRIES = 3
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("MAX_RETRIES")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.VARIABLE)
            assertThat(result.declarations[0].usedTypes).isEmpty()
        }

        @Test
        fun `should not extract tuple-unpacking assignment as VARIABLE`() {
            // Arrange
            val code = """
            a, b = 1, 2
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.declarations).isEmpty()
        }

        @Test
        fun `should extract annotated assignment as VARIABLE`() {
            // Arrange
            val code = """
            counter: int = 0
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("counter")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.VARIABLE)
        }

        @Test
        fun `should not extract attribute-LHS assignment as VARIABLE`() {
            // Arrange
            val code = """
            obj.field = 1
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.declarations).isEmpty()
        }

        @Test
        fun `should unwrap decorated function and emit FUNCTION declaration`() {
            // Arrange
            val code = """
            @decorator
            def handler():
                pass
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("handler")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.FUNCTION)
        }

        @Test
        fun `should unwrap decorated class and emit CLASS declaration`() {
            // Arrange
            val code = """
            @dataclass
            class Item:
                pass
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("Item")
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.CLASS)
        }

        @Test
        fun `should not extract nested class or function as separate declaration`() {
            // Arrange
            val code = """
            class Outer:
                class Inner:
                    pass
                def method(self):
                    pass
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.declarations).hasSize(1)
            assertThat(result.declarations[0].name).isEqualTo("Outer")
        }
    }

    @Nested
    inner class UsedTypeExtraction {
        @Test
        fun `should emit identifier references inside a class as UsedType`() {
            // Arrange
            val code = """
            class Holder:
                def get():
                    return Foo
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            val foos = result.declarations[0].usedTypes.filter { it.name == "Foo" }
            assertThat(foos).containsExactly(UsedType("Foo"))
        }

        @Test
        fun `should emit attribute reference with last segment as name and earlier segments as namespacePrefix`() {
            // Arrange
            val code = """
            def handler():
                return os.path.join("a", "b")
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            val attributes = result.declarations[0].usedTypes.filter { it.namespacePrefix.isNotEmpty() }
            assertThat(attributes).containsExactlyInAnyOrder(
                UsedType(name = "join", namespacePrefix = listOf("os", "path")),
                UsedType(name = "path", namespacePrefix = listOf("os"))
            )
        }

        @Test
        fun `should rewrite FROM-import alias to original name in identifier stream`() {
            // Arrange
            val code = """
            from numpy import ndarray as Array
            def make() -> Array:
                return Array()
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            val rewritten = result.declarations[0].usedTypes.filter { it.name == "ndarray" || it.name == "Array" }
            assertThat(rewritten).containsExactly(UsedType("ndarray"))
        }

        @Test
        fun `should rewrite STANDARD-import alias prefix to original module name in attribute stream`() {
            // Arrange
            val code = """
            import numpy as np
            def make():
                return np.array([1, 2])
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            val attributes = result.declarations[0].usedTypes.filter { it.namespacePrefix.isNotEmpty() }
            assertThat(attributes).containsExactly(UsedType(name = "array", namespacePrefix = listOf("numpy")))
        }

        @Test
        fun `should run UsedTypeExtractor over the outer decorated subtree so decorators contribute`() {
            // Arrange
            val code = """
            @app.route
            def handler():
                pass
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            val attributes = result.declarations[0].usedTypes.filter { it.namespacePrefix.isNotEmpty() }
            assertThat(attributes).containsExactly(UsedType(name = "route", namespacePrefix = listOf("app")))
        }

        @Test
        fun `should leave VARIABLE declarations with empty usedTypes`() {
            // Arrange
            val code = """
            CONSTANT = some_factory(SomeType)
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            assertThat(result.declarations[0].type).isEqualTo(DeclarationType.VARIABLE)
            assertThat(result.declarations[0].usedTypes).isEmpty()
        }

        @Test
        fun `should preserve source order within identifier stream`() {
            // Arrange
            val code = """
            def handler():
                return ZZZ + AAA + MMM
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            val orderedTestIdentifiers = result.declarations[0]
                .usedTypes
                .filter { it.namespacePrefix.isEmpty() && it.name in setOf("ZZZ", "AAA", "MMM") }
                .map { it.name }
            assertThat(orderedTestIdentifiers).containsExactly("ZZZ", "AAA", "MMM")
        }

        @Test
        fun `should preserve source order within attribute stream`() {
            // Arrange
            val code = """
            def handler():
                first.alpha
                second.beta
                third.gamma
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            val orderedAttributes = result.declarations[0]
                .usedTypes
                .filter { it.namespacePrefix.isNotEmpty() }
            assertThat(orderedAttributes).containsExactly(
                UsedType(name = "alpha", namespacePrefix = listOf("first")),
                UsedType(name = "beta", namespacePrefix = listOf("second")),
                UsedType(name = "gamma", namespacePrefix = listOf("third"))
            )
        }

        @Test
        fun `should partition usedTypes by namespacePrefix into identifier and attribute streams`() {
            // Arrange
            val code = """
            def handler():
                Foo
                bar.Baz
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.PYTHON)

            // Assert
            val (identifierStream, attributeStream) = result.declarations[0]
                .usedTypes
                .partition { it.namespacePrefix.isEmpty() }
            assertThat(identifierStream).containsExactlyInAnyOrder(
                UsedType("handler"),
                UsedType("Foo"),
                UsedType("bar"),
                UsedType("Baz")
            )
            assertThat(attributeStream).containsExactly(
                UsedType(name = "Baz", namespacePrefix = listOf("bar"))
            )
        }
    }
}
