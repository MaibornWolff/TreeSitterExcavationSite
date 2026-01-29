---
name: Architecture Refactoring
issue: N/A
state: complete
version: 1.0
---

## Goal

Restructure the codebase so that each package has a clear responsibility: `api/` as pure facade, `languages/` with all language-specific logic (one file per concept), `integration/` for common logic, and `shared/` for interfaces and types.

## Tasks

### 1. Move core types to shared/domain/

Move types that are used across layers:
- `Language` enum from `languages/` to `shared/domain/`
- `MetricsResult` from `api/TreeSitterMetrics.kt` to `shared/domain/`
- `AvailableFileMetrics` and `AvailableFunctionMetrics` from `integration/metrics/domain/` to `shared/domain/`
- `CalculationContext` from `integration/metrics/domain/` to `shared/domain/`

### 2. Create separate mapping interfaces in shared/domain/

Split `LanguageDefinition` into three interfaces:
- `MetricMapping` - interface with `nodeMetrics: Map<String, Set<Metric>>`
- `ExtractionMapping` - interface with `nodeExtractions: Map<String, Extract>`
- `LanguageDefinition` - combines both + `calculationConfig`

### 3. Split language definitions into separate files

For each language (e.g., Kotlin), create:
- `languages/kotlin/KotlinMetricMapping.kt` - implements `MetricMapping`
- `languages/kotlin/KotlinExtractionMapping.kt` - implements `ExtractionMapping`
- `languages/kotlin/KotlinDefinition.kt` - composes both, implements `LanguageDefinition`

### 4. Clean up api/ to be pure facade

- Move `MetricsResult` data class out (done in task 1)
- Keep only delegation methods
- Update type aliases to point to new locations

### 5. Update integration/ package structure

- Remove `integration/metrics/domain/` (types moved to shared)
- Ensure adapters reference interfaces from `shared/domain/`

### 6. Update imports throughout codebase

- Fix all imports after package moves
- Ensure tests still compile and pass

## Steps

- [x] Move `Language` enum to `shared/domain/`
- [x] Move `MetricsResult` to `shared/domain/`
- [x] Move `AvailableFileMetrics` and `AvailableFunctionMetrics` to `shared/domain/`
- [x] Move `CalculationContext` to `shared/domain/`
- [x] Create `MetricMapping` interface in `shared/domain/`
- [x] Create `ExtractionMapping` interface in `shared/domain/`
- [x] Update `LanguageDefinition` to extend both interfaces
- [x] Split Kotlin definition into 3 files (as template for others)
- [x] Split remaining 13 languages into 3 files each
- [x] Update `LanguageRegistry` to use new structure
- [x] Clean up api/ package
- [x] Move StringParser and CommentParser to `shared/domain/`
- [x] Run tests and fix any remaining import issues
- [x] Run ktlintFormat

## Notes

- Dependency flow (sibling architecture):
  ```
             api/
            /    \
           v      v
     languages/  integration/
            \    /
             v  v
        shared/domain/
  ```
- `api/` orchestrates: gets definitions from `languages/`, passes them to `integration/`
- `languages/` and `integration/` are siblings - both depend only on `shared/domain/`
- `shared/domain/` has no internal dependencies (innermost layer)
- Language-specific extractors stay in their respective language packages
- `LanguageRegistry` stays in `languages/` as it orchestrates language-specific implementations
