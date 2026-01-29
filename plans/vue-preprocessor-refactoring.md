---
name: vue-preprocessor-refactoring
issue: N/A
state: complete
version: 1
---

## Goal

Refactor Vue support to use a preprocessor pattern instead of custom processors that violate the hexagonal architecture by depending on the integration layer.

## Tasks

### 1. Add preprocessor to LanguageDefinition
- Add `val preprocessor: ((String) -> String)? get() = null` to `LanguageDefinition` interface
- This allows content transformation before parsing

### 2. Update facades to apply preprocessor
- Modify `MetricsFacade.collectMetrics()` to apply `definition.preprocessor` to content if present
- Modify `ExtractionFacade.extract()` to apply `definition.preprocessor` to content if present

### 3. Refactor VueDefinition to use preprocessor
- Set `preprocessor = VueScriptExtractor::extractScriptContent`
- Reuse `JavascriptDefinition.nodeMetrics` and `JavascriptDefinition.nodeExtractions`
- Reuse `JavascriptDefinition.calculationConfig`

### 4. Update LanguageRegistry for Vue
- Return `TreeSitterJavascript()` for Vue (since we're parsing extracted JS)
- Remove `getMetricsProcessor()` and `getExtractionProcessor()` methods

### 5. Delete obsolete files
- Delete `VueMetricsProcessor.kt`
- Delete `VueExtractionProcessor.kt`
- Delete `VueMetricMapping.kt`
- Delete `VueExtractionMapping.kt`

### 6. Update API layer
- Remove custom processor handling from `TreeSitterMetrics.parse()`
- Remove custom processor handling from `TreeSitterExtraction.extract()`

### 7. Update tests
- Ensure existing Vue tests pass
- Verify LOC now reports script lines only (accepted behavior change)

## Steps

- [x] Complete Task 1: Add preprocessor to LanguageDefinition
- [x] Complete Task 2: Update facades to apply preprocessor
- [x] Complete Task 3: Refactor VueDefinition
- [x] Complete Task 4: Update LanguageRegistry
- [x] Complete Task 5: Delete obsolete files
- [x] Complete Task 6: Update API layer
- [x] Complete Task 7: Run tests and verify

## Notes

- LOC will now report script section lines only (previously reported full Vue file lines)
- This removes the architectural violation where `languages/` depended on `integration/`
- The preprocessor pattern is extensible for future composite file formats
