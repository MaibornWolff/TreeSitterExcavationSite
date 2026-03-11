package de.maibornwolff.treesitter.excavationsite.languages.java

import de.maibornwolff.treesitter.excavationsite.api.Language
import de.maibornwolff.treesitter.excavationsite.api.TreeSitterDependencies
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class JavaDependencyTest {
    @Nested
    inner class PackageExtraction {
        @Test
        fun `should extract package path`() {
            // Arrange
            val code = """
            package com.example.service;

            public class MyService {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            assertThat(result.packagePath).containsExactly("com", "example", "service")
        }

        @Test
        fun `should return empty package path when no package declaration`() {
            // Arrange
            val code = "public class MyService {}"

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

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
            import java.util.List;

            public class MyService {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("java", "util", "List")
            assertThat(result.imports[0].isWildcard).isFalse()
        }

        @Test
        fun `should extract multiple imports`() {
            // Arrange
            val code = """
            import java.util.List;
            import java.util.Map;
            import java.io.File;

            public class MyService {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

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
            import java.util.*;

            public class MyService {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("java", "util")
            assertThat(result.imports[0].isWildcard).isTrue()
        }

        @Test
        fun `should extract static import`() {
            // Arrange
            val code = """
            import static java.util.Collections.emptyList;

            public class MyService {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("java", "util", "Collections", "emptyList")
            assertThat(result.imports[0].isWildcard).isFalse()
        }

        @Test
        fun `should extract static wildcard import`() {
            // Arrange
            val code = """
            import static java.util.Collections.*;

            public class MyService {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            assertThat(result.imports).hasSize(1)
            assertThat(result.imports[0].path).containsExactly("java", "util", "Collections")
            assertThat(result.imports[0].isWildcard).isTrue()
        }

        @Test
        fun `should return empty imports when no import declarations`() {
            // Arrange
            val code = """
            package com.example;

            public class MyService {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            assertThat(result.imports).isEmpty()
        }

        @Test
        fun `should return empty declarations`() {
            // Arrange
            val code = """
            package com.example;

            import java.util.List;

            public class MyService {}
            """.trimIndent()

            // Act
            val result = TreeSitterDependencies.analyze(code, Language.JAVA)

            // Assert
            assertThat(result.declarations).isEmpty()
        }
    }

    @Nested
    inner class ApiSupportCheck {
        @Test
        fun `should report Java as supported for dependency analysis`() {
            // Act & Assert
            assertThat(TreeSitterDependencies.isDependencyAnalysisSupported(Language.JAVA)).isTrue()
        }

        @Test
        fun `should report unsupported language`() {
            // Act & Assert
            assertThat(TreeSitterDependencies.isDependencyAnalysisSupported(Language.PYTHON)).isFalse()
        }
    }
}
