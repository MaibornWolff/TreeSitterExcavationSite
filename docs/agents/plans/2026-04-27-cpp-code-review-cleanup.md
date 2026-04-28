---
date: 2026-04-27T09:11:55.468858+00:00
git_commit: 4a58eb9
branch: feat/cpp-dependency-support
topic: "C++ code review cleanup"
tags: [plan, cpp, refactor, code-review]
status: progress
---

# C++ Code Review Cleanup Implementation Plan

## Session Status (last updated 2026-04-27, end of session)

- **Phases 1–7 complete**:
  - Phase 1 — `9b79371` refactor(cpp): consolidate namespace walking into CppNamespaceWalker
  - Phase 2 — `a575c04` refactor(cpp): split DeclarationExtractor into focused sub-extractors
  - Phase 3 — `82b31f2` refactor(cpp): rename extractors to disambiguate dependency vs identifier
  - Phase 4 — `05750d0` refactor(cpp): lift QualifiedIdentifierPath out of CppTypeHelper
  - Phase 5 — `aa8c9f6` refactor(cpp): move complexity-ignore config to CppCalculationConfig
  - Phase 6 — `4a58eb9` docs(cpp): document DC-legacy quirks + drop dead null checks
  - Phase 7 — verified zero behavior change on cppcheck (no commit; comparison artifacts in `../dc-compare/after-refactor/`)
- **Investigation done this session** (validated `cpp-extraction-followups.md` gap claims):
  - All 3 documented DC bugs confirmed with exact citations.
  - No correctness bugs in TSE — extraction matches DC categories exactly.
  - Reachability analysis on 1337 main-only edges: 12 unreachable, 601 sibling-attribution-shift (dump-victim), 279 "pure under-extraction" of which ~170 are caused by the simplecpp-pollution dynamic.
  - **New finding documented in `dependency-migration.md`**: TSE parsing files DC main fails on (`externals/simplecpp/simplecpp.cpp`) adds extra projectDictionary candidates that get picked over the "right" target via DC's empty-wildcard substring fallback. ~121 `Token` + ~49 `TokenList` deps redirect to `simplecpp.*` instead of `lib.*`. Same resolver, different inputs, different outputs. Not a TSE bug.
  - **Realistic fixable budget**: ~40-60 main-only edges via case-by-case extraction work — not the 150-300 the followups plan estimated. Closing them would lift match-rate to ~46-47%, not 50-55%.
  - Strategic decision: **do NOT mirror DC bugs in TSE**. Stick with the "fix bugs, document accepted improvements" agenda from `dependency-migration.md`. Ship at 45.32% match rate; document the ceiling as realistic 45-50%.
- **Decision recorded for Phase 8 first task**: primitive-type extraction → **Option B** (add `PRIMITIVE_TYPE` + `sized_type_specifier` support; the 4th DC test's expected name is updated to `unsigned`/`signed` rather than mirroring DC's `→ int` normalization). Rationale: minimum-risk to TSE cleanliness; no DC-quirk mirroring.
- **Phase 8 progress**: Option B implemented (commit `acd3dcf`). dc-compare on cppcheck unchanged at R15 baseline (1118/1349/980/45.32%). Next: DC `CppAnalyzerTest` updates on `feat/cpp-dependency-integration`.
- **DC composite-build wiring**: currently active on DC `feat/cpp-dependency-integration` — left in place because Phase 8 needs it again. Must be reverted before any DC-side commit lands.

## Open question for next session

**Should the DC resolver fix (`Node.resolveTypeImport` empty-wildcard substring fallback) bundle into Phase 8 or schedule as a separate post-merge DC PR?**

Context:
- DC's `Node.kt:127-133` empty-wildcard substring fallback is the documented root cause of the simplecpp-pollution misdirection (~120-180 main-only deps on cppcheck).
- DC's own in-code TODO acknowledges the looseness.
- Three fix candidates (in order of recommendation):
  1. **Namespace-proximity tie-break** when empty-wildcard finds >1 candidate — most defensible; preserves current behavior for unambiguous cases.
  2. Strict-suffix match instead of substring (doesn't help empty-wildcard case — the actual issue).
  3. Skip empty wildcards entirely (would break ~358 currently-matched C++ deps that depend on the fallback).
- Cross-language impact assessment: empty-wildcard emission is essentially C++-specific (Java/Kotlin/C#/Go/PHP/TS/JS/Vue all emit non-empty package-derived wildcards). One audit needed: confirm Java's adapter doesn't emit empty wildcards for default-package projects before landing the DC fix.
- Trade-off:
  - **Bundle into Phase 8**: matches at ~50% on cppcheck (visible bump in shipped numbers). Adds DC commit to merge sequence.
  - **Post-merge separate DC PR**: cleaner separation; C++ migration ships pure. Resolver fix is a DC-quality improvement that benefits all future migrations regardless of which language goes next.

Recommendation: **post-merge separate DC PR** — keeps the C++ migration's commit history focused, and the resolver fix is a DC-wide improvement that deserves its own review window. But it's a small change either way; revisit at start of next session.

## Overview

Address the in-scope issues from the C++ code review by refactoring `languages/cpp/` only. Six small, sequential commits plus a final three-way dc-compare verification: DRY namespace walking, split `DeclarationExtractor` SRP violation, rename ambiguous extractor files/objects, lift `QualifiedIdentifierPath` from `CppTypeHelper`, relocate complexity-ignore constants, add documentation comments for DC-legacy quirks. No behavior change — pure structural cleanup verified by the existing C++ test suite and a final dc-compare against the previous run.

After Phases 1–7 land, **Phase 8** picks up the cross-repo wrap-up work carried over from `plans/cpp-extraction-followups.md`: primitive-type extraction decision, 8 DC `CppAnalyzerTest` fixes, composite-build revert, and the TSE → DC merge sequence that closes out the C++ migration.

## Current State Analysis

The C++ slice is functionally complete (`feat/cpp-dependency-support`, all tests green) but accumulated technical debt during the migration:

- **Namespace walking duplicated 3×** in `languages/cpp/extractors/`:
  - `DeclarationExtractor.kt:84-119` (`findNamespacePath` + `extractNamespaceSegments`)
  - `ImportExtractor.kt:72-92` (`aggregateNamespacePath` + `extractNamespaceSegments`)
  - `PackageExtractor.kt:12-22` (`extract`, file-namespace variant)
  - Each redeclares `NAMESPACE_SEPARATOR = "::"`, `NAMESPACE_IDENTIFIER`, `NESTED_NAMESPACE_SPECIFIER`.
- **`DeclarationExtractor` violates SRP**: one object owns in-class extraction, out-of-class function-definition→class promotion, declaration merging, namespace-path walking, parent-class chain walking, and type mapping (~125 lines, 5 distinct responsibilities).
- **Naming collision** between two `DeclarationExtractor` symbols:
  - `languages/cpp/extractors/DeclarationExtractor.kt` (object, dependency feature)
  - `languages/cpp/extractors/GenericDeclarationExtractor.kt` (file containing `extractFromDeclaration`, extraction feature)
  - The "Generic" prefix is misleading — nothing in the file is generic, and the function name doesn't match the file name.
- **`CppTypeHelper.walkQualified` is already a private helper** but returns `Pair<List<String>, TSNode?>` — readability loss vs a named type.
- **`CppDefinition` mixes composition with logic**: defines four `private const val`s and constructs `calculationConfig` inline. Other language definitions (Java, Kotlin, C#) are pure composition.
- **DC-legacy quirks lack inline comments**: `hasBody` skip in `DeclarationExtractor.toDeclaration:69`, bare-name `UsedType` emission for `IDENTIFIER` callees in `CallExpressionTypeExtractor.kt:62-67` — both intentional but unobvious.
- **Dead defensive null checks** in C++ ancestor walks: `while (current != null && !current.isNull)` — `current` from `node.parent` is never Kotlin-null. Affects `DeclarationExtractor.kt:99-107` and `ImportExtractor.kt:75-80`.

## Desired End State

- `languages/cpp/extractors/` no longer duplicates namespace-walking logic; one `CppNamespaceWalker` helper used by all three call sites.
- `languages/cpp/extractors/declarations/` subdirectory mirrors the existing `usedtypes/` pattern, holding focused sub-extractors that the orchestrator composes.
- No filename or symbol collision between the two `DeclarationExtractor`-shaped concepts.
- `CppTypeHelper` exposes a small `QualifiedIdentifierPath` data class; the three selector methods (`extractRightmostSegment`, `extractSecondToLastSegment`, `extractSingleSegmentScope`) become one-liners over it.
- `CppDefinition` is a pure composition file: only `override val` declarations referencing siblings.
- DC-legacy quirks have one-line inline comments naming the rule they implement.
- `./gradlew build` green after every commit. All existing tests in `CppDependencyTest`, `CppExtractionTest`, `CppMetricsTest` pass unmodified.
- A final `dc-compare/after-refactor/` run on cppcheck shows zero divergence vs `dc-compare/feature/` (the latest pre-refactor run), and identical match-rate against `dc-compare/main/` (DC legacy baseline).

## What We're NOT Doing

- **No `shared/domain/` changes**: the `ParentPath` value class concept and `ImportKind` enum expansion are explicitly out of scope per the user's "C++-only" constraint.
- **No changes to other languages**: same defensive-null-check pattern exists in `csharp/DeclarationExtractor.kt` but is left untouched.
- **No `UsedTypeExtractor` re-split**: already done in the previous refactor session (`usedtypes/` subdirectory).
- **No new extraction patterns**: this is restructuring only; dc-compare numbers must not move (verified by Phase 7).
- **No mass conversion of file-level extractor functions to objects** (or vice versa). Both styles coexist; only the two confusingly-named symbols are renamed.
- **No fix to `RealLinesOfCodeCalc` mutable-state finding** from the quality report — outside the C++ slice.

## Architecture and Code Reuse

`CppNamespaceWalker` consolidates the duplicated logic across three extractors. The split-off declaration sub-extractors mirror the existing `usedtypes/` decomposition pattern.

```text
languages/cpp/
├── CppDefinition.kt                    # pure composition (no constants)
├── CppMetricMapping.kt                 # unchanged
├── CppCalculationConfig.kt             # NEW — owns ignoreForComplexity rules + constants
├── CppExtractionMapping.kt             # unchanged (imports renamed extractor file)
├── CppDependencyMapping.kt             # unchanged
└── extractors/
    ├── CppNamespaceWalker.kt           # NEW — shared by 3 extractors
    ├── CppTypeHelper.kt                # walkQualified returns QualifiedIdentifierPath
    ├── QualifiedIdentifierPath.kt      # NEW — small data class with selector methods
    ├── DependencyDeclarationExtractor.kt  # was DeclarationExtractor; thin orchestrator
    ├── declarations/                   # NEW subdirectory (mirrors usedtypes/)
    │   ├── InClassDeclarationFinder.kt
    │   ├── OutOfClassMethodPromoter.kt
    │   └── DeclarationMerger.kt
    ├── ImportExtractor.kt              # uses CppNamespaceWalker
    ├── PackageExtractor.kt             # uses CppNamespaceWalker
    ├── DeclarationIdentifierExtractor.kt  # was GenericDeclarationExtractor
    ├── usedtypes/                      # unchanged
    │   ├── AliasConstraintExtractor.kt
    │   ├── CallExpressionTypeExtractor.kt
    │   ├── ClassScopeExtractor.kt
    │   ├── DeclarationTypeExtractor.kt
    │   └── SignatureTypeExtractor.kt
    └── (other extractor files — file-level functions, unchanged)
```

**Reused utilities** (no changes needed):
- `TreeTraversal.findAllDescendantsOfType`, `findFirstChildTextByType` — already used by all extractors.
- `TreeTraversal.findAllDescendantsGroupedByType` — used in `usedtypes/`.

## Performance Considerations

Pure structural change. No additional AST traversals introduced; in fact `CppNamespaceWalker` may marginally reduce overhead by avoiding the three independent constant tables. Not measurable.

## Migration Notes

Each commit must keep `./gradlew build` green. Phase 2 depends on Phase 1 (uses the new walker). Phase 3 depends on Phase 2 (renames the orchestrator file split out in Phase 2). Phases 4 and 5 are independent of each other and of 1–3. Phase 6 depends on Phases 2 and 3 because it adds comments inside `OutOfClassMethodPromoter` (created in Phase 2) and references the renamed `DependencyDeclarationExtractor` symbol (Phase 3). Phase 7 is the final verification step — runs after every refactor commit lands, produces no code commit, only an analysis output and a comparison report.

---

## Phase 1: Extract `CppNamespaceWalker`

[No phase dependencies]

Consolidate the three duplicate namespace-walk implementations into one helper in `languages/cpp/extractors/CppNamespaceWalker.kt`. Existing call sites switch to the helper; behavior unchanged.

**Tasks**:
- [x] Create `languages/cpp/extractors/CppNamespaceWalker.kt` with:
  ```kotlin
  internal object CppNamespaceWalker {
      fun walkAncestorsFrom(node: TSNode, sourceCode: String): List<String>
      fun firstFileNamespace(rootNode: TSNode, sourceCode: String): List<String>
      // Plus private extractSegments(namespaceDef, sourceCode) shared by both.
      // Constants: NAMESPACE_SEPARATOR, NAMESPACE_DEFINITION, NAMESPACE_IDENTIFIER, NESTED_NAMESPACE_SPECIFIER
  }
  ```
- [x] Replace `DeclarationExtractor.findNamespacePath` + `extractNamespaceSegments` with a call to `CppNamespaceWalker.walkAncestorsFrom`. Drop the local namespace constants.
- [x] Replace `ImportExtractor.aggregateNamespacePath` + `extractNamespaceSegments` with a call to `CppNamespaceWalker.walkAncestorsFrom`. Drop the local namespace constants.
- [x] Replace `PackageExtractor.extract` body with a call to `CppNamespaceWalker.firstFileNamespace`. Drop the local namespace constants.

**Automated Verification**:
- [x] `./gradlew build` passes
- [x] `./gradlew test --tests "*Cpp*"` passes (covers `CppDependencyTest`, `CppExtractionTest`, `CppMetricsTest`)
- [x] `./gradlew ktlintCheck` passes

---

## Phase 2: Split `DependencyDeclarationExtractor` into focused classes

Dependencies: **Phase 1**

Split the dependency-side `DeclarationExtractor` into a thin orchestrator plus three single-responsibility sub-extractors under `languages/cpp/extractors/declarations/`. The orchestrator stays named `DeclarationExtractor` in this phase; the file rename + symbol rename to `DependencyDeclarationExtractor` happen in Phase 3.

**Tasks**:
- [x] Create `languages/cpp/extractors/declarations/InClassDeclarationFinder.kt`:
  ```kotlin
  internal object InClassDeclarationFinder {
      fun find(rootNode: TSNode, sourceCode: String): List<Declaration>
      // Owns CLASS/STRUCT/UNION/ENUM_SPECIFIER discovery, hasBody check,
      // findParentClassPath, mapType. Uses CppNamespaceWalker for namespace path.
  }
  ```
- [x] Create `languages/cpp/extractors/declarations/OutOfClassMethodPromoter.kt`:
  ```kotlin
  internal object OutOfClassMethodPromoter {
      fun promote(rootNode: TSNode, sourceCode: String): List<Declaration>
      // Owns FUNCTION_DEFINITION → qualified-declarator → synthetic CLASS Declaration.
      // Uses CppTypeHelper.extractSecondToLastSegment and CppNamespaceWalker.
  }
  ```
- [x] Create `languages/cpp/extractors/declarations/DeclarationMerger.kt`:
  ```kotlin
  internal object DeclarationMerger {
      fun merge(declarations: List<Declaration>): List<Declaration>
      // The linkedMapOf<Pair<List<String>, String>, Declaration> consolidation.
  }
  ```
- [x] Reduce `DeclarationExtractor.kt` (the dependency-side object — to be renamed in Phase 3) to a thin orchestrator:
  ```kotlin
  internal object DeclarationExtractor {
      fun extract(rootNode: TSNode, sourceCode: String): List<Declaration> =
          DeclarationMerger.merge(
              InClassDeclarationFinder.find(rootNode, sourceCode) +
                  OutOfClassMethodPromoter.promote(rootNode, sourceCode)
          )
  }
  ```

**Automated Verification**:
- [x] `./gradlew build` passes
- [x] `./gradlew test --tests "CppDependencyTest"` passes — full 1816-line test file unchanged
- [x] `./gradlew test --tests "*Cpp*"` passes
- [x] `./gradlew ktlintCheck` passes

---

## Phase 3: Rename for disambiguation

Dependencies: **Phase 2**

Eliminate the two confusingly-named files/symbols. No logic changes — file renames + symbol renames + import updates only.

**Tasks**:
- [x] Rename file `languages/cpp/extractors/GenericDeclarationExtractor.kt` → `DeclarationIdentifierExtractor.kt`. The function `extractFromDeclaration` keeps its name (it's the public reference target in `CppExtractionMapping`).
- [x] Rename object `DeclarationExtractor` (dependency-side, post-Phase-2) → `DependencyDeclarationExtractor`. Move its file `languages/cpp/extractors/DeclarationExtractor.kt` → `DependencyDeclarationExtractor.kt`.
- [x] Update import + reference in `CppDependencyMapping.kt`:
  ```kotlin
  // before:
  extractDeclarations = DeclarationExtractor::extract
  // after:
  extractDeclarations = DependencyDeclarationExtractor::extract
  ```
- [x] No update needed in `CppExtractionMapping.kt` — `extractFromDeclaration` is a top-level package function, so its import path is package-based, not file-based, and is unaffected by the filename change.
- [x] No test updates needed — `CppDependencyTest.kt` doesn't reference `DeclarationExtractor` directly (verified via grep).

**Automated Verification**:
- [x] `./gradlew build` passes
- [x] `./gradlew test --tests "*Cpp*"` passes
- [x] `./gradlew ktlintCheck` passes

---

## Phase 4: Lift `QualifiedIdentifierPath` from `CppTypeHelper`

[No phase dependencies — independent of 1–3]

Replace `CppTypeHelper.walkQualified`'s `Pair<List<String>, TSNode?>` return with a named data class. Three selector methods (`extractRightmostSegment`, `extractSecondToLastSegment`, `extractSingleSegmentScope`) consume it.

**Tasks**:
- [x] Create `languages/cpp/extractors/QualifiedIdentifierPath.kt`:
  ```kotlin
  internal data class QualifiedIdentifierPath(val segments: List<String>, val leaf: TSNode?) {
      companion object {
          fun walk(qualifiedId: TSNode, sourceCode: String): QualifiedIdentifierPath
      }
  }
  ```
  The `walk` factory contains the loop previously in `CppTypeHelper.walkQualified`. (Single-line constructor required by ktlint.)
- [x] Refactor `CppTypeHelper.extractRightmostSegment` and `extractSecondToLastSegment` to delegate to `QualifiedIdentifierPath.walk(...)` and read `.segments` / `.leaf`. Drop the private `walkQualified` method.
- [x] Leave `CppTypeHelper.extractSingleSegmentScope` as-is (it reads the `scope` field directly, doesn't need the walker).
- [x] Verify `CppTypeHelper.kt` line count stays <120 lines (was 113, now 96).

**Automated Verification**:
- [x] `./gradlew build` passes
- [x] `./gradlew test --tests "CppDependencyTest"` passes — exercises all three selector methods through used-type extraction
- [x] `./gradlew ktlintCheck` passes

---

## Phase 5: Move complexity-ignore constants to `CppCalculationConfig`

[No phase dependencies]

Make `CppDefinition` a pure composition file by moving the four metric-related constants and the `calculationConfig` construction to a new sibling object.

**Tasks**:
- [x] Create `languages/cpp/CppCalculationConfig.kt`:
  ```kotlin
  internal object CppCalculationConfig {
      private const val ABSTRACT_FUNCTION_DECLARATOR = "abstract_function_declarator"
      private const val LAMBDA_EXPRESSION = "lambda_expression"
      private const val FUNCTION_DECLARATOR = "function_declarator"
      private const val FUNCTION_DEFINITION = "function_definition"

      val config = CalculationConfig(
          ignoreForComplexity = listOf(
              IgnoreRule.TypeWithParentType(ABSTRACT_FUNCTION_DECLARATOR, LAMBDA_EXPRESSION),
              IgnoreRule.TypeWithParentType(FUNCTION_DECLARATOR, FUNCTION_DEFINITION)
          )
      )
  }
  ```
- [x] Reduce `CppDefinition.kt` to pure composition:
  ```kotlin
  object CppDefinition : LanguageDefinition {
      override val nodeMetrics = CppMetricMapping.nodeMetrics
      override val nodeExtractions = CppExtractionMapping.nodeExtractions
      override val dependencyMapping = CppDependencyMapping.dependencyMapping
      override val calculationConfig = CppCalculationConfig.config
  }
  ```

**Automated Verification**:
- [x] `./gradlew build` passes
- [x] `./gradlew test --tests "CppMetricsTest"` passes — covers the complexity-ignore rules
- [x] `./gradlew ktlintCheck` passes

---

## Phase 6: Documentation comments + dead null-check cleanup

Dependencies: **Phase 2** (touches `OutOfClassMethodPromoter`), **Phase 3** (references the renamed `DependencyDeclarationExtractor` symbol)

Add one-line comments documenting the DC-legacy quirks the C++ extractors implement, and remove the C++-only dead `current != null` half of `while` loops walking `node.parent`. Both small, low-risk; bundled to keep commit count down.

**Tasks**:
- [x] Add `hasBody`-skip comment in `InClassDeclarationFinder` (Phase 2 moved `hasBody` out of the dependency-side `DeclarationExtractor`/`DependencyDeclarationExtractor` into this finder): `// Forward declarations have no body — DC legacy emits no Declaration for them.`
- [x] In `CallExpressionTypeExtractor.kt` near the `IDENTIFIER` branch (~`:62-67`), add a comment explaining the bare-name `UsedType` emission for nested-call arg-list and throw-statement contexts (mirrors DC's empty-namespace-wildcard behavior — see `dependency-migration.md` lesson on tree-sitter-cpp parsing).
- [x] Remove dead `current != null && ` from the ancestor-walk loops in `CppNamespaceWalker.walkAncestorsFrom` (Phase 1 absorbed the original `ImportExtractor.aggregateNamespacePath` and `DeclarationExtractor.findNamespacePath` walks) and in `InClassDeclarationFinder.findParentClassPath` (Phase 2 moved the original `DeclarationExtractor.findParentClassPath` here). Pattern: `while (current != null && !current.isNull)` → `while (!current.isNull)`. Pre-existing equivalents in `csharp/` are left unchanged.

**Automated Verification**:
- [x] `./gradlew build` passes
- [x] `./gradlew test --tests "*Cpp*"` passes
- [x] `./gradlew ktlintCheck` passes

---

## Phase 7: Final dc-compare verification

Dependencies: **Phases 1–6** (all refactor commits must be landed)

Three-way comparison on cppcheck to confirm the refactor introduced zero behavior change. Generates a new `dc-compare/after-refactor/` folder alongside the existing `dc-compare/main/` (DC legacy baseline) and `dc-compare/feature/` (latest pre-refactor TSE run). Produces no code commit — only an analysis output and a comparison report.

**Tasks**:
- [x] Verify DC's composite-build wiring is in place per `.claude/rules/dependency-migration.md`:
  - `DependaCharta/analysis/settings.gradle.kts` includes `../../TreeSitterExcavationSite` ✓
  - `DependaCharta/analysis/build.gradle.kts` uses the composite-build dep (`de.maibornwolff.treesitter.excavationsite:treesitter-excavationsite`), not JitPack ✓
- [x] Generate the after-refactor analysis output:
  ```bash
  cd ../DependaCharta/analysis
  ./gradlew fatJar
  java -jar build/libs/dependacharta.jar -d "../../cppcheck" -o "../../dc-compare/after-refactor" -f analysis
  ```
- [x] Run two pairwise comparisons:
  - `dc-compare/feature/` vs `dc-compare/after-refactor/` → **MATCH: byte-identical normalized JSON; identical edge sets** (zero divergence as expected)
  - `dc-compare/main/` vs `dc-compare/after-refactor/` → matched=1118, main-only=1349, feat-only=980, **rate=45.32%**; identical to `main vs feature` (1118/1349/980/45.32%) — confirms zero behavior change. The R15 reference numbers in the plan (1337 main-only / 507 feat-only) reflect a different aggregation methodology; the `matched=1118` and `45.x%` rate match.
- [ ] Revert DC's composite-build wiring before any DC-side commit lands (deferred — Phase 8 needs the wiring for primitive-type re-verification, will revert before any DC-side commit per migration rules).

**Automated Verification**:
- [x] `dc-compare/feature/` vs `dc-compare/after-refactor/` reports zero divergence (byte-identical edge sets)
- [x] `dc-compare/main/` vs `dc-compare/after-refactor/` reports the same matched/main-only/feat-only counts as the pre-refactor `main vs feature` baseline (identical: 1118/1349/980/45.32%)
- [x] `./gradlew build` in TSE green at the final commit (Phase 6 commit `4a58eb9`)

---

## Phase 8: Wrap-up — primitive-type decision, DC test fixes, cross-repo merge

Dependencies: **Phases 1–7** (refactor must be complete and dc-compare verified before changing extraction behavior or touching DC)

Carried over from `plans/cpp-extraction-followups.md` "Remaining wrap-up work for next session". This is the last remaining work to close out the C++ migration end-to-end. Unlike Phases 1–7 (TSE-only structural cleanup), Phase 8 spans both repos and includes a behavior decision (primitive-type extraction) plus DC-side test updates.

**Pre-flight check** (verify state before starting):

```bash
cd C:/Users/ChristianSpa/IdeaProjects/DCTSE/TreeSitterExcavationSite
git branch --show-current         # feat/cpp-dependency-support
./gradlew build                   # green

cd ../DependaCharta
git branch --show-current         # feat/cpp-dependency-integration
grep -c "includeBuild" analysis/settings.gradle.kts   # 1 (composite wiring present)
grep "TreeSitterExcavationSite\|treesitter-excavationsite" analysis/build.gradle.kts
# expected: composite-build dependency, NOT JitPack
```

If composite wiring is missing, restore per `.claude/rules/dependency-migration.md` "Composite Build" section.

**Tasks**:

- [x] **Decide primitive-type extraction option** — Option B (decided 2026-04-27).
  - **A (minimal)**: add `PRIMITIVE_TYPE` to `CppTypeHelper.TYPE_NODE_TYPES` + extractType branch. Fixes 3 of 4 category-(a) DC tests. ~5 LOC in TSE, but ~29 TSE `CppDependencyTest` `containsExactly` assertions need primitives added.
  - **B (medium)** ← chosen: A + `sized_type_specifier` support. Fixes the 4th primitive test as `"unsigned"`/`"signed"`, not `"int"`. One more node-type constant + extractType branch.
  - **C (medium+)**: B + DC-legacy "bare unsigned/signed == int" normalization. All 4 DC tests pass as-written. Adds language-quirk normalization to TSE (semantically questionable).
- [x] **Implement Option B** — commit `acd3dcf` `feat(cpp): extract primitive_type and sized_type_specifier as used types`. Updated 17 TSE `CppDependencyTest` assertions to include now-emitted primitives (mostly `void` from method return types, `int` from int-typed params). dc-compare on cppcheck unchanged from R15 baseline: matched=1118, main-only=1349, feat-only=980, rate=45.32% — primitives don't resolve to project nodes on cppcheck so they're invisible at the resolved-cg.json layer (zero feat-only drift, zero matched gain).
- [ ] **Update 8 failing `DependaCharta/.../analyzers/cpp/CppAnalyzerTest.kt` tests** (categorized in `cpp-extraction-followups.md` "Wrap-up work" section):
  - **Category (a) — primitive-type tests** (4 tests): pass automatically once A/B/C is picked (A fixes 3, B/C fix all 4).
  - **Category (b) — flattening divergence** (2 tests): update assertions to drop standalone expectations for types that appear only as nested generics (e.g., assert `shared_ptr.genericTypes == [TEntity]` instead of looking for bare `shared_ptr[TEntity]`).
  - **Category (c) — multiline include backslash continuation** (1 test): `@Disabled("TSE extraction gap: multiline include continuation")` with a TODO, OR fix TSE `ImportExtractor` to strip `\\\n\s*` from raw include path.
- [ ] **Revert DC's composite-build wiring** in `analysis/settings.gradle.kts` + `analysis/build.gradle.kts` (composite paths must not be committed).
- [ ] **Final dc-compare** on cppcheck — confirm match rate ≥ R15 baseline (45.6%, matched 1118).
- [ ] **Merge TSE** `feat/cpp-dependency-support` → `main`, **tag release**.
- [ ] **Update DC's JitPack TSE dependency** to the new tag in `analysis/build.gradle.kts`.
- [ ] **Merge DC** `feat/cpp-dependency-integration` → `main`.

**Automated Verification**:

- [ ] `./gradlew build` green in both TSE and DC
- [ ] All `CppDependencyTest` (TSE) and `CppAnalyzerTest` (DC) tests pass
- [ ] Final cppcheck dc-compare match rate ≥ R15 (45.6%)
- [ ] DC composite-build wiring is reverted (no `includeBuild("../../TreeSitterExcavationSite")` in `analysis/settings.gradle.kts` at merge time)

**Manual Verification**:

- [ ] Confirm primitive-type option choice with user before implementing (A/B/C is a judgment call, not deterministic)
- [ ] Confirm TSE release tag name with user before tagging

---

## Follow-ups: disabled DC tests

Two `@Disabled` tests in `DependaCharta/.../analyzers/cpp/CppAnalyzerTest.kt` mark known gaps that were deliberately deferred out of the migration's scope. Each is a legitimate next-step item.

### Follow-up A: multiline `#include` normalization (small, ready-to-fix)

- **Test**: `should recognize multiline include statements`
- **Symptom**: `#include "dir/\<newline>    subdir/Foo.h"` produces the dependency `dir.\.subdir.Foo_h` (literal `\` segment retained) instead of the expected `dir.subdir.Foo_h`.
- **Cause**: TSE's `languages/cpp/extractors/ImportExtractor.kt` reads the include path verbatim from the tree-sitter-cpp AST. tree-sitter does not run the C preprocessor's translation phase 2 (`\<newline>` line-splicing), so the raw `\`, newline, and continuation indentation pass through into `ImportDeclaration.path`.
- **Why deferred**: zero impact on the cppcheck dc-compare benchmark (no multiline includes in the corpus); rare construct in real code; isolatable one-line fix that doesn't need to coordinate with the rest of the migration.
- **Fix scope**: TSE-only, ~5 lines + a TSE test. See "Plan: fix multiline `#include` normalization in TSE" below for the implementation plan.
- **Unblock**: removing `@Disabled` from the DC test once TSE strips the line-continuation pattern.

### Follow-up B: header / `.cpp` `pathWithName` merge gap (Issue 1)

- **Test**: `should produce matching pathWithName for class declared in header and its implementation file`
- **Symptom**: A class declared in `cli/executor.h` and implemented out-of-class in `cli/executor.cpp` produces two declarations with different `pathWithName` values (`cli.executor_h.Executor` vs `cli.executor_cpp.Executor`). DC's `ProcessingPipeline.mergeIdenticalTypes` only unites declarations with **identical** `pathWithName`, so the merge never fires and the .cpp's `usedTypes` never reach the header's node.
- **Investigation status (from Round 5 / Round 5 follow-up)**: a strip-cpp-extension fix in DC's `CppAnalyzer` was implemented and reverted (commit reverted before `a4a73c9`). It made the merge happen at the `pathWithName` level but didn't actually close the dep-resolution gap because the resolver substring fallback in `Node.resolveTypeImport` (the `it.withDots().contains("")` path) is what actually drives most of the cross-file matches DC main produces, and that mechanism wasn't yet understood when the fix was tried. Re-enabling the test requires both:
  1. Understanding the resolver mechanism on DC main (live debugging step documented in `plans/add-cpp-dependency-support.md` "Resume instructions for tomorrow").
  2. A `pathWithName` strategy decision: strip extensions in TSE's `Declaration` output, strip in the DC adapter, or change DC's `mergeIdenticalTypes` to ignore the file-extension suffix.
- **Why deferred**: the investigation surfaced **three DC legacy bugs** (header parse crash, empty-namespace wildcard substring fallback, dump-to-`lastOrNull()` misattribution) that collectively cap the cppcheck match rate at 45–50% — see `dependency-migration.md` "Match-rate ceiling" lesson. Issue 1 is a real gap, but pursuing it without first cracking the resolver substring mechanism risks more reverts. The disabled test documents the investigation's open state.
- **Fix scope**: cross-repo, requires DC-side changes (resolver and/or `mergeIdenticalTypes`) plus possibly TSE-side path-stripping. Material follow-up, not a quick one-liner.
- **Unblock**: see `plans/add-cpp-dependency-support.md` "Re-enable Issue 1 fix (when resolver mystery is solved)" for the resume recipe.

### Plan: fix multiline `#include` normalization in TSE

**Goal**: strip `\<newline>\s*` from raw `#include` path text in TSE's `ImportExtractor` so multiline include directives produce the same path list as their single-line equivalents.

**Steps** (TDD, one commit per step):

1. **Failing TSE test first** in `src/test/kotlin/.../languages/cpp/CppDependencyTest.kt` `ImportExtraction.IncludeDirectives`:
   ```kotlin
   @Test
   fun `should normalize multiline include with backslash continuation`() {
       // Arrange — C/C++ preprocessor splices `\<newline>` and continuation indent.
       val code = """
           #include "dir/\
               subdir/Foo.h"
       """.trimIndent()

       // Act
       val result = TreeSitterDependencies.analyze(code, Language.CPP)

       // Assert
       assertThat(result.imports.single().path).containsExactly("dir", "subdir", "Foo.h")
   }
   ```
   Run — must fail with the literal-`\` segment as the symptom.

2. **Locate the read site** in `languages/cpp/extractors/ImportExtractor.kt` — the function that reads the include's raw text from the AST and splits it on `/`. Add a normalization pass before the split:
   ```kotlin
   private val LINE_CONTINUATION = Regex("""\\\s*\n\s*""")
   …
   val cleanedPath = rawIncludePath.replace(LINE_CONTINUATION, "")
   val pathParts = cleanedPath.split('/').filter { it.isNotEmpty() }
   ```
   The regex matches a backslash + optional whitespace + newline + optional leading whitespace on the continuation line — covering both standard-conformant `\<newline>` splicing and the indented-continuation convention.

3. **Run the new test → green.** Run the whole `CppDependencyTest` suite — must stay green; this is purely additive normalization, no other tests should observe a change.

4. **Run dc-compare on cppcheck** as a regression sanity check. Expect: zero change to matched/main-only/feat-only counts (cppcheck has no multiline includes). If anything moves, investigate before continuing.

5. **DC-side**: remove the `@Disabled` annotation from `should recognize multiline include statements` in `DependaCharta/.../analyzers/cpp/CppAnalyzerTest.kt`. Run the test against the new TSE jar via composite build. Should pass.

6. **Commit sequence** (TSE then DC):
   - TSE: `test(cpp): add failing test for multiline #include normalization`
   - TSE: `feat(cpp): strip backslash-newline line continuations from #include paths`
   - DC: `test(cpp): re-enable multiline #include test now that TSE normalizes line continuations`

**Risk**: low. The regex is conservative — `\\\s*\n\s*` only matches at line continuations, never inside a normal path segment (no `\n` in legal include paths). The `filter { isNotEmpty() }` guards against the existing `#include "//foo"` edge case if any. No dc-compare movement expected.

**Out of scope for this fix**: the second disabled test (Follow-up B / Issue 1). That requires resolver investigation in DC and is tracked separately in `plans/add-cpp-dependency-support.md`.

---

## Out of Scope (carried forward from cpp-extraction-followups.md)

These items were in the prior plan but are **explicitly not in scope** for either today's plan or Phase 8:

- **Refactor Task 3 from old plan — Unify `TreeTraversal` ancestor walkers** (`hasAncestorOfType`, `hasAncestorOfTypes`, `findAncestorOfType`, `isDescendantOf` → one private `walkAncestors` sequence helper). Was attempted and reverted (commit `64b6f4a`). Today's plan scopes to `languages/cpp/` only; `TreeTraversal` lives in `shared/infrastructure/`.
- **Refactor Task 5 from old plan (optional) — Encapsulate `RealLinesOfCodeCalc` state** (8 mutable fields → two data classes). Outside the C++ slice; flagged High severity in `Reports/TreeSitterExcavationSite-analysis-2026-04-23.md` but should be addressed in a separate metrics-focused session.

---

## References

- C++ code review (this conversation, 2026-04-27)
- Existing C++ migration plan with already-completed refactors: `plans/cpp-extraction-followups.md` (esp. the "Prerequisite — refactor session" section that landed the `usedtypes/` split and `walkQualified` helper, plus "Remaining wrap-up work for next session" which Phase 8 absorbs)
- Migration rules covering DC-legacy quirks: `.claude/rules/dependency-migration.md`
- Architecture conventions: `.claude/rules/architecture.md`, `.claude/rules/extraction.md`
- Pattern reference for sub-extractor split: existing `languages/cpp/extractors/usedtypes/` directory
- Pattern reference for namespace walker: comparable logic in `languages/csharp/extractors/DeclarationExtractor.kt:44-66` (left untouched per scope)
