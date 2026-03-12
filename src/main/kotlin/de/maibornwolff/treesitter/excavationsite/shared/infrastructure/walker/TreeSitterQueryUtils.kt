package de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker

import org.treesitter.TSLanguage
import org.treesitter.TSNode
import org.treesitter.TSQuery
import org.treesitter.TSQueryCursor
import java.lang.ref.Reference

data class QueryCapture(val node: TSNode, val index: Int)

data class QueryMatch(val captures: List<QueryCapture>)

fun TSNode.executeQuery(queryString: String, treeSitterLanguage: TSLanguage): List<QueryMatch> {
    val query = TSQuery(treeSitterLanguage, queryString)
    val cursor = TSQueryCursor()
    cursor.exec(query, this)
    val results = cursor.matches.toList()
    // Prevent GC from freeing the native TSQuery while the cursor iterates.
    // TSQueryCursor.exec() only passes the native pointer, not the Java object reference.
    Reference.reachabilityFence(query)
    return results
}

private fun TSQueryCursor.TSMatchIterator.toList(): List<QueryMatch> {
    val result = mutableListOf<QueryMatch>()
    forEach { match ->
        val captures = match.captures.map { QueryCapture(it.node, it.index) }
        result.add(QueryMatch(captures))
    }
    return result
}
