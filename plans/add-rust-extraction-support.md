---
name: Rust Language Support for Extraction
issue:
state: todo
version:
---

## Goal

Add **Rust** as the 19th supported language, with **text extraction only** (identifiers,
comments, strings). Rust is wired into the `Language` enum and `LanguageRegistry` using the
`tree-sitter-ng` Rust grammar (`io.github.bonede:tree-sitter-rust:0.24.0`,
`org.treesitter.TreeSitterRust`). Metrics and dependency analysis are **out of scope**:
`RustDefinition.nodeMetrics` is left empty and no `DependencyMapping` is provided.

## Decisions (resolved before planning)

- **Scope:** Extraction only. `nodeMetrics = emptyMap()` (mandatory override, no default).
  `TreeSitterMetrics.parse(rust)` will return zeros for complexity/functions/comments (LOC is
  still computed generically); the Rust metrics golden file is expected to be near-all-zeros.
- **Dependency:** Maven dependency `io.github.bonede:tree-sitter-rust:0.24.0`. ✅ **Verified** against
  Maven Central (reachable; `0.24.0` is the latest release; jar contains
  `org/treesitter/TreeSitterRust.class` + native libs for aarch64/x86_64 linux, macOS, Windows).
  Network policy is now open; `gradle/verification-metadata.xml` still needs regenerating during
  implementation.
- **Breadth:** Comprehensive, Go-like (all identifier-bearing declarations, doc comments, raw/char
  strings).
- **Plan location:** repo `plans/` convention (this file).

## Key facts (from research)

- **Public API is enum-driven.** Adding `RUST` to `shared/domain/Language.kt` cascades into two
  exhaustive `when`s in `LanguageRegistry` (won't compile otherwise), an exhaustive `when` in
  `RobustnessContractTest.getMinimalCodeSample` (won't compile), and several tests that derive from
  `Language.entries` or hardcode counts.
- **`isExtractionSupported(lang)` = `definition.nodeExtractions.isNotEmpty()`.** A non-empty Rust
  extraction mapping satisfies the all-languages extraction contracts in
  `LanguageSupportContractTest` automatically.
- **Gradle dependency verification is ENABLED** (`gradle/verification-metadata.xml`,
  `<verify-metadata>true</verify-metadata>`). `tree-sitter-rust` is absent → the build fails until
  checksums are regenerated. Maven Central network access is now allowed (verified reachable), so
  `./gradlew --write-verification-metadata sha256 help` can run in-sandbox.
- **Grammar node types** (tree-sitter-rust `node-types.json`):
  - Comments: `line_comment` (covers `//`, `///`, `//!`), `block_comment` (covers `/* */`,
    `/** */`, `/*! */`). `CommentFormats.AutoDetect` already strips `///` (XmlDoc), `//`, `/* */`
    correctly.
  - Strings: `string_literal` + `raw_string_literal` (inner text in `string_content` child),
    `char_literal`. Byte strings parse as these with a `b`/`br` prefix token (no dedicated node).
  - Names: type-defining decls (`struct_item`, `enum_item`, `union_item`, `trait_item`,
    `type_item`) use `type_identifier`; `function_item`/`function_signature_item`, `mod_item`,
    `const_item`, `static_item`, `macro_definition`, `enum_variant` use `identifier`;
    `field_declaration` uses `field_identifier`; `impl_item` has no name field;
    `let_declaration` binds via its `pattern` field.

## Affected files

| File | Change | Why |
|------|--------|-----|
| `shared/domain/Language.kt` | add `RUST(primaryExtension = ".rs")` | enum source of truth |
| `languages/LanguageRegistry.kt` | import + `RUST ->` in both `when`s | exhaustive `when`, won't compile otherwise |
| `gradle/libs.versions.toml` | `tree-sitter-rust = "0.24.0"` + `treesitter-rust` library | dependency catalog |
| `build.gradle.kts` | `implementation(libs.treesitter.rust)` | wire dependency |
| `gradle/verification-metadata.xml` | regenerate sha256 for the new artifact | dep verification on |
| `languages/rust/RustExtractionMapping.kt` | **new** — node→Extract map | extraction behavior |
| `languages/rust/RustDefinition.kt` | **new** — `nodeMetrics = emptyMap()`, `nodeExtractions = …` | language definition |
| `languages/rust/extractors/*.kt` | **new (if TDD needs)** — `let_declaration` / `impl_item` | custom patterns |
| `src/test/.../languages/rust/RustExtractionTest.kt` | **new** — TDD tests | feature coverage |
| `api/contract/ApiSignatureContractTest.kt:314-316` | `hasSize(18)` → `19`; rename test | hardcoded count |
| `api/contract/RobustnessContractTest.kt:585-697` | add `RUST ->` minimal sample | exhaustive `when`, won't compile |
| `api/contract/RobustnessContractTest.kt:14` | "all 18 languages" → 19 | doc comment |
| `api/contract/GoldenFileContractTest.kt:28-68` | add `RUST` to both sample maps | `error()` at runtime otherwise |
| `src/test/resources/contract/rust_sample.rs` | **new** — golden sample | golden input |
| `src/test/resources/contract/rust_sample_{metrics,extraction}.golden` | **generated** | golden output |
| `api/TreeSitterExtractionTest.kt:94-100` | add `RUST` to list; `hasSize(18)` → `19` | exact list + count |
| `api/TreeSitterExtractionTest.kt:109-115` | add `.rs`; `hasSize(33)` → `34` | exact list + count |
| `api/TreeSitterMetricsTest.kt:116-122` | add `.rs`; `hasSize(33)` → `34` | exact list + count |
| `api/contract/LanguageSupportContractTest.kt` | *(recommended)* add `.rs, RUST` rows | parity (won't fail without) |
| `README.md:9,201` / `CLAUDE.md:13,41` / `.claude/rules/overview.md:9` / `.claude/rules/architecture.md:42` | 18 → 19 + add Rust to lists | docs |
| `CHANGELOG.md` | add entry | release notes |

## Proposed extraction mapping (starting point — TDD resolves exact node coverage)

```
// Identifiers — simple first-child strategies
function_item / function_signature_item → Identifier(FirstChildByType("identifier"))
struct_item / enum_item / union_item / trait_item / type_item
                                        → Identifier(FirstChildByType("type_identifier"))
mod_item / const_item / static_item / macro_definition / enum_variant
                                        → Identifier(FirstChildByType("identifier"))
field_declaration                       → Identifier(FirstChildByType("field_identifier"))
parameter                               → Identifier(FirstChildByType("identifier"))

// Identifiers — likely need a custom extractor (resolve via TDD; see Swift PatternExtractor / Go)
let_declaration                         → Identifier(customSingle = … pattern field)
impl_item                               → Identifier(customSingle = … impl target type, optional)

// Comments
line_comment                            → Comment(CommentFormats.AutoDetect)   // //, ///, //!
block_comment                           → Comment(CommentFormats.Block)        // /* */, /** */

// Strings
string_literal                          → StringLiteral(StringFormats.FromChild("string_content"))
raw_string_literal                      → StringLiteral(StringFormats.FromChild("string_content"))
char_literal                            → StringLiteral(StringFormats.Quoted(stripSingleQuotes = true))
```

## What we're NOT doing

- No `RustMetricMapping` / complexity / function counts (extraction only).
- No `DependencyMapping` (no package/use/declaration extraction).
- No new `ExtractionStrategy` / `CommentFormats` / `StringFormats` variants — reuse existing.
- No changes to the public API surface (`TreeSitterExtraction`, `ExtractionResult`, type aliases).

## Tasks

### Phase 1 — Walking skeleton: dependency + enum wiring (TDD)

Make the project compile and stay fully green with a **minimal** Rust extraction mapping. Order:
the grammar + enum + registry must exist before any Rust test can run, so the first failing test
drives the minimal wiring.

1. **Prerequisite (environment):** ✅ done — Maven Central (`repo1.maven.org`) verified reachable
   and `tree-sitter-rust:0.24.0` confirmed downloadable.
2. **Red:** add `RustExtractionTest.should extract function name` —
   `fn add(a: i32) {}` → identifiers contain `"add"`.
3. **Dependency wiring (Green prerequisites):**
   - `gradle/libs.versions.toml`: `tree-sitter-rust = "0.24.0"` and
     `treesitter-rust = { group = "io.github.bonede", name = "tree-sitter-rust", version.ref = "tree-sitter-rust" }`.
   - `build.gradle.kts`: `implementation(libs.treesitter.rust)`.
   - Regenerate checksums: `./gradlew --write-verification-metadata sha256 help` then review the
     `tree-sitter-rust` additions in `gradle/verification-metadata.xml`.
4. **Enum + registry:**
   - `shared/domain/Language.kt`: add `RUST(primaryExtension = ".rs")`.
   - `LanguageRegistry.kt`: `import org.treesitter.TreeSitterRust` + `RustDefinition`; add
     `Language.RUST -> TreeSitterRust()` and `Language.RUST -> RustDefinition`.
5. **Minimal definition (Green):**
   - `languages/rust/RustExtractionMapping.kt`: start with `function_item → FirstChildByType("identifier")`.
   - `languages/rust/RustDefinition.kt`: `override val nodeMetrics = emptyMap()`,
     `override val nodeExtractions = RustExtractionMapping.nodeExtractions`.
6. **Fix the now-failing/non-compiling shared tests:**
   - `RobustnessContractTest.getMinimalCodeSample`: add a `Language.RUST -> """ … """` branch (a
     small valid snippet with a fn, a `//` comment, and a `"string"`).
   - `RobustnessContractTest` line 14 comment: 18 → 19.
   - `ApiSignatureContractTest`: rename `should have exactly 18 language values` → 19,
     `hasSize(18)` → `hasSize(19)`.
   - `TreeSitterExtractionTest`: add `Language.RUST` to the `getSupportedLanguages` list,
     `hasSize(18)` → `19`; add `.rs` to the extensions list, `hasSize(33)` → `34`.
   - `TreeSitterMetricsTest`: add `.rs` to the extensions list, `hasSize(33)` → `34`.
   - `GoldenFileContractTest`: add `Language.RUST to "rust_sample.rs"` to `SAMPLE_FILE_NAMES` and
     `Language.RUST to "rust_sample"` to `GOLDEN_BASE_NAMES` (golden files created in Phase 3).

### Phase 2 — Comprehensive extraction mapping (TDD)

[Dependencies: **Phase 1**]

Iterate Red → Green → Refactor per node-type group, expanding `RustExtractionMapping`. Add custom
extractors in `languages/rust/extractors/` only where a declarative strategy can't express the
pattern (model on Swift `PatternExtractor` / Go helpers).

7. **Type declarations:** `struct_item`, `enum_item`, `union_item`, `trait_item`, `type_item`
   (`type_identifier`); struct fields (`field_declaration` → `field_identifier`); `enum_variant`.
8. **Values & modules:** `const_item`, `static_item`, `mod_item`, `macro_definition`,
   `function_signature_item`, function `parameter`s.
9. **Let bindings & impls:** `let_declaration` (pattern field; custom extractor for
   `mut_pattern`/`tuple_pattern`), `impl_item` (impl-target type; optional — decide via test).
10. **Comments:** `line_comment` (`AutoDetect`, covering `//`, `///`, `//!`), `block_comment`
    (`Block`, covering `/** */`). Assert stripped output for each doc-comment form.
11. **Strings:** `string_literal`, `raw_string_literal` (`FromChild("string_content")`),
    `char_literal` (`Quoted(stripSingleQuotes = true)`). Cover escapes and `r#"…"#` raw strings.
12. **Refactor:** extract shared traversal helpers into `RustHelpers.kt` if extractors repeat logic;
    run `./gradlew ktlintFormat`.

### Phase 3 — Golden files & documentation

[Dependencies: **Phase 2**]

13. **Golden sample:** create `src/test/resources/contract/rust_sample.rs` — a representative file
    exercising structs, enums, traits, impls, functions, fields, consts, modules, doc comments,
    and raw/normal/char strings.
14. **Generate goldens:** run `GoldenFileContractTest` once (it auto-creates the `.golden` files and
    fails), review `rust_sample_metrics.golden` (expected near-all-zeros, LOC only) and
    `rust_sample_extraction.golden`, then re-run to confirm green. Commit both.
15. **Recommended parity:** in `LanguageSupportContractTest`, add `.rs, RUST` to the
    `PrimaryExtensionMappingContract` CsvSource and `.rs` to `IsLanguageSupportedContract`.
16. **Docs:** bump 18 → 19 and add Rust to the language lists in `README.md` (lines 9, 201),
    `CLAUDE.md` (13, 41), `.claude/rules/overview.md` (9), `.claude/rules/architecture.md` (42);
    add a `feat(rust): add Rust extraction support` entry to `CHANGELOG.md`.

## Steps

- [x] **Phase 1**
  - [x] Allow Maven Central in network policy (host prerequisite) — also installed JDK 17 toolchain
  - [x] Red: `RustExtractionTest.should extract function name`
  - [x] Add `tree-sitter-rust` to `libs.versions.toml` + `build.gradle.kts`
  - [x] Regenerate `gradle/verification-metadata.xml` (sha256) and review the new entries (clean +8 lines)
  - [x] Add `RUST` to `Language` enum; wire both `when`s in `LanguageRegistry`
  - [x] Create `RustExtractionMapping` (function_item only) + `RustDefinition` (empty metrics)
  - [x] Add `Language.RUST` branch to `RobustnessContractTest.getMinimalCodeSample` (+ line 14 comment)
  - [x] Update `ApiSignatureContractTest` (18 → 19, + `should contain RUST`)
  - [x] Update `TreeSitterExtractionTest` (languages list +RUST, 18 → 19; extensions +`.rs`, 33 → 34)
  - [x] Update `TreeSitterMetricsTest` (extensions +`.rs`, 33 → 34)
  - [x] Add `RUST` to both maps in `GoldenFileContractTest`
  - [x] Green: compiles; RustExtractionTest + contract tests pass (golden `.golden` files generated in Phase 3)
- [x] **Phase 2** — Complete Task 7–12 (comprehensive mapping via TDD)
  - Type decls, fields, enum variants, values/modules/macros/fn-signatures/params via declarative strategies
  - `let_declaration` via custom `extractLetBindingIdentifiers` (simple/mut/tuple); `impl_item` intentionally skipped
  - Comments: `line_comment` (AutoDetect), `block_comment` (Block)
  - Strings: `string_literal` (Quoted — preserves escapes), `raw_string_literal` (FromChild string_content), `char_literal` (Quoted stripSingleQuotes)
- [x] **Phase 3** — Complete Task 13–16 (golden files + docs)
  - [x] `rust_sample.rs` golden sample + generated `_metrics`/`_extraction` goldens (reviewed, green)
  - [x] `LanguageSupportContractTest` parity rows (`.rs, RUST`)
  - [x] Docs: README (count+table+tree), CLAUDE.md, rules/overview, rules/architecture, CHANGELOG

## Environment notes (implementation)

- Toolchain JDK 17 was absent and foojay auto-provisioning is network-blocked → installed
  `openjdk-17-jdk-headless` via apt; set `JAVA_HOME` to JDK 17 in `/etc/sandbox-persistent.sh`
  (detekt 1.23.8 crashes on the JDK 25 Gradle daemon; the project targets Java 17).
- `config/detekt/detekt.yml`: the 19th language tipped two thresholds — added
  `CyclomaticComplexMethod.ignoreSimpleWhenEntries: true` (registry dispatch `when`s) and raised
  `LongMethod` 120→150 (test sample method). Idiomatic resolutions, not `@Suppress`.

## Post-plan gap closure (all 6 review limitations closed)

A follow-up workflow verified each documented limitation against the real grammar and adversarially
checked the fixes for double-counting / over-extraction; none were intentional design constraints,
all were closed without touching shared code:

- **Type/const generic parameters** (`fn f<T, U: Clone>`, `struct S<const N: usize>`): map
  `type_parameter`→`type_identifier` and `const_parameter`→`identifier` (bounds `Clone` / type `usize`
  excluded — direct-child lookup; container `type_parameters` left unmapped to avoid double-counting).
- **Untyped closure params**: map `closure_parameters`→`AllChildrenByType("identifier")` (direct
  children only; typed params remain their own `parameter` nodes — no double count).
- **Struct-destructuring + tuple-struct/let-else `let`**: `LetBindingExtractor` rewritten to recurse
  patterns, collecting `identifier`/`shorthand_field_identifier` while skipping the leading
  constructor/type path of `struct_pattern`/`tuple_struct_pattern` and matched `field_identifier`s.
- **Byte strings** (`b"…"`): custom `extractRustStringLiteralContent` concatenates
  `string_content`+`escape_sequence` children (also keeps full escaped-string content).
- **`//!` / `/*!` inner-doc**: custom Rust comment extractors strip the bang marker, then delegate
  to the shared parser (which is called, not modified).

### Remaining minor gaps (genuinely out of scope)
- Lifetime parameters (`'a`) are intentionally not extracted (labels, not value/type names).
- Byte-char literals (`b'A'`) keep the `b` prefix (`char_literal` still uses `Quoted`).

## Success Criteria

**Automated Verification:**
- [x] `RustExtractionTest` (Unit) passes — 24 tests covering identifiers, comments (incl. doc),
      strings (incl. raw/char), and the typed-`let` regression.
- [x] `LanguageSupportContractTest.ExtractionSupportContract` (Unit) green — Rust reported as
      extraction-supported across all `Language.entries`.
- [x] `GoldenFileContractTest` (Unit) green for `RUST` metrics + extraction goldens.
- [x] `RobustnessContractTest` (Unit) green for `RUST` (empty/whitespace/malformed/determinism).
- [x] `ApiSignatureContractTest`, `TreeSitterExtractionTest`, `TreeSitterMetricsTest` (Unit) green
      with updated counts/lists.
- [x] `./gradlew build` passes (compile + dependency verification + all tests).
- [x] `./gradlew ktlintCheck` and `detekt` pass for new files.

**Manual Verification:**
- [x] Ran extraction on a realistic Rust file (task queue with structs/trait/impl, typed `let`,
      doc comments, raw/char strings): identifiers included struct/enum/trait/fn/field/let names with
      no type-reference leakage; comments included doc text; strings included normal, raw, and char.
      (Awaiting user sign-off.)

## References

- Internal Language enum: `src/main/kotlin/.../shared/domain/Language.kt:6-24`
- Registry exhaustive `when`s: `src/main/kotlin/.../languages/LanguageRegistry.kt:52-95`
- Exemplar (broad extraction + custom extractors): `languages/go/GoExtractionMapping.kt`,
  `languages/go/extractors/`
- Comment stripping: `shared/domain/CommentParser.kt` (`stripCommentMarkers`, `stripXmlDocComment`)
- Contract tests touched: `api/contract/{ApiSignature,Robustness,GoldenFile,LanguageSupport}ContractTest.kt`
- Prior single-language plan (format model): `plans/add-tsx-support.md`
- tree-sitter-rust grammar node types: `tree-sitter/tree-sitter-rust` `src/node-types.json`
- Maven artifact: `io.github.bonede:tree-sitter-rust:0.24.0` (class `org.treesitter.TreeSitterRust`)
