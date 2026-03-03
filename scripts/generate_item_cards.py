"""
Generate per-item detail cards with all scores across variants.

One markdown file per item showing: coverage, T3, efficiency, cost, and
judge criterion details.

Usage:
    python scripts/generate_item_cards.py
"""

from pathlib import Path

import duckdb

PROJECT_ROOT = Path(__file__).resolve().parent.parent
CURATED_DIR = PROJECT_ROOT / "data" / "curated"
CARDS_DIR = PROJECT_ROOT / "analysis" / "cards"
ITEM_RESULTS = CURATED_DIR / "item_results.parquet"
JUDGE_DETAILS = CURATED_DIR / "judge_details.parquet"


def main():
    CARDS_DIR.mkdir(parents=True, exist_ok=True)
    con = duckdb.connect()

    items = con.execute(f"""
        SELECT DISTINCT item_slug FROM '{ITEM_RESULTS}' ORDER BY item_slug
    """).fetchall()

    for (slug,) in items:
        lines = [f"# {slug}\n"]

        # Score table
        rows = con.execute(f"""
            SELECT variant,
                ROUND(coverage_final, 1) AS cov,
                ROUND(t3_adherence, 2) AS t3,
                ROUND(eff_composite, 3) AS eff,
                ROUND(eff_build_errors, 3) AS eff_be,
                ROUND(eff_cost, 3) AS eff_c,
                ROUND(eff_recovery_cycles, 3) AS eff_rc,
                ROUND(cost_usd, 2) AS cost,
                input_tokens, output_tokens, thinking_tokens
            FROM '{ITEM_RESULTS}'
            WHERE item_slug = ?
            ORDER BY variant
        """, [slug]).fetchall()

        lines.append("## Scores by Variant\n")
        lines.append("| Variant | Cov% | T3 | Eff | Eff:BE | Eff:Cost | Eff:RC | Cost | In Tok | Out Tok | Think |")
        lines.append("|---------|------|-----|------|--------|----------|--------|------|--------|---------|-------|")
        for r in rows:
            lines.append(f"| {r[0]} | {r[1]} | {r[2]} | {r[3]} | {r[4]} | {r[5]} | {r[6]} | ${r[7]} | {r[8]} | {r[9]} | {r[10]} |")
        lines.append("")

        # T3 judge criteria details (from judge_details)
        lines.append("## T3 Practice Adherence — Criterion Details\n")
        for variant_row in rows:
            variant = variant_row[0]
            criteria = con.execute(f"""
                SELECT criterion_name, score, evidence
                FROM '{JUDGE_DETAILS}'
                WHERE item_slug = ? AND run_id IN (
                    SELECT run_id FROM '{ITEM_RESULTS}' WHERE variant = ? AND item_slug = ?
                )
                AND criterion_name NOT IN ('build_success', 'coverage_improvement', 'coverage_preservation')
                ORDER BY criterion_name
            """, [slug, variant, slug]).fetchall()

            if criteria:
                lines.append(f"### {variant}\n")
                lines.append("| Criterion | Score | Evidence |")
                lines.append("|-----------|-------|----------|")
                for c in criteria:
                    score_str = f"{c[1]:.2f}" if c[1] is not None else "—"
                    evidence = (c[2] or "")[:120].replace("|", "\\|")
                    lines.append(f"| {c[0]} | {score_str} | {evidence} |")
                lines.append("")

        output = CARDS_DIR / f"{slug}.md"
        output.write_text("\n".join(lines))
        print(f"  {slug} -> {output.name}")

    con.close()
    print(f"\nGenerated {len(items)} item cards in {CARDS_DIR}")


if __name__ == "__main__":
    main()
