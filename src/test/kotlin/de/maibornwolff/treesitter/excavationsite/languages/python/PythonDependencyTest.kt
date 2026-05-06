package de.maibornwolff.treesitter.excavationsite.languages.python

import de.maibornwolff.treesitter.excavationsite.api.ImportKind
import de.maibornwolff.treesitter.excavationsite.api.Language
import de.maibornwolff.treesitter.excavationsite.api.TreeSitterDependencies
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
    }
}
