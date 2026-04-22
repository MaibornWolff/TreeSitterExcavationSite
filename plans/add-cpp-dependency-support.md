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
| 5. `UsedTypeExtractor` (14 cats + boundary exclusion) | ▶ in progress (9/14 cats) | `7d5bc19` → (cat 9) |
| 6. Wire `CppDependencyMapping` | 🔶 partial (stubs in place from Task 2) | `88d5eff` |
| 7. Test consolidation | ⏳ pending | — |
| 8. dc-compare iteration on Catch2 | ▶ round 1 done (structure OK, deps blocked on Task 5) | `7288351` (DC), local composite build |
| 9. DC adapter (`feat/cpp-dependency-integration`) | 🔶 partial — analyzer rewrite committed, legacy files still on disk | DC: `7288351` |
| 10. Release + integrate | ⏳ pending | — |

## Goal

Migrate DependaCharta's legacy C++ dependency analyzer to TSE. Add `CppDependencyMapping` composed of an `ImportExtractor` (covers both `#include` and `using namespace`/`using X::Y`), `PackageExtractor`, `DeclarationExtractor`, `UsedTypeExtractor`, plus a `CppTypeHelper`. All new files live directly under `languages/cpp/extractors/` alongside existing extractors — matches the Java/Kotlin/C# pattern where dependency and extraction-feature extractors share one directory. One collision exists: the existing `languages/cpp/extractors/DeclarationExtractor.kt` is an extraction-feature file (defines `extractFromDeclaration` for the generic `declaration` AST node) and must be renamed so the dependency `DeclarationExtractor` can use the conventional name. Follow the C# Class-2 namespace pattern (multiple/nested namespaces per file; `parentPath = namespaceChain + parentClassChain`). Verify behavior parity with DC main via `dc-compare` on Catch2, then replace DC's `CppAnalyzer` with a thin adapter mirroring `CSharpAnalyzer`.

**Naming convention note**: This plan uses `PackageExtractor` / `ImportExtractor` (following Java/Kotlin precedent and matching the `DependencyResult.packagePath` / `DependencyResult.imports` field names). C# uses `NamespaceExtractor` / `UsingDirectiveExtractor`. Both are valid; the Java/Kotlin names fit C++ better because C++'s `imports` come from two distinct AST sources (`#include` + `using`), so a unified `ImportExtractor` is more accurate than a source-specific name.

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
  - [ ] 10. New expression types
  - [ ] 11. Call expression types
  - [ ] 12. Friend declaration types
  - [ ] 13. In-class using directive types
  - [ ] 14. Type operands (sizeof/noexcept/alignof/typeid)
  - [ ] Boundary exclusion helper (category 15)
  - [ ] `extractFromFunctionBody` secondary entry point (category 16)
  - [ ] Pin concatenation order (category 17)

  **Resume notes for next session:**
  - `UsedTypeExtractor` + `CppTypeHelper` scaffolding lives in `languages/cpp/extractors/`. `DeclarationExtractor` calls `UsedTypeExtractor.extract(declarationNode, sourceCode)` (cycle 1 wired it in place of the earlier `emptySet()` stub).
  - `CppTypeHelper.isTypeNode` currently recognizes `type_identifier` + `template_type` only. `primitive_type` and `sized_type_specifier` are intentionally out until they matter for a category (method param types with primitives are skipped for now — DC legacy does the same for their own primitive-stripping logic).
  - `TYPE_DESCRIPTOR` unwrap pattern is already in two places (`extractTrailingReturnType` and `extractGenericArgument`). Future categories that reference `type_descriptor` should share a helper if a third use appears.
  - Out-of-class method declarations currently get `usedTypes = emptySet()` because `extractOutOfClassDeclarations` does not call `UsedTypeExtractor` yet. Category 16 (`extractFromFunctionBody`) will change that — scope the walk to the function's params + return + body.
  - `extractOutOfClassDeclarations` passes individual declarations through `UsedTypeExtractor.extract(declarationNode, ...)` implicitly for *explicit* class/struct decls; synthetic decls from out-of-class methods bypass it and will need category 16 routing.
  - Boundary exclusion: needs a local private helper in `UsedTypeExtractor` that recurses like `TreeTraversal.findAllDescendantsGroupedByType` but stops at nested `class_specifier`/`struct_specifier`/`union_specifier`/`enum_specifier` — so types referenced *inside* a nested class body don't leak upward into the outer class's `usedTypes`. Keep the helper local until a second TSE caller appears.

- [ ] Complete Task 6: `CppDependencyMapping` wiring + `CppDefinition` update — **partially done**: mapping + override exist with stubs from Task 2; still need replacement of inline stubs as Tasks 3 and 4 land
- [ ] Complete Task 7: `CppDependencyTest` with `@Nested` groups mirroring C#
- [ ] Complete Task 8: dc-compare iteration against Catch2 v3 (interleaved with Tasks 3–5)
  - **Round 1 (2026-04-21)**: ran composite build against `../Catch2` (depth-1 clone). Output in `../dc-compare/main/` (golden) and `../dc-compare/feature/`. Structural parity confirmed after adding physical-path prefix for file-scope decls in the DC adapter (`fullyQualify` equivalent). Remaining diffs: +150 feature-only nodes (files DC legacy errored on — accepted improvement), ~20 main-only nodes (DC legacy namespace-loss duplicates — accepted improvement), ~5 genuine gaps (template-specialized struct/class names like `Catch.StringMaker<std::string>` and typedef-like decls — will re-check after Task 5 lands alias/typedef + richer template_type handling). Dependencies empty on C++ nodes because `usedTypes` was empty at round time — rerun after Task 5 to unblock dep/cycle comparison.
  - **Round 2 (2026-04-22, after cats 1–9)**: re-ran composite build. Output: `../dc-compare/feature/analysis.cg.json`.
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
