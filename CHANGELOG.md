# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.8.0] - 2026-05-04

### Added

- C++ dependency analysis including namespace path, `#include`/`using` directives, declarations, and used types (11 categories)
- `UsedType.namespacePrefix` and `ImportDeclaration.kind` (with `ImportKind` enum) on the public dependency model to support C++ qualified inline references
  and the `#include`/`using` distinction; source- and binary-compatible for non-C++ callers

## [0.7.0] - 2026-04-28

### Added

- Delphi (`.pas`, `.dpr`) language support for metrics, extraction, and dependency analysis. Based on the `tree-sitter-pascal`
  grammar (v0.10.2); covers classes, interfaces, records, enums, helpers, and procedure / function implementations. Pascal's three
  comment styles (`//`, `{ }`, `(* *)`) and single-quoted string literals are handled via custom extractors.

### Changed

- Reorder Delphi used-type concatenation to match Kotlin's category sequence (inheritance → data → callable → annotations → calls).

### Fixed

- PackageExtractor now recovers the unit/program/library name from tree-sitter-pascal parse-error wrapping, so files containing
  unsupported asm/IFDEF combinations no longer produce declarations with an empty package path.

## [0.6.0] - 2026-04-17

### Added
- C# dependency analysis including namespace extraction, using directives with namespace scoping, declarations, and used types (11
  categories)
- Using directives inside nested namespaces or wrapped in preprocessor directives are now extracted with their full aggregated
  namespace path (improvement over DC legacy, which only scans immediate children of the compilation unit and namespace bodies and
  therefore misses these)

## [0.5.0] - 2026-03-31

### Added

- Kotlin dependency analysis including package path, imports, declarations, and used types

## [0.4.1] - 2026-03-26

### Fixed

- Fixed tree-sitter-tsx packaging in build artifact

## [0.4.0] - 2026-03-23

### Added

- Dependencies API: `TreeSitterDependencies.analyze(code, language) -> DependencyResult`
- Java dependency analysis including package path, imports, declarations, and used types
- TSX language support with JSX element and attribute extraction

## [0.3.1] - 2026-01-30

### Added

- ABL support for `.i` include files

## [0.3.0] - 2026-01-29

### Added

- ABL/OpenEdge language support
- Detekt static analysis

## [0.2.0] - 2025-12-18

### Added

- Extraction API: `TreeSitterExtraction.extract(code, language) -> ExtractionResult`

## [0.1.0] - 2025-12-16

### Added

- Initial release
- Metrics API: `TreeSitterMetrics.parse(code, language) -> MetricsResult`
- Support for 14 languages

[unreleased]: https://github.com/MaibornWolff/TreeSitterExcavationSite/compare/v0.8.0...HEAD

[0.8.0]: https://github.com/MaibornWolff/TreeSitterExcavationSite/compare/v0.7.0...v0.8.0

[0.7.0]: https://github.com/MaibornWolff/TreeSitterExcavationSite/compare/v0.6.0...v0.7.0

[0.6.0]: https://github.com/MaibornWolff/TreeSitterExcavationSite/compare/v0.5.0...v0.6.0

[0.5.0]: https://github.com/MaibornWolff/TreeSitterExcavationSite/compare/v0.4.1...v0.5.0

[0.4.1]: https://github.com/MaibornWolff/TreeSitterExcavationSite/compare/v0.4.0...v0.4.1

[0.4.0]: https://github.com/MaibornWolff/TreeSitterExcavationSite/compare/v0.3.1...v0.4.0

[0.3.1]: https://github.com/MaibornWolff/TreeSitterExcavationSite/compare/v0.3.0...v0.3.1

[0.3.0]: https://github.com/MaibornWolff/TreeSitterExcavationSite/compare/v0.2.0...v0.3.0

[0.2.0]: https://github.com/MaibornWolff/TreeSitterExcavationSite/compare/v0.1.0...v0.2.0

[0.1.0]: https://github.com/MaibornWolff/TreeSitterExcavationSite/releases/tag/v0.1.0
