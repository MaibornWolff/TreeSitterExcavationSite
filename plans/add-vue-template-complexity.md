---
name: Add Vue Template Complexity
issue: N/A
state: todo
version: 1.0
---

## Goal

Add Vue template directive complexity metrics (v-if, v-for, v-else) to the existing Vue language support. This is Phase 2, building on the script-only metrics from Phase 1.

## Prerequisites

- Phase 1 (add-vue-support.md) must be completed first

## Tasks

### 1. Explore Vue template AST structure
- Examine how tree-sitter-vue parses `<template>` sections
- Identify node types for: v-if, v-else, v-else-if, v-for, v-show directives
- Determine if directives are attributes on element nodes or separate nodes

### 2. Extend Vue metric mappings
- Add template directive nodes to `VueMetricMapping.kt`
- Map v-if, v-else-if as logic complexity
- Map v-for as logic complexity
- Consider v-show if it creates branches

### 3. Handle conditional metrics
- May need `MetricCondition` to match specific attribute names (v-if vs other attributes)
- Ensure directives are counted correctly without double-counting

### 4. Write tests
- Add tests for v-if complexity in templates
- Add tests for v-for complexity in templates
- Add tests combining script and template complexity

## Steps

- [ ] Explore Vue template AST structure
- [ ] Identify directive node types and their structure
- [ ] Extend VueMetricMapping with template directive mappings
- [ ] Write tests for template complexity
- [ ] Run all tests and ensure they pass

## Notes

- Template directives may be represented as attributes on element nodes
- May require `MetricCondition` to filter specific attribute values
- Total complexity = script complexity + template complexity
- This builds on the infrastructure from Phase 1
