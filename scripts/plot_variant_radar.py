"""
Radar/spider chart showing three scoring dimensions per variant.

Dimensions:
- Correctness: coverage_final / 100 (normalized to [0,1])
- Practice Adherence: t3_adherence (already [0,1])
- Efficiency: eff_composite (already [0,1])

Usage:
    python scripts/plot_variant_radar.py
"""

import math
from pathlib import Path

import duckdb
import matplotlib.pyplot as plt
import numpy as np

PROJECT_ROOT = Path(__file__).resolve().parent.parent
CURATED_DIR = PROJECT_ROOT / "data" / "curated"
FIGURES_DIR = PROJECT_ROOT / "analysis" / "figures"
ITEM_RESULTS = CURATED_DIR / "item_results.parquet"

VARIANT_COLORS = {
    "control": "#e74c3c",
    "variant-a": "#3498db",
    "variant-b": "#2ecc71",
    "variant-c": "#9b59b6",
}

VARIANT_LABELS = {
    "control": "Control (naive prompt)",
    "variant-a": "Var-A (hardened prompt)",
    "variant-b": "Var-B (targeted KB)",
    "variant-c": "Var-C (deep KB)",
}


def main():
    FIGURES_DIR.mkdir(parents=True, exist_ok=True)
    con = duckdb.connect()

    data = con.execute(f"""
        SELECT variant,
            AVG(coverage_final) / 100.0 AS correctness,
            AVG(t3_adherence) AS adherence,
            AVG(eff_composite) AS efficiency
        FROM '{ITEM_RESULTS}'
        GROUP BY variant ORDER BY variant
    """).fetchall()

    categories = ["Correctness\n(coverage)", "Practice\nAdherence (T3)", "Efficiency"]
    N = len(categories)
    angles = [n / float(N) * 2 * math.pi for n in range(N)]
    angles += angles[:1]  # close the polygon

    fig, ax = plt.subplots(figsize=(8, 8), subplot_kw=dict(polar=True))

    for row in data:
        variant = row[0]
        values = list(row[1:])
        values += values[:1]  # close

        color = VARIANT_COLORS.get(variant, "#666")
        label = VARIANT_LABELS.get(variant, variant)
        ax.plot(angles, values, 'o-', linewidth=2, label=label, color=color)
        ax.fill(angles, values, alpha=0.1, color=color)

    ax.set_xticks(angles[:-1])
    ax.set_xticklabels(categories, size=11)
    ax.set_ylim(0, 1.0)
    ax.set_yticks([0.2, 0.4, 0.6, 0.8, 1.0])
    ax.set_yticklabels(["0.2", "0.4", "0.6", "0.8", "1.0"], size=8, color="gray")
    ax.set_title("Three-Dimension Variant Comparison\n(Code Coverage Experiment — Spring Guides)",
                 size=13, pad=20)

    ax.legend(loc="upper right", bbox_to_anchor=(1.3, 1.1), fontsize=9)

    # Stats box
    stats = []
    for row in data:
        v = row[0]
        stats.append(f"{v}: corr={row[1]:.2f}  T3={row[2]:.2f}  eff={row[3]:.2f}")
    ax.text(0.02, -0.12, "\n".join(stats), transform=ax.transAxes,
            fontsize=8, fontfamily="monospace",
            bbox=dict(boxstyle="round", facecolor="white", alpha=0.8))

    output = FIGURES_DIR / "variant-radar.png"
    plt.savefig(output, dpi=150, bbox_inches="tight")
    plt.close()
    print(f"Written to {output}")

    con.close()


if __name__ == "__main__":
    main()
