package de.maibornwolff.treesitter.excavationsite.shared.domain

data class DependencyResult(val packagePath: List<String>, val imports: List<ImportDeclaration>, val declarations: List<Declaration>)

enum class ImportKind {
    STANDARD,
    INCLUDE
}

data class ImportDeclaration(
    val path: List<String>,
    val isWildcard: Boolean,
    val namespacePath: List<String> = emptyList(),
    val kind: ImportKind = ImportKind.STANDARD
)

enum class DeclarationType {
    CLASS,
    INTERFACE,
    ENUM,
    RECORD,
    ANNOTATION,
    UNKNOWN
}

data class Declaration(
    val name: String,
    val type: DeclarationType,
    val usedTypes: Set<UsedType>,
    val parentPath: List<String> = emptyList()
)

data class UsedType(val name: String, val genericTypes: List<UsedType> = emptyList()) {
    fun isUppercase(): Boolean = name.firstOrNull()?.isUpperCase() == true
}
