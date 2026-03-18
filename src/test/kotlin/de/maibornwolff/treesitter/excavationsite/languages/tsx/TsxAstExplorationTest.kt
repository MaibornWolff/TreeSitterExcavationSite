package de.maibornwolff.treesitter.excavationsite.languages.tsx

import de.maibornwolff.treesitter.excavationsite.languages.LanguageRegistry
import de.maibornwolff.treesitter.excavationsite.shared.domain.Language
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeSitterParser
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.treesitter.TSNode

/**
 * Exploration tests to understand the TSX AST structure.
 * Disabled by default — enable locally when investigating AST behaviour.
 */
class TsxAstExplorationTest {

    @Test
    fun `explore arrow function in variable declarator`() {
        val code = """
            const Button = ({ label, onClick, disabled }: ButtonProps) => {
                return <button>{label}</button>;
            };
            const formatLabel = (value: string | null): string | undefined => {
                return value;
            };
        """.trimIndent()

        val language = LanguageRegistry.getTreeSitterLanguage(Language.TSX)
        val root = TreeSitterParser.parse(code, language)

        println("=== Arrow function variable_declarator AST ===")
        printAst(root, code, 0)
    }

    @Test
    fun `explore string type annotation AST`() {
        val code = """
            interface ButtonProps {
                label: string;
            }
            const Card = ({ title }: { title: string }) => <div>{title}</div>;
        """.trimIndent()

        val language = LanguageRegistry.getTreeSitterLanguage(Language.TSX)
        val root = TreeSitterParser.parse(code, language)

        println("=== String type annotation AST ===")
        printAst(root, code, 0)
    }

    private fun printAst(node: TSNode, code: String, depth: Int) {
        val indent = "  ".repeat(depth)
        val nodeText = getNodeTextPreview(node, code)
        println("$indent${node.type}${if (nodeText.isNotEmpty()) " [$nodeText]" else ""}")

        for (child in node.children()) {
            if (!child.isNull) {
                printAst(child, code, depth + 1)
            }
        }
    }

    private fun getNodeTextPreview(node: TSNode, code: String, maxLength: Int = 40): String {
        val bytes = code.toByteArray(Charsets.UTF_8)
        val start = node.startByte
        val end = node.endByte
        val text = String(bytes, start, end - start, Charsets.UTF_8)
            .replace("\n", "\\n")
            .replace("\t", "\\t")
        return if (text.length > maxLength) text.take(maxLength) + "..." else text
    }
}
