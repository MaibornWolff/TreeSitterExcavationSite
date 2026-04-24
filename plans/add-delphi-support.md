---
name: Delphi Language Support
issue:
state: progress
version:
---

## Goal

Add Delphi as a supported language (`Language.DELPHI`) using the `tree-sitter-pascal` grammar (v0.10.2) from https://github.com/Isopod/tree-sitter-pascal. The grammar covers all Pascal dialects including Delphi/Object Pascal. Metrics, extraction, and file extension detection for `.pas` and `.dpr` files.

## Tasks

### 0. Build tree-sitter-pascal JAR (Prerequisite — DONE ✓)

No JVM binding exists for tree-sitter-pascal. Built it using `bonede/tree-sitter-ng`'s `gen` task (same approach as `tree-sitter-tsx-0.23.2.jar`):

```bash
git clone https://github.com/bonede/tree-sitter-ng   # already cloned at C:\Development\CodeChartaEtc\tree-sitter-ng
./gradlew gen --parser-name=pascal --parser-version=0.10.2 \
  --parser-zip=https://github.com/Isopod/tree-sitter-pascal/archive/refs/tags/v0.10.2.zip
./gradlew :tree-sitter-pascal:buildNative
./gradlew :tree-sitter-pascal:jar
```

**Fixes required during build (gen task is outdated for this version of tree-sitter-ng):**
1. `settings.gradle` line 28: missing newline between `tree-sitter-tests` and `tree-sitter-pascal` (gen bug)
2. `tree-sitter-pascal/build.gradle`: replaced generated old-style `tasks.register()` boilerplate with modern one-liner:
   ```groovy
   tasks.named('downloadSource') {
       url = "https://github.com/Isopod/tree-sitter-pascal/archive/refs/tags/v${libVersion}.zip"
   }
   ```
3. `TreeSitterPascal.java`: gen used outdated `implements TSLanguage` pattern; updated to `extends TSLanguage` with `copy()` method (matching TreeSitterJava pattern)

Result: `libs/tree-sitter-pascal-0.10.2.jar` — native DLLs for all 5 platforms (Windows x64, macOS x64, macOS ARM, Linux x64, Linux ARM).

`build.gradle.kts` updated (no `libs.versions.toml` entry needed — local JAR bypasses the version catalog, same as TSX):
```kotlin
// Added near top with treeSitterTsxJar:
val treeSitterPascalJar = "libs/tree-sitter-pascal-0.10.2.jar"

// Added to dependencies block:
implementation(files(treeSitterPascalJar))

// Added to tasks.jar block (alongside existing TSX entry):
from(zipTree(treeSitterPascalJar))
```

### 1. Add Language enum and registration

- Add `DELPHI(primaryExtension = ".pas", otherExtensions = setOf(".dpr"))` to `shared/domain/Language.kt`
- Register in `languages/LanguageRegistry.kt`:
  - `Language.DELPHI -> TreeSitterPascal()` in `getTreeSitterLanguage`
  - `Language.DELPHI -> DelphiDefinition` in `getLanguageDefinition`

### 2. Create `languages/delphi/` language definition (TDD)

**Node type reference** — tree-sitter-pascal uses **camelCase** node types (unlike all other languages in the project which use snake_case):

| Category | Node types |
|----------|-----------|
| Logic complexity | `if`, `ifElse`, `for`, `foreach`, `while`, `case`, `caseCase`, `repeat`, `try`, `exceptionHandler` |
| Conditional logic | `exprBinary` with `and`/`or`/`xor` operators |
| Functions (impl only) | `defProc`, `lambda` |
| Function body | `block` (begin/end block) |
| Parameters | `declArg` |
| Comments | `comment` (covers `//`, `{ }`, `(* *)`) |
| Strings | `literalString` |
| Message chains | `exprDot` (chain), `exprCall` (chain + call) |

**`DelphiMetricMapping.kt`** — maps node types to metrics per table above.

Note: `declProc` (declarations in the interface section) does NOT count — only `defProc` (implementations).

**`DelphiExtractionMapping.kt`** — extraction mappings:
- `defProc` → `Identifier`, `FirstChildByType("identifier")` (function/procedure names)
- `declClass`, `declIntf`, `declHelper` → `Identifier`, `FirstChildByType("identifier")` (class/interface names)
- `declVar` → `Identifier`, `FirstChildByType("identifier")` (variable names)
- `comment` → `Comment`, custom function `extractDelphiComment`
- `literalString` → `StringLiteral`, `StringFormats.Quoted(stripSingleQuotes = true)`

**Comment extraction** — Pascal's `{ }` and `(* *)` formats are NOT handled by `CommentFormats.AutoDetect`.
Create `languages/delphi/extractors/DelphiCommentExtractor.kt` with a custom function:
```kotlin
fun extractDelphiComment(node: TSNode, code: String): String? {
    val text = node.text(code)
    return when {
        text.startsWith("//") -> text.removePrefix("//").trim()
        text.startsWith("{") -> text.removePrefix("{").removeSuffix("}").trim()
        text.startsWith("(*") -> text.removePrefix("(*").removeSuffix("*)").trim()
        else -> text
    }
}
```

**`DelphiDefinition.kt`** — combines both mappings.

### 3. Fix contract/API tests

- `ApiSignatureContractTest`: `hasSize(17)` → `hasSize(18)`
- `TreeSitterExtractionTest` (general tests, not contract/): add `Language.DELPHI` to supported languages list, `hasSize(17)` → `hasSize(18)`
- `TreeSitterMetricsTest` (general tests): add `.pas` extension expectation → `Language.DELPHI`
- `LanguageSupportContractTest`: add `.pas, DELPHI` entry to `@CsvSource` in the primary extension mapping
- `GoldenFileContractTest`:
  - Add `Language.DELPHI to "delphi_sample.pas"` to `SAMPLE_FILE_NAMES`
  - Add `Language.DELPHI to "delphi_sample"` to `GOLDEN_BASE_NAMES`
  - Create `src/test/resources/contract/delphi_sample.pas` with a realistic Delphi file
  - Generate golden files via `UPDATE_GOLDEN_FILES=true`, review, restore flag

### 4. Write Delphi-specific tests (TDD)

**`DelphiMetricsTest.kt`** — one `@Nested` class per metric:
- `if` / `ifElse` → complexity
- `for` / `foreach` / `while` / `repeat` → complexity
- `case` → complexity
- `try` / `exceptionHandler` → complexity
- `exprBinary` with `and`/`or` → conditional complexity
- `defProc` → number_of_functions
- `lambda` → function_complexity (not counted in number_of_functions)
- `declArg` → parameters
- `comment` → comment_lines
- `block` → RLOC per function boundary

**`DelphiExtractionTest.kt`** — tests for:
- Identifier extraction from `defProc` (function/procedure names)
- Comment extraction for all three styles (`//`, `{ }`, `(* *)`)
- String extraction from `literalString`

## Steps

- [x] Build tree-sitter-pascal JAR and copy to `libs/tree-sitter-pascal-0.10.2.jar`
- [x] Update `build.gradle.kts` (JAR reference)
- [ ] Add `Language.DELPHI` to `shared/domain/Language.kt`
- [ ] Write failing `DelphiMetricsTest`
- [ ] Create `DelphiMetricMapping.kt` (make metrics tests pass)
- [ ] Write failing `DelphiExtractionTest`
- [ ] Create `DelphiCommentExtractor.kt`, `DelphiExtractionMapping.kt`, `DelphiDefinition.kt` (make extraction tests pass)
- [ ] Register in `LanguageRegistry.kt`
- [ ] `./gradlew test --tests "*Delphi*"` — green
- [ ] Fix contract/API tests (count changes, extension mapping)
- [ ] Create `delphi_sample.pas`, generate golden files
- [ ] `./gradlew test` — full suite green

## Notes

- tree-sitter-pascal node types are **camelCase** (e.g., `defProc`, `exprBinary`, `ifElse`) — unlike all other languages in this project
- `{$...}` compiler directives are NOT parsed as comments (the grammar excludes `{$` prefix) — no action needed
- Delphi IS Object Pascal — "Delphi language" is the official name for Embarcadero's Object Pascal dialect; the tree-sitter-pascal grammar explicitly supports it
- Pascal strings use single quotes only: `'hello'` → `StringFormats.Quoted(stripSingleQuotes = true)`
- Char-code literals `#65` will appear in `literalString` nodes but contain no meaningful text — acceptable to include/extract as-is
- `declProc` in interface sections does NOT count as a function implementation — only `defProc` counts
- `blockTr` (try block body) could also be used as FunctionBody boundary — verify with tests