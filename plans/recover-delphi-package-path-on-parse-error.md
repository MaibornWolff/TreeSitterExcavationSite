---
name: Recover Delphi Package Path When tree-sitter-pascal Fails to Wrap Unit
issue: ~
state: complete
version: ~
---

## Goal

Make `PackageExtractor` resilient to tree-sitter-pascal parse failures so that files which the parser collapses into a top-level `ERROR` node still produce a correct `packagePath`. Today, 22 Spring4D root-level orphan declarations stem from this single failure mode: the file contains `unit Foo;` and the parser emits a `kUnit` keyword and a `moduleName` token, but those tokens are inside an `ERROR` node, so the current direct-child lookup in `PackageExtractor` returns `emptyList()` and downstream declarations end up with an empty package path.

## Background

Reproduction confirmed by an investigation subagent (existing failing test in `DelphiDependencyTest.kt` → `SpringComparersBugReproduction.should extract package path from real Spring Comparers pas file`):

- `tree-sitter-pascal` 0.10.2 cannot wrap files like `Spring.Comparers.pas`, `Spring.pas`, `Spring.Utils.pas`, and the `Spring.Collections.*` family in a top-level `unit` node — most likely triggered by dense inline `asm … end;` blocks gated by `{$IF}/{$IFDEF}/{$ELSE}/{$IFEND}` directives.
- The parse output for such files is `root → [comments…, pp '{$I Spring.inc}', ERROR]`. Inside the ERROR: `kUnit`, `moduleName "Spring.Comparers"`, `interface`, `kImplementation`, `declUses`, `defProc`, `declType`, etc.
- `PackageExtractor.extract` (`PackageExtractor.kt:29`) does `rootNode.children().firstOrNull { it.type in CONTAINERS } ?: return emptyList()` and short-circuits.
- `DeclarationExtractor.extract` (`DeclarationExtractor.kt:69`) uses `findAllDescendantsOfType` and successfully descends into the ERROR subtree, emitting 12+ declarations per affected file.
- DC then builds `Path(packagePath + parentPath + name)`. With `packagePath = []`, every declaration becomes a project-tree root, and any nested declaration (e.g. `TStringComparer.TOrdinalCaseInsensitiveStringComparer`) is grouped under a synthetic root container (`TStringComparer` here) that has no relationship to the real `Spring → Comparers` namespace.

The two extractors disagree about how to handle malformed parses; this plan brings `PackageExtractor` into line with `DeclarationExtractor`'s recursive-descent strategy.

## Tasks

### 1. Add a tree-walking fallback to `PackageExtractor.extract`

`languages/delphi/extractors/PackageExtractor.kt`.

Three-stage lookup:

1. **Current behaviour** — direct child of root is one of `unit`, `program`, `library`. Read its `moduleName` field exactly as today. (Happy path, no behavior change.)
2. **Descendant fallback** — if step 1 fails, run `TreeTraversal.findAllDescendantsOfType(rootNode, CONTAINERS)` and use the first hit. Covers cases where the wrapper node exists but is nested under an ERROR.
3. **Keyword-token fallback** — if step 2 fails too, scan descendants for the keyword tokens `kUnit`, `kProgram`, `kLibrary`. For the first hit, look at its parent's children (or the next named sibling, depending on what tree-sitter emits inside the ERROR) for an adjacent `moduleName` node and extract the dotted path from its identifier children. This is what unblocks `Spring.Comparers.pas` and similar files where the wrapper node never appears at all.

If all three stages fail, return `emptyList()` (current behaviour for genuinely package-less files).

Add a constant set `KEYWORD_FALLBACK = setOf("kUnit", "kProgram", "kLibrary")` near the existing `CONTAINERS`.

Document the rationale in the function's KDoc — anyone touching this in the future needs to know the fallback is there because tree-sitter-pascal's error recovery hides the wrapper but leaves the keyword + moduleName intact.

### 2. Convert / extend the existing investigation tests in `DelphiDependencyTest.kt`

The investigation subagent added `@Nested inner class SpringComparersBugReproduction` at `DelphiDependencyTest.kt:1768`. It contains four tests today: three minimal-shape reproductions that pass (proving the bug isn't the `{$I Spring.inc}` directive, the `{$O+,W-,Q-,R-}` directive, or the nested-record shape in isolation) and one real-file test that fails. Plan of action:

- **Rename** the `@Nested` class to `PackageExtractionRobustness` and re-position it next to the existing `PackageExtraction` group so it reads as a normal regression suite, not a bug-tracker artifact. Drop the "BugReproduction" framing — once this fix lands, these are regression tests.
- **Keep all four existing tests as-is.** The three minimal-shape tests are valuable as negative-control coverage (confirming the fallback isn't accidentally engaged for happy-path inputs); they passed before the fix and must continue to pass after. The real-file test (`should extract package path from real Spring Comparers pas file`) flips from failing to passing.
- **Add one new ERROR-wrap test** using the smallest synthetic Pascal snippet that reliably triggers tree-sitter-pascal's ERROR-wrap behaviour (try the `asm … end;` + `{$IFDEF}` combination identified by the investigation). Goal: assert `packagePath == ["Foo"]` even when the wrapper node is collapsed into an ERROR. Document the trigger in the test comment so future readers know why a minimal-looking snippet is required to be exactly this shape.
- **Add a `program` / `library` variant test** of the keyword-fallback if the same ERROR-wrap can be reproduced for those module forms. If tree-sitter-pascal's error recovery makes that impractical, skip the test and document the deferral in the new `PackageExtractionRobustness` group's KDoc / class-level comment.

### 3. Update documentation

- `CHANGELOG.md`: add a new `### Fixed` subsection under `[Unreleased]` (the section currently has `### Added` and `### Changed` only). Entry text: "PackageExtractor now recovers the unit/program/library name from tree-sitter-pascal parse-error wrapping, so files containing unsupported asm/IFDEF combinations no longer produce declarations with an empty package path."
- `KNOWN_ISSUES.md`: if it mentions parse-failure handling, update; otherwise no change.
- The existing `delphi-edge-cases.md` files in DC and TSE root don't currently mention this — no update needed.

### 4. Verify on Spring4D

The success criterion is end-to-end: re-running DependaCharta against `/Users/christian.huehn/Projects/SHC-Tools/spring4d/Source/` (with TSE composite build still wired in DC) should drop the orphan-root-container count from 22 to 0 (or near-0 if any other parse-pathology files lurk).

Steps to verify locally:
1. `cd /Users/christian.huehn/Projects/SHC-Tools/DependaCharta/analysis && /tmp/claude-1000/gradle-dist/gradle-9.1.0/bin/gradle fatJar` (with `dangerouslyDisableSandbox: true`).
2. `rm -rf /Users/christian.huehn/Projects/SHC-Tools/DependaCharta/spring4d-source-only.cg.json && java -jar build/libs/dependacharta.jar -d /Users/christian.huehn/Projects/SHC-Tools/spring4d/Source/ -o /Users/christian.huehn/Projects/SHC-Tools/DependaCharta/spring4d-source-only.cg.json`
3. Apply the unresolved-externals filter from before (Python one-liner that drops simple-name root leaves) and count remaining non-`Spring` containers. Target: **0**.
4. Confirm `Spring.Comparers`, `Spring.Utils`, `Spring.Collections.Base`, etc. now exist as proper subdirectories of `Spring` in the JSON tree.

## Steps

- [x] Dump AST for Spring.Comparers.pas to confirm exact ERROR-subtree shape (Task 1 prep)
- [x] Implement the three-stage fallback in `PackageExtractor.extract` (Task 1)
- [x] Rename `SpringComparersBugReproduction` to `PackageExtractionRobustness`, keep its 4 existing tests, re-position it next to `PackageExtraction` (Task 2)
- [x] Update real-file test to use repo-relative path (`./spring4d/Source/Base/Spring.Comparers.pas`) (Task 2)
- [x] Document deferral of synthetic ERROR-wrap regression test in class KDoc — 12 candidate shapes probed, none reproduced root-level ERROR-wrap (Task 2)
- [x] Document deferral of `program` / `library` keyword-fallback variant test in class KDoc (Task 2)
- [x] Run `./gradlew test --tests "*DelphiDependencyTest*"` — all Delphi dependency tests green
- [x] Run `./gradlew test` — full TSE suite green, no regressions in other languages (also fixed two pre-existing `.contains()` weak-assertion violations on lines 176/234 inherited from the moved test class)
- [x] Run `./gradlew ktlintCheck` — passes
- [x] Update `CHANGELOG.md` `[Unreleased]` → `Fixed` (Task 3)
- [ ] (Skipped per user request) Build DC fat jar against the fixed TSE and re-run analysis on Spring4D (Task 4)
- [ ] (Skipped per user request) Confirm orphan-root-container count drops from 22 to 0 (Task 4)
- [ ] (Skipped per user request) Confirm `Spring.Comparers`, `Spring.Utils`, and the affected `Spring.Collections.*` units appear as proper subdirectories in the JSON tree (Task 4)

## Notes

- **Why fix only `PackageExtractor` and not also gate `DeclarationExtractor`?** Because `DeclarationExtractor`'s recursive descent is doing the right thing — it recovers real declarations from a partially-parsed file. Suppressing those would lose information that's perfectly usable once the package path is restored. The asymmetry is what creates the bug; aligning the two by making `PackageExtractor` *also* tolerant is the minimum-information-loss fix.
- DC's `BaseLanguageAnalyzer` skips the implicit self-wildcard import when `packagePath` is empty (`BaseLanguageAnalyzer.kt:17-21`). Once the fallback restores `packagePath`, that wildcard re-appears automatically — no DC-side change is needed.
- This is a TSE-only change. No DC code changes; only an end-to-end re-run to verify.
- The investigation revealed the parser failure is sensitive to file content well past the `unit Foo;` line. The minimal-shape repro test in Task 2 is best-effort — if we cannot construct a small snippet that reliably flips tree-sitter-pascal into ERROR-wrap mode, the real-file `Spring.Comparers.pas` test is the authoritative regression guard.
- Once this fix is released, DC's TSE dependency version in `analysis/build.gradle.kts` will need to be bumped (currently `v0.6.0`). That is out of scope for this plan but worth noting on the DC side.
