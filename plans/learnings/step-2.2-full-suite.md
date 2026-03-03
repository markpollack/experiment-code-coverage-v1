# Step 2.2: Golden Dataset Pivot + Full Suite Run — Learnings

## Golden Dataset Pivot

Original dataset had 71-92% baseline coverage — agents did nothing useful. Pivoted to SWE-bench style:
- Strip all test sources from `before/` directory
- Save Spring developers' tests as reference in `golden/`
- Agents write tests from scratch (0% baseline)

This made coverage improvement the primary signal and enabled the GoldenTestComparisonJudge.

## Full Suite Run (2026-03-03)

- **Duration**: 1h39m (4 variants × 5 guides, sequential)
- **Model**: claude-sonnet-4-6
- **Results**: All 4 variants achieved 100% pass rate
- **Harmless error**: Thread shutdown exception at end of run — no impact on results

## Efficiency Evaluator Wiring

`DefaultEfficiencyEvaluator` wired via `EfficiencyConfig.defaults()` in `ExperimentApp`. Moved from invoker-level to `ExperimentRunner`-level — evaluates after all judges complete, using phases data from `InvocationResult`.

## Model Switch

Switched from `claude-sonnet-4-5-20241022` to `claude-sonnet-4-6`. Updated both agent invocation and judge model configs.

## Key Outcome

Hardened prompt alone (variant-a) beat all KB-injected variants on T3 adherence, efficiency, and cost. See `analysis/findings-summary.md` for full analysis.
