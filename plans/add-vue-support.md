---
name: Add Vue Support (Script Metrics)
issue: N/A
state: complete
version: 1.0
---

## Goal

Add Vue Single File Component (.vue) support with metrics from the `<script>` section only. This is Phase 1; template directive complexity will be added in a follow-up plan.

## Tasks

### 1. Add tree-sitter-vue dependency
- Add version `0.2.1a` to `gradle/libs.versions.toml`
- Add library reference and implementation dependency in `build.gradle.kts`

### 2. Add Vue to Language enum
- Add `VUE` entry in `shared/domain/Language.kt` with `.vue` extension

### 3. Create Vue language definition files
- Create `languages/vue/` directory with:
  - `VueMetricMapping.kt` - Map Vue AST nodes to metrics (script section)
  - `VueExtractionMapping.kt` - Map nodes to extraction types
  - `VueDefinition.kt` - Combine mappings

### 4. Register Vue in LanguageRegistry
- Import `TreeSitterVue` parser
- Import `VueDefinition`
- Add Vue cases to both `getTreeSitterLanguage()` and `getLanguageDefinition()`

### 5. Explore Vue AST structure
- Parse sample Vue code and examine the AST
- Identify node types for script section: functions, control flow, comments

### 6. Implement Vue metric mappings
- Map JavaScript-like nodes in `<script>` sections (functions, if/for/while, ternary, etc.)
- Map comment nodes for comment line counting

### 7. Write tests
- Create `VueMetricsTest.kt` with tests for complexity, LOC, comment lines, function counts
- Create `VueExtractionTest.kt` for identifier/comment/string extraction

## Steps

- [x] Add tree-sitter-vue dependency to version catalog and build file
- [x] Add VUE to Language enum
- [x] Create Vue language directory and definition files
- [x] Register Vue in LanguageRegistry
- [x] Explore Vue AST structure with test code
- [x] Implement metric mappings based on AST analysis
- [x] Write metrics tests
- [x] Run all tests and ensure they pass

## Notes

- Vue files have three sections: `<template>`, `<script>`, `<style>`
- This plan focuses only on `<script>` section metrics
- Template directive complexity (v-if, v-for) will be added in Phase 2
- tree-sitter-vue version 0.2.1a is available from io.github.bonede
