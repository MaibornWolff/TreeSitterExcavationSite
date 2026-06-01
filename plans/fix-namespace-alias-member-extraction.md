---
name: fix-namespace-alias-member-extraction
issue:
state: complete
version:
---

## Goal

`new ns.Class()` and `ns.Class.method()` patterns where `ns` is a namespace alias
(`import * as ns from './module'`) produce no `UsedType` for `Class`, so DC emits no dependency
edge. This is a follow-up to the bindingName/aliasMap fix (commits `ttwmxqpu`, `uztxluov`):
that fix added `ns→ns` to the aliasMap, but `extractMemberAccesses` and `extractConstructorCalls`
still ignore the aliasMap when filtering by uppercase.

## Context: What the Previous Fix Did

- `ImportExtractor`: sets `bindingName="ns"` on wildcard imports
- `DeclarationPrepass.buildAliasMap`: adds `ns→ns` (identity) for wildcard bindings
- `extractRelevantIdentifiers`: emits `UsedType("ns")` for bare identifier usages

**Remaining gap**: `extractMemberAccesses` discards `ns.Class` because `ns` is lowercase,
without consulting the aliasMap. `extractConstructorCalls` discards `new ns.Class()` because
the constructor child is a `member_expression`, not a bare `IDENTIFIER`.

Both extractors need to be aliasMap-aware for the single first-level property case (`ns.Class`).

## Tasks

### 1. Fix `extractMemberAccesses`

When the leftmost identifier of a `member_expression` is in the aliasMap (namespace alias),
emit the first-level `property_identifier` as the `UsedType` instead of discarding:

```
types.Logger  →  UsedType("Logger")   (types is in aliasMap)
types.sub.X   →  skip or take first   (deeper chains — don't crash, just ignore)
```

`extractMemberAccesses` currently does not receive `aliasMap` — add it as a parameter and
thread it down from `extract()`.

### 2. Fix `extractConstructorCalls`

When a `new_expression`'s constructor child is a `member_expression` (not a bare identifier),
apply the same aliasMap check: if the object is a namespace alias, emit the property as `UsedType`.

```
new types.Logger()  →  UsedType("Logger")
```

### 3. Update `ns-consumer.ts` demo file

Remove the `const logger: types.Logger = ...` type annotation from the demo so the
`Logger` edge only appears after the fix (not via the independent type-annotation extraction path).
A clean demo:

```typescript
log(): void {
    new types.Logger().log(this.animal?.name ?? '')
}
```

Before fix: `NsConsumer → src.types.Animal` only (Animal from type annotation).
After fix: `NsConsumer → src.types.Animal` AND `src.types.Logger`.

### 4. Regenerate demo JSONs in ts-dc-test

After the fix, regenerate `analysis-before-fix.cg.json` and `analysis-after-fix.cg.json`
to show the actual graph difference. See the dc-compare workflow in MEMORY.md for the
publish → rebuild → run steps.

## Steps

- [x] Task 1: Fix `extractMemberAccesses` — write failing test first (Red)
- [x] Task 1: Implement aliasMap-aware member access extraction (Green)
- [x] Task 2: Fix `extractConstructorCalls` — write failing test first (Red)
- [x] Task 2: Implement aliasMap-aware constructor extraction (Green)
- [x] Task 3: Update `ts-dc-test/src/ns-consumer.ts` demo
- [x] Task 4: Regenerate before/after demo JSONs
- [x] Run `./gradlew ktlintFormat test` — all green

## Notes

- Only first-level property access matters: `ns.Class` → `Class`. Deeper chains (`ns.a.B`)
  are uncommon in TS; skip them (return null) rather than trying to recurse.
- `PROPERTY_IDENTIFIER` is the tree-sitter node type for the right-hand side of a member
  expression in value context. `TYPE_IDENTIFIER` appears in type annotation context — both
  should be checked.
- The existing `UsedType("ns")` emission from `extractRelevantIdentifiers` stays — it's
  harmless (DC can't resolve it to a specific node) and may be useful later.
- DC-side: no resolver changes needed. Once TSE emits `UsedType("Logger")`, DC's existing
  wildcard resolver maps it to `src.types.Logger` via the `import * as types` wildcard dep.
- Coordinate with DC plan `plans/fix-namespace-alias-member-extraction.md` for the
  test and demo JSON steps that belong in DC.
