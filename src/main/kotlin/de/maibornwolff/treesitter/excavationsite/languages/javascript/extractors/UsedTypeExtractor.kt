package de.maibornwolff.treesitter.excavationsite.languages.javascript.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.UsedType
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import org.treesitter.TSNode

internal object UsedTypeExtractor {
    private const val TYPE_ANNOTATION = "type_annotation"
    private const val TYPE_IDENTIFIER = "type_identifier"
    private const val IDENTIFIER = "identifier"
    private const val NEW_EXPRESSION = "new_expression"
    private const val MEMBER_EXPRESSION = "member_expression"
    private const val CALL_EXPRESSION = "call_expression"
    private const val PROPERTY_IDENTIFIER = "property_identifier"
    private const val EXTENDS_CLAUSE = "extends_clause"
    private const val IMPLEMENTS_CLAUSE = "implements_clause"

    private val ALL_NODE_TYPES = setOf(
        TYPE_ANNOTATION,
        NEW_EXPRESSION,
        MEMBER_EXPRESSION,
        CALL_EXPRESSION,
        EXTENDS_CLAUSE,
        IMPLEMENTS_CLAUSE,
        IDENTIFIER
    )

    fun extract(declaration: TSNode, sourceCode: String, aliasMap: Map<String, String> = emptyMap()): Set<UsedType> {
        val buckets = TreeTraversal.findAllDescendantsGroupedByType(declaration, ALL_NODE_TYPES)

        // DC concatenation order: typeIdentifiers, constructorCalls, memberAccesses, methodCalls, extensions, relevantIdentifiers
        val typeIdentifiers = extractTypeIdentifiers(buckets, sourceCode)
        val constructorCalls = extractConstructorCalls(buckets, sourceCode)
        val memberAccesses = extractMemberAccesses(buckets, sourceCode)
        val methodCalls = extractMethodCalls(buckets, sourceCode)
        val extensions = extractExtensions(buckets, sourceCode)
        val relevantIdentifiers = extractRelevantIdentifiers(buckets, sourceCode)

        return (typeIdentifiers + constructorCalls + memberAccesses + methodCalls + extensions + relevantIdentifiers)
            .map { usedType -> aliasMap[usedType.name]?.let { UsedType(name = it) } ?: usedType }
            .toSet()
    }

    // type_identifier nodes within type annotations (field types, param types, return types)
    private fun extractTypeIdentifiers(buckets: Map<String, List<TSNode>>, sourceCode: String): List<UsedType> =
        buckets[TYPE_ANNOTATION].orEmpty().flatMap { typeAnnotationNode ->
            TreeTraversal
                .findAllDescendantsOfType(typeAnnotationNode, TYPE_IDENTIFIER)
                .map { UsedType(name = TreeTraversal.getNodeText(it, sourceCode).trim()) }
        }

    // new MyService() → MyService
    private fun extractConstructorCalls(buckets: Map<String, List<TSNode>>, sourceCode: String): List<UsedType> =
        buckets[NEW_EXPRESSION].orEmpty().mapNotNull { node ->
            val constructor = node.children().firstOrNull { it.type == IDENTIFIER || it.type == TYPE_IDENTIFIER }
                ?: return@mapNotNull null
            val name = TreeTraversal.getNodeText(constructor, sourceCode).trim()
            if (name.firstOrNull()?.isUpperCase() != true) return@mapNotNull null
            UsedType(name = name)
        }

    // MyModule.value → MyModule (uppercase object of member expression)
    private fun extractMemberAccesses(buckets: Map<String, List<TSNode>>, sourceCode: String): List<UsedType> =
        buckets[MEMBER_EXPRESSION].orEmpty().mapNotNull { node ->
            val root = findLeftmostIdentifier(node) ?: return@mapNotNull null
            val name = TreeTraversal.getNodeText(root, sourceCode).trim()
            if (name.firstOrNull()?.isUpperCase() != true) return@mapNotNull null
            UsedType(name = name)
        }

    // obj.Build() → Build (uppercase method name in a call expression)
    private fun extractMethodCalls(buckets: Map<String, List<TSNode>>, sourceCode: String): List<UsedType> =
        buckets[CALL_EXPRESSION].orEmpty().mapNotNull { callNode ->
            val memberExpr = callNode.children().firstOrNull { it.type == MEMBER_EXPRESSION }
                ?: return@mapNotNull null
            val prop = memberExpr.children().firstOrNull { it.type == PROPERTY_IDENTIFIER }
                ?: return@mapNotNull null
            val name = TreeTraversal.getNodeText(prop, sourceCode).trim()
            if (name.firstOrNull()?.isUpperCase() != true) return@mapNotNull null
            UsedType(name = name)
        }

    // extends Bar / implements IBar, IBaz
    private fun extractExtensions(buckets: Map<String, List<TSNode>>, sourceCode: String): List<UsedType> {
        val clauseNodes = buckets[EXTENDS_CLAUSE].orEmpty() + buckets[IMPLEMENTS_CLAUSE].orEmpty()
        return clauseNodes.flatMap { clauseNode ->
            TreeTraversal
                .findAllDescendantsOfType(clauseNode, TYPE_IDENTIFIER, IDENTIFIER)
                .map { UsedType(name = TreeTraversal.getNodeText(it, sourceCode).trim()) }
        }
    }

    // all uppercase identifier nodes in the declaration body
    private fun extractRelevantIdentifiers(buckets: Map<String, List<TSNode>>, sourceCode: String): List<UsedType> =
        buckets[IDENTIFIER].orEmpty().mapNotNull { node ->
            val name = TreeTraversal.getNodeText(node, sourceCode).trim()
            if (name.firstOrNull()?.isUpperCase() != true) return@mapNotNull null
            UsedType(name = name)
        }
}
