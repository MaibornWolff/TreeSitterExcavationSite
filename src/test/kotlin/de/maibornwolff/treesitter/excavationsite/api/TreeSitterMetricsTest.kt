package de.maibornwolff.treesitter.excavationsite.api

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TreeSitterMetricsTest {
    @Test
    fun `should calculate metrics for simple Java class`() {
        // Arrange
        val javaCode = """
            public class HelloWorld {
                // A simple method
                public void sayHello() {
                    if (true) {
                        System.out.println("Hello");
                    }
                }

                public int add(int a, int b) {
                    return a + b;
                }
            }
        """.trimIndent()

        // Act
        val result = TreeSitterMetrics.parse(javaCode, Language.JAVA)

        // Assert
        assertThat(result.numberOfFunctions).isEqualTo(2.0)
        assertThat(result.complexity).isGreaterThanOrEqualTo(2.0) // 2 methods
        assertThat(result.logicComplexity).isGreaterThanOrEqualTo(1.0) // 1 if statement
        assertThat(result.commentLines).isGreaterThanOrEqualTo(1.0)
        assertThat(result.linesOfCode).isGreaterThan(0.0)
        assertThat(result.realLinesOfCode).isGreaterThan(0.0)
    }

    @Test
    fun `should calculate metrics for Kotlin file`() {
        // Arrange
        val kotlinCode = """
            fun main() {
                val x = 10
                if (x > 5) {
                    println("Big")
                } else {
                    println("Small")
                }
            }

            fun calculate(a: Int, b: Int): Int {
                return when {
                    a > b -> a - b
                    else -> b - a
                }
            }
        """.trimIndent()

        // Act
        val result = TreeSitterMetrics.parse(kotlinCode, Language.KOTLIN)

        // Assert
        assertThat(result.numberOfFunctions).isEqualTo(2.0)
        assertThat(result.complexity).isGreaterThan(0.0)
    }

    @Test
    fun `should calculate metrics for Python file`() {
        // Arrange
        val pythonCode = """
            def greet(name):
                # This is a comment
                if name:
                    print(f"Hello, {name}")
                else:
                    print("Hello, World")

            def add(a, b):
                return a + b
        """.trimIndent()

        // Act
        val result = TreeSitterMetrics.parse(pythonCode, Language.PYTHON)

        // Assert
        assertThat(result.numberOfFunctions).isEqualTo(2.0)
        assertThat(result.commentLines).isGreaterThanOrEqualTo(1.0)
    }

    @Test
    fun `should detect language from file extension`() {
        // Assert
        assertThat(Language.fromExtension(".java")).isEqualTo(Language.JAVA)
        assertThat(Language.fromExtension(".kt")).isEqualTo(Language.KOTLIN)
        assertThat(Language.fromExtension(".kts")).isEqualTo(Language.KOTLIN)
        assertThat(Language.fromExtension(".ts")).isEqualTo(Language.TYPESCRIPT)
        assertThat(Language.fromExtension(".tsx")).isEqualTo(Language.TYPESCRIPT)
        assertThat(Language.fromExtension(".py")).isEqualTo(Language.PYTHON)
        assertThat(Language.fromExtension(".go")).isEqualTo(Language.GO)
        assertThat(Language.fromExtension(".unknown")).isNull()
    }

    @Test
    fun `should detect language from filename`() {
        // Assert
        assertThat(Language.fromFilename("Main.java")).isEqualTo(Language.JAVA)
        assertThat(Language.fromFilename("build.gradle.kts")).isEqualTo(Language.KOTLIN)
        assertThat(Language.fromFilename("app.component.ts")).isEqualTo(Language.TYPESCRIPT)
    }

    @Test
    fun `should return all supported extensions`() {
        // Act
        val extensions = TreeSitterMetrics.getSupportedExtensions()

        // Assert
        assertThat(extensions).contains(".java", ".kt", ".ts", ".py", ".go", ".rb", ".swift")
        assertThat(extensions.size).isGreaterThan(14) // at least 14 primary extensions
    }

    @Test
    fun `should calculate per-function metrics`() {
        // Arrange
        val javaCode = """
            public class Test {
                public void longMethod() {
                    int a = 1;
                    int b = 2;
                    int c = 3;
                    int d = 4;
                    int e = 5;
                    int f = 6;
                    int g = 7;
                    int h = 8;
                    int i = 9;
                    int j = 10;
                    int k = 11;
                    int l = 12;
                }

                public void shortMethod() {
                    return;
                }
            }
        """.trimIndent()

        // Act
        val result = TreeSitterMetrics.parse(javaCode, Language.JAVA)

        // Assert
        assertThat(result.perFunctionMetrics).containsKey("max_rloc_per_function")
        assertThat(result.perFunctionMetrics).containsKey("min_rloc_per_function")
        assertThat(result.perFunctionMetrics).containsKey("mean_rloc_per_function")
        assertThat(result.perFunctionMetrics).containsKey("median_rloc_per_function")
    }

    @Test
    fun `should calculate message chains metric`() {
        // Arrange
        val javaCode = """
            public class Test {
                public void method() {
                    // This is a message chain with 4+ calls
                    obj.method1().method2().method3().method4().method5();
                }
            }
        """.trimIndent()

        // Act
        val result = TreeSitterMetrics.parse(javaCode, Language.JAVA)

        // Assert
        assertThat(result.messageChains).isGreaterThanOrEqualTo(1.0)
    }
}
