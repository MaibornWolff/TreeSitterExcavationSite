package de.maibornwolff.treesitter.excavationsite.shared.domain

data class DependencyResult(val packagePath: List<String>, val imports: List<ImportDeclaration>, val declarations: List<Declaration>)

enum class ImportKind {
    STANDARD,
    INCLUDE,
    IMPORT_FROM
}

data class ImportDeclaration(
    val path: List<String>,
    val isWildcard: Boolean,
    val namespacePath: List<String> = emptyList(),
    val kind: ImportKind = ImportKind.STANDARD,
    val isAliased: Boolean = false
)

enum class DeclarationType {
    CLASS,
    INTERFACE,
    ENUM,
    RECORD,
    ANNOTATION,
    FUNCTION,
    VARIABLE,
    REEXPORT,
    UNKNOWN
}

data class Declaration(
    val name: String,
    val type: DeclarationType,
    val usedTypes: Set<UsedType>,
    val parentPath: List<String> = emptyList()
)

/**
 * A type referenced inside a declaration body.
 *
 * `namespacePrefix` holds the qualifier segments written alongside the type
 * at the use site, empty for unqualified uses. The concrete meaning of
 * "qualifier" is language-specific (e.g. namespace scopes in C++, attribute
 * chains in Python); per-language interpretation and the contract for
 * adapters consuming this field are documented in
 * `integration/dependencies/README.md`.
 */
data class UsedType(val name: String, val genericTypes: List<UsedType> = emptyList(), val namespacePrefix: List<String> = emptyList()) {
    fun isUppercase(): Boolean = name.firstOrNull()?.isUpperCase() == true
}
