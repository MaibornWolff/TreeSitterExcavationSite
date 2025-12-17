---
paths: src/main/kotlin/**/integration/metrics/**/*.kt
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

2. **Add Metric type** (if needed) in `shared/domain/Metric.kt`:

```kotlin
sealed class Metric {
    // ...
    data object NewMetricType : Metric()
}
```

3. **Create calculator** in `integration/metrics/calculators/`:

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
- **Port**: `MetricNodeTypes` interface in `integration/metrics/ports/`
- **Adapter**: `LanguageDefinitionMetricsAdapter` in `integration/metrics/adapters/` adapts `LanguageDefinition` to `MetricNodeTypes`

## Key Files

| File | Purpose |
|------|---------|
| `integration/metrics/MetricsFacade.kt` | Feature entry point |
| `integration/metrics/MetricCollector.kt` | Orchestrates calculations |
| `integration/metrics/MetricsToCalculatorsMap.kt` | Calculator registry |
| `integration/metrics/calculators/*.kt` | Individual calculators |
| `integration/metrics/domain/CalculationContext.kt` | Context passed to calculators |
| `shared/domain/Metric.kt` | Metric type definitions |
