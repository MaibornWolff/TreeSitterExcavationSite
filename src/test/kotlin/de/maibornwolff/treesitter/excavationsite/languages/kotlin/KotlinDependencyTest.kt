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
}
