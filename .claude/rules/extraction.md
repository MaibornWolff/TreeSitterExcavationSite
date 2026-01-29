---
paths: src/main/kotlin/**/integration/extraction/**/*.kt, src/main/kotlin/**/languages/**/extractors/**/*.kt
---

# Text Extraction

## ExtractionStrategy Types

Generic patterns for extracting text from AST nodes (from `shared/domain/ExtractionStrategy.kt`):

- `FirstChildByType(type)` - Find first child of given type
- `FirstChildByTypes(types)` - Find first child matching any type
- `NestedInChild(container, target)` - Find target inside container child
- `AllChildrenByType(type)` - Extract all children of type

## Adding Custom Extractors

For language-specific patterns that can't be expressed with generic strategies:

1. **Create extractor files** in `languages/<lang>/extractors/`:

Each extractor is a standalone `internal fun` in its own file (named `{Purpose}Extractor.kt`):

```kotlin
// languages/newlang/extractors/SpecialPatternExtractor.kt
package ...languages.newlang.extractors

internal fun extractSpecialPattern(node: TSNode, sourceCode: String): String? {
    return TreeTraversal.findFirstChildByType(node, "identifier")
        ?.let { TreeTraversal.getNodeText(it, sourceCode) }
}

// languages/newlang/extractors/MultipleIdentifiersExtractor.kt
internal fun extractMultipleIdentifiers(node: TSNode, sourceCode: String): List<String> {
    return TreeTraversal.findAllChildrenByType(node, "identifier")
        .map { TreeTraversal.getNodeText(it, sourceCode) }
}
```

2. **Reference in extraction mapping** (import functions directly):

```kotlin
// languages/newlang/NewLangExtractionMapping.kt
import ...languages.newlang.extractors.extractSpecialPattern
import ...languages.newlang.extractors.extractMultipleIdentifiers

object NewLangExtractionMapping : ExtractionMapping {
    override val nodeExtractions = buildMap {
        put("special_node", Extract.Identifier(
            customSingle = ::extractSpecialPattern
        ))
        put("multi_node", Extract.Identifier(
            customMulti = ::extractMultipleIdentifiers
        ))
    }
}
```

3. **Shared helpers**: If multiple extractors share helper functions, create a `{Lang}Helpers.kt` file

## Comment and String Formats

From `shared/domain/`:
- `CommentFormats.Line("//")` - Line comments
- `CommentFormats.Block` - Block comments /* */
- `StringFormats.Quoted()` - Standard quoted strings

## Hexagonal Pattern

The extraction feature uses ports/adapters:
- **Port**: `ExtractionNodeTypes` interface in `integration/extraction/ports/`
- **Adapter**: `LanguageDefinitionExtractionAdapter` in `integration/extraction/adapters/` adapts `LanguageDefinition` to `ExtractionNodeTypes`

## Key Files

| File | Purpose |
|------|---------|
| `integration/extraction/ExtractionFacade.kt` | Feature entry point |
| `integration/extraction/ExtractionExecutor.kt` | Orchestrates extraction |
| `integration/extraction/DirectTextExtractor.kt` | AST walker |
| `integration/extraction/extractors/common/*.kt` | Common extractors |
| `languages/<lang>/extractors/*.kt` | Language-specific extractors |
| `shared/domain/Extract.kt` | Extract type definitions |
| `shared/domain/ExtractionResult.kt` | Result data class |
