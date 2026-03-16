package de.maibornwolff.treesitter.excavationsite.languages.java

import de.maibornwolff.treesitter.excavationsite.shared.domain.Declaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.DeclarationType
import de.maibornwolff.treesitter.excavationsite.shared.domain.DependencyAnalyzer
import de.maibornwolff.treesitter.excavationsite.shared.domain.DependencyResult
import de.maibornwolff.treesitter.excavationsite.shared.domain.ImportDeclaration
import de.maibornwolff.treesitter.excavationsite.shared.domain.UsedType
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.QueryMatch
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.TreeTraversal
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.children
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.executeQuery
import de.maibornwolff.treesitter.excavationsite.shared.infrastructure.walker.namedChildren
import org.treesitter.TSLanguage
import org.treesitter.TSNode

object JavaDependencyAnalyzer : DependencyAnalyzer {
    private const val PACKAGE_QUERY = "(package_declaration) @package"
    private const val IMPORT_QUERY = "(import_declaration) @import"
    private const val DECLARATION_QUERY =
        "[" +
            "(class_declaration)" +
            "(record_declaration)" +
            "(interface_declaration)" +
            "(enum_declaration)" +
            "(annotation_type_declaration)" +
            "] @declaration"

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

    override fun analyze(rootNode: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): DependencyResult {
        val packagePath = extractPackagePath(rootNode, sourceCode, treeSitterLanguage)
        val imports = extractImports(rootNode, sourceCode, treeSitterLanguage)
        val declarations = extractDeclarations(rootNode, sourceCode, treeSitterLanguage)
        return DependencyResult(
            packagePath = packagePath,
            imports = imports,
            declarations = declarations
        )
    }

    private fun extractPackagePath(rootNode: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): List<String> {
        val matches = rootNode.executeQuery(PACKAGE_QUERY, treeSitterLanguage)
        if (matches.isEmpty()) return emptyList()

        val packageNode = matches.first().capture("package").node
        val packageText = TreeTraversal.findFirstChildTextByType(packageNode, sourceCode, "scoped_identifier", "identifier")
            ?: return emptyList()
        return packageText.split(".")
    }

    private fun extractImports(rootNode: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): List<ImportDeclaration> {
        val matches = rootNode.executeQuery(IMPORT_QUERY, treeSitterLanguage)
        return matches.map { match ->
            val importNode = match.capture("import").node
            val isWildcard = importNode.children().any { it.type == "asterisk" }
            val identifierText = TreeTraversal.findFirstChildTextByType(importNode, sourceCode, "scoped_identifier", "identifier")
            val path = identifierText?.split(".") ?: emptyList()
            ImportDeclaration(path = path, isWildcard = isWildcard)
        }
    }

    private fun extractDeclarations(rootNode: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): List<Declaration> {
        val matches = rootNode.executeQuery(DECLARATION_QUERY, treeSitterLanguage)
        return matches.map { match ->
            val declarationNode = match.capture("declaration").node

            val name = TreeTraversal.getNodeText(
                declarationNode.getChildByFieldName("name"),
                sourceCode
            )
            val type = declarationType(declarationNode)
            val usedTypes = extractUsedTypes(declarationNode, sourceCode, treeSitterLanguage)
            Declaration(name = name, type = type, usedTypes = usedTypes)
        }
    }

    private fun declarationType(node: TSNode): DeclarationType = when (node.type) {
        "class_declaration" -> DeclarationType.CLASS
        "record_declaration" -> DeclarationType.RECORD
        "interface_declaration" -> DeclarationType.INTERFACE
        "enum_declaration" -> DeclarationType.ENUM
        "annotation_type_declaration" -> DeclarationType.ANNOTATION
        else -> DeclarationType.UNKNOWN
    }

    private fun extractUsedTypes(declaration: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): Set<UsedType> {
        val fieldTypes = extractTypesByFieldName(declaration, sourceCode, treeSitterLanguage, FIELD_QUERY, "field", "type")
        val variableTypes = extractTypesByFieldName(declaration, sourceCode, treeSitterLanguage, VARIABLE_QUERY, "variable", "type")
        val annotationTypes = extractTypesByFieldName(declaration, sourceCode, treeSitterLanguage, ANNOTATION_QUERY, "annotation", "name")
        val constructorCallTypes = extractTypesByFieldName(declaration, sourceCode, treeSitterLanguage, CONSTRUCTOR_CALL_QUERY, "creation", "type")
        val methodInvocationTypes = extractMethodInvocationAndFieldAccessTypes(declaration, sourceCode, treeSitterLanguage)
        val inheritanceTypes = extractInheritanceTypes(declaration, sourceCode, treeSitterLanguage)
        val thrownTypes = extractThrownTypes(declaration, sourceCode, treeSitterLanguage)
        val methodTypes = extractMethodAndConstructorTypes(declaration, sourceCode, treeSitterLanguage)

        return (
            fieldTypes + variableTypes + annotationTypes + constructorCallTypes +
                methodInvocationTypes + inheritanceTypes + thrownTypes + methodTypes
        ).toSet()
    }

    private fun extractTypesByFieldName(
        node: TSNode,
        sourceCode: String,
        treeSitterLanguage: TSLanguage,
        queryString: String,
        captureName: String,
        fieldName: String
    ): List<UsedType> {
        val matches = node.executeQuery(queryString, treeSitterLanguage)
        return matches.map { match ->
            val capturedNode = match.capture(captureName).node
            val typeNode = capturedNode.getChildByFieldName(fieldName)
            extractType(typeNode, sourceCode)
        }
    }

    private fun extractMethodInvocationAndFieldAccessTypes(node: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): List<UsedType> {
        val matches = node.executeQuery(METHOD_INVOCATION_AND_FIELD_ACCESS_QUERY, treeSitterLanguage)
        return matches
            .map { match ->
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
            typeListNode.namedChildren().map { child -> extractType(child, sourceCode) }.toList()
        }
        val superClasses = node.executeQuery(SUPERCLASS_QUERY, treeSitterLanguage).map { match ->
            extractType(match.capture("superclass").node.getNamedChild(0), sourceCode)
        }
        return implementsAndExtends + superClasses
    }

    private fun extractThrownTypes(node: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): List<UsedType> =
        node.executeQuery(THROWS_QUERY, treeSitterLanguage).flatMap { match ->
            match.capture("throws").node
                .namedChildren()
                .map { extractType(it, sourceCode) }
                .toList()
        }

    private fun extractMethodAndConstructorTypes(node: TSNode, sourceCode: String, treeSitterLanguage: TSLanguage): List<UsedType> {
        val constructorParams = node
            .executeQuery(CONSTRUCTOR_QUERY, treeSitterLanguage)
            .flatMap { extractMethodParameters(it, sourceCode) }

        val methodTypes = node.executeQuery(METHOD_QUERY, treeSitterLanguage).flatMap { match ->
            extractMethodParameters(match, sourceCode) + extractMethodReturnType(match, sourceCode, "method")
        }
        return constructorParams + methodTypes
    }

    private fun extractMethodReturnType(match: QueryMatch, sourceCode: String, captureName: String): UsedType {
        val methodNode = match.capture(captureName).node
        val typeNode = methodNode.getChildByFieldName("type")
        return extractType(typeNode, sourceCode)
    }

    private fun extractMethodParameters(match: QueryMatch, sourceCode: String): List<UsedType> {
        val parametersNode = match.capture("parameters").node
        return parametersNode
            .namedChildren()
            .map {
                val parameterType = it.getChildByFieldName("type")
                extractType(parameterType, sourceCode)
            }.toList()
    }

    private fun extractType(typeNode: TSNode, sourceCode: String): UsedType {
        if (typeNode.isNull) return UsedType("unparsable_type_in_analysis")
        if (typeNode.type == "generic_type") {
            val typeIdentifier = typeNode.getNamedChild(0)
            val typeArguments = typeNode.getNamedChild(1)
            val genericTypes = typeArguments.namedChildren().map { extractType(it, sourceCode) }.toList()
            return UsedType(
                name = TreeTraversal.getNodeText(typeIdentifier, sourceCode).trim(),
                genericTypes = genericTypes
            )
        }
        return UsedType(name = TreeTraversal.getNodeText(typeNode, sourceCode).trim())
    }
}
