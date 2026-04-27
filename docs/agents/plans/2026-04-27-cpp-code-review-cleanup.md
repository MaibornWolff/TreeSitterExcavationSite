---
date: 2026-04-27T09:11:55.468858+00:00
git_commit: ef7bd7c17d2abfe8ca18f6084227108fc16ae73d
branch: feat/cpp-dependency-support
topic: "C++ code review cleanup"
tags: [plan, cpp, refactor, code-review]
status: draft
---

# C++ Code Review Cleanup Implementation Plan

## Overview

Address the in-scope issues from the C++ code review by refactoring `languages/cpp/` only. Six small, sequential commits plus a final three-way dc-compare verification: DRY namespace walking, split `DeclarationExtractor` SRP violation, rename ambiguous extractor files/objects, lift `QualifiedIdentifierPath` from `CppTypeHelper`, relocate complexity-ignore constants, add documentation comments for DC-legacy quirks. No behavior change — pure structural cleanup verified by the existing C++ test suite and a final dc-compare against the previous run.

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
- [ ] Create `languages/cpp/extractors/declarations/InClassDeclarationFinder.kt`:
  ```kotlin
  internal object InClassDeclarationFinder {
      fun find(rootNode: TSNode, sourceCode: String): List<Declaration>
      // Owns CLASS/STRUCT/UNION/ENUM_SPECIFIER discovery, hasBody check,
      // findParentClassPath, mapType. Uses CppNamespaceWalker for namespace path.
  }
  ```
- [ ] Create `languages/cpp/extractors/declarations/OutOfClassMethodPromoter.kt`:
  ```kotlin
  internal object OutOfClassMethodPromoter {
      fun promote(rootNode: TSNode, sourceCode: String): List<Declaration>
      // Owns FUNCTION_DEFINITION → qualified-declarator → synthetic CLASS Declaration.
      // Uses CppTypeHelper.extractSecondToLastSegment and CppNamespaceWalker.
  }
  ```
- [ ] Create `languages/cpp/extractors/declarations/DeclarationMerger.kt`:
  ```kotlin
  internal object DeclarationMerger {
      fun merge(declarations: List<Declaration>): List<Declaration>
      // The linkedMapOf<Pair<List<String>, String>, Declaration> consolidation.
  }
  ```
- [ ] Reduce `DeclarationExtractor.kt` (the dependency-side object — to be renamed in Phase 3) to a thin orchestrator:
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
- [ ] `./gradlew build` passes
- [ ] `./gradlew test --tests "CppDependencyTest"` passes — full 1816-line test file unchanged
- [ ] `./gradlew test --tests "*Cpp*"` passes
- [ ] `./gradlew ktlintCheck` passes

---

## Phase 3: Rename for disambiguation

Dependencies: **Phase 2**

Eliminate the two confusingly-named files/symbols. No logic changes — file renames + symbol renames + import updates only.

**Tasks**:
- [ ] Rename file `languages/cpp/extractors/GenericDeclarationExtractor.kt` → `DeclarationIdentifierExtractor.kt`. The function `extractFromDeclaration` keeps its name (it's the public reference target in `CppExtractionMapping`).
- [ ] Rename object `DeclarationExtractor` (dependency-side, post-Phase-2) → `DependencyDeclarationExtractor`. Move its file `languages/cpp/extractors/DeclarationExtractor.kt` → `DependencyDeclarationExtractor.kt`.
- [ ] Update import + reference in `CppDependencyMapping.kt`:
  ```kotlin
  // before:
  extractDeclarations = DeclarationExtractor::extract
  // after:
  extractDeclarations = DependencyDeclarationExtractor::extract
  ```
- [ ] Update import in `CppExtractionMapping.kt` to reference the renamed file (function import path changes).
- [ ] Update test imports in `CppDependencyTest.kt` if it directly references `DeclarationExtractor` (likely not — tests usually go through the public API, but verify).

**Automated Verification**:
- [ ] `./gradlew build` passes
- [ ] `./gradlew test --tests "*Cpp*"` passes
- [ ] `./gradlew ktlintCheck` passes

---

## Phase 4: Lift `QualifiedIdentifierPath` from `CppTypeHelper`

[No phase dependencies — independent of 1–3]

Replace `CppTypeHelper.walkQualified`'s `Pair<List<String>, TSNode?>` return with a named data class. Three selector methods (`extractRightmostSegment`, `extractSecondToLastSegment`, `extractSingleSegmentScope`) consume it.

**Tasks**:
- [ ] Create `languages/cpp/extractors/QualifiedIdentifierPath.kt`:
  ```kotlin
  internal data class QualifiedIdentifierPath(
      val segments: List<String>,
      val leaf: TSNode?
  ) {
      companion object {
          fun walk(qualifiedId: TSNode, sourceCode: String): QualifiedIdentifierPath
      }
  }
  ```
  The `walk` factory contains the loop currently in `CppTypeHelper.walkQualified`.
- [ ] Refactor `CppTypeHelper.extractRightmostSegment` and `extractSecondToLastSegment` to delegate to `QualifiedIdentifierPath.walk(...)` and read `.segments` / `.leaf`. Drop the private `walkQualified` method.
- [ ] Leave `CppTypeHelper.extractSingleSegmentScope` as-is (it reads the `scope` field directly, doesn't need the walker).
- [ ] Verify `CppTypeHelper.kt` line count stays <120 lines (was 113); the `walkQualified` extraction should reduce surface area, not grow it.

**Automated Verification**:
- [ ] `./gradlew build` passes
- [ ] `./gradlew test --tests "CppDependencyTest"` passes — exercises all three selector methods through used-type extraction
- [ ] `./gradlew ktlintCheck` passes

---

## Phase 5: Move complexity-ignore constants to `CppCalculationConfig`

[No phase dependencies]

Make `CppDefinition` a pure composition file by moving the four metric-related constants and the `calculationConfig` construction to a new sibling object.

**Tasks**:
- [ ] Create `languages/cpp/CppCalculationConfig.kt`:
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
- [ ] Reduce `CppDefinition.kt` to pure composition:
  ```kotlin
  object CppDefinition : LanguageDefinition {
      override val nodeMetrics = CppMetricMapping.nodeMetrics
      override val nodeExtractions = CppExtractionMapping.nodeExtractions
      override val dependencyMapping = CppDependencyMapping.dependencyMapping
      override val calculationConfig = CppCalculationConfig.config
  }
  ```

**Automated Verification**:
- [ ] `./gradlew build` passes
- [ ] `./gradlew test --tests "CppMetricsTest"` passes — covers the complexity-ignore rules
- [ ] `./gradlew ktlintCheck` passes

---

## Phase 6: Documentation comments + dead null-check cleanup

Dependencies: **Phase 2** (touches `OutOfClassMethodPromoter`), **Phase 3** (references the renamed `DependencyDeclarationExtractor` symbol)

Add one-line comments documenting the DC-legacy quirks the C++ extractors implement, and remove the C++-only dead `current != null` half of `while` loops walking `node.parent`. Both small, low-risk; bundled to keep commit count down.

**Tasks**:
- [ ] In `DependencyDeclarationExtractor` (post-Phase-3 name) or wherever `hasBody` is used, add a comment to the `if (!hasBody(node)) return null` line explaining: forward declarations have no body — DC legacy emits no Declaration for them.
- [ ] In `CallExpressionTypeExtractor.kt` near the `IDENTIFIER` branch (~`:62-67`), add a comment explaining the bare-name `UsedType` emission for nested-call arg-list and throw-statement contexts (mirrors DC's empty-namespace-wildcard behavior — see `dependency-migration.md` lesson on tree-sitter-cpp parsing).
- [ ] Remove dead `current != null && ` from the ancestor-walk loops in `OutOfClassMethodPromoter` and `ImportExtractor` (the C++-side files only). Pattern: `while (current != null && !current.isNull)` → `while (!current.isNull)`. Pre-existing equivalents in `csharp/` are left unchanged.

**Automated Verification**:
- [ ] `./gradlew build` passes
- [ ] `./gradlew test --tests "*Cpp*"` passes
- [ ] `./gradlew ktlintCheck` passes

---

## Phase 7: Final dc-compare verification

Dependencies: **Phases 1–6** (all refactor commits must be landed)

Three-way comparison on cppcheck to confirm the refactor introduced zero behavior change. Generates a new `dc-compare/after-refactor/` folder alongside the existing `dc-compare/main/` (DC legacy baseline) and `dc-compare/feature/` (latest pre-refactor TSE run). Produces no code commit — only an analysis output and a comparison report.

**Tasks**:
- [ ] Verify DC's composite-build wiring is in place per `.claude/rules/dependency-migration.md`:
  - `DependaCharta/analysis/settings.gradle.kts` includes `../../TreeSitterExcavationSite`
  - `DependaCharta/analysis/build.gradle.kts` uses the composite-build dep (`de.maibornwolff.treesitter.excavationsite:treesitter-excavationsite`), not JitPack
- [ ] Generate the after-refactor analysis output:
  ```bash
  cd ../DependaCharta/analysis
  ./gradlew fatJar
  java -jar build/libs/dependacharta.jar -d "../../cppcheck" -o "../../dc-compare/after-refactor" -f analysis
  ```
- [ ] Run two pairwise comparisons via the `/dc-compare` skill or the comparison script in `plans/cpp-extraction-followups.md` Step 3:
  - `dc-compare/feature/` vs `dc-compare/after-refactor/` → expected: zero divergence (identical node/dependency sets)
  - `dc-compare/main/` vs `dc-compare/after-refactor/` → expected: matched/main-only/feat-only counts equal the baseline `dc-compare/main/` vs `dc-compare/feature/`
- [ ] Revert DC's composite-build wiring before any DC-side commit lands (composite-build paths must not be committed per migration rules).

**Automated Verification**:
- [ ] `dc-compare/feature/` vs `dc-compare/after-refactor/` reports zero divergence (no main-only or feat-only edges)
- [ ] `dc-compare/main/` vs `dc-compare/after-refactor/` reports the same matched / main-only / feat-only counts as the latest pre-refactor baseline (R15 reference: matched ≈ 1118, main-only ≈ 1337, feat-only ≈ 507, ~45.6%)
- [ ] `./gradlew build` in TSE green at the final commit

---

## References

- C++ code review (this conversation, 2026-04-27)
- Existing C++ migration plan with already-completed refactors: `plans/cpp-extraction-followups.md` (esp. the "Prerequisite — refactor session" section that landed the `usedtypes/` split and `walkQualified` helper)
- Migration rules covering DC-legacy quirks: `.claude/rules/dependency-migration.md`
- Architecture conventions: `.claude/rules/architecture.md`, `.claude/rules/extraction.md`
- Pattern reference for sub-extractor split: existing `languages/cpp/extractors/usedtypes/` directory
- Pattern reference for namespace walker: comparable logic in `languages/csharp/extractors/DeclarationExtractor.kt:44-66` (left untouched per scope)
