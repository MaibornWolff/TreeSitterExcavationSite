---
name: cpp-extraction-followups
issue: TBD
state: todo
version: 1
tse_branch: feat/cpp-dependency-support
dc_branch: feat/cpp-dependency-integration
---

## Goal

Track the remaining C++ extraction gaps identified after round 11 of dc-compare on cppcheck (44.4% match rate) and the planned refactor prerequisite. Split out from `add-cpp-dependency-support.md` because the base migration is functionally complete; this file covers incremental post-migration work.

## Context — where we stand

Base migration is green:
- **TSE `feat/cpp-dependency-support`** at `634a77d` — build passes, all tests green.
- **DC `feat/cpp-dependency-integration`** at `20ea8a4` — CppAnalyzer rewritten to call TSE, namespacePrefix + selfWildcard fixes landed.
- **Composite-build wiring uncommitted on DC** — `analysis/settings.gradle.kts` + `analysis/build.gradle.kts` must be reverted before any DC merge.

Round 11 cppcheck dc-compare numbers:
- matched: 1091
- main-only: 1364
- feat-only: 474
- match rate: 44.4%

Bucket analysis of the 1364 main-only:
- **167 misdirection** — resolver picks different same-simple-name candidate. Inherent to DC's loose groupBy; unfixable without changing DC resolver.
- **~682 DC misattribution** — DC's `BodyProcessor.addTypesAndDependenciesToRelatedNode` dumps types onto `this.lastOrNull()` in multi-class files (`lib/valueflow.cpp` alone accounts for 411). Known DC legacy bug; we don't mirror it.
- **~503 genuine extraction gaps** across 111 source nodes — the scope of this follow-up.

Ceiling against cppcheck is ~50-55% match rate without mirroring DC bugs; everything in this plan targets the remaining ~503 real gaps.

## Prerequisite — refactor session (MUST come first)

From `Reports/TreeSitterExcavationSite-analysis-2026-04-23.md`:

**`UsedTypeExtractor.kt` is at detekt's 15-function ceiling.** Any new extraction category triggers the `TooManyFunctions` violation and forces folding into existing branches (as happened with Issue 4 throw-handling merging into `extractInstantiationTypes`). Adding more patterns blind will make the file progressively harder to maintain.

The refactor must:

1. **Split `UsedTypeExtractor` into category sub-extractors** under `languages/cpp/extractors/usedtypes/`:
   - `CallExpressionTypeExtractor` — CALL_EXPRESSION, NEW_EXPRESSION, THROW_STATEMENT, FIELD_INITIALIZER_LIST
   - `DeclarationTypeExtractor` — FIELD_DECLARATION, DECLARATION, CAST_EXPRESSION, SIZEOF, ALIGNOF
   - `SignatureTypeExtractor` — FUNCTION_DEFINITION, FUNCTION_DECLARATOR, BASE_CLASS_CLAUSE, TRAILING_RETURN
   - `AliasConstraintExtractor` — TYPE_DEFINITION, ALIAS_DECLARATION, template `requires` constraints
   - `ClassScopeExtractor` — FRIEND_DECLARATION, in-class USING_DECLARATION
   Each file keeps its own constants; the root `UsedTypeExtractor.extract()` becomes a concat of 5 sub-extractor calls in DC-legacy order.

2. **Unify qualified-identifier walkers in `CppTypeHelper`**. Extract a private `walkQualified(qualifiedId, sourceCode): Pair<List<String>, String>` returning `(scopeSegments, leafText)`. The three public methods (`extractRightmostSegment`, `extractSecondToLastSegment`, `extractSingleSegmentScope`) become one-line selectors over that result. Drops 11 lines of duplicated loop bodies.

3. **Unify `TreeTraversal` ancestor walkers**. Extract private `walkAncestors(node: TSNode): Sequence<TSNode>`; `hasAncestorOfType`, `hasAncestorOfTypes`, `findAncestorOfType`, `isDescendantOf` become one-line `any`/`firstOrNull` filters over the sequence.

4. **Use AST-structural walking in `DeclarationExtractor.toOutOfClassDeclaration`** instead of `String.split("::")` on the declarator text (L44–49). Template-argument types containing `::` can split at the wrong boundary. Replace with `CppTypeHelper`'s qualified-walker.

5. Optional (not blocking): encapsulate state in `RealLinesOfCodeCalc` (8 mutable fields → two data classes). Unrelated to C++ work but the quality report flagged it High severity.

**Success criteria:**
- `./gradlew build` green after each task
- `UsedTypeExtractor` under detekt's function-count threshold with headroom (target ≤ 10 functions)
- All existing tests still pass — no behavior changes
- No dc-compare run needed; refactor is pure restructuring

**Commits**: one per task (5 or 6 total, small and reviewable).

## Tasks — extraction patterns (after refactor)

### 1. Issue 6 — primitive_type as type node — DEFERRED (measurement 2026-04-23)

**Status: deferred on cppcheck.** Measured impact is exactly 0 matched deps.

Direct node query over the R11 dc-compare output (cppcheck main vs feature):

- `main-only deps with primitive simple name: 0 / 1364`
- `feat-only deps with primitive simple name: 0 / 474`

Primitive list tested: `int, long, short, char, float, double, bool, void, auto, signed, unsigned, size_t, ssize_t, ptrdiff_t, intptr_t, uintptr_t, uint{8,16,32,64}_t, int{8,16,32,64}_t, wchar_t, char{16,32}_t`.

DC legacy does emit `UsedType("int")` etc. via `typeDeclarations()`, but none of those resolve to a project node in cppcheck — there is no cppcheck file or class named `int`, `size_t`, etc. Neither side produces primitive-target Dependencies, so adding `primitive_type` to TSE's `TYPE_NODE_TYPES` would not move the match count on this corpus.

**Re-measure before implementing on any future corpus.** A project that typedefs primitives (`typedef int Counter;` used inline) could flip the result — the measurement is cppcheck-specific.

DC's `typeDeclarations()` list includes `primitive_type` alongside the three types we already handle; if this is ever reopened, the change is still the one-liner: extend `CppTypeHelper.TYPE_NODE_TYPES` and add a `PRIMITIVE_TYPE` branch in `extractType`.

### 2. Issue 7 — nested-type constant references as UsedType

Dominant pattern in the 503 real gaps. Top missing simple names that correspond to nested classes:
- `FileLocation` (17) — inside `ErrorMessage`
- `simpleMatch` (16) — `Token::simpleMatch` (already extracted; investigate why 16 still main-only)
- `Value` (10) — inside `ValueFlow`
- Many others

Sample: `CppCheckExecutor → SuppressionList.ErrorMessage` (main-only, target exists in feature). Source usage is likely something like `SuppressionList::ErrorMessage errMsg;` as a variable type, or `return SuppressionList::ErrorMessage(...)`.

Investigate whether our existing `extractRightmostSegment` handles this correctly — might be a resolver-side issue rather than extraction.

### 3. Issue 8 — `nonneg int`, sized specifiers, typedef aliases

DC probably treats some macro / typedef aliases as types. Sample the remaining gap for patterns like `nonneg int`, `cppcheck::stdtype`, etc. Worth a grep sweep on cppcheck before designing a fix.

### 4. Issue 9 — template specializations as declarations

Previously documented (plan 9372naa): DC legacy synthesizes separate declarations for `Catch.StringMaker<std::string>`-style specializations. Currently we extract just the base template. Requires a `template_declaration` handler that emits one `Declaration` per specialization.

Only visible on Catch2 (amalgamated); probably small impact on cppcheck. Deferred unless we return to Catch2 as a baseline.

### 5. Issue 10 — inspect `lib.valueflow_cpp.ValueFlow` 22-gap cluster

Single highest-gap real-gap source. ValueFlow is a namespace-scoped struct with many free functions around it. Our extractor sees ValueFlow's direct members; DC picks up function types that reference ValueFlow. Investigate whether there's an out-of-class method pattern we're missing.

## Sampling data (captured 2026-04-23 from R11 feature output)

### Top 20 missing target simple names (real-gap sources only)

```
  22  Function             22  ValueType            18  Token
  17  FileLocation         17  Settings             16  Type
  16  Variable             16  simpleMatch          15  Path
  15  ErrorMessage         15  Scope                14  Library
  12  XMLElement           12  ValueFlow            11  InternalError
  10  Entity               10  value                10  TokenList
  10  ProgramMemoryState   10  ProgramMemory
```

### Top 10 real-gap sources

```
  22  lib.valueflow_cpp.ValueFlow
  15  lib.valueflow_cpp.ConditionHandler
  14  lib.valueflow_cpp.LifetimeStore
  13  CTU.FileInfo
  13  lib.symboldatabase_cpp.Scope
  11  cli.cppcheckexecutor_cpp.StdLogger
  11  lib.clangimport_cpp.clangimport
  11  lib.ctu_cpp.CTU
  10  lib.symboldatabase_cpp.ValueType
   9  lib.forwardanalyzer_cpp.AsAnalyze
```

### Bucket breakdown of 1364 main-only (for pattern hunting)

```
not-emitted by source file (top 12):
   411  lib.valueflow_cpp          (mostly DC misattribution — skip)
    84  lib.symboldatabase_cpp     (mix of misattribution + real)
    49  lib.vf_analyzers_cpp
    44  lib.checkclass_cpp
    44  lib.checkother_cpp
    33  CTU.FileInfo
    26  lib.checkmemoryleak_cpp
    25  lib.importproject_cpp
    21  lib.programmemory_cpp
    19  CheckClass_internal.MyFileInfo
    19  lib.preprocessor_cpp
    17  lib.suppressions_cpp
```

## Steps

- [ ] Refactor Task 1: split `UsedTypeExtractor` into sub-extractors
- [ ] Refactor Task 2: unify `CppTypeHelper` qualified walkers
- [ ] Refactor Task 3: unify `TreeTraversal` ancestor walkers
- [ ] Refactor Task 4: AST-structural walk in `DeclarationExtractor.toOutOfClassDeclaration`
- [ ] Refactor Task 5 (optional): encapsulate `RealLinesOfCodeCalc` state
- [x] Issue 6: primitive_type as type node — DEFERRED on cppcheck (0/1364 main-only, 0/474 feat-only have primitive simple names; re-measure on future corpora before implementing)
- [x] Issue 7: nested-type constant references — PARTIAL. Fixed namespaced-template-generic extraction (TSE 8576afd) + nested-generic namespace wildcards (DC 1ff96b4). R14 cppcheck: 1091→1116 matched (+25), 44.4%→45.5%. Remaining gaps (Token 125, Function 74, ValueType 57…) are dominated by DC misattribution/misdirection, not extraction. Exemplar CppCheckExecutor→ErrorMessage.FileLocation stayed main-only because the source is a DC dump-to-lastNode victim (the usage is in a free function, not a CppCheckExecutor member).
- [x] Issue 8: macro/typedef aliases — SKIPPED. After Issue 7 landed, remaining top-20 main-only target simple names are all project class names (Token, Function, ValueType, TokenList, etc.); no macro/typedef pattern stands out in the gap distribution.
- [x] Issue 9: template specializations — DEFERRED. Only relevant to Catch2-style amalgamated headers; cppcheck does not exhibit the pattern at any scale.
- [x] Issue 10: ValueFlow 22-gap cluster — CLOSED. Investigated 24 main-only deps for lib.valueflow_cpp.ValueFlow: all fall into existing misattribution + misdirection buckets (sibling classes like ConditionHandler/Lambda/LifetimeStore dumped via DC's lastOrNull() bug; Token/Variable/Scope/Function/Type/ValueType resolving to simplecpp.* alternates instead of lib.*). No new extraction pattern.
- [ ] Final dc-compare R_n and DC-side cleanup (Task 9 of base plan — update 8 CppAnalyzerTest failures, revert composite-build wiring, merge TSE + DC)

## Next session pick-up — wrap-up status

### Where we are now

- **TSE `feat/cpp-dependency-support`** at `e8018a9` — build green, 4 namespaced-template generic tests added and passing.
- **DC `feat/cpp-dependency-integration`** at `1ff96b4` — Fix 2 (nested-generic wildcards) committed; composite-build wiring in `analysis/settings.gradle.kts` + `analysis/build.gradle.kts` still uncommitted (must revert before merge).
- **cppcheck dc-compare R15**: matched 1118, main-only 1337, feat-only 507, **match rate 45.6%** (R11 baseline 44.4%, total +27 matched across the session).

### Issue 7 extension that landed mid-wrap-up

Investigating the CppAnalyzerTest failures surfaced two additional tree-sitter-cpp parsing quirks that `8576afd`'s fix didn't cover:

1. Templated call callees like `std::make_shared<T>()` — tree-sitter parses the leaf of the qualified_identifier as `template_function` (not `template_type`), with `name=identifier` (not `type_identifier`).
2. Nested template-argument types like `CreatureRepository<X, Y>` inside `list<CreatureRepository<X, Y>>` also parse as `template_function` even in a type context; bare class names used as template args appear as direct `identifier` nodes (not wrapped in `type_descriptor`).

Commit `e8018a9` extends `CppTypeHelper` to handle both via `extractTemplateLike` (accepts `TYPE_IDENTIFIER` or `IDENTIFIER` as name) and looser `extractGenericArgument` filtering. Gained +2 matched on cppcheck (R14→R15), no feat-only regression.

### Remaining wrap-up work for next session

#### 1. CppAnalyzerTest — 8 failing, categorized

All 8 failures are in `DependaCharta/.../analyzers/cpp/CppAnalyzerTest.kt`.

**Category (a) — tests that expect primitive_type extraction** (4 tests):
- `should recognize types of function parameters correctly` — expects `int64_t`, `uint`. `int64_t` parses as `primitive_type` in tree-sitter-cpp; TSE currently skips it.
- `should extract constructor parameter types correctly` — expects `void`.
- `should extract method return types correctly` — expects `void`, and `string`/`CreatureEntity` which are extracted as *nested generics* not as standalone (see category b).
- `should recognize unsigned statement without type as int` and `should recognize signed statement without type as int` — expect "int" as the extracted type name for bare `unsigned`/`signed`. Tree-sitter parses these as `sized_type_specifier`, and DC legacy normalized them to "int".

**Category (b) — flattening divergence** (2 tests, also overlap with some (a) tests):
- `should extract field types correctly` — expects `shared_ptr[TEntity]` as a standalone UsedType. TSE keeps it nested inside `unordered_set` and `set`; `Node.resolveTypes` flattens via `Type.containedTypes()` at resolve time, but pre-resolution `usedTypes` set stores only top-level entries.
- `should recognize constructor call correctly` — expects `Mu`, `Nu` as standalone. Same pattern; they appear nested in `unique_ptr[Mu]`, `shared_ptr[Nu]`, `make_unique[Mu]`, `make_shared[Nu]`.

**Category (c) — multiline include backslash continuation** (1 test):
- `should recognize multiline include statements` — source uses `#include "dir/\` + next-line continuation. TSE doesn't strip `\\\n\s*` from the raw include path text, producing `dir.\ subdir.AnotherCreatureRepository_h` (literal backslash + spaces).

#### 2. Primitive-type extraction — three graduated options (from prior discussion)

Tried the minimal (primitive_type only) during this session; it added `void`/`int`/etc. to TSE's extraction which broke **29 TSE CppDependencyTest** tests that use `containsExactly` and didn't expect primitives. Reverted because the test-update cascade was larger than expected for a "minimal" change.

Graduated options for next session:

- **A (minimal)**: Add `PRIMITIVE_TYPE` to `CppTypeHelper.TYPE_NODE_TYPES` + extractType branch. Fixes 3 of the 4 category (a) tests (leaves `unsigned`/`signed`-as-int). **Cost: ~5 lines in TSE, but also ~29 TSE test-assertion updates** to add primitives to expected `containsExactly` sets.
- **B (medium)**: A + `sized_type_specifier` support. Fixes the 4th primitive test but with `"unsigned"`/`"signed"` names, not `"int"`. One more node-type constant + extractType branch. Tests need assertion tweaks.
- **C (medium+)**: B plus the DC-legacy "bare unsigned/signed == int" normalization. All 4 tests pass as-written. Adds language-quirk normalization to TSE which is semantically questionable.

R15 data shows primitive-name resolution impact on cppcheck is 0 matched deps (we measured this as Issue 6). Primitives as UsedTypes don't resolve to project nodes because cppcheck has no file/class named `int`/`void`/`size_t`. The feat-only risk is statistically low (empty-wildcard would need to match a primitive's simple name against a project node containing that name as substring — rare). But other corpora that typedef primitives could change the calculation.

**Recommended sequence for next session**:
1. Decide on A / B / C for primitive extraction.
2. If A or B: update the TSE tests (~29 assertions adding primitives) and re-run dc-compare to quantify any feat-only drift on cppcheck.
3. Update the 8 DC `CppAnalyzerTest` tests:
   - Category (a): pass automatically once A/B/C is picked (A fixes 3, B/C fix all 4).
   - Category (b): update assertions to drop standalone expectations for types that appear only as nested generics (e.g., assert `shared_ptr.genericTypes == [TEntity]` instead of looking for bare `shared_ptr[TEntity]`).
   - Category (c): `@Disabled("TSE extraction gap: multiline include continuation")` with a TODO, or optionally fix TSE to strip `\\\n\s*` from `ImportExtractor`'s include path text.
4. Revert DC's composite-build wiring in `analysis/settings.gradle.kts` + `analysis/build.gradle.kts`.
5. Merge TSE, tag release.
6. Update DC's JitPack TSE dep to the new tag.
7. Merge DC.

#### 3. Verify state before continuing (repeat Step 1 from top of this plan)

```bash
cd C:/Users/ChristianSpa/IdeaProjects/DCTSE/TreeSitterExcavationSite
git branch --show-current         # feat/cpp-dependency-support
git log --oneline -5              # top should be e8018a9 feat(cpp): handle template_function leaves ...
./gradlew build                   # green

cd ../DependaCharta
git branch --show-current         # feat/cpp-dependency-integration
git log --oneline -5              # top should be 1ff96b4 feat(cpp): synthesize wildcards from nested generic ...
grep -c "includeBuild" analysis/settings.gradle.kts   # 1 (still-uncommitted composite wiring)
grep "TreeSitterExcavationSite\|treesitter-excavationsite" analysis/build.gradle.kts
```

If the composite wiring is missing, restore per the instructions at the top of this plan.

## How to pick up next session

### Step 1 — verify state

```bash
cd C:/Users/ChristianSpa/IdeaProjects/DCTSE/TreeSitterExcavationSite
git branch --show-current          # should be feat/cpp-dependency-support
git log --oneline -5
./gradlew build                     # should be green

cd ../DependaCharta
git branch --show-current          # should be feat/cpp-dependency-integration
grep -c "includeBuild" analysis/settings.gradle.kts   # should be 1 (composite wiring present)
grep "TreeSitterExcavationSite" analysis/build.gradle.kts
# should show "de.maibornwolff.treesitter.excavationsite:treesitter-excavationsite"
# (composite-build dependency, NOT JitPack)
```

If composite-build wiring is missing (happens if DC was checked out to main for debugging), restore:

```kotlin
// analysis/settings.gradle.kts — append:
includeBuild("../../TreeSitterExcavationSite")

// analysis/build.gradle.kts — replace TSE dep line:
implementation("de.maibornwolff.treesitter.excavationsite:treesitter-excavationsite")
```

### Step 2 — run refactor

Start with the refactor tasks above. Each task is an independent commit. No dc-compare between refactor commits; refactor is pure restructuring and must not change behavior.

After all refactor commits, confirm:
- `./gradlew build` green
- `UsedTypeExtractor` function count under detekt threshold with headroom
- Full test suite passes

### Step 3 — run dc-compare baseline (R12)

After the refactor, re-run cppcheck dc-compare to confirm no behavior change:

```bash
cd ../DependaCharta/analysis
./gradlew fatJar
java -jar build/libs/dependacharta.jar -d "../../cppcheck" -o "../../dc-compare/feature" -f analysis
cd ../../TreeSitterExcavationSite
node -e "$(cat << 'EOF'
const fs = require('fs');
const m = JSON.parse(fs.readFileSync('../dc-compare/main/analysis.cg.json','utf8'));
const f = JSON.parse(fs.readFileSync('../dc-compare/feature/analysis.cg.json','utf8'));
const mL = Object.keys(m.leaves), fL = Object.keys(f.leaves);
let mD=0, fD=0;
Object.values(m.leaves).forEach(v => mD += Object.keys(v.dependencies||{}).length);
Object.values(f.leaves).forEach(v => fD += Object.keys(v.dependencies||{}).length);
const shared = mL.filter(k => fL.includes(k));
let matched=0, mainOnly=0, featOnly=0;
for (const k of shared) {
  const md = Object.keys(m.leaves[k].dependencies || {});
  const fd = Object.keys(f.leaves[k].dependencies || {});
  matched += md.filter(d => fd.includes(d)).length;
  mainOnly += md.filter(d => !fd.includes(d)).length;
  featOnly += fd.filter(d => !md.includes(d)).length;
}
console.log('nodes:', mL.length, 'vs', fL.length);
console.log('shared', shared.length, ': matched', matched, ', main-only', mainOnly, ', feat-only', featOnly);
EOF
)"
```

Expected: R12 numbers equal R11 (1091/1364/474) exactly. Any drift means the refactor changed behavior — investigate before proceeding to Issue 6.

### Step 4 — tackle extraction issues

Work the Issues 6-10 list in order, TDD-style, measuring dc-compare after each. Stop when match rate approaches ~50-55% ceiling or when individual issues yield <5 matched.

## What NOT to do

- Don't add more extraction categories directly to `UsedTypeExtractor.kt` before the split — detekt will block you and you'll end up folding into existing branches like the throw/arg-list handling did.
- Don't try to chase misattribution or misdirection counts — those are DC legacy bugs we explicitly don't mirror.
- Don't commit DC's composite-build wiring (`analysis/settings.gradle.kts`, `analysis/build.gradle.kts`) — it must be reverted before DC merge.
- Don't push DC `feat/cpp-dependency-integration` to remote before completing Task 9 of the base plan (9 pre-existing `CppAnalyzerTest` failures need updating).

## Notes

- Three DC legacy bugs identified and documented in `.claude/rules/dependency-migration.md`:
  1. Tree-sitter parser returns null for many `.h` files (main has 2 `_h` nodes, feature has 179)
  2. Empty-namespace wildcard + `String.contains("")` gives resolver a hidden simple-name fallback
  3. `BodyProcessor.addTypesAndDependenciesToRelatedNode` dumps onto `lastOrNull()` — misattributes types to most-recently-added node
- Match-rate ceiling against cppcheck main: ~50-55% without mirroring DC bugs. This is the realistic cap for any correctness-respecting migration.
- Quality report at `Reports/TreeSitterExcavationSite-analysis-2026-04-23.md` — read the two High-severity findings (UsedTypeExtractor function ceiling, RealLinesOfCodeCalc state) before the refactor.
- Plan `add-cpp-dependency-support.md` holds the full session history (R5 → R11) and the three DC-bug discoveries. Don't re-document those here.
