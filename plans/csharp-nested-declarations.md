---
name: C# nested declaration extraction
issue: n/a
state: complete
version: 1
---

## Goal

Extract nested declarations (inner classes, nested enums, nested interfaces, etc.) in C# to be consistent with Java, Kotlin, and C++. Currently C# only extracts top-level declarations within namespaces, making nested types invisible to the dependency graph.

## Context

- **Java/Kotlin/C++** already extract nested declarations recursively via `findAllDescendantsOfType`
- **C#** uses `.children()` (direct children only), skipping anything nested inside a class/struct
- This is an improvement over DC's legacy C# analyzer, which also skipped nested declarations
- Kotlin's approach is the closest reference: recursive traversal + `findParentPath()` walking up the AST

## Tasks

### 1. Write failing tests (TDD red phase)
- Update existing `should not extract nested declarations` test to expect nested declarations instead
- Add test: nested class inside a class should be extracted with `parentPath` including the outer class name
- Add test: deeply nested declarations (class inside class inside class) should build full parentPath chain
- Add test: nested declarations inside traditional namespace should combine namespace + parent class path
- Add test: used types in nested declarations should be scoped to the nested declaration, not leak to outer

### 2. Refactor DeclarationExtractor to support nesting
- Replace `.children().filter` with `TreeTraversal.findAllDescendantsOfType()` for recursive discovery
- Add `findParentPath()` method (like Kotlin) that walks up the AST collecting ancestor declaration names
- Combine namespace path + parent declaration path for the full `parentPath`
- Build a `namesByStartByte` map upfront so `findParentPath` can resolve ancestor names efficiently

### 3. Update documentation
- Update `dependency-migration.md` — note that C# now extracts nested declarations (improvement over DC legacy)
- Update `integration/dependencies/README.md` if needed — clarify that nested extraction behavior is language-specific

## Steps

- [x] Write failing tests for nested declaration extraction
- [x] Implement recursive traversal in DeclarationExtractor
- [x] Add findParentPath logic
- [x] Verify all tests pass (green phase)
- [x] Refactor if needed (code is clean, no refactoring needed)
- [x] Run dc-compare to verify purely additive change (+353 nodes, 0 regressions)
- [x] Update documentation

## Notes

- No changes needed to `UsedTypeExtractor` — it already operates on the declaration node passed to it
- The outer class will still include used types from nested class bodies (same as Kotlin's behavior — no boundary exclusion)
- dc-compare should be run after implementation to verify the improvement doesn't cause unexpected regressions
