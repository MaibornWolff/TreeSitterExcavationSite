---
name: add-kotlin-dependency-support
issue:
state: complete
version: 6
---

## Goal

Migrate Kotlin dependency analysis from DependaCharta's legacy `KotlinAnalyzer` to TSE, following the same pattern established by the Java implementation.

**Done when:**
- All new and existing TSE tests pass
- ktlint and architecture tests pass
- dc-compare against a real Kotlin project matches DC main

**DC follow-up (separate PR, out of scope):** After TSE is merged and tagged, DC's `KotlinAnalyzer` needs to be rewritten to call `TreeSitterDependencies.analyze()` and its legacy query classes deleted — same pattern as the Java migration.

## Design Decisions

### Nested class parent paths — start flat, let dc-compare decide

DC's Kotlin analyzer is the ONLY language that tracks parent class paths for nested declarations (e.g., `Path(["pkg", "Outer", "Inner"])`). All other languages (Java, C#, Go, Python, PHP, JS/TS, C++) use flat names.

The likely reason: Kotlin's sealed classes (`Result.Success`, `Result.Error`) are referenced through their parent in imports (`import com.example.Result.Success`). DC's resolver may need the full path to match correctly.

**Approach:** Start with flat names (like Java). Run dc-compare. If it fails due to path mismatches, add `parentPath: List<String> = emptyList()` to `Declaration`. This is backward-compatible, doesn't affect Java or future languages, and isn't Kotlin-specific — any language with nested type paths (C# nested types, C++ nested classes) could use it later.

### Type leakage — match DC's Kotlin behavior

Both Java and Kotlin legacy analyzers in DC re-parse each declaration body as a new AST (`parseCode(nodeAsString(declaration, content))`). Re-parsing a class still includes its nested classes, so both DC analyzers leak nested types upward. TSE's direct tree traversal leaks the same way. This is expected to match — dc-compare passed for Java with this behavior.

**Approach:** No boundary exclusion needed. TSE's traversal leaks identically to DC's re-parsing.

### Companion objects — likely a distinct node type

DC's Kotlin declarations query only matches `class_declaration` and `object_declaration` — it never matches companion objects. This suggests Kotlin's TreeSitter grammar uses `companion_object` as a **distinct node type** from `object_declaration`. If so, `findAllDescendantsOfType("class_declaration", "object_declaration")` will naturally skip companion objects with no extra filtering needed.

**Verify during AST exploration:** Confirm `companion_object` is a separate node type. If it's an `object_declaration` with a modifier instead, we need explicit filtering.

### DeclarationType detection — differs from Java

Java has distinct AST node types (`class_declaration`, `enum_declaration`, `interface_declaration`). Kotlin uses a single `class_declaration` with modifier children (`enum`, `interface`, `annotation`, `data`, `sealed`). Detection must check child modifiers, not node type.

### Type extraction — keep per language, no shared utility

Investigated whether `extractType` (recursive generic type parsing) should be shared across languages. Only ~4 languages have similar generic type structures (Java, Kotlin, C#, C++). The remaining languages have fundamentally different or no type systems (Python/JS/Ruby have no type nodes, TypeScript structures generics on declarations not inline, PHP has no generics). A shared utility would need too many parameters to handle the differences. ~13 lines of duplication per language is acceptable — each language owns its own `extractType`.

### Star projections and function types

- **Star projections** (`List<*>`): DC produces `UsedType("List")` with empty generic types. The `*` in a `type_projection` has no named children, so `mapNotNull { firstOrNull() }` filters it out. TSE should match: skip star projections, return the base type with no generics.
- **Function types** (`(String) -> Int`): DC does NOT decompose function types. They are `function_type` nodes, not `user_type`, so `extractType` falls through to raw string extraction producing `UsedType("(String) -> Int")`. DC tests make no assertions on function type usedTypes, confirming they're not meaningful for dependency resolution. TSE should match: if the type node is a `function_type`, extract as raw string or skip entirely. Verify behavior with dc-compare.

## Tasks

Tasks follow TDD: write failing test → implement → verify green → next test.

### 0. Explore Kotlin AST

Before writing code, dump Kotlin AST for sample code covering: package, imports, class, sealed class, enum class, interface, object, companion object, data class, annotation class, nullable types, generics, star projections, function types. Verify all node type assumptions before proceeding.

### 1. PackageExtractor (TDD)

- Write `PackageExtraction` tests first (multi-segment, single-segment, no package)
- Implement `PackageExtractor` — extract from `package_header` → `simple_identifier` nodes, split by `.`

### 2. ImportExtractor (TDD)

- Write `ImportExtraction` tests first (single, multiple, wildcard, no imports)
- Implement `ImportExtractor` — extract from `import_header` → `simple_identifier` nodes, detect wildcard via `*`
- **AST assumption to verify:** Kotlin imports may use `identifier` with dotted text rather than separate `simple_identifier` per segment (Java uses `scoped_identifier`). Confirm during AST exploration.

### 3. DeclarationExtractor + UsedTypeExtractor (TDD)

Most complex task. Break into sub-steps:

**DeclarationExtractor:**
- Find `class_declaration` and `object_declaration` recursively via `findAllDescendantsOfType`
- `companion_object` is likely a distinct node type and won't be matched (verify during AST exploration)
- Map `object_declaration` → `DeclarationType.CLASS` (matches DC behavior)
- Detect enum/interface/annotation via modifier child nodes (not AST node type)
- Delegate to UsedTypeExtractor for each declaration

**UsedTypeExtractor:**
- Extract all types used within a declaration, following DC's concatenation order:
  1. Inheritance (delegation_specifier → user_type/nullable_type)
  2. Properties (property_declaration → type annotations)
  3. Parameters (parameter + class_parameter → type annotations)
  4. Return types (function_declaration → return type)
  5. Annotations (annotation → constructor_invocation/user_type)
  6. Constructor calls (call_expression, uppercase-first filter)
  7. Call expressions (navigation_expression, uppercase-first filter)
- Unwrap `nullable_type` to inner `user_type` (e.g., `String?` → `String`)
- Support generic types via `type_arguments` → `type_projection` recursion
- Star projections: skip (no named children in `type_projection`), base type gets empty generics
- Function types: fall through to raw string or skip — not meaningful for dependency resolution
- Apply uppercase-first heuristic for constructor calls and call expressions
- Start WITHOUT boundary exclusion (like Java). Add only if dc-compare reveals leakage issues.

**TDD approach for this task:** Start with simple declaration tests (single class with one field type), incrementally add complexity (generics, nullable, nested classes, boundary exclusion). Each test written before the implementation that makes it pass.

### 4. Create KotlinDependencyMapping

Compose the extractors into a `LanguageDependencyMapping` and register in `KotlinDefinition`.

### 5. Write remaining tests

After core implementation, add:

**Negative/edge cases:**
- Empty file (no declarations, no package)
- File with only functions (no class declarations)
- File with only imports
- Class with no used types (empty body)

**Completeness check:**
- Realistic multi-declaration file exercising all features together (like Java's completeness test)

**Nesting tests:**
- Nested declarations are found recursively (not just top-level)
- Companion objects are NOT treated as declarations
- Sealed class nested types are separate declarations
- Used types within object declarations (properties, inheritance, functions)

### 6. Set up early dc-compare loop

Create a DC branch (`feat/tse-kotlin-integration`) that points to the latest commit of TSE's `feat/kotlin-dependency-support` branch via JitPack. This enables running `/dc-compare` throughout development, not just at the end.

**DC branch setup:**
- Branch from DC `main`
- Update `analysis/build.gradle.kts` to point TSE dependency at the latest TSE Kotlin branch commit
- DC's `KotlinAnalyzer` stays unchanged (still legacy) — dc-compare compares DC main (legacy) vs DC main + TSE Kotlin support

**When to run dc-compare:**
- After PackageExtractor + ImportExtractor + DeclarationExtractor are working (basic declarations)
- After UsedTypeExtractor is feature-complete (full type extraction)
- After any fix prompted by earlier dc-compare runs

Update the DC branch's TSE commit hash after each TSE push.

**Test repos:**
- **Primary:** kotlinx-datetime (small, pure Kotlin, sealed classes)
- **Fallback:** kotlinx-serialization (larger, more diverse patterns — nested classes, companion objects, annotations)

**If dc-compare reveals mismatches:**
- **Parent path mismatches** → add `parentPath` to `Declaration`
- **Used type mismatches** → trace to specific extractor, fix extraction logic
- **Declaration count mismatches** → check companion object filtering, nested class handling
- **Other** → investigate DC's resolver behavior vs TSE output

### 7. Final verification

- Run full TSE test suite (`./gradlew test`) — all existing tests must still pass
- Run `./gradlew ktlintCheck` — code style passes
- Run architecture tests — no dependency violations
- Run `./gradlew ktlintFormat` before committing
- Final dc-compare run confirms match

## Steps

- [x] Explore Kotlin AST structure (dump sample code, verify assumptions)
- [x] Write PackageExtraction tests → implement PackageExtractor
- [x] Write ImportExtraction tests → implement ImportExtractor
- [x] Write DeclarationExtractor tests → implement DeclarationExtractor (finding + classifying declarations)
- [x] Write UsedTypeExtractor tests (incremental) → implement UsedTypeExtractor (type extraction per category)
- [x] Create KotlinDependencyMapping and register in KotlinDefinition
- [x] Set up DC branch (`feat/tse-kotlin-integration`) pointing to TSE commit 7d1f7f2, KotlinAnalyzer rewritten to use TSE
- [x] First dc-compare run — 17k line diff, identified parentPath + navigation expression issues
- [x] Write negative/edge case tests + completeness test
- [x] Write nesting tests (recursive discovery, companion objects, sealed types, object used types)
- [x] Fix parentPath: added `parentPath` field to `Declaration`, Kotlin DeclarationExtractor computes it
- [x] Fix navigation expressions: extract standalone `navigation_expression` types (Padding.NONE, Formats.ISO pattern)
- [x] Second dc-compare run — down to ~2.9k line diff
- [x] Fix qualified navigation expressions and constructor calls (root cause: TSE skipped nested navigation_expression first children)
- [x] Fix unsafe `!!` operators in UsedTypeExtractor (replaced with `!= true` pattern)
- [x] Annotation classification decided: keep ANNOTATION (correct), DC integration already maps correctly
- [x] Third dc-compare run — diff down from ~2.9k to ~1.7k lines. Navigation expression fix helped but new issue found.
- [x] Dotted `user_type` extraction investigated — first-segment-only is correct (matches DC), resolver handles path matching
- [x] Fix nested inheritance leakage: `extractInheritanceTypes` now uses buckets (all descendants) instead of direct children
- [x] Fourth dc-compare run — diff down to 74 lines (5 annotation improvements + 6 MonthDayTime improvements + 3 weight effects)
- [x] Final verification: full test suite + ktlintCheck + architecture tests — all pass

## Notes

- DC concatenation order: inheritance, properties, parameters, returnTypes, annotations, constructorCalls, callExpressions
- `object_declaration` maps to `DeclarationType.CLASS` (user decision, matches DC)
- No new `DeclarationType` values needed
- Use `TreeTraversal.findAllDescendantsGroupedByType()` for single-pass collection in UsedTypeExtractor
- Kotlin AST uses `user_type` and `nullable_type` instead of Java's `type_identifier`
- Each language owns its own `extractType` — no shared utility (see design decision above)
- DC follow-up: rewrite DC's `KotlinAnalyzer` to use TSE, delete legacy query classes (separate PR)
- Keep DC legacy files (KotlinUtils.kt, queries/) until dc-compare confirms the new implementation matches

## Resolved dc-compare differences

### 1. Qualified navigation expression types (FIXED)
**Root cause:** TSE's `extractCallExpressionTypes` and `extractConstructorCallTypes` only extracted from `simple_identifier` first children, skipping nested `navigation_expression` nodes. DC's `extractSimpleType` extracts from ANY first named child via `nodeAsString`. For `DateTimeFormatBuilder.WithTime.create()`, DC extracted `{"DateTimeFormatBuilder.WithTime.create", "DateTimeFormatBuilder.WithTime", "DateTimeFormatBuilder"}` while TSE only extracted `{"DateTimeFormatBuilder"}`.

**Fix:** Removed `simple_identifier` type check — now extract text from any first named child, matching DC exactly.

### 2. Annotation classification (ACCEPTED as improvement)
TSE correctly identifies `annotation class` as `DeclarationType.ANNOTATION`. DC legacy classifies as `CLASS`. DC's `NodeType.ANNOTATION` enum already exists, and the rewritten `KotlinAnalyzer` on `feat/tse-kotlin-integration` already maps `DeclarationType.ANNOTATION → NodeType.ANNOTATION`. This is a genuine improvement (~10 diff lines in dc-compare).

### 3. Downstream effects (RESOLVED)
isCyclic, weight, level changes all resolved after fixing root causes.

### 4. Missing nested inheritance types (FIXED)
**Root cause:** TSE's `extractInheritanceTypes` used `declaration.children()` (direct children only), while DC's TSQuery-based inheritance extraction runs on the entire re-parsed body (all descendants). Nested types' inheritance (e.g., `WithDate : DateTimeFormatBuilder`) leaked to the outer class in DC but not TSE.

**Fix:** Changed `extractInheritanceTypes` to use `buckets[DELEGATION_SPECIFIER]` from `findAllDescendantsGroupedByType`, matching DC's recursive behavior.

### 5. Dotted user_type extraction (RESOLVED — no fix needed)
**Investigation result:** DC's `extractUserType` also extracts only the first `type_identifier` segment via `getNamedChild(0)`. The missing dependencies to nested types (`DateTimeFormatBuilder.WithDate`, etc.) came from the RESOLVER matching simple names (e.g., `WithDate`) against declarations with parent paths, NOT from the raw extraction. TSE correctly matches DC by extracting only the first segment.

### 6. MonthDayTime (ACCEPTED as improvement)
TSE correctly extracts generic type arguments from dotted `user_type` nodes (finds `type_arguments` by type name). DC's `getNamedChild(1)` positional approach breaks for dotted types like `RecurringZoneRules.Rule<MonthDayTime>` — gets the second `type_identifier` instead of `type_arguments`. Result: 3 extra correct dependencies in TSE output.
