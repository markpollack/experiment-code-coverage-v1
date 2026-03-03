# Step 3.2: Variant Comparison + Visualization — Learnings

## Scripts Created

1. **`scripts/variant_comparison.py`** — Core ablation analysis
   - Per-variant aggregates, per-item pivots (T3, efficiency, coverage)
   - Diagnostic framework reading table
   - Output: `analysis/tables/variant-comparison.md`

2. **`scripts/plot_variant_radar.py`** — Three-dimension radar chart
   - Dimensions: Correctness (coverage/100), Practice Adherence (T3), Efficiency
   - matplotlib polar plot, one polygon per variant
   - Output: `analysis/figures/variant-radar.png`

3. **`scripts/generate_item_cards.py`** — Per-item detail cards
   - All scores + efficiency breakdown + token counts + T3 criterion details
   - Output: `analysis/cards/{item_slug}.md`

## Key Findings

| Variant | Avg T3 | Avg Eff | Total Cost |
|---------|--------|---------|------------|
| Control | 0.62 | 0.878 | $4.57 |
| Variant-A | 0.80 | 0.937 | $4.17 |
| Variant-B | 0.697 | 0.837 | $4.98 |
| Variant-C | 0.757 | 0.823 | $4.55 |

- **Hardened prompt (variant-a) is the biggest lever**: +0.18 T3 over control, better efficiency, lower cost
- **KB injection did NOT improve over prompt alone**: T3 dropped from 0.80 to 0.70-0.76, cost increased
- **Coverage hit ceiling**: 85-100% regardless of variant — the Spring guides are too simple for coverage to discriminate
- **Discriminator items**: `gs-reactive-rest-service` shows widest T3 spread (0.50→0.93), good for debugging variant differences
- **Efficiency correlates with prompt quality**: fewer build errors = fewer recovery cycles = lower cost

## Visualization Notes

- Radar chart uses 3 dimensions (correctness, adherence, efficiency) — keeps them separate, not collapsed
- Variant-A polygon visibly dominates on adherence and efficiency axes
- Stats box at bottom shows exact values for reference
