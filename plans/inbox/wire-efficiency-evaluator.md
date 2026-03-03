# Wire Efficiency Evaluator into Code-Coverage Experiment

## Context

The experiment-driver's `DefaultEfficiencyEvaluator` is already on the classpath (`ai.tuvium:experiment-core:0.1.0-SNAPSHOT` is a dependency). It produces 4 normalized [0,1] efficiency metrics + a weighted composite from agent trajectory data. Currently the code-coverage experiment captures agent exhaust (`PhaseCapture`) but doesn't score efficiency — the raw data goes into `InvocationResult` but is never evaluated.

The efficiency scores complete the feedback loop: functional correctness (T0-T2) tells you IF the agent worked, practice adherence (T3) tells you HOW WELL it followed prescribed practices, and efficiency tells you HOW EFFICIENTLY it worked. Each dimension maps to a different optimization lever:

| Low score on... | Lever to fix | Cost |
|-----------------|-------------|------|
| Practice adherence | Knowledge — edit KB files | Low |
| Efficiency | Execution structure — improve prompt, tool routing | Medium |
| Functional correctness | Tools or model | High |

This is the `knowledge + structured execution > model` equation made actionable as a diagnostic feedback loop.

## What Already Works

- `CodeCoverageAgentInvoker` captures `PhaseCapture` from agent response (line 116)
- `PhaseCapture` contains `toolUses()`, `toolResults()`, `numTurns`, `inputTokens`, `outputTokens`, `totalCostUsd`
- `InvocationResult.completed()` passes phases through to results
- Results JSON already has cost, tokens, duration per item

## What Needs to Happen

### Step 1: Build `ReasoningContext` from `PhaseCapture`

After the agent returns and before returning `InvocationResult`, construct a `ReasoningContext`:

```java
import ai.tuvium.experiment.diagnostic.ReasoningContext;

// In CodeCoverageAgentInvoker.invoke(), after step 7 (measure final coverage):

List<PhaseCapture> phases = capture != null ? List.of(capture) : List.of();

ReasoningContext reasoningContext = new ReasoningContext(
    null,              // analysis — not used in code-coverage
    null,              // plan — no execution plan (tool utilization will be skipped gracefully)
    Set.of(),          // availableTools — empty for now (could populate with agent's tool list)
    phases,            // phases — the captured agent trajectory
    null,              // runDir
    null,              // runLog
    List.of(),         // traceFiles
    workspace,         // workspacePath
    null               // resultJsonPath
);
```

**Note**: With `plan=null`, the `toolUtilization` metric is gracefully skipped (not scored 0.0). This is correct — code-coverage doesn't have a planner. The remaining 3 metrics (buildErrors, cost, recoveryCycles) will evaluate normally.

### Step 2: Evaluate and Merge Scores

```java
import ai.tuvium.experiment.diagnostic.DefaultEfficiencyEvaluator;
import ai.tuvium.experiment.diagnostic.EfficiencyConfig;
import ai.tuvium.experiment.diagnostic.EfficiencyReport;

DefaultEfficiencyEvaluator evaluator = new DefaultEfficiencyEvaluator();
EfficiencyConfig config = EfficiencyConfig.defaults(); // $5 ceiling, standard weights

EfficiencyReport report = evaluator.evaluate(result, reasoningContext, config);

// Log the composite
logger.info("Efficiency: composite={}, checks={}",
    String.format("%.3f", report.composite()),
    report.checks().stream()
        .map(c -> c.metric() + "=" + String.format("%.3f", c.normalizedScore()))
        .toList());
```

### Step 3: Get Scores into Results JSON

The efficiency scores need to end up in `ItemResult.scores()` alongside the jury scores. Two options:

**Option A: Store in metadata** (simplest, no experiment-driver changes):

Add the efficiency scores as metadata entries on the `InvocationResult`:

```java
// After evaluator runs:
for (var entry : report.scores().entrySet()) {
    enrichedMetadata.put(entry.getKey(), String.valueOf(entry.getValue()));
}
```

This puts `efficiency.buildErrors`, `efficiency.cost`, `efficiency.recoveryCycles`, `efficiency.composite` into metadata. Analysis scripts can read them. But they won't appear in the jury `scores` map.

**Option B: Post-invocation hook in the pipeline** (cleaner, uses experiment-driver integration point):

The experiment-driver's `ExperimentRunner.runItem()` already calls the efficiency evaluator and merges scores with `efficiency.` prefix into `ItemResult.scores()`. If the code-coverage experiment uses `ExperimentRunner` (or can be adapted to), this happens automatically.

Check whether `CodeCoverageExperiment` (or whatever orchestrates the runs) delegates to `ExperimentRunner.runItem()`. If so, the efficiency evaluation is already wired — it just needs the `PhaseCapture` to be non-empty in the `InvocationResult`, which it already is after Step 2.2b.

**Recommendation**: Start with Option A to get scores immediately visible in the current run results. Migrate to Option B when the experiment fully uses `ExperimentRunner`.

### Step 4: Adapt Metrics for Code-Coverage Context

The default config works but consider tuning:

| Metric | Default | Code-Coverage Consideration |
|--------|---------|----------------------------|
| costCeilingUsd | $5.00 | Reasonable — agent runs cost $0.50-$2.00 typically |
| errorCountThreshold | 8 | Reasonable — agents hit 2-6 build errors typically |
| buildErrors weight | 0.35 | Keep — build errors are the primary inefficiency signal |
| cost weight | 0.20 | Maybe increase for cross-model comparison (Haiku vs Opus cost gap) |

For the cross-model ablation story, consider a variant-specific cost ceiling: Haiku runs should be cheap, so a $2.00 ceiling would score efficiency more sensitively.

## What the Efficiency Scores Tell You Per Variant

| Variant | Expected Efficiency Pattern | Why |
|---------|---------------------------|-----|
| Control | Low — many build errors, high retry cost | No structure, agent flails |
| Variant-a | Medium — fewer errors (compile gates) but still searching blind | Structure without knowledge = efficient process, uninformed decisions |
| Variant-b | Medium-high — KB reduces wrong turns | Knowledge reduces trial-and-error |
| Variant-c | Highest — JIT navigation avoids reading unnecessary files | Deep knowledge = directed search, minimal waste |

If this pattern holds, it demonstrates: **knowledge improves efficiency, not just correctness**. The agent spends fewer tokens reaching the right answer because the KB tells it where to look.

## The Three-Dimensional Growth Story

With efficiency scores, the ablation data tells a richer story:

```
Control  → Variant-a:  Δ efficiency (fewer build errors) — structured execution value
Variant-a → Variant-b: Δ adherence (correct patterns)    — basic knowledge value
Variant-b → Variant-c: Δ efficiency (JIT navigation)     — knowledge depth value
```

The equation is demonstrated across all three scoring dimensions, not just adherence.

## Files to Modify

| File | Change |
|------|--------|
| `CodeCoverageAgentInvoker.java` | Add `ReasoningContext` construction + `DefaultEfficiencyEvaluator` call after step 7 |
| `CodeCoverageAgentInvoker.java` | Merge `report.scores()` into `enrichedMetadata` |
| No POM changes | `experiment-core` dependency already present |

## Key Source Files (Reference)

| File | What |
|------|------|
| `DefaultEfficiencyEvaluator.java` | `experiment-core/.../diagnostic/` — the evaluator |
| `EfficiencyConfig.java` | Default weights, cost ceiling, error threshold |
| `EfficiencyReport.java` | Output: checks list + scores map + composite |
| `EfficiencyCheck.java` | Per-metric: name, raw value, normalized score, detail string |
| `ReasoningContext.java` | Input: phases, plan, available tools, workspace |
| `DefaultEfficiencyEvaluatorTest.java` | 22 unit tests — shows how to construct test contexts |
| `Run2351d0afEfficiencyTest.java` | Real-data validation — shows expected scores for known run |
