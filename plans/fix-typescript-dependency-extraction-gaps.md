---
name: Fix TypeScript dependency extraction gaps
issue:
state: complete
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
| 2 | `export namespace EngineArgs {}` — no declaration emitted | `internal_module` node type missing from `DECLARATION_NODE_TYPES` | ~20 lines |

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
- [x] Add a failing `UsedTypeExtraction` test in `TypescriptDependencyTest`:
  `should extract extends clause type from interface declaration`
  — `interface Foo extends Bar {}` → `Foo.usedTypes` contains `Bar`
- [x] In `UsedTypeExtractor`, add constant `EXTENDS_TYPE_CLAUSE = "extends_type_clause"`
- [x] Add `EXTENDS_TYPE_CLAUSE` to `ALL_NODE_TYPES`
- [x] Include `EXTENDS_TYPE_CLAUSE` in `extractExtensions` alongside `EXTENDS_CLAUSE` and
  `IMPLEMENTS_CLAUSE`

**Automated verification**:
- [x] `./gradlew test --tests "TypescriptDependencyTest.UsedTypeExtraction"` green
- [x] `./gradlew ktlintCheck` passes

---

## Phase 2: type alias RHS + generic type constraints (Gap 3)

Two distinct sub-issues, both in `UsedTypeExtractor`, addressed together since they share the same
test group and are both gaps in type-level reference tracking.

### 2a — Generic type constraints

TypeScript allows `class Foo<T extends Bar>`. The `Bar` reference lives inside a `constraint`
node under `type_parameters` → `type_parameter`. This node is not in `ALL_NODE_TYPES`, so
constraint types are silently skipped.

**Tasks**:
- [x] Add a failing `UsedTypeExtraction` test:
  `should extract type constraint from generic type parameter`
  — `class Foo<T extends Bar> {}` → `Foo.usedTypes` contains `Bar`
- [x] Add constant `CONSTRAINT = "constraint"` in `UsedTypeExtractor`
- [x] Add `CONSTRAINT` to `ALL_NODE_TYPES`
- [x] Add `extractConstraintTypes(buckets, sourceCode)` that finds all `type_identifier`
  descendants within `constraint` nodes, and include its result in `extract()`

### 2b — Type alias right-hand side

For `type Foo = Bar<Baz>`, the RHS is a `generic_type` or `type_identifier` — not a
`type_annotation`. `extractTypeIdentifiers` only searches inside `type_annotation` nodes, so
it finds nothing for type aliases. The fix: for `type_alias_declaration` nodes, additionally
collect all `type_identifier` descendants that are not the declaration's own name.

**Tasks**:
- [x] Add a failing `UsedTypeExtraction` test:
  `should extract type reference from type alias right-hand side`
  — `type Foo = Bar<Baz>` → `Foo.usedTypes` contains `Bar` and `Baz`
- [x] Add a second test for a union type alias:
  `should extract multiple types from union type alias`
  — `type Foo = Bar | Baz` → `Foo.usedTypes` contains `Bar` and `Baz`
- [x] In `UsedTypeExtractor.extract()`, add an internal branch: when the passed `declaration`
  node's type is `TYPE_ALIAS_DECLARATION`, additionally collect all `type_identifier` descendants
  from the non-name subtree — skip the first `type_identifier` direct child (the declaration
  name), then `findAllDescendantsOfType` over the remaining children

**Automated verification**:
- [x] `./gradlew test --tests "TypescriptDependencyTest.UsedTypeExtraction"` green
- [x] `./gradlew ktlintCheck` passes

---

## Phase 3: namespace declarations (Gap 2)

**Tasks**:
- [x] Add a failing `NamespaceDeclaration` test:
  `should extract namespace declaration as UNKNOWN type`
  — `export namespace EngineArgs { export type Foo = string }` → one declaration with
  `name="EngineArgs"`, `type=UNKNOWN`, `usedTypes=emptySet()`
- [x] Add a second test for namespace without export:
  `should extract namespace declaration without export keyword`
  — `namespace Foo {}` → `Declaration(name="Foo", type=UNKNOWN)`
- [x] Add constant `INTERNAL_MODULE = "internal_module"` and `EXPRESSION_STATEMENT = "expression_statement"`
  to `DeclarationExtractor` (the plan assumed `"module"`; actual tree-sitter node type is
  `"internal_module"`, confirmed via AST dump)
- [x] Add `INTERNAL_MODULE` to `DECLARATION_NODE_TYPES`
- [x] Add `EXPRESSION_STATEMENT` case in top-level dispatch to unwrap bare `namespace Foo {}`
  (which tree-sitter wraps in `expression_statement → internal_module`)
- [x] Add `INTERNAL_MODULE` branch in `extractFromNode` returning `Declaration(name, UNKNOWN, emptySet())`
  — without calling `UsedTypeExtractor` (which would pick up the uppercase namespace name
  as a self-referencing usedType via `extractRelevantIdentifiers`)

**Automated verification**:
- [x] `./gradlew test --tests "TypescriptDependencyTest.NamespaceDeclaration"` green
- [x] `./gradlew test` (full suite) green — 2904 tests, 0 failed
- [x] `./gradlew ktlintCheck` passes

---

## Notes

- **Gap 3b implementation detail**: the type alias RHS logic lives entirely inside
  `UsedTypeExtractor.extract()` (not split to `DeclarationExtractor`), keeping the existing
  pattern where `DeclarationExtractor` calls only `UsedTypeExtractor.extract()` as its
  single extraction entry point. Within `extract()`, detect `declaration.type == TYPE_ALIAS_DECLARATION`,
  skip the first `type_identifier` direct child (the name), and collect all `type_identifier`
  descendants from the remaining children.
- **AST discovery for Phase 3**: the plan assumed `"module"` as the node type for namespace
  declarations. Actual tree-sitter-typescript node type is `"internal_module"`. Bare
  `namespace Foo {}` is additionally wrapped in `expression_statement` at top level (unlike
  `export namespace`, which sits directly inside `export_statement`). Confirmed via a temporary
  `AstDumpTest` before implementing.
- **Namespace usedTypes must be empty**: calling `UsedTypeExtractor.extract()` on an
  `internal_module` node would pick up the namespace name itself (uppercase identifier) as a
  usedType. The `INTERNAL_MODULE` branch in `extractFromNode` short-circuits with `emptySet()`
  to match DC legacy output.
- **No new `DeclarationType` value** needed — `UNKNOWN` matches the DC legacy output for
  namespace nodes, so the DC adapter requires no changes.
