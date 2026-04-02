---
name: add-csharp-dependency-support
issue:
state: progress
version: 3
---

## Goal

Migrate C# dependency analysis from DependaCharta's legacy `CSharpAnalyzer` to TSE, following the pattern established by Java and Kotlin
migrations.

**Done when:**

- All new and existing TSE tests pass
- ktlint and architecture tests pass
- dc-compare against a real C# project matches DC main

**Parallel work:** TSE branch (`feat/csharp-dependency-support`) implements the extractors. DC branch (`feat/tse-csharp-integration`)
rewrites `CSharpAnalyzer` to call `TreeSitterDependencies.analyze()` and points at the TSE branch via JitPack. Both branches are needed
throughout — dc-compare validates TSE output by comparing DC main (legacy) vs DC branch (TSE-backed).

## Current Status

**TSE (this repo, `feat/csharp-dependency-support`):**
- All extractors implemented: NamespaceExtractor, UsingDirectiveExtractor, DeclarationExtractor, UsedTypeExtractor (all 11 categories)
- 40 tests pass (23 structural + 17 UsedType), ktlint clean, architecture tests pass

**DC (`feat/tse-csharp-integration` in `../DependaCharta`):**
- CSharpAnalyzer rewritten as thin TSE adapter (implements `LanguageAnalyzer` directly, not `BaseLanguageAnalyzer`, because `Language.CSHARP` ≠ `SupportedLanguage.C_SHARP` name mismatch)
- Uses `declaration.parentPath` for namespace path (not `packagePath + parentPath` — avoids duplication for file-scoped namespaces)
- Composite build configured (settings.gradle.kts + build.gradle.kts) — **revert before merging**
- Legacy analyzer files kept in place (not deleted yet)
- 16/18 unit tests pass; 2 fail (namespace-scoped import association)

**dc-compare (Spectre.Console, 441 C# files):**
- Golden standard: `../dc-compare/main/analysis.cg.json`
- Feature output: `../dc-compare/feature/analysis.cg.json`
- **409/437 nodes match exactly (94%)**
- 5 nodes missing in feat (preprocessor directives `#if` / C# 14 `extension` keyword — tree-sitter grammar limitation)
- 28 nodes differ: 7 main-has-more, 20 feat-has-more, 1 both-differ (total: 9 missing deps, 22 extra deps)

## Remaining Work

- [ ] Investigate dc-compare differences (28 nodes) — categorize root causes
- [ ] Fix namespace-scoped import association (2 failing DC tests, likely contributes to 20 "feat has more" deps)
- [ ] Iterate dc-compare fixes (one commit per fix)
- [ ] Final verification: full test suite + ktlint + architecture tests + dc-compare
- [ ] Clean up: delete legacy DC files, revert composite build, tag TSE release

## Analysis: DC's Legacy C# Analyzer

DC's `CSharpAnalyzer` processes C# files as follows:

1. **Namespaces**: Finds file-scoped (`namespace Foo.Bar;`) and traditional block-scoped (`namespace Foo.Bar { ... }`) namespaces
2. **Using directives**: Extracts at both file level (global) and namespace level (scoped). Each becomes a wildcard dependency. Supports
   optional aliases.
3. **Declarations**: 6 types — `class_declaration`, `struct_declaration`, `record_declaration`, `interface_declaration`, `enum_declaration`,
   `delegate_declaration`. Only direct children of namespace bodies (NOT nested types).
4. **Used types**: 11 extraction categories (see concatenation order below)
5. **Re-parsing**: Each declaration is re-parsed individually for type isolation — nested types leak upward (same as Java/Kotlin)
6. **Attribute suffix**: `[MyAttr]` generates both `MyAttr` and `MyAttrAttribute`

**DC concatenation order:** constructors, methods, casts, genericParams, genericConstraints, inherited, variables, objectCreations,
memberAccesses, attributes, isTypeChecks

## Design Decisions

### Namespaces vs packagePath — start simple, dc-compare decides

C# has multiple namespaces per file and per-namespace using directives. TSE's model has one `packagePath` and one flat `imports` list.

**Approach:** Use `packagePath` for file-scoped namespace (or empty). Use `Declaration.parentPath` for each declaration's namespace path.
Merge all using directives into `imports`. Start with this — dc-compare will reveal whether namespace-scoped using directive association
matters. If it does, extend the model then.

**Findings:** dc-compare shows 20 nodes with extra deps in feat, likely caused by flattened imports resolving to nodes they shouldn't
reach. Namespace-scoped import association is needed.

### Declaration types — TSE is precise, DC adapter maps

Java already uses `DeclarationType.RECORD` for records, Kotlin uses `ANNOTATION` for annotation classes. TSE mapping:

- `class_declaration` → CLASS
- `struct_declaration` → CLASS (no STRUCT type, closest match)
- `record_declaration` → RECORD (matches Java precedent)
- `interface_declaration` → INTERFACE
- `delegate_declaration` → INTERFACE (no DELEGATE type, closest match)
- `enum_declaration` → ENUM

### Nested declarations — top-level only (match DC)

DC extracts only direct children of namespace bodies, NOT nested types. TSE must do the same. Nested types' used types leak upward via
traversal (matching DC's re-parsing behavior).

### Type leakage — match DC's re-parsing

DC re-parses each declaration individually. Nested types' used types leak upward to the parent declaration. TSE's direct traversal leaks the
same way. No boundary exclusion needed (same as Java/Kotlin).

### Attribute suffix duplication

DC generates both `MyAttribute` and `MyAttributeAttribute` for `[MyAttribute]`. TSE must match this behavior. Implementation: after
collecting attributes, duplicate each with the "Attribute" suffix appended.

### Variable type filtering

DC filters out `var` and `void` from variable declarations. TSE must match.

### No standard library filtering needed in TSE

DC has `CSharpStandardLibrary.kt` with 88 keywords, but it's NOT used in `CSharpAnalyzer`'s extraction pipeline. Don't implement.

### DC CSharpAnalyzer — custom LanguageAnalyzer (not BaseLanguageAnalyzer)

Cannot extend `BaseLanguageAnalyzer` because:
1. `Language.CSHARP` (TSE) ≠ `SupportedLanguage.C_SHARP` (DC) — `Language.valueOf(language.name)` would fail
2. C# needs per-declaration namespace wildcards (from `declaration.parentPath`), not a single shared wildcard from `packagePath`

The DC adapter uses `declaration.parentPath` directly for both `pathWithName` and the implicit wildcard dependency.

## Completed Steps

- [x] Explore C# AST structure (dump sample code, verify all assumptions)
- [x] NamespaceExtractor: tests + implementation
- [x] UsingDirectiveExtractor: tests + implementation
- [x] DeclarationExtractor (finding + classifying declarations): tests + implementation
- [x] CSharpDependencyMapping + register in CSharpDefinition
- [x] Set up DC branch (`feat/tse-csharp-integration`) — rewrite CSharpAnalyzer to use TSE
- [x] First DC test run — 6/18 pass (structural), 10 fail (usedTypes empty), 2 fail (namespace-scoped imports)
- [x] UsedTypeExtractor — all 11 categories: tests + implementation
- [x] Second DC test run — 16/18 pass, 2 remain (namespace-scoped imports)
- [x] dc-compare (Spectre.Console): 409/437 same (94%), 28 nodes differ

## AST Findings (verified)

- **No `type_declaration` wrapper** — declarations are direct children of `compilation_unit` or `declaration_list`
- **File-scoped namespace**: `file_scoped_namespace_declaration` → `qualified_name`/`identifier`
- **Traditional namespace**: `namespace_declaration` → `qualified_name`/`identifier` + `declaration_list` body
- **Using directives**: `using_directive` → `identifier`/`qualified_name`; aliased usings have `identifier` (alias) + `=` +
  `qualified_name` (path)
- **Nested classes** are `class_declaration` inside parent's `declaration_list` — same node type, just deeper
- **All 6 declaration types** use `identifier` child for the name
- **Primary constructors**: `parameter_list` directly on `class_declaration`
- **Generic types**: `generic_name` → `identifier` + `type_argument_list`
- **5 missing nodes** in dc-compare are from preprocessor directives (`#if NETSTANDARD2_0` etc.) and C# 14 `extension` keyword — tree-sitter grammar limitations, not TSE bugs

## dc-compare Differences (28 nodes, to investigate)

Nodes where **feat has extra deps** (20): Likely namespace-scoped import flattening causing extra dependency resolution.
Sample: `Spectre.Console.SpinnerExtensions` has extra dep on `Spectre.Console.IAnsiConsole`.

Nodes where **main has more deps** (7): TSE may be missing some type extractions.
Sample: `Spectre.Console.AnsiConsoleFacade` missing dep on `Spectre.Console.Capabilities`.

Nodes where **both differ** (1): `Spectre.Console.Paragraph` — main has `Justify`, feat has `Link`.

## Notes

- DC concatenation order: constructors, methods, casts, genericParams, genericConstraints, inherited, variables, objectCreations,
  memberAccesses, attributes, isTypeChecks
- C# `generic_name` differs from Java's `generic_type`
- Attribute suffix: `[X]` → `X` + `XAttribute` (both added as used types)
- Filter `var` and `void` from variable declaration types
- Struct → CLASS, delegate → INTERFACE, record → RECORD (TSE is precise, DC adapter maps)
- `parentPath` used for namespace path (C# doesn't need Kotlin-style nesting paths)
- Use `TreeTraversal.*` for direct traversal, never TSQuery
- DC does NOT use CSharpStandardLibrary in CSharpAnalyzer — skip it
