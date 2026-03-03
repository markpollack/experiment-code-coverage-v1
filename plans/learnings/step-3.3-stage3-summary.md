# Step 3.3: Stage 3 Consolidation — Learnings

## What Stage 3 Produced

1. **Data quality resolution**: Efficiency gap was a run selection bug, not missing data. All variants have complete scores.
2. **ETL pipeline**: `load_results.py` (JSON → 3 parquet files) with explicit run ID selection.
3. **Analysis scripts**: `variant_comparison.py`, `plot_variant_radar.py`, `generate_item_cards.py`.
4. **Findings**: Hardened prompt > KB injection on simple Spring guides. Coverage hits ceiling. T3 is the discriminating dimension.

## Key Experimental Finding

**Prompt engineering is the dominant lever for simple projects.** The hardened prompt (variant-a) improved T3 by +0.18 over control while reducing cost. KB injection (variants b/c) regressed T3 and increased cost. The model already knows enough about Spring testing for simple guides.

## What This Means for Next Steps

- Need **harder targets** where the model's built-in knowledge is insufficient
- Need **cross-model comparison** to test if KB helps cheaper models
- Need **multiple runs** for statistical confidence (N=1 per cell currently)
- The analysis pipeline is reusable — add new run groups to `FULL_SUITE_RUN` dict and re-run scripts

## Analysis Pipeline Maturity

Scripts are functional but basic. Future improvements:
- Parameterize run group selection (currently hardcoded)
- Add confidence intervals when multiple runs available
- Add per-criterion T3 heatmap across items × variants
- Add token/cost breakdown charts
