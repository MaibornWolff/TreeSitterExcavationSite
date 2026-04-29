---
name: add-cpp-dependency-support
issue: TBD
state: progress
version: 1
tse_branch: feat/cpp-dependency-support
dc_branch: feat/cpp-dependency-integration
---

## Progress

| Task | Status | Commits |
|---|---|---|
| 0. Rename extraction-feature file | ✅ done | `7e678a1` |
| 1. `CppTypeHelper` | ⏸ deferred — grows in Task 5 | — |
| 2. `PackageExtractor` | ✅ done (6/6 cycles) | `88d5eff` → `c4dc5c4` |
| 3. `ImportExtractor` + `ImportKind` | ✅ done (13/13 cycles) | `1a825e9` → `7f27773` |
| 4. `DeclarationExtractor` | ✅ done (16/16 cycles) | `db43ebf` → (cycle 16) |
| 5. `UsedTypeExtractor` (14 cats + boundary exclusion + out-of-class) | ✅ done (14/14 cats, boundary exclusion, out-of-class bodies, order-pin test) | `7d5bc19` → (cat 16+17) |
| 6. Wire `CppDependencyMapping` | ✅ done — real extractor refs in place; `CppDefinition.dependencyMapping` override wired | `88d5eff` |
| 7. Test consolidation | ✅ done — `ApiSupportCheck` added; `@Nested` structure matches C#; concatenation-order integration test present | `ae5a176` |
| 8. dc-compare iteration | ▶ 4 rounds done, switched baseline to cppcheck in R3 | R2 `f3d625f`, R3 `fd5475b`, R4 `e9ae7cb` |
| 9. DC adapter (`feat/cpp-dependency-integration`) | 🔶 partial — analyzer rewrite committed, legacy files still on disk, resolver gap open | DC: `7288351` |
| 10. Release + integrate | ⏳ pending | — |

## Goal

Migrate DependaCharta's legacy C++ dependency analyzer to TSE. Add `CppDependencyMapping` composed of an `ImportExtractor` (covers both `#include` and `using namespace`/`using X::Y`), `PackageExtractor`, `DeclarationExtractor`, `UsedTypeExtractor`, plus a `CppTypeHelper`. All new files live directly under `languages/cpp/extractors/` alongside existing extractors — matches the Java/Kotlin/C# pattern where dependency and extraction-feature extractors share one directory. One collision exists: the existing `languages/cpp/extractors/DeclarationExtractor.kt` is an extraction-feature file (defines `extractFromDeclaration` for the generic `declaration` AST node) and must be renamed so the dependency `DeclarationExtractor` can use the conventional name. Follow the C# Class-2 namespace pattern (multiple/nested namespaces per file; `parentPath = namespaceChain + parentClassChain`). Verify behavior parity with DC main via `dc-compare` on Catch2, then replace DC's `CppAnalyzer` with a thin adapter mirroring `CSharpAnalyzer`.

**Naming convention note**: This plan uses `PackageExtractor` / `ImportExtractor` (following Java/Kotlin precedent and matching the `DependencyResult.packagePath` / `DependencyResult.imports` field names). C# uses `NamespaceExtractor` / `UsingDirectiveExtractor`. Both are valid; the Java/Kotlin names fit C++ better because C++'s `imports` come from two distinct AST sources (`#include` + `using`), so a unified `ImportExtractor` is more accurate than a source-specific name.

## Reference corpus

The C++ migration was validated via dc-compare against [cppcheck](https://github.com/danmar/cppcheck) (depth-1 clone). Catch2 was the original baseline through R2 but was rejected after R3 because its amalgamated header (`extras/catch_amalgamated.hpp`) duplicates every class, triggering DC's `mergeDuplicates` and polluting the diff.

**Final dc-compare numbers (R15, pre-merge baseline)**: matched 1118 / main-only 1349 / feat-only 980 / **45.32% match rate**. Stable through the structural refactor and Phase 8 implementation.

**Corpus-specific evidence behind the generic ranges in `.claude/rules/dependency-migration.md`** (kept here so the rules file can stay corpus-agnostic):

| Lesson | Concrete cppcheck measurement |
|---|---|
| selfWildcard fix for empty parentPath (DC `20ea8a4`) | +358 matched deps |
| Header parse failure in DC main (`Node is a null node`) | DC main: 2 `_h` nodes; TSE: 179 `_h` nodes |
| BodyProcessor dump-to-lastNode misattribution | ~700-900 main-only deps (`lib/valueflow.cpp` alone accounts for ~411) |
| simplecpp-pollution misdirection | ~121 `Token` + ~49 `TokenList` deps redirected to `simplecpp.*` instead of `lib.*` |
| Match-rate ceiling | ~45-55% on cppcheck after exclusion of misattribution + misdirection buckets |

**Cppcheck file paths referenced in lesson examples**:

- `lib/valueflow.cpp` — single `.cpp` file with ~30 class/struct declarations interleaved with free functions; canonical example of DC's `BodyProcessor.addTypesAndDependenciesToRelatedNode` dumping types onto `lastOrNull()` instead of the semantic owner.
- `externals/simplecpp/simplecpp.cpp` — embedded vendored single-file C preprocessor; canonical example of TSE's tree-sitter parsing files DC main's parser version fails on, which inflates the project dictionary and triggers the empty-wildcard substring fallback to pick a different candidate.

## Implementation Approach

**TDD — strict red → green → refactor, same as the Kotlin and C# migrations:**

1. Write one failing test (`// Arrange / // Act / // Assert` comments; name starts with `should`). Run it — must fail with a clear message (not a compile error, unless that's the first test establishing a new file).
2. Write the minimum code to make it pass. Resist adding extra categories "while we're here."
3. Run the full test suite (`./gradlew test`) — everything must be green, not just the new test.
4. Refactor only when green. One change at a time. Tests after each.
5. Commit.
6. Repeat.

**Small commits — commit after each green test, or at a natural sub-feature boundary:**

- One commit per extractor category is the target granularity. For the 14 `UsedTypeExtractor` categories, that's 14 commits in Task 5 alone.
- Commit at the red → green transition, not before. Never commit failing tests.
- Every commit must leave the build green (`./gradlew build` passes, `./gradlew ktlintCheck` passes).
- Conventional Commits per `.claude/rules/code-style.md`:
  - `test(cpp): add failing test for inheritance type extraction` (when introducing a new TDD cycle — red)
  - `feat(cpp): extract inheritance types in UsedTypeExtractor` (green)
  - `refactor(cpp): extract common type-node unwrap helper` (refactor step)
  - `fix(cpp): handle template specialization as base class` (bugfix via dc-compare)
  - `test(cpp): cover nested namespace with preproc wrapping` (test-only additions)
  - `docs(dependencies): document ImportKind enum`
- Each task below calls out explicit **Commit checkpoints** — minimum commit points. More granular commits are welcome.
- If multiple categories share a refactor (e.g., a new helper function), the refactor is its own commit.

**Verification between commits:**
- `./gradlew ktlintFormat && ./gradlew build` before every commit
- Full test suite green, not just the new test
- No `// TODO` left without an issue reference

**When dc-compare (Task 8) reveals a gap mid-stream**: stop the current category, write a failing test reproducing the gap, fix it, commit, then return to the paused category.

## Tasks

### 0. TSE: Rename existing extraction-feature file

Prerequisite rename to avoid the dependency `DeclarationExtractor.kt` collision. The existing `languages/cpp/extractors/DeclarationExtractor.kt` defines top-level `internal fun extractFromDeclaration(node, sourceCode)` used only by `CppExtractionMapping.kt` at `put("declaration", Extract.Identifier(customSingle = ::extractFromDeclaration))`.

**Backward compatibility verification (done):**
- `extractFromDeclaration` is `internal` — not visible outside TSE's module. External consumers (DC, CodeCharta) cannot reference it.
- Grep confirms the only caller is `CppExtractionMapping.kt` (same package).
- `c/extractors/DeclarationExtractor.kt` and `objectivec/extractors/DeclarationExtractor.kt` are separate packages with their own unrelated `extractFromDeclaration` functions — no cross-contamination.
- File names are not part of Kotlin/Java public API. Zero impact for library consumers.

**Steps:**
- Rename file: `DeclarationExtractor.kt` → `GenericDeclarationExtractor.kt`. Matches the naming pattern of the other extraction files in this directory (`FieldDeclarationExtractor`, `ParameterDeclarationExtractor`, etc. — each named after its AST node type; `GenericDeclarationExtractor` names the generic `"declaration"` node explicitly).
- No code changes inside the file needed — `extractFromDeclaration` stays as-is; `CppExtractionMapping.kt` uses `::extractFromDeclaration`, which resolves by package after rename.
- Run `./gradlew build` to confirm compilation.

**Commit checkpoint** — 1 commit: `refactor(cpp): rename DeclarationExtractor to GenericDeclarationExtractor to free conventional name for dependency slice`

- [x] Rename file, verify clean build, commit (`7e678a1`)

### 1. TSE: `CppTypeHelper`

New `languages/cpp/extractors/CppTypeHelper.kt` — mirrors `CSharpTypeHelper` but for C++ type nodes.

Handle these type node types:
- `type_identifier` → `UsedType(name=text)`
- `primitive_type` / `sized_type_specifier` → `UsedType(name=text)` (strip `unsigned`/`signed` like DC's `TypeExtractionService.kt:102-120`)
- `qualified_identifier` (e.g. `A::B::C`) → **rightmost segment only** (`C`). Unwrap nested `qualified_identifier` chain by descending into the `name` field until reaching a leaf `type_identifier` / `template_type`, then emit that leaf's name. Matches DC's `TypeExtractionService.kt:23-29, 67-68` — DC puts the rightmost segment into `Type.name` and the leftmost segments into a separate wildcard `Dependency`. DC's resolver matches simple names against project nodes and relies on imports/`using` directives for resolution. **See "Namespace-prefix loss" regression note below.**
- `template_type` (e.g. `A::B::Foo<int>`) → name field (rightmost, `Foo`) + recursive generic types from `template_argument_list` / `type_descriptor` children. Matches DC's `TypeExtractionService.kt:53-64` + `FunctionArgumentParser.extractNames` which takes `substringAfterLast("::")`.
- `pointer_declarator` / `reference_declarator` / `array_declarator` → unwrap recursively, emit the underlying type
- `placeholder_type_specifier` (`auto`) → **skip at this node, but don't lose info**: the initializer expression is walked separately by `extractNewExpressionTypes` / `extractCallExpressionTypes` / member-access walks, which capture the actual type. Emitting `UsedType("auto")` would pollute `usedTypes` with a non-existent type. Matches DC legacy (its `VariableDeclarationProcessor` TSQuery doesn't match `placeholder_type_specifier` as a type child).
- `decltype` → skip at this node, same reasoning as `auto`: the inner expression is walked separately.
- `dependent_type` (e.g. `typename T::value_type`) → emit `UsedType` with the rightmost segment after unwrapping the `qualified_identifier`-like structure. DC's legacy falls through and emits the full source text as `Type.name` (`TypeExtractionService.kt:67-68`) — this is a DC bug. TSE matches DC's effective behavior when `dependent_type` contains a `qualified_identifier`; for bare `typename T::x` with no further qualification, emit `"x"` (rightmost).
- `scoped_type_identifier` → does not exist in tree-sitter-cpp (it's a Rust grammar node). Remove from the list; keep only `qualified_identifier` handling.

Signature: `isTypeNode(node): Boolean`, `extractType(node, source): UsedType?`.

**TDD cycles & commit checkpoints** (one commit per cycle, more if refactor):
1. `type_identifier` + `primitive_type` — simplest case
2. `sized_type_specifier` with `unsigned`/`signed` stripping
3. `qualified_identifier` rightmost extraction
4. `template_type` with recursive generic types
5. Pointer/reference/array declarator unwrapping
6. `placeholder_type_specifier` / `decltype` skip cases
7. `dependent_type` edge case

**Accepted regression — namespace-prefix loss**: DC legacy emits `A::B` as a separate `Dependency(Path(["A","B"]), isWildcard=true)` alongside the `Type("C")` whenever it extracts a qualified type. TSE's `UsedType` model has no slot for this prefix, so the information is lost. In practice this matters only when the same simple name (`C`) is declared in multiple namespaces within the project and the usage site disambiguates via `A::B::C`. In typical codebases this is rare — qualified usages usually reference types already imported via `#include` or `using namespace`, which DC's resolver handles. If dc-compare on Catch2 reveals systematic misresolution traceable to this loss, escalate to either (a) adding a `namespacePrefix: List<String>` field to `UsedType`, or (b) emitting synthetic `ImportDeclaration`s per qualified usage. Document either escalation as a deviation from the C# migration pattern.

### 2. TSE: `PackageExtractor`

New `languages/cpp/extractors/PackageExtractor.kt`. Class-2 language, so `packagePath` is informational only (C# adapter ignores it).

- Find first `namespace_definition` anywhere via `findAllDescendantsOfType(root, "namespace_definition")`
- Read `name` field; if `namespace_identifier`, use its text; if `nested_namespace_specifier`, split on `::`
- Anonymous namespace (no `name` field) → return `emptyList()`
- Return the segments as `List<String>`

**TDD cycles & commit checkpoints**:
- [x] 1. Simple single namespace — commit `88d5eff` (also bootstrapped `CppDependencyMapping` + `CppDefinition` override with inline stubs for imports/declarations)
- [x] 2. Nested namespace (C++17 `A::B::C`) — commit `1013230`
- [x] 3. Physically nested (`namespace A { namespace B { } }`) — outermost wins; green on first write, `test(cpp):` only — commit `6733139`
- [x] 4. Anonymous namespace → empty — green on first write — commit `fd14d62`
- [x] 5. No namespace → empty — same commit as #4 — commit `fd14d62`
- [x] 6. `#ifdef`-wrapped namespace — green on first write (`findAllDescendantsOfType` recurses through `preproc_if` naturally) — commit `c4dc5c4`

### 3. TSE: `ImportExtractor`

New `languages/cpp/extractors/ImportExtractor.kt`. Handles two distinct AST sources that both produce `ImportDeclaration`s per DC legacy (`IncludeProcessor.kt` + `UsingProcessor.kt`):

**#include directives:**
- Walk `findAllDescendantsOfType(root, "preproc_include")` so `#include`s under `preproc_if`/`preproc_ifdef` are caught
- Read the `path` field:
  - `system_lib_string` → strip `<` and `>`, split on `/`
  - `string_literal` → strip `"`, split on `/`
- Strip whitespace, `\n`, `\r`, `\` from each segment (matches DC's `IncludeProcessor.kt:41-60`)
- Emit `ImportDeclaration(path, isWildcard=false, namespacePath=[])`
- TSE does **no** relative-path resolution and **no** `.`→`_` transform — DC adapter handles both
- Skip empty paths (defensive extraction)

**using directives (at file/namespace scope only, not inside class/struct/enum bodies):**

DC's `UsingProcessor.kt:15-55` handles these cases:
- `using namespace X::Y;` → wildcard import with `path=[X, Y]`
- `using X::Y;` (plain qualified) → non-wildcard import with `path=[X, Y]`
- `using enum X::Y;` (qualified) → non-wildcard import with `path=[X, Y]`
- `using enum X;` (unqualified, no `::`) → **not an import**, used type only (handled in UsedTypeExtractor)
- `using` inside `base_class_clause` (inheriting constructors) → skip entirely

Implementation:
- Walk `findAllDescendantsOfType(root, "using_declaration")`
- Skip if ancestor is `base_class_clause`
- Skip if ancestor is `class_specifier`/`struct_specifier`/`union_specifier` body — used types inside class bodies are handled in UsedTypeExtractor step 13
- Distinguish form by children: `namespace` keyword child → wildcard; `enum` keyword child + `qualified_identifier` → non-wildcard; plain `qualified_identifier` → non-wildcard; plain `identifier` only → skip (too ambiguous for DC)
- Compute `namespacePath` via the same ancestor-walk pattern as `DeclarationExtractor.findNamespacePath` — for `using` directives nested inside a namespace, this becomes the scoped import namespace
- Emit `ImportDeclaration(path=split on ::, isWildcard=<as above>, namespacePath=<aggregated>)`

Result: `include imports + using imports` concatenated, preserving relative order.

**Disambiguation — domain-model change required (decided upfront):** The DC adapter needs to distinguish `#include` imports (apply path-resolution + `.`→`_`) from `using` imports (no normalization). The current `ImportDeclaration(path, isWildcard, namespacePath)` gives both forms identical shape: `using X::Y;` at file scope → `(path=[X,Y], isWildcard=false, namespacePath=[])`, `#include "x"` → `(path=["x"], isWildcard=false, namespacePath=[])`.

Add an `ImportKind` enum to `shared/domain/DependencyResult.kt`:
```kotlin
enum class ImportKind { STANDARD, INCLUDE }
data class ImportDeclaration(
    val path: List<String>,
    val isWildcard: Boolean,
    val namespacePath: List<String> = emptyList(),
    val kind: ImportKind = ImportKind.STANDARD
)
```
C++ `ImportExtractor` emits `kind = INCLUDE` for `preproc_include` nodes and `kind = STANDARD` for `using` directives. All existing languages (Java, Kotlin, C#) continue to use the default `STANDARD` — no adapter changes required for them. C++'s DC adapter switches normalization on `kind == INCLUDE`.

**Backward compatibility verification (done):**
- All existing TSE construction sites use named args (`csharp/extractors/UsingDirectiveExtractor.kt:34-38`, `java/extractors/ImportExtractor.kt:22`, `kotlin/extractors/ImportExtractor.kt:25`). New field with default doesn't break them.
- External consumers (DC, CodeCharta) only **read** `ImportDeclaration` from `TreeSitterDependencies.analyze().imports`. They never construct instances.
- `.copy()`, destructuring (`component1..component3` unchanged), and reading stay stable with the additive field.
- **Zero public API break for downstream library consumers.**

**Docs updates required in this task:**
- `README.md:107` — update `ImportDeclaration` example to show `kind` field
- `src/main/kotlin/de/maibornwolff/treesitter/excavationsite/integration/dependencies/README.md:35, :59` — update domain-model snippet to include `kind`

**TDD cycles & commit checkpoints**:
1. `feat(dependencies): add ImportKind enum and kind field to ImportDeclaration` — domain model change alone; existing tests must stay green (default value means no behavioral change for Java/Kotlin/C#)
2. `docs(dependencies): document ImportKind in READMEs` — standalone docs commit
3. `#include <system_lib_string>` — single system include
4. `#include "string_literal"` — single quoted include
5. Multiple includes in file
6. Preprocessor-wrapped includes (`#ifdef A\n#include "x"\n#endif`)
7. `using namespace X;` at file scope — wildcard
8. `using namespace X::Y::Z;` — nested qualified wildcard
9. `using X::Y;` at file scope — non-wildcard
10. `using enum X::Y;` qualified
11. `using` inside a namespace — `namespacePath` populated
12. `using` inside `base_class_clause` — skipped
13. `using` inside class body — skipped (deferred to UsedTypeExtractor in Task 5)

- [x] 1. `ImportKind` enum + field — commit `1a825e9`
- [x] 2. Docs update — commits `cbab587`, `671e53c`
- [x] 3. System `#include <…>` — commit `83b2ab5`
- [x] 4. Quoted `#include "…"` — commit `82adb3b`
- [x] 5. Multiple includes in source order — commit `ff6666e`
- [x] 6. Preproc-wrapped includes — commit `e94d9eb`
- [x] 7. `using namespace X;` wildcard — commit `1c30408`
- [x] 8. `using namespace X::Y::Z;` qualified — commit `77afec6`
- [x] 9. `using X::Y;` non-wildcard — commit `ee6e803`
- [x] 10. `using enum X::Y;` qualified — commit `4ae6b35`
- [x] 11. Scoped `using` inside namespace (namespacePath) — commit `8a35dbc`
- [x] 12. Inheriting constructor `using Base::Base;` in class body — skipped — commit `77edd00`
- [x] 13. `using enum X;` in class body — skipped — commit `7f27773`

### 4. TSE: `DeclarationExtractor`

New `languages/cpp/extractors/DeclarationExtractor.kt`. Mirror C# `DeclarationExtractor` structure.

- Declaration node types: `class_specifier`, `struct_specifier`, `union_specifier`, `enum_specifier`
- Skip anonymous declarations (no `type_identifier` child) — matches DC's TSQuery requiring a name
- Skip forward declarations without a body (no `field_declaration_list` / `enumerator_list` child)
- All four types map to `DeclarationType.CLASS` except `enum_specifier` → `ENUM`. **Accepted improvement**: struct_specifier → CLASS (fixes DC's body-skip bug at `TypeDeclarationProcessor.kt:115` — documented in migration notes)
- **`parentPath = findNamespacePath(decl) + findParentClassPath(decl)`** — same ancestor-walk pattern as C#:
  - `findNamespacePath`: walk ancestors, for every `namespace_definition` prepend its segments (split `nested_namespace_specifier` text on `::`). Anonymous namespaces contribute nothing.
  - `findParentClassPath`: walk ancestors, for every `class_specifier`/`struct_specifier`/`union_specifier`/`enum_specifier` prepend the `type_identifier` name.
- **Out-of-class methods** (per Q2 decision): additionally walk `findAllDescendantsOfType(root, "function_definition")`. For each whose declarator chain ends in a `qualified_identifier`:
  - Parse `A::B::bar` → `name=B`, `parentPath=namespace_chain + [A]`
  - `type = CLASS`
  - `usedTypes = UsedTypeExtractor.extractFromFunctionBody(functionDef, source)` (new entry point that extracts from params + return + body only, no class context)
  - **Merge on duplicate `(parentPath, name)`**: if another declaration exists with the same key (from a class/struct in the same file, or from another overload), merge `usedTypes` via set union. Matches DC's `BodyProcessor.consolidate` (`BodyProcessor.kt:65-73`). Prevents losing types from overloads.

**Namespace aliases** (`namespace Short = Long::Namespace;`): DC has no handler for `namespace_alias_definition` — it is not listed in any processor's `appliesTo`, and `VariableDeclarationProcessor`'s TSQuery does not match it either. DC legacy emits nothing for namespace aliases (no declaration, no dependency, no used type). **TSE matches — skip entirely.** Neither `DeclarationExtractor` nor `UsedTypeExtractor` handles `namespace_alias_definition`. No test assertion other than "alias produces no declaration" needed.

**`extern "C"` blocks** (`linkage_specification`): transparent — declarations inside them are extracted normally as if at file scope. Boundary exclusion in UsedTypeExtractor must NOT stop at `linkage_specification`.

**TDD cycles & commit checkpoints**:
1. Single `class_specifier` → `Declaration(name, type=CLASS, parentPath=[])`
2. `struct_specifier` → CLASS (accepted improvement)
3. `union_specifier` → CLASS
4. `enum_specifier` / `enum class` / `enum struct` → ENUM
5. Anonymous declaration → skipped
6. Forward declaration (no body) → skipped
7. Single namespace wrapping a class → `parentPath=[namespace]`
8. Nested namespace (`A::B::C`) → `parentPath=[A,B,C]`
9. Physically nested namespaces → aggregated chain
10. Nested class inside class → `parentPath=[namespace..., OuterClass]`
11. `extern "C"` transparent pass-through
12. Preprocessor-wrapped declaration (`#ifdef A\nclass X {}\n#endif`) — mirrors C# fix `623fa5e`
13. Out-of-class method `void A::B::bar() {}` → synthetic `Declaration(name=B, parentPath=[A])`
14. Out-of-class overloads — merge-on-duplicate
15. Namespace alias (`namespace Short = Long;`) → no Declaration emitted
16. C++20 concept definition → no Declaration emitted

### 5. TSE: `UsedTypeExtractor` with boundary exclusion

New `languages/cpp/extractors/UsedTypeExtractor.kt`.

**Categories + concatenation order** (TSE design choice; does NOT match DC's `BodyProcessor.nodeProcessors` dispatch order, which is Comment → Using → Include → Namespace → TypeDeclaration → Inheritance → Method → TypeDef → Alias → GenericTemplate → Define. Our order groups semantically — declaration-contract types first, then expression types):

1. `extractInheritanceTypes` — walk `base_class_clause` descendants; children filtered via `CppTypeHelper.isTypeNode`, skip `access_specifier`. Handle `template_type` as base (template specialization).
2. `extractMethodReturnAndParamTypes` — for each `function_definition`: return type (first type-like child before `function_declarator`) + parameter types from `parameter_list`. Also handle `trailing_return_type` (`auto f() -> Foo`) — extract the type after `->`.
3. `extractConstructorInitializerTypes` — for each `field_initializer_list` (inside constructor bodies), extract the field being initialized and its initialization types. Matches DC's `FieldInitializerProcessor` invoked from `MethodProcessor.kt`.
4. `extractTypeDefTypes` — `type_definition` `type:` field (classic `typedef int X;`)
5. `extractAliasTypes` — `alias_declaration` `type_descriptor` child (C++11 `using X = Y;`)
6. `extractTemplateConstraintTypes` — `requires_clause` (C++20) inside `template_declaration`
7. `extractFieldTypes` — `field_declaration` type node (first type-like child). Skip if already counted in a function signature.
8. `extractVariableTypes` — `declaration` (non-top-level) type node, `parameter_declaration` not already inside a function
9. `extractCastTypes` — `cast_expression` `type:` field **plus** the four explicit-cast forms `static_cast`/`dynamic_cast`/`reinterpret_cast`/`const_cast`. In tree-sitter-cpp these appear as `cast_expression` children of a `template_function`-wrapped call (`static_cast<T>(x)`). Verify exact node types via AST dump in Task 5's first TDD cycle; extract the `T` argument.
10. `extractNewExpressionTypes` — `new_expression` `type:` field
11. `extractCallExpressionTypes` — `call_expression` where callee is `template_function` or `qualified_identifier` (instantiation/static call targets)
12. `extractFriendDeclarationTypes` — `friend_declaration` type child. **Note**: DC legacy has no friend processor; friend types fall through to `VariableDeclarationProcessor`'s fallback. TSE making this explicit is a minor difference but aligns with DC's effective behavior.
13. `extractInClassUsingTypes` — `using_declaration` inside the class body (not at namespace scope). Covers `using enum X;` (unqualified) and `using Base::method;` inside a class.
14. `extractTypeOperandTypes` — type references inside `noexcept(T)`, `sizeof(T)`, `alignof(T)`, and `typeid(T)` where `T` is a type expression. Tree-sitter-cpp may parse these as `sizeof_expression` / `alignof_expression` / `noexcept_expression` with a `type_descriptor` child — verify in Task 5.

Final: `(cat1 + cat2 + ... + cat14).toSet()`.

**Accepted regressions**:
1. **Namespace-prefix loss** for qualified type references (see CppTypeHelper note in Task 1) — DC's separate `Dependency(namespace_prefix, isWildcard=true)` emission is not replicated because TSE's `UsedType` has no slot for it. Escalation path documented in Task 1.
2. **C++20 modules** (`module_declaration`, `import_declaration` for modules — not includes): not handled. DC legacy has no support. Extremely rare in real codebases. If dc-compare reveals Catch2 uses them, add support.
3. **C++20 concepts** (`concept_definition`): not emitted as a Declaration. DC's `CppUtils.nodeType` maps it to `UNKNOWN` and no processor emits a node for it. TSE matches — skip entirely.

**Boundary exclusion**: implement a local private helper inside `UsedTypeExtractor` that recurses like `TreeTraversal.findAllDescendantsGroupedByType` but stops descending at `class_specifier`/`struct_specifier`/`union_specifier`/`enum_specifier` nodes that are NOT the root. Matches DC's scoped-context behavior (`TypeDeclarationProcessor.kt:105-129`). Keep local — no TSE caller besides C++ needs this yet (Kotlin and C# intentionally don't exclude). Promote to `TreeTraversal` only if a second caller appears.

**Secondary entry point** for out-of-class methods:
```kotlin
fun extractFromFunctionBody(functionDef: TSNode, source: String): Set<UsedType>
```
Collects types only from the function's `parameter_list`, return type node, and `compound_statement` body (with boundary exclusion applied).

**TDD cycles & commit checkpoints** — one commit per category (14 categories → ≥14 commits):
1. `feat(cpp): extract inheritance types` — cat 1, incl. template specialization as base
2. `feat(cpp): extract method return and parameter types` — cat 2, incl. trailing return type
3. `feat(cpp): extract constructor initializer types` — cat 3
4. `feat(cpp): extract typedef types` — cat 4
5. `feat(cpp): extract alias declaration types` — cat 5
6. `feat(cpp): extract template constraint types` — cat 6
7. `feat(cpp): extract field types` — cat 7
8. `feat(cpp): extract variable types` — cat 8
9. `feat(cpp): extract cast types including static/dynamic/reinterpret/const_cast` — cat 9
10. `feat(cpp): extract new expression types` — cat 10
11. `feat(cpp): extract call expression types` — cat 11
12. `feat(cpp): extract friend declaration types` — cat 12
13. `feat(cpp): extract in-class using directive types` — cat 13
14. `feat(cpp): extract type operands from sizeof/noexcept/alignof/typeid` — cat 14
15. `feat(cpp): apply boundary exclusion to prevent nested-class type leakage` — the local helper (separate commit so the diff is reviewable)
16. `feat(cpp): add extractFromFunctionBody entry point for out-of-class methods` — the secondary entry point
17. `test(cpp): pin used-type category concatenation order` — the one big integration test

Shared refactors (extract common helper for `find a type-like child` etc.) get their own commits as they emerge.

### 6. TSE: `CppDependencyMapping` + wiring

- New `languages/cpp/CppDependencyMapping.kt` — composes the four extractors (function references)
- Update `languages/cpp/CppDefinition.kt` to add `override val dependencyMapping = CppDependencyMapping.dependencyMapping`
- No changes to `api/TreeSitterDependencies.kt` (it checks `isDependencyMappingSupported` dynamically)

**Commit checkpoint** — 1 commit: `feat(cpp): wire CppDependencyMapping into CppDefinition`. End-to-end `TreeSitterDependencies.analyze(sample, Language.CPP)` should work after this commit.

### 7. TSE: Tests

New `src/test/kotlin/.../languages/cpp/CppDependencyTest.kt` mirroring `CSharpDependencyTest.kt` structure. `@Nested` groups:

- `PackageExtraction` — traditional namespace, nested namespace (`A::B::C`), nested physical (`namespace A { namespace B { } }`), anonymous namespace, no namespace, `#ifdef`-wrapped namespace
- `ImportExtraction`:
  - `IncludeDirectives` — `<system>`, `"quoted"`, nested path, multiple includes, preprocessor-wrapped includes
  - `UsingDirectives` — `using namespace X::Y` (wildcard), `using X::Y` (non-wildcard), `using enum X::Y` (non-wildcard), `using enum X` unqualified (NOT an import — used type), `using` inside `base_class_clause` (skipped), `using` at file scope vs inside a namespace (different `namespacePath`)
- `DeclarationExtraction` — class, struct, union, enum, `enum class` / `enum struct`, nested classes, nested namespaces, forward declarations (skipped), anonymous types (skipped), template class, template specialization as base (`class Foo : Bar<int>`), out-of-class method (`A::B::foo()`), out-of-class constructor, deeply qualified (`A::B::C::method`), out-of-class method dedup-merge (same `(parentPath, name)`), namespace alias (`namespace Short = Long;` — no declaration emitted), `extern "C"` block (declarations extracted transparently), **class/struct/function inside `#ifdef` preproc blocks** (mirrors C# fix `623fa5e` — use `findAllDescendantsOfType` not direct children), **using directives inside nested and preproc-wrapped namespaces** (mirrors C# `UsingDirectiveExtractor` fix in `623fa5e`), concept definitions (skipped)
- `UsedTypeExtraction` — second-level nested classes per category: `Inheritance` (incl. template specialization), `MethodReturnTypes`, `MethodParameterTypes`, `TrailingReturnTypes`, `ConstructorInitializers`, `FieldTypes`, `TypeDefs`, `Aliases`, `TemplateConstraints`, `Casts` (incl. `static_cast<T>` etc.), `NewExpressions`, `CallExpressions`, `FriendDeclarations`, `InClassUsingDirectives`, `TypeOperands` (sizeof/noexcept/alignof/typeid). Plus one top-level-in-`UsedTypeExtraction` test `\`should extract all used type categories in correct order\`` pinning the full concatenation order. Also `BoundaryExclusion` group verifying nested-class types do NOT leak into outer.
- `ApiSupportCheck` — assert `Language.CPP` is in `TreeSitterDependencies`'s supported set (membership, not exact set — per the recent Kotlin commit style `43d5d6a`)

Follow `testing.md`: test names start with `should`; Arrange/Act/Assert comments; `containsExactly` over `contains` where precision matters.

**Note on commit timing**: Most of these tests are written during Tasks 2–5 as part of each TDD cycle, not in a separate pass. Task 7 here is the cleanup/consolidation task: ensure `@Nested` structure is coherent, add any integration tests that didn't fit a single category, mirror recent C# test-file conventions, and verify the `ApiSupportCheck` membership test.

**Commit checkpoints for any work unique to this task**:
- `test(cpp): add ApiSupportCheck membership test for CPP`
- `test(cpp): pin used-type category concatenation order` (if not already done in Task 5)
- `test(cpp): consolidate @Nested structure and mirror C# conventions` (refactor if needed)

### 8. dc-compare iteration

**Test repo**: **Catch2 v3** (`catchorg/Catch2`, default branch `devel`). Reasons: modern C++17, real `.cpp` files with out-of-class method bodies (stresses the `qualified_identifier` path), multiple nested namespaces (`Catch::`, `Catch::Matchers::`, `Catch::Detail::`, `Catch::Generators::`), heavy template use including template specialization as base, mix of system and quoted `#include`s, self-contained (no submodules). Clone as a sibling to TSE/DC, e.g. `../Catch2`.

Run `/dc-compare ../Catch2` early — after PackageExtractor + ImportExtractor + a minimal DeclarationExtractor work, not after everything's done. Iterate fix→rebuild→re-compare until diffs are either zero or documented accepted improvements/regressions.

Interleave this task with Task 5's 14 categories — each category is roughly one TDD cycle, and dc-compare reveals which categories actually contribute meaningful types in real code.

**Commit checkpoints**:
- Each dc-compare-driven fix is its own commit: `fix(cpp): <specific diff source>` with the minimal reproduction test committed first (red → green). Example: `test(cpp): add failing test for X<Y::Z> template arg extraction` → `fix(cpp): unwrap qualified type in template argument`.
- Don't bundle multiple dc-compare fixes in one commit — the whole point of small commits is that each fix is independently reviewable and bisect-able.

### 9. DC: `CppAnalyzer` adapter

Work happens on DC branch **`feat/cpp-dependency-integration`** (already created). During local testing use composite build (per `.claude/rules/dependency-migration.md` "Composite Build" section); revert before committing. After TSE merges + release tag, update DC's JitPack dep to the new TSE version.

**Rewrite `DependaCharta/analysis/.../analyzers/cpp/CppAnalyzer.kt` to mirror `CSharpAnalyzer` (now on main):**

- Call `TreeSitterDependencies.analyze(fileInfo.content, Language.CPP)`
- For each `declaration`:
  - `pathWithName = Path(declaration.parentPath + declaration.name)` (ignore `result.packagePath` — Class-2 model)
  - Imports per declaration = global imports + scoped imports + self-wildcard:
    - Global imports: `result.imports.filter { it.namespacePath.isEmpty() }.map { normalizeInclude(it, fileInfo.physicalPath).toDependency() }`
    - Scoped imports: `result.imports.filter { it.namespacePath == declaration.parentPath }.map { normalizeInclude(it, fileInfo.physicalPath).toDependency() }`
    - Self-wildcard: if `declaration.parentPath.isNotEmpty()`, emit `Dependency(Path(declaration.parentPath), isWildcard = true)`. Skip for top-level declarations (matches DC legacy's empty-path filter `CppContext.kt:24`).
  - `nodeType = declaration.type.toNodeType()` (existing `TseMappings.kt`)
  - `usedTypes = declaration.usedTypes.map { Type(it.name) }` (or whatever existing Node model requires — mirror `CSharpAnalyzer.kt:29`)

**Include path normalization helper (`normalizeInclude` or inline):**

Applies only to imports with `kind == ImportKind.INCLUDE` (set by TSE's `ImportExtractor` per Task 3). `kind == STANDARD` imports (C++ `using` directives; all Java/Kotlin/C# imports) pass through to `toDependency()` without normalization.

```kotlin
private fun normalizeInclude(import: ImportDeclaration, physicalPath: String): Dependency {
    if (import.kind != ImportKind.INCLUDE) return import.toDependency()
    val resolved = if (import.path.firstOrNull() == "." || import.path.firstOrNull() == "..") {
        resolveRelativePath(RelativeImport(import.path.joinToString("/")), Path.fromPhysicalPath(physicalPath))
    } else {
        Path(import.path)
    }
    val transformed = resolved.parts.last().replace(".", "_")
    return Dependency(Path(resolved.parts.dropLast(1) + transformed), isWildcard = false)
}
```

**Fully-qualification for bare-name nodes:** DC legacy's `BodyProcessor.fullyQualify` (`BodyProcessor.kt:60-63`) prepends `Path.fromPhysicalPath(physicalPath)` to nodes whose `pathWithName.hasOnlyName()`. Post-migration: since TSE always computes `parentPath` (incl. out-of-class methods via `qualified_identifier` parse), no node should have `hasOnlyName() == true`. Verify with dc-compare — if any node slips through, fall back to physicalPath prefixing in the adapter.

**Legacy deletion:**

Delete all of `analyzers/cpp/processing/`, `analyzers/cpp/model/`, `analyzers/cpp/CppUtils.kt`, `analyzers/cpp/CppQueryFactory.kt`, `analyzers/cpp/FunctionArgumentParser.kt`, `analyzers/cpp/TypeExtractionService.kt`. Keep only the thin `CppAnalyzer.kt`. Retain `PathUtils.kt` (shared with other analyzers).

**DC test updates:**

- `TypeDeclarationProcessorTest.kt:48` (asserts `NodeType.VALUECLASS` for struct) → update to `NodeType.CLASS` (accepted improvement per Q1)
- `CppAnalysisPipelineTests.kt:335, :389` (same assertion + expected empty `usedTypes`/`dependencies` for structs) → update expected nodeType + update expected `usedTypes`/`dependencies` to reflect struct-body extraction
- Any other tests asserting empty extraction from structs → update to match

**Re-verify:** run `/dc-compare ../Catch2` (same repo as Task 8). Remaining diffs must map to the documented accepted improvements and regressions from Task 1 / Task 5 — currently: struct label (CLASS vs VALUECLASS), struct body extraction gaining usedTypes/nested decls, namespace-prefix loss for qualified type references, skipped C++20 modules and concepts. No other diffs permitted without documentation.

**Commit checkpoints (DC branch)**:
- `refactor(cpp): call TreeSitterDependencies.analyze in CppAnalyzer` — new adapter alongside legacy code initially, so tests keep passing
- `feat(cpp): add normalizeInclude helper for ImportKind.INCLUDE paths`
- `test(cpp): update TypeDeclarationProcessorTest expectations for struct->CLASS` (one per updated test file)
- `test(cpp): update CppAnalysisPipelineTests expectations for struct body extraction`
- `refactor(cpp): remove legacy processors/, model/, CppUtils.kt` — done last, once new adapter is green end-to-end
- `refactor(cpp): remove legacy CppQueryFactory, FunctionArgumentParser, TypeExtractionService`

### 10. Release + integrate

- Merge TSE `feat/cpp-dependency-support` → `main`, tag release
- On DC `feat/cpp-dependency-integration`: update JitPack dependency to new TSE tag
- Merge DC `feat/cpp-dependency-integration` → `main`

**Commit checkpoint (DC)**: 1 commit on the DC branch: `chore(deps): bump TSE to <version> for C++ dependency support`.

## Steps

- [x] Complete Task 0: Rename existing `DeclarationExtractor.kt` → `GenericDeclarationExtractor.kt` — commit `7e678a1`
- [ ] Complete Task 1: `CppTypeHelper` — **deferred**, will grow organically during Task 5 (matches C# pattern; no standalone `CSharpTypeHelperTest` exists)
- [x] Complete Task 2: `PackageExtractor` — commits `88d5eff`, `1013230`, `6733139`, `fd14d62`, `c4dc5c4` (6 TDD cycles, pipeline bootstrapped via inline stubs for imports/declarations)
- [x] Complete Task 3: `ImportExtractor` (includes + using directives) — commits `1a825e9` → `7f27773` (13 cycles: domain model, docs, 4 include forms, 7 using-directive forms)
- [x] Complete Task 4: `DeclarationExtractor` (incl. out-of-class methods with merge-on-dup) — **done, 16/16 cycles**
  - [x] 1. Single `class_specifier` → CLASS — commit `db43ebf`
  - [x] 2. `struct_specifier` → CLASS — commit `cd69b52`
  - [x] 3. `union_specifier` → CLASS — commit `f66695c`
  - [x] 4. `enum_specifier` / `enum class` / `enum struct` → ENUM — commit `0cea9e6`
  - [x] 5. Anonymous declaration → skipped — green on first write (existing `?: return null` guard), `test(cpp):` only
  - [x] 6. Forward declaration (no body) → skipped — added `hasBody` check on `field_declaration_list` / `enumerator_list`
  - [x] 7. Single namespace wrapping a class → `parentPath=[namespace]` — added `findNamespacePath` ancestor walk
  - [x] 8. Nested namespace (`A::B::C`) → `parentPath=[A,B,C]` — green on first write (existing `::` split), `test(cpp):` only
  - [x] 9. Physically nested namespaces → aggregated chain — green on first write (ancestor walk prepends each namespace), `test(cpp):` only
  - [x] 10. Nested class inside class → `parentPath=[namespace..., OuterClass]` — added `findParentClassPath` ancestor walk with name memoization
  - [x] 11. `extern "C"` transparent pass-through — green on first write (`linkage_specification` is ignored by both walks), `test(cpp):` only
  - [x] 12. Preprocessor-wrapped declaration — green on first write (`findAllDescendantsOfType` recurses through `preproc_if`/`preproc_ifdef`), `test(cpp):` only
  - [x] 13. Out-of-class method `void A::B::bar() {}` → synthetic `Declaration(name=B, parentPath=[A])` — added `extractOutOfClassDeclarations` + `mergeDeclarations`
  - [x] 14. Out-of-class overloads — merge-on-duplicate — green on first write (`mergeDeclarations` already in place), `test(cpp):` only
  - [x] 15. Namespace alias (`namespace Short = Long;`) → no Declaration emitted — green on first write (`namespace_alias_definition` not in any walked set), `test(cpp):` only
  - [x] 16. C++20 concept definition → no Declaration emitted — green on first write (`concept_definition` not in any walked set), `test(cpp):` only

  **Resume notes for next session:**
  - Current skeleton in `languages/cpp/extractors/DeclarationExtractor.kt` — handles `class_specifier`/`struct_specifier`/`union_specifier`/`enum_specifier` with `type_identifier` name. `usedTypes = emptySet()` as a placeholder until Task 5 adds `UsedTypeExtractor`.
  - No parent-path logic yet — cycles 7+ require `findNamespacePath` + `findParentClassPath` ancestor walks (mirror C# pattern in `csharp/extractors/DeclarationExtractor.kt`).
  - Cycle 5 should produce a failing test for `class {}` (anonymous). Current code already does `?: return null` when no `type_identifier` found, so the test may be green on first write — verify empirically.
  - Cycle 6 (forward declaration `class Foo;`) — current code likely already skips these because tree-sitter-cpp still parses the stub as a `class_specifier` child inside a `declaration` node, but without a `field_declaration_list` body. Need to decide: explicitly require body child, or rely on the stub not having a `type_identifier` (it does have one). Plan says "skip if no body child" — implement explicitly.
  - Cycles 13–14 (out-of-class methods) are the biggest jump — require a separate `findAllDescendantsOfType(root, "function_definition")` pass and `qualified_identifier`-declarator parsing, plus merge-on-dup. Handle last.
- [ ] Complete Task 5: `UsedTypeExtractor` with local boundary-exclusion helper (14 categories); creates `CppTypeHelper` along the way — **in progress, 2/14 categories done**
  - [x] 1. Inheritance (incl. template specialization as base) — commits `7d5bc19`, `34990ab`
  - [x] 2. Method return + parameter types + trailing return type — commits `37570c4`, `9b2847f`
  - [x] 3. Constructor initializer types — done, 5 tests covering brace/paren init, qualified constants (2- and 3-segment), nested call expression, and no-op base call. `extractInitializerTypeFromQualifiedIdentifier` walks scope fields and emits the last scope segment (second-to-last overall) to match DC's `FieldInitializerProcessor` behavior. Call expressions skipped inside `initializer_list` (brace init) to match DC emitting `Type.unparsable()` there.
  - [x] 4. Typedef types — bundled with cat 5 in single commit
  - [x] 5. Alias declaration types — bundled with cat 4. `extractTypeAliasTypes` handles both `type_definition` (`typedef`) and `alias_declaration` (`using X = Y;`) via the `type:` field + CppTypeHelper. Primitive/sized-type children deliberately skipped (accepted improvement — primitives don't resolve as project types).
  - [x] 6. Template constraint types (C++20 requires) — `extractTemplateConstraintTypes` walks up to a template_declaration parent, descends into the requires_clause constraint, and recursively handles constraint_disjunction/constraint_conjunction (left/right fields).
  - [x] 7. Field types — bundled with cat 8. `field_declaration` added to ALL_NODE_TYPES; delegates to `extractTypeFromTypeField` which unwraps `type_descriptor` or the `type:` field directly.
  - [x] 8. Variable types — bundled with cat 7. `declaration` (non-top-level, found inside function bodies by `findAllDescendantsGroupedByType`) reuses `extractTypeFromTypeField`. Parameters remain handled by cat 2.
  - [x] 9. Cast types (incl. static_cast/dynamic_cast/reinterpret_cast/const_cast) — `extractCastTypes` handles C-style `cast_expression` via `extractTypeFromTypeField`; the four explicit-cast forms are recognized as `call_expression` whose `function:` is a `template_function` with one of four fixed names, extracting from the `arguments` template_argument_list.
  - [x] 10. New expression types — bundled with cat 11. `new_expression` emits type via `extractTypeFromTypeField`. Pointer/ref declarators on the new-type are handled by CppTypeHelper's generic-argument unwrap.
  - [x] 11. Call expression types — bundled with cat 10. `extractInstantiationTypes` processes `call_expression`: template_function callees yield their template arguments (subsumes the explicit-cast handling from cat 9 — `EXPLICIT_CAST_NAMES` filter removed since generic template-function extraction catches static_cast/etc. by design); qualified_identifier callees yield the rightmost segment (matching DC's `extractTypeWithFoundNamespacesAsDependencies` behavior for static/method calls, even when that emits a function name rather than a class).
  - [x] 12. Friend declaration types — bundled with cat 13. `friend_declaration` children are inspected; type_identifiers/template_types pass through `CppTypeHelper.extractType`, `qualified_identifier` yields its rightmost segment (matching DC's fallback behavior).
  - [x] 13. In-class using directive types — bundled with cat 12. `using_declaration` nodes inside `class`/`struct`/`union` bodies emit a type: qualified forms yield the second-to-last segment (the base class), plain identifiers yield themselves (for `using enum X;` unqualified). Refactor: the two qualified_identifier walking helpers (rightmost / second-to-last) moved to `CppTypeHelper.extractRightmostSegment` / `extractSecondToLastSegment` to keep UsedTypeExtractor's function count under detekt's threshold.
  - [x] 14. Type operands (sizeof/alignof) — partial. `sizeof_expression` and `alignof_expression` share the same `type:`/type_descriptor pattern as `cast_expression` and reuse `extractTypeFromTypeField`. `typeid` deferred: tree-sitter-cpp parses it as a generic `call_expression` with identifier name (no dedicated node), so type extraction would require a call-expression name filter. `noexcept(T)` is actually a runtime-expression check (`noexcept(foo())`) rather than a type operator in standard C++, so nothing to do. Follow-up note: add a dedicated handler for `typeid(T)` if dc-compare reveals it matters in real code.
  - [x] Boundary exclusion helper (category 15) — `groupDescendantsStoppingAtNestedDeclarations` is a local iterative DFS that replaces `TreeTraversal.findAllDescendantsGroupedByType` in `extract()`. It buckets descendants by type but refuses to descend into any `class_specifier` / `struct_specifier` / `union_specifier` / `enum_specifier` that isn't the root. Effect: types referenced inside a nested declaration no longer leak into the outer declaration's `usedTypes`. Each nested declaration is still picked up independently by `DeclarationExtractor` and gets its own types.
  - [x] `extractFromFunctionBody` secondary entry point (category 16) — instead of a separate entry point, the root-inclusive walker in `groupDescendantsStoppingAtNestedDeclarations` makes `extract()` handle both top-level declarations and bare `function_definition` nodes. `DeclarationExtractor.extractOutOfClassDeclarations` now calls `UsedTypeExtractor.extract(functionDef, ...)` so synthetic out-of-class declarations get their params, return types, and body types like in-class methods.
  - [x] Pin concatenation order (category 17) — implemented as a `should extract used types from every category in a comprehensive class` integration test that spans every implemented category (inheritance, method signature, constructor initializer, alias, typedef, field, variable, cast, new, call, friend, sizeof) in one class and asserts the exact multiset of extracted names.

  **Resume notes for next session:**
  - `UsedTypeExtractor` + `CppTypeHelper` scaffolding lives in `languages/cpp/extractors/`. `DeclarationExtractor` calls `UsedTypeExtractor.extract(declarationNode, sourceCode)` (cycle 1 wired it in place of the earlier `emptySet()` stub).
  - `CppTypeHelper.isTypeNode` currently recognizes `type_identifier` + `template_type` only. `primitive_type` and `sized_type_specifier` are intentionally out until they matter for a category (method param types with primitives are skipped for now — DC legacy does the same for their own primitive-stripping logic).
  - `TYPE_DESCRIPTOR` unwrap pattern is already in two places (`extractTrailingReturnType` and `extractGenericArgument`). Future categories that reference `type_descriptor` should share a helper if a third use appears.
  - Out-of-class method declarations currently get `usedTypes = emptySet()` because `extractOutOfClassDeclarations` does not call `UsedTypeExtractor` yet. Category 16 (`extractFromFunctionBody`) will change that — scope the walk to the function's params + return + body.
  - `extractOutOfClassDeclarations` passes individual declarations through `UsedTypeExtractor.extract(declarationNode, ...)` implicitly for *explicit* class/struct decls; synthetic decls from out-of-class methods bypass it and will need category 16 routing.
  - Boundary exclusion: needs a local private helper in `UsedTypeExtractor` that recurses like `TreeTraversal.findAllDescendantsGroupedByType` but stops at nested `class_specifier`/`struct_specifier`/`union_specifier`/`enum_specifier` — so types referenced *inside* a nested class body don't leak upward into the outer class's `usedTypes`. Keep the helper local until a second TSE caller appears.

- [x] Complete Task 6: `CppDependencyMapping` wiring + `CppDefinition` update — mapping references real `PackageExtractor`/`ImportExtractor`/`DeclarationExtractor`; `CppDefinition.dependencyMapping` override in place; end-to-end `TreeSitterDependencies.analyze(code, Language.CPP)` confirmed via full build + `*CppDependencyTest*`
- [x] Complete Task 7: `CppDependencyTest` with `@Nested` groups mirroring C# — `ApiSupportCheck` added; structure already covers Package/Import/Declaration/UsedType extraction with per-category nested groups and one top-level concatenation-order integration test
- [ ] Complete Task 8: dc-compare iteration against Catch2 v3 (interleaved with Tasks 3–5)
  - **Round 1 (2026-04-21)**: ran composite build against `../Catch2` (depth-1 clone). Output in `../dc-compare/main/` (golden) and `../dc-compare/feature/`. Structural parity confirmed after adding physical-path prefix for file-scope decls in the DC adapter (`fullyQualify` equivalent). Remaining diffs: +150 feature-only nodes (files DC legacy errored on — accepted improvement), ~20 main-only nodes (DC legacy namespace-loss duplicates — accepted improvement), ~5 genuine gaps (template-specialized struct/class names like `Catch.StringMaker<std::string>` and typedef-like decls — will re-check after Task 5 lands alias/typedef + richer template_type handling). Dependencies empty on C++ nodes because `usedTypes` was empty at round time — rerun after Task 5 to unblock dep/cycle comparison.
  - **Round 5 follow-up (2026-04-22, cppcheck, Option-3 sampling)**: dug into the 1936 main-only deps remaining after namespacePrefix. Sampled 8 classes across files (cmdlineparser, executor, processexecutor, singleexecutor, etc.). Findings (revised — earlier "unqualified usages → resolver gap" framing was wrong):
    - **Issue 1 — `.h` / `.cpp` declarations don't merge** (probable largest contributor). TSE produces both `cli.executor_h.Executor` (from header) and `cli.executor_cpp.Executor` (synthesized from out-of-class methods in the .cpp). DC's `ProcessingPipeline.mergeIdenticalTypes` groups by `pathWithName`, but the file-extension suffixes diverge so the merge never fires. DC main mostly only emits `_cpp` variants because its legacy analyzer drops most headers on parse error — TSE correctly parses both, exposing this gap. **Attempted fix**: strip C++ file extensions in CppAnalyzer pathWithName + include-path normalization. The merge then works (verified with two new tests), but the include path `[settings]` still doesn't match a class at `[lib, settings, Settings]` because the resolver has no rule that bridges the missing directory prefix. **Mystery**: DC main resolves `#include "settings.h"` → `lib.settings_cpp.Settings` despite the same apparent mismatch on its own paths. Tracing `Node.resolveTypeImport` and `resolveComplexModuleImport` line-by-line, none of the existing rules should fire. There is some resolver mechanism we haven't yet identified — needs live debugging on DC main with the legacy analyzer to capture the actual code path. **Status**: fix reverted in DC adapter (kept namespacePrefix synthesis); a `@Disabled` test in `CppAnalyzerTest` documents the merge gap for future work.
    - **Issue 2 — static method-call extraction** (smaller contributor). `Path::removeQuotationMarks()` should produce `UsedType("Path")`, but TSE currently produces `UsedType("removeQuotationMarks", namespacePrefix=["Path"])` — the method name, not the class. DC legacy's `VariableDeclarationProcessor` has a dedicated TSQuery `(call_expression function: (qualified_identifier scope: (namespace_identifier)@type))` that captures the SCOPE for static calls. Fix is straightforward in TSE's `UsedTypeExtractor.extractInstantiationTypes`: when callee is a single-scope `qualified_identifier`, also emit the scope as a UsedType. **Status**: not implemented yet, deferred until Issue 1's resolver mystery is understood (otherwise the new types may not resolve either).
    - **False positives in DC main**: small minority. Example: `CmdLineParser.usedType("Result")` — main resolves to `lib.symboldatabase_cpp.Result` but the actual `Result` is `CmdLineParser::Result` (inner enum). DC main picks an arbitrary project `Result`. Feature correctly produces no dep here. Not our problem.
    - **Estimated breakdown** (from 8-class sample, small N): Issue 1 ~60-70%, Issue 2 ~15-25%, false positives 5-15% of the 1936 main-only deps.

  - **Round 5 (2026-04-22, cppcheck, after namespacePrefix support)**: added `namespacePrefix: List<String>` to `UsedType`, wired C++ extractors to populate it, and updated DC's CppAnalyzer to synthesize `Dependency(Path(prefix), isWildcard=true)` per qualified usage.
    - **Nodes**: main 624 / feature 947 (unchanged from round 4). Shared 616. Declaration parity held.
    - **Dependencies**: main 2467 / feature 774 (up from 566 in round 4). On the 616 shared nodes: **519 deps match (up from 467, +52)**, 1936 main-only (down from 1988), 150 feat-only (up from 23, +127).
    - **Interpretation**: namespacePrefix closes ~11% of the prior gap — a real but partial improvement. The remaining 1936 main-only deps mostly involve *unqualified* usages (`Settings s;` with a `#include "settings.h"` somewhere in the file or transitive header). Those don't go through `namespacePrefix` — they need the `#include` path → resolver-wildcard pathway that DC legacy wired differently. Separate follow-up.
    - **Feat-only jump (+127)**: new wildcard matches enabled by the synthetic `Dependency(Path(prefix), isWildcard=true)`. Most are likely true positives (TSE is more robust than DC legacy on header parsing, so some of these are real deps DC main missed); some may be overmatches from broad namespace wildcards. Spot-check before any next iteration.
    - **Action items**:
      - Ship the namespacePrefix change (TSE + DC) as-is — it's a net win.
      - Backlog: investigate why unqualified usages resolve so much better in DC legacy (likely resolver-side, possibly around how `#include`s seed project-wide type-name wildcards).
      - Re-run dc-compare after DC-side test cleanup in Task 9 to confirm no regressions.

  - **Round 4 (2026-04-22, cppcheck, after full Task 5)**: re-ran cppcheck analysis with the complete Task 5 chain (cats 1–14 + boundary exclusion + out-of-class bodies + function_declarator param types).
    - **Nodes**: main 624 / feature 947 (unchanged from round 3). 616 shared ids, 614 with identical physicalPath, 8 main-only, 331 feat-only. No regression on declaration parity.
    - **Dependencies**: main 2467 / feature 566. Total grew from 481 → 566 (+85). On the 616 shared nodes: 467 deps match, **1988 main-only deps** (TSE+resolver miss these), 23 feat-only.
    - **Dep gap is DC-resolver-bound, not extractor-bound.** Spot-check: `cli/cmdlineparser_cpp.CmdLineParser` — main emits 17 cross-file deps (Path, Settings, ErrorMessage, CppCheck, …), feat emits 1 (file-local XMLErrorMessagesLogger). Feat's imports are populated (cmdlineparser.cpp `#include`s dozens of headers) and TSE's extractor walks every usedType category, but DC's resolver is not matching the simple-name `UsedType`s against project nodes at the same rate DC legacy does. Possible causes: (a) DC's resolver relies on `TypeOfUsage` tags (INSTANTIATION, ARGUMENT, RETURN_VALUE) that the `TseMappings.UsedType.toType()` collapses to `USAGE`; (b) DC legacy's `extractTypeWithFoundNamespacesAsDependencies` emits a namespace-prefix `Dependency(Path([NS]), isWildcard=true)` per qualified type that seeds the resolver's wildcards — TSE's `UsedType` has no slot for this (documented accepted regression from Task 1). Either way, closing the gap needs resolver-side work in DC, not more TSE categories.
    - **Action items**:
      - File a backlog note for the type-usage/namespace-prefix resolver gap. This is out of scope for Task 5 but is the clear next iteration.
      - Move forward to Task 6 (wire `CppDependencyMapping`, already partial), Task 7 (test consolidation), and Task 9 (DC adapter finalisation + legacy file removal).

  - **Round 3 (2026-04-22, cppcheck)**: switched test repo from Catch2 to **cppcheck** (`danmar/cppcheck`, depth-1). Catch2 produced a noisy baseline because its `extras/catch_amalgamated.hpp|.cpp` files duplicate every class declared elsewhere, and DC legacy's file-level error-drop behavior (`AnalysisPipeline.kt:155`) meant DC main emitted 0 nodes for those files while TSE parsed them fully — a cosmetic delta driven entirely by amalgamation, not extraction correctness. cppcheck has a traditional `.cpp`/`.h` layout with no bundled or amalgamated files.
    - **Nodes**: main 624 / feature 947. **Shared by id: 616** (of those, **614 have identical `physicalPath`** — no `mergeDuplicates` synthesis triggered). Only **8 main-only nodes** (mostly inner types inside struct members like `Library::LibraryData.Platform` — edge cases in parentPath construction). **331 feature-only** — all from the 23 cppcheck files DC main's legacy C++ analyzer errored on (`lib/cppcheck.h`, `lib/errorlogger.h`, `lib/platform.h`, `externals/simplecpp/simplecpp.h`, etc.), which DC drops file-level on any parse exception. TSE extracts from them correctly.
    - **Dependencies**: main 2467 / feature 481 (~19%). Same root cause as Round 2 — cats **10 (new_expression), 11 (call_expression), 12 (friend), 13 (in-class using), 14 (type operands)** not yet implemented. Re-run after Commit 5/9 to validate the gap closes.
    - **Conclusion**: declaration parity is clean (616/624 = 99% of DC's node set is matched with identical paths). The divergence comes from TSE being more robust than DC legacy (parses 23 files DC drops) — genuine improvement, not noise. No path-mangling, no amalgamation artefacts. cppcheck is the right baseline going forward.
    - **Action items unchanged from Round 2**: proceed to Commit 5/9; file a backlog note for the 8 inner-type misses (all have `::` in node id — unusual nested-member-struct pattern worth investigating).

  - **Round 2 (2026-04-22, Catch2, superseded)**: re-ran composite build. Output: `../dc-compare/feature/analysis.cg.json`.
    - **Nodes**: main 459 / feature 573. 25 main-only, 139 feature-only. Breakdown:
      - `.cpp` files: identical 90 in both — no declaration gaps on source files.
      - `.hpp` files: main 247 / feature 361 (+114). DC main's legacy C++ analyzer skips declarations in most header files; TSE picks them up. Accepted improvement, per Round-1 notes.
      - Main-only nodes are mostly **template specializations** (`Catch.StringMaker<std::string>`, `Catch.StringMaker<Catch::Approx>` etc.) and a few nested classes (`BenchmarkFunction.callable`, `TestSpec.Pattern`, `ColourImpl.ColourGuard`). **Gap**: TSE does not synthesize a Declaration for explicit template specializations. Needs a separate task (`template_declaration` wrapping a specialized `struct_specifier` / `class_specifier`).
      - 14 feature-only nodes show **duplicate segments in parentPath**: 11 inside `extras/catch_amalgamated.hpp` (`Catch.Detail.Catch.ExprLhs.*`) plus `Catch.Catch.IStream`, `Catch.Benchmark.Benchmark`, `Catch.Generators.Generators`. The last three look legit (`namespace Catch::Benchmark { class Benchmark; }` is valid). The 11 amalgamated ones look like a real bug — probably `extractOutOfClassDeclarations` prefixing `findNamespacePath(fn)` + a `classPathPrefix` that also contains the namespace segment, double-counting. Worth a fix, but low impact (single file, only affects amalgamated).
    - **Dependencies**: main 976 / feature 520 (~46% fewer). On `.cpp` files alone, main 223 / feature 55 (~25%). Root cause: cats **10 (new_expression), 11 (call_expression), 12 (friend), 13 (in-class using), 14 (type operands)** are not implemented yet — and cats 10+11 are by far the heaviest contributors to real-code `usedTypes` (every `Foo::bar()` or `new Foo()` routes through them). Expect the dep gap to collapse after Commit 5/9 lands cats 10+11; re-run to confirm.
    - **Action items**:
      - Proceed to Commit 5/9 (instantiation sites). Re-compare immediately after.
      - File a backlog note for: (a) template specializations as declarations, (b) parentPath duplication in `extractOutOfClassDeclarations` when fully-qualified out-of-class method definitions appear inside their own namespace. Both are edge cases; fix after Task 5 completes.
- [ ] Complete Task 9: DC `CppAnalyzer` adapter + include-path normalization + legacy deletion + test updates — **partially done**: adapter rewritten on DC `feat/cpp-dependency-integration` (commit `7288351`); legacy `analyzers/cpp/processing/`, `analyzers/cpp/model/`, `CppUtils.kt`, `CppQueryFactory.kt`, `FunctionArgumentParser.kt`, `TypeExtractionService.kt` still on disk per user request. Composite-build wiring lives in the DC working tree uncommitted (`analysis/settings.gradle.kts` + `analysis/build.gradle.kts`) — revert before DC merge.
- [ ] Complete Task 10: Release TSE, update DC JitPack dep, merge DC

## How to resume the composite build + dc-compare loop

1. From TSE: `./gradlew ktlintFormat build` — confirm green.
2. DC working tree should already contain the composite-build overrides:
   - `DependaCharta/analysis/settings.gradle.kts` ends with `includeBuild("../../TreeSitterExcavationSite")`
   - `DependaCharta/analysis/build.gradle.kts` uses `implementation("de.maibornwolff.treesitter.excavationsite:treesitter-excavationsite")` instead of the JitPack coordinate
   If missing (after a checkout or cleanup), restore them manually.
3. `cd ../DependaCharta/analysis && ./gradlew fatJar && java -jar build/libs/dependacharta.jar -d ../../Catch2 -o ../../dc-compare/feature -f analysis`
4. Compare via the `/dc-compare` skill or directly: `node` script in `plans/add-cpp-dependency-support.md` ecosystem — normalized JSON diff in `../dc-compare/{main,feature}/analysis.cg.json.normalized.json`.
5. Golden standard at `../dc-compare/main/` is valid for Catch2 v3 @ devel (depth-1 clone 2026-04-21). Regenerate with `--regen-main` semantics if Catch2 is re-cloned.

## Review Feedback Addressed

1. **Missing `using namespace` / `using X::Y` as imports (BLOCKER)**: Expanded Task 3 from `IncludeExtractor` to `ImportExtractor` that covers both `#include` and `using` directives. DC legacy's `UsingProcessor` emits wildcard/non-wildcard Dependencies; TSE must emit corresponding `ImportDeclaration`s. File-scope vs namespace-scope `using` directives populate `namespacePath` differently (same ancestor-walk as declarations).
2. **Constructor initializer lists**: Added category 3 (`extractConstructorInitializerTypes`) in Task 5 matching DC's `FieldInitializerProcessor`.
3. **`#define` macro bodies**: Removed — was mischaracterization. DC's `DefineProcessor` handles `preproc_ifdef`/`postproc_else` (preprocessor *conditionals*) and just recurses `BodyProcessor`; it does NOT extract from `#define` macro bodies. DC never did that, so "accepted regression" was vacuous. Declarations under `#ifdef` are caught by `findAllDescendantsOfType` which recurses through `preproc_if`/`preproc_ifdef` nodes naturally.
4. **Missing language coverage**: Task 4 now explicitly addresses `namespace_alias_definition`, `extern "C"` blocks, `enum_struct`/`enum_class`. Task 5 addresses template specialization as base class (`template_type` in inheritance). Task 7 has corresponding test categories.
5. **DC adapter underspecified**: Task 9 now spells out include-path normalization logic, the include vs using disambiguation problem, fully-qualification fallback, and test updates file-by-file.
6. **Missing test groups**: Task 7 restructured with `ImportExtraction` split into `IncludeDirectives` and `UsingDirectives`, added `ConstructorInitializers`, `TemplateConstraints`, `CallExpressions`, `InClassUsingDirectives`, `NamespaceAlias`, `ExternBlock`, `TemplateSpecializationAsBase`, `OutOfClassMethodDedup`.
7. **Phasing**: Task 8 (dc-compare) marked as interleaved with Tasks 3–5. Task 5's 14 categories remain one task but each is a TDD cycle.
8. **Boundary-exclusion helper scope**: Kept local to C++'s `UsedTypeExtractor` (not promoted to `TreeTraversal`) since no second caller exists. C# has no boundary-exclusion helper at all — it intentionally lets nested types leak (per Task 5 prose and `dependency-migration.md`). C++ is the first TSE language with boundary exclusion; the helper stays local until a second caller appears.
9. **Out-of-class method overloads**: Changed "skip on dup" to "merge on dup" in Task 4 — union `usedTypes` for same `(parentPath, name)` key. Matches DC's `BodyProcessor.consolidate`.
10. **Qualified type extraction — leftmost vs rightmost (correction)**: Original plan cited `dependency-migration.md`'s "first type identifier segment" rule and wrote "leftmost". Verified against DC's `TypeExtractionService.kt:23-29, 67-68`: DC emits the **rightmost** segment (`C` from `A::B::C`) as `Type.name` and the leftmost segments (`A::B`) as a separate wildcard `Dependency`. Plan Task 1 corrected. Added "namespace-prefix loss" as an accepted regression since TSE's `UsedType` model has no slot for the separate prefix.
11. **Filename collision — rename existing file, no subpackage**: The existing `languages/cpp/extractors/DeclarationExtractor.kt` (extraction feature, defines `extractFromDeclaration`) would collide with the new dependency `DeclarationExtractor.kt`. Resolution: rename the existing file to `GenericDeclarationExtractor.kt` (Task 0) so the dependency extractor can use the conventional name alongside the other extractors in the same directory. Matches Java/Kotlin/C# architecture where dependency and extraction-feature extractors share `languages/<lang>/extractors/`. Those languages had no collision because their extraction-feature files use very specific names (e.g., `InstanceofPatternVariableExtractor`, `UsingVariableExtractor`) — C++ is the only outlier because its extraction-feature files are named after AST node types, and the generic `declaration` node grabbed the short name.
12. **`ImportDeclaration` disambiguation — decided upfront**: `using X::Y;` and `#include "x"` both collapse to `(path, isWildcard=false, namespacePath=[])` in the current domain model, so the DC adapter cannot distinguish them. Added `ImportKind { STANDARD, INCLUDE }` enum + optional `kind` field to `shared/domain/DependencyResult.kt`. C++ `ImportExtractor` tags `#include` imports with `INCLUDE`, everything else defaults to `STANDARD`. Backward compatible with Java/Kotlin/C#.
13. **`DefineProcessor` mischaracterization removed**: Verified that DC's `DefineProcessor` handles preprocessor conditionals (`preproc_ifdef`/`postproc_else`), not `#define` macro bodies. The original "accepted regression" was meaningless. Declarations under `#ifdef`/`preproc_if` are caught naturally by `findAllDescendantsOfType`.
14. **BodyProcessor dispatch-order framing removed**: Task 5's concatenation order was framed as "matches DC's dispatch order" — it doesn't. DC's order is Comment → Using → Include → Namespace → TypeDeclaration → Inheritance → Method → TypeDef → Alias → GenericTemplate → Define. TSE's concatenation order is a design choice, not a replay of DC's.
15. **preproc-wrapped declarations test coverage added**: Task 7's `DeclarationExtraction` group now includes class/struct/function inside `#ifdef` (mirroring C# fix `623fa5e`), and `UsingDirectives` coverage includes nested-and-preproc-wrapped namespaces (mirroring the same C# commit's scope).
16. **C++ features added**: Trailing return types (`-> Foo`), `static_cast`/`dynamic_cast`/`reinterpret_cast`/`const_cast`, `noexcept(T)`/`sizeof(T)`/`alignof(T)`/`typeid(T)` all added to UsedTypeExtractor (new category 14 + expanded cats 2 and 9). Modules and concepts explicitly marked as accepted regressions.
17. **Namespace alias decision made**: Confirmed via DC code search that DC has no handler for `namespace_alias_definition`. TSE skips it entirely. No "verify empirically" deferral.
18. **Naming convention note added to Goal**: Plan acknowledges `PackageExtractor`/`ImportExtractor` differs from C#'s `NamespaceExtractor`/`UsingDirectiveExtractor` and explains why (matches domain field names + C++'s imports come from two AST sources).

## Notes

**Accepted improvements over DC legacy** (per Q1 decision and `dependency-migration.md` "fix bugs where possible"):

1. **Struct body extraction**: DC legacy's `TypeDeclarationProcessor.kt:115` gates body recursion on `nodeType == NodeType.CLASS`, so structs produce empty `usedTypes` and miss nested declarations. TSE treats struct_specifier like class_specifier. Structs containing types will show diffs in dc-compare — all additions, no removals.

2. **Struct label**: `struct_specifier → CLASS` (not `VALUECLASS`). Matches C# precedent (C# struct already lost VALUECLASS in its TSE migration). `VALUECLASS` has no downstream behavior in DC's resolver/levelizer/cycle analyzer — it's only a string label in cc.json.

3. **Nested declaration extraction**: DC C++ already extracts nested class/struct/enum via `TypeDeclarationProcessor`'s partition of top-level vs nested. TSE preserves this.

**Public API impact — summary (all changes verified as non-breaking for library consumers):**

| Change | Impact on downstream consumers |
|---|---|
| Rename `cpp/extractors/DeclarationExtractor.kt` → `GenericDeclarationExtractor.kt` | None — `internal fun extractFromDeclaration`, single internal caller |
| Add `ImportKind` enum to `shared/domain/DependencyResult.kt` | None — new type, additive |
| Add `kind: ImportKind = ImportKind.STANDARD` field to `ImportDeclaration` | None — default value preserves `ImportDeclaration(path, isWildcard, namespacePath)` construction; `.copy()`, destructuring, reads unchanged |
| Add `CppDependencyMapping` and override `CppDefinition.dependencyMapping` | None — `dependencyMapping` was already `null` by default (per `DependencyMapping` interface); non-null for C++ is the migration's intended effect |
| Update `README.md` + `integration/dependencies/README.md` | Docs only |
| DC adapter changes (Task 9) | In DC's repo, not TSE's — no impact on TSE consumers |

TSE's public API surface (`api/`, exposed `shared/domain/` types) remains source- and binary-compatible with previous releases for all read-only consumers. Positional construction of `ImportDeclaration` remains valid (new parameter has a default). If any consumer does `ImportDeclaration(path, isWildcard, namespacePath, someValue)` — not found in our grep — that would be the only way to break, and is not expected.

**Design parallels with C# migration** (see `feat/tse-csharp-integration`, merged PR #195):

- Class-2 namespace model: `parentPath = namespaceChain + parentClassChain`; DC adapter ignores `result.packagePath`
- Self-wildcard import emitted per-declaration from `parentPath` (not per-file from `packagePath`)
- Scoped imports matched on `namespacePath == declaration.parentPath`

**Separation boundaries between TSE and DC adapter**:

| Concern | Layer | Rationale |
|---|---|---|
| AST traversal, type name extraction | TSE | AST-level concern |
| Namespace/parent-class chain computation | TSE | AST-level concern |
| `parentPath` in `Declaration` | TSE | Needed by both Class-1 and Class-2 adapters |
| Relative-path resolution (`./ ../`) | DC adapter | Requires `physicalFilePath`, which TSE doesn't have |
| `transformFileEnding` (`.`→`_`) | DC adapter | DC-specific node-naming convention |
| Self-wildcard Dependency emission | DC adapter | DC-specific concept (Dependency ≠ ImportDeclaration) |
| Node consolidation across files | DC adapter | Cross-file concern, TSE parses one file at a time |

**Boundary exclusion implementation detail**: DC's scoped-context behavior is structural (each `BodyProcessor` call gets a fresh context). TSE emits flat `List<Declaration>` so boundary exclusion must be explicit in `UsedTypeExtractor` — skip descending into nested `class_specifier`/`struct_specifier`/`union_specifier`/`enum_specifier` subtrees. This is opposite of Kotlin's intentional leakage (Kotlin mirrors DC Kotlin's re-parsing) and aligns with Java's scoped behavior (per `dependency-migration.md` "Nested declarations" lesson).

**Verification strategy**: set up `/dc-compare` after PackageExtractor + IncludeExtractor work, not after everything's done. Migration rules: "The Kotlin migration went through 4 rounds (17k → 2.9k → 1.7k → 74 lines), each revealing issues that unit tests couldn't catch." Apply same iteration here.

## Session break — 2026-04-22 state

### What's done in this session
- Task 5 fully complete (14 extractor categories + boundary exclusion + out-of-class method bodies + order-pin integration test). 11 commits (`e530f49` → `400083e`) grouped per the user-agreed "bundle related categories" scheme: type aliases, template constraints, fields+vars, casts, instantiation sites, friend+in-class using, type operands, boundary exclusion, out-of-class bodies.
- Plus `1f03d92` — `function_declarator` param extraction (caught method decls in headers).
- Four dc-compare rounds recorded. Switched baseline from Catch2 to cppcheck because Catch2 ships an amalgamated header (`extras/catch_amalgamated.hpp`) that duplicates every class, triggering DC's `mergeDuplicates` and polluting the diff.

### Where the build stands
- `feat/cpp-dependency-support` green on `./gradlew build` (ktlint, detekt, all tests).
- `CppDependencyTest` covers every category with `@Nested` groups + one comprehensive integration test.
- Composite build wiring on DC's `feat/cpp-dependency-integration` intact (uncommitted edits in `analysis/settings.gradle.kts` + `analysis/build.gradle.kts` — revert before merging DC).

### Open follow-ups (backlog, roughly in priority order)

**Resolver-side gap (blocks meaningful dep parity)** — Round 4 cppcheck: 616 shared declaration ids, but only 467/2455 cross-file deps match (1988 main-only). The gap is in DC's resolver, not TSE's extractor:
- `TseMappings.UsedType.toType()` collapses every type to `TypeOfUsage.USAGE`; DC legacy distinguishes INSTANTIATION/ARGUMENT/RETURN_VALUE and some resolver paths may branch on that.
- DC legacy seeds its resolver with `Dependency(Path([NS]), isWildcard=true)` per qualified type (namespace-prefix wildcard). TSE's `UsedType` model has no slot for this (Task 1 accepted regression). The DC adapter currently cannot recreate these namespace wildcards from just the import list, so the resolver loses matches for `A::B::Settings`-style usages.
- Fix requires DC-side adapter work, not TSE categories. Either (a) thread `TypeOfUsage` through `UsedType` or (b) have the DC adapter synthesize namespace-prefix wildcards from the qualified callees it sees — but the latter needs more info than TSE currently exposes.

**TSE-side edge cases** (backlog, low priority):
- 8 main-only declaration ids on cppcheck have unusual `::` separators in parent path (`Library::LibraryData.Platform`, `PathMatch::PathIterator.Pos`). Likely inner types inside member structs that our `findParentClassPath` doesn't handle.
- 11 `Catch.Detail.Catch.ExprLhs.*` duplicate-segment paths from Catch2's amalgamated file — `extractOutOfClassDeclarations` double-counts the namespace when a fully-qualified out-of-class method is defined inside its own namespace. Only affects amalgamated file; not visible on cppcheck.
- Template specializations (`Catch.StringMaker<std::string>`) are not synthesized as separate declarations. DC legacy does. Needs a `template_declaration` handler that emits one declaration per specialization.
- `typeid(T)` parses as `call_expression` with identifier function, no dedicated node — skipped per Task 5 cat 14 note.

### Resume instructions

**Step 1 — continue the migration (priority order):**
1. **Task 6**: replace the inline stubs in `languages/cpp/CppDependencyMapping.kt` with real references to `PackageExtractor`, `ImportExtractor`, `DeclarationExtractor`, `UsedTypeExtractor`. Confirm `TreeSitterDependencies.analyze(code, Language.CPP)` works end-to-end (the composite build run during dc-compare R4 confirms the chain already does, but the mapping itself may still be pointing to temporary stubs).
2. **Task 7**: consolidate `CppDependencyTest` — mirror recent C# test-file conventions, add `ApiSupportCheck` membership test, verify no section-comment-style grouping anywhere.
3. **Task 9 (DC side)**: finish the adapter on `feat/cpp-dependency-integration`. Delete `analyzers/cpp/processing/`, `analyzers/cpp/model/`, `CppUtils.kt`, `CppQueryFactory.kt`, `FunctionArgumentParser.kt`, `TypeExtractionService.kt`. Update DC tests as documented in Task 9. Revert composite-build overrides before committing.
4. **Resolver gap**: decide whether to fix now (with adapter changes) or release as-is and tackle in a follow-up migration.
5. **Task 10**: release TSE, bump DC's JitPack dep, merge DC.

**Step 2 — to re-run dc-compare (round 5+):**
- Golden main at `../dc-compare/main/` is cppcheck v@HEAD (depth-1 clone 2026-04-22). Reuse as-is unless cppcheck is re-cloned.
- From TSE: `./gradlew ktlintFormat build` (confirm green).
- From DC: `cd ../DependaCharta/analysis && ./gradlew fatJar && java -jar build/libs/dependacharta.jar -d "../../cppcheck" -o ../../dc-compare/feature -f analysis`
- Compare via `/dc-compare ../cppcheck` or the node normalizer snippet in the skill.

**Step 3 — the comparative node script I was using** (drop into a Bash block):
```js
node -e "
const fs = require('fs');
const m = JSON.parse(fs.readFileSync('../dc-compare/main/analysis.cg.json','utf8'));
const f = JSON.parse(fs.readFileSync('../dc-compare/feature/analysis.cg.json','utf8'));
const mL = Object.keys(m.leaves), fL = Object.keys(f.leaves);
let mD=0, fD=0;
Object.values(m.leaves).forEach(v => mD += Object.keys(v.dependencies||{}).length);
Object.values(f.leaves).forEach(v => fD += Object.keys(v.dependencies||{}).length);
const shared = mL.filter(k => fL.includes(k));
let matched=0, mainOnly=0;
for (const k of shared) {
  const md = Object.keys(m.leaves[k].dependencies || {});
  const fd = Object.keys(f.leaves[k].dependencies || {});
  matched += md.filter(d => fd.includes(d)).length;
  mainOnly += md.filter(d => !fd.includes(d)).length;
}
console.log('nodes:', mL.length, 'vs', fL.length);
console.log('deps:', mD, 'vs', fD);
console.log('shared nodes', shared.length, ': matched', matched, ', main-only', mainOnly);
"
```

## Session break — 2026-04-22 (later, after Tasks 6/7 + namespacePrefix + Issue 1 investigation)

### What's done in this session

1. **Task 6** confirmed complete on inspection — `CppDependencyMapping` already references real extractors, `CppDefinition.dependencyMapping` override in place. Plan checkbox flipped. (commit `e70233b`)
2. **Task 7** complete — `ApiSupportCheck` membership test added to `CppDependencyTest`; rest of `@Nested` structure already mirrored C# / Java conventions. (commit `ae5a176`)
3. **`namespacePrefix` mechanism added** — `UsedType.namespacePrefix: List<String>` field for capturing scope segments in qualified type references. C++ extractors populate it; DC adapter synthesizes wildcard `Dependency` per qualified usage. Closes ~52 cppcheck cross-file deps (round 5: 467 → 519 matched). Non-breaking for Java/Kotlin/C#.
   - TSE commits: `36e00d7` (domain), `3a6a21a` (docs + C++ motivation), `b87cfa7` (extractor), `3275d69` (round 5 + lesson)
   - DC commit: `a4a73c9` on `feat/cpp-dependency-integration` (synthesizes wildcards + disabled Issue 1 test)
4. **Documented C++-specific motivation** for `namespacePrefix` in `integration/dependencies/README.md` (new "Namespace-prefix handling" section) and `.claude/rules/dependency-migration.md` (new lesson — useful when migrating PHP).
5. **Option-3 sampling** — investigated the 1936 main-only deps remaining after namespacePrefix. Two specific issues identified plus small false-positive contribution (commit `9372daa` — see "Round 5 follow-up" entry above).
6. **Reverted Issue 1 fix** — strip-cpp-extension implementation worked at unit level (`should produce matching pathWithName for class declared in header and its implementation file` test passed), but blocked on a resolver mystery. Kept the test as `@Disabled` for future work.

### Where the build stands
- `feat/cpp-dependency-support` (TSE): green on `./gradlew build`. All tests passing.
- `feat/cpp-dependency-integration` (DC): commit `a4a73c9` is the latest. CppAnalyzerTest has 9 pre-existing failures from the original adapter rewrite (`7288351`) — those are documented in Task 9's pending test updates and are NOT introduced by this session's work.
- Composite-build wiring on DC still uncommitted: `analysis/settings.gradle.kts` + `analysis/build.gradle.kts` — revert before any DC merge.
- dc-compare round 5 baseline restored: 519 matched / 1936 main-only / 150 feat-only.

### Open follow-ups (in priority order for next session)

**1. Live-debug DC main's resolver to crack Issue 1's mystery (BLOCKER for the rest)**

Issue 1 (`.h`/`.cpp` declarations don't merge in pipeline) attempted fix is on the disabled-test branch of work. The fix made the merge happen but didn't close the dep gap because:
- `#include "settings.h"` produces dep path `[settings]` (just filename)
- Settings class lives at `[lib, settings, Settings]`
- `Node.resolveTypeImport` and `resolveComplexModuleImport` were traced line-by-line; no existing rule matches `[settings]` against `[lib, settings, Settings]`.
- **Yet DC main resolves the same shape** (legacy paths `[settings_h]` vs `[lib, settings_cpp, Settings]` — same mismatch). There's a resolver mechanism we haven't located.

**Recommended: set a breakpoint** in `DependaCharta/.../model/Node.kt:resolveTypeImport` for a specific case (e.g., resolving `Settings` from `cli.cmdlineparser_cpp.CmdLineParser`). Run DC main against cppcheck. Step through. Find which line returns the resolved Path. That'll tell us:
- Whether the resolver has unexpected logic
- Whether the dependency or usedType has unexpected shape
- What the actual mechanism is

Without this, any further Issue-1 work is speculation. With it, the path forward is clear.

**2. Implement Issue 2 — static method-call extraction (~15-25% of remaining gap)**

For `Path::removeQuotationMarks()`, TSE currently extracts `UsedType("removeQuotationMarks", namespacePrefix=["Path"])`. Should ALSO emit `UsedType("Path")` because `Path` is the actual class being referenced. DC legacy's `VariableDeclarationProcessor.kt:24-25` does this via dedicated TSQuery: `(call_expression function: (qualified_identifier scope: (namespace_identifier)@type))`.

TDD steps:
- Failing test in TSE `CppDependencyTest.UsedTypeExtraction.InstantiationSites`:
  ```kotlin
  @Test
  fun `should extract scope as type for single-scope qualified static call`() {
      val code = """class Container { void doWork() { Path::removeQuotationMarks(x); } };"""
      val result = TreeSitterDependencies.analyze(code, Language.CPP)
      val container = result.declarations.single { it.name == "Container" }
      assertThat(container.usedTypes).contains(UsedType(name = "Path"))
  }
  ```
- Implement in `UsedTypeExtractor.extractInstantiationTypes` — for `call_expression` with `function = qualified_identifier`, additionally emit `UsedType(scope_text)` IF scope is a `namespace_identifier` (single scope, not nested qualified_identifier).
- Re-run dc-compare; expect another bump in matched deps.

**Note**: tackling Issue 2 BEFORE Issue 1 is cracked is fine — they're independent. Both gains will compound when Issue 1 is fixed.

**3. Decision point after Issues 1+2 are fixed**

Re-run dc-compare. If matched/total reaches ~80-90%, ship. If still big gap, repeat sampling on what's left.

### Re-enable Issue 1 fix (when resolver mystery is solved)

The Issue 1 fix code was reverted but the disabled test is preserved. To re-enable:
1. Remove `@org.junit.jupiter.api.Disabled` from `CppAnalyzerTest > should produce matching pathWithName for class declared in header and its implementation file`.
2. Reapply the strip-cpp-extension fix in `CppAnalyzer.kt`. Working version was at git ref before commit `a4a73c9` modulo the namespacePrefix lines. Specifically:
   - Add `companion object { private val CPP_FILE_EXTENSIONS = setOf("h", "hpp", "hxx", "hh", "cpp", "cc", "cxx", "c", "c++"); private fun stripCppFileExtension(segment: String): String { val dot = segment.lastIndexOf('.'); if (dot <= 0) return segment; val ext = segment.substring(dot + 1).lowercase(); return if (ext in CPP_FILE_EXTENSIONS) segment.substring(0, dot) else segment } }`
   - In `analyze()`: replace `val physicalPathParts = splitNameToParts(fileInfo.physicalPath).filter { it != "." }` with the stripped version (apply `stripCppFileExtension` to last segment).
   - In `normalize()`: strip extensions BEFORE constructing `Path` (Path constructor's `replaceDots` mangles the dot otherwise).
3. Re-run dc-compare. If resolver mystery is solved, gap should now close meaningfully.

### Resume instructions for tomorrow

**Step 1 — get back to current state:**
```bash
# TSE
cd <workspace>/TreeSitterExcavationSite
git checkout feat/cpp-dependency-support
./gradlew build  # confirm green

# DC
cd ../DependaCharta
git checkout feat/cpp-dependency-integration
git status  # should show modified analysis/build.gradle.kts + analysis/settings.gradle.kts (composite-build wiring; uncommitted by design)
cd analysis && ./gradlew compileKotlin  # confirm composite build still works
```

**Step 2 — for Issue 1 live debugging:**
- Open `DependaCharta` in IntelliJ on `main` branch (NOT `feat/cpp-dependency-integration` — we want the legacy analyzer).
- Set breakpoint in `Node.kt:resolveTypeImport` line 92 (`val plainTypeName = fullName.split(".").last()`).
- Add conditional: `plainTypeName == "Settings" && pathWithName.withDots().contains("CmdLineParser")`.
- Run `dependacharta-analysis` Cli main with args `-d "<workspace>/cppcheck" -o "<workspace>/dc-compare/main-debug" -f analysis -c`.
- Step through resolution. Record which line returns the matching Path.

**Step 3 — for Issue 2 fix (if working alone):**
- TDD on TSE branch as outlined above.
- Re-run dc-compare to measure isolated impact.

**Step 4 — to re-run dc-compare (still cppcheck baseline):**
```bash
cd <workspace>/DependaCharta/analysis
./gradlew fatJar
java -jar build/libs/dependacharta.jar -d "../../cppcheck" -o "../../dc-compare/feature" -f analysis -c
# then comparison script (Step 3 in earlier session-break section)
```

### What NOT to do

- Don't reapply Issue 1 fix until the resolver mystery is solved — it changes node IDs in dc-compare-incompatible ways without closing the gap.
- Don't commit composite-build overrides on DC — must be reverted before any merge to DC main.
- Don't push DC `feat/cpp-dependency-integration` to remote yet — Task 9's test cleanups (the 9 pre-existing failures) still need to be done first.

### Task / commit summary for this session

| Task | Commits | Status |
|---|---|---|
| Task 6 | `e70233b` | ✅ done (was already wired, plan reconciled) |
| Task 7 | `ae5a176` | ✅ done |
| `namespacePrefix` (cross-cutting feature) | TSE: `36e00d7`, `3a6a21a`, `b87cfa7`, `3275d69` / DC: `a4a73c9` | ✅ done |
| Option-3 sampling + Issue analysis | `9372daa` | ✅ done (findings recorded) |
| Issue 1 fix | DC: `20ea8a4` | ✅ done — unconditional selfWildcard; +358 matched cppcheck deps (R6 → R7) |
| Issue 2 fix | `4891922` | ✅ done — +112 matched cppcheck deps (R5 → R6) |
| Issue 3 fix | `d189b57` | ✅ done — qualified_identifier as type node; +91 matched (R7 → R8) |
| Issue 4 fix | `8cfc2a2` | ✅ done — throw-statement callee extraction; +9 matched (R9 → R10) |
| Concat order | `69bca49` | ✅ done — match DC BodyProcessor order; neutral on cppcheck |
| Constructor regression guard | `bfc75ce` | ✅ done — pins out-of-class constructor param extraction |

## Session break — 2026-04-23

### What's done in this session

1. **Constructor regression test committed** (`bfc75ce`). Yesterday's uncommitted test for `Executor::Executor(const Settings& settings, ErrorLogger& errorLogger)` already passed; kept as a regression guard since constructors have no leading return-type node and future changes to `extractMethodReturnAndParamTypes` could silently break this.
2. **Issue 2 implemented** (`4891922`) — `UsedTypeExtractor.extractInstantiationTypes` now also emits the immediate scope of a qualified call as its own `UsedType` via a new `CppTypeHelper.extractSingleSegmentScope` helper. Mirrors DC's `VariableDeclarationProcessor.kt:24-25` TSQuery.
3. **Tree-sitter-cpp parses qualified identifiers right-associatively** — discovered this during the TDD red step. For `A::B::C::helper()`, the scope of the top-level `qualified_identifier` is `namespace_identifier("A")`, not a nested qualified_identifier. DC's TSQuery matches and emits `Type("A")` (captured node is the bare `namespace_identifier`, DC's while-loop in `extractTypeWithFoundNamespacesAsDependencies` never fires because its `node.type` is already not `qualified_identifier`). TSE matches this behavior. Updated `should capture multi-segment namespace prefix on qualified call` to assert both emissions.

### Round 6 dc-compare (cppcheck) — Issue 2 isolated impact

| | R5 | R6 | Δ |
|---|---|---|---|
| nodes | 616 | 624 vs 947 | shared unchanged |
| deps | 1998 vs ? | 2467 vs 926 | — |
| matched | 519 | 631 | **+112** |
| main-only | 1936 | 1824 | **-112** |
| feat-only | 150 | 173 | +23 |

Match rate: 21.1% → 25.7%. Below the plan's estimated 15-25% of the gap (would have been 290-484), but a clean directional win. The +23 feat-only is expected — new scope-as-type emissions create deps DC main doesn't emit, typically single-segment scopes that resolve against common namespaces.

### Issue 1 mechanism (cracked via static research, no debugger needed)

The `#include "settings.h"` path never actually resolves through the include dependency. DC legacy's resolution goes:

1. `analyzers/cpp/CppUtils.kt:41` (`createNode`) unconditionally appends `Dependency(namespace, isWildcard=true)` to every node's dependencies, including when `namespace = Path(emptyList())` (file-scope declarations).
2. `DependencyResolverService.resolveNodes` builds `projectDictionary = nodes.groupBy { it.pathWithName.parts.last() }` — indexed by simple name.
3. `Node.kt:134-141` unqualified-name fallback iterates wildcards and does `it.withDots().contains(wildcard.withDots())` — `String.contains`, not List. With an empty-namespace wildcard, `wildcard.withDots() == ""`, and `"lib.settings_h.Settings".contains("")` is trivially true. Effect: resolver picks the first project-wide node whose simple name matches `fullName`. DC's `Node.kt:136` has an in-code TODO flagging this as too loose.

The feature-branch `CppAnalyzer.kt:36-40` guarded `selfWildcard` behind `if (parentPath.isNotEmpty())`, so file-scope declarations got no wildcard and the simple-name fallback never fired. **Fix: always emit `Dependency(Path(parentPath), isWildcard=true)`.**

### Round 7 dc-compare (cppcheck) — Issue 1 isolated impact

| | R6 | R7 | Δ |
|---|---|---|---|
| matched | 631 | **989** | **+358** |
| main-only | 1824 | 1466 | -358 |
| feat-only | 173 | 356 | +183 |

Match rate: 25.7% → **40.3%**. 3x the gain from Issue 2. The +183 feat-only is the expected cost of loose simple-name matching; DC main has the same looseness but lands on slightly different candidates via groupBy iteration order.

### Rounds 8-10 summary

| Round | Change | matched | main-only | feat-only | rate |
|---|---|---|---|---|---|
| R5 | baseline | 519 | 1936 | 150 | 21.1% |
| R6 | Issue 2 (scope-as-type) | 631 | 1824 | 173 | 25.7% |
| R7 | Issue 1 (DC: selfWildcard) | 989 | 1466 | 356 | 40.3% |
| R8 | Issue 3 (qualified_identifier as type) | 1080 | 1375 | 470 | 43.9% |
| R9 | concat reorder | 1081 | 1374 | 469 | 44.0% |
| R10 | Issue 4 (throw callee) | 1090 | 1365 | 470 | 44.4% |

### Bucket analysis of remaining 1365 main-only (post-R10)

Classification via script against the R10 cg.json:

| Category | Count | Can we close? |
|---|---|---|
| Misdirection (resolver picks different same-simple-name candidate) | 167 | No — inherent to DC's loose simple-name resolver + groupBy iteration order |
| DC misattribution (dump-to-last-node in multi-class files) | ~700-900 | No — would require replicating a DC legacy bug |
| Genuine extraction gaps | ~300-500 | Yes — additional AST patterns (Issue 5 bare-identifier-call, etc.) |

**Key bucket-analysis finding**: 411 of 1186 not-emitted (34.7%) come from a single file — `lib/valueflow.cpp`. Top 4 files (`valueflow_cpp` 411, `symboldatabase_cpp` 84, `vf_analyzers_cpp` 49, `checkclass_cpp` 44) account for 588 deps (~50% of not-emitted). All four are multi-class-per-file scenarios where DC's misattribution bug pollutes every node.

### Third DC bug discovered: dump-to-last-node misattribution

In DC's `analyzers/cpp/processing/BodyProcessor.kt:86-95`, `addTypesAndDependenciesToRelatedNode` uses `this.lastOrNull()` — the most-recently-added node — as the target for accumulated types:

```kotlin
private fun MutableList<Node>.addTypesAndDependenciesToRelatedNode(processorResult: CppContext): MutableList<Node> {
    val relatedNode = this.lastOrNull() ?: return this
    ...
}
```

For a file like `lib/valueflow.cpp` with ~30 sequential class/struct declarations interleaved with free functions, every free function's type usages get dumped onto the most recent declaration node — NOT the semantic owner. Example: `lib.valueflow_cpp.ValueFlowPass` is a 12-line struct with one real dependency (`ValueFlowState`), but DC attributes 32 deps to it including `Path`, `ErrorMessage`, `simpleMatch`, etc. Feature correctly scopes to the actual owner (enclosing class for methods, file-scope for free functions if they had declarations — which they don't).

### Three DC legacy bugs surfaced, none mirrored by feature

| Bug | Effect in DC main | Feature behavior |
|---|---|---|
| Header parse crash (`Node is a null node`) | ~121 `.h`-only classes missing from main's output | Feature correctly extracts all 179 `_h` nodes |
| Empty-namespace wildcard + `String.contains("")` in resolver | Every simple name matches first project candidate regardless of context | Feature benefits (Issue 1 fix) — same loose-match behavior |
| `addTypesAndDependenciesToRelatedNode` dumps on `lastOrNull()` | ~700-900 deps misattributed to wrong leaf nodes | Feature correctly scopes types to semantic owner |

The 167 misdirection + 700-900 misattribution is a ceiling for correctness-respecting matching against cppcheck main. Match rate above ~50-55% requires mirroring DC bugs.

### What's left for extraction work

~300-500 genuine extraction gaps. Candidates to pursue via TDD:

1. **Issue 5: bare-identifier call in argument list** — DC's `(argument_list (call_expression function:(identifier)@potential_constructor))` pattern. `push_back(Widget(x))` → emit `UsedType("Widget")`. Likely produces modest gains (~30-80 matched) but also adds feat-only noise for function calls that aren't constructor-like.
2. **Issue 6: primitive_type in field/var declarations** — `int x;`, `size_t n;`. Low impact (resolves to C++ standard library externally).
3. **Nested type references `A::B` as constants** — e.g., `FilePath.ANY` or `SuppressionList.ErrorMessage`. Some of these are already extracted via second-to-last segment (`ConstructorInitializers`); likely more patterns exist.

### Resume instructions

- TSE: `feat/cpp-dependency-support` at `8cfc2a2`, green on `./gradlew build`.
- DC: `feat/cpp-dependency-integration` at `20ea8a4`, composite-build wiring uncommitted on `analysis/settings.gradle.kts` + `analysis/build.gradle.kts` — revert before any DC merge. Note: composite-build wiring gets lost when checking out main for debugging; always confirm with `grep includeBuild analysis/settings.gradle.kts`.
- dc-compare baseline: cppcheck depth-1 clone at `../cppcheck/`. Main golden at `../dc-compare/main/`. Feature output at `../dc-compare/feature/`.
- **Watch for branch drift**: if TSE is on `main`, composite build ships TSE main (no `namespacePrefix`/`ImportKind`) and DC's feature branch fails to compile. Always confirm `git branch --show-current` on TSE before rebuilding DC.
