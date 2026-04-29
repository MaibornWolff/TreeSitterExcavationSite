# Dependency Migration (TSE ↔ DependaCharta)

### DC Analyzer Structure

Legacy analyzers live at:

```
DependaCharta/analysis/src/main/kotlin/de/maibornwolff/dependacharta/pipeline/analysis/analyzers/
├── LanguageAnalyzer.kt          # Interface all analyzers implement
├── LanguageAnalyzerFactory.kt   # Maps SupportedLanguage → analyzer class
├── java/JavaAnalyzer.kt         # ✅ MIGRATED — calls TreeSitterDependencies.analyze()
├── kotlin/KotlinAnalyzer.kt      # ✅ MIGRATED — calls TreeSitterDependencies.analyze()
├── typescript/                  # ❌ Legacy
├── javascript/                  # ❌ Legacy
├── python/                      # ❌ Legacy
├── golang/                      # ❌ Legacy
├── php/                         # ❌ Legacy
├── csharp/CSharpAnalyzer.kt      # ✅ MIGRATED — calls TreeSitterDependencies.analyze()
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

- **Nested declarations**: Always use recursive traversal (`findAllDescendantsOfType`), never top-level-only filtering. DC's legacy analyzers are inconsistent: Java/Kotlin/C++ extract nested declarations, while C#/TypeScript/Python/Go/PHP skip them. TSE normalizes this — languages that support nested type declarations (Java, Kotlin, C#, C++) should always extract them. This is an accepted improvement over DC legacy for C#.
- **Concatenation order**: Must match DC legacy order exactly — affects levelization and cycle detection
- **No TSQuery**: Direct tree traversal is simpler and avoids native GC crashes
- **Defensive extraction**: Prefer skipping (`mapNotNull` + return null) over fallback defaults (`?: ""`, `?: emptyList()`) when an extractor can't resolve a name or path. DC's handling is inconsistent across languages (Go filters empties, Java/Kotlin don't), but empty-named Declarations or empty-path Imports are garbage data. Note: TSE's Java extractors have the same gap (no empty-name guard in DeclarationExtractor, `?: emptyList()` in ImportExtractor) — apply the same fix pattern when touching them.
- **Generic types on qualified calls**: Always look for call suffix type arguments on the call expression node, regardless of whether the first child is a simple identifier or a navigation expression
- **Inheritance extraction must be recursive**: Use `findAllDescendantsGroupedByType`, not direct children — DC's re-parsing leaks nested types' inheritance to the outer class (verify per language whether this leakage is intentional)
- **Dotted type references**: Only extract the first type identifier segment — DC's resolver handles matching simple names to full qualified paths
- **Nested type paths (parentPath)**: Languages with nested type declarations need hierarchical parent paths. `parentPath` combines the namespace path (from `findNamespacePath`) with the parent class chain (from `findParentClassPath`). For C#, file-scoped namespaces are AST siblings (not parents) of declarations, requiring a `compilation_unit` descendants search as fallback. When namespaces are inside `#if` preprocessor directives, tree-sitter nests them inside `preproc_if` nodes — use `findAllDescendantsOfType` (not direct children check) to find namespaces at any depth below `compilation_unit`.
- **Aggregate nested namespaces in `findNamespacePath`**: For languages where `namespace { namespace { ... } }` is legal (C#, C++), walk all ancestors of the declaration and collect every namespace segment — don't return on the first match. Prepend (`add(0, ...)`) as you walk up so the outer-to-inner order is preserved, then flatten. A declaration inside `namespace A.B { namespace C { namespace D.E { class X } } }` must produce `parentPath = [A, B, C, D, E]`, not `[E]` or `[D, E]`.
- **Two namespace models across languages**: DC's legacy analyzers split into two classes. Class 1 (single namespace per file — Java, Kotlin, Go, PHP, Python, JS, Vue) is satisfied by `packagePath` alone; `parentPath` carries only in-file class nesting if any. Class 2 (multiple/nested namespaces per file — C#, C++, TypeScript ambient modules) requires the full namespace chain in `Declaration.parentPath`, because no single file-level value can represent it. DC's Class 2 adapters ignore `result.packagePath` entirely and derive per-declaration path, scoped imports, and per-declaration self-wildcard import from `declaration.parentPath`. See `integration/dependencies/README.md` section "Namespace models" for the per-language table. When migrating C++ next, apply the C# pattern: `parentPath = namespaceChain + parentClassChain`, and the DC adapter should mirror `CSharpAnalyzer` on `feat/tse-csharp-integration`, not `BaseLanguageAnalyzer`.
- **Set up dc-compare early**: Run after basic extractors work, not just at the end. Iterate: fix, rebuild, re-compare.
- **Namespace-prefix on qualified types (`UsedType.namespacePrefix`)**: C++ is the first language where fully-qualified inline type references (`A::B::Settings`) are *idiomatic*, not rare — the `using namespace` directive is discouraged in headers, so qualified usage is the normal cross-namespace reference pattern. For those languages, the extractor must capture the scope segments (`["A", "B"]`) in `UsedType.namespacePrefix`, and the DC adapter must emit a synthetic `Dependency(Path(namespacePrefix), isWildcard=true)` per qualified usage. The resolver consumes the synthetic wildcard through its existing wildcard-prepending loop in `Node.resolveTypeImport`. Java / Kotlin / C# leave the field empty — imports at the top of the file already carry the neighborhood info the resolver needs. PHP is the other language that could opt in when its migration lands. Without this hint, qualified cross-namespace usages silently fail to resolve, producing sparse dep graphs. See `integration/dependencies/README.md` "Namespace-prefix handling" for details.
- **Tree-sitter-cpp parses qualified identifiers right-associatively**: For `A::B::C::helper()`, the outermost `qualified_identifier`'s `scope` field is `namespace_identifier("A")` (not a nested qualified_identifier); its `name` field holds the nested `qualified_identifier("B::C::helper")`. Extractors that walk via `name` (`CppTypeHelper.extractRightmostSegment`) must handle this — each level contributes one scope segment. DC's TSQuery `(qualified_identifier scope: (namespace_identifier)@type)` matches at every level because every non-leaf qualified_identifier has a `namespace_identifier` scope, so DC emits the OUTERMOST segment (`A`) as its own Type for nested calls — not the innermost class name. TSE mirrors this via `CppTypeHelper.extractSingleSegmentScope`.
- **Empty-namespace wildcard + `String.contains("")` is DC's hidden simple-name resolver**: DC legacy C++ `CppUtils.createNode` unconditionally appends `Dependency(namespace, isWildcard=true)` even when `namespace = Path(emptyList())`. `Node.resolveTypeImport` then uses `it.withDots().contains(wildcard.withDots())` — a **String** substring check. For an empty-path wildcard, `withDots() == ""` and `"anything".contains("")` is trivially true, so the resolver effectively falls back to "find any project node by simple name". Migrated C++ adapters MUST emit the self-wildcard unconditionally, including when `parentPath` is empty — our first DC adapter guarded on `parentPath.isNotEmpty()` and file-scope declarations silently failed to resolve cross-file references. Closing this gap recovers a substantial number of cross-file dependency edges for file-scope declarations (several hundred in our C++ reference corpus). See `Node.kt:134-141` with the resolver's in-code TODO flagging the looseness.
- **DC legacy C++ has a tree-sitter parser failure on some `.h` files**: DC main logs `<file.h>: Node is a null node` for many C++ headers (typical C++ corpora show DC main producing only a tiny fraction of the `_h` nodes TSE produces — orders-of-magnitude ratios in our reference corpus). This is a DC bug, not a feature over-production. TSE correctly extracts declarations from headers. Don't try to mirror this by filtering `.h` files — per migration rules, "match DC but fix bugs". Document the extra header nodes as an accepted improvement.
- **DC's `BodyProcessor.addTypesAndDependenciesToRelatedNode` dumps types onto `lastOrNull()`**: in `analyzers/cpp/processing/BodyProcessor.kt:86`, accumulated usedTypes are attached to the most-recently-added node — not the semantic owner. In multi-class-per-file scenarios (e.g., a single `.cpp` file with ~30 sequential class/struct declarations interleaved with free functions), every free function's type usage pollutes the preceding declaration. TSE's extractor correctly scopes to the semantic owner; do not replicate this bug. In typical C++ corpora this accounts for hundreds of unmatchable main-only deps — a hard ceiling on correctness-respecting match rates (~45-50% for multi-class C++ codebases). When planning extraction work, filter out misattribution-victim sources before estimating gaps: a source where feature has 0-2 deps but main has 10-30 on the same node is almost certainly a DC dump victim.
- **Match-rate ceiling**: against DC main, correctness-respecting migrations cap around 45-55% match rate for C++ codebases with many multi-class files. The three DC bugs (header parse crash, empty-wildcard over-match that we benefit from, dump-to-lastNode) collectively exclude ~30-50% of deps from being matchable without mirroring bugs. When planning Issue-N work, classify the gap first (misdirection vs misattribution vs real) before predicting impact — predictions without classification have been repeatedly off by 5-10x in this migration.
- **`TypeOfUsage` is silently dropped at the TSE/DC adapter boundary** — TSE's public `UsedType` carries only `name`, `genericTypes`, `namespacePrefix`. DC's `Type` model has a `usageSource: TypeOfUsage` field (`USAGE`/`INHERITANCE`/`IMPLEMENTATION`/`CONSTANT_ACCESS`/`RETURN_VALUE`/`INSTANTIATION`/`ARGUMENT`) that the shared `analyzers/TseMappings.toType()` always sets to the default `USAGE`. **Languages whose DC legacy analyzers DO classify**: C++ (6 files), Go (3 query files), PHP (`UsedTypesExtractor`), Vue (`VueAnalyzer`). **Languages whose DC legacy analyzers DON'T classify (everything → `USAGE`)**: Java, Kotlin, C#, TypeScript, JavaScript. Java/Kotlin/C# migrations didn't notice the gap because their legacy outputs were already flat-`USAGE`. The C++ migration is the **first migration where the lossy mapping diverges from legacy**; the resulting `.cg.json` carries flat `"type": "usage"` instead of the mix DC main emits. Match-rate is unaffected — `usageSource` isn't part of dc-compare's edge-set key — but downstream visualization fidelity (Web Studio edge-color/icon legend) degrades. **Migrating Go, PHP, or Vue will resurface this gap.** A proper fix requires extending TSE's public `UsedType` with a `usageSource` field and updating extractors in all four affected languages plus the shared `TseMappings.toType()`. Out of scope for any single language migration; track as its own follow-up.
- **Primitives emit raw source text, no semantic normalization** — for languages with built-in primitive types (currently C++; relevant for any new language with similar AST shapes), TSE emits the trimmed source text of `primitive_type` and `sized_type_specifier` nodes verbatim: `void`, `int`, `unsigned int`, bare `unsigned`/`signed`. **DC's legacy C++ analyzer normalized bare `unsigned`/`signed` to `"int"`** (the C standard's implicit type); TSE does not replicate that — semantic normalization is a language quirk that doesn't belong in a generic extractor, and the resolver doesn't need it (no project class is named `int` so primitives don't produce dep edges). **Test impact:** assertions over `usedTypes` see the primitive — strict `containsExactly` sets must include `void`/`int`/etc. when the fixture's methods have primitive return or parameter types. Use `containsExactlyInAnyOrder` and add the primitive, or filter the collection if the test only cares about user-defined types. See `integration/dependencies/README.md` "Primitive and sized-type representation" for the full table.
- **Generics are stored nested in `UsedType`, never flat-duplicated** — TSE's `UsedTypeExtractor` represents `List<String>` as one nested entry (`UsedType("List", genericTypes=[UsedType("String")])`), not as two parallel entries (`List` plus a flat `String`). Same recursively for `Map<Key, Container<Item>>`. **Some DC legacy analyzers (notably C++) emitted both forms** — wrapped plus flat duplicates — so DC tests inherited from those legacy adapters often assert both. Migrated tests must drop the standalone-duplicate expectations; the wrapped entry is the canonical one. **Behavior is preserved** because DC's `Node.resolveTypes` recurses `genericTypes` via `Type.containedTypes()` at resolution time, so a project class `String` still gets a dependency edge from `List[String]`. Don't add a pre-flattening pass in the adapter — it would create duplicate edges. See `integration/dependencies/README.md` "Generic types representation" for the full spec. Watch for this when migrating any language with parameterized types — Java/Kotlin/C# already migrated and treat it correctly; TypeScript and PHP will need to follow the same pattern.
- **Better parsing pushes match-rate DOWN via DC's resolver substring fallback** — TSE's tree-sitter version may successfully parse files that DC main's older parser fails on (notably embedded vendored libraries or amalgamated single-file dependencies inside the corpus). Each successfully-parsed file adds nodes to the project dictionary that DC main doesn't have. The same DC resolver runs in both, but the input differs: candidates for a given simple name now include the extra-parsed file. The empty-wildcard substring fallback in `Node.resolveTypeImport` (the `it.withDots().contains(wildcard.withDots())` block, with `wildcard.withDots() == ""` matching trivially) picks the *first* candidate in the dictionary order — which differs once an extra candidate is added. In practice this can redirect dozens-to-hundreds of dependency edges per simple-name collision, inflating both main-only and feat-only counts by the same amount. **The misdirection is not a TSE extraction bug** — both runs agree the source uses the same name; only the resolver outcome diverges. **Future migrations will see this dynamic whenever**: (a) TSE parses something DC's parser version chokes on, AND (b) the extra parsed file declares a class whose simple name collides with a project class. Whether match-rate is affected depends on the corpus. The DC resolver itself acknowledges the looseness in an in-code TODO at `Node.kt:127-133`. Do not chase this as an extraction gap — recognize it via the symmetric inflation of main-only and feat-only counts on the same target name. Fix candidates: (1) skip empty wildcards entirely (breaks C++'s file-scope-decl resolution — several hundred matched deps depend on the fallback), (2) require namespace-proximity tie-break when the empty-wildcard fallback finds multiple candidates, (3) replace substring with strict-suffix match (still doesn't help empty case). Fix should land as its own DC change once the C++ migration ships, not before.
