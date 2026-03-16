package de.maibornwolff.treesitter.excavationsite.languages.java.extractors

import de.maibornwolff.treesitter.excavationsite.shared.domain.UsedType
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.QueryMatch
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.executeQuery
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.namedChildren
import org.treesitter.TSLanguage
import org.treesitter.TSNode

internal object UsedTypeExtractor {
    private const val ANNOTATION_QUERY = "[(annotation) (marker_annotation)] @annotation"
    private const val FIELD_QUERY = "(field_declaration) @field"
    private const val VARIABLE_QUERY = "(local_variable_declaration) @variable"
    private const val CONSTRUCTOR_CALL_QUERY = "(object_creation_expression) @creation"
    private const val METHOD_INVOCATION_AND_FIELD_ACCESS_QUERY = "[(method_invocation) (field_access)] @invocation"
    private const val IMPLEMENTS_AND_EXTENDS_QUERY = "[(super_interfaces) (extends_interfaces)] @implementsStatement"
    private const val SUPERCLASS_QUERY = "(superclass) @superclass"
    private const val THROWS_QUERY = "(throws) @throws"
    private const val METHOD_QUERY =
        "(method_declaration parameters: (formal_parameters) @parameters) @method"
    private const val CONSTRUCTOR_QUERY =
        "(constructor_declaration parameters: (formal_parameters) @parameters) @constructor"

    fun extract(declaration: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): Set<UsedType> {
        val fieldTypes = declaration.executeQuery(FIELD_QUERY, treeSitterLanguage).mapNotNull {
            extractType(it.capture("field").node.getChildByFieldName("type"), sourceCode)
        }
        val variableTypes = declaration.executeQuery(VARIABLE_QUERY, treeSitterLanguage).mapNotNull {
            extractType(it.capture("variable").node.getChildByFieldName("type"), sourceCode)
        }
        val annotationTypes = declaration.executeQuery(ANNOTATION_QUERY, treeSitterLanguage).mapNotNull {
            extractType(it.capture("annotation").node.getChildByFieldName("name"), sourceCode)
        }
        val constructorCallTypes = declaration.executeQuery(CONSTRUCTOR_CALL_QUERY, treeSitterLanguage).mapNotNull {
            extractType(it.capture("creation").node.getChildByFieldName("type"), sourceCode)
        }
        val methodInvocationTypes = extractMethodInvocationAndFieldAccessTypes(declaration, sourceCode, treeSitterLanguage)
        val inheritanceTypes = extractInheritanceTypes(declaration, sourceCode, treeSitterLanguage)
        val thrownTypes = extractThrownTypes(declaration, sourceCode, treeSitterLanguage)
        val methodTypes = extractMethodAndConstructorTypes(declaration, sourceCode, treeSitterLanguage)

        return (
            fieldTypes + variableTypes + annotationTypes + constructorCallTypes +
                methodInvocationTypes + inheritanceTypes + thrownTypes + methodTypes
        ).toSet()
    }

    private fun extractMethodInvocationAndFieldAccessTypes(
        node: TSNode,
        sourceCode: String,
        treeSitterLanguage: TSLanguage
    ): List<UsedType> {
        val matches = node.executeQuery(METHOD_INVOCATION_AND_FIELD_ACCESS_QUERY, treeSitterLanguage)
        return matches
            .mapNotNull { match ->
                val capturedNode = match.capture("invocation").node
                val objectNode = capturedNode.getChildByFieldName("object")
                extractType(objectNode, sourceCode)
            }
            // Heuristic: uppercase-first names are likely type references (e.g., SomeClass.method()),
            // lowercase-first are likely variables (e.g., myVar.method()). This misclassifies
            // uppercase variables like LOGGER as types, but matches DC's existing behavior.
            .filter { it.isUppercase() }
    }

    private fun extractInheritanceTypes(node: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): List<UsedType> {
        val implementsAndExtends = node.executeQuery(IMPLEMENTS_AND_EXTENDS_QUERY, treeSitterLanguage).flatMap { match ->
            val typeListNode = match.capture("implementsStatement").node.getNamedChild(0)
            typeListNode.namedChildren().mapNotNull { child -> extractType(child, sourceCode) }.toList()
        }
        val superClasses = node.executeQuery(SUPERCLASS_QUERY, treeSitterLanguage).mapNotNull { match ->
            extractType(match.capture("superclass").node.getNamedChild(0), sourceCode)
        }
        return implementsAndExtends + superClasses
    }

    private fun extractThrownTypes(node: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): List<UsedType> =
        node.executeQuery(THROWS_QUERY, treeSitterLanguage).flatMap { match ->
            match
                .capture("throws")
                .node
                .namedChildren()
                .mapNotNull { extractType(it, sourceCode) }
                .toList()
        }

    private fun extractMethodAndConstructorTypes(node: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): List<UsedType> {
        val constructorParams = node
            .executeQuery(CONSTRUCTOR_QUERY, treeSitterLanguage)
            .flatMap { extractMethodParameters(it, sourceCode) }

        val methodTypes = node.executeQuery(METHOD_QUERY, treeSitterLanguage).flatMap { match ->
            extractMethodParameters(match, sourceCode) + listOfNotNull(extractMethodReturnType(match, sourceCode, "method"))
        }
        return constructorParams + methodTypes
    }

    private fun extractMethodReturnType(match: QueryMatch, sourceCode: String, captureName: String): UsedType? {
        val methodNode = match.capture(captureName).node
        val typeNode = methodNode.getChildByFieldName("type")
        return extractType(typeNode, sourceCode)
    }

    private fun extractMethodParameters(match: QueryMatch, sourceCode: String): List<UsedType> {
        val parametersNode = match.capture("parameters").node
        return parametersNode
            .namedChildren()
            .mapNotNull {
                val parameterType = it.getChildByFieldName("type")
                extractType(parameterType, sourceCode)
            }.toList()
    }

    private fun extractType(typeNode: TSNode, sourceCode: String): UsedType? {
        if (typeNode.isNull) return null
        if (typeNode.type == "generic_type") {
            val typeIdentifier = typeNode.getNamedChild(0)
            val typeArguments = typeNode.getNamedChild(1)
            val genericTypes = typeArguments.namedChildren().mapNotNull { extractType(it, sourceCode) }.toList()
            return UsedType(
                name = TreeTraversal.getNodeText(typeIdentifier, sourceCode).trim(),
                genericTypes = genericTypes
            )
        }
        return UsedType(name = TreeTraversal.getNodeText(typeNode, sourceCode).trim())
    }
}
