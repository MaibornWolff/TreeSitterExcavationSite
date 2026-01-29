---
name: Fix LOC off-by-one bug
issue: none
state: complete
version: 1
---

## Goal

Fix the LOC (lines of code) metric which is off-by-one because it uses TreeSitter's zero-based `endPoint.row` index instead of actual line count.

## Tasks

### 1. Fix the calculation
- Change `rootNode.endPoint.row` to `rootNode.endPoint.row + 1` in `MetricCollector.kt:66`
- This converts from zero-based row index to actual line count

### 2. Update bug tests to expect correct values
- Change all `BUG - LOC misses last line` tests to assert correct behavior
- Rename tests to remove "BUG" prefix since they'll now test correct behavior
- Update expected LOC values from N-1 to N (matching RLOC for code without blanks/comments)

### 3. Verify existing tests still pass
- Run full test suite to ensure no regressions
- Existing LOC tests that add trailing newlines should still work

## Steps

- [x] Complete Task 1: Fix the calculation in MetricCollector.kt
- [x] Complete Task 2: Update bug tests in all 14 language test files
- [x] Complete Task 3: Run full test suite and verify

## Notes

- Bug affects all 14 languages because they share MetricCollector
- RLOC is correct (uses node-by-node counting), only file-level LOC is wrong
- Per-function metrics are unaffected
