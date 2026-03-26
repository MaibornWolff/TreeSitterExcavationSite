package de.maibornwolff.treesitter.excavationsite.languages.kotlin

import de.maibornwolff.treesitter.excavationsite.languages.LanguageRegistry
import de.maibornwolff.treesitter.excavationsite.shared.domain.Language
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeSitterParser
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.treesitter.TSNode

/**
 * Exploration tests to understand the Kotlin AST structure for dependency extraction.
 * These tests are disabled by default and used for development.
 */
class KotlinAstExplorationTest {
    @Test
    @Disabled("Exploration test - enable when needed to examine AST")
    fun `explore package and imports`() {
        val code = """
            package com.example.service

            import java.util.List
            import com.example.model.*
            import com.example.util.StringUtils as Utils
        """.trimIndent()

        printAstFor("Package and Imports", code)
    }

    @Test
    @Disabled("Exploration test - enable when needed to examine AST")
    fun `explore class declarations with modifiers`() {
        val code = """
            package com.example

            class RegularClass
            data class DataClass(val name: String)
            enum class Color { RED, GREEN, BLUE }
            interface Drawable { fun draw() }
            annotation class MyAnnotation
            sealed class Result {
                data class Success(val value: String) : Result()
                data class Error(val message: String) : Result()
            }
            object Singleton
            abstract class AbstractBase
        """.trimIndent()

        printAstFor("Class Declarations with Modifiers", code)
    }

    @Test
    @Disabled("Exploration test - enable when needed to examine AST")
    fun `explore companion object`() {
        val code = """
            package com.example

            class MyClass {
                companion object {
                    const val DEFAULT = "default"
                    fun create(): MyClass = MyClass()
                }
            }

            class Named {
                companion object Factory {
                    fun create(): Named = Named()
                }
            }
        """.trimIndent()

        printAstFor("Companion Object", code)
    }

    @Test
    @Disabled("Exploration test - enable when needed to examine AST")
    fun `explore type references and generics`() {
        val code = """
            package com.example

            class Container {
                val simple: String = ""
                val nullable: String? = null
                val generic: List<String> = emptyList()
                val nested: Map<String, List<Int>> = emptyMap()
                val star: List<*> = emptyList<Any>()
                val func: (String) -> Int = { it.length }
                val multiGeneric: Triple<String, Int, List<Boolean>> = Triple("", 0, emptyList())
            }
        """.trimIndent()

        printAstFor("Type References and Generics", code)
    }

    @Test
    @Disabled("Exploration test - enable when needed to examine AST")
    fun `explore inheritance and delegation`() {
        val code = """
            package com.example

            open class Base
            interface Printable
            interface Serializable

            class Child : Base(), Printable, Serializable {
                override fun toString(): String = ""
            }
        """.trimIndent()

        printAstFor("Inheritance and Delegation", code)
    }

    @Test
    @Disabled("Exploration test - enable when needed to examine AST")
    fun `explore annotations`() {
        val code = """
            package com.example

            @Deprecated("use other")
            @JvmStatic
            class Annotated {
                @Inject
                lateinit var service: MyService

                @Throws(IOException::class)
                fun process() {}
            }
        """.trimIndent()

        printAstFor("Annotations", code)
    }

    @Test
    @Disabled("Exploration test - enable when needed to examine AST")
    fun `explore constructor calls and call expressions`() {
        val code = """
            package com.example

            class Builder {
                fun build() {
                    val obj = MyObject()
                    val list = ArrayList<String>()
                    val result = ServiceFactory.create()
                    val value = helper.process()
                    val chained = Builder().withName("test").build()
                }
            }
        """.trimIndent()

        printAstFor("Constructor Calls and Call Expressions", code)
    }

    @Test
    @Disabled("Exploration test - enable when needed to examine AST")
    fun `explore function declarations`() {
        val code = """
            package com.example

            class Service {
                fun noReturn() {}
                fun withReturn(): String { return "" }
                fun withParams(name: String, count: Int): Boolean { return true }
                fun withNullable(value: String?): Int? { return null }
                fun withGenerics(): List<Map<String, Int>> { return emptyList() }
            }
        """.trimIndent()

        printAstFor("Function Declarations", code)
    }

    @Test
    @Disabled("Exploration test - enable when needed to examine AST")
    fun `explore object declaration`() {
        val code = """
            package com.example

            object AppConfig : Config(), Serializable {
                val name: String = ""
                fun initialize(): Result { return Result() }
            }
        """.trimIndent()

        printAstFor("Object Declaration", code)
    }

    private fun printAstFor(label: String, code: String) {
        val language = LanguageRegistry.getTreeSitterLanguage(Language.KOTLIN)
        val root = TreeSitterParser.parse(code, language)
        val sb = StringBuilder()
        sb.appendLine("=== Kotlin AST: $label ===")
        buildAst(root, code, 0, sb)
        val file = java.io.File("build/kotlin-ast-exploration.txt")
        file.writeText(sb.toString() + "\n")
        println(sb.toString())
    }

    private fun buildAst(node: TSNode, code: String, depth: Int, sb: StringBuilder) {
        val indent = "  ".repeat(depth)
        val nodeText = getNodeTextPreview(node, code)
        sb.appendLine("$indent${node.type}${if (nodeText.isNotEmpty()) " [$nodeText]" else ""}")

        for (child in node.children()) {
            if (!child.isNull) {
                buildAst(child, code, depth + 1, sb)
            }
        }
    }

    private fun getNodeTextPreview(node: TSNode, code: String, maxLength: Int = 50): String {
        val bytes = code.toByteArray(Charsets.UTF_8)
        val start = node.startByte
        val end = node.endByte
        if (start >= bytes.size || end > bytes.size || start >= end) return ""
        val text = String(bytes, start, end - start, Charsets.UTF_8)
            .replace("\n", "\\n")
            .replace("\t", "\\t")
        return if (text.length > maxLength) text.take(maxLength) + "..." else text
    }
}
