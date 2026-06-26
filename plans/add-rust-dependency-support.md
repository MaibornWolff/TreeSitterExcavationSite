---
name: Rust Dependency Support
issue:
state: progress
version:
---

## Goal

Add **dependency analysis** for Rust to TSE, so `TreeSitterDependencies.analyze(code, Language.RUST)`
returns a populated `DependencyResult` (package/module path, `use` imports, declarations with used
types). This is **PR 1 of 2** — DependaCharta consumes it in a follow-up PR after this is tagged.

Scope is **full parity with the other migrated languages** (Java/Kotlin/C#): comprehensive, not a
reduced subset. Rust's nested `mod` blocks make it a **Class-2 / multi-namespace language** (same
family as C# and C++), so per-declaration `parentPath` carries the in-file module chain — `packagePath`
is informational only. Used types are extracted from **declaration signatures** (fields, params,
returns, generic bounds, where-clauses, supertraits, `impl Trait for Type`), exactly like every other
TSE language — no function-body usage. Nothing is filtered out (test modules included).

## Decisions (resolved before planning)

- **Module model: Class 2.** Inline `mod a { mod b { … } }` blocks are legal and nest arbitrarily,
  each its own namespace with its own scoped `use` imports. A single file-level `packagePath` cannot
  represent this, so each `Declaration` gets a `parentPath` = its inline-module chain. Follow the C#
  implementation (`languages/csharp/`) as the template on both sides. See README "Namespace models:
  single-namespace vs multi-namespace languages".
- **File→module path is DC's job, not TSE's.** A `.rs` file's module path from the crate root
  (`src/foo/bar.rs` → `crate::foo::bar`) is filesystem-derived and not visible in file content. TSE
  only sees content (same constraint that makes import-alias resolution DC-side). TSE therefore:
  emits `packagePath = []`, emits `parentPath` = the **in-file** inline-`mod` chain only, and emits
  `use` paths **verbatim including leading `crate`/`self`/`super` segments**. DC normalizes those and
  prepends the file module path (modeled on `GoAnalyzer.derivePackagePathFromFilePath`).
- **Used types: signatures only**, matching all other TSE languages. No `let x: T`, `T::new()`, or
  struct-literal body usage.
- **Qualified inline types populate `UsedType.namespacePrefix`** (like C++): `crate::a::Foo` used
  inline → `UsedType("Foo", namespacePrefix=["crate","a"])`. The DC adapter emits a synthetic
  wildcard from the prefix (existing resolver path). Imports via `use` carry the neighborhood for the
  common case; the prefix covers fully-qualified inline references without a `use`.
- **No filtering.** `#[cfg(test)] mod tests`, `#[cfg(...)]`-gated items, etc. are all extracted
  (over-approximate, like the other analyzers which don't special-case test code).
- **Grammar/parser already wired.** `Language.RUST`, `LanguageRegistry`, and the
  `tree-sitter-rust:0.24.0` binding all exist from the extraction work — nothing to add there.

## Current state (from research)

- `RustDefinition` (`languages/rust/RustDefinition.kt`) overrides only `nodeMetrics` (empty) and
  `nodeExtractions`. It does **not** override `dependencyMapping`, so
  `definition.isDependencyMappingSupported` is `false` and `TreeSitterDependencies.analyze(_, RUST)`
  throws `UnsupportedOperationException`. Adding the override is the single existing-file edit.
- `LanguageDefinition` mixes in `DependencyMapping`, which exposes a nullable
  `dependencyMapping: LanguageDependencyMapping?` (default `null`) and a derived
  `isDependencyMappingSupported`. `TreeSitterDependencies.getSupportedLanguages()` is
  `Language.entries.filter { isDependencyAnalysisSupported(it) }`, so RUST is auto-added once the
  override lands — **no API/enum/registry edits needed**.
- C# is the reference Class-2 implementation. Reuse its extractor shapes:
  `languages/csharp/CSharpDependencyMapping.kt`, `extractors/{NamespaceExtractor, UsingDirectiveExtractor,
  DeclarationExtractor, UsedTypeExtractor}.kt`, `CSharpTypeHelper.kt`.
- Existing Rust extractors (`languages/rust/extractors/{LetBinding, RustComment, StringLiteral}Extractor.kt`)
  are extraction-only and untouched by this work.

## Affected files

| File | Change | Why |
|------|--------|-----|
| `languages/rust/RustDefinition.kt` | add `override val dependencyMapping = RustDependencyMapping.dependencyMapping` (+ import) | flips support flag; the only existing-file edit |
| `languages/rust/RustDependencyMapping.kt` | **new** — `object` exposing `LanguageDependencyMapping(extractPackagePath, extractImports, extractDeclarations)` | wires the 3 lambdas |
| `languages/rust/extractors/PackageExtractor.kt` | **new** — returns `emptyList()` (file module path is DC's job) | satisfies signature; documents the decision |
| `languages/rust/extractors/ImportExtractor.kt` | **new** — `use_declaration` → flattened `ImportDeclaration`s | imports |
| `languages/rust/extractors/DeclarationExtractor.kt` | **new** — all item kinds + inline-mod `parentPath` + impl aggregation | declarations |
| `languages/rust/extractors/UsedTypeExtractor.kt` | **new** — signature types per declaration | used types |
| `languages/rust/extractors/RustTypeHelper.kt` | **new** — unwrap generic/ref/array/tuple, `scoped_type_identifier`→`namespacePrefix`, skip primitives/lifetimes/`Self` | shared type parsing |
| `src/test/kotlin/.../languages/rust/RustDependencyTest.kt` | **new** — `@Nested` Package/Import/Declaration/UsedType/ApiSupportCheck | TDD coverage |
| `README.md` (dependencies feature) | add Rust rows to namespace-model + concatenation-order tables | docs parity |
| `CHANGELOG.md` | add `feat(rust): add Rust dependency analysis support` | release notes |

**No edits needed (confirmed present):** `shared/domain/Language.kt`, `languages/LanguageRegistry.kt`,
`api/TreeSitterDependencies.kt`, `api/DependencyTypes.kt`, `build.gradle.kts`,
`gradle/verification-metadata.xml`.

## Rust → DependencyResult mapping

**Imports** (`use_declaration`; flatten every use-tree leaf to one `ImportDeclaration`, `kind = STANDARD`):

| Rust | node types | ImportDeclaration |
|---|---|---|
| `use a::b::C;` | `scoped_identifier` | path `[a,b,C]`, wildcard `false`, `bindingName="C"` |
| `use a::{b, c::D};` | `scoped_use_list` + `use_list` | one per leaf: `[a,b]`, `[a,c,D]` |
| `use a::b::*;` | `use_wildcard` | path `[a,b]`, **`isWildcard=true`** |
| `use a::B as C;` | `use_as_clause` | path `[a,B]`, `bindingName="C"` |
| `pub use a::B;` | `visibility_modifier` child | path `[a,B]`, **`kind=REEXPORT`** (DC models it as a forwarding/alias edge — see SHCBarber validation note) |
| leading `crate`/`self`/`super` | `crate`/`self`/`super` tokens | kept **verbatim** as first path segment (DC normalizes) |

**Declarations** (recursive; record the enclosing inline-`mod` chain as `parentPath`):

| Rust node | name via | DeclarationType |
|---|---|---|
| `struct_item`, `union_item` | `type_identifier` | `CLASS` |
| `enum_item` | `type_identifier` | `ENUM` |
| `trait_item` | `type_identifier` | `INTERFACE` |
| `type_item` (alias) | `type_identifier` | `CLASS` |
| `function_item`, `function_signature_item` | `identifier` | `FUNCTION` |
| `const_item`, `static_item` | `identifier` | `VARIABLE` |
| `macro_definition` | `identifier` | `FUNCTION` |
| `mod_item` | — | **not a Declaration** — a namespace; contributes to children's `parentPath` |
| `impl_item` | — | **not a Declaration** — fold onto target type: `impl Tr for T` adds `Tr` to `T`'s usedTypes; method-signature types fold onto `T` |

`DeclarationType` has no STRUCT/TRAIT/UNION values; mapping to the existing 9 keeps DC's
`TseMappings.toNodeType()` unchanged.

**UsedTypes** (signature categories; document a fixed concatenation order — it propagates through
`.toSet()` → `LinkedHashSet` → DC's Levelizer cycle tie-breaking):

- `struct`/`union`: `field_declaration` types (named + tuple `ordered_field_declaration_list`).
- `enum`: `enum_variant` payload types.
- `fn`: parameter types, return type, generic bounds, `where_clause` bounds.
- `trait`: supertraits (`trait_bounds` after `:`), associated-type bounds, method-signature types.
- `impl`: target type + trait (if `impl Trait for Type`) + method-signature types.
- `type` alias: RHS type.
- Type parsing via `RustTypeHelper`: `type_identifier`→plain; `generic_type`→recurse `type_arguments`
  (nested, never flat-duplicated); `scoped_type_identifier`→`namespacePrefix`; unwrap
  `reference_type`/`pointer_type`/`array_type`/`slice_type`/`tuple_type`/`dynamic_type`/`abstract_type`;
  **skip** `primitive_type`, `lifetime`, and `Self`.

**Proposed concatenation order** (Rust is TSE-native, no DC legacy to match): inheritance/supertraits,
fields, variants, parameters, returnTypes, genericBounds, whereClauseBounds, implTraits,
associatedTypes. Document the chosen order in the README table.

## What we're NOT doing

- No cross-file module-path / crate-root resolution (DC's job — TSE is per-file).
- No `crate`/`self`/`super` normalization (DC's job — needs the file path).
- No function-body / value-level usage (`let x: T`, `T::new()`, struct literals).
- No macro expansion / proc-macro / macro-generated types; `macro_rules!` is recorded only as a
  named declaration.
- No `#[cfg(...)]` evaluation or test-code filtering — everything is extracted unconditionally.
- No new `DeclarationType`/`ImportKind` enum values, no `DependencyResult` shape changes, no changes
  to other languages.

## Phase 1: Walking skeleton + wiring (TDD)

Make `TreeSitterDependencies.analyze(_, RUST)` return a real result for a minimal file, with the
support flag flipped.

**Tasks**:
- [x] **Red**: `RustDependencyTest.ApiSupportCheck.should report Rust as dependency-supported` —
  `TreeSitterDependencies.isDependencyAnalysisSupported(RUST)` is `true`; `getSupportedLanguages()`
  contains `RUST`.
- [x] **Red**: `DeclarationExtraction.should extract a top-level struct` —
  `struct Foo { bar: Bar }` → one `Declaration(name="Foo", type=CLASS)`.
- [x] Create `RustDependencyMapping` (object) + minimal `PackageExtractor` (`emptyList()`),
  `ImportExtractor` (`use_declaration` simple `scoped_identifier`/`identifier`),
  `DeclarationExtractor` (`struct_item`/`function_item`), `UsedTypeExtractor` (field/param/return
  via a minimal `RustTypeHelper`).
- [x] Add `override val dependencyMapping = RustDependencyMapping.dependencyMapping` to
  `RustDefinition`.

**Automated Verification**:
- [x] `RustDependencyTest.ApiSupportCheck` (Unit) passes.
- [x] `RustDependencyTest.DeclarationExtraction.should extract a top-level struct` (Unit) passes.
- [x] `./gradlew test` — existing suites (incl. `*ContractTest`) stay green (RUST already counted as
  a language). _(Updated `LanguageSupportContractTest.DependencySupportContract` to include RUST.)_

---

## Phase 2: Comprehensive imports (TDD)

[Dependencies: **Phase 1**]

Flatten every `use`-tree form to per-leaf `ImportDeclaration`s, verbatim leading segments.

**Tasks**:
- [x] Simple + scoped (`use a::b::C;`), nested trees (`use a::{b, c::D};` →
  `scoped_use_list`/`use_list`, recurse), glob (`use a::*;` → `use_wildcard`, `isWildcard=true`).
- [x] Aliases (`use a::B as C;` → `use_as_clause`, `bindingName="C"`, path uses original `B`).
- [x] `pub use` re-exports (emit as STANDARD import; `visibility_modifier` present).
- [x] Keep `crate`/`self`/`super` as the verbatim first path segment.

**Automated Verification**:
- [x] `RustDependencyTest.ImportExtraction` (Unit) covers simple, nested, glob, alias, `pub use`,
  and `crate`/`self`/`super` cases — all pass.

---

## Phase 3: Comprehensive declarations + module nesting + impl aggregation (TDD)

[Dependencies: **Phase 1**]

All item kinds, recursive discovery, inline-`mod` `parentPath`, and impl folding.

**Tasks**:
- [x] All declaration item kinds → `DeclarationType` per the mapping table (struct/union/enum/trait/
  type-alias/fn/fn-signature/const/static/macro_definition).
- [x] Recursive discovery via `TreeTraversal.findAllDescendantsOfType`; record the enclosing
  `mod_item` chain as `parentPath` (e.g. `mod a { mod b { struct Foo } }` → `Foo.parentPath=[a,b]`).
- [x] `impl_item` folding: `impl Tr for T` adds `Tr` to `T`'s usedTypes; inherent/trait method
  signatures fold their types onto `T` (cf. Go receiver aggregation). `impl` itself is not a node.
- [x] Skip-name guard: drop declarations whose name can't be resolved (no empty-named garbage).

**Automated Verification**:
- [x] `RustDependencyTest.DeclarationExtraction` (Unit): every item kind, nested-module `parentPath`,
  and impl aggregation (incl. `impl Trait for Type` edge) — all pass.

---

## Phase 4: Comprehensive used types (TDD)

[Dependencies: **Phase 3**]

Full `RustTypeHelper` + every signature category, with a documented concatenation order.

**Tasks**:
- [x] `RustTypeHelper`: `generic_type` (recurse `type_arguments`, nested), `scoped_type_identifier`
  (`namespacePrefix`), unwrap `reference_type`/`pointer_type`/`array_type`/`slice_type`/`tuple_type`/
  `dynamic_type`/`abstract_type`; skip `primitive_type`/`lifetime`/`Self`.
- [x] All categories: struct/union fields, enum variant payloads, fn params/returns, generic bounds,
  `where_clause`, trait supertraits + associated-type bounds, type-alias RHS — in the documented order.
- [x] `mut`/`ref`/lifetime annotations don't leak into names; generics stay nested (no flat dupes).

**Automated Verification**:
- [x] `RustDependencyTest.UsedTypeExtraction` (Unit): fields, generics (nested), references/slices/
  tuples, trait bounds, `where`-clauses, supertraits, qualified `namespacePrefix` — all pass.
- [x] `./gradlew ktlintCheck` and `detekt` pass for new files. _(detekt must run on JDK 17, not the
  sandbox's default JDK 25 — detekt's environment probe throws `IllegalArgumentException: 25.0.3`
  before analysis; `JAVA_HOME=.../java-17 ./gradlew build` is fully green.)_
- [x] `./gradlew build` (compile + dependency verification + full suite) passes (under JDK 17).

---

## Phase 5: Docs + release

[Dependencies: **Phase 4**]

**Tasks**:
- [x] README (dependencies feature): add Rust rows to the namespace-model (Class 2) and
  used-type concatenation-order tables; note the file-module-path-is-DC's-job decision and the
  `namespacePrefix` opt-in.
- [x] `CHANGELOG.md`: `feat(rust): add Rust dependency analysis support`.
- [ ] Merge, then **tag a release** (e.g. `v0.10.0`) so DC can pin it (`com.github.MaibornWolff:
  TreeSitterExcavationSite:<tag>` via JitPack). This tag is the hand-off to PR 2. _(User action —
  PR 2 develops against this repo via composite build until the tag exists.)_

**Automated Verification**:
- [x] `./gradlew build` green on the release commit (under JDK 17; see Phase 4 detekt note).

**Manual Verification**:
- [ ] Run `TreeSitterDependencies.analyze` on a realistic multi-module Rust file (nested `mod`,
  generics, trait impls, `use` trees with globs/aliases/`pub use`): `DependencyResult` shows correct
  per-declaration `parentPath`, flattened imports, and signature used types with no body-level or
  primitive leakage.

## References

- C# (Class-2 template): `languages/csharp/CSharpDependencyMapping.kt`,
  `languages/csharp/extractors/{NamespaceExtractor, UsingDirectiveExtractor, DeclarationExtractor,
  UsedTypeExtractor}.kt`, `CSharpTypeHelper.kt`
- Dependencies feature contract + namespace-model tables:
  `integration/dependencies/README.md`
- Migration workflow / Class-1-vs-2 + `namespacePrefix` rationale: `.claude/rules/dependency-migration.md`
- Prior Rust work (extraction; grammar node types): `plans/add-rust-extraction-support.md`
- DC consumer (PR 2): `DependaCharta/plans/tse-rust-integration.md`
- tree-sitter-rust node types: `tree-sitter/tree-sitter-rust` `src/node-types.json`
</content>
</invoke>
