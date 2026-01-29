# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

TreeSitterLibrary is a standalone Kotlin library for calculating code metrics using TreeSitter. It was extracted from CodeCharta's UnifiedParser to be reusable across projects.

**Key Features**:
- **Metrics API**: `TreeSitterMetrics.parse(code, language) -> MetricsResult`
- **Extraction API**: `TreeSitterExtraction.extract(code, language) -> ExtractionResult`
- Support for 14 programming languages
- No external dependencies beyond TreeSitter

**Requirements**: Java >= 11, <= 21 | Gradle 8.x (wrapper included)

## Quick Commands

```bash
./gradlew build              # Build
./gradlew test               # Run all tests
./gradlew ktlintFormat       # Auto-format code
./gradlew ktlintCheck        # Check code style
```

## Modular Rules

Detailed guidelines are in `.claude/rules/`:

| File | Purpose |
|------|---------|
| `overview.md` | Project overview and supported languages |
| `commands.md` | All development commands |
| `planning.md` | Planning workflow for new instructions |
| `code-style.md` | Kotlin code style guidelines |
| `tdd.md` | TDD workflow and test structure |
| `architecture.md` | Vertical slice + hexagonal architecture |
| `api-usage.md` | API usage patterns (applies to `api/` files) |
| `language-definitions.md` | Adding new languages (applies to `languages/` files) |
| `metrics.md` | Metric calculator patterns (applies to `integration/metrics/` files) |
| `extraction.md` | Text extraction patterns (applies to `integration/extraction/` files) |
| `testing.md` | Testing conventions (applies to test files) |
