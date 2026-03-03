"""
Core ablation analysis: per-variant aggregates and per-item breakdowns.

Produces analysis/tables/variant-comparison.md with:
- Summary table across all variants
- Per-item T3 breakdown pivoted by variant
- Per-item efficiency breakdown
- Diagnostic framework reading

Usage:
    python scripts/variant_comparison.py
"""

from pathlib import Path

import duckdb

PROJECT_ROOT = Path(__file__).resolve().parent.parent
CURATED_DIR = PROJECT_ROOT / "data" / "curated"
TABLES_DIR = PROJECT_ROOT / "analysis" / "tables"
ITEM_RESULTS = CURATED_DIR / "item_results.parquet"


def main():
    TABLES_DIR.mkdir(parents=True, exist_ok=True)
    con = duckdb.connect()

    lines = ["# Variant Comparison — Full Suite Run 2026-03-03\n"]

    # 1. Summary table
    lines.append("## Summary\n")
    summary = con.execute(f"""
        SELECT variant,
            COUNT(*) AS items,
            SUM(CASE WHEN passed THEN 1 ELSE 0 END) * 100 / COUNT(*) AS pass_pct,
            ROUND(AVG(coverage_final), 1) AS avg_cov,
            ROUND(AVG(t3_adherence), 3) AS avg_t3,
            ROUND(AVG(eff_composite), 3) AS avg_eff,
            ROUND(SUM(cost_usd), 2) AS total_cost,
            ROUND(AVG(cost_usd), 2) AS avg_cost
        FROM '{ITEM_RESULTS}'
        GROUP BY variant ORDER BY variant
    """).fetchall()

    lines.append("| Variant | Pass% | Avg Cov% | Avg T3 | Avg Eff | Total Cost | Avg Cost |")
    lines.append("|---------|-------|----------|--------|---------|------------|----------|")
    for row in summary:
        lines.append(f"| {row[0]} | {row[2]}% | {row[3]} | {row[4]} | {row[5]} | ${row[6]} | ${row[7]} |")
    lines.append("")

    # Print to stdout
    print("=== VARIANT SUMMARY ===")
    for row in summary:
        print(f"  {row[0]:12s}  pass={row[2]}%  cov={row[3]}%  T3={row[4]}  eff={row[5]}  cost=${row[6]}")
    print()

    # 2. Per-item T3 breakdown
    lines.append("## Per-Item T3 Practice Adherence\n")
    t3_pivot = con.execute(f"""
        SELECT item_slug,
            MAX(CASE WHEN variant='control' THEN ROUND(t3_adherence, 2) END) AS control,
            MAX(CASE WHEN variant='variant-a' THEN ROUND(t3_adherence, 2) END) AS var_a,
            MAX(CASE WHEN variant='variant-b' THEN ROUND(t3_adherence, 2) END) AS var_b,
            MAX(CASE WHEN variant='variant-c' THEN ROUND(t3_adherence, 2) END) AS var_c
        FROM '{ITEM_RESULTS}'
        GROUP BY item_slug ORDER BY item_slug
    """).fetchall()

    lines.append("| Item | Control | Var-A | Var-B | Var-C |")
    lines.append("|------|---------|-------|-------|-------|")
    for row in t3_pivot:
        best = max(row[1:])
        cells = [f"**{v}**" if v == best else str(v) for v in row[1:]]
        lines.append(f"| {row[0]} | {cells[0]} | {cells[1]} | {cells[2]} | {cells[3]} |")
    lines.append("")

    print("=== PER-ITEM T3 ===")
    for row in t3_pivot:
        print(f"  {row[0]:35s}  ctrl={row[1]}  a={row[2]}  b={row[3]}  c={row[4]}")
    print()

    # 3. Per-item efficiency breakdown
    lines.append("## Per-Item Efficiency\n")
    eff_pivot = con.execute(f"""
        SELECT item_slug,
            MAX(CASE WHEN variant='control' THEN ROUND(eff_composite, 3) END) AS control,
            MAX(CASE WHEN variant='variant-a' THEN ROUND(eff_composite, 3) END) AS var_a,
            MAX(CASE WHEN variant='variant-b' THEN ROUND(eff_composite, 3) END) AS var_b,
            MAX(CASE WHEN variant='variant-c' THEN ROUND(eff_composite, 3) END) AS var_c
        FROM '{ITEM_RESULTS}'
        GROUP BY item_slug ORDER BY item_slug
    """).fetchall()

    lines.append("| Item | Control | Var-A | Var-B | Var-C |")
    lines.append("|------|---------|-------|-------|-------|")
    for row in eff_pivot:
        lines.append(f"| {row[0]} | {row[1]} | {row[2]} | {row[3]} | {row[4]} |")
    lines.append("")

    # 4. Per-item coverage
    lines.append("## Per-Item Coverage (%)\n")
    cov_pivot = con.execute(f"""
        SELECT item_slug,
            MAX(CASE WHEN variant='control' THEN ROUND(coverage_final, 1) END) AS control,
            MAX(CASE WHEN variant='variant-a' THEN ROUND(coverage_final, 1) END) AS var_a,
            MAX(CASE WHEN variant='variant-b' THEN ROUND(coverage_final, 1) END) AS var_b,
            MAX(CASE WHEN variant='variant-c' THEN ROUND(coverage_final, 1) END) AS var_c
        FROM '{ITEM_RESULTS}'
        GROUP BY item_slug ORDER BY item_slug
    """).fetchall()

    lines.append("| Item | Control | Var-A | Var-B | Var-C |")
    lines.append("|------|---------|-------|-------|-------|")
    for row in cov_pivot:
        lines.append(f"| {row[0]} | {row[1]} | {row[2]} | {row[3]} | {row[4]} |")
    lines.append("")

    # 5. Diagnostic framework reading
    lines.append("## Diagnostic Framework\n")
    lines.append("| Dimension | Control | Var-A | Delta (C→A) | Var-B | Var-C | Interpretation |")
    lines.append("|-----------|---------|-------|-------------|-------|-------|----------------|")

    s = {row[0]: row for row in summary}
    for dim, idx, fmt in [("T3 Adherence", 4, ".3f"), ("Efficiency", 5, ".3f"), ("Cost", 7, ".2f")]:
        ctrl = s["control"][idx]
        va = s["variant-a"][idx]
        vb = s["variant-b"][idx]
        vc = s["variant-c"][idx]
        delta = va - ctrl if dim != "Cost" else ctrl - va
        sign = "+" if delta > 0 else ""
        interp = ""
        if dim == "T3 Adherence":
            interp = "Prompt structure > KB for practice quality"
        elif dim == "Efficiency":
            interp = "Hardened prompt = fewer build errors = more efficient"
        elif dim == "Cost":
            interp = "KB injection costs more (reading KB files)" if vb > va else "Comparable cost"
        lines.append(f"| {dim} | {ctrl:{fmt}} | {va:{fmt}} | {sign}{delta:{fmt}} | {vb:{fmt}} | {vc:{fmt}} | {interp} |")
    lines.append("")

    # Write markdown
    output = TABLES_DIR / "variant-comparison.md"
    output.write_text("\n".join(lines))
    print(f"Written to {output}")

    con.close()


if __name__ == "__main__":
    main()
