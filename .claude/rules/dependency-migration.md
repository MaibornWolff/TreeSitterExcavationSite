# Dependency Migration (TSE ↔ DependaCharta)

### DC Analyzer Structure

Legacy analyzers live at:

```
DependaCharta/analysis/src/main/kotlin/de/maibornwolff/dependacharta/pipeline/analysis/analyzers/
├── LanguageAnalyzer.kt          # Interface all analyzers implement
├── LanguageAnalyzerFactory.kt   # Maps SupportedLanguage → analyzer class
├── java/JavaAnalyzer.kt         # ✅ MIGRATED — calls TreeSitterDependencies.analyze()
├── kotlin/                      # ❌ Legacy — custom TSQuery extraction
├── typescript/                  # ❌ Legacy
├── javascript/                  # ❌ Legacy
├── python/                      # ❌ Legacy
├── golang/                      # ❌ Legacy
├── php/                         # ❌ Legacy
├── csharp/                      # ❌ Legacy
├── cpp/                         # ❌ Legacy
└── vue/                         # ❌ Legacy
```

### How to read DC context

When migrating a language, read the legacy analyzer to understand:

- **AST node types** used for packages, imports, declarations
- **UsedType extraction categories** and their **concatenation order** (see `integration/dependencies/README.md`)
- **Language quirks** (CommonJS vs ES6, aliasing, `__init__`, etc.)

Key DC files for a language `<lang>`:

- `analyzers/<lang>/<Lang>Analyzer.kt` — main extraction logic
- `analyzers/<lang>/queries/` — TSQuery patterns (if present)
- `analyzers/<lang>/*Extractor.kt` — helper extractors (if present)

### Migrated analyzer pattern (Java as reference)

After migration, the DC analyzer becomes a thin adapter:

1. Calls `TreeSitterDependencies.analyze(content, Language.X)`
2. Maps TSE's `DependencyResult` → DC's `FileReport` (Node, Dependency, Type)
3. Adds implicit wildcard import for own package (language-specific)
4. Maps `DeclarationType` → DC's `NodeType`

## Migration Workflow

### Phase 1: Implement in TSE

1. **Read DC's legacy analyzer** for the target language (see paths above)
2. **Create extractors** in `languages/<lang>/extractors/`:
  - `PackageExtractor` — package/module path as `List<String>`
  - `ImportExtractor` — imports as `List<ImportDeclaration>`
  - `DeclarationExtractor` — type declarations, delegates to UsedTypeExtractor
  - `UsedTypeExtractor` — all types used within a declaration
3. **Create `<Lang>DependencyMapping`** composing the extractors
4. **Register** in `<Lang>Definition` by overriding `dependencyMapping`
5. **Write tests** in `<Lang>DependencyTest` with `@Nested` groups

Rules:

- Use direct tree traversal (`TreeTraversal.*`), never TSQuery
- Use `findAllDescendantsOfType` for recursive discovery (not `children().filter`)
- Boundary exclusion in UsedTypeExtractor is language- and analyzer-specific: apply it only when the language's DC legacy analyzer does not leak nested types upward. Kotlin intentionally omits boundary exclusion because DC's legacy re-parsing leaks nested types upward and TSE's traversal mirrors that behavior (see `plans/add-kotlin-dependency-support.md`). For languages where the analyzer scopes types per declaration (e.g., Java), add boundary exclusion to prevent type leakage across nested declarations.
- Match DC's concatenation order for used type categories (documented in `integration/dependencies/README.md`)

### Phase 2: Verify with dc-compare

1. Find/clone a medium-sized open-source repo in the target language
2. Run `/dc-compare <repo-path>` — DC main is the golden standard
3. Fix any differences in TSE until output matches

### Phase 3: Integrate in DC

1. Create DC feature branch
2. Rewrite `<Lang>Analyzer` to call `TreeSitterDependencies.analyze()`
3. Delete legacy extraction code (queries, helper extractors)
4. Re-verify with `/dc-compare`

### Phase 4: Release

1. Merge TSE, tag release
2. Update DC's JitPack dependency to new TSE tag
3. Merge DC

## Composite Build (for local testing)

To test TSE changes in DC without publishing:

In DC's `analysis/settings.gradle.kts`, temporarily add:

```kotlin
includeBuild("../../TreeSitterExcavationSite")
```

In DC's `analysis/build.gradle.kts`, change TSE dependency:

```kotlin
// From:
implementation("com.github.MaibornWolff:TreeSitterExcavationSite:<commit>")
// To:
implementation("de.maibornwolff.treesitter.excavationsite:treesitter-excavationsite")
```

Keep the JitPack repository (TSE has transitive dependencies there). **Revert these changes before committing.**

## Lessons Learned

- **Nested declarations**: Always use recursive traversal, never top-level-only filtering
- **Concatenation order**: Must match DC legacy order exactly — affects levelization and cycle detection
- **No TSQuery**: Direct tree traversal is simpler and avoids native GC crashes
- **Defensive extraction**: Prefer skipping (`mapNotNull` + return null) over fallback defaults (`?: ""`, `?: emptyList()`) when an extractor can't resolve a name or path. DC's handling is inconsistent across languages (Go filters empties, Java/Kotlin don't), but empty-named Declarations or empty-path Imports are garbage data. Note: TSE's Java extractors have the same gap (no empty-name guard in DeclarationExtractor, `?: emptyList()` in ImportExtractor) — apply the same fix pattern when touching them.
- **Generic types on qualified calls**: Always look for call suffix type arguments on the call expression node, regardless of whether the first child is a simple identifier or a navigation expression
- **Inheritance extraction must be recursive**: Use `findAllDescendantsGroupedByType`, not direct children — DC's re-parsing leaks nested types' inheritance to the outer class (verify per language whether this leakage is intentional)
- **Dotted type references**: Only extract the first type identifier segment — DC's resolver handles matching simple names to full qualified paths
- **Nested type paths (parentPath)**: Some languages need hierarchical parent paths for nested declarations. Default `emptyList()` is backward-compatible — add when dc-compare reveals path mismatches.
- **Set up dc-compare early**: Run after basic extractors work, not just at the end. Iterate: fix, rebuild, re-compare.
