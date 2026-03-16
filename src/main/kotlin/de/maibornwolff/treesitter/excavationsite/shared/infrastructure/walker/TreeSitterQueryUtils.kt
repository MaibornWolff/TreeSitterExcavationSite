package de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker

import org.treesitter.TSLanguage
import org.treesitter.TSNode
import org.treesitter.TSQuery
import org.treesitter.TSQueryCursor
import java.lang.ref.Reference

data class QueryCapture(val node: TSNode, val name: String)

data class QueryMatch(val captures: List<QueryCapture>) {
    fun capture(name: String): QueryCapture = captures.first { it.name == name }
}

fun TSNode.executeQuery(queryString: String, treeSitterLanguage: TSLanguage): List<QueryMatch> {
    val query = TSQuery(treeSitterLanguage, queryString)
    val cursor = TSQueryCursor()
    cursor.exec(query, this)
    val results = cursor.matches.toList(query)
    // Prevent GC from freeing the native TSQuery while the cursor iterates.
    // TSQueryCursor.exec() only passes the native pointer, not the Java object reference.
    Reference.reachabilityFence(query)
    return results
}

private fun TSQueryCursor.TSMatchIterator.toList(query: TSQuery): List<QueryMatch> {
    val result = mutableListOf<QueryMatch>()
    forEach { match ->
        val captures = match.captures.map { QueryCapture(it.node, query.getCaptureNameForId(it.index)) }
        result.add(QueryMatch(captures))
    }
    return result
}
