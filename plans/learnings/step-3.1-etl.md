# Step 3.1: Python ETL — Learnings

## Stack

- `uv venv` + `uv pip install` for Python env management
- DuckDB in-process for SQL queries on parquet
- pandas DataFrame as intermediate (DuckDB can't scan Python lists directly)

## ETL Pattern

`scripts/load_results.py` reads JSON result files by explicit run ID → normalizes into 3 parquet files:
- `runs.parquet` (4 rows) — one per variant
- `item_results.parquet` (20 rows) — one per item × variant
- `judge_details.parquet` (180 rows) — one per judge criterion × item

## Gotchas

1. **DuckDB can't scan Python lists directly**: `CREATE TABLE t AS SELECT * FROM rows` fails with `InvalidInputException`. Must convert to `pd.DataFrame(rows)` first, then `SELECT * FROM df`.

2. **Duplicate judge criteria**: Recursive verdict extraction (`verdict.individual[]` + `verdict.subVerdicts[]`) hits the same checks multiple times. Added deduplication set keyed on `(item_slug, criterion_name, evidence[:100])`. Reduced from 300 to 180 rows.

3. **Score key mapping**: JSON keys don't match column names. Map explicitly:
   - `"CommandJudge"` → `t0_build`
   - `"Judge#1"` → `t3_adherence`
   - `"efficiency.composite"` → `eff_composite`

4. **Coverage metadata is strings**: `invocationResult.metadata` stores coverage as strings ("94.6"). Parse with `float()` and handle None/errors.

## Run Selection

Hardcoded `FULL_SUITE_RUN` dict with explicit UUIDs solves the run grouping problem. Tagged with `run_group = "full-suite-2026-03-03"`.
