"""
ETL: Load experiment result JSON files into DuckDB-queryable parquet.

Reads results from the full-suite run (2026-03-03) by explicit run IDs,
normalizes into 3 parquet files: runs, item_results, judge_details.

Usage:
    python scripts/load_results.py
"""

import json
from pathlib import Path

import duckdb
import pandas as pd

PROJECT_ROOT = Path(__file__).resolve().parent.parent
RESULTS_DIR = PROJECT_ROOT / "results" / "code-coverage-experiment"
CURATED_DIR = PROJECT_ROOT / "data" / "curated"

# Full suite run IDs — see analysis/data-quality-notes.md
FULL_SUITE_RUN = {
    "control": "05aa20bb-5e04-42e7-acf6-e74276dcb1c2",
    "variant-a": "4f25dfd2-ba55-4add-96f8-2f9030f91a2f",
    "variant-b": "9c2d49af-8fc3-407c-aaf6-9b0b17c3b4b3",
    "variant-c": "d7926aaf-a2f7-4ab0-9c96-1fdd66ac5dd6",
}

RUN_GROUP = "full-suite-2026-03-03"
MODEL = "claude-sonnet-4-6"

# Score key mappings: JSON key → column name
SCORE_MAP = {
    "CommandJudge": "t0_build",
    "CoveragePreservationJudge": "t1_preservation",
    "CoverageImprovementJudge": "t2_improvement",
    "Judge#1": "t3_adherence",
    "efficiency.buildErrors": "eff_build_errors",
    "efficiency.cost": "eff_cost",
    "efficiency.recoveryCycles": "eff_recovery_cycles",
    "efficiency.composite": "eff_composite",
    "GoldenTestComparisonJudge": "golden_similarity",
}


def load_result(variant: str, run_id: str) -> dict:
    path = RESULTS_DIR / variant / f"{run_id}.json"
    with open(path) as f:
        return json.load(f)


def parse_float(value) -> float | None:
    if value is None:
        return None
    try:
        return float(value)
    except (ValueError, TypeError):
        return None


def extract_runs(results: dict[str, dict]) -> list[dict]:
    rows = []
    for variant, data in results.items():
        rows.append({
            "run_id": data["experimentId"],
            "variant": variant,
            "model": MODEL,
            "timestamp": data["timestamp"],
            "pass_rate": data["passRate"],
            "total_cost_usd": data["totalCostUsd"],
            "total_duration_ms": data["totalDurationMs"],
            "total_tokens": data.get("totalTokens", 0),
            "item_count": len(data["items"]),
            "run_group": RUN_GROUP,
        })
    return rows


def extract_item_results(results: dict[str, dict]) -> list[dict]:
    rows = []
    for variant, data in results.items():
        run_id = data["experimentId"]
        for item in data["items"]:
            inv_meta = item.get("invocationResult", {}).get("metadata", {})
            inv = item.get("invocationResult", {})
            scores = item.get("scores", {})
            metrics = item.get("metrics", {})

            row = {
                "run_id": run_id,
                "variant": variant,
                "item_slug": item["itemSlug"],
                "passed": item["passed"],
                "cost_usd": item["costUsd"],
                "duration_ms": item.get("durationMs", 0),
                "total_tokens": item.get("totalTokens", 0),
                # Coverage from invocationResult.metadata (string → float)
                "coverage_baseline": parse_float(inv_meta.get("baselineCoverage")),
                "coverage_final": parse_float(inv_meta.get("finalCoverage")),
                "coverage_delta": parse_float(inv_meta.get("coverageImprovement")),
                # Tokens
                "input_tokens": metrics.get("input_tokens") or inv.get("inputTokens", 0),
                "output_tokens": metrics.get("output_tokens") or inv.get("outputTokens", 0),
                "thinking_tokens": metrics.get("thinking_tokens") or inv.get("thinkingTokens", 0),
            }

            # Map scores to named columns
            for json_key, col_name in SCORE_MAP.items():
                row[col_name] = scores.get(json_key)

            rows.append(row)
    return rows


def extract_judge_details(results: dict[str, dict]) -> list[dict]:
    rows = []
    for variant, data in results.items():
        run_id = data["experimentId"]
        for item in data["items"]:
            verdict = item.get("verdict")
            if not verdict:
                continue
            _extract_checks(rows, run_id, item["itemSlug"], verdict)
    return rows


def _extract_checks(rows: list[dict], run_id: str, item_slug: str, verdict: dict,
                     seen: set | None = None):
    """Recursively extract checks from verdict and subVerdicts, deduplicating."""
    if seen is None:
        seen = set()

    for individual in verdict.get("individual", []):
        judge_name = individual.get("reasoning", "unknown")
        for check in individual.get("checks", []):
            msg = check.get("message", "")
            criterion = check.get("name", "unknown")

            # Deduplicate: same criterion + same evidence = same check
            key = (item_slug, criterion, msg[:100])
            if key in seen:
                continue
            seen.add(key)

            # Parse score from check message if available (format: "0.50 — ...")
            score = None
            if msg and msg[0].isdigit():
                try:
                    score = float(msg.split(" ")[0])
                except ValueError:
                    pass
            rows.append({
                "run_id": run_id,
                "item_slug": item_slug,
                "judge_name": judge_name[:100],
                "criterion_name": criterion,
                "score": score,
                "passed": check.get("passed", False),
                "evidence": msg[:500] if msg else None,
            })

    for sub in verdict.get("subVerdicts", []):
        _extract_checks(rows, run_id, item_slug, sub, seen)


def write_parquet(rows: list[dict], table_name: str, output_path: Path):
    if not rows:
        print(f"  {table_name}: no rows, skipping")
        return

    df = pd.DataFrame(rows)
    con = duckdb.connect()
    con.execute(f"CREATE TABLE {table_name} AS SELECT * FROM df")
    con.execute(f"COPY {table_name} TO '{output_path}' (FORMAT PARQUET)")
    count = con.execute(f"SELECT count(*) FROM {table_name}").fetchone()[0]
    print(f"  {table_name}: {count} rows -> {output_path.name}")
    con.close()


def main():
    CURATED_DIR.mkdir(parents=True, exist_ok=True)

    print(f"Loading results from {RESULTS_DIR}")
    print(f"Run group: {RUN_GROUP}")

    # Load all variant results
    results = {}
    for variant, run_id in FULL_SUITE_RUN.items():
        data = load_result(variant, run_id)
        results[variant] = data
        print(f"  {variant}: {len(data['items'])} items, passRate={data['passRate']}")

    # Extract and write
    print("\nWriting parquet files:")

    runs = extract_runs(results)
    write_parquet(runs, "runs", CURATED_DIR / "runs.parquet")

    item_results = extract_item_results(results)
    write_parquet(item_results, "item_results", CURATED_DIR / "item_results.parquet")

    judge_details = extract_judge_details(results)
    write_parquet(judge_details, "judge_details", CURATED_DIR / "judge_details.parquet")

    # Verify
    print("\nVerification:")
    con = duckdb.connect()
    result = con.execute(f"""
        SELECT variant, count(*) as items,
               round(avg(t3_adherence), 3) as avg_t3,
               round(avg(eff_composite), 3) as avg_eff,
               round(sum(cost_usd), 2) as total_cost
        FROM '{CURATED_DIR}/item_results.parquet'
        GROUP BY variant ORDER BY variant
    """).fetchall()
    for row in result:
        print(f"  {row[0]}: {row[1]} items, T3={row[2]}, Eff={row[3]}, Cost=${row[4]}")
    con.close()

    print("\nDone.")


if __name__ == "__main__":
    main()
