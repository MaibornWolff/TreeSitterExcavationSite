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
    private const val TYPE_FIELD = "type"
    private const val TYPE_DEFINITION = "type_definition"
    private const val ALIAS_DECLARATION = "alias_declaration"
    private const val TEMPLATE_DECLARATION = "template_declaration"
    private const val REQUIRES_CLAUSE = "requires_clause"
    private const val CONSTRAINT_FIELD = "constraint"
    private const val CONSTRAINT_DISJUNCTION = "constraint_disjunction"
    private const val CONSTRAINT_CONJUNCTION = "constraint_conjunction"
    private const val LEFT_FIELD = "left"
    private const val RIGHT_FIELD = "right"
    private const val FIELD_DECLARATION = "field_declaration"
    private const val DECLARATION = "declaration"
    private const val CAST_EXPRESSION = "cast_expression"
    private const val NEW_EXPRESSION = "new_expression"
    private const val TEMPLATE_FUNCTION = "template_function"
    private const val TEMPLATE_ARGUMENT_LIST = "template_argument_list"
    private const val ARGUMENTS_FIELD = "arguments"
    private const val FUNCTION_FIELD = "function"

    private val ALL_NODE_TYPES = setOf(
        BASE_CLASS_CLAUSE,
        FUNCTION_DEFINITION,
        FIELD_INITIALIZER_LIST,
        TYPE_DEFINITION,
        ALIAS_DECLARATION,
        FIELD_DECLARATION,
        DECLARATION,
        CAST_EXPRESSION,
        CALL_EXPRESSION,
        NEW_EXPRESSION
    )

    fun extract(declaration: TSNode, sourceCode: String): Set<UsedType> {
        val buckets = TreeTraversal.findAllDescendantsGroupedByType(declaration, ALL_NODE_TYPES)
        val inheritance = extractInheritanceTypes(buckets, sourceCode)
        val methodTypes = extractMethodReturnAndParamTypes(buckets, sourceCode)
        val initializerTypes = extractConstructorInitializerTypes(buckets, sourceCode)
        val aliasTypes =
            (buckets[TYPE_DEFINITION].orEmpty() + buckets[ALIAS_DECLARATION].orEmpty())
                .mapNotNull { extractTypeFromTypeField(it, sourceCode) }
        val constraintTypes = extractTemplateConstraintTypes(declaration, sourceCode)
        val fieldAndVariableTypes =
            (buckets[FIELD_DECLARATION].orEmpty() + buckets[DECLARATION].orEmpty())
                .mapNotNull { extractTypeFromTypeField(it, sourceCode) }
        val cStyleCasts = buckets[CAST_EXPRESSION].orEmpty().mapNotNull { extractTypeFromTypeField(it, sourceCode) }
        val instantiationTypes = extractInstantiationTypes(buckets, sourceCode)
        return (
            inheritance + methodTypes + initializerTypes + aliasTypes +
                constraintTypes + fieldAndVariableTypes + cStyleCasts + instantiationTypes
        ).toSet()
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
                .flatMap { fieldInit ->
                    val argList = fieldInit.children().firstOrNull { it.type == ARGUMENT_LIST || it.type == INITIALIZER_LIST }
                    if (argList == null) emptySequence() else collectInitializerArgumentTypes(argList, sourceCode)
                }.toList()
        }

    private fun collectInitializerArgumentTypes(argList: TSNode, sourceCode: String): Sequence<UsedType> {
        val isBraceInit = argList.type == INITIALIZER_LIST
        return argList.namedChildren().flatMap { arg ->
            when (arg.type) {
                QUALIFIED_IDENTIFIER ->
                    listOfNotNull(extractInitializerTypeFromQualifiedIdentifier(arg, sourceCode)).asSequence()
                CALL_EXPRESSION ->
                    if (isBraceInit) {
                        emptySequence()
                    } else {
                        arg
                            .children()
                            .filter { it.type == QUALIFIED_IDENTIFIER }
                            .mapNotNull { extractInitializerTypeFromQualifiedIdentifier(it, sourceCode) }
                    }
                else -> emptySequence()
            }
        }
    }

    private fun extractTypeFromTypeField(node: TSNode, sourceCode: String): UsedType? {
        val typeField = node.getChildByFieldName(TYPE_FIELD).takeIf { !it.isNull }
            ?: node.namedChildren().firstOrNull { CppTypeHelper.isTypeNode(it) || it.type == TYPE_DESCRIPTOR }
            ?: return null
        val unwrapped = if (typeField.type == TYPE_DESCRIPTOR) {
            typeField.namedChildren().firstOrNull { CppTypeHelper.isTypeNode(it) } ?: return null
        } else {
            typeField
        }
        if (!CppTypeHelper.isTypeNode(unwrapped)) return null
        return CppTypeHelper.extractType(unwrapped, sourceCode)
    }

    private fun extractTemplateConstraintTypes(declaration: TSNode, sourceCode: String): List<UsedType> {
        val parent = declaration.parent
        if (parent.isNull || parent.type != TEMPLATE_DECLARATION) return emptyList()
        val requiresClause = parent.children().firstOrNull { it.type == REQUIRES_CLAUSE } ?: return emptyList()
        val constraint = requiresClause.getChildByFieldName(CONSTRAINT_FIELD).takeIf { !it.isNull } ?: return emptyList()
        return collectConstraintTypes(constraint, sourceCode)
    }

    private fun collectConstraintTypes(node: TSNode, sourceCode: String): List<UsedType> {
        if (node.type == CONSTRAINT_DISJUNCTION || node.type == CONSTRAINT_CONJUNCTION) {
            val left = node.getChildByFieldName(LEFT_FIELD).takeIf { !it.isNull }
            val right = node.getChildByFieldName(RIGHT_FIELD).takeIf { !it.isNull }
            return listOfNotNull(left, right).flatMap { collectConstraintTypes(it, sourceCode) }
        }
        if (CppTypeHelper.isTypeNode(node)) {
            return listOfNotNull(CppTypeHelper.extractType(node, sourceCode))
        }
        return node
            .namedChildren()
            .filter { CppTypeHelper.isTypeNode(it) }
            .mapNotNull { CppTypeHelper.extractType(it, sourceCode) }
            .toList()
    }

    private fun extractInstantiationTypes(buckets: Map<String, List<TSNode>>, sourceCode: String): List<UsedType> {
        val newTypes = buckets[NEW_EXPRESSION].orEmpty().mapNotNull { extractTypeFromTypeField(it, sourceCode) }
        val callTypes = buckets[CALL_EXPRESSION].orEmpty().flatMap { call ->
            val function = call.getChildByFieldName(FUNCTION_FIELD).takeIf { !it.isNull } ?: return@flatMap emptyList()
            when (function.type) {
                TEMPLATE_FUNCTION -> extractTemplateArgumentTypes(function, sourceCode)
                QUALIFIED_IDENTIFIER -> listOfNotNull(extractRightmostTypeFromQualifiedIdentifier(function, sourceCode))
                else -> emptyList()
            }
        }
        return newTypes + callTypes
    }

    private fun extractTemplateArgumentTypes(templateFunction: TSNode, sourceCode: String): List<UsedType> {
        val argList = templateFunction
            .getChildByFieldName(ARGUMENTS_FIELD)
            .takeIf { !it.isNull && it.type == TEMPLATE_ARGUMENT_LIST }
            ?: return emptyList()
        return argList
            .namedChildren()
            .mapNotNull { arg ->
                val inner = if (arg.type == TYPE_DESCRIPTOR) {
                    arg.namedChildren().firstOrNull { CppTypeHelper.isTypeNode(it) }
                } else {
                    arg.takeIf { CppTypeHelper.isTypeNode(it) }
                }
                inner?.let { CppTypeHelper.extractType(it, sourceCode) }
            }.toList()
    }

    private fun extractRightmostTypeFromQualifiedIdentifier(qualifiedId: TSNode, sourceCode: String): UsedType? {
        var node = qualifiedId
        while (node.type == QUALIFIED_IDENTIFIER) {
            val nameField = node.getChildByFieldName(NAME_FIELD)
            if (nameField.isNull) return null
            node = nameField
        }
        val text = TreeTraversal.getNodeText(node, sourceCode).trim()
        if (text.isEmpty()) return null
        return UsedType(name = text)
    }

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
