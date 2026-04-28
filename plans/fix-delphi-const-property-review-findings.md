---
name: Fix Delphi const/property review findings
issue:
state: complete
version:
---

## Goal

Address the punch list raised in the post-implementation review of
`plans/extract-delphi-const-and-property-types.md`. Two real bugs (one pre-existing,
exposed by the new feature; one missed by the plan), one stale documentation table, and
a thin test that should be tightened to lock down the new concatenation order.

## What we're doing

Four phases, ordered from lowest blast radius to highest:

1. **Documentation + concat-order test tightening** — pure bookkeeping; locks the new order in.
2. **Property identifier extraction** — add `declProp` to `DelphiExtractionMapping`. Property
   names should surface in `result.identifiers` (parity with Java/Kotlin/C#).
3. **`array of` / `set of` element-type extraction** — extend `TyperefResolver.fromTypeWrapper`
   to descend into `declArray` / `declSet` and surface the element type. Pre-existing
   limitation that affected fields/vars too; exposed by the new const/property paths.
4. **Edge-case test coverage** — pick the highest-value P1 cases and lock current behavior.

## What we're NOT doing

- Multi-element array/set surfacing (e.g. `array of array of TFoo` → only inner `TFoo` is
  captured). Doubly-nested element types are out of scope.
- Bounded-array range-type extraction (`array[TIndex..TOther] of TFoo` would also reference
  `TIndex` / `TOther`). Treat the range as opaque.
- Pointer-typed properties (`property Foo: ^TBar`). Already work via `typerefPtr` because
  pointer types are still wrapped by `typeref` — out of scope unless the test reveals a gap.
- New `KNOWN_ISSUES.md` entry for the *bounded-array element loss* case until verified.

## AST shapes used

Verified via one-shot probe:

- `declField` / `declProp` / `declConst` each carry a `type` field whose wrapper holds
  exactly one child: `typeref`, `declArray`, or `declSet`.
- `declArray` and `declSet` carry their element type as an unnamed child of node-type
  `"type"` (a wrapper) → `typeref` → `identifier` / `typerefDot` / etc. There is **no**
  `type` field on the inner wrapper — it is a node-type-named child.

## Tasks

### 1. Lock new concat order (Phase 1)
- Update the Delphi row in `src/main/kotlin/.../integration/dependencies/README.md` to read
  `inheritance, parameters, returnTypes, fieldTypes, propertyTypes, constTypes,
  variableTypes, constructorCalls, methodCalls`.
- Extend `DelphiDependencyTest.UsedTypeExtraction.should emit used types in fixed
  concatenation order`:
  - Add `class const FOO: TConstType = nil;` and `property Bar: TPropType read FValue;` to
    the class fixture (interface section).
  - Extend the `containsSubsequence` chain so `TPropType` appears between fieldTypes and
    constTypes, and `TConstType` between propertyTypes and variableTypes.
  - Update the comment block on the assertion to mention the new categories.

### 2. Property identifier extraction (Phase 2)
- Add to `DelphiExtractionMapping.nodeExtractions`:
  ```kotlin
  put("declProp", Extract.Identifier(single = ExtractionStrategy.FirstChildByType(IDENTIFIER)))
  ```
  `declProp` carries `[name]=identifier` per the AST probe — first identifier child is the
  property name.
- Add a `DelphiExtractionTest.IdentifierExtraction` case asserting that
  `property Foo: TBar read FValue;` and `property Items[I: Integer]: TItem read GetItem;`
  surface `Foo` and `Items` (not the type names — those are not identifiers per the
  existing `declField` precedent).

### 3. `array of` / `set of` element type capture (Phase 3)
- Extend `TyperefResolver.fromTypeWrapper(typeWrapper, sourceCode)`:
  - When the wrapper's first non-trivial child is `typeref`, current behavior (no change).
  - When it is `declArray` or `declSet`, find that child's first node-type-`"type"` child
    (unnamed, walk children directly) and recursively resolve that wrapper. Returns the
    element type.
  - When it is anything else, return null (status quo).
- Defensive: keep returning `null` when the inner wrapper has no `typeref` — the existing
  blank-name guard in `UsedTypeExtractor` is the second line of defense.
- Add tests in `DelphiDependencyTest.UsedTypeExtraction`:
  - `array of Byte` field → `Byte` in usedTypes.
  - `set of TColor` field → `TColor` in usedTypes.
  - `array[0..9] of Integer` field → `Integer` in usedTypes (bounded form; range is opaque).
  - `property Buf: array of Byte read FBuf;` → `Byte` in usedTypes.
  - `class const Colors: set of TColor = [];` → `TColor` in usedTypes.

### 4. Edge-case lockdown tests (Phase 4)
Pick the highest-value cases from the review. No production-code changes.
- Qualified property type: `property Q: System.TDateTime read FQ;` → `TDateTime`.
- Two-arg generic property type: `property D: TDict<TKey, TValue> read FD;` → `TDict` with
  generics `TKey, TValue`.
- `class property CFoo: TBaz read FBaz;` → `TBaz` (verifies `kClass`-prefixed properties
  go through the same path).
- Default array property `property Items[I: Integer]: TItem read GetItem; default;` →
  `Integer` and `TItem` (the `default;` attribute must not break extraction).
- Module-level typed const: `const MAX: Integer = 1;` outside any class — surfaces `MAX`
  in identifiers, contributes nothing to any decl's `usedTypes` (assert empty
  `result.declarations`).

## Steps

- [x] Complete Task 1: Lock new concat order
- [x] Complete Task 2: Property identifier extraction
- [x] Complete Task 3: `array of` / `set of` element type capture
- [x] Complete Task 4: Edge-case lockdown tests
- [x] `./gradlew clean build` green
- [x] `./gradlew ktlintCheck detekt` green
- [x] Set this plan's `state:` to `complete`

## Notes

- AST probe confirmed `declConst.type` and `declProp.type` are both `type` wrappers, same
  shape as `declField.type` — no per-node-type branching needed in `fromTypeWrapper`.
- `TyperefResolver.fromTypeWrapper` is shared between fields, vars, props, and consts. The
  array/set fix benefits all four call sites simultaneously.
- The pre-existing limitation around `array of` / `set of` was never documented in
  `KNOWN_ISSUES.md`. After the fix lands, no new entry needed.
