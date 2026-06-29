---
name: Rust Metric Support
issue:
state: complete
version:
---

## Goal

Add **code-metrics** support for Rust to TSE, so `TreeSitterMetrics.parse(code, Language.RUST)`
returns populated complexity, function, comment, parameter, and message-chain metrics (today it
returns only `loc`/`rloc`, with every node-based metric `0.0`). Extraction and dependencies already
exist; this is the last of the three features for Rust.

The metrics engine is fully generic — the only production change is a new `RustMetricMapping` (node
type → `Metric`) plus wiring it into `RustDefinition`. No calculator, port, adapter, API, or
`LanguageRegistry` change is needed (Rust is already registered, and `isLanguageSupported(".rs")`
already returns `true`).

## Decisions (resolved before planning)

All node types below were verified empirically by dumping the real AST with `tree-sitter-rust 0.24.0`
(the grammar jar ships no `node-types.json`). Three design calls had divergent precedent across the
existing 18 languages and were resolved with the user:

- **Closures (`closure_expression`) → complexity only.** Adds to `complexity`/`function_complexity`
  but is **not** counted in `number_of_functions` and does **not** open a per-function scope. Matches
  Kotlin `lambda_literal` and Swift `lambda_literal` (Rust closures are lambda-like). *Not* Go's
  `func_literal` (which counts as a function).
- **Trait method signatures (`function_signature_item`, bodyless, e.g. `fn area(&self) -> f64;`) →
  count as functions.** Gets `FunctionComplexity + Function`, like Java treats abstract
  `method_declaration`. Accepted consequence: a bodyless signature opens a function scope that never
  finds a body `block`, so it contributes a `0` to `min/mean/median_*_per_function` (same skew Java
  already accepts for abstract methods). Covered explicitly by a test.
- **Complexity extras → count `match` arms + `loop`; exclude `?` and match guards.** This is exactly
  what the other languages do:
  - `match_arm` (incl. the `_` wildcard) — every language counts each switch/match branch incl. the
    catch-all (PHP `match_conditional_expression`/`match_default_expression`, C#
    `switch_expression_arm`, Go `*_case`/`default_case`, Kotlin `when_entry`, Ruby `when`/`else`, …).
  - `loop_expression` — every language counts its infinite loop; ABL's dedicated unconditional-loop
    node `repeat_statement` is the direct precedent and it counts.
  - `?` (`try_expression`) — **excluded**; no language counts an error-*propagation*/early-return
    operator. What languages count is the *handler* (`catch_clause`/`except_clause`/`rescue`), whose
    Rust analog is `match`/`if let` on `Result` (already counted).
  - match guards (`if cond` on an arm) — **excluded**; no language separately counts a branch guard
    (Swift `where`, C# arm `when` are not mapped). The arm counts once; `&&`/`||` inside the guard
    still count via `binary_expression`.
- **`else` not counted.** Plain `else` is `else_clause` (the majority — Java/Go/Kotlin/Swift/C#/C/
  C++/JS — don't count it); `else if` is a nested `if_expression` and counts naturally.
- **`self` not a parameter.** Rust's receiver is a distinct `self_parameter` node, so mapping only
  `parameter` excludes it automatically (matches Go/Java receiver handling).
- **README maturity → `Stable`.** Coverage matches the other Stable languages; mark Rust metrics
  `Stable`, consistent with its already-Stable extraction.

## Verified node mapping

| Metric | Rust node type(s) | Notes |
|--------|-------------------|-------|
| `LogicComplexity` | `if_expression`, `while_expression`, `for_expression`, `loop_expression`, `match_arm` | `if`/`while` nodes also cover `if let`/`while let` (condition is `let_condition`) |
| `LogicComplexityConditional` | `binary_expression` where field `operator` ∈ {`&&`, `\|\|`} | field name `operator` confirmed |
| `FunctionComplexity` + `Function` | `function_item`, `function_signature_item` | free fns, impl methods, trait default methods, trait signatures |
| `FunctionComplexity` only | `closure_expression` | not counted as a function |
| `FunctionBody` | `block` | first `block` inside a function = its body; nested control-flow blocks are harmless (engine guards with `isInFunctionBody`) |
| `Parameter` | `parameter` | `self_parameter` excluded by design |
| `MessageChain` + `MessageChainCall` | `call_expression` | |
| `MessageChain` | `field_expression` | navigation node for `a.b().c()` chains |
| `CommentLine` | `line_comment`, `block_comment` | cover `//` `///` `//!` and `/* */` `/** */` `/*! */` |

## Tasks

### 1. Create `RustMetricMapping`

`languages/rust/RustMetricMapping.kt` — new `object RustMetricMapping : MetricMapping` mirroring the
`GoMetricMapping`/`JavaMetricMapping` shape:

```kotlin
object RustMetricMapping : MetricMapping {
    override val nodeMetrics: Map<String, Set<Metric>> = buildMap {
        // Logic complexity (if/while cover their `let` forms)
        listOf("if_expression", "while_expression", "for_expression", "loop_expression", "match_arm")
            .forEach { put(it, setOf(Metric.LogicComplexity)) }

        // Logic complexity - conditional (&& / ||)
        put(
            "binary_expression",
            setOf(
                Metric.LogicComplexityConditional(
                    MetricCondition.ChildFieldMatches(
                        fieldName = "operator",
                        allowedValues = setOf("&&", "||")
                    )
                )
            )
        )

        // Function complexity + number of functions (bodyless trait signatures included)
        listOf("function_item", "function_signature_item")
            .forEach { put(it, setOf(Metric.FunctionComplexity, Metric.Function)) }

        // Closures: complexity only, not counted as functions
        put("closure_expression", setOf(Metric.FunctionComplexity))

        // Function body (for RLOC per function)
        put("block", setOf(Metric.FunctionBody))

        // Parameters (self_parameter is a separate node, naturally excluded)
        put("parameter", setOf(Metric.Parameter))

        // Message chains
        put("call_expression", setOf(Metric.MessageChain, Metric.MessageChainCall))
        put("field_expression", setOf(Metric.MessageChain))

        // Comments
        listOf("line_comment", "block_comment").forEach { put(it, setOf(Metric.CommentLine)) }
    }
}
```

### 2. Wire `RustMetricMapping` into `RustDefinition`

`languages/rust/RustDefinition.kt`:
- Change `override val nodeMetrics: Map<String, Set<Metric>> = emptyMap()` →
  `= RustMetricMapping.nodeMetrics`.
- Update the KDoc: remove "Metrics are out of scope, so `nodeMetrics` is intentionally empty"; state
  that Rust now supports metrics, extraction, and dependencies.
- No `calculationConfig` override is needed — Rust uses brace-delimited bodies (`block`), so the
  default `CalculationConfig()` (`hasFunctionBodyStartOrEndNode = true`) is correct.

### 3. Write `RustMetricsTest` (TDD — write before Tasks 1–2 pass)

`src/test/kotlin/.../languages/rust/RustMetricsTest.kt`, AAA style, `@Nested` groups, names start
with "should". Cover every decision so regressions are caught:
- **Logic complexity**: `if`, `if let`, `while`, `while let`, `for`, `loop` each `+1`; `&&`/`||`
  via `binary_expression`; a `match` with N arms → `+N` including the `_` wildcard arm; nested
  control flow sums.
- **Exclusions (regression guards)**: `?` (`try_expression`) adds `0`; a `match` arm *guard*
  (`Some(n) if n > 0 =>`) adds only the arm's `1`, not an extra `1`; plain `else` adds `0`.
- **number_of_functions**: `function_item` (free fn, impl method, trait default method) counts;
  `function_signature_item` (trait signature) counts; `closure_expression` does **not**.
- **complexity_per_function**: a trait signature contributes `0` (drives `min` to `0.0`); a
  concrete fn with branches aggregates correctly (max/min/mean/median).
- **parameters_per_function**: `&self` excluded; `(a, b)` → 2; closures don't form their own entry.
- **rloc_per_function**: max/min/mean/median over function bodies.
- **comment_lines**: line, doc (`///`, `//!`), and block (`/* */`) comments counted.
- **message_chains**: `a.b().c().d()` (4 calls) → `1`; a shorter chain → `0`.

### 4. Update the metrics golden file

`src/test/resources/contract/rust_sample_metrics.golden` currently has all node-based metrics `0.0`.
After Tasks 1–2, `GoldenFileContractTest` (`@EnumSource(Language::class)`) will run RUST and fail.
Regenerate by setting `UPDATE_GOLDEN_FILES = true` in `GoldenFileContractTest`, run the metrics
golden test, then **revert the flag**. Manually sanity-check the new values against `rust_sample.rs`
(e.g. `number_of_functions` = 4 `function_item` + 2 trait `function_signature_item` = `6.0`;
`logic_complexity` = `0.0` since the sample has no control flow; `complexity` = `6.0`).

### 5. Update documentation

- `README.md` line 136: change the Rust **Metrics** column from `—` to `Stable`.
- `CHANGELOG.md` (`[Unreleased] → Added`): add a Rust-metrics entry; and correct the existing
  extraction line that claims "Metrics are out of scope; `TreeSitterMetrics.parse` returns LOC/RLOC
  only for Rust."

## Steps

- [x] Complete Task 3 (partial): add failing `RustMetricsTest` cases (Red)
- [x] Complete Task 1: create `RustMetricMapping`
- [x] Complete Task 2: wire into `RustDefinition` + fix KDoc (Green — `RustMetricsTest` passes)
- [x] Complete Task 3 (full): finish/round out test cases incl. regression guards; refactor
- [x] Complete Task 4: regenerate & verify `rust_sample_metrics.golden` (flag reverted)
- [x] Complete Task 5: update README matrix + CHANGELOG
- [x] `./gradlew ktlintFormat` then `./gradlew build` (all tests + ktlint) green

## Automated Verification

- [x] `./gradlew test --tests "RustMetricsTest"` passes (28 cases)
- [x] `./gradlew test --tests "GoldenFileContractTest"` passes (RUST metrics golden matches)
- [x] `./gradlew test --tests "LanguageSupportContractTest"` still passes (no support-matrix regression)
- [x] `./gradlew ktlintCheck` passes
- [x] `./gradlew build` passes (full suite — under JDK 21; see note)

## Implementation deviations from plan

Two tree-sitter-rust comment quirks (not anticipated by the plan, which assumed no calculator/config
change) required fixes for correct metrics:

1. **RLOC leak (Rust-local fix).** Unlike most grammars, Rust `line_comment`/`block_comment` nodes are
   not leaves; their child tokens (`//`, `/*`, `*/`, `doc_comment`, doc markers, and `!`/`/` under the
   markers) leaked into RLOC as code lines. Fixed via `RustDefinition.calculationConfig.ignoreForRloc`
   (`TypeInSet` for unique tokens + `TypeWithParentType` for `!`/`/`, which are safe because real
   negation/division sit under `unary_expression`/`binary_expression`).
2. **comment_lines over-count (shared calculator fix, user-approved).** Rust line doc comments
   (`///`, `//!`) span two rows because the `doc_comment` token swallows the trailing newline, so the
   node's exclusive end point lands at column 0 of the next row. `CommentLinesCalc` now clamps that row
   off. Verified: every other language's metric golden is unchanged.

Environment note: `./gradlew build` fails under the sandbox default JDK 25 because detekt 1.23.8's
bundled compiler can't parse the version string "25.0.3" (pre-existing, unrelated to this change).
The full build (incl. detekt) is green under JDK 17/21.

## Notes

- AST node types verified against `tree-sitter-rust 0.24.0` via a throwaway exploration test (since
  the jar has no `node-types.json`); the test was removed after confirming. To re-derive, parse with
  `LanguageRegistry.getTreeSitterLanguage(Language.RUST)` and walk `node.type` + `getFieldNameForChild`.
- Mapping `block` → `FunctionBody` is safe despite Rust's expression-oriented grammar (blocks appear
  as `if`/`while`/`for`/`loop` bodies and bare blocks): the per-function engine
  (`MetricPerFunctionCalc`) only opens a scope on `Metric.Function` nodes and latches `isInFunctionBody`
  on the first body block, so nested control-flow blocks don't double-count. Go/Java map `block` the
  same way.
- No new `Metric` type, calculator, or `api/AvailableMetrics` change — all required metrics already
  exist; this is purely a node→metric mapping addition.
- Out of scope: the README Rust **Dependencies** column (tracked by the separate dependency plan) and
  any DependaCharta-side work.
```
