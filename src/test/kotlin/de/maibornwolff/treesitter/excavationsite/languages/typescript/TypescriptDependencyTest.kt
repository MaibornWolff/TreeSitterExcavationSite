package de.maibornwolff.treesitter.excavationsite.languages.typescript

import de.maibornwolff.treesitter.excavationsite.api.DeclarationType
import de.maibornwolff.treesitter.excavationsite.api.Language
import de.maibornwolff.treesitter.excavationsite.api.TreeSitterDependencies
import de.maibornwolff.treesitter.excavationsite.api.UsedType
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
}
