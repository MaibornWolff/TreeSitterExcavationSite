---
name: Delphi cross-language gap fixes
issue:
state: complete
version:
---

## Goal

Close the cross-language gaps and review-debt in Delphi support uncovered by three review
subagents after `plans/fix-delphi-const-property-review-findings.md` landed: missing
`as`/`is` cast extraction, missing attribute extraction, missing generic
parameter/constraint capture, the last open `KNOWN_ISSUES.md` Delphi bullet (paren-less
message chains), plus tests and nitpick cleanup that the prior fix left behind.

## What we're NOT doing

- **Multi-constraint type-reference capture beyond the first constraint.** `<T: TBase, IFoo>`
  captures `TBase` only — `IFoo` lives in a tree-sitter-pascal ERROR sibling and we will
  not walk ERROR shapes (decision: strict capture; document as KNOWN_ISSUE).
- **Keyword constraint capture.** `<T: class>` / `<T: constructor>` / `<T: record>` —
  these are semantic markers, not type references. No peer language captures them.
- **Attribute argument types.** `[SomeAttr(TFoo, 1)]` captures `SomeAttr` only. `TFoo`
  inside the argument list is not separately surfaced (mirrors Java/Kotlin/C#).
- **`uses Foo in 'path/Foo.pas';` project-import form.** Already documented as v1
  limitation; tree-sitter-pascal 0.10.2 ERROR-recovers it. Defer.
- **Anonymous pointer types in qualified references** (`Foo.^TBar`). Defensive guard
  already drops the empty name; out of scope.
- **No DC `/dc-compare` round.** No DC Delphi analyzer exists.

## Current state (verified via AST probe and source read)

- **`is` / `as`**: parse as `exprBinary` with `[operator]=kIs`/`kAs` and `[rhs]=identifier`
  (BARE — not wrapped in `typeref`). `TyperefResolver` won't help; need a small RHS reader.
- **Attributes**: `rttiAttributes` node carries one or more bracket pairs. Each contains
  either `identifier` (`[Inject]`) or `exprCall(entity=identifier, args=exprArgs)`
  (`[SomeAttr('x',1)]`). Attached to `declType` (class-level) or `declProc` (member-level).
- **Generic decls**: `genericTpl(entity=identifier, args=genericArgs(genericArg(...)))`.
  Each `genericArg` carries one or more `[name]=identifier` children (so `<T, U>` is a
  single `genericArg` with two name children) plus an optional `[type]=type→typeref`
  for a constraint on that param. Multi-constraint on the SAME param (`<T: A, B>`):
  parser emits an ERROR sibling for the second constraint. Keyword constraints
  (`<T: class>`, `<T: constructor>`): ERROR recovery.
- **Generic call sites**: `typerefTpl` already handled by `TyperefResolver.genericTemplate`
  (no change needed for call-site generics).
- **Lambdas**: `lambda(args=declArgs(declArg(name=identifier, type=type)))`. The existing
  `findAllDescendantsOfType("declArg")` walk already reaches them — verification only.
- **`MetricCondition` system** in `shared/domain/MetricCondition.kt` only supports
  child-side checks (`Always`, `ChildFieldMatches`, `ChildPositionMatches`). No
  parent-aware variant. The cleanest path for the message-chain fix is a new
  `CalculationExtensions.ignoreNodeForMessageChainCall` lambda — mirrors the existing
  five `ignoreNode...` hooks.

## Architecture and code reuse

- **Reuse**: `TyperefResolver.fromTypeWrapper` for declared-type wrappers; the existing
  one-pass `findAllDescendantsGroupedByType` over `ALL_NODE_TYPES`; the
  `isPotentialTypeReceiver` uppercase-first heuristic for cast RHS bare identifiers.
- **New buckets** in `UsedTypeExtractor`: `EXPR_BINARY`, `RTTI_ATTRIBUTES`, `GENERIC_TPL`.
  Concat order grows by three categories at the end:
  ```
  inheritance, parameters, returnTypes, fieldTypes, propertyTypes, constTypes,
  variableTypes, constructorCalls, methodCalls,
  castTypes, attributeTypes, genericConstraintTypes
  ```
- **One small `shared/domain/` change** (already approved): add
  `ignoreNodeForMessageChainCall: (TSNode, String) -> Boolean = { _, _ -> false }` to
  `CalculationExtensions`, plumb it through `LanguageDefinitionMetricsAdapter` →
  `MetricNodeTypes` (new field) → `MessageChainsCalc` (one if-check).
- **No grammar / `tree-sitter-pascal` upgrade.**

### Affected files

- `src/main/kotlin/.../languages/delphi/extractors/UsedTypeExtractor.kt` — Phases 3, 4, 6.
- `src/main/kotlin/.../languages/delphi/extractors/TyperefResolver.kt` — Phase 1 (comments).
- `src/main/kotlin/.../languages/delphi/DelphiExtractionMapping.kt` — Phase 5 (genericArg).
- `src/main/kotlin/.../languages/delphi/DelphiMetricMapping.kt` — Phase 7 (exprDot adds
  MessageChainCall + supplies the new ignore hook via DelphiDefinition).
- `src/main/kotlin/.../languages/delphi/DelphiDefinition.kt` — Phase 7
  (`calculationExtensions` override).
- `src/main/kotlin/.../shared/domain/CalculationExtensions.kt` — Phase 7 (one new lambda field).
- `src/main/kotlin/.../integration/metrics/ports/MetricNodeTypes.kt` — Phase 7
  (expose `ignoreNodeForMessageChainCall`).
- `src/main/kotlin/.../integration/metrics/adapters/LanguageDefinitionMetricsAdapter.kt`
  — Phase 7 (forward the hook).
- `src/main/kotlin/.../integration/metrics/calculators/MessageChainsCalc.kt` — Phase 7
  (one if-check before `currentChainCallCount++`).
- `src/main/kotlin/.../integration/dependencies/README.md` — Phase 8 only
  (single consolidated concat-order row update; phases 3/4/6 add the categories in
  code, Phase 8 reflects the final order in docs in one diff).
- `src/test/kotlin/.../languages/delphi/DelphiDependencyTest.kt` — every phase.
- `src/test/kotlin/.../languages/delphi/DelphiExtractionTest.kt` — Phases 2, 5.
- `src/test/kotlin/.../languages/delphi/DelphiMetricsTest.kt` — Phase 7.
- `KNOWN_ISSUES.md` — Phase 8.
- `plans/add-delphi-dependency-support.md` — Phase 8.

## Performance considerations

None expected. Adding three node types to `ALL_NODE_TYPES` adds at most one extra bucket
per declaration. No new file-wide passes.

## Migration notes

`Declaration.usedTypes` will grow for files that use casts, attributes, or generic
constraints. This is behaviour-additive: no previously captured type disappears, only
new ones appear. CodeCharta treats `usedTypes` as a set; duplicates of the same name
across categories are already handled.

## Tasks

### 1. Bookkeeping + nitpicks (Phase 1)
- Update the stale comment block in `TyperefResolver.kt:54-57` ("we currently don't
  unwrap nested cases" — no longer true; the recursion does unwrap them).
- Add a one-line comment distinguishing `TYPE_NODE` (node-type string `"type"`) from
  `TYPE_FIELD` (field name `"type"`) in `TyperefResolver.kt`.
- Strike through the "Golden files are generated on first test run" bullet in
  `plans/add-delphi-dependency-support.md` "Accepted v1 limitations" — workflow note,
  not a real limitation.

### 2. Lock-in tests for the recent array/set fix + lambda verification (Phase 2)
- Nested array fixture: `FNested: array of array of TFoo;` → captures inner `TFoo`.
- Set-typed property: `property Colors: set of TColor read FColors;` → captures `TColor`.
- Set-typed const inside a record: `class const Codes: set of TCode = [];` inside
  `TFoo = record` → captures `TCode`.
- Bounded-array with non-literal bounds: `array[Low..High] of TFoo;` → captures `TFoo`,
  does NOT include `Low` / `High` (range is opaque).
- Lambda usedTypes: a class method whose body declares an anonymous procedure with
  `var Helper: TBodyType` or a `(procedure(P: TBar) begin end)` invocation should
  contribute `TBar` (and `TBodyType`) to the enclosing class's usedTypes via the
  existing `findAllDescendantsOfType("declArg")` and `declVar` traversals. One test;
  no production code change expected. Don't re-test parameter-name extraction —
  that's already covered by the existing `declArg` extraction tests.

### 3. Cast / type-test extraction (Phase 3)
Cleanly the most isolated P0 — adds one bucket, one extractor.
- Add `EXPR_BINARY = "exprBinary"` constant to `UsedTypeExtractor`; include in `ALL_NODE_TYPES`.
- New `extractCastTypes(buckets, sourceCode)`:
  - Iterate `buckets[EXPR_BINARY]`. For each, read `getChildByFieldName("operator").type`.
  - Skip unless operator is `kAs` or `kIs`.
  - Read `getChildByFieldName("rhs")`. If it's a bare `identifier`, take its trimmed text;
    if it's `typeref` (forward-compat in case grammar changes), delegate to
    `TyperefResolver.toUsedType`.
  - Apply the existing uppercase-first heuristic (`isPotentialTypeReceiver`) so that
    pattern guards like `Foo is nil` don't pollute usedTypes.
- Splice `castTypes` after `methodCalls` in the concat.
- Tests:
  - `(Foo as TBar).Name` → `TBar` in usedTypes.
  - `if Foo is TBar then ...` → `TBar` in usedTypes.
  - Cast inside a class method body → attributed to the enclosing class.
  - `Foo as nil` (semantically nonsense but parseable) → defensive guard drops.
  - Both `as` and `is` in same scope, deduplicated to one entry.
- README row update is consolidated in Phase 8.

### 4. Attribute extraction (Phase 4)
- Add `RTTI_ATTRIBUTES = "rttiAttributes"` constant; include in `ALL_NODE_TYPES`.
- New `extractAttributeTypes(buckets, sourceCode)`:
  - For each `rttiAttributes` node, walk its named children.
  - Two shapes per bracket pair: bare `identifier` (e.g. `[Inject]`) or
    `exprCall(entity=identifier, args=exprArgs)`. Read the attribute name from the
    identifier (or `exprCall`'s entity-field identifier).
  - Skip empty / blank.
- Splice `attributeTypes` after `castTypes` in the concat.
- Tests:
  - `[Inject] TFoo = class` → `Inject` in usedTypes for `TFoo`.
  - `[SomeAttr('x', 42)] TFoo = class` → `SomeAttr` in usedTypes; `'x'` and `42` are not.
  - Multiple stacked attributes coalesced into one `rttiAttributes` → all names captured.
  - Member-level attribute on a method: `[Validate] procedure DoIt;` → `Validate` in
    enclosing class's usedTypes.
- Note: argument types are NOT captured (mirrors Java/Kotlin/C#).
- README row update is consolidated in Phase 8.

### 5. Generic type parameter identifier extraction (Phase 5)
- Add to `DelphiExtractionMapping.nodeExtractions`:
  ```kotlin
  put("genericArg", Extract.Identifier(customMulti = ::extractDelphiMultipleNames))
  ```
  `genericArg` carries multiple `[name]=identifier` children (one per type parameter).
  Reuses the existing multi-name extractor used by `declVar`/`declField`/`declArg`.
- Test: `TFoo<T, U> = class` → identifiers contain `T` and `U` alongside `TFoo`.
- Test: `TBaz<T: TBase> = class` → identifiers contain `T` (constraint type goes to
  usedTypes via Phase 6, not identifiers).

### 6. Generic constraint capture (Phase 6)
- Add `GENERIC_TPL = "genericTpl"` constant; include in `ALL_NODE_TYPES`.
- New `extractGenericConstraintTypes(buckets, sourceCode)`:
  - For each `genericTpl`, descend `getChildByFieldName("args")` (`genericArgs`) →
    iterate `genericArg` children.
  - Per `genericArg`, find the **first** `getChildByFieldName("type")` wrapper.
  - Delegate to `TyperefResolver.fromTypeWrapper` to resolve to a `UsedType`. Skip
    silently when null.
  - **Strict capture**: only the first `[type]` field per `genericArg` is read. Multi-
    constraint second-and-onwards (which the parser drops into ERROR siblings) is left
    uncaptured by design — see KNOWN_ISSUE bullet added in Phase 8.
- Splice `genericConstraintTypes` after `attributeTypes`.
- Tests:
  - `TFoo<T: TBase> = class` → `TBase` in `TFoo.usedTypes`.
  - `TFoo<T: TBase, U: IFoo> = class` → `TBase` AND `IFoo` (each `genericArg` has its
    own `[type]` field; multi-PARAM works, only multi-CONSTRAINT-on-single-param breaks).
  - `TFoo<T> = class` → no constraint contribution; usedTypes empty for `TFoo`.
  - `TFoo<T: class> = class` → keyword constraint, nothing captured (`fromTypeWrapper`
    returns null because the inner is not `typeref`/`declArray`/`declSet`).
- README row update is consolidated in Phase 8.

### 7. Paren-less message chain (Phase 7)
The only phase that touches `shared/domain/` and `integration/metrics/`.

**Goal**: count `Obj.M1.M2.M3.M4` (paren-less) as a chain of 4 calls. Today only
`exprCall` is a "call" node; `exprDot` only contributes "chain" status. Adding
`MessageChainCall` to `exprDot` makes paren-less chains count — but it would also
double-count `A.b().c().d().e()` (where each `exprCall` wraps an `exprDot`, both of
which would then count as calls). The new ignore-hook makes `exprDot` only count when
its parent is NOT `exprCall`, which is exactly the rule we need. The hook is
**load-bearing**, not defensive: without it, `exprDot` cannot safely be a call node.

**MetricNodeTypes audit**: verified there is exactly one production implementation
(`LanguageDefinitionMetricsAdapter`) and zero test stubs (`grep -r ": MetricNodeTypes"`
returns only the adapter). Adding a new field is safe.

- **`shared/domain/CalculationExtensions.kt`**: add a new field
  ```kotlin
  val ignoreNodeForMessageChainCall: (TSNode, String) -> Boolean = { _, _ -> false }
  ```
  Mirrors the existing five `ignoreNode...` lambdas.
- **`integration/metrics/ports/MetricNodeTypes.kt`**: expose
  `val ignoreNodeForMessageChainCall: (TSNode, String) -> Boolean`.
- **`integration/metrics/adapters/LanguageDefinitionMetricsAdapter.kt`**: forward
  `definition.calculationExtensions.ignoreNodeForMessageChainCall` to the new port field.
- **`integration/metrics/calculators/MessageChainsCalc.kt`**: inside the
  `if (isCallNode(nodeType)) { ... }` branch, guard the `currentChainCallCount++`
  increment with `if (nodeTypeProvider.ignoreNodeForMessageChainCall(node, nodeType)) return 0`.
  Returns 0 (does not increment, does not reset chain).
- **`languages/delphi/DelphiMetricMapping.kt`**: extend `exprDot` mapping to include
  `Metric.MessageChainCall`:
  ```kotlin
  put("exprDot", setOf(Metric.MessageChain, Metric.MessageChainCall))
  ```
- **`languages/delphi/DelphiDefinition.kt`**: override `calculationExtensions` with:
  ```kotlin
  override val calculationExtensions = CalculationExtensions(
      ignoreNodeForMessageChainCall = { node, nodeType ->
          // exprDot wrapped by exprCall is already counted via the exprCall mapping;
          // skip to avoid double-counting on `A.b().c()` chains. Without this guard,
          // adding MessageChainCall to exprDot would over-count parenthesised chains.
          nodeType == "exprDot" && !node.parent.isNull && node.parent.type == "exprCall"
      }
  )
  ```
- Tests in `DelphiMetricsTest`:
  - `Obj.M1.M2.M3.M4` (paren-less) → `messageChains` = 1 (chain of 4 calls).
  - `Obj.M1().M2().M3().M4()` (parenthesised) → still `messageChains` = 1 (no double
    count due to the new ignore hook).
  - Mixed: `Obj.M1.M2().M3.M4()` → still 1.
  - Short chain `A.B.C` (3 nodes) → 0 (below threshold).
  - Single non-chain expression → 0.
- Defensive verification: re-run all existing `*MetricsTest` files (Java, Kotlin, C#,
  TypeScript, etc.) — none of them set `ignoreNodeForMessageChainCall`, so the default
  `{ _, _ -> false }` keeps existing behaviour.

### 8. Final bookkeeping (Phase 8)
- **`integration/dependencies/README.md`**: update the Delphi concat-order row to the
  final order with all categories added in Phases 3, 4, 6:
  `inheritance, parameters, returnTypes, fieldTypes, propertyTypes, constTypes,
  variableTypes, constructorCalls, methodCalls, castTypes, attributeTypes,
  genericConstraintTypes`.
- **`KNOWN_ISSUES.md`**: remove the message-chain paren-less bullet from "Accepted
  Limitations / Delphi (v1)". Add a new bullet: "Multi-constraint type-reference
  generics beyond the first constraint are not captured. `TFoo<T: TBase, IFoo>`
  produces only `TBase` because tree-sitter-pascal 0.10.2 ERROR-recovers the second
  constraint." (Section heading stays — the new bullet keeps it non-empty.)
- **`plans/add-delphi-dependency-support.md`** "Accepted v1 limitations":
  - Strike through the golden-file-generation bullet (workflow note, not a real limitation).
  - Add a strike-through plus pointer to this plan for the message-chain bullet
    (matching the convention used for prior fixes).
- **`plans/extract-delphi-const-and-property-types.md`** — already complete; no change.
- **This plan**: set `state: complete`.

## Steps

- [x] Complete Task 1: Bookkeeping + nitpicks
- [x] Complete Task 2: Lock-in tests for array/set fix + lambda verification
- [x] Complete Task 3: Cast / type-test extraction
- [x] Complete Task 4: Attribute extraction
- [x] Complete Task 5: Generic type parameter identifier extraction
- [x] Complete Task 6: Generic constraint capture
- [x] Complete Task 7: Paren-less message chain
- [x] Complete Task 8: Final bookkeeping
- [x] `./gradlew clean build` green
- [x] `./gradlew ktlintCheck detekt` green
- [x] Set this plan's `state:` to `complete`

## Notes

- **Concat order rationale**: new categories appended after `methodCalls` so the
  call-site categories stay grouped. Cast/attribute/constraint are non-call categories
  but adding them at the end keeps the diff impact predictable for downstream
  consumers (DC/CodeCharta) who treat `usedTypes` as a set.
- **Why the message-chain fix needs `shared/domain/`**: the calculator
  (`MessageChainsCalc`) is shared. Its loop checks `messageChainsCallNodeTypes`. To
  make `exprDot` *conditionally* count (only when its parent isn't `exprCall`) we need
  either (a) a new `MetricCondition` variant + `MessageChainCallConditional` metric +
  adapter branch + nested-types path, or (b) a single new ignore-lambda. (b) chosen
  per the user decision. ~10 LOC outside `languages/delphi/`.
- **Lambda P0 was actually a no-op**: the existing `findAllDescendantsOfType("declArg")`
  walk reaches lambda parameters. Phase 2 verifies and locks behavior with one test;
  no production code change in extraction or dependency layers.
- **Multi-constraint generics: the user's call**: capture only the cleanly-parsed first
  constraint. Document the dropped second-and-onwards as KNOWN_ISSUE. Avoids walking
  ERROR-recovered shapes (which would risk false positives).
- **No new `shared/domain/` types**: the message-chain lambda is the only addition,
  and it follows the existing 5-lambda pattern in `CalculationExtensions`.
