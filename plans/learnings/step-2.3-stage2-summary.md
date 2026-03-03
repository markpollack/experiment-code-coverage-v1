# Step 2.3: Stage 2 Consolidation — Learnings

## Stage 2 Summary

Stage 2 took the experiment from "compiles and has tests" to "complete full-suite run with results."

### Steps Completed
- **2.0**: ExperimentApp bootstrap with CLI parsing
- **2.1**: JIT knowledge injection (workspace file copying)
- **2.2a**: Pipeline validation (upstream metadata fixes, --item filter)
- **2.2b**: Agent exhaust capture (4 repos, TeeingIterator, coordinate consolidation)
- **2.2c**: GoldenTestComparisonJudge (AST-based, 13 tests)
- **2.2**: Golden dataset pivot + full suite run

### Key Patterns from Stage 2
1. **Always trace the full data path** before running experiments (invoker → metadata → factory → context → judge). Silent abstain is worse than a crash.
2. **Verify installed JARs match source** — `CoverageImprovementJudge` existed in source but was missing from installed JAR.
3. **Per-variant invoker construction** — each variant may have different knowledge config, so create invoker in `runVariant()` not constructor.
4. **Run from plain terminal** — Claude CLI nesting detection prevents running from within Claude Code sessions. Use `systemd-run` workaround scripts.

### What Rolled into Stage 3
Stage 2 consolidation was deferred while building the analysis pipeline. The LEARNINGS.md compaction was done in Step 3.3 covering both Stage 2 and 3 together.
