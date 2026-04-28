---
name: Align Delphi Used-Type Concatenation Order with Kotlin
issue: ~
state: complete
version: ~
---

## Goal

Reorder the categories returned by `UsedTypeExtractor.extractFromRoots()` for Delphi so they follow Kotlin's "data block → callable signature → annotations → calls" philosophy — the current docstring already claims this but the code does not. Kotlin does not have a 1:1 mapping (it lacks fields / consts / variables / casts / generic constraints), so this is structural alignment, not strict mirroring: Delphi's data block expands to cover its separate `field` / `property` / `const` / `var` declarations, and Delphi-specific extras (cast types, generic-constraint types) are appended at the end. The order matters: it determines which entry survives in `LinkedHashSet` deduplication and influences DC's downstream resolver / levelizer.

## Tasks

### 1. Reorder the return statement in `UsedTypeExtractor.kt`

`languages/delphi/extractors/UsedTypeExtractor.kt` lines 123–126.

New order (Kotlin's structural philosophy is `inheritance → properties → params → returns → annotations → ctorCalls → callExpressions`; Delphi's data block expands Kotlin's "properties" slot, and Delphi-only categories sit at the end):

```
inheritance
+ fieldTypes + propertyTypes + constTypes + variableTypes   // data block (Kotlin's "properties" slot, expanded for Delphi's separate categories)
+ parameters + returnTypes                                  // callable signature
+ attributeTypes                                            // Kotlin's "annotations" slot
+ constructorCalls + methodCalls                            // call sites
+ castTypes + genericConstraintTypes                        // Delphi-specific, end
```

### 2. Update the docstring at `UsedTypeExtractor.kt` lines 9–27

- Replace the inline category list (lines 12–16) so all 12 categories appear in the new order:
  `inheritance, fieldTypes, propertyTypes, constTypes, variableTypes, parameters, returnTypes, attributeTypes, constructorCalls, methodCalls, castTypes, genericConstraintTypes`.
- Rewrite the rationale paragraph (lines 18–22): drop the misleading "mirrors Kotlin's ordering" wording. Replace with an honest description: "The order follows Kotlin's `inheritance → data → callable → annotations → calls` philosophy; Kotlin has no direct equivalent for Delphi's `field`/`const`/`var` declarations or for `cast`/`genericConstraint` types, so the data block is expanded and the Delphi-only categories are appended at the end."

### 3. Update order-sensitive tests in `DelphiDependencyTest.kt`

Three assertions need to change; one stays as-is:

- **Test at line 606 `should emit used types in fixed concatenation order`** (assertion at lines 636–646) — reorder the `containsSubsequence(...)` list to: `TBase, TFieldType, TPropType, TConstType, TLocalType, TParamType, TReturnType, TCtorType, TUtility`. Also rewrite the inline comment block above the assertion (lines 632–634) to list **all 12 categories** in the new order, not just the categories the test code happens to exercise.
- **Test at line ~830 `should extract types from indexed properties`** (assertion at line 849) — flip from `containsExactly("Integer", "TElement")` to `containsExactly("TElement", "Integer")`. Update the inline comment to read "propertyTypes come before parameters in the concatenation order".
- **Test at line ~1130 `should extract types of default array property` (or similar)** (assertion at line 1150) — same pattern as the indexed-property test: flip from `containsExactly("Integer", "TItem")` to `containsExactly("TItem", "Integer")`. Verify there's no inline comment to keep in sync; if there is one referring to parameter-before-property order, update it.
- **Test at line ~649** (`should capture used types from method bodies implemented in implementation section`, assertion `containsExactly("TBodyOnlyType", "TBodyCtor")` at line 676) — **no change**; "variableTypes come before constructorCalls" is true under both old and new order.

Run a final `grep` for `containsExactly` and `containsSubsequence` in `DelphiDependencyTest.kt` after the edits to confirm no order-sensitive assertions are left in their old state.

### 4. Update `KNOWN_ISSUES.md` and `CHANGELOG.md`

- `KNOWN_ISSUES.md`: if any line still references the old order or the old "mirrors Kotlin" rationale, update it.
- `CHANGELOG.md`: add an entry under `[Unreleased]` → `Changed`: "Reorder Delphi used-type concatenation to match Kotlin's category sequence (inheritance → data → callable → annotations → calls)."

## Steps

- [x] Reorder the return statement in `UsedTypeExtractor.kt` (Task 1)
- [x] Rewrite the docstring at `UsedTypeExtractor.kt` lines 9–27 (Task 2)
- [x] Update the `containsSubsequence` assertion + rewrite the comment to list all 12 categories in the "fixed concatenation order" test (Task 3a)
- [x] Flip the `containsExactly` assertion + comment in the indexed-property test at line 849 (Task 3b)
- [x] Flip the `containsExactly` assertion (and any order-related comment) in the default-array-property test at line 1150 (Task 3c)
- [x] Final grep of `containsExactly` / `containsSubsequence` in `DelphiDependencyTest.kt` — confirm no stale ordering assertions remain
- [x] Run `./gradlew test --tests "*delphi*"` — all Delphi tests green
- [x] Run `./gradlew test` — full suite green, no regressions in other languages
- [x] Run `./gradlew ktlintCheck` — passes
- [x] Update `KNOWN_ISSUES.md` if needed (Task 4) — no references to old order or "mirrors Kotlin" found, no change required
- [x] Add `CHANGELOG.md` entry (Task 4)

## Notes

- This is a TSE-only change. DC's `DelphiAnalyzer` is unaffected (it just consumes the resulting set).
- The output is `Set<UsedType>` but `.toSet()` produces a `LinkedHashSet` which preserves insertion order — that's why the order matters in practice.
- DC's `DelphiAnalyzerTest` uses `contains` / `anyMatch` (not order-sensitive), so no DC test changes are required.
- No existing real-world Delphi project is being levelized incorrectly today — this is a consistency / accuracy fix, not a bug fix. The motivation is that the docstring claim should match the code.
- Consider whether the test names should reference category positions less specifically (e.g. drop "before propertyTypes" from the indexed-property test name) so future reorders don't require renaming. Optional polish, not required by this plan.
