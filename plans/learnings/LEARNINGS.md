# Learnings: Code Coverage Experiment

> **Last compacted**: 2026-03-03
> **Covers through**: Stage 3 (Steps 1.0–3.2)

This is the **Tier 1 compacted summary**. Read this first for the current state of project knowledge. For details on specific steps, see the per-step files (Tier 2).

---

## Key Discoveries

### 1. Agent-based judging requires filesystem navigation, not prompt stuffing

The biggest design pivot came from the project owner's critical insight (v1 review): a quality judge needs to navigate the workspace like a developer — follow imports, examine related files, understand project structure. A single `ChatClient` request stuffed with file contents doesn't scale and loses the agent's ability to explore. This led to using `AgentClient` with `ClaudeAgentModel` (read-only: `Read`, `Glob`, `Grep` only) instead of `LLMJudge`.

### 2. Fixed judge prompt, not adaptive

The judge uses one static rubric (`prompts/judge-practice-adherence.txt`) applied identically to all variants. The KB informs prompt *authorship*, not runtime behavior. This means the judge rewards built-in LLM knowledge — if the model already knows `@WebMvcTest` without KB injection, it scores. The growth story shows what knowledge adds *on top of* what the model already knows.

### 3. Functional correctness and practice adherence are separate dimensions

T0–T2 (build success, coverage preservation, coverage improvement) are deterministic. T3 (practice adherence) is LLM-based. These are reported separately, never combined into a single number. This prevents a high-quality-but-low-coverage run from appearing equivalent to a high-coverage-but-low-quality run.

### 4. Exhaust capture needed upstream library work

The judge's audit trail (tool calls, thinking, tokens, cost) required changes across three repos before TestQualityJudge could be implemented: (1) `journal-core` migrated from private `ai.tuvium` coordinates, (2) `claude-code-capture` promoted from `refactoring-agent` to standalone module, (3) `Consumer<ParsedMessage> messageListener` added to `ClaudeAgentModel`. This was a significant prerequisite (Step 1.4a) that the original design didn't anticipate.

### 5. Spring Getting Started guides are an ideal first dataset

All 5 guides compile, pass tests, use `./mvnw`, and run Spring Boot 4.0.x. Most have minimal test coverage (1-2 tests) — ideal targets for improvement. `gs-securing-web` has the richest suite (5 tests) — good for testing coverage preservation. The dataset is easy to verify and reproduces reliably via `materialize.sh`.

### 6. Golden dataset pivot was necessary

Original dataset had 71-92% baseline coverage — agents did nothing. Pivoted to SWE-bench style: strip all tests, save Spring developers' tests as reference, agents write from scratch (0% baseline). This made coverage improvement the primary signal.

### 7. Hardened prompt > knowledge injection on simple guides

Full suite results (4 variants × 5 guides, Sonnet):
- **Variant-A (hardened prompt)**: T3=0.80, Eff=0.937, Cost=$4.17 — best across all dimensions
- **Control (naive prompt)**: T3=0.62, Eff=0.878, Cost=$4.57 — prompt matters most
- **Variant-C (deep KB)**: T3=0.757, Eff=0.823, Cost=$4.55 — KB adds noise, not signal
- **Variant-B (targeted KB)**: T3=0.697, Eff=0.837, Cost=$4.98 — KB hurts on simple targets

On Spring Getting Started guides, the model already knows enough. KB injection adds reading overhead without proportional benefit. This may change with harder targets (Pet Clinic, multi-module projects).

### 8. Coverage hits ceiling on simple projects

85-100% coverage regardless of variant on most guides. Coverage doesn't discriminate between variants — practice adherence (T3) is the meaningful signal. Need harder targets where coverage is genuinely challenging.

### 9. Run selection from index.json is fragile

`index.json` per variant appends chronologically. Overlapping/stale runs mix entries. The "latest" entry is not necessarily from the most recent full suite. ETL must use explicit run IDs, not "latest in index." Future: run group IDs or subdirectories per suite invocation.

---

## Patterns Established

### Testability via functional interfaces

`TestQualityJudge` takes `Function<Path, AgentClient>` as a constructor parameter instead of creating agents internally. Tests mock the factory without touching static methods or real agent infrastructure. The `defaultAgentClientFactory(model, timeout)` static method provides production wiring. This pattern should be reused for any component that creates agents.

### Package-private parsing for unit tests

`parseJudgment()` is package-private, allowing direct unit testing of JSON parsing without mocking the agent chain. Most test logic lives in parsing tests. This separates "does the agent invocation work?" (integration) from "does the JSON parsing work?" (unit).

### Score clamping before NumericalScore construction

`NumericalScore.normalized(double)` throws on out-of-bounds values. LLMs sometimes produce scores outside [0,1]. Always clamp with `Math.max(0.0, Math.min(1.0, rawScore))` before constructing the score. Log the clamping so it's visible.

### Dataset materialization via script

`dataset/materialize.sh` clones repos into `dataset/workspaces/`, copies `complete/` into `dataset/items/{id}/before/`. Generated content is gitignored. Run `--verify` to rebuild and test. The experiment-core framework expects `dataset.json` manifest + per-item `item.json` metadata.

### 4-tier jury with escalating policies

| Tier | Judge | Policy | Signal |
|------|-------|--------|--------|
| 0 | BuildSuccessJudge | REJECT_ON_ANY_FAIL | Binary gate |
| 1 | CoveragePreservationJudge | REJECT_ON_ANY_FAIL | Binary gate |
| 2 | CoverageImprovementJudge + GoldenTestComparisonJudge | ACCEPT_ON_ALL_PASS | Functional metric + AST comparison |
| 3 | TestQualityJudge | FINAL_TIER | Practice adherence (LLM) |

### JIT knowledge injection via workspace file copying

`CodeCoverageAgentInvoker` copies knowledge files into `workspace/knowledge/` before agent invocation. If `knowledgeFiles` contains `index.md`, copies entire tree (variant-c JIT navigation). Otherwise copies only listed files (variant-b targeted).

### DuckDB + parquet analysis pipeline

`scripts/load_results.py` → parquet ETL. `scripts/variant_comparison.py` → markdown tables. `scripts/plot_variant_radar.py` → radar chart. `scripts/generate_item_cards.py` → per-item detail cards. All scripts are standalone (no CLI framework), read from `data/curated/*.parquet`.

---

## Deviations from Design

| Design said | What happened | Rationale |
|-------------|---------------|-----------|
| Copy workspace to temp dir before judging | Deferred | Judge agent uses read-only tools (`Read`, `Glob`, `Grep`) with `yolo(false)`. Isolation already enforced at tool level. Add copy if files are modified in practice. |
| CompletableFuture timeout wrapper | Dropped | `ClaudeAgentOptions.timeout(Duration)` provides native timeout support (default 10 min). Simple try/catch on synchronous `run()` is sufficient. |
| `LLMJudge` base class | Replaced with direct `Judge` implementation | Agent-based judge needs filesystem navigation. No `agent-judge-llm` dependency needed — reuse existing agent-client stack. |
| Naming quality criterion | Dropped | No team convention ground truth for naming. Weight redistributed to assertion quality and edge-case coverage. |
| BDD + no-trivial criteria (v1) | Collapsed into error/edge-case coverage (v2) | Original criteria were overlapping. 6 final criteria better capture distinct quality dimensions. |
| KB injection as biggest lever | Prompt hardening was bigger | On simple Spring guides, the model already knows enough. KB injection adds reading overhead. Hypothesis needs harder targets. |
| Latest index entry = correct run | Explicit run IDs needed | Overlapping runs pollute index.json ordering. Always select by explicit UUID. |

---

## Common Pitfalls

### DuckDB can't scan Python lists directly

`con.execute("CREATE TABLE t AS SELECT * FROM rows")` fails. Must convert to `pd.DataFrame(rows)` first, then `SELECT * FROM df`.

### Judge verdict deduplication needed

Recursive verdict extraction (`verdict.individual[]` + `verdict.subVerdicts[]`) hits the same checks multiple times. Deduplicate by `(item_slug, criterion_name, evidence[:100])`.

### Coverage metadata location

Coverage is in `invocationResult.metadata` (strings like "94.6"), NOT `item.metadata`. ETL parses with `float()`.

### AgentClient API surprises

- `AgentClientRequestSpec` is a **nested interface** inside `AgentClient`, not a top-level class. Import as `AgentClient.AgentClientRequestSpec`.
- The model result type is `AgentGeneration` (not `AgentResult`). Method is `getOutput()`.
- `AgentClientResponse.getResult()` is a convenience that delegates to `agentResponse.getResult().getOutput()`.
- Mock the full fluent chain: `goal() → workingDirectory() → run()`. `goal()` returns a builder, not self.

### NumericalScore validates eagerly

`NumericalScore.normalized(double)` throws `IllegalArgumentException` on values outside [0.0, 1.0]. Always clamp LLM-produced scores before construction.

### experiment-core dataset format

The forge-scaffolded `items.yaml` is documentation only. The framework reads `dataset/dataset.json` (manifest) + `dataset/items/{id}/item.json` (per-item metadata) + `dataset/items/{id}/before/` (source snapshot).

### workingDirectory priority chain

`DefaultAgentClient.determineWorkingDirectory()`: request-level > goal-level > builder default > cwd. For the judge, set on request-level only — it overrides everything else.

---

## Per-Step Detail Files (Tier 2)

| File | Step | Topic |
|------|------|-------|
| `step-1.3-dataset.md` | 1.3 | Dataset population and verification |
| `step-1.4-test-quality-judge.md` | 1.4 | TestQualityJudge implementation |
| `step-1.5-stage1-summary.md` | 1.5 | Stage 1 consolidation summary |
| `step-2.0-bootstrap.md` | 2.0 | ExperimentApp bootstrap wiring |
| `step-2.1-knowledge-injection.md` | 2.1 | JIT knowledge file copying |
| `step-2.2a-pipeline-validation.md` | 2.2a | Upstream metadata fixes, CLI filter |
| `step-2.2b-exhaust-capture.md` | 2.2b | Agent exhaust capture wiring |
| `step-3.0-data-quality.md` | 3.0 | Efficiency gap resolution, run selection |
| `step-3.1-etl.md` | 3.1 | Python ETL (JSON → parquet) |
| `step-3.2-variant-comparison.md` | 3.2 | Analysis scripts + visualization |
| `step-3.3-stage3-summary.md` | 3.3 | Stage 3 consolidation |

---

## Revision History

| Timestamp | Change | Trigger |
|-----------|--------|---------|
| 2026-03-01 | Initial skeleton — Step 1.3 complete | Plan-to-roadmap conversion |
| 2026-03-02 | Stage 1 consolidation — compacted Steps 1.0–1.4 | Step 1.5 |
| 2026-03-03 | Stage 2+3 consolidation — full suite results + analysis pipeline | Step 3.3 |
