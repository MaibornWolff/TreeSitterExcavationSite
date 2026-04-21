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

    private val ALL_NODE_TYPES = setOf(BASE_CLASS_CLAUSE, FUNCTION_DEFINITION)

    fun extract(declaration: TSNode, sourceCode: String): Set<UsedType> {
        val buckets = TreeTraversal.findAllDescendantsGroupedByType(declaration, ALL_NODE_TYPES)
        val inheritance = extractInheritanceTypes(buckets, sourceCode)
        val methodTypes = extractMethodReturnAndParamTypes(buckets, sourceCode)
        return (inheritance + methodTypes).toSet()
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
            val returnType = fnDef
                .children()
                .takeWhile { it.type != FUNCTION_DECLARATOR }
                .firstOrNull { CppTypeHelper.isTypeNode(it) }
                ?.let { CppTypeHelper.extractType(it, sourceCode) }
            val paramTypes = fnDef
                .children()
                .firstOrNull { it.type == FUNCTION_DECLARATOR }
                ?.let { extractParameterTypes(it, sourceCode) }
                ?: emptyList()
            paramTypes + listOfNotNull(returnType)
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
}
