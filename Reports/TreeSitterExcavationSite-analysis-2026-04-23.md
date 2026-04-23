# Code Quality Analysis Report — TreeSitterExcavationSite

**Analysis Date:** 2026-04-23
**Metric Used:** `logic_complexity`
**Scope:** Top 10 files by `logic_complexity` on branch `feat/cpp-dependency-support` (excluding test resources).

---

## TL;DR — Top Systemic Issues

- **High complexity/line ratios across AST-walking files (0.20–0.44):** Dense `when`/`if` dispatch over tree-sitter node types is characteristic of the codebase; this is by design but creates a cluster of files with very little room per branch to breathe. Most concentrated in `StringExtractor`, `LanguageRegistry`, `StringParser`, and `PatternCaptureExtractor`.
- **Near-duplicate walker / dispatch helpers inside single files:** `TreeTraversal` has four ancestor walkers that share the same `while (parent)` loop with different predicates; `CppTypeHelper` has three qualified-identifier walkers with near-identical loop bodies; `CDeclaratorParser` exposes four public entry points whose names (`findIdentifierInDeclarator`, `extractIdentifierFromDeclaratorField`, `extractFromQualifiedIdentifier`, `extractFromInitDeclarator`) make the intended boundary unclear.
- **`UsedTypeExtractor` is at the detekt function-count ceiling (15):** The file already orchestrates 11 extraction buckets and sits at 53 logic_complexity / 309 RLOC. New categories can't be added as new methods without going over the detekt threshold — this session hit that wall and worked around it by merging throw-statement handling into the existing `extractInstantiationTypes`. A deliberate split (e.g. call-expression extractor vs. field/declaration extractor) is warranted before adding more categories.
- **Stateful metric calculators carry many mutable fields:** `RealLinesOfCodeCalc` has 8 mutable state fields and mixed read/write per call. Correct, but untestable in isolation and fragile to reorder.
- **No god classes, no deep nesting, no critical complexity:** All top-10 files sit well below the "moderate" threshold (complexity < 100). Largest file is 309 RLOC. This is a healthy codebase at the file level; the issues are mostly density-and-duplication inside small files.

---

## File 1: UsedTypeExtractor.kt (C++)
**Path:** `src/main/kotlin/de/maibornwolff/treesitter/excavationsite/languages/cpp/extractors/UsedTypeExtractor.kt`
**Complexity:** 53
**File Size:** 309 RLOC

### Finding 1: Orchestrator at detekt function-count ceiling
**Severity:** High

**Explanation:** The object sits at exactly 15 private functions — detekt's configured `TooManyFunctions` ceiling for objects. The C++ TSQuery coverage is incomplete (no `primitive_type`-in-declaration, no amalgamation cases, template specializations); any new extractor category triggers the detekt violation and forces an awkward merge into an existing branch, as happened this session when throw-statement handling had to be folded into `extractInstantiationTypes` rather than living in its own function. This creates a hard scaling ceiling that is semantic, not physical.

**Line:** 78–254 (whole body of `extract` plus its 11 helpers)

**Code Snippet:**
```kotlin
78    fun extract(declaration: TSNode, sourceCode: String): Set<UsedType> {
79        val buckets = groupDescendantsStoppingAtNestedDeclarations(declaration, ALL_NODE_TYPES)
80        val inheritance = extractInheritanceTypes(buckets, sourceCode)
81        val methodTypes = extractMethodReturnAndParamTypes(buckets, sourceCode)
...
99        return (
100           fieldAndVariableTypes + cStyleCasts + instantiationTypes + initializerTypes +
101               typeOperandTypes + friendAndUsingTypes +
...
105       ).toSet()
106   }
```

**Suggested Fix:** Split by category cluster into separate files under `languages/cpp/extractors/usedtypes/` — e.g. `CallExpressionTypeExtractor` (CALL_EXPRESSION, NEW_EXPRESSION, THROW_STATEMENT, INIT_LIST), `DeclarationTypeExtractor` (FIELD, DECLARATION, CAST, SIZEOF, ALIGNOF), `SignatureTypeExtractor` (FUNCTION_DEFINITION, FUNCTION_DECLARATOR, BASE_CLASS_CLAUSE, TRAILING_RETURN), `AliasConstraintExtractor` (TYPE_DEFINITION, ALIAS_DECLARATION, template constraints), `ClassScopeExtractor` (FRIEND, in-class USING). Each keeps its own constants and helpers; the root `UsedTypeExtractor.extract()` becomes a concat of 5 smaller extractors' outputs in DC-legacy order.

**Recommendation:** Split before adding more extraction patterns; the next TDD cycle is already blocked by the ceiling.

---

### Finding 2: Mixed concerns in `extractInstantiationTypes`
**Severity:** Medium

**Explanation:** The method at L216–254 now dispatches on `function.type` × `(isInThrow, isInArgList)` — three orthogonal concerns in one function. It handles regular call expressions, throw-wrapped calls, and bare-identifier arg-list calls. The parent-type lookup adds a third dimension. Behavior per branch is correct but the combinatorial space is hard to eyeball, and the session's Issue 4 commit merged throw handling here purely because detekt blocked a dedicated function.

**Line:** 216–254

**Code Snippet:**
```kotlin
221       val callTypes = (buckets[CALL_EXPRESSION].orEmpty() + throwCallees).flatMap { call ->
222           val function = call.getChildByFieldName(FUNCTION_FIELD).takeIf { !it.isNull } ?: return@flatMap emptyList()
223           val parentType = call.parent.takeIf { !it.isNull }?.type
224           val isInThrow = parentType == THROW_STATEMENT
225           val isInArgList = parentType == ARGUMENT_LIST
226           when (function.type) {
227               TEMPLATE_FUNCTION -> {
228                   val generics = extractTemplateArgumentTypes(function, sourceCode)
229                   if (isInThrow) { ... }
230                   else { generics }
...
244               IDENTIFIER -> if (isInThrow || isInArgList) { ... } else { emptyList() }
```

**Suggested Fix:** After the file split (Finding 1), lift the parent-type detection to the caller and make the call-expression extractor receive a `CallContext(parentType)`. Each callsite can then decide once whether to invoke the extractor at all, rather than re-checking per call.

**Recommendation:** Pair this refactor with the file split; doing it without reduces only local complexity without resolving the ceiling.

---

### Finding 3: 41-line constant-declaration block
**Severity:** Low

**Explanation:** Lines 10–51 declare 41 private `const val` strings for AST node types. It's clear and consistent, but obscures the operational part of the file — you scroll a screenful before reaching the first real logic. The constants are semantically related to the node types used in each extractor, so they could move closer to the sites that use them (once the file is split per Finding 1).

**Line:** 10–51

**Code Snippet:**
```kotlin
10    private const val BASE_CLASS_CLAUSE = "base_class_clause"
11    private const val FUNCTION_DEFINITION = "function_definition"
...
51    private const val TYPE_IDENTIFIER = "type_identifier"
```

**Suggested Fix:** After splitting, relocate each constant to the sub-extractor that uses it. A tiny shared set (CLASS_SPECIFIER, etc. for DECLARATION_BOUNDARIES) can live in a small companion.

**Recommendation:** Handle as part of the split refactor; not worth a standalone change.

---

## File 2: LanguageRegistry.kt
**Path:** `src/main/kotlin/de/maibornwolff/treesitter/excavationsite/languages/LanguageRegistry.kt`
**Complexity:** 34
**File Size:** 77 RLOC

### Finding 1: Two parallel 17-arm `when`s over the same enum
**Severity:** Medium

**Explanation:** `getTreeSitterLanguage` and `getLanguageDefinition` each switch on the same `Language` enum with 17 arms. Every new language requires two additions in two separate functions — an easy place to forget one half. Ratio 0.44 (highest among the top 10) is almost entirely from the two dispatch tables. This is not technical debt in the sense of bad design — it's a classic "data is code" spot where a single source of truth would serve better.

**Line:** 50–91

**Code Snippet:**
```kotlin
50    fun getTreeSitterLanguage(language: Language): TSLanguage = when (language) {
51        Language.JAVA -> TreeSitterJava()
52        Language.KOTLIN -> TreeSitterKotlin()
...
73    fun getLanguageDefinition(language: Language): LanguageDefinition = when (language) {
74        Language.JAVA -> JavaDefinition
75        Language.KOTLIN -> KotlinDefinition
```

**Suggested Fix:** Introduce a `LanguageSpec(val treeSitter: () -> TSLanguage, val definition: LanguageDefinition)` keyed by `Language` in a single map. Both getters become one-liners: `registry.getValue(language).treeSitter()` / `.definition`. Compilers will still warn on missing cases if a new enum value isn't registered.

**Recommendation:** Consolidate the two dispatch tables into one keyed by the `Language` enum value — prevents half-added-language bugs.

---

## File 3: StringParser.kt
**Path:** `src/main/kotlin/de/maibornwolff/treesitter/excavationsite/shared/domain/StringParser.kt`
**Complexity:** 28
**File Size:** 85 RLOC

### Finding 1: Index-arithmetic raw-string parser
**Severity:** Medium

**Explanation:** `stripCppRawString` at L63–82 does manual index arithmetic on `R"delim(...)delim"` literals. The fallback path (L80) silently returns `text.removePrefix("R\"(").removeSuffix(")\"")` when the delimiter logic fails, masking malformed inputs. A future bug here (off-by-one, delimiter with special chars) would produce silently wrong string content rather than throw. Manual index math on user-visible string literals is always a fragile spot.

**Line:** 63–82

**Code Snippet:**
```kotlin
63    fun stripCppRawString(text: String): String {
64        val rawPrefix = "R\""
65        if (!text.startsWith(rawPrefix)) return text
66        val afterPrefix = text.removePrefix(rawPrefix)
67        val openParen = afterPrefix.indexOf('(')
...
76        val contentEnd = afterPrefix.lastIndexOf(closingPattern)
77        return if (contentEnd > contentStart) {
78            afterPrefix.substring(contentStart, contentEnd)
79        } else {
80            text.removePrefix("R\"(").removeSuffix(")\"")
81        }
82    }
```

**Suggested Fix:** Replace with a compiled regex: `R"^R"([^(]*)\((.*)\)\1"$"` in multiline mode. Makes the intent explicit and pushes correctness responsibility onto the regex engine. Keep the no-prefix early return; let the regex mismatch fall back to `text` unchanged.

**Recommendation:** Add property-based tests against malformed raw-string inputs before refactoring; current behavior is serving as the de-facto spec.

---

### Finding 2: Ordered prefix list is a smell
**Severity:** Low

**Explanation:** `stripPythonString` iterates a hardcoded list of Python string prefixes (L121–124). Ordering matters — "fr" must come before "f" — which is easy to miss when adding prefixes. A regex-based approach would be self-documenting and order-insensitive.

**Line:** 120–148

**Code Snippet:**
```kotlin
121       val prefixes = listOf(
122           "fr", "rf", "br", "rb", "FR", "RF", "BR", "RB",
123           "f", "r", "b", "u", "F", "R", "B", "U"
124       )
125       var stripped = text
126       for (prefix in prefixes) {
127           if (stripped.startsWith(prefix) && ...) {
```

**Suggested Fix:** Replace with `Regex("^(?:fr|rf|br|rb|f|r|b|u)?", RegexOption.IGNORE_CASE).replace(text, "")`. Removes the ordering trap and the list length.

**Recommendation:** Low priority; fine as-is if nobody is actively adding Python prefixes.

---

## File 4: TreeTraversal.kt
**Path:** `src/main/kotlin/de/maibornwolff/treesitter/excavationsite/shared/infrastructure/walker/TreeTraversal.kt`
**Complexity:** 24
**File Size:** 99 RLOC

### Finding 1: Four near-identical ancestor walkers
**Severity:** Medium

**Explanation:** `hasAncestorOfType`, `hasAncestorOfTypes`, `findAncestorOfType`, and `isDescendantOf` all share the same `var current = node.parent; while (current != null && !current.isNull) { ... current = current.parent }` loop, differing only in the predicate and return type. Four copies of the walk means a change to null-handling, isNull semantics, or early-termination has to be made in four places.

**Line:** 55–99

**Code Snippet:**
```kotlin
55    fun hasAncestorOfType(node: TSNode, type: String): Boolean {
56        var current = node.parent
57        while (current != null && !current.isNull) {
58            if (current.type == type) return true
59            current = current.parent
60        }
61        return false
62    }
...
80    fun findAncestorOfType(node: TSNode, type: String): TSNode? {
81        var current = node.parent
82        while (current != null && !current.isNull) {
83            if (current.type == type) return current
84            current = current.parent
85        }
86        return null
87    }
```

**Suggested Fix:** Extract a private `walkAncestors(node: TSNode): Sequence<TSNode>` generator. Then `hasAncestorOfType = walkAncestors(n).any { it.type == type }`, `findAncestorOfType = walkAncestors(n).firstOrNull { it.type == type }`, `isDescendantOf = walkAncestors(n).any { it == ancestor }`. Reduces four duplications to a single walker plus three one-line callers.

**Recommendation:** Low-risk DRY refactor — do it next time you touch this file.

---

### Finding 2: Two recursive descent implementations
**Severity:** Low

**Explanation:** `findAllDescendantsOfType` (L104) and `findAllDescendantsGroupedByType` (L123) duplicate the recursion; only the collector differs (list vs. typed map). This is the one shape where extraction is worth it because a shared `visitDescendants(node, onMatch: (TSNode) -> Unit)` gives the same result at zero readability cost.

**Line:** 104–137

**Code Snippet:**
```kotlin
111   private fun collectDescendantsOfType(node: TSNode, types: Set<String>, result: MutableList<TSNode>) { ... }
...
129   private fun collectDescendantsByTypes(node: TSNode, types: Set<String>, result: MutableMap<String, MutableList<TSNode>>) { ... }
```

**Suggested Fix:** Unify into `private fun visitDescendants(node: TSNode, types: Set<String>, onMatch: (TSNode) -> Unit)`. Public methods close over their own accumulator (list or map).

**Recommendation:** Combine with Finding 1 into a single walker-cleanup commit.

---

## File 5: RealLinesOfCodeCalc.kt
**Path:** `src/main/kotlin/de/maibornwolff/treesitter/excavationsite/integration/metrics/calculators/RealLinesOfCodeCalc.kt`
**Complexity:** 21
**File Size:** 104 RLOC

### Finding 1: Eight mutable state fields on a per-file calculator
**Severity:** High

**Explanation:** The class holds 8 `var` fields (L12–21) that encode the progress of a line-by-line walk. Each call to `calculateMetricForNode` reads and mutates a subset; some mutations happen in helpers two levels deep. This makes the calculator impossible to unit-test in isolation without calling a specific sequence of nodes, and the failure mode for a missed reset (e.g. when `resetBoundaries` isn't invoked) is a silent wrong-RLOC. The alternative would be to thread state explicitly (per-call `CalculationState` data class) and let Kotlin's type system enforce that every branch sets every field.

**Line:** 12–21

**Code Snippet:**
```kotlin
12        override val metric = AvailableFunctionMetrics.RLOC
13        private var lastCountedLine = -1
14        private var isFirstOrLastNodeInFunction = false
15        private var isStartOfFunctionBody = false
16        private var isFirstAllowedNodeInFunctionBody = false
17        private var functionBodyBoundariesSet = false
18        private var functionBodyStartRow = -1
19        private var functionBodyStartColumn = -1
20        private var functionBodyEndRow = -1
21        private var functionBodyEndColumn = -1
```

**Suggested Fix:** Introduce a private `data class FunctionBodyRange(val startRow: Int, val startCol: Int, val endRow: Int, val endCol: Int)` to group the four position fields, and a `FunctionTracking` data class wrapping the four booleans. Reduces the surface area, makes `resetBoundaries` a single assignment, and makes it obvious which fields are part of the function-tracking state vs. the line-counting state.

**Recommendation:** Worth a dedicated refactor pass when the calculator is next touched; currently correct but fragile.

---

### Finding 2: Long positional helper signature
**Severity:** Low

**Explanation:** `setFunctionBodyBoundaries` at L109 takes four positional integers with similar names (`startRow, startCol, endRow, endCol`). Transposing startRow and startCol at a call site would compile fine and silently produce wrong results.

**Line:** 109–117

**Code Snippet:**
```kotlin
109       private fun setFunctionBodyBoundaries(startRow: Int, startCol: Int, endRow: Int, endCol: Int) {
```

**Suggested Fix:** After introducing the `FunctionBodyRange` data class (Finding 1), pass a single range object. Callers at L96 can construct the object from `nodeContext.startRow/startColumn/endRow/endCol` directly.

**Recommendation:** Bundle with Finding 1.

---

## File 6: PatternCaptureExtractor.kt (Python)
**Path:** `src/main/kotlin/de/maibornwolff/treesitter/excavationsite/languages/python/extractors/PatternCaptureExtractor.kt`
**Complexity:** 21
**File Size:** 73 RLOC

### Finding 1: Higher-order parameter threaded through recursion
**Severity:** Low

**Explanation:** Every recursive call to `extractCapturesFromNode` passes a `findFirstIdentifier: (TSNode, String) -> String?` lambda through (L14, L19, L26, L31, L44, L55, L63). Only `extractCapturesFromAsPattern` appears to actually dispatch it into a call site, but the signature is duplicated at each helper. This is a minor API smell — the callback exists because the Python extractor architecture needed to avoid a direct dependency, but it forces every intermediate function to care about something that's effectively a dependency injection detail.

**Line:** 11–70

**Code Snippet:**
```kotlin
11    internal fun extractPatternCaptureVariables(
12        node: TSNode,
13        sourceCode: String,
14        findFirstIdentifier: (TSNode, String) -> String?
15    ): List<String> { ... }
...
31    private fun extractCapturesFromNode(node: TSNode, sourceCode: String, findFirstIdentifier: (TSNode, String) -> String?): List<String> =
```

**Suggested Fix:** Wrap in a small class that holds the callback as a constructor parameter, or bind it once at the top-level call using a local nested function. The callback is constant across the recursion; it doesn't need to be an argument.

**Recommendation:** Minor cleanup; not worth a standalone change.

---

## File 7: StringExtractor.kt
**Path:** `src/main/kotlin/de/maibornwolff/treesitter/excavationsite/integration/extraction/extractors/common/StringExtractor.kt`
**Complexity:** 20
**File Size:** 53 RLOC

### Finding 1: Single `when` over 15 sealed-class branches
**Severity:** Low

**Explanation:** The `when` at L21–42 dispatches on 15 `StringFormats` sealed subclasses. Ratio 0.38 (highest in the top 10) is entirely from this density. This is the canonical sealed-class dispatch pattern — Kotlin's compiler enforces exhaustiveness so the ratio is actually a feature. Adding a new format requires touching the sealed class and this file, nowhere else. No finding in the antipattern sense; flagging to contextualize the ratio.

**Line:** 21–42

**Code Snippet:**
```kotlin
21        return when (format) {
22            is StringFormats.Quoted -> { ... }
29            is StringFormats.Template -> StringParser.stripBackticks(text)
30            is StringFormats.TripleQuoted -> ...
...
42        }
```

**Suggested Fix:** None — the density is structural and the exhaustiveness check is enforced by the compiler.

**Recommendation:** Leave as-is.

---

## File 8: CDeclaratorParser.kt (C)
**Path:** `src/main/kotlin/de/maibornwolff/treesitter/excavationsite/languages/c/extractors/CDeclaratorParser.kt`
**Complexity:** 19
**File Size:** 72 RLOC

### Finding 1: Four public entry points with overlapping names
**Severity:** Medium

**Explanation:** The object exposes `findIdentifierInDeclarator`, `extractIdentifierFromDeclaratorField`, `extractFromQualifiedIdentifier`, and `extractFromInitDeclarator`. The boundary between `findIdentifier*` and `extract*From*` isn't evident from the names — in fact all four follow the same pattern (take a node, return its identifier text). A reader has to inspect each body to understand when to use which. This is a naming smell that makes correct usage from outside the file harder than it needs to be.

**Line:** 45–117

**Code Snippet:**
```kotlin
45    fun findIdentifierInDeclarator(node: TSNode, sourceCode: String): String? { ... }
72    fun extractIdentifierFromDeclaratorField(node: TSNode, sourceCode: String): String? {
73        val declarator = node.getChildByFieldName("declarator")
74        return declarator?.let { findIdentifierInDeclarator(it, sourceCode) }
75    }
...
86    fun extractFromQualifiedIdentifier(node: TSNode, sourceCode: String): String? { ... }
114   fun extractFromInitDeclarator(initDeclarator: TSNode, sourceCode: String): String? { ... }
```

**Suggested Fix:** Rename for semantic clarity:
- `findIdentifierInDeclarator` → `identifierOf(declarator)`
- `extractIdentifierFromDeclaratorField` → `identifierOfDeclaratorField(parent)`
- `extractFromQualifiedIdentifier` → `identifierOfQualifiedIdentifier(qualified)`
- `extractFromInitDeclarator` → `identifierOfInitDeclarator(initDeclarator)`

Alternatively, collapse them to a single `identifierOf(node: TSNode, context: NodeContext)` where `NodeContext` is an enum. Current shape is OK for 4 callers, not OK if it grows to 10.

**Recommendation:** Rename at the next touch; low-effort and catches the next caller's confusion early.

---

## File 9: CppTypeHelper.kt
**Path:** `src/main/kotlin/de/maibornwolff/treesitter/excavationsite/languages/cpp/extractors/CppTypeHelper.kt`
**Complexity:** 19
**File Size:** 83 RLOC

### Finding 1: Three near-identical qualified-identifier walkers
**Severity:** Medium

**Explanation:** `extractRightmostSegment` (L55), `extractSingleSegmentScope` (L71), and `extractSecondToLastSegment` (L78) all accept a `qualified_identifier` node and walk it to extract different segments. The first and third share an identical `while (node.type == QUALIFIED_IDENTIFIER) { scope = ...; node = name }` loop — 11 lines of nearly verbatim duplication. This is on the hotpath for every C++ qualified type in the codebase (Issues 2, 3, 4, 5 all call into it).

**Line:** 55–92

**Code Snippet:**
```kotlin
55    fun extractRightmostSegment(qualifiedId: TSNode, sourceCode: String): UsedType? {
56        val scopeSegments = mutableListOf<String>()
57        var node = qualifiedId
58        while (node.type == QUALIFIED_IDENTIFIER) {
59            val scope = node.getChildByFieldName(SCOPE_FIELD)
60            if (!scope.isNull) {
61                scopeSegments.add(TreeTraversal.getNodeText(scope, sourceCode).trim())
62            }
...
78    fun extractSecondToLastSegment(qualifiedId: TSNode, sourceCode: String): UsedType? {
79        val scopeSegments = mutableListOf<String>()
80        var node = qualifiedId
81        while (node.type == QUALIFIED_IDENTIFIER) {
82            val scope = node.getChildByFieldName(SCOPE_FIELD)
83            if (!scope.isNull) {
84                scopeSegments.add(TreeTraversal.getNodeText(scope, sourceCode).trim())
85            }
```

**Suggested Fix:** Extract `private fun walkQualified(qualifiedId, sourceCode): Pair<List<String>, String>` returning `(scopeSegments, leafText)`. Three public methods become one-liner selectors:
- `extractRightmostSegment` = `UsedType(name=leaf, namespacePrefix=scopes)`
- `extractSecondToLastSegment` = `UsedType(name=scopes.last, namespacePrefix=scopes.dropLast(1))`
- `extractSingleSegmentScope` unchanged (it's a different access pattern — immediate scope field only, not the full walk).

**Recommendation:** Small, safe DRY refactor — do it next time this file is touched, since extractions based on these helpers will keep multiplying.

---

## File 10: DeclarationExtractor.kt (C++)
**Path:** `src/main/kotlin/de/maibornwolff/treesitter/excavationsite/languages/cpp/extractors/DeclarationExtractor.kt`
**Complexity:** 18
**File Size:** 118 RLOC

### Finding 1: String-splitting on `::` instead of AST walking
**Severity:** Medium

**Explanation:** `toOutOfClassDeclaration` at L42–58 extracts the qualified declarator text and splits it on `"::"` (L46–49) to identify the class-name prefix. If the declarator ever contains a template instantiation with `::` inside — e.g. `template<class T = std::map<K,V>> void Foo::bar() { }` — the raw-text split would break the declarator apart at the wrong boundary. Tree-sitter can already answer this question structurally via the `qualified_identifier`'s `scope`/`name` fields; mirroring that logic is safer than running String.split on source text.

**Line:** 42–58

**Code Snippet:**
```kotlin
42    private fun toOutOfClassDeclaration(functionDef: TSNode, sourceCode: String): Declaration? {
43        val qualifiedDeclarator = findQualifiedDeclarator(functionDef) ?: return null
44        val segments = TreeTraversal
45            .getNodeText(qualifiedDeclarator, sourceCode)
46            .split(NAMESPACE_SEPARATOR)
47            .map { it.trim() }
48            .filter { it.isNotEmpty() }
49        if (segments.size < 2) return null
50        val className = segments[segments.size - 2]
51        val classPathPrefix = segments.dropLast(2)
```

**Suggested Fix:** Use `CppTypeHelper.extractRightmostSegment` or write a purpose-built walker that iterates the nested `qualified_identifier` structurally via the `scope`/`name` fields. Returns the same `(pathPrefix, className)` pair but robust to `::` appearing inside template arguments.

**Recommendation:** Add a regression test for a template-parameterized out-of-class method before refactoring; current behavior is probably correct for cppcheck but will bite on codebases with heavy std-template usage.

---

### Finding 2: Dual-path extraction with post-merge
**Severity:** Low

**Explanation:** The extractor runs two passes — explicit declarations (L33) and out-of-class method bodies (L34) — then merges them by `(parentPath, name)` (L35, L65–73). The dual-path design is justified by C++'s split-declaration-and-definition convention, but the merge step is easy to overlook when reading the file for the first time. A comment explaining the why would help.

**Line:** 27–36

**Code Snippet:**
```kotlin
27    fun extract(rootNode: TSNode, sourceCode: String): List<Declaration> {
28        val allDeclarationNodes = TreeTraversal
29            .findAllDescendantsOfType(rootNode, *DECLARATION_NODE_TYPES.toTypedArray())
...
33        val explicit = allDeclarationNodes.mapNotNull { toDeclaration(it, sourceCode, nameByStartByte) }
34        val outOfClass = extractOutOfClassDeclarations(rootNode, sourceCode)
35        return mergeDeclarations(explicit + outOfClass)
36    }
```

**Suggested Fix:** Add a brief comment before L35 explaining why the merge is needed (same class can appear in both the header's class body and the .cpp's out-of-class method definitions; used-types accumulate across both).

**Recommendation:** One-line comment, low priority.

---

## Summary

### Metrics Overview

| File | Complexity | Lines | Ratio | Critical | High | Medium | Low |
|------|-----------:|------:|------:|---------:|-----:|-------:|----:|
| UsedTypeExtractor.kt (cpp) | 53 | 309 | 0.17 | 0 | 1 | 1 | 1 |
| LanguageRegistry.kt | 34 | 77 | 0.44 | 0 | 0 | 1 | 0 |
| StringParser.kt | 28 | 85 | 0.33 | 0 | 0 | 1 | 1 |
| TreeTraversal.kt | 24 | 99 | 0.24 | 0 | 0 | 1 | 1 |
| RealLinesOfCodeCalc.kt | 21 | 104 | 0.20 | 0 | 1 | 0 | 1 |
| PatternCaptureExtractor.kt (python) | 21 | 73 | 0.29 | 0 | 0 | 0 | 1 |
| StringExtractor.kt | 20 | 53 | 0.38 | 0 | 0 | 0 | 1 |
| CDeclaratorParser.kt (c) | 19 | 72 | 0.26 | 0 | 0 | 1 | 0 |
| CppTypeHelper.kt | 19 | 83 | 0.23 | 0 | 0 | 1 | 0 |
| DeclarationExtractor.kt (cpp) | 18 | 118 | 0.15 | 0 | 0 | 1 | 1 |
| **Total** | — | — | — | **0** | **2** | **7** | **7** |

### Overall Assessment

The codebase is in **healthy** shape at the file level — all top-10 files sit well below the "moderate" complexity threshold (< 100), the longest file is 309 RLOC, and there are no god classes, nested loops, or deep-nesting issues. The dominant pattern in the quality data is the high complexity/line ratio (0.20–0.44) across AST-walking files, which is structural: tree-sitter extractors are inherently dense dispatch tables over node types and the compiler-enforced exhaustiveness is a feature, not a smell. The two high-severity findings are actionable and narrow: `UsedTypeExtractor` hits detekt's 15-function ceiling and blocks further C++ category additions without a file split; and `RealLinesOfCodeCalc` carries 8 mutable state fields that make the calculator hard to isolate-test. Seven medium findings cluster around duplication (four ancestor walkers, three qualified-identifier walkers, two recursive descenders, two dispatch tables) that would benefit from small DRY refactors when those files are next touched. The newly-added C++ extraction code from this session follows the same patterns as Java/Kotlin/C# extractors and introduces no new antipatterns. Priority refactor targets: `UsedTypeExtractor` split (unblocks further C++ work) and `RealLinesOfCodeCalc` state encapsulation (improves testability of a core metric). **Verdict: healthy.**
