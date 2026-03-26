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

## Key Principles

1. **Match DC main's output — fix bugs where possible.** The goal is to produce the same results as DC main. DC's legacy behavior is the baseline — even when it seems wrong (type leakage, quirky concatenation order, positional extraction). However, if DC has a genuine bug (e.g., wrong classification, broken positional extraction for dotted types), TSE should fix it. Document accepted improvements explicitly and verify the differences are real improvements, not extraction errors.

2. **Set up dc-compare before you think you're ready.** Run it as soon as basic extractors work, not after you think you're done. The Kotlin migration went through 4 rounds (17k → 2.9k → 1.7k → 74 lines), each revealing issues that unit tests couldn't catch. Iterate: fix, rebuild, re-compare.

3. **Don't copy the previous language's pattern.** Every language has different AST structures, node types, and DC legacy quirks. Java has distinct node types for class/enum/interface; Kotlin uses modifiers on a single node. Java needed boundary exclusion; Kotlin didn't. parentPath was needed for Kotlin but not Java. Start each migration by dumping the AST and reading DC's legacy analyzer for that specific language.

## Migration Workflow

### Phase 1: Understand the language

1. **Read DC's legacy analyzer** for the target language (see paths above) — understand how it extracts packages, imports, declarations, and used types
2. **Dump the AST** for sample code covering all declaration types, imports, generics, inheritance, and language-specific features. Verify node type assumptions before writing any extractor code.
3. **Identify language-specific quirks** — how does DC handle nested types, type leakage, boundary exclusion, dotted types? Don't assume it works the same as Java or Kotlin.

### Phase 2: Implement in TSE

1. **Create extractors** in `languages/<lang>/extractors/`:
  - `PackageExtractor` — package/module path as `List<String>`
  - `ImportExtractor` — imports as `List<ImportDeclaration>`
  - `DeclarationExtractor` — type declarations, delegates to UsedTypeExtractor
  - `UsedTypeExtractor` — all types used within a declaration
2. **Create `<Lang>DependencyMapping`** composing the extractors
3. **Register** in `<Lang>Definition` by overriding `dependencyMapping`
4. **Write tests** in `<Lang>DependencyTest` with `@Nested` groups
5. **Run dc-compare early** — don't wait until all extractors are feature-complete

Rules:

- Use direct tree traversal (`TreeTraversal.*`), never TSQuery
- Use `findAllDescendantsOfType` for recursive discovery (not `children().filter`)
- Boundary exclusion in UsedTypeExtractor is language- and analyzer-specific: apply it only when the language's DC legacy analyzer does not leak nested types upward. Kotlin intentionally omits boundary exclusion because DC's legacy re-parsing leaks nested types upward and TSE's traversal mirrors that behavior (see `plans/add-kotlin-dependency-support.md`). For languages where the analyzer scopes types per declaration (e.g., Java), add boundary exclusion to prevent type leakage across nested declarations.
- Match DC's concatenation order for used type categories (documented in `integration/dependencies/README.md`)

### Phase 3: Verify with dc-compare

1. Find/clone a medium-sized open-source repo in the target language
2. Run `/dc-compare <repo-path>` — DC main is the golden standard
3. Fix any differences in TSE, rebuild, re-compare — repeat until output matches
4. Differences that are genuine improvements (e.g., better annotation classification) should be explicitly accepted and documented

### Phase 4: Integrate in DC

1. Create DC feature branch
2. Rewrite `<Lang>Analyzer` to call `TreeSitterDependencies.analyze()`
3. Delete legacy extraction code (queries, helper extractors)
4. Re-verify with `/dc-compare`

### Phase 5: Release

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
