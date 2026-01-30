# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

TreeSitterLibrary is a standalone Kotlin library for calculating code metrics using TreeSitter. It was extracted from CodeCharta's UnifiedParser to be reusable across projects.

**Key Features**:
- **Metrics API**: `TreeSitterMetrics.parse(code, language) -> MetricsResult`
- **Extraction API**: `TreeSitterExtraction.extract(code, language) -> ExtractionResult`
- Support for 16 languages and frameworks: Java, Kotlin, TypeScript, JavaScript, Python, Go, PHP, Ruby, Swift, Bash, C#, C++, C, Objective-C, Vue, ABL
- No external dependencies beyond TreeSitter

**Requirements**: Java >= 11, <= 21 | Gradle 8.x (wrapper included)

**CodeCharta Compatibility**: This library maintains API compatibility with CodeCharta's metric expectations. Changes to the public API should consider impact on CodeCharta's UnifiedParser.

## Quick Commands

```bash
./gradlew build                          # Build
./gradlew test                           # Run all tests
./gradlew test --tests "JavaMetricsTest" # Run single test class
./gradlew test --tests "*java*"          # Run tests matching pattern
./gradlew ktlintFormat                   # Auto-format code
./gradlew ktlintCheck                    # Check code style
```

## Architecture

Vertical slice + hexagonal architecture:

```
api/                    # Public entry points (thin facade)
integration/            # Feature slices
  ├── metrics/          # Metrics feature (calculators, ports, adapters)
  └── extraction/       # Extraction feature (extractors, ports, adapters)
languages/              # Language definitions (16 languages and frameworks)
  └── <lang>/           # Per-language: Definition, MetricMapping, ExtractionMapping
shared/
  ├── domain/           # Core types (innermost layer - no dependencies)
  └── infrastructure/   # Tree traversal utilities
```

**Dependency flow** (all arrows point inward): `api/` → `integration/` → `languages/` → `shared/domain/`

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
