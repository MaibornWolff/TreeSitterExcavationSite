---
name: fix-decorator-and-cjs-alias-extraction
issue: n/a
state: todo
version: 0.10.0
---

## Goal

Fix two gaps in TSE's JS/TS used-type extraction so that DC's `TypescriptAnalyzer` does not need a broad-net `extraUsedTypes()` override:

1. **Decorator used types**: identifiers inside TypeScript decorators (e.g., `@Component({ imports: [MyService] })`) are not attributed to the decorated class's `usedTypes`, causing missing dependency edges for Angular-style components.
2. **CJS destructured alias mapping**: `const { myMethod: alias } = require('module')` — `alias` is used in the class body but is not mapped back to `myMethod` in the alias map, so the `usedType` entry is missing.

## Tasks

### 1. Decorator used-type extraction

When tree-sitter parses `@Component({...}) export class MyClass {}`, the `decorator` node is a child of `export_statement` — a sibling of `class_declaration`, not a descendant. So `UsedTypeExtractor.extract(classNode, ...)` misses identifiers in the decorator.

Fix in `DeclarationExtractor.extractFromExportStatement()`: collect any `decorator` children of the `export_statement`, run `UsedTypeExtractor.extract()` on each decorator node, and merge the results into each declaration's `usedTypes`.

- Node type constant to add: `private const val DECORATOR = "decorator"`
- Where to merge: in the `else` branch of `extractFromExportStatement`, before/after calling `extractFromNode`
- Identifiers extracted from decorators follow the same alias-map and PascalCase rules as the rest of `UsedTypeExtractor`

### 2. CJS alias map extension

`DeclarationPrepass.buildAliasMap()` currently only processes ES6 `import_statement` nodes. CJS destructured requires are not handled, so `alias` in `const { myMethod: alias } = require('module')` is never mapped to `myMethod`.

Fix in `buildAliasMap()`: after processing ES6 imports, also scan `call_expression` nodes where the callee is `require`, find the parent `variable_declarator` with an `object_pattern`, and add alias map entries:
- `shorthand_property_identifier_pattern` → identity mapping `name → name`
- `pair_pattern` → `binding (right) → import-name (left)`

This mirrors the logic already present in `ImportExtractor.extractDestructuredCommonJs()`.

### 3. Verify and clean up in DC

Once both TSE fixes are in place:
- Run DC regression tests (`TypescriptAnalyzerTest`) — the decorator and CJS alias tests should pass without any `extraUsedTypes()` override in `TypescriptAnalyzer`
- If a workaround `extraUsedTypes()` was added to `TypescriptAnalyzer` (wuwzyqrs state), remove it — the base class default (`emptySet()`) is correct
- Run dc-compare on Prisma (TS) and React (JS) to verify no new regressions

## Steps

- [ ] Task 1a: Write failing TSE test — decorator identifiers in `usedTypes` for a decorated exported class
- [ ] Task 1b: Fix `DeclarationExtractor.extractFromExportStatement()` to scan `decorator` siblings and merge usedTypes
- [ ] Task 1c: Run tests — green
- [ ] Task 2a: Write failing TSE test — CJS alias (`alias → myMethod`) resolved in `usedTypes`
- [ ] Task 2b: Extend `DeclarationPrepass.buildAliasMap()` to handle CJS require with destructuring
- [ ] Task 2c: Run tests — green
- [ ] Task 3a: Run DC `TypescriptAnalyzerTest` — decorator and CJS alias tests pass
- [ ] Task 3b: Remove any `extraUsedTypes()` override from DC `TypescriptAnalyzer` if present
- [ ] Task 3c: Run dc-compare on Prisma + React — no new regressions

## Notes

- **Decorator scope**: only scan `decorator` nodes that are direct children of `export_statement`. Top-level decorator siblings at program level are a different AST pattern and should be addressed separately if needed.
- **CJS vs ES6**: the CJS alias fix is purely in `buildAliasMap()` (prepass). `ImportExtractor` already emits the correct `ImportDeclaration.path` with the original import name; this fix only affects which identifiers are tracked through the alias map during declaration scanning.
- **DC test fixture for decorator**: `@Component({ imports: [MyComponentImport] }) export class MyClass {}` — expects `MyComponentImport` in `usedTypes`. The decorator call expression name (`Component`) will also be emitted as a usedType (PascalCase identifier) — this is correct behavior.
- **`extraUsedTypes()` in DC**: the base class default already returns `emptySet()`. If TypescriptAnalyzer overrides it, remove the override; if not, no change needed.
- **No version bump until dc-compare validated**: bump to 0.10.0 after confirming dc-compare numbers are acceptable.