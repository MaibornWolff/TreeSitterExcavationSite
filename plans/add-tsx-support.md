---
name: TSX Language Definition with JSX Support
issue:
state: progress
version:
---

## Goal

Create a dedicated `TsxDefinition` with its own metric and extraction mappings that fully covers JSX-specific node types. Uses `TreeSitterTsx()` from `libs/tree-sitter-tsx-0.23.2.jar`.

## Tasks

### 1. Check existing tests (baseline)
- Run `./gradlew test --tests "*Tsx*"` to record which tests currently pass/fail

### 2. Write new JSX tests (TDD: Red)
Add to `TsxExtractionTest.kt`:
- `should extract component name from jsx opening element` — `<MyComponent>...</MyComponent>` → "MyComponent"
- `should extract component name from jsx self-closing element` — `<Icon />` → "Icon"
- `should extract attribute name from jsx attribute` — `onClick={fn}` → "onClick"

### 3. Create `languages/tsx/` language definition (TDD: Green)

**`TsxMetricMapping.kt`** — copy of `TypescriptMetricMapping` (same node types, no JSX-specific metrics needed)

**`TsxExtractionMapping.kt`** — based on `JavascriptExtractionMapping` + JSX additions:
```
jsx_opening_element      → Identifier, FirstChildByType("identifier")
jsx_self_closing_element → Identifier, FirstChildByType("identifier")
jsx_attribute            → Identifier, FirstChildByType("property_identifier")
```
`jsx_text` is intentionally not mapped (already tested: must not be extracted as string)

**`TsxDefinition.kt`** — combines TsxMetricMapping + TsxExtractionMapping

### 4. Update `LanguageRegistry.kt`
- Replace `Language.TSX -> TypescriptDefinition` with `Language.TSX -> TsxDefinition`
- Replace `Language.TSX -> TreeSitterTypescript()` with `Language.TSX -> TreeSitterTsx()`

## Steps

- [x] `./gradlew test --tests "*Tsx*"` — baseline
- [x] Create `TsxMetricMapping.kt`, `TsxExtractionMapping.kt` (without JSX nodes), `TsxDefinition.kt`
- [x] Update `LanguageRegistry.kt`

### 5. Fix failing contract/API tests (caused by LanguageRegistry change)

**`ApiSignatureContractTest`**
- [x] `should have exactly 16 language values` → change `hasSize(16)` to `hasSize(17)`

**`TreeSitterExtractionTest`**
- [x] `should return supported languages` → add `Language.TSX` to list, change `hasSize(16)` to `hasSize(17)`

**`TreeSitterMetricsTest`**
- [x] `should detect language from file extension` → change `.tsx` expectation from `Language.TYPESCRIPT` to `Language.TSX`

**`LanguageSupportContractTest`**
- [x] `should map tsx extension to TYPESCRIPT` → renamed + changed expected value to `Language.TSX`

**`GoldenFileContractTest`** (needs sample file)
- [x] Add `Language.TSX to "tsx_sample.tsx"` to `SAMPLE_FILE_NAMES` map
- [x] Add `Language.TSX to "tsx_sample"` to `GOLDEN_BASE_NAMES` map
- [x] Create `src/test/resources/contract/tsx_sample.tsx`
- [x] Golden files auto-created: `tsx_sample_metrics.golden`, `tsx_sample_extraction.golden`
- [x] Golden file content reviewed and correct (`number_of_functions=4` after fix)
- [x] Fix: `extractNonImportString` skips nodes whose text doesn't start with `"` or `'` — prevents TypeScript predefined_type keywords (e.g. `string`) from being extracted as string literals

**`Assertion Style Rules`**
- [x] `TsxExtractionTest`: replaced `.contains()` with `.containsExactlyInAnyOrder()`

- [x] `./gradlew test` — full suite green

TDD Cycle Metrics: basic functionality
- [x] Write JSX-specific test: `should parse JSX element without corrupting function count` → run (red)
- [x] Copy all tests from `TypescriptMetricsTest`, change `Language.TYPESCRIPT` → `Language.TSX`
- [x] `./gradlew test --tests "*TsxMetricsTest*"` — green

TDD-Cycle 1: `jsx_opening_element`
- [x] Write test: `should extract component name from jsx opening element` → run (red)
- [x] Add `jsx_opening_element` mapping to `TsxExtractionMapping.kt`
- [x] `./gradlew test --tests "*Tsx*"` — green

TDD-Cycle 2: `jsx_self_closing_element`
- [x] Write test: `should extract component name from jsx self-closing element` → run (red)
- [x] Add `jsx_self_closing_element` mapping
- [x] `./gradlew test --tests "*Tsx*"` — green

TDD-Cycle 3: `jsx_attribute`
- [x] Write test: `should extract attribute name from jsx attribute` → run (red)
- [x] Add `jsx_attribute` mapping
- [x] `./gradlew test --tests "*Tsx*"` — green

TDD-Cycle 4: destructured params with type annotation
- [x] Write test: `should extract parameter names from typed props` → run (red)
- [x] Implement support for `({ name, age }: Props) =>` pattern in `FormalParametersExtractor.kt`
- [x] `./gradlew test --tests "*Tsx*"` — green
- **Note**: This is a TypeScript feature (`required_parameter` wrapping `object_pattern`), not JSX-specific.
  The fix in `FormalParametersExtractor.kt` benefits both TS and TSX. Test also added to `TypescriptExtractionTest.kt` (`DestructuringTests`).

- [x] `./gradlew test` — full suite green (final check)

### 6. Update sample file and regenerate golden files
- [x] Add `jsx_opening_element` example to `tsx_sample.tsx` (e.g. `<MyComponent>...</MyComponent>`)
- [x] Add `jsx_self_closing_element` example to `tsx_sample.tsx` (e.g. `<Icon />`)
- [x] Add `jsx_attribute` example to `tsx_sample.tsx` (e.g. `onClick={handler}`)
- [x] Regenerate golden files (`UPDATE_GOLDEN_FILES=true`, run, set back to `false`)
- [x] `./gradlew test` — full suite green

## JSX Node Types (from tsx/src/node-types.json)

| Node Type | Relevant fields | Action |
|-----------|----------------|--------|
| `jsx_element` | `open_tag`, `close_tag`, children | no mapping needed (covered via open_tag) |
| `jsx_opening_element` | `name`: identifier / member_expression | Extract.Identifier → `FirstChildByType("identifier")` |
| `jsx_self_closing_element` | `name`: identifier / member_expression | Extract.Identifier → `FirstChildByType("identifier")` |
| `jsx_closing_element` | `name` | no mapping (redundant with opening_element) |
| `jsx_attribute` | `property_identifier`, value | Extract.Identifier → `FirstChildByType("property_identifier")` |
| `jsx_expression` | expression | no mapping (contents handled by normal JS extraction) |
| `jsx_text` | — | explicitly not mapped (must not be extracted as string) |
| `jsx_namespace_name` | identifier | no mapping (edge case) |

## Key Files

| File | Action |
|------|--------|
| `languages/javascript/TypescriptMetricMapping.kt` | Template for TsxMetricMapping |
| `languages/javascript/JavascriptExtractionMapping.kt` | Template for TsxExtractionMapping |
| `languages/javascript/TypescriptDefinition.kt` | Template for TsxDefinition |
| `languages/LanguageRegistry.kt` | Update TSX definition mapping and TODO comment |
| `src/test/kotlin/.../languages/tsx/TsxExtractionTest.kt` | Add new JSX tests |

## Notes

- `TreeSitterTypescript()` is the **TypeScript parser only** — NOT TSX. `<MyComponent>` would be parsed as TypeScript generics. `TreeSitterTsx()` is required for JSX nodes.
- `TreeSitterTsx` comes from `libs/tree-sitter-tsx-0.23.2.jar` (checked into the repo directly).
  - Built from `bonede/tree-sitter-ng`: `git clone https://github.com/bonede/tree-sitter-ng && ./gradlew :tree-sitter-tsx:publishToMavenLocal`
  - `TreeSitterTsx.class` was recompiled with Java 17 (original was Java 21), native DLLs unchanged.
  - Not on Maven Central; JitPack fails (bonede requires Java 11+, JitPack uses Java 8).
- JSX extraction extracts **all** element names including HTML primitives (`button`, `div`, `span`) — correct behaviour with the real TSX parser.
- Bare JSX (`<MyComponent>`) without context is parsed as TypeScript generics (ERROR node) — JSX always needs an expression context (assignment, return, etc.).
- Member-expression components like `<React.Fragment>` are not extracted (no custom extractor needed for basic support).
- `property_identifier` instead of `identifier` for JSX attribute names (tree-sitter distinguishes these).