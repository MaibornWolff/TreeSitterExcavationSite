---
name: TSX Language Definition with JSX Support
issue:
state: todo
version:
---

## Goal

Create a dedicated `TsxDefinition` with its own metric and extraction mappings that fully covers JSX-specific node types. The parser stays as `TreeSitterTypescript()` — it already IS the TSX variant (without `.typescript`).

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
- Replace `Language.TSX -> TypescriptDefinition` with `Language.TSX -> TsxDefinition` (line 78), remove `// placeholder until TsxDefinition is created`
- Replace the existing TODO block (lines 52–54) with:
```kotlin
// TreeSitterTypescript() without .typescript IS the TSX parser — no TreeSitterTsx() needed.
// TODO: Fix Language.TYPESCRIPT to use TreeSitterTypescript().typescript once the property works.
//       Until then, both TYPESCRIPT and TSX use the TSX grammar, which is a superset of TS.
```

## Steps

- [x] `./gradlew test --tests "*Tsx*"` — baseline
- [x] Create `TsxMetricMapping.kt`, `TsxExtractionMapping.kt` (ohne JSX-Nodes), `TsxDefinition.kt`
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

TDD-Zyklus Metrics: Grundfunktionalität
- [x] Write JSX-specific test: `should parse JSX element without corrupting function count` → run (red)
- [x] Copy all tests from `TypescriptMetricsTest`, change `Language.TYPESCRIPT` → `Language.TSX`
- [x] `./gradlew test --tests "*TsxMetricsTest*"` — green

TDD Cycle 1: `jsx_opening_element`
- [ ] Write test: `should extract component name from jsx opening element` → run (red)
- [ ] Add `jsx_opening_element` mapping to `TsxExtractionMapping.kt`
- [ ] `./gradlew test --tests "*Tsx*"` — green

TDD Cycle 2: `jsx_self_closing_element`
- [ ] Write test: `should extract component name from jsx self-closing element` → run (red)
- [ ] Add `jsx_self_closing_element` mapping
- [ ] `./gradlew test --tests "*Tsx*"` — green

TDD Cycle 3: `jsx_attribute`
- [ ] Write test: `should extract attribute name from jsx attribute` → run (red)
- [ ] Add `jsx_attribute` mapping
- [ ] `./gradlew test --tests "*Tsx*"` — green

TDD Cycle 4: destructured arrow function params
- [ ] Write test: `should extract parameter names from typed props` → run (red)
- [ ] Implement support for `({ name, age }: Props) =>` pattern
- [ ] `./gradlew test --tests "*Tsx*"` — green

- [ ] `./gradlew test` — full suite green (final check)

### 6. Update sample file and regenerate golden files
- [ ] Add `jsx_opening_element` example to `tsx_sample.tsx` (e.g. `<MyComponent>...</MyComponent>`)
- [ ] Add `jsx_self_closing_element` example to `tsx_sample.tsx` (e.g. `<Icon />`)
- [ ] Add `jsx_attribute` example to `tsx_sample.tsx` (e.g. `onClick={handler}`)
- [ ] Regenerate golden files (`UPDATE_GOLDEN_FILES=true`, run, set back to `false`)
- [ ] `./gradlew test` — full suite green

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

- `TreeSitterTypescript()` without `.typescript` IS the TSX parser. `TreeSitterTsx()` will never be needed.
- The old TODO ("Replace with TreeSitterTsx()") was incorrect — replaced with a precise comment (see Task 4)
- Member-expression components like `<React.Fragment>` are not extracted for now (no custom extractor needed for basic support)
- `property_identifier` instead of `identifier` for JSX attribute names (tree-sitter distinguishes these)