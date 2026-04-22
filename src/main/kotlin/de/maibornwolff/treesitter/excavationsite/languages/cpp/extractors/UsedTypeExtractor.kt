package de.maibornwolff.treesitter.excavationsite.languages.cpp.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.UsedType
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.namedChildren
import org.treesitter.TSNode

internal object UsedTypeExtractor {
    private const val BASE_CLASS_CLAUSE = "base_class_clause"
    private const val FUNCTION_DEFINITION = "function_definition"
    private const val FUNCTION_DECLARATOR = "function_declarator"
    private const val PARAMETER_LIST = "parameter_list"
    private const val PARAMETER_DECLARATION = "parameter_declaration"
    private const val TRAILING_RETURN_TYPE = "trailing_return_type"
    private const val TYPE_DESCRIPTOR = "type_descriptor"
    private const val FIELD_INITIALIZER_LIST = "field_initializer_list"
    private const val FIELD_INITIALIZER = "field_initializer"
    private const val ARGUMENT_LIST = "argument_list"
    private const val INITIALIZER_LIST = "initializer_list"
    private const val QUALIFIED_IDENTIFIER = "qualified_identifier"
    private const val CALL_EXPRESSION = "call_expression"
    private const val SCOPE_FIELD = "scope"
    private const val NAME_FIELD = "name"

    private val ALL_NODE_TYPES = setOf(BASE_CLASS_CLAUSE, FUNCTION_DEFINITION, FIELD_INITIALIZER_LIST)

    fun extract(declaration: TSNode, sourceCode: String): Set<UsedType> {
        val buckets = TreeTraversal.findAllDescendantsGroupedByType(declaration, ALL_NODE_TYPES)
        val inheritance = extractInheritanceTypes(buckets, sourceCode)
        val methodTypes = extractMethodReturnAndParamTypes(buckets, sourceCode)
        val initializerTypes = extractConstructorInitializerTypes(buckets, sourceCode)
        return (inheritance + methodTypes + initializerTypes).toSet()
    }

    private fun extractInheritanceTypes(buckets: Map<String, List<TSNode>>, sourceCode: String): List<UsedType> =
        buckets[BASE_CLASS_CLAUSE].orEmpty().flatMap { baseClause ->
            baseClause
                .namedChildren()
                .filter { CppTypeHelper.isTypeNode(it) }
                .mapNotNull { CppTypeHelper.extractType(it, sourceCode) }
                .toList()
        }

    private fun extractMethodReturnAndParamTypes(buckets: Map<String, List<TSNode>>, sourceCode: String): List<UsedType> =
        buckets[FUNCTION_DEFINITION].orEmpty().flatMap { fnDef ->
            val leadingReturnType = fnDef
                .children()
                .takeWhile { it.type != FUNCTION_DECLARATOR }
                .firstOrNull { CppTypeHelper.isTypeNode(it) }
                ?.let { CppTypeHelper.extractType(it, sourceCode) }
            val fnDeclarator = fnDef.children().firstOrNull { it.type == FUNCTION_DECLARATOR }
            val paramTypes = fnDeclarator?.let { extractParameterTypes(it, sourceCode) } ?: emptyList()
            val trailingReturnType = fnDeclarator?.let { extractTrailingReturnType(it, sourceCode) }
            paramTypes + listOfNotNull(leadingReturnType, trailingReturnType)
        }

    private fun extractParameterTypes(fnDeclarator: TSNode, sourceCode: String): List<UsedType> {
        val paramList = fnDeclarator.children().firstOrNull { it.type == PARAMETER_LIST } ?: return emptyList()
        return paramList
            .children()
            .filter { it.type == PARAMETER_DECLARATION }
            .mapNotNull { param ->
                val typeNode = param.children().firstOrNull { CppTypeHelper.isTypeNode(it) }
                typeNode?.let { CppTypeHelper.extractType(it, sourceCode) }
            }.toList()
    }

    private fun extractTrailingReturnType(fnDeclarator: TSNode, sourceCode: String): UsedType? {
        val trailing = fnDeclarator.children().firstOrNull { it.type == TRAILING_RETURN_TYPE } ?: return null
        val typeDescriptor = trailing.children().firstOrNull { it.type == TYPE_DESCRIPTOR } ?: return null
        val typeNode = typeDescriptor.namedChildren().firstOrNull { CppTypeHelper.isTypeNode(it) } ?: return null
        return CppTypeHelper.extractType(typeNode, sourceCode)
    }

    private fun extractConstructorInitializerTypes(buckets: Map<String, List<TSNode>>, sourceCode: String): List<UsedType> =
        buckets[FIELD_INITIALIZER_LIST].orEmpty().flatMap { initList ->
            initList
                .children()
                .filter { it.type == FIELD_INITIALIZER }
                .flatMap { fieldInit -> extractTypesFromFieldInitializer(fieldInit, sourceCode) }
                .toList()
        }

    private fun extractTypesFromFieldInitializer(fieldInit: TSNode, sourceCode: String): Sequence<UsedType> {
        val argList = fieldInit.children().firstOrNull { it.type == ARGUMENT_LIST || it.type == INITIALIZER_LIST }
            ?: return emptySequence()
        val isBraceInit = argList.type == INITIALIZER_LIST
        return argList.namedChildren().flatMap { arg ->
            when (arg.type) {
                QUALIFIED_IDENTIFIER -> listOfNotNull(extractInitializerTypeFromQualifiedIdentifier(arg, sourceCode)).asSequence()
                CALL_EXPRESSION -> if (isBraceInit) emptySequence() else extractTypesFromCallExpression(arg, sourceCode)
                else -> emptySequence()
            }
        }
    }

    private fun extractTypesFromCallExpression(call: TSNode, sourceCode: String): Sequence<UsedType> = call
        .children()
        .filter { it.type == QUALIFIED_IDENTIFIER }
        .mapNotNull { extractInitializerTypeFromQualifiedIdentifier(it, sourceCode) }

    private fun extractInitializerTypeFromQualifiedIdentifier(qualifiedId: TSNode, sourceCode: String): UsedType? {
        val scopeSegments = mutableListOf<String>()
        var node = qualifiedId
        while (node.type == QUALIFIED_IDENTIFIER) {
            val scope = node.getChildByFieldName(SCOPE_FIELD)
            if (!scope.isNull) {
                scopeSegments.add(TreeTraversal.getNodeText(scope, sourceCode).trim())
            }
            node = node.getChildByFieldName(NAME_FIELD)
            if (node.isNull) break
        }
        return scopeSegments.lastOrNull()?.let { UsedType(name = it) }
    }
}
