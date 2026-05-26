---
name: fix-type-assertion-usedtype-extraction
state: complete
version: 0.10.0
---

## Goal

Capture the type operand of TypeScript `as`-expressions (`x as Response`) and
`satisfies`-expressions (`settings satisfies Config`) as `usedType` entries.
Both are currently invisible to `UsedTypeExtractor` because the extractor only
looks inside `type_annotation` nodes — not inside these value-position type
references.

## Tasks

### 1. Add failing tests

Add a `@Nested inner class TypeAssertionAndSatisfies` to
`TypescriptDependencyTest.kt` with two tests:

- `should emit type in as-expression as usedType` — imports `Response`, exports
  a class that does `x as Response` inside a method body, asserts `Response` is
  in the class's `usedTypes`.
- `should emit type in satisfies-expression as usedType` — imports `Config`,
  exports a `const settings = { ... } satisfies Config`, asserts `Config` is in
  `settings`'s `usedTypes`.

Run the tests and confirm they fail with "does not contain 'Response'" /
"does not contain 'Config'" — not a compile error.

### 2. Implement extractTypeAssertionTypes in UsedTypeExtractor

In `languages/javascript/extractors/UsedTypeExtractor.kt`:

1. Add constants:
   ```kotlin
   private const val AS_EXPRESSION = "as_expression"
   private const val SATISFIES_EXPRESSION = "satisfies_expression"
   ```
2. Add both to `ALL_NODE_TYPES`.
3. Add a private extraction function (see Learn-by-Doing request in plan).
4. Include the result in the `extract()` return concatenation.

**AST structure note:** In tree-sitter-typescript, `x as Response` produces:
```
as_expression
  identifier: "x"
  type_identifier: "Response"
```
The expression side uses `identifier`; the type side uses `type_identifier`.
`findAllDescendantsOfType(node, TYPE_IDENTIFIER)` on the whole `as_expression`
therefore only captures the type — no false positives from the expression side.
Same structure applies to `satisfies_expression`.

### 3. Run full test suite and commit

Run `./gradlew test --tests "*TypescriptDependencyTest"`. All tests must be
green. Then commit with message:
`fix(js,ts): capture as-expression and satisfies-expression types as usedTypes`

## Steps

- [ ] Task 1: Write failing test for `as_expression`
- [ ] Task 1: Run test — confirm failure message is "does not contain 'Response'"
- [ ] Task 1: Write failing test for `satisfies_expression`
- [ ] Task 1: Run test — confirm failure message is "does not contain 'Config'"
- [ ] Task 2: Add constants `AS_EXPRESSION`, `SATISFIES_EXPRESSION` and add to `ALL_NODE_TYPES`
- [ ] Task 2: Request human contribution for `extractTypeAssertionTypes` implementation
- [ ] Task 2: Wire result into `extract()` concatenation
- [ ] Task 3: Run `./gradlew test --tests "*TypescriptDependencyTest"` — all green
- [ ] Task 3: Commit

## dc-compare Results (2026-05-26, TSE 0.10.0-local)

| Metric | TS (Prisma) | JS (React) | vs previous |
|--------|-------------|------------|-------------|
| only-in-main | 7 | 96 | unchanged |
| only-in-feat | 265 | 4,448 | unchanged |
| Missing edges | 11,871 | 25,923 | −67 TS / −27 JS ✅ |
| Extra edges | 672 | 8,832 | unchanged / +1 JS |
| NodeType mismatches | 23 | 20 | unchanged |

The fix closed 67 TS and 27 JS missing edges (type references in `as`/`satisfies` positions now captured).
No new extra-edge noise: the expression side of `as_expression` uses `identifier` nodes, not `type_identifier`,
so `findAllDescendantsOfType` never touches the value being cast.

## Notes

- `as_expression` and `satisfies_expression` are TypeScript-only; JS has no
  such syntax. The extractor is shared (`javascript/extractors/UsedTypeExtractor.kt`)
  but only TS produces these nodes, so adding the node types is harmless for JS.
- The extraction pattern is identical to `extractConstraintTypes` — collect all
  `type_identifier` descendants from the assertion node.
- No boundary exclusion needed: `as`-expressions appear inside method bodies,
  and by the time the traversal reaches them we're already scoped to a single
  declaration node.
- DC main does not capture these either (whole-file scan using `identifier`
  nodes, not `type_identifier`) so this is a net improvement over DC, not a
  regression gap.

---

## Further Improvement Candidates

### 1. Generic type arguments on function calls ✅ Worth doing

**What:** `createService<UserService>()` — `UserService` is a `type_identifier`
inside a `type_arguments` node on a `call_expression`. Not inside any
`type_annotation`, so `extractTypeIdentifiers` never sees it.

**Feasibility:** High — confirmed node type is `type_arguments` (same name used
in Kotlin's `UsedTypeExtractor`). Fix is identical to the `as`/`satisfies`
pattern: add `type_arguments` to `ALL_NODE_TYPES` and collect `type_identifier`
descendants. The `.toSet()` in `extract()` handles any duplicates with
`type_annotation`-already-captured entries.

**Usefulness:** Medium — generic function calls with imported type arguments are
common in TypeScript codebases. Estimate: tens to low-hundreds of missing edges
closed per repo. No noise risk (type_arguments only contains type-position nodes).

---

### 2. `instanceof` checks — Already handled ✓

`x instanceof SomeClass` — `SomeClass` is an `identifier` with uppercase first
char. `extractRelevantIdentifiers` captures it via the uppercase check. No action
needed.

---

### 3. Angle-bracket type assertion (`<Response>x`) — Not worth fixing

**Feasibility:** Trivial (add `type_assertion` to `ALL_NODE_TYPES`).

**Usefulness:** Near zero. This syntax is deprecated in favour of `as`-expressions
and cannot be used in `.tsx` files at all (conflicts with JSX). Practically
absent from modern codebases.

---

### 4. Body-scan gap (~11k TS / ~25k JS missing edges) — Complex, deferred

**What:** DC main scans all identifiers in every function body and attributes
them to the enclosing exported declaration. TSE only captures type-annotation-
position references. The gap is the bulk of the remaining missing edges.

**Feasibility:** Low for a correct fix. Scanning function bodies is easy, but
scoping each body's identifiers to only the correct declaration is hard:
- A file with `class Foo` and `class Bar` both containing method bodies would
  attribute every body identifier to both Foo and Bar (DC's over-attribution bug).
- Correctly scoping requires knowing which declaration owns which method body,
  which in turn requires understanding class structure, module-level functions,
  arrow functions assigned to variables, etc.

**Usefulness:** Would close ~93% of remaining missing edges if done correctly.

**Verdict:** Accept the gap for now. Revisit only if DC integration requires
parity. Document as a known, deliberate trade-off — not an oversight.

---

### 5. `only-in-feat` nodes from ambient module declarations — Not a TSE issue

The ~600–700 extra nodes for JS that look like npm package names (e.g.
`notistack.VariantOverrides`, `hermes-parser.HermesParserOptions`) are NOT from
`node_modules/` files. DC already excludes `node_modules` via
`ignoredDirectories()` in `IgnoredDirectories.kt` — both main and feature use
the same walker.

These come from TypeScript ambient module declarations in project source files:
```typescript
declare module 'notistack' {
  interface VariantOverrides { level: MessageLevel }
}
```
TSE's `extractFromAmbientDeclaration` correctly extracts these; DC main's legacy
TSQuery patterns miss them. This is a TSE improvement, not leakage. No fix needed.
