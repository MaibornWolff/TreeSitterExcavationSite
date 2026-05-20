---
name: TS/JS dependency extraction — completed work summary
state: complete
version: 0.9.0
---

# TS/JS Dependency Extraction — Completed Work

Summary of all fixes applied to JS/TS dependency extraction. Implementation detail is in git
history. See `dc-compare-results-2026-05-20.md` for the full comparison analysis.

---

## Fixes Applied

### 1. TypeScript-specific AST gaps (fix-typescript-dependency-extraction-gaps)

Four tree-sitter node types missing from `UsedTypeExtractor`/`DeclarationExtractor`:

| Gap | Fix |
|---|---|
| `interface Foo extends Bar` — `Bar` missing | Added `extends_type_clause` to `ALL_NODE_TYPES` |
| `class Foo<T extends Bar>` — `Bar` missing | Added `constraint` to `ALL_NODE_TYPES` |
| `type Foo = Bar<Baz>` — `Bar`, `Baz` missing | Type alias RHS: collect `type_identifier` descendants outside the name node |
| `export namespace Foo {}` — no declaration | Added `internal_module` to `DECLARATION_NODE_TYPES` |

**Key AST quirks discovered:**
- `interface` inheritance uses `extends_type_clause`, not `extends_clause` (which is for classes)
- `namespace Foo {}` produces `internal_module`, not `module` — and bare (unexported) namespaces are wrapped in `expression_statement → internal_module`
- Type alias RHS is not a `type_annotation` node — it's a raw type expression

---

### 2. Default import usedType remapping (fix-default-import-usedtype-remapping)

**Problem:** `import Foo from './dep'` caused TSE to emit `DEFAULT_EXPORT` as usedType. DC's resolver
couldn't match `DEFAULT_EXPORT` → the qualified node name `dep_index_DEFAULT_EXPORT`, so the edge
was lost. DC had two workarounds that masked the issue imprecisely.

**Fix:** Added `bindingName: String?` to `ImportDeclaration`. `buildAliasMap()` now identity-maps
default bindings (`Foo → Foo`) instead of `Foo → DEFAULT_EXPORT`. TSE emits the local alias as
usedType; DC's resolver matches it via the import path to the correct node.

**DC changes at yssymqmn:** Removed `selectUsedTypes()` DEFAULT_EXPORT filter and the imprecise
module-name proxy from `extraUsedTypes()`.

**Also fixed:** `collectExportReferencedLocalNames` extended to capture `export default <identifier>`
so declare-then-export-default patterns correctly include the original declaration as a node.

---

### 3. Export-only filtering + lowercase named import tracking (fix-js-declaration-extraction-gaps)

**Export-only filtering:** TSE was extracting all top-level declarations regardless of export
status. Removed the bare `in DECLARATION_NODE_TYPES` and `EXPRESSION_STATEMENT` (namespace
unwrapper) branches from `DeclarationExtractor.extract()`. Exported declarations still flow through
`EXPORT_STATEMENT` and `AMBIENT_DECLARATION` branches. Exception: `extractFromAmbientDeclaration`
retains its `in DECLARATION_NODE_TYPES` branch — inside `declare module "X" {}` all declarations
are implicitly ambient.

**Named import tracking:** `buildAliasMap()` extended to self-map unaliased named imports
(`import { foo }` → `foo → foo`), making aliasMap a complete registry of all locally bound import
names. `extractRelevantIdentifiers()` extended to emit any identifier matching an aliasMap key,
regardless of capitalisation.

**`localDeclarationNames`:** Tracks exported symbol names so cross-references between exported
declarations in the same file are emitted as usedTypes. Own name excluded to prevent
self-references.

---

### 4. Decorator usedTypes + CJS destructured aliases (fix-decorator-and-cjs-alias-extraction)

**Decorators:** `extractFromExportStatement()` now collects `decorator` siblings of the export
statement and merges their usedTypes into each declaration. Decorator identifiers (e.g.
`@Component({ imports: [MyService] })`) are now attributed to the decorated class.

**CJS aliases:** `buildAliasMap()` extended to scan `call_expression[require]` → `object_pattern`
for destructured CJS requires. Shorthand patterns (`{ foo }`) → identity map; pair patterns
(`{ myMethod: alias }`) → `alias → myMethod`.

---

## Key Design Decisions

**TSE only emits identifiers in type-annotation positions** (plus aliasMap-matched identifiers in
value positions). This means identifiers used purely as values — enum access (`ErrorArea.FMT_CLI`),
switch cases (`case TYPES.X:`), bitwise flags (`flags & DidCapture`) — are not captured. This is
intentional: scanning full bodies would replicate DC main's over-attribution (attributing a dep
from one function to a different exported declaration in the same file).

**DC main's whole-file scan** produces systematic over-attribution where any identifier in the file
body gets credited to every exported declaration in that file. Spot-checks confirmed ~43% of "true
regressions" are DC over-attribution (TSE correct); the remaining ~57% are genuine TSE value-position
gaps. Neither is a blocker — see `dc-compare-results-2026-05-20.md`.

**The DC integration is ready.** Remaining differences are documented, understood, and accepted.
