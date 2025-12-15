# Architecture Overview

## Vertical Slice + Hexagonal Architecture

The codebase follows a vertical slice architecture where each feature (metrics, extraction) is self-contained with its own hexagonal structure (ports/adapters pattern).

## Core Components

```
src/main/kotlin/de/maibornwolff/treesitter/excavationsite/
├── api/                           # Public API (thin facade)
│   ├── Language.kt                # Public enum of supported languages
│   ├── TreeSitterMetrics.kt       # Delegates to features/metrics/
│   ├── TreeSitterExtraction.kt    # Delegates to features/extraction/
│   ├── AvailableMetrics.kt        # Public metric enums
│   └── ExtractionTypes.kt         # Public extraction types
├── features/                      # Feature slices (vertical slices)
│   ├── metrics/                   # Metrics feature
│   │   ├── domain/                # Feature-specific models (CalculationContext, etc.)
│   │   ├── ports/                 # Interfaces (MetricNodeTypes)
│   │   ├── adapters/              # Adapters (LanguageDefinitionMetricsAdapter)
│   │   ├── calculators/           # Metric calculators
│   │   ├── MetricsFacade.kt       # Feature entry point
│   │   ├── MetricCollector.kt     # Orchestrates metric calculations
│   │   └── MetricsToCalculatorsMap.kt  # Calculator registry
│   └── extraction/                # Extraction feature
│       ├── ports/                 # Interfaces (ExtractionNodeTypes)
│       ├── adapters/              # Adapters (LanguageDefinitionExtractionAdapter)
│       ├── model/                 # ExtractedText, ExtractionContext
│       ├── extractors/            # Extraction logic
│       │   ├── common/            # Common extractors (CommentExtractor, StringExtractor)
│       │   └── languagespecific/  # Per-language extractors
│       ├── parsers/               # Text parsing utilities
│       ├── ExtractionFacade.kt    # Feature entry point
│       ├── ExtractionExecutor.kt  # Orchestrates extraction
│       └── DirectTextExtractor.kt # AST walker
├── languages/                     # Language definitions
│   ├── Language.kt                # Internal language enum with definitions
│   ├── LanguageRegistry.kt        # Language lookup
│   ├── LanguageDefinition.kt      # Interface for language definitions
│   └── *Definition.kt             # Per-language definitions (14 languages)
└── shared/                        # Cross-cutting concerns
    ├── domain/                    # Domain core (innermost layer - no dependencies)
    │   ├── Metric.kt              # Metric types
    │   ├── MetricCondition.kt     # Metric conditions
    │   ├── CalculationExtensions.kt  # Language-specific calculation hooks
    │   ├── Extract.kt             # Extraction behavior types
    │   ├── ExtractionStrategy.kt  # Generic extraction strategies
    │   ├── CommentFormats.kt      # Comment format definitions
    │   └── StringFormats.kt       # String format definitions
    └── infrastructure/
        └── walker/                # Tree traversal utilities
            ├── TreeTraversal.kt   # Node traversal helpers
            ├── TreeWalker.kt      # AST walking
            ├── TreeSitterParser.kt # Parser wrapper
            └── TreeNodeTypes.kt   # Node type abstractions
```

## Hexagonal Architecture Pattern

The architecture follows concentric layers with `shared/domain/` at the center:

```
                    ┌─────────────────────────────────────────┐
                    │              api/ (outermost)           │
                    │  ┌───────────────────────────────────┐  │
                    │  │         features/ (adapters)      │  │
                    │  │  ┌─────────────────────────────┐  │  │
                    │  │  │      languages/ (ports)     │  │  │
                    │  │  │  ┌───────────────────────┐  │  │  │
                    │  │  │  │  shared/domain/       │  │  │  │
                    │  │  │  │  (innermost - core)   │  │  │  │
                    │  │  │  │                       │  │  │  │
                    │  │  │  │  Metric, Extract,     │  │  │  │
                    │  │  │  │  CommentFormats, etc. │  │  │  │
                    │  │  │  └───────────────────────┘  │  │  │
                    │  │  └─────────────────────────────┘  │  │
                    │  └───────────────────────────────────┘  │
                    └─────────────────────────────────────────┘
```

**Dependency Flow** (all arrows point inward):
- `api/` → `features/`, `languages/`, `shared/domain/`
- `features/` → `languages/`, `shared/domain/`
- `languages/` → `shared/domain/`
- `shared/domain/` → nothing (no internal dependencies)

- **Domain Core** (`shared/domain/`): Core types like `Metric`, `Extract`, `CommentFormats` with no dependencies
- **Ports** (`languages/`): `LanguageDefinition` interface that defines language behavior
- **Adapters** (`features/`): Convert language definitions to feature-specific interfaces
- **API** (`api/`): Public entry points for external consumers

## Data Flow

```
Source Code String
       ↓
TreeSitterMetrics.parse(code, language)
       ↓
MetricsFacade → LanguageDefinitionMetricsAdapter (adapts LanguageDefinition to MetricNodeTypes)
       ↓
MetricCollector.collectMetrics()
       ↓
Walk AST → For each node, look up metrics via MetricNodeTypes port
       ↓
Delegate to calculators via MetricsToCalculatorsMap
       ↓
Aggregate results (complexity = logic + function complexity)
       ↓
MetricsResult (metrics map + per-function metrics map)
```

## Unified Language Definition Architecture

Each language implements `LanguageDefinition` with two maps:

```kotlin
interface LanguageDefinition {
    val nodeMetrics: Map<String, Set<Metric>>      // Node type → metric contributions
    val nodeExtractions: Map<String, Extract>       // Node type → extraction behavior
    val calculationExtensions: CalculationExtensions // Language-specific hooks
}
```

## Key File Locations

| Purpose | Location |
|---------|----------|
| Public API | `api/` |
| **Domain core types** | `shared/domain/` |
| Metrics feature | `features/metrics/` |
| Extraction feature | `features/extraction/` |
| Language definitions | `languages/` |
| Metric calculators | `features/metrics/calculators/` |
| Language-specific extractors | `features/extraction/extractors/languagespecific/<lang>/` |
| Tree traversal utilities | `shared/infrastructure/walker/` |
| Language tests | `src/test/kotlin/.../languages/<lang>/` |
| Architecture tests | `src/test/kotlin/.../architecture/` |

All paths relative to `src/main/kotlin/de/maibornwolff/treesitter/excavationsite/`
