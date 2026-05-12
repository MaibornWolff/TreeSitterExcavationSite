---
name: Fix TypeScript dependency extraction gaps
issue:
state: todo
version:
---

## Goal

Fix three TypeScript dependency extraction regressions found via dc-compare against the Prisma
codebase. All gaps are in `UsedTypeExtractor` or `DeclarationExtractor` inside `languages/javascript/`.
No shared-domain or facade changes required.

## Gaps

| # | Description | Root cause | Effort |
|---|-------------|------------|--------|
| 1 | `interface Foo extends Bar` — `Bar` missing from `usedTypes` | `extends_type_clause` node not in `ALL_NODE_TYPES` | ~3 lines |
| 3a | `class Foo<T extends Bar>` — `Bar` missing from `usedTypes` | `constraint` node not in `ALL_NODE_TYPES` | ~15 lines |
| 3b | `type Foo = Bar<Baz>` — `Bar`, `Baz` missing from `usedTypes` | type alias RHS is not a `type_annotation` node | ~20 lines |
| 2 | `export namespace EngineArgs {}` — no declaration emitted | `module` node type missing from `DECLARATION_NODE_TYPES` | ~20 lines |

## What we're NOT doing

- No recursion into namespace bodies — DC legacy produced only the opaque namespace node itself
  (`type=UNKNOWN`, no members), and TSE will match that.
- No new `DeclarationType.NAMESPACE` — `UNKNOWN` matches DC legacy output.
- No changes to JavaScript (non-TypeScript) dependency extraction.
- No dc-compare round (Prisma comparison already done; these fixes address known regressions).

## Architecture and code reuse

All changes are in two files plus their test class:

- `languages/javascript/extractors/UsedTypeExtractor.kt` — Phases 1 and 2
- `languages/javascript/extractors/DeclarationExtractor.kt` — Phase 3
- `test/.../languages/typescript/TypescriptDependencyTest.kt` — all phases add `@Nested` cases

---

## Phase 1: interface extends clause (Gap 1)

Tree-sitter TypeScript uses `extends_type_clause` for interface inheritance and `extends_clause`
for class inheritance. Only the latter is currently in `ALL_NODE_TYPES`, so interface-extends-interface
relationships are invisible to the dependency graph.

**Tasks**:
- [ ] Add a failing `UsedTypeExtraction` test in `TypescriptDependencyTest`:
  `should extract extends clause type from interface declaration`
  — `interface Foo extends Bar {}` → `Foo.usedTypes` contains `Bar`
- [ ] In `UsedTypeExtractor`, add constant `EXTENDS_TYPE_CLAUSE = "extends_type_clause"`
- [ ] Add `EXTENDS_TYPE_CLAUSE` to `ALL_NODE_TYPES`
- [ ] Include `EXTENDS_TYPE_CLAUSE` in `extractExtensions` alongside `EXTENDS_CLAUSE` and
  `IMPLEMENTS_CLAUSE`

**Automated verification**:
- [ ] `./gradlew test --tests "TypescriptDependencyTest.UsedTypeExtraction"` green
- [ ] `./gradlew ktlintCheck` passes

---

## Phase 2: type alias RHS + generic type constraints (Gap 3)

Two distinct sub-issues, both in `UsedTypeExtractor`, addressed together since they share the same
test group and are both gaps in type-level reference tracking.

### 2a — Generic type constraints

TypeScript allows `class Foo<T extends Bar>`. The `Bar` reference lives inside a `constraint`
node under `type_parameters` → `type_parameter`. This node is not in `ALL_NODE_TYPES`, so
constraint types are silently skipped.

**Tasks**:
- [ ] Add a failing `UsedTypeExtraction` test:
  `should extract type constraint from generic type parameter`
  — `class Foo<T extends Bar> {}` → `Foo.usedTypes` contains `Bar`
- [ ] Add constant `CONSTRAINT = "constraint"` in `UsedTypeExtractor`
- [ ] Add `CONSTRAINT` to `ALL_NODE_TYPES`
- [ ] Add `extractConstraintTypes(buckets, sourceCode)` that finds all `type_identifier`
  descendants within `constraint` nodes, and include its result in `extract()`

### 2b — Type alias right-hand side

For `type Foo = Bar<Baz>`, the RHS is a `generic_type` or `type_identifier` — not a
`type_annotation`. `extractTypeIdentifiers` only searches inside `type_annotation` nodes, so
it finds nothing for type aliases. The fix: for `type_alias_declaration` nodes, additionally
collect all `type_identifier` descendants that are not the declaration's own name.

**Tasks**:
- [ ] Add a failing `UsedTypeExtraction` test:
  `should extract type reference from type alias right-hand side`
  — `type Foo = Bar<Baz>` → `Foo.usedTypes` contains `Bar` and `Baz`
- [ ] Add a second test for a union type alias:
  `should extract multiple types from union type alias`
  — `type Foo = Bar | Baz` → `Foo.usedTypes` contains `Bar` and `Baz`
- [ ] In `UsedTypeExtractor.extract()`, add an internal branch: when the passed `declaration`
  node's type is `TYPE_ALIAS_DECLARATION`, additionally collect all `type_identifier` descendants
  from the non-name subtree — skip the first `type_identifier` direct child (the declaration
  name), then `findAllDescendantsOfType` over the remaining children

**Automated verification**:
- [ ] `./gradlew test --tests "TypescriptDependencyTest.UsedTypeExtraction"` green
- [ ] `./gradlew ktlintCheck` passes

---

## Phase 3: namespace declarations (Gap 2)

`namespace Foo {}` and `export namespace Foo {}` produce `module` AST nodes in tree-sitter
TypeScript. `module` is not in `DECLARATION_NODE_TYPES`, so no declaration is emitted. DC
legacy emitted a single opaque `Declaration(name="Foo", type=UNKNOWN, usedTypes=emptySet())`.

The `module` node type is already handled inside `extractFromAmbientDeclaration` (for
`declare module "string" {}`) but only when the name is a `string` child. Top-level `module`
nodes with an `identifier` name are unrelated and not affected by adding `module` to
`DECLARATION_NODE_TYPES`.

**Tasks**:
- [ ] Add a failing `DeclarationExtraction` test:
  `should extract namespace declaration as UNKNOWN type`
  — `export namespace EngineArgs { export type Foo = string }` → one declaration with
  `name="EngineArgs"`, `type=UNKNOWN`, `usedTypes=emptySet()`
- [ ] Add a second test for namespace without export:
  `should extract namespace declaration without export keyword`
  — `namespace Foo {}` → `Declaration(name="Foo", type=UNKNOWN)`
- [ ] Verify `MODULE_DECLARATION = "module"` already exists at line 32 of `DeclarationExtractor`
  — reuse it, no new constant needed
- [ ] Add `MODULE_DECLARATION` to `DECLARATION_NODE_TYPES`
- [ ] Confirm `extractName` for `module` nodes: the `else` branch uses `arrayOf(IDENTIFIER)`,
  and the namespace name is a direct `identifier` child of the `module` node (verified via
  `findFirstChildTextByType` which scans direct children) ✓
- [ ] Confirm `declarationType("module")`: the `else` branch returns `UNKNOWN` ✓

**Automated verification**:
- [ ] `./gradlew test --tests "TypescriptDependencyTest.DeclarationExtraction"` green
- [ ] `./gradlew test` (full suite) green
- [ ] `./gradlew ktlintCheck` passes

---

## Notes

- **Phase ordering**: Each phase is independent. Run `ktlintFormat` after each phase.
- **Gap 3b implementation detail**: the type alias RHS logic lives entirely inside
  `UsedTypeExtractor.extract()` (not split to `DeclarationExtractor`), keeping the existing
  pattern where `DeclarationExtractor` calls only `UsedTypeExtractor.extract()` as its
  single extraction entry point. Within `extract()`, detect `declaration.type == TYPE_ALIAS_DECLARATION`,
  skip the first `type_identifier` direct child (the name), and collect all `type_identifier`
  descendants from the remaining children.
- **`MODULE_DECLARATION` already exists** at line 32 of `DeclarationExtractor.kt` — no new
  constant needed.
- **No new `DeclarationType` value** needed — `UNKNOWN` matches the DC legacy output for
  namespace nodes, so the DC adapter requires no changes.