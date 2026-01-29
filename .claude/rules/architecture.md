# Architecture Overview

## Vertical Slice + Hexagonal Architecture

The codebase follows a vertical slice architecture where each feature (metrics, extraction) is self-contained with its own hexagonal structure (ports/adapters pattern).

## Core Components

```
src/main/kotlin/de/maibornwolff/treesitter/excavationsite/
├── api/                           # Public API (thin facade)
│   ├── Language.kt                # Public enum of supported languages
│   ├── TreeSitterMetrics.kt       # Delegates to integration/metrics/
│   ├── TreeSitterExtraction.kt    # Delegates to integration/extraction/
│   ├── AvailableMetrics.kt        # Public metric enums
│   └── ExtractionTypes.kt         # Public extraction types (re-exports)
├── integration/                   # Feature integration (vertical slices)
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
│       ├── extractors/common/     # Common extractors (CommentExtractor, StringExtractor)
│       ├── ExtractionFacade.kt    # Feature entry point
│       ├── ExtractionExecutor.kt  # Orchestrates extraction
│       └── DirectTextExtractor.kt # AST walker
├── languages/                     # Language definitions
│   ├── Language.kt                # Internal language enum with definitions
│   ├── LanguageRegistry.kt        # Language lookup
│   └── <lang>/                    # Per-language directory (16 languages and frameworks)
│       ├── *Definition.kt         # Combines metric and extraction mappings
│       ├── *MetricMapping.kt      # Metric node type mappings
│       ├── *ExtractionMapping.kt  # Extraction node type mappings
│       └── extractors/            # Language-specific extractors
└── shared/                        # Cross-cutting concerns
    ├── domain/                    # Domain core (innermost layer - no dependencies)
    │   ├── Metric.kt              # Metric types
    │   ├── MetricCondition.kt     # Metric conditions
    │   ├── CalculationExtensions.kt  # Language-specific calculation hooks
    │   ├── Extract.kt             # Extraction behavior types
    │   ├── ExtractionStrategy.kt  # Generic extraction strategies
    │   ├── ExtractionResult.kt    # Extraction result data class
    │   ├── ExtractedText.kt       # Extracted text with context
    │   ├── ExtractionContext.kt   # Context enum (IDENTIFIER, COMMENT, STRING)
    │   ├── LanguageDefinition.kt  # Interface for language definitions
    │   ├── MetricMapping.kt       # Metric mapping interface
    │   ├── ExtractionMapping.kt   # Extraction mapping interface
    │   ├── CommentFormats.kt      # Comment format definitions
    │   ├── StringFormats.kt       # String format definitions
    │   ├── CommentParser.kt       # Comment parsing utilities
    │   └── StringParser.kt        # String parsing utilities
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
                    │  │       integration/ (adapters)     │  │
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
- `api/` → `integration/`, `languages/`, `shared/domain/`
- `integration/` → `languages/`, `shared/domain/`
- `languages/` → `shared/domain/`
- `shared/domain/` → nothing (no internal dependencies)

- **Domain Core** (`shared/domain/`): Core types like `Metric`, `Extract`, `CommentFormats`, `ExtractionResult` with no dependencies
- **Ports** (`languages/`): `LanguageDefinition` interface implementations that define language behavior
- **Adapters** (`integration/`): Convert language definitions to feature-specific interfaces
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

## Language Definition Architecture

Each language is organized in its own directory with separated concerns:

```kotlin
// languages/java/JavaDefinition.kt - Combines mappings
object JavaDefinition : LanguageDefinition {
    override val nodeMetrics = JavaMetricMapping.nodeMetrics
    override val nodeExtractions = JavaExtractionMapping.nodeExtractions
}

// languages/java/JavaMetricMapping.kt - Metric mappings only
object JavaMetricMapping : MetricMapping {
    override val nodeMetrics: Map<String, Set<Metric>> = buildMap { ... }
}

// languages/java/JavaExtractionMapping.kt - Extraction mappings only
object JavaExtractionMapping : ExtractionMapping {
    override val nodeExtractions: Map<String, Extract> = buildMap { ... }
}
```

## Key File Locations

| Purpose | Location |
|---------|----------|
| Public API | `api/` |
| **Domain core types** | `shared/domain/` |
| Metrics integration | `integration/metrics/` |
| Extraction integration | `integration/extraction/` |
| Language definitions | `languages/<lang>/` |
| Metric calculators | `integration/metrics/calculators/` |
| Common extractors | `integration/extraction/extractors/common/` |
| Language-specific extractors | `languages/<lang>/extractors/` |
| Tree traversal utilities | `shared/infrastructure/walker/` |
| Language tests | `src/test/kotlin/.../languages/<lang>/` |
| Architecture tests | `src/test/kotlin/.../architecture/` |

All paths relative to `src/main/kotlin/de/maibornwolff/treesitter/excavationsite/`
