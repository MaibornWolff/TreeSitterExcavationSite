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
    private const val FRIEND_DECLARATION = "friend_declaration"
    private const val USING_DECLARATION = "using_declaration"
    private const val SIZEOF_EXPRESSION = "sizeof_expression"
    private const val ALIGNOF_EXPRESSION = "alignof_expression"
    private const val THROW_STATEMENT = "throw_statement"
    private const val CLASS_SPECIFIER = "class_specifier"
    private const val STRUCT_SPECIFIER = "struct_specifier"
    private const val UNION_SPECIFIER = "union_specifier"
    private const val ENUM_SPECIFIER = "enum_specifier"
    private const val IDENTIFIER = "identifier"
    private const val TYPE_IDENTIFIER = "type_identifier"

    private val DECLARATION_BOUNDARIES = setOf(CLASS_SPECIFIER, STRUCT_SPECIFIER, UNION_SPECIFIER, ENUM_SPECIFIER)

    private val ALL_NODE_TYPES = setOf(
        BASE_CLASS_CLAUSE,
        FUNCTION_DEFINITION,
        FUNCTION_DECLARATOR,
        FIELD_INITIALIZER_LIST,
        TYPE_DEFINITION,
        ALIAS_DECLARATION,
        FIELD_DECLARATION,
        DECLARATION,
        CAST_EXPRESSION,
        CALL_EXPRESSION,
        NEW_EXPRESSION,
        FRIEND_DECLARATION,
        USING_DECLARATION,
        SIZEOF_EXPRESSION,
        ALIGNOF_EXPRESSION,
        THROW_STATEMENT
    )

    private val TYPE_OPERAND_EXPRESSIONS = setOf(SIZEOF_EXPRESSION, ALIGNOF_EXPRESSION)

    private val CLASS_BODY_TYPES = setOf(CLASS_SPECIFIER, STRUCT_SPECIFIER, UNION_SPECIFIER)

    fun extract(declaration: TSNode, sourceCode: String): Set<UsedType> {
        val buckets = groupDescendantsStoppingAtNestedDeclarations(declaration, ALL_NODE_TYPES)
        val inheritance = extractInheritanceTypes(buckets, sourceCode)
        val methodTypes = extractMethodReturnAndParamTypes(buckets, sourceCode)
        val declaratorParamTypes = buckets[FUNCTION_DECLARATOR]
            .orEmpty()
            .flatMap { extractParameterTypes(it, sourceCode) }
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
        val friendAndUsingTypes = extractClassScopeUsageTypes(buckets, sourceCode)
        val typeOperandTypes = TYPE_OPERAND_EXPRESSIONS
            .flatMap { buckets[it].orEmpty() }
            .mapNotNull { extractTypeFromTypeField(it, sourceCode) }
        return (
            fieldAndVariableTypes + cStyleCasts + instantiationTypes + initializerTypes +
                typeOperandTypes + friendAndUsingTypes +
                inheritance +
                methodTypes + declaratorParamTypes +
                aliasTypes + constraintTypes
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
                    listOfNotNull(CppTypeHelper.extractSecondToLastSegment(arg, sourceCode)).asSequence()
                CALL_EXPRESSION ->
                    if (isBraceInit) {
                        emptySequence()
                    } else {
                        arg
                            .children()
                            .filter { it.type == QUALIFIED_IDENTIFIER }
                            .mapNotNull { CppTypeHelper.extractSecondToLastSegment(it, sourceCode) }
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
        val throwCallees = buckets[THROW_STATEMENT].orEmpty().mapNotNull { throwStmt ->
            throwStmt.namedChildren().firstOrNull { it.type == CALL_EXPRESSION }
        }
        val callTypes = (buckets[CALL_EXPRESSION].orEmpty() + throwCallees).flatMap { call ->
            val function = call.getChildByFieldName(FUNCTION_FIELD).takeIf { !it.isNull } ?: return@flatMap emptyList()
            val isInThrow = call.parent.takeIf { !it.isNull }?.type == THROW_STATEMENT
            when (function.type) {
                TEMPLATE_FUNCTION -> {
                    val generics = extractTemplateArgumentTypes(function, sourceCode)
                    if (isInThrow) {
                        val name = function
                            .getChildByFieldName("name")
                            .takeIf { !it.isNull }
                            ?.let { TreeTraversal.getNodeText(it, sourceCode).trim() }
                            .orEmpty()
                        if (name.isEmpty()) generics else generics + UsedType(name = name, genericTypes = generics)
                    } else {
                        generics
                    }
                }
                QUALIFIED_IDENTIFIER -> listOfNotNull(
                    CppTypeHelper.extractRightmostSegment(function, sourceCode),
                    CppTypeHelper.extractSingleSegmentScope(function, sourceCode)
                )
                IDENTIFIER -> if (isInThrow) {
                    val text = TreeTraversal.getNodeText(function, sourceCode).trim()
                    if (text.isEmpty()) emptyList() else listOf(UsedType(name = text))
                } else {
                    emptyList()
                }
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

    private fun extractClassScopeUsageTypes(buckets: Map<String, List<TSNode>>, sourceCode: String): List<UsedType> {
        val friendTypes = buckets[FRIEND_DECLARATION].orEmpty().flatMap { friend ->
            friend
                .namedChildren()
                .mapNotNull { child ->
                    when {
                        CppTypeHelper.isTypeNode(child) -> CppTypeHelper.extractType(child, sourceCode)
                        child.type == QUALIFIED_IDENTIFIER -> CppTypeHelper.extractRightmostSegment(child, sourceCode)
                        else -> null
                    }
                }.toList()
        }
        val inClassUsingTypes = buckets[USING_DECLARATION]
            .orEmpty()
            .filter { usingDecl ->
                var anc = usingDecl.parent
                var inside = false
                while (!inside && anc != null && !anc.isNull) {
                    if (anc.type in CLASS_BODY_TYPES) inside = true else anc = anc.parent
                }
                inside
            }.mapNotNull { usingDecl ->
                val qualified = usingDecl.namedChildren().firstOrNull { it.type == QUALIFIED_IDENTIFIER }
                if (qualified != null) {
                    CppTypeHelper.extractSecondToLastSegment(qualified, sourceCode)
                } else {
                    val plain = usingDecl.namedChildren().firstOrNull { it.type == IDENTIFIER || it.type == TYPE_IDENTIFIER }
                    plain?.let {
                        val text = TreeTraversal.getNodeText(it, sourceCode).trim()
                        if (text.isEmpty()) null else UsedType(name = text)
                    }
                }
            }
        return friendTypes + inClassUsingTypes
    }

    private fun groupDescendantsStoppingAtNestedDeclarations(root: TSNode, targetTypes: Set<String>): Map<String, List<TSNode>> {
        val buckets = mutableMapOf<String, MutableList<TSNode>>()
        if (!root.isNull && root.type in targetTypes) {
            buckets.getOrPut(root.type) { mutableListOf() }.add(root)
        }
        val stack = ArrayDeque<TSNode>()
        root.children().forEach(stack::addLast)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (!node.isNull) {
                if (node.type in targetTypes) {
                    buckets.getOrPut(node.type) { mutableListOf() }.add(node)
                }
                if (node.type !in DECLARATION_BOUNDARIES) {
                    node.children().forEach(stack::addLast)
                }
            }
        }
        return buckets
    }
}
