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
    private const val STATEMENT_BLOCK = "statement_block"

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
        VARIABLE_DECLARATION
    )

    internal fun findInlineDefaultExportName(rootNode: TSNode, sourceCode: String): String? {
        return rootNode
            .children()
            .filter { it.type == EXPORT_STATEMENT }
            .firstNotNullOfOrNull { exportNode ->
                val children = exportNode.children().toList()
                if (!children.any { it.type == DEFAULT_KEYWORD }) return@firstNotNullOfOrNull null
                val declarationChild = children.firstOrNull { it.type in DECLARATION_NODE_TYPES }
                    ?: return@firstNotNullOfOrNull null
                extractName(declarationChild, sourceCode).takeIf { it.isNotBlank() }
            }
    }

    fun extract(rootNode: TSNode, sourceCode: String): List<Declaration> {
        val aliasMap = buildAliasMap(rootNode, sourceCode)
        val defaultExportIdentifier = findDefaultExportIdentifier(rootNode, sourceCode)
        val declarations = rootNode
            .children()
            .flatMap { child ->
                when (child.type) {
                    EXPORT_STATEMENT -> extractFromExportStatement(child, sourceCode, aliasMap = aliasMap)
                    AMBIENT_DECLARATION -> extractFromAmbientDeclaration(child, sourceCode, aliasMap = aliasMap)
                    in DECLARATION_NODE_TYPES -> extractFromNode(child, sourceCode, aliasMap = aliasMap)
                    else -> emptyList()
                }
            }.filter { it.name.isNotBlank() }
            .toList()
        return when {
            defaultExportIdentifier != null ->
                declarations + Declaration(
                    name = DEFAULT_EXPORT,
                    type = DeclarationType.REEXPORT,
                    usedTypes = setOf(UsedType(defaultExportIdentifier))
                )
            hasValueDefaultExport(rootNode) || hasAnonymousDefaultDeclaration(rootNode, sourceCode) ->
                declarations + Declaration(
                    name = DEFAULT_EXPORT,
                    type = DeclarationType.REEXPORT,
                    usedTypes = emptySet()
                )
            else -> declarations
        }
    }

    private fun findDefaultExportIdentifier(rootNode: TSNode, sourceCode: String): String? = rootNode
        .children()
        .filter { it.type == EXPORT_STATEMENT }
        .firstNotNullOfOrNull { exportNode ->
            val children = exportNode.children().toList()
            val identifier = children.firstOrNull { it.type == IDENTIFIER }
            if (children.any { it.type == DEFAULT_KEYWORD } && identifier != null) {
                TreeTraversal.getNodeText(identifier, sourceCode).trim()
            } else {
                null
            }
        }

    private fun hasValueDefaultExport(rootNode: TSNode): Boolean = rootNode
        .children()
        .filter { it.type == EXPORT_STATEMENT }
        .any { exportNode ->
            val children = exportNode.children().toList()
            children.any { it.type == DEFAULT_KEYWORD } &&
                children.none { it.type in DECLARATION_NODE_TYPES }
        }

    private fun hasAnonymousDefaultDeclaration(rootNode: TSNode, sourceCode: String): Boolean = rootNode
        .children()
        .filter { it.type == EXPORT_STATEMENT }
        .any { exportNode ->
            val children = exportNode.children().toList()
            val declarationChild = children.firstOrNull { it.type in DECLARATION_NODE_TYPES }
            children.any { it.type == DEFAULT_KEYWORD } &&
                declarationChild != null &&
                extractName(declarationChild, sourceCode).isBlank()
        }

    private fun extractFromAmbientDeclaration(
        node: TSNode,
        sourceCode: String,
        aliasMap: Map<String, String> = emptyMap()
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
                    EXPORT_STATEMENT -> extractFromExportStatement(child, sourceCode, parentPath, aliasMap)
                    in DECLARATION_NODE_TYPES -> extractFromNode(child, sourceCode, parentPath, aliasMap)
                    else -> emptyList()
                }
            }.filter { it.name.isNotBlank() }
            .toList()
    }

    private fun extractFromExportStatement(
        node: TSNode,
        sourceCode: String,
        parentPath: List<String> = emptyList(),
        aliasMap: Map<String, String> = emptyMap()
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
                    .flatMap { extractFromNode(it, sourceCode, parentPath, aliasMap) }
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
                        Declaration(name = name, type = DeclarationType.REEXPORT, usedTypes = setOf(UsedType(name)))
                    }

                    2 -> {
                        val originalName = identifiers[0]
                        val alias = identifiers[1]
                        val usedTypeName = if (originalName == "default") DEFAULT_EXPORT else originalName
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
        aliasMap: Map<String, String> = emptyMap()
    ): List<Declaration> = when (node.type) {
        LEXICAL_DECLARATION, VARIABLE_DECLARATION -> extractVariableDeclarations(node, sourceCode, parentPath, aliasMap)
        else -> {
            val name = extractName(node, sourceCode)
            val type = declarationType(node.type)
            val usedTypes = UsedTypeExtractor.extract(node, sourceCode, aliasMap)
            listOf(Declaration(name = name, type = type, usedTypes = usedTypes, parentPath = parentPath))
        }
    }

    private fun extractVariableDeclarations(
        node: TSNode,
        sourceCode: String,
        parentPath: List<String> = emptyList(),
        aliasMap: Map<String, String> = emptyMap()
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
                    usedTypes = UsedTypeExtractor.extract(declarator, sourceCode, aliasMap),
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

    private fun buildAliasMap(rootNode: TSNode, sourceCode: String): Map<String, String> {
        val aliasMap = mutableMapOf<String, String>()
        TreeTraversal.findAllDescendantsOfType(rootNode, IMPORT_STATEMENT).forEach { importNode ->
            val importClause = importNode.children().firstOrNull { it.type == IMPORT_CLAUSE } ?: return@forEach
            val defaultBinding = importClause.children().firstOrNull { it.type == IDENTIFIER }
            if (defaultBinding != null) {
                val name = TreeTraversal.getNodeText(defaultBinding, sourceCode).trim()
                if (name.isNotBlank()) aliasMap[name] = DEFAULT_EXPORT
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
                    if (identifiers.size == 2) {
                        val original = identifiers[0]
                        val alias = identifiers[1]
                        aliasMap[alias] = original
                    }
                }
        }
        return aliasMap
    }
}
