package de.maibornwolff.treesitter.excavationsite.languages.kotlin.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.UsedType
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.namedChildren
import org.treesitter.TSNode

internal object UsedTypeExtractor {
    // Declaration structure
    private const val DELEGATION_SPECIFIER = "delegation_specifier"
    private const val CONSTRUCTOR_INVOCATION = "constructor_invocation"
    private const val PROPERTY_DECLARATION = "property_declaration"
    private const val VARIABLE_DECLARATION = "variable_declaration"
    private const val CLASS_PARAMETER = "class_parameter"
    private const val PARAMETER = "parameter"
    private const val FUNCTION_DECLARATION = "function_declaration"
    private const val FUNCTION_VALUE_PARAMETERS = "function_value_parameters"
    private const val ANNOTATION = "annotation"

    // Call expressions
    private const val CALL_EXPRESSION = "call_expression"
    private const val NAVIGATION_EXPRESSION = "navigation_expression"
    private const val CALL_SUFFIX = "call_suffix"
    private const val SIMPLE_IDENTIFIER = "simple_identifier"

    // Type nodes
    private const val USER_TYPE = "user_type"
    private const val NULLABLE_TYPE = "nullable_type"
    private const val TYPE_IDENTIFIER = "type_identifier"
    private const val TYPE_ARGUMENTS = "type_arguments"
    private const val TYPE_PROJECTION = "type_projection"

    private val ALL_NODE_TYPES = setOf(
        DELEGATION_SPECIFIER,
        PROPERTY_DECLARATION,
        CLASS_PARAMETER,
        FUNCTION_DECLARATION,
        ANNOTATION,
        CALL_EXPRESSION,
        NAVIGATION_EXPRESSION
    )

    fun extract(declaration: TSNode, sourceCode: String): Set<UsedType> {
        val buckets = TreeTraversal.findAllDescendantsGroupedByType(declaration, ALL_NODE_TYPES)

        // DC concatenation order: inheritance, properties, parameters, returnTypes, annotations, constructorCalls, callExpressions
        val inheritanceTypes = extractInheritanceTypes(declaration, sourceCode)
        val propertyTypes = extractPropertyTypes(buckets, sourceCode)
        val parameterTypes = extractParameterTypes(buckets, sourceCode)
        val returnTypes = extractReturnTypes(buckets, sourceCode)
        val annotationTypes = extractAnnotationTypes(buckets, sourceCode)
        val constructorCallTypes = extractConstructorCallTypes(buckets, sourceCode)
        val callExpressionTypes = extractCallExpressionTypes(buckets, sourceCode)

        return (
            inheritanceTypes + propertyTypes + parameterTypes + returnTypes +
                annotationTypes + constructorCallTypes + callExpressionTypes
        ).toSet()
    }

    private fun extractInheritanceTypes(declaration: TSNode, sourceCode: String): List<UsedType> = declaration
        .children()
        .filter { it.type == DELEGATION_SPECIFIER }
        .mapNotNull { specifier ->
            val constructorInvocation = specifier.children().firstOrNull { it.type == CONSTRUCTOR_INVOCATION }
            if (constructorInvocation != null) {
                val userType = constructorInvocation.children().firstOrNull { it.type == USER_TYPE }
                userType?.let { extractType(it, sourceCode) }
            } else {
                val userType = specifier.children().firstOrNull { it.type == USER_TYPE }
                userType?.let { extractType(it, sourceCode) }
            }
        }.toList()

    private fun extractPropertyTypes(buckets: Map<String, List<TSNode>>, sourceCode: String): List<UsedType> {
        val fromProperties = buckets[PROPERTY_DECLARATION].orEmpty().mapNotNull { property ->
            val varDecl = property.children().firstOrNull { it.type == VARIABLE_DECLARATION }
            varDecl?.let { extractTypeFromTypedNode(it, sourceCode) }
        }
        val fromClassParams = buckets[CLASS_PARAMETER].orEmpty().mapNotNull { param ->
            extractTypeFromTypedNode(param, sourceCode)
        }
        return fromProperties + fromClassParams
    }

    private fun extractParameterTypes(buckets: Map<String, List<TSNode>>, sourceCode: String): List<UsedType> {
        return buckets[FUNCTION_DECLARATION].orEmpty().flatMap { function ->
            val params = function.children().firstOrNull { it.type == FUNCTION_VALUE_PARAMETERS }
                ?: return@flatMap emptyList()
            params
                .namedChildren()
                .filter { it.type == PARAMETER }
                .mapNotNull { extractTypeFromTypedNode(it, sourceCode) }
                .toList()
        }
    }

    private fun extractReturnTypes(buckets: Map<String, List<TSNode>>, sourceCode: String): List<UsedType> =
        buckets[FUNCTION_DECLARATION].orEmpty().mapNotNull { function ->
            extractReturnType(function, sourceCode)
        }

    private fun extractAnnotationTypes(buckets: Map<String, List<TSNode>>, sourceCode: String): List<UsedType> =
        buckets[ANNOTATION].orEmpty().mapNotNull { annotation ->
            val constructorInvocation = annotation.children().firstOrNull { it.type == CONSTRUCTOR_INVOCATION }
            if (constructorInvocation != null) {
                val userType = constructorInvocation.children().firstOrNull { it.type == USER_TYPE }
                userType?.let { extractType(it, sourceCode) }
            } else {
                val userType = annotation.children().firstOrNull { it.type == USER_TYPE }
                userType?.let { extractType(it, sourceCode) }
            }
        }

    private fun extractConstructorCallTypes(buckets: Map<String, List<TSNode>>, sourceCode: String): List<UsedType> {
        return buckets[CALL_EXPRESSION].orEmpty().mapNotNull { callExpr ->
            val firstChild = callExpr.getNamedChild(0)
            if (firstChild.isNull) return@mapNotNull null
            val name = TreeTraversal.getNodeText(firstChild, sourceCode).trim()
            if (name.firstOrNull()?.isUpperCase() != true) return@mapNotNull null
            if (firstChild.type == SIMPLE_IDENTIFIER) {
                val callSuffix = callExpr.children().firstOrNull { it.type == CALL_SUFFIX }
                val genericTypes = callSuffix?.let { extractGenericTypesFromCallSuffix(it, sourceCode) } ?: emptyList()
                UsedType(name = name, genericTypes = genericTypes)
            } else {
                UsedType(name = name)
            }
        }
    }

    private fun extractCallExpressionTypes(buckets: Map<String, List<TSNode>>, sourceCode: String): List<UsedType> {
        return buckets[NAVIGATION_EXPRESSION].orEmpty().mapNotNull { navExpr ->
            val firstChild = navExpr.getNamedChild(0)
            if (firstChild.isNull) return@mapNotNull null
            val name = TreeTraversal.getNodeText(firstChild, sourceCode).trim()
            if (name.firstOrNull()?.isUpperCase() != true) return@mapNotNull null
            UsedType(name = name)
        }
    }

    private fun extractReturnType(function: TSNode, sourceCode: String): UsedType? {
        // In Kotlin AST, return type comes after `:` which follows function_value_parameters
        // Look for user_type or nullable_type as direct children of function_declaration
        val children = function.children().toList()
        val colonIndex = children.indexOfFirst { it.type == ":" }
        if (colonIndex < 0 || colonIndex + 1 >= children.size) return null
        val typeNode = children[colonIndex + 1]
        return when (typeNode.type) {
            USER_TYPE -> extractType(typeNode, sourceCode)
            NULLABLE_TYPE -> extractTypeFromNullable(typeNode, sourceCode)
            else -> null
        }
    }

    private fun extractTypeFromTypedNode(node: TSNode, sourceCode: String): UsedType? {
        // Find user_type or nullable_type child after the `:` separator
        val children = node.children().toList()
        val colonIndex = children.indexOfFirst { it.type == ":" }
        if (colonIndex < 0 || colonIndex + 1 >= children.size) return null
        val typeNode = children[colonIndex + 1]
        return when (typeNode.type) {
            USER_TYPE -> extractType(typeNode, sourceCode)
            NULLABLE_TYPE -> extractTypeFromNullable(typeNode, sourceCode)
            else -> null
        }
    }

    private fun extractTypeFromNullable(nullableNode: TSNode, sourceCode: String): UsedType? {
        val userType = nullableNode.children().firstOrNull { it.type == USER_TYPE }
        return userType?.let { extractType(it, sourceCode) }
    }

    private fun extractType(userTypeNode: TSNode, sourceCode: String): UsedType? {
        if (userTypeNode.isNull) return null
        val typeIdentifier = userTypeNode.children().firstOrNull { it.type == TYPE_IDENTIFIER }
            ?: return null
        val name = TreeTraversal.getNodeText(typeIdentifier, sourceCode).trim()
        val typeArgs = userTypeNode.children().firstOrNull { it.type == TYPE_ARGUMENTS }
        val genericTypes = typeArgs?.let { extractTypeArguments(it, sourceCode) } ?: emptyList()
        return UsedType(name = name, genericTypes = genericTypes)
    }

    private fun extractTypeArguments(typeArgsNode: TSNode, sourceCode: String): List<UsedType> = typeArgsNode
        .children()
        .filter { it.type == TYPE_PROJECTION }
        .mapNotNull { projection ->
            // Star projection has no user_type child — skip it
            val userType = projection.children().firstOrNull { it.type == USER_TYPE }
                ?: projection.children().firstOrNull { it.type == NULLABLE_TYPE }
            when {
                userType == null -> null
                userType.type == NULLABLE_TYPE -> extractTypeFromNullable(userType, sourceCode)
                else -> extractType(userType, sourceCode)
            }
        }.toList()

    private fun extractGenericTypesFromCallSuffix(callSuffix: TSNode, sourceCode: String): List<UsedType> {
        val typeArgs = callSuffix.children().firstOrNull { it.type == TYPE_ARGUMENTS }
            ?: return emptyList()
        return extractTypeArguments(typeArgs, sourceCode)
    }
}
