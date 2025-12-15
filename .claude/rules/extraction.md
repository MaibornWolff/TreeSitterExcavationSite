---
paths: src/main/kotlin/**/features/extraction/**/*.kt
---

# Text Extraction

## ExtractionStrategy Types

Generic patterns for extracting text from AST nodes:

- `FirstChildByType(type)` - Find first child of given type
- `FirstChildByTypes(types)` - Find first child matching any type
- `NestedInChild(container, target)` - Find target inside container child
- `AllChildrenByType(type)` - Extract all children of type

## Adding Custom Extractors

For language-specific patterns that can't be expressed with generic strategies:

1. **Create extractor files** in `features/extraction/extractors/languagespecific/newlang/`:

Each extractor is a standalone `internal fun` in its own file (named `{Purpose}Extractor.kt`):

```kotlin
// SpecialPatternExtractor.kt
package ...languagespecific.newlang

internal fun extractSpecialPattern(node: TSNode, sourceCode: String): String? {
    return TreeTraversal.findFirstChildByType(node, "identifier")
        ?.let { TreeTraversal.getNodeText(it, sourceCode) }
}

// MultipleIdentifiersExtractor.kt
internal fun extractMultipleIdentifiers(node: TSNode, sourceCode: String): List<String> {
    return TreeTraversal.findAllChildrenByType(node, "identifier")
        .map { TreeTraversal.getNodeText(it, sourceCode) }
}
```

2. **Reference in definition** (import functions directly):

```kotlin
import ...languagespecific.newlang.extractSpecialPattern
import ...languagespecific.newlang.extractMultipleIdentifiers

put("special_node", Extract.Identifier(
    customSingle = ::extractSpecialPattern
))
put("multi_node", Extract.Identifier(
    customMulti = ::extractMultipleIdentifiers
))
```

3. **Shared helpers**: If multiple extractors share helper functions, create a `{Lang}Helpers.kt` file

## Comment and String Formats

- `CommentFormats.Line("//")` - Line comments
- `CommentFormats.Block` - Block comments /* */
- `StringFormats.Quoted()` - Standard quoted strings

## Hexagonal Pattern

The extraction feature uses ports/adapters:
- **Port**: `ExtractionNodeTypes` interface in `features/extraction/ports/`
- **Adapter**: `LanguageDefinitionExtractionAdapter` in `features/extraction/adapters/` adapts `LanguageDefinition` to `ExtractionNodeTypes`