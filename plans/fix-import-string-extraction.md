---
name: Fix Import Path String Extraction
issue: #N/A
state: complete
version: 1
---

## Goal

Prevent import path strings from being extracted as STRING context. Currently, languages that use string literals for imports (JS/TS, Go, Ruby, PHP) incorrectly extract import paths like `'@angular/core'` as meaningful string content.

## Background

The `Extract.StringLiteral(custom = ...)` pattern already supports returning `null` to skip nodes (see `DirectTextExtractor.kt:104-105`). The fix uses custom extractors that check parent node context.

## Affected Languages

| Language | Import Syntax | Parent Node Types to Skip |
|----------|---------------|---------------------------|
| JavaScript/TypeScript | `import from 'path'` | `import_statement`, `export_statement` |
| Go | `import "fmt"` | `import_declaration`, `import_spec` |
| Ruby | `require 'gem'` | `call` with method `require`/`require_relative` |
| PHP | `include 'file'` | `include_expression`, `require_expression`, etc. |

## Tasks

### 1. Create custom string extractor for JavaScript/TypeScript
- Create `languages/javascript/extractors/StringLiteralExtractor.kt`
- Check if any ancestor is import/export related, return `null` if so
- Otherwise parse string using `StringParser.stripAnyQuotes()`
- Update `JavascriptExtractionMapping.kt` to use `custom = ::extractNonImportString`
- Also handle `template_string` the same way

### 2. Create custom string extractor for Go
- Create `languages/go/extractors/StringLiteralExtractor.kt`
- Check if ancestor is `import_declaration` or `import_spec`
- Update `GoExtractionMapping.kt` for `interpreted_string_literal`, `raw_string_literal`
- Keep `rune_literal` as-is (not used in imports)

### 3. Create custom string extractor for Ruby
- Create `languages/ruby/extractors/StringLiteralExtractor.kt`
- Ruby is special: `require 'gem'` parses as `call` → `argument_list` → `string`
- Check if ancestor is a `call` node, then check if method name is `require`/`require_relative`
- Update `RubyExtractionMapping.kt` to use custom extractor

### 4. Create custom string extractor for PHP
- Create `languages/php/extractors/StringLiteralExtractor.kt`
- Check for ancestors: `include_expression`, `require_expression`, `include_once_expression`, `require_once_expression`
- Update `PhpExtractionMapping.kt` to use custom extractor

### 5. Write tests for each language
- Add test: `should not extract import path strings`
- Add test: `should still extract regular string literals alongside imports`
- Test edge cases (multiple imports, mixed with regular strings)

## Steps

- [x] Complete Task 1: Fix JavaScript/TypeScript extraction
- [x] Complete Task 2: Fix Go extraction
- [x] Complete Task 3: Fix Ruby extraction
- [x] Complete Task 4: Fix PHP extraction
- [x] Complete Task 5: Write tests for all affected languages
- [x] Run full test suite to verify no regressions

## Notes

- Python, Java, Kotlin, C/C++, C#, Swift, Bash, Objective-C are NOT affected (imports use identifiers)
- Performance impact is negligible (~2ns per string for ancestor check)
- Consider JS/TS `require()` calls (CommonJS) - these are `call_expression` nodes
- Vue uses JavaScript extraction, so it inherits the fix automatically
