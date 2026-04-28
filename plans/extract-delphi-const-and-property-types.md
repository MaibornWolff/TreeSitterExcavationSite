---
name: Extract Delphi const identifiers and property types
issue:
state: complete
version:
---

## Goal

Close two of the documented Delphi v1 limitations by mirroring how Java/Kotlin already
handle their analogues:

- `const` declarations should appear as identifiers (like every other named entity in the
  language) and, when scoped inside a class, should contribute their type to the enclosing
  class's `usedTypes` (mirrors Java's `final static` field handling).
- `property` declarations should contribute their declared type to the enclosing class's
  `usedTypes` (mirrors Kotlin's `property_declaration` handling). Accessor names
  (`read GetFoo` / `write SetFoo`) are NOT captured — they are method-bind references, not
  type references; Kotlin/C# don't capture analogous things either.

Behaviour and tests live in `languages/delphi/extractors/` and the test directory; no
shared-domain or facade changes.

## What we're NOT doing

- No promotion of `declConst` to top-level `Declaration` entries — consts are not types.
- No accessor-name capture for property `read X` / `write Y`. Kotlin doesn't model property
  accessors as separate identifiers in `usedTypes`; Pascal stays consistent.
- No grammar / `tree-sitter-pascal` upgrade.
- No DC `/dc-compare` round (no DC Delphi analyzer exists).
- No new golden file — the existing `delphi_sample.pas` already exercises both shapes via
  `TSample` (no consts; the property addition would be a new fixture). Keeping the golden
  unchanged simplifies review; the `*_dependencies.golden` is regenerated only if the
  inserted fixture lands inside the sample.

## Current state

- `DelphiExtractionMapping.kt:34-36` registers `declVar` / `declField` / `declArg` for
  multi-name identifier extraction, but has no entry for `declConst`. Module-level
  `const PI = 3.14;` and class-level `const FOO = 1;` are not surfaced as identifiers.
- `UsedTypeExtractor.kt:58-63` defines
  `ALL_NODE_TYPES = {DECL_CLASS, DECL_INTF, DECL_HELPER, DECL_PROC, DEF_PROC, DECL_ARG,
  DECL_FIELD, DECL_VAR, EXPR_CALL, EXPR_DOT}` — neither `declConst` nor `declProp` is
  included, so neither contributes types.
- AST shapes verified by one-off dump:
  - `declConst` is a single-named node with optional `type` field (`const MAX_SIZE: Integer
    = 100;`) wrapped in a `declConsts` block. Untyped consts (`const PI = 3.14;`) carry no
    `type` field and contribute nothing to `usedTypes`.
  - `declProp` carries `kProperty`, `identifier` (property name), optional `declPropArgs`
    for indexed properties, then `:` + `type` field, then `kRead` / `kWrite` accessor
    identifiers. Indexed-property `declArg` children are already covered by the existing
    `declArg` walk, so they get parameter-type capture for free once `declProp` is in the
    traversal set.

## Desired end state

- `declConst` is registered in `DelphiExtractionMapping` with a single-name `Identifier`
  extraction.
- `declConst` and `declProp` are members of `UsedTypeExtractor.ALL_NODE_TYPES`, with
  dedicated extraction methods that pull their `type` field via
  `TyperefResolver.fromTypeWrapper`.
- The concatenation order grows from
  `inheritance, parameters, returnTypes, fieldTypes, variableTypes, constructorCalls, methodCalls`
  to
  `inheritance, parameters, returnTypes, fieldTypes, propertyTypes, constTypes, variableTypes, constructorCalls, methodCalls`.
  (Properties + consts are class-shape members, so they slot next to fields.)
- `DelphiExtractionTest` covers identifier surfacing for typed and untyped consts.
- `DelphiDependencyTest.UsedTypeExtraction` covers property types, indexed-property
  parameter types, and class-level const types.
- `KNOWN_ISSUES.md` "Accepted Limitations / Delphi (v1)" no longer claims `const`
  declarations are not extracted or that `property` declarations don't contribute types.
- `plans/add-delphi-dependency-support.md` "Accepted v1 limitations" mirrors the same
  cleanup with strikethrough + a pointer to this plan.
- `./gradlew clean build` and `./gradlew ktlintCheck detekt` green.

## Architecture and code reuse

All work stays inside `languages/delphi/`. No new files. Reuse:

- `TyperefResolver.fromTypeWrapper(node, sourceCode)` — already extracts a `UsedType` from
  a `type`-wrapper child. Identical shape for `declField`, `declConst`, `declProp`.
- `ExtractionStrategy.FirstChildByType("identifier")` — the single-name pattern already
  used by `declEnumValue` and `exceptionHandler` in `DelphiExtractionMapping`.

Affected files:

- `src/main/kotlin/.../languages/delphi/DelphiExtractionMapping.kt` — Phase 1 (add
  `declConst` entry).
- `src/main/kotlin/.../languages/delphi/extractors/UsedTypeExtractor.kt` — Phases 1 and 2
  (extend `ALL_NODE_TYPES`, add `extractPropertyTypes` / `extractConstTypes`, splice into
  the concat order).
- `src/test/kotlin/.../languages/delphi/DelphiExtractionTest.kt` — Phase 1 (identifier
  cases).
- `src/test/kotlin/.../languages/delphi/DelphiDependencyTest.kt` — Phases 1 and 2 (used
  type cases).
- `KNOWN_ISSUES.md` — Phase 3.
- `plans/add-delphi-dependency-support.md` — Phase 3.

## Performance considerations

None expected. Adding two node types to the grouped traversal in `UsedTypeExtractor` adds at
most one additional bucket per declaration; type counts per declaration are small.

## Migration notes

`Declaration.usedTypes` may grow for files using class-level consts or properties. This is
behaviour-additive: no previously captured type disappears, only new ones appear. CodeCharta
treats the set as a set, so duplication of the same simple name with another category is
already handled.

---

## Phase 1: `const` support

Cover both the identifier extraction (so `const PI = 3.14;` surfaces in
`ExtractionResult.identifiers`) and the type capture (so a class-level `const FOO: TBar`
contributes `TBar` to its enclosing class's `usedTypes`).

**Tasks**:
- [x] Add an extraction case in `DelphiExtractionMapping.nodeExtractions`:
   ```kotlin
   put("declConst", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
   ```
   Pascal `declConst` carries exactly one `identifier` per node (multi-name `const A = 1, B = 2;`
   is not legal Pascal — the AST splits them into siblings inside `declConsts`).
- [x] Add `DECL_CONST = "declConst"` constant to `UsedTypeExtractor` and include it in
   `ALL_NODE_TYPES`.
- [x] Add `extractConstTypes(buckets, sourceCode)`:
   ```kotlin
   buckets[DECL_CONST].orEmpty().mapNotNull {
       TyperefResolver.fromTypeWrapper(it.getChildByFieldName(TYPE_FIELD), sourceCode)
   }
   ```
   Untyped consts return null and are dropped — exactly the existing `declField` /
   `declVar` defensive pattern.
- [x] Splice `constTypes` into the final concat after `propertyTypes`:
   `inheritance + parameters + returnTypes + fieldTypes + propertyTypes + constTypes +
   variableTypes + constructorCalls + methodCalls`.
- [x] Add a `DelphiExtractionTest` case asserting that
   `const PI = 3.14; MAX_SIZE: Integer = 100;` surfaces both `PI` and `MAX_SIZE` in
   `result.identifiers`.
- [x] Add a `DelphiDependencyTest.UsedTypeExtraction` case asserting that a class with
   `class const FOO: TBar = nil;` produces `TBar` in the class's `usedTypes`.
- [x] Add a `DelphiDependencyTest.UsedTypeExtraction` case asserting that an untyped
   class-level const (`class const PI = 3.14;`) does NOT introduce a blank-named UsedType.

**Automated verification**:
- [x] `./gradlew test --tests "DelphiExtractionTest"` green.
- [x] `./gradlew test --tests "DelphiDependencyTest"` green.
- [x] `./gradlew ktlintCheck detekt` pass.

---

## Phase 2: `property` types

Capture the declared type of each `declProp` so properties contribute to the enclosing
class's `usedTypes`. Indexed-property `declArg` parameters fall through to the existing
`declArg` walk automatically.

**Tasks**:
- [x] Add `DECL_PROP = "declProp"` constant to `UsedTypeExtractor` and include it in
   `ALL_NODE_TYPES`.
- [x] Add `extractPropertyTypes(buckets, sourceCode)`:
   ```kotlin
   buckets[DECL_PROP].orEmpty().mapNotNull {
       TyperefResolver.fromTypeWrapper(it.getChildByFieldName(TYPE_FIELD), sourceCode)
   }
   ```
- [x] Splice `propertyTypes` into the final concat between `fieldTypes` and `constTypes`
   (see Phase 1 for the full updated order).
- [x] Add a `DelphiDependencyTest.UsedTypeExtraction` case asserting that
   `property Foo: TBar read FValue;` puts `TBar` in the class's `usedTypes`.
- [x] Add a `DelphiDependencyTest.UsedTypeExtraction` case asserting that
   `property Items: TList<TItem> read GetItems write SetItems;` puts `TList` and `TItem` in
   `usedTypes` and does NOT include `GetItems` / `SetItems` (accessor names are not
   captured).
- [x] Add a `DelphiDependencyTest.UsedTypeExtraction` case asserting that an indexed
   property `property Indexed[Index: Integer]: TElement read GetItem;` puts both `Integer`
   (parameter type) and `TElement` (return type) in `usedTypes`.

**Automated verification**:
- [x] `./gradlew test --tests "DelphiDependencyTest"` green.
- [x] `./gradlew ktlintCheck detekt` pass.

---

## Phase 3: Bookkeeping

**Tasks**:
- [x] In `KNOWN_ISSUES.md`, remove these two bullets from "Accepted Limitations / Delphi
   (v1)":
   - `const` declarations are not extracted.
   - `property` declarations don't contribute types.
- [x] In `plans/add-delphi-dependency-support.md`, strike through the matching bullets
   under "Accepted v1 limitations" with a short pointer to this plan, mirroring the
   convention already used for the operator-overload entry.
- [x] Set this plan's `state:` to `complete`.

**Automated verification**:
- [x] `./gradlew clean build` green.
- [x] `grep -nE "const declarations are not extracted|property.*don.?t contribute" KNOWN_ISSUES.md plans/add-delphi-dependency-support.md` returns no live (un-struck-through) matches.

---

## Notes

- **No DC reference for ordering.** Concatenation order is chosen TSE-side (mirrors
  Kotlin's "properties next to fields" intuition); no DC Delphi analyzer exists to
  reproduce.
- **Kotlin parity.** Kotlin's `UsedTypeExtractor` captures property types (via
  `extractPropertyTypes`) but not accessor names. Delphi mirrors this. Java's `final static`
  constants are captured indirectly via `field_declaration` — Pascal achieves the same by
  including `declConst` in the traversal.
- **C# gap not addressed.** C#'s `UsedTypeExtractor` does NOT capture
  `property_declaration` types — that's a separate gap, not in scope here.
- **Untyped consts contribute nothing.** `const PI = 3.14;` has no `type` field;
  `TyperefResolver.fromTypeWrapper(null)` returns null and the entry is dropped. No risk of
  blank-named UsedTypes.
- **Module-level consts are no-ops for `usedTypes`.** They live outside any `declType` and
  therefore outside any declaration's bucket walk; only their identifier is surfaced (Phase
  1's extraction-mapping change).
