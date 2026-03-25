Compare DependaCharta analysis output between `main` (legacy dependency analysis) and the current DC branch to verify identical results after migrating a language to TSE.

**Usage**: `/dc-compare <test-repo-path>` (e.g., `/dc-compare ../commons-lang`)

The test repo to analyze is: `$ARGUMENTS`

## Prerequisites

- **DC repo**: sibling directory `../DependaCharta` (relative to TSE repo root)
- **TSE repo**: this repo (current working directory)
- **Test repo**: provided as argument — a medium-sized repo in the language being migrated
- **DC branch**: the current checked-out branch in DC (this is the branch integrating TSE for the language being migrated)
- DC `main` is always the **golden standard** — any differences mean TSE needs fixing

## Before starting

Ask the user where to save the temporary comparison output files (two JSON files + normalized versions). Suggest a default like `../dc-compare`. Store the chosen path as `OUTPUT_DIR` and use it throughout.

## Steps

### 1. Generate golden standard (DC main)

```bash
cd ../DependaCharta

# Remember current branch to switch back later
CURRENT_BRANCH=$(git branch --show-current)

git checkout main
cd analysis
./gradlew fatJar
java -jar build/libs/dependacharta.jar -d "$ARGUMENTS" -o $OUTPUT_DIR/main -f analysis
cd ..
```

### 2. Generate TSE-based output (DC feature branch with local TSE)

Switch DC back to its feature branch. Temporarily add composite build to use local TSE instead of JitPack:

```bash
git checkout $CURRENT_BRANCH
```

In `analysis/settings.gradle.kts`, add:
```kotlin
includeBuild("../../TreeSitterExcavationSite")
```

In `analysis/build.gradle.kts`, change the TSE dependency line only — **keep the JitPack repository** (TSE has transitive dependencies on JitPack, e.g., tree-sitter-abl):
```kotlin
// Change this:
implementation("com.github.MaibornWolff:TreeSitterExcavationSite:<commit-hash>")
// To this:
implementation("de.maibornwolff.treesitter.excavationsite:treesitter-excavationsite")
```

Then build and run:
```bash
cd analysis
./gradlew fatJar
java -jar build/libs/dependacharta.jar -d "$ARGUMENTS" -o $OUTPUT_DIR/feature -f analysis
cd ..
```

**Important**: After generating the output, revert the `settings.gradle.kts` and `build.gradle.kts` changes (don't commit composite build config).

### 3. Compare the two JSON files

Use Node.js to do a semantic JSON comparison:
- Recursively sort all object keys (key order is irrelevant for DC's visualization)
- Preserve array order (tree structure ordering matters for visualization)
- Write normalized versions, then diff

```bash
node -e "
const fs = require('fs');
function sortKeys(obj) {
  if (Array.isArray(obj)) return obj.map(sortKeys);
  if (obj && typeof obj === 'object') {
    return Object.keys(obj).sort().reduce((acc, key) => {
      acc[key] = sortKeys(obj[key]);
      return acc;
    }, {});
  }
  return obj;
}
const a = sortKeys(JSON.parse(fs.readFileSync(process.argv[1], 'utf8')));
const b = sortKeys(JSON.parse(fs.readFileSync(process.argv[2], 'utf8')));
fs.writeFileSync(process.argv[1] + '.normalized.json', JSON.stringify(a, null, 2));
fs.writeFileSync(process.argv[2] + '.normalized.json', JSON.stringify(b, null, 2));
console.log(JSON.stringify(a) === JSON.stringify(b) ? 'MATCH: Files are semantically identical' : 'MISMATCH: Files differ');
" $OUTPUT_DIR/main/analysis.cg.json $OUTPUT_DIR/feature/analysis.cg.json
```

If MISMATCH, diff the normalized files to find specific differences:
```bash
diff $OUTPUT_DIR/main/analysis.cg.json.normalized.json $OUTPUT_DIR/feature/analysis.cg.json.normalized.json
```

### 4. Investigate differences

If there are differences:
- DC main output is correct — the TSE implementation needs to change
- Look at the specific nodes/dependencies that differ
- Trace back to which TSE extractor produces the wrong result
- Fix in TSE, rebuild, re-run comparison

### 5. Cleanup

Keep the `$OUTPUT_DIR/main/` golden standard output across runs — only regenerate it if the user explicitly asks. On subsequent runs, skip Step 1 if `$OUTPUT_DIR/main/analysis.cg.json` already exists.

Only delete the feature output (`$OUTPUT_DIR/feature/`) between runs so it gets regenerated fresh. Ask the user before cleaning up the entire `$OUTPUT_DIR`.

## Extending to other languages

When adding dependency support for a new language in TSE:
1. Find or clone a medium-sized open-source repo for that language
2. Pass that repo path as the argument: `/dc-compare ../some-kotlin-project`
3. DC must already have a legacy analyzer for that language on `main` to serve as golden standard
4. The DC feature branch should be the one integrating TSE for that specific language
5. Run the same comparison flow
