package de.maibornwolff.treesitter.excavationsite.languages.kotlin

import de.maibornwolff.treesitter.excavationsite.api.Language
import de.maibornwolff.treesitter.excavationsite.api.TreeSitterDependencies
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class KotlinDependencyTest {
    @Nested
    inner class PackageExtraction {
        @Test
        fun `should extract multi-segment package path`() {
            // Arrange
            val code = """
            package com.example.service

            class MyService
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.KOTLIN)

            // Assert
            assertThat(result.packagePath).containsExactly("com", "example", "service")
        }

        @Test
        fun `should extract single-segment package path`() {
            // Arrange
            val code = """
            package mypackage

            class MyService
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.KOTLIN)

            // Assert
            assertThat(result.packagePath).containsExactly("mypackage")
        }

        @Test
        fun `should return empty package path when no package declaration`() {
            // Arrange
            val code = "class MyService"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.KOTLIN)

            // Assert
            assertThat(result.packagePath).isEmpty()
        }
    }

    @Nested
    inner class ImportExtraction {
        @Test
        fun `should extract single import`() {
            // Arrange
            val code = """
            import java.util.List

            class MyService
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.KOTLIN)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("java", "util", "List")
            assertThat(result.imports[0].isWildcard).isFalse()
        }

        @Test
        fun `should extract multiple imports`() {
            // Arrange
            val code = """
            import java.util.List
            import java.util.Map
            import java.io.File

            class MyService
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.KOTLIN)

            // Assert
            assertThat(result.imports).hasSize(3)
            assertThat(result.imports[0].path).containsExactly("java", "util", "List")
            assertThat(result.imports[1].path).containsExactly("java", "util", "Map")
            assertThat(result.imports[2].path).containsExactly("java", "io", "File")
        }

        @Test
        fun `should extract wildcard import`() {
            // Arrange
            val code = """
            import java.util.*

            class MyService
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.KOTLIN)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("java", "util")
            assertThat(result.imports[0].isWildcard).isTrue()
        }

        @Test
        fun `should extract aliased import`() {
            // Arrange
            val code = """
            import com.example.util.StringUtils as Utils

            class MyService
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.KOTLIN)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("com", "example", "util", "StringUtils")
            assertThat(result.imports[0].isWildcard).isFalse()
        }

        @Test
        fun `should return empty imports when no import declarations`() {
            // Arrange
            val code = """
            package com.example

            class MyService
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.KOTLIN)

            // Assert
            assertThat(result.imports).isEmpty()
        }
    }
}
