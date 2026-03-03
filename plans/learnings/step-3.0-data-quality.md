# Step 3.0: Data Quality — Learnings

## Key Discovery: No Efficiency Gap

The "missing efficiency scores" for variant-b/c was a **run selection bug**, not a code bug.

### Root Cause

`index.json` per variant appends entries chronologically. An earlier overlapping run (different pass rates, no efficiency scores) was indexed AFTER the full-suite run entries for variant-b and variant-c. Using "latest entry" picked stale results.

### Correct Run IDs (Full Suite 2026-03-03)

| Variant | Run ID |
|---------|--------|
| control | `05aa20bb-5e04-42e7-acf6-e74276dcb1c2` |
| variant-a | `4f25dfd2-ba55-4add-96f8-2f9030f91a2f` |
| variant-b | `9c2d49af-8fc3-407c-aaf6-9b0b17c3b4b3` |
| variant-c | `d7926aaf-a2f7-4ab0-9c96-1fdd66ac5dd6` |

### Corrected Results

All 4 variants: 100% pass rate, efficiency scores present. The previously reported "80% pass rate for variant-b/c" was from the stale overlapping run.

### Prevention

- ETL uses explicit run IDs, not "latest in index"
- Future: add run group IDs or subdirectories per suite invocation
- Document run IDs in `analysis/data-quality-notes.md`

## Coverage Metadata Location

Coverage is in `invocationResult.metadata` (not `item.metadata`). ETL must read `invocationResult.metadata.baselineCoverage`, `finalCoverage`, `coverageImprovement`.
