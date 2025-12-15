package de.maibornwolff.treesitter.excavationsite.languages

import org.treesitter.TSLanguage
import org.treesitter.TreeSitterBash
import org.treesitter.TreeSitterC
import org.treesitter.TreeSitterCSharp
import org.treesitter.TreeSitterCpp
import org.treesitter.TreeSitterGo
import org.treesitter.TreeSitterJava
import org.treesitter.TreeSitterJavascript
import org.treesitter.TreeSitterKotlin
import org.treesitter.TreeSitterObjc
import org.treesitter.TreeSitterPhp
import org.treesitter.TreeSitterPython
import org.treesitter.TreeSitterRuby
import org.treesitter.TreeSitterSwift
import org.treesitter.TreeSitterTypescript

/**
 * Registry that maps Language enum values to their TreeSitter language and definition.
 *
 * This centralizes the mapping between the Language enum and its associated
 * TreeSitter parser and language definition.
 */
object LanguageRegistry {
    /**
     * Returns a new TreeSitter language parser instance for the given language.
     */
    fun getTreeSitterLanguage(language: Language): TSLanguage {
        return when (language) {
            Language.JAVA -> TreeSitterJava()
            Language.KOTLIN -> TreeSitterKotlin()
            Language.TYPESCRIPT -> TreeSitterTypescript()
            Language.JAVASCRIPT -> TreeSitterJavascript()
            Language.PYTHON -> TreeSitterPython()
            Language.GO -> TreeSitterGo()
            Language.PHP -> TreeSitterPhp()
            Language.RUBY -> TreeSitterRuby()
            Language.SWIFT -> TreeSitterSwift()
            Language.BASH -> TreeSitterBash()
            Language.CSHARP -> TreeSitterCSharp()
            Language.CPP -> TreeSitterCpp()
            Language.C -> TreeSitterC()
            Language.OBJECTIVE_C -> TreeSitterObjc()
        }
    }

    /**
     * Returns the language definition for the given language.
     */
    fun getLanguageDefinition(language: Language): LanguageDefinition {
        return when (language) {
            Language.JAVA -> JavaDefinition
            Language.KOTLIN -> KotlinDefinition
            Language.TYPESCRIPT -> TypescriptDefinition
            Language.JAVASCRIPT -> JavascriptDefinition
            Language.PYTHON -> PythonDefinition
            Language.GO -> GoDefinition
            Language.PHP -> PhpDefinition
            Language.RUBY -> RubyDefinition
            Language.SWIFT -> SwiftDefinition
            Language.BASH -> BashDefinition
            Language.CSHARP -> CSharpDefinition
            Language.CPP -> CppDefinition
            Language.C -> CDefinition
            Language.OBJECTIVE_C -> ObjectiveCDefinition
        }
    }
}
