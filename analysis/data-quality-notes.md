# Data Quality Notes — Full Suite Run 2026-03-03

## Run Identification

The full suite run (`--run-all-variants`) executed 2026-03-03 22:51-00:31 PST (1h39m).

**Correct result files** (use these run IDs for ETL):

| Variant | Run ID | Timestamp (UTC) |
|---------|--------|-----------------|
| control | `05aa20bb-5e04-42e7-acf6-e74276dcb1c2` | 07:16:51 |
| variant-a | `4f25dfd2-ba55-4add-96f8-2f9030f91a2f` | 07:41:32 |
| variant-b | `9c2d49af-8fc3-407c-aaf6-9b0b17c3b4b3` | 08:07:48 |
| variant-c | `d7926aaf-a2f7-4ab0-9c96-1fdd66ac5dd6` | 08:31:08 |

## Issue: Stale Overlapping Results

The `index.json` for variant-b and variant-c had "latest" entries pointing to result files from a **prior overlapping run** that finished 1-2 minutes after the full suite's results. These stale results:
- Had different pass rates (0.8 vs 1.0)
- Had NO efficiency scores
- Were written to the same variant directory by a separate process

**Root cause**: Prior smoke test sessions (or earlier full-suite attempts) were still running when the current full suite completed. Both wrote to the same `results/code-coverage-experiment/{variant}/` directory, and `index.json` appended chronologically.

**Fix for ETL**: Select result files by explicit run ID (from the table above), NOT by "latest entry in index.json". Tag these as `run_group = "full-suite-2026-03-03"`.

**Prevention**: Future runs should use a unique run group ID or subdirectory per full-suite invocation. The index.json append-only pattern is fragile when concurrent/overlapping runs write to the same directory.

## Corrected Results Summary

All 4 variants: 100% pass rate (5/5), efficiency scores present.

| Variant | Pass Rate | Avg T3 | Avg Efficiency | Total Cost |
|---------|-----------|--------|----------------|------------|
| Control | 100% | 0.62 | 0.878 | $4.57 |
| Variant-A | 100% | 0.80 | 0.937 | $4.17 |
| Variant-B | 100% | 0.70 | 0.837 | $4.98 |
| Variant-C | 100% | 0.76 | 0.823 | $4.55 |

## Coverage Metadata Location

Coverage data is in `invocationResult.metadata` (string-encoded), NOT in `item.metadata` (which is empty). ETL must parse from:
- `invocationResult.metadata.baselineCoverage` → float
- `invocationResult.metadata.finalCoverage` → float
- `invocationResult.metadata.coverageImprovement` → float
