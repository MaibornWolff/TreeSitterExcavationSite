package de.maibornwolff.treesitter.excavationsite.languages.javascript.extractors

import de.maibornwolff.treesitter.excavationsite.languages.javascript.DEFAULT_EXPORT
import de.maibornwolff.treesitter.excavationsite.shared.domain.Declaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.DeclarationType
import de.maibornwolff.treesitter.excavationsite.shared.domain.UsedType
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

internal object DeclarationExtractor {
    private const val CLASS_DECLARATION = "class_declaration"
    private const val ABSTRACT_CLASS_DECLARATION = "abstract_class_declaration"
    private const val INTERFACE_DECLARATION = "interface_declaration"
    private const val ENUM_DECLARATION = "enum_declaration"
    private const val FUNCTION_DECLARATION = "function_declaration"
    private const val FUNCTION_SIGNATURE = "function_signature"
    private const val GENERATOR_FUNCTION_DECLARATION = "generator_function_declaration"
    private const val TYPE_ALIAS_DECLARATION = "type_alias_declaration"
    private const val LEXICAL_DECLARATION = "lexical_declaration"
    private const val VARIABLE_DECLARATION = "variable_declaration"
    private const val EXPORT_STATEMENT = "export_statement"

    private const val EXPORT_CLAUSE = "export_clause"
    private const val EXPORT_SPECIFIER = "export_specifier"
    private const val STRING = "string"
    private const val STRING_FRAGMENT = "string_fragment"
    private const val DEFAULT_KEYWORD = "default"
    private const val WILDCARD_REEXPORT = "*"

    private const val AMBIENT_DECLARATION = "ambient_declaration"
    private const val MODULE_DECLARATION = "module"
    private const val INTERNAL_MODULE = "internal_module"
    private const val STATEMENT_BLOCK = "statement_block"

    private const val EXPRESSION_STATEMENT = "expression_statement"
    private const val ASSIGNMENT_EXPRESSION = "assignment_expression"
    private const val MEMBER_EXPRESSION = "member_expression"

    private const val TYPE_IDENTIFIER = "type_identifier"
    private const val IDENTIFIER = "identifier"
    private const val VARIABLE_DECLARATOR = "variable_declarator"

    private const val IMPORT_STATEMENT = "import_statement"
    private const val IMPORT_CLAUSE = "import_clause"
    private const val NAMED_IMPORTS = "named_imports"
    private const val IMPORT_SPECIFIER = "import_specifier"

    private val DECLARATION_NODE_TYPES = setOf(
        CLASS_DECLARATION,
        ABSTRACT_CLASS_DECLARATION,
        INTERFACE_DECLARATION,
        ENUM_DECLARATION,
        FUNCTION_DECLARATION,
        FUNCTION_SIGNATURE,
        GENERATOR_FUNCTION_DECLARATION,
        TYPE_ALIAS_DECLARATION,
        LEXICAL_DECLARATION,
        VARIABLE_DECLARATION,
        INTERNAL_MODULE
    )

    internal sealed interface DefaultExport {
        data class Named(val name: String) : DefaultExport // export default class Foo {}

        data class Reexport(val name: String) : DefaultExport // export default Foo;

        data object Anonymous : DefaultExport // export default class {}

        data object Value : DefaultExport // export default { ... } / 42

        data object None : DefaultExport // no export default
    }

    internal fun classifyDefaultExport(rootNode: TSNode, sourceCode: String): DefaultExport = rootNode
        .children()
        .filter { it.type == EXPORT_STATEMENT }
        .firstNotNullOfOrNull { exportNode ->
            val children = exportNode.children().toList()
            if (!children.any { it.type == DEFAULT_KEYWORD }) return@firstNotNullOfOrNull null
            val identifier = children.firstOrNull { it.type == IDENTIFIER }
            if (identifier != null) {
                return@firstNotNullOfOrNull DefaultExport.Reexport(TreeTraversal.getNodeText(identifier, sourceCode).trim())
            }
            val declarationChild = children.firstOrNull { it.type in DECLARATION_NODE_TYPES }
            if (declarationChild != null) {
                val name = extractName(declarationChild, sourceCode)
                return@firstNotNullOfOrNull if (name.isNotBlank()) DefaultExport.Named(name) else DefaultExport.Anonymous
            }
            DefaultExport.Value
        } ?: DefaultExport.None

    fun extract(rootNode: TSNode, sourceCode: String): List<Declaration> {
        val aliasMap = buildAliasMap(rootNode, sourceCode)
        val localDeclarationNames = extractLocalDeclarationNames(rootNode, sourceCode)
        val exportReferencedLocalNames = collectExportReferencedLocalNames(rootNode, sourceCode)
        val declarations = rootNode
            .children()
            .flatMap { child ->
                when (child.type) {
                    EXPORT_STATEMENT -> extractFromExportStatement(
                        child,
                        sourceCode,
                        aliasMap = aliasMap,
                        localDeclarationNames = localDeclarationNames
                    )
                    AMBIENT_DECLARATION -> extractFromAmbientDeclaration(
                        child,
                        sourceCode,
                        aliasMap = aliasMap,
                        localDeclarationNames = localDeclarationNames
                    )
                    in DECLARATION_NODE_TYPES -> {
                        if (declarationNamesIncludes(child, sourceCode, exportReferencedLocalNames)) {
                            extractFromNode(child, sourceCode, aliasMap = aliasMap, localDeclarationNames = localDeclarationNames)
                        } else {
                            emptyList()
                        }
                    }
                    else -> emptyList()
                }
            }.filter { it.name.isNotBlank() }
            .toList()
        return when (val shape = classifyDefaultExport(rootNode, sourceCode)) {
            is DefaultExport.Reexport ->
                declarations + Declaration(DEFAULT_EXPORT, DeclarationType.REEXPORT, setOf(UsedType(shape.name)))
            is DefaultExport.Anonymous, DefaultExport.Value ->
                declarations + Declaration(DEFAULT_EXPORT, DeclarationType.REEXPORT, emptySet())
            is DefaultExport.Named, DefaultExport.None -> declarations
        }
    }

    private fun extractFromAmbientDeclaration(
        node: TSNode,
        sourceCode: String,
        aliasMap: Map<String, String> = emptyMap(),
        localDeclarationNames: Set<String> = emptySet()
    ): List<Declaration> {
        val moduleNode = node.children().firstOrNull { it.type == MODULE_DECLARATION } ?: return emptyList()
        val moduleName = moduleNode
            .children()
            .firstOrNull { it.type == STRING }
            ?.children()
            ?.firstOrNull { it.type == STRING_FRAGMENT }
            ?.let { TreeTraversal.getNodeText(it, sourceCode).trim() }
            ?: return emptyList()
        if (moduleName.contains("*")) return emptyList()
        val parentPath = moduleName.split("/")
        val body = moduleNode.children().firstOrNull { it.type == STATEMENT_BLOCK } ?: return emptyList()
        return body
            .children()
            .flatMap { child ->
                when (child.type) {
                    EXPORT_STATEMENT -> extractFromExportStatement(child, sourceCode, parentPath, aliasMap, localDeclarationNames)
                    in DECLARATION_NODE_TYPES -> extractFromNode(child, sourceCode, parentPath, aliasMap, localDeclarationNames)
                    else -> emptyList()
                }
            }.filter { it.name.isNotBlank() }
            .toList()
    }

    private fun extractFromExportStatement(
        node: TSNode,
        sourceCode: String,
        parentPath: List<String> = emptyList(),
        aliasMap: Map<String, String> = emptyMap(),
        localDeclarationNames: Set<String> = emptySet()
    ): List<Declaration> {
        val hasExportClause = node.children().any { it.type == EXPORT_CLAUSE }
        val hasSource = node.children().any { it.type == STRING }
        return when {
            hasExportClause && hasSource -> extractReexportDeclarations(node, sourceCode)
            hasSource && !hasExportClause ->
                listOf(Declaration(name = WILDCARD_REEXPORT, type = DeclarationType.REEXPORT, usedTypes = emptySet()))

            else ->
                node
                    .children()
                    .filter { it.type in DECLARATION_NODE_TYPES }
                    .flatMap { extractFromNode(it, sourceCode, parentPath, aliasMap, localDeclarationNames) }
                    .toList()
        }
    }

    private fun extractReexportDeclarations(node: TSNode, sourceCode: String): List<Declaration> {
        val exportClause = node.children().firstOrNull { it.type == EXPORT_CLAUSE } ?: return emptyList()
        return exportClause
            .children()
            .filter { it.type == EXPORT_SPECIFIER }
            .mapNotNull { specifier ->
                val identifiers = specifier
                    .children()
                    .filter { it.type == IDENTIFIER }
                    .map { TreeTraversal.getNodeText(it, sourceCode).trim() }
                    .toList()
                when (identifiers.size) {
                    1 -> {
                        val name = identifiers[0]
                        val usedTypeName = if (name == DEFAULT_KEYWORD) DEFAULT_EXPORT else name
                        Declaration(name = name, type = DeclarationType.REEXPORT, usedTypes = setOf(UsedType(usedTypeName)))
                    }

                    2 -> {
                        val originalName = identifiers[0]
                        val alias = identifiers[1]
                        val usedTypeName = if (originalName == DEFAULT_KEYWORD) DEFAULT_EXPORT else originalName
                        Declaration(name = alias, type = DeclarationType.REEXPORT, usedTypes = setOf(UsedType(usedTypeName)))
                    }

                    else -> null
                }
            }.toList()
    }

    private fun extractFromNode(
        node: TSNode,
        sourceCode: String,
        parentPath: List<String> = emptyList(),
        aliasMap: Map<String, String> = emptyMap(),
        localDeclarationNames: Set<String> = emptySet()
    ): List<Declaration> = when (node.type) {
        LEXICAL_DECLARATION, VARIABLE_DECLARATION -> extractVariableDeclarations(
            node,
            sourceCode,
            parentPath,
            aliasMap,
            localDeclarationNames
        )
        INTERNAL_MODULE -> {
            val name = extractName(node, sourceCode)
            listOf(Declaration(name = name, type = DeclarationType.UNKNOWN, usedTypes = emptySet(), parentPath = parentPath))
        }
        else -> {
            val name = extractName(node, sourceCode)
            val type = declarationType(node.type)
            val usedTypes = UsedTypeExtractor.extract(node, sourceCode, aliasMap, localDeclarationNames - name)
            listOf(Declaration(name = name, type = type, usedTypes = usedTypes, parentPath = parentPath))
        }
    }

    private fun extractVariableDeclarations(
        node: TSNode,
        sourceCode: String,
        parentPath: List<String> = emptyList(),
        aliasMap: Map<String, String> = emptyMap(),
        localDeclarationNames: Set<String> = emptySet()
    ): List<Declaration> = node
        .children()
        .filter { it.type == VARIABLE_DECLARATOR }
        .mapNotNull { declarator ->
            val name = TreeTraversal.findFirstChildTextByType(declarator, sourceCode, IDENTIFIER)?.trim()
            if (name.isNullOrBlank()) {
                null
            } else {
                Declaration(
                    name = name,
                    type = DeclarationType.VARIABLE,
                    usedTypes = UsedTypeExtractor.extract(declarator, sourceCode, aliasMap, localDeclarationNames - name),
                    parentPath = parentPath
                )
            }
        }.toList()

    private fun extractName(node: TSNode, sourceCode: String): String {
        val nameTypes = when (node.type) {
            CLASS_DECLARATION, ABSTRACT_CLASS_DECLARATION,
            INTERFACE_DECLARATION, TYPE_ALIAS_DECLARATION -> arrayOf(TYPE_IDENTIFIER, IDENTIFIER)
            else -> arrayOf(IDENTIFIER)
        }
        return TreeTraversal.findFirstChildTextByType(node, sourceCode, *nameTypes)?.trim() ?: ""
    }

    private fun declarationType(nodeType: String): DeclarationType = when (nodeType) {
        CLASS_DECLARATION, ABSTRACT_CLASS_DECLARATION, TYPE_ALIAS_DECLARATION -> DeclarationType.CLASS
        INTERFACE_DECLARATION -> DeclarationType.INTERFACE
        ENUM_DECLARATION -> DeclarationType.ENUM
        FUNCTION_DECLARATION, FUNCTION_SIGNATURE, GENERATOR_FUNCTION_DECLARATION -> DeclarationType.FUNCTION
        LEXICAL_DECLARATION, VARIABLE_DECLARATION -> DeclarationType.VARIABLE
        else -> DeclarationType.UNKNOWN
    }

    private fun extractLocalDeclarationNames(rootNode: TSNode, sourceCode: String): Set<String> = rootNode
        .children()
        .flatMap { child ->
            when (child.type) {
                EXPORT_STATEMENT -> extractNamesFromExportStatement(child, sourceCode)
                else -> emptyList()
            }
        }.filter { it.isNotBlank() }
        .toSet()

    private fun extractNamesFromExportStatement(node: TSNode, sourceCode: String): List<String> {
        val hasExportClause = node.children().any { it.type == EXPORT_CLAUSE }
        val hasSource = node.children().any { it.type == STRING }
        if (hasSource) return emptyList()
        if (hasExportClause) {
            val clause = node.children().firstOrNull { it.type == EXPORT_CLAUSE } ?: return emptyList()
            return clause
                .children()
                .filter { it.type == EXPORT_SPECIFIER }
                .mapNotNull { specifier ->
                    specifier
                        .children()
                        .filter { it.type == IDENTIFIER }
                        .lastOrNull()
                        ?.let { TreeTraversal.getNodeText(it, sourceCode).trim() }
                }.toList()
        }
        return node
            .children()
            .filter { it.type in DECLARATION_NODE_TYPES }
            .flatMap { extractNamesFromNode(it, sourceCode) }
            .toList()
    }

    private fun extractNamesFromNode(node: TSNode, sourceCode: String): List<String> {
        if (node.type == LEXICAL_DECLARATION || node.type == VARIABLE_DECLARATION) {
            return node
                .children()
                .filter { it.type == VARIABLE_DECLARATOR }
                .mapNotNull { TreeTraversal.findFirstChildTextByType(it, sourceCode, IDENTIFIER)?.trim() }
                .filter { it.isNotBlank() }
                .toList()
        }
        val name = extractName(node, sourceCode)
        return if (name.isNotBlank()) listOf(name) else emptyList()
    }

    private fun buildAliasMap(rootNode: TSNode, sourceCode: String): Map<String, String> {
        val aliasMap = mutableMapOf<String, String>()
        TreeTraversal.findAllDescendantsOfType(rootNode, IMPORT_STATEMENT).forEach { importNode ->
            val importClause = importNode.children().firstOrNull { it.type == IMPORT_CLAUSE } ?: return@forEach
            val defaultBinding = importClause.children().firstOrNull { it.type == IDENTIFIER }
            if (defaultBinding != null) {
                val name = TreeTraversal.getNodeText(defaultBinding, sourceCode).trim()
                if (name.isNotBlank()) aliasMap[name] = name
            }
            val namedImports = importClause.children().firstOrNull { it.type == NAMED_IMPORTS } ?: return@forEach
            namedImports
                .children()
                .filter { it.type == IMPORT_SPECIFIER }
                .forEach { specifier ->
                    val identifiers = specifier
                        .children()
                        .filter { it.type == IDENTIFIER }
                        .map { TreeTraversal.getNodeText(it, sourceCode).trim() }
                        .toList()
                    when (identifiers.size) {
                        1 -> aliasMap[identifiers[0]] = identifiers[0]
                        2 -> aliasMap[identifiers[1]] = normalizeDefaultKeyword(identifiers[0])
                    }
                }
        }
        return aliasMap
    }

    private fun normalizeDefaultKeyword(name: String): String = if (name == DEFAULT_KEYWORD) DEFAULT_EXPORT else name

    private fun collectExportReferencedLocalNames(rootNode: TSNode, sourceCode: String): Set<String> {
        val fromExportClauses = rootNode
            .children()
            .filter { it.type == EXPORT_STATEMENT }
            .flatMap { node -> exportClauseOriginalNames(node, sourceCode) }
            .toSet()
        val fromDefaultExport = when (val shape = classifyDefaultExport(rootNode, sourceCode)) {
            is DefaultExport.Reexport -> setOf(shape.name)
            else -> emptySet()
        }
        return fromExportClauses + fromDefaultExport + extractCJSExportedNames(rootNode, sourceCode)
    }

    private fun exportClauseOriginalNames(node: TSNode, sourceCode: String): List<String> {
        val hasSource = node.children().any { it.type == STRING }
        val hasExportClause = node.children().any { it.type == EXPORT_CLAUSE }
        if (hasSource || !hasExportClause) return emptyList()
        val clause = node.children().firstOrNull { it.type == EXPORT_CLAUSE } ?: return emptyList()
        return clause
            .children()
            .filter { it.type == EXPORT_SPECIFIER }
            .mapNotNull { specifier ->
                val name = specifier
                    .children()
                    .filter { it.type == IDENTIFIER }
                    .firstOrNull()
                    ?.let { TreeTraversal.getNodeText(it, sourceCode).trim() }
                    ?: return@mapNotNull null
                if (name == DEFAULT_KEYWORD) null else name
            }.toList()
    }

    private fun extractCJSExportedNames(rootNode: TSNode, sourceCode: String): Set<String> {
        return rootNode
            .children()
            .filter { it.type == EXPRESSION_STATEMENT }
            .mapNotNull { stmt ->
                val assignment = stmt.children().firstOrNull { it.type == ASSIGNMENT_EXPRESSION }
                    ?: return@mapNotNull null
                val children = assignment.children().toList()
                val left = children.firstOrNull { it.type == MEMBER_EXPRESSION } ?: return@mapNotNull null
                val right = children.lastOrNull { it.type == IDENTIFIER } ?: return@mapNotNull null
                val leftText = TreeTraversal.getNodeText(left, sourceCode).trim()
                if (!leftText.startsWith("module.exports") && !leftText.startsWith("exports.")) {
                    return@mapNotNull null
                }
                TreeTraversal.getNodeText(right, sourceCode).trim().takeIf { it.isNotBlank() }
            }.toSet()
    }

    private fun declarationNamesIncludes(node: TSNode, sourceCode: String, names: Set<String>): Boolean {
        if (names.isEmpty()) return false
        return when (node.type) {
            LEXICAL_DECLARATION, VARIABLE_DECLARATION ->
                node
                    .children()
                    .filter { it.type == VARIABLE_DECLARATOR }
                    .any { declarator ->
                        val name = TreeTraversal.findFirstChildTextByType(declarator, sourceCode, IDENTIFIER)?.trim()
                        !name.isNullOrBlank() && name in names
                    }
            else -> extractName(node, sourceCode) in names
        }
    }
}
