# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.4.0] - 2026-03-23

### Added
- Dependencies API: `TreeSitterDependencies.analyze(code, language) -> DependencyResult`
- Java dependency analysis including package path, imports, declarations, and used types

## [0.3.1] - 2026-01-30

### Added
- ABL support for `.i` include files

## [0.3.0]

### Added
- ABL/OpenEdge language support
- Detekt static analysis

## [0.2.0]

### Added
- Extraction API: `TreeSitterExtraction.extract(code, language) -> ExtractionResult`

## [0.1.0]

### Added
- Initial release
- Metrics API: `TreeSitterMetrics.parse(code, language) -> MetricsResult`
- Support for 14 languages

[unreleased]: https://github.com/MaibornWolff/TreeSitterExcavationSite/compare/v0.4.0...HEAD
[0.4.0]: https://github.com/MaibornWolff/TreeSitterExcavationSite/compare/v0.3.1...v0.4.0
[0.3.1]: https://github.com/MaibornWolff/TreeSitterExcavationSite/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/MaibornWolff/TreeSitterExcavationSite/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/MaibornWolff/TreeSitterExcavationSite/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/MaibornWolff/TreeSitterExcavationSite/releases/tag/v0.1.0
