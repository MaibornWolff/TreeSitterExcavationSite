---
paths: src/main/kotlin/**/features/metrics/**/*.kt
---

# Metric Calculators

## Adding a New Metric

1. **Add to public enum** in `api/AvailableMetrics.kt`:

```kotlin
enum class AvailableFileMetrics(val metricName: String) {
    // ...
    NEW_METRIC("new_metric")
}
```

2. **Add Metric type** (if needed) in `features/metrics/domain/Metric.kt`:

```kotlin
sealed class Metric {
    // ...
    data object NewMetricType : Metric()
}
```

3. **Create calculator** in `features/metrics/calculators/`:

```kotlin
class NewMetricCalc(private val nodeTypeProvider: MetricNodeTypes) : MetricPerFileCalc {
    override fun calculateMetricForNode(nodeContext: CalculationContext): Int {
        // Return 1 if this node should be counted, 0 otherwise
    }
}
```

4. **Register in MetricsToCalculatorsMap** and update `LanguageDefinitionMetricsAdapter`

## Calculator Types

- `MetricPerFileCalc` - File-level metrics (sum across all nodes)
- `MetricPerFunctionCalc` - Per-function metrics (aggregated as max, min, mean, median)

## Hexagonal Pattern

The metrics feature uses ports/adapters:
- **Port**: `MetricNodeTypes` interface in `features/metrics/ports/`
- **Adapter**: `LanguageDefinitionMetricsAdapter` in `features/metrics/adapters/` adapts `LanguageDefinition` to `MetricNodeTypes`