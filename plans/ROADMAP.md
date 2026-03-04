# Roadmap: Code Coverage Experiment

> **Created**: 2026-03-01
> **Last updated**: 2026-03-03
> **Status**: Stage 4 in progress. Steps 4.0-4.2 complete (two-phase invoker implemented, prompts written, config updated). Step 4.3 next — vibe check on gs-rest-service from plain terminal.

## Overview

Grow a code coverage improvement agent through 5 variants across 5 Spring Getting Started guides. Test the hypothesis that `knowledge + structured execution > model`. Stage 1 builds all infrastructure (invoker, judges, dataset). Stage 2 wires bootstrap + knowledge injection, then runs variants and collects data. Stage 3 audits data quality then builds a DuckDB + Python analysis pipeline. **Stage 4** implements a two-phase explore-then-act variant (variant-d) to test whether structured knowledge consumption (Layer 1 + Layer 2) beats unstructured injection. **Stage 5** runs the full suite with all 5 variants + golden judge for thesis validation. **Stage 6** graduates the best variant, adds Pet Clinic for harder targets, and runs cross-model comparison (Haiku+KB vs Sonnet).

> **Before every commit**: Verify ALL exit criteria for the current step are met. Do NOT remove exit criteria to mark a step complete — fulfill them.

---

## Stage 1: Project Setup

### Step 1.0: Design Review

**Status**: Complete (scaffolded by `markpollack/forge`)

**Deliverables**: VISION.md, DESIGN.md, ROADMAP.md populated with domain content. Project compiles.

---

### Step 1.1: Implement AgentInvoker

**Status**: Complete

**Deliverables**: `CodeCoverageAgentInvoker` — measures baseline/final JaCoCo coverage, invokes agent via AgentClient, enriches metadata for judges.

---

### Step 1.2: Write Prompts and Knowledge

**Status**: Complete

**Deliverables**: 3 prompt files (v0-naive, v1-hardened, v2-with-kb), 4 knowledge files (coverage-fundamentals, jacoco-patterns, spring-test-slices, common-gaps), experiment-config.yaml with 4 variants.

---

### Step 1.3: Populate and Verify Dataset

**Status**: Complete

**Entry criteria**:
- [x] Steps 1.0–1.2 complete

**Work items**:
- [x] CLONE 5 Spring guide repos into dataset/workspaces/
- [x] VERIFY each guide's `complete/` subdirectory compiles: `./mvnw clean compile`
- [x] VERIFY existing tests pass: `./mvnw test`
- [x] CONFIGURE workspace materialization (git clone per item)

**Exit criteria**:
- [x] All 5 dataset items resolve and build
- [x] Create: `plans/learnings/step-1.3-dataset.md`
- [x] COMMIT

**Deliverables**: 5 verified dataset items with `dataset.json` manifest, `materialize.sh` script.

---

### Step 1.4a: Promote agent-journal and Wire Exhaust Capture (Prerequisite)

**Status**: Complete

**Rationale**: TestQualityJudge (Step 1.4) needs a full audit trail from its agent-based evaluation — tool calls, thinking blocks, cost, tokens. Three upstream gaps were blocking this:
1. Execution ledger (`tuvium-runtime-core`) was stuck under private `ai.tuvium` coordinates
2. Claude SDK capture bridge was buried inside `refactoring-agent`
3. `ClaudeAgentModel.call()` discarded everything except assistant text

**Work completed**:
- [x] Created `markpollack/agent-journal` repo (BSL licensed)
- [x] Migrated + repackaged `ai.tuvium:tracking-core` → `io.github.markpollack:journal-core` (279 tests)
- [x] Promoted `claude-sdk-capture` → `claude-code-capture` module in agent-journal (28 tests)
- [x] Added `Consumer<ParsedMessage> messageListener` to `ClaudeAgentModel` (all 3 consumption paths)
- [x] Pushed to `spring-ai-community/agent-client` main (compile-scope dependency, not test-only)
- [x] Updated `refactoring-agent` imports/deps to new coordinates
- [x] Added journal-core + claude-code-capture to this project's pom.xml
- [x] E2E integration test verified: thinking blocks, tool calls, tokens, cost all captured through full pipeline

**Artifacts installed locally**:
- `io.github.markpollack:journal-core:0.1.0-SNAPSHOT`
- `io.github.markpollack:claude-code-capture:0.1.0-SNAPSHOT`
- `org.springaicommunity.agents:spring-ai-claude-agent:0.10.0-SNAPSHOT` (with messageListener)

---

### Step 1.4: Implement TestQualityJudge (Tier 3)

**Entry criteria**:
- [x] Step 1.3 complete
- [x] Step 1.4a complete (agent-journal + messageListener available)
- [ ] Read: `plans/learnings/step-1.3-dataset.md` — prior step learnings
- [ ] Read: `plans/inbox/design-review.md` — reviewer feedback
- [ ] Read: `plans/JOURNAL.md` — critical insight on agent-based judge approach

**Design decisions** (from reviews v1–v4 + owner input):

*Judge philosophy — fixed quality bar (v5, supersedes v1–v4):*
- **One fixed judge prompt (`prompts/judge-practice-adherence.txt`), applied identically to all variants.** Authored by reading the full KB and distilling best practices into concrete evaluation criteria. Static artifact — the judge does NOT read the KB at runtime.
- **Criteria come from the KB authorship, not from code.** The `TestQualityJudge` code is generic — it takes the prompt file path as input. If the KB evolves between experiment cycles, the judge prompt is updated as a deliberate versioned step.
- **Rewards built-in LLM knowledge**: if the model already knows `@WebMvcTest` without KB injection, it scores. The growth story shows what knowledge adds *on top of* what the model already knows.
- **KB is a forkable policy layer**: the experiment validates the mechanism (does KB injection produce measurable adherence?), not the opinions. Any team can fork the KB and get a matching judge.
- **Diagnostic feedback**: judge evidence strings map to improvement levers (knowledge gap, orchestration gap, tool gap, model gap) per the refactoring-agent `AIAnalyzer` pattern.

*Implementation (unchanged from v1–v3):*
- Agent-based judge (not `LLMJudge`): uses `AgentClient`/`ClaudeAgentModel` for filesystem navigation
- No `agent-judge-llm` dependency needed — reuse existing `spring-ai-agent-client` + `spring-ai-claude-agent`
- Two dimensions reported separately, never combined: functional (T0–T2, deterministic) + practice adherence (T3, LLM)
- Adherence scores per-criterion (continuous 0-1) for gradient in analysis
- Pass threshold configurable (constructor param, default 0.5)
- Use stronger model for judging than experiment agent (configurable via `AgentModel` injection)
- Timeout: use `ClaudeAgentOptions.timeout(Duration.ofMinutes(3))` — confirmed native support, default 10 min (no CompletableFuture)
- Read-only judge: use `allowedTools(List.of("Read", "Glob", "Grep"))` with `yolo(false)` — confirmed in `ClaudeAgentOptions`
- `workingDirectory` priority: request-level > goal-level > builder default > cwd — set on request only for judge
- Copy workspace to temp dir before judging — isolate from session files, build artifacts
- Never ABSTAIN from FINAL_TIER: no test files = `Judgment.fail()` with score 0.0
- Agent prompt constrains output to JSON-only; parser extracts outermost `{...}` block
- Accept functional interface for agent creation (testability seam, avoids static factory mocking)
- Clamp criterion scores to [0.0, 1.0] during parsing
- **Exhaust capture** (resolved in Step 1.4a): `ClaudeAgentModel.messageListener` → `SessionLogParser` → `PhaseCapture` → `BaseRunRecorder` → journal-core Run events. Full pipeline verified end-to-end.

**Work items**:
- [x] CHECK `ClaudeAgentOptions` for timeout config and read-only/restricted mode — confirmed: `timeout(Duration)`, `allowedTools(List)`, `disallowedTools(List)`, `yolo(boolean)`
- [x] WRITE judge prompt (`prompts/judge-practice-adherence.txt`):
  - 6 criteria: test slice selection, assertion quality, error/edge case coverage, domain-specific patterns, coverage target selection, version-aware patterns
  - Each scored 0.0–1.0 with concrete anchors at 0.2/0.5/0.8/1.0
  - Companion traceability doc (`prompts/judge-practice-adherence-traceability.md`) maps criteria → source KB files
  - Refined: zero-tests escape hatch, N/A for absent domains, multi-file evidence format
- [x] IMPLEMENT `TestQualityJudge` implementing `Judge` directly:
  - Constructor takes `Function<Path, AgentClient>` factory, judge prompt path, pass threshold
  - Check for test files; if none → `Judgment.fail()` with `NumericalScore(0.0)`
  - Load judge prompt, invoke agent via factory, parse outermost `{...}` from output
  - Clamp scores to [0.0, 1.0], return `Judgment` with `Check` per criterion
  - Error handling: agent failure or unparseable output → `Judgment.error()`
  - Includes `defaultAgentClientFactory(model, timeout)` for read-only agent setup
- [x] WIRE UP `JuryFactory`: builder already supports `addJudge(3, judge)` + `tierPolicy(3, FINAL_TIER)`. Wiring at bootstrap with `TestQualityJudge.defaultAgentClientFactory()`.
- [x] WRITE unit test `TestQualityJudgeTest` (11 tests):
  - Valid JSON → correct scores and PASS/FAIL status
  - No test files → FAIL with score 0.0
  - Malformed output / missing criteria / empty criteria → ERROR
  - Out-of-range scores → clamped
  - Agent exception → ERROR
  - JSON embedded in text → extracted correctly
  - Custom pass threshold → respected
  - `parseJudgment()` directly testable (package-private)
- [x] VERIFY: `./mvnw compile` and `./mvnw test` — 11 tests pass

**Exit criteria**:
- [x] TestQualityJudge compiles and passes tests
- [x] All tests pass: `./mvnw test`
- [x] Create: `plans/learnings/step-1.4-test-quality-judge.md`
- [x] Update `CLAUDE.md` with distilled learnings
- [x] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: `TestQualityJudge.java`, `TestQualityJudgeTest.java`

---

### Step 1.5: Stage 1 Consolidation and Review

**Entry criteria**:
- [x] All Stage 1 steps complete (1.0–1.4)
- [x] Read: all `plans/learnings/step-1.*` files from this stage

**Work items**:
- [x] COMPACT learnings from all Stage 1 steps into `plans/learnings/LEARNINGS.md`
  - Key discoveries that changed the approach
  - Patterns established during implementation
  - Deviations from design with rationale
  - Common pitfalls to avoid in future stages
- [x] UPDATE `CLAUDE.md` with distilled learnings from the full stage
- [x] VERIFY project compiles and all tests pass: `./mvnw clean test`

**Exit criteria**:
- [x] `LEARNINGS.md` updated with compacted summary covering Stage 1
- [x] Create: `plans/learnings/step-1.5-stage1-summary.md`
- [x] Update `CLAUDE.md` with distilled learnings
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT (`7c459d4`)

**Deliverables**: Compacted `LEARNINGS.md`, stage summary, clean `CLAUDE.md`

---

## Stage 2: Variant Execution

### Step 2.0: Wire ExperimentApp Bootstrap

**Entry criteria**:
- [x] Stage 1 complete
- [x] Read: `plans/learnings/LEARNINGS.md` — Stage 1 compacted learnings
- [x] Read: `plans/learnings/step-1.5-stage1-summary.md` — stage summary

**Work items**:
- [x] IMPLEMENT `loadConfig(Path)` method — parse `experiment-config.yaml` via SnakeYAML into `ExperimentVariantConfig` with `FileSystemDatasetManager`
- [x] IMPLEMENT `main()` method — CLI parsing (`--variant <name>` / `--run-all-variants`), component wiring:
  - `FileSystemResultStore(projectRoot.resolve("results"))`
  - `JuryFactory` with 4 tiers: T0 `BuildSuccessJudge.maven("clean", "test")`, T1 `CoveragePreservationJudge()`, T2 `CoverageImprovementJudge()`, T3 `TestQualityJudge` with `defaultAgentClientFactory("claude-sonnet-4-6", 3min)`
  - `ExperimentApp` construction and dispatch
- [x] REFACTOR `ExperimentApp` to create per-variant `CodeCoverageAgentInvoker` in `runVariant()` — remove `AgentInvoker` from constructor (each variant may have different knowledge config)
- [x] VERIFY: `./mvnw compile` — all new imports resolve
- [x] VERIFY: `./mvnw test` — 11 existing tests still pass

**Exit criteria**:
- [x] `ExperimentApp.main()` is no longer a stub — can be invoked from CLI
- [x] Config loading parses all 4 variants from YAML
- [x] All tests pass: `./mvnw test`
- [x] Create: `plans/learnings/step-2.0-bootstrap.md`
- [x] Update `CLAUDE.md` with distilled learnings
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT (`e41b24b`)

**Deliverables**: Runnable `ExperimentApp` with full component wiring, CLI argument parsing

---

### Step 2.1: Add JIT Knowledge Injection

**Entry criteria**:
- [x] Step 2.0 complete
- [x] Read: `plans/learnings/step-2.0-bootstrap.md` — prior step learnings

**Work items**:
- [x] ADD optional knowledge config to `CodeCoverageAgentInvoker`:
  - Fields: `@Nullable Path knowledgeSourceDir`, `@Nullable List<String> knowledgeFiles`
  - New constructor alongside existing no-arg constructor
- [x] IMPLEMENT knowledge file copying in `invoke()` (after baseline measurement, before agent invocation):
  - If `knowledgeFiles` contains `index.md` → copy entire `knowledgeSourceDir` recursively (variant-c: full KB tree for JIT navigation)
  - Otherwise → copy only listed files preserving relative paths (variant-b: 3 targeted files)
  - Target: `workspace/knowledge/` directory
- [x] WIRE per-variant invoker creation in `ExperimentApp.runVariant()`:
  - `variant.knowledgeDir() != null` → `new CodeCoverageAgentInvoker(projectRoot.resolve(knowledgeDir), knowledgeFiles)`
  - Otherwise → `new CodeCoverageAgentInvoker()`
- [x] VERIFY: `./mvnw test` — existing tests pass, add test for knowledge file copying if feasible

**Exit criteria**:
- [x] Control/variant-a invoke with no knowledge (empty workspace)
- [x] variant-b copies 3 targeted files to `workspace/knowledge/coverage-mechanics/`
- [x] variant-c copies full KB tree to `workspace/knowledge/` (agent can JIT navigate from index.md)
- [x] All tests pass: `./mvnw test`
- [x] Create: `plans/learnings/step-2.1-knowledge-injection.md`
- [x] Update `CLAUDE.md` with distilled learnings
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT (`984254f`)

**Deliverables**: JIT knowledge injection via workspace file copying, per-variant invoker construction

---

### Step 2.2a: Dry-run Pipeline Validation

**Entry criteria**:
- [x] Step 2.1 complete
- [x] Read: `plans/learnings/step-2.1-knowledge-injection.md`

**Work items**:
- [x] FIX `JudgmentContextFactory` — add `invocationResult.metadata()` pass-through (upstream: experiment-core)
- [x] FIX `CoveragePreservationJudge` — accept String baseline with double parsing fallback (upstream: agent-judge-exec)
- [x] FIX `CoverageImprovementJudge` — accept String baseline with double parsing fallback (upstream: agent-judge-exec)
- [x] INSTALL upstream deps locally (`experiment-core`, `agent-judge-exec`)
- [x] ADD `--item <slug>` CLI filter via `SlugFilteringDatasetManager` wrapper
- [x] VERIFY: `./mvnw compile` — compiles with updated deps
- [x] VERIFY: `./mvnw test` — 17 existing tests pass

**Exit criteria**:
- [x] Metadata flows from InvocationResult → JudgmentContext (upstream fix installed)
- [x] Coverage judges accept string-encoded baselines (upstream fix installed)
- [x] `--item` CLI filter works for single-item smoke testing
- [x] Run instructions documented in CLAUDE.md
- [x] All tests pass: `./mvnw test`
- [x] Create: `plans/learnings/step-2.2a-pipeline-validation.md`
- [x] Update `CLAUDE.md` with distilled learnings
- [x] Update `ROADMAP.md` — insert Step 2.2a
- [x] COMMIT

**Deliverables**: Upstream metadata fixes, String baseline parsing, `--item` CLI filter, run instructions

---

### Step 2.2b: Wire Agent Exhaust Capture

**Entry criteria**:
- [x] Step 2.2a complete
- [x] Read: `plans/learnings/step-2.2a-pipeline-validation.md` — prior step learnings

**Context**: Control and variant-a runs completed but with zero exhaust visibility — `InvocationResult.phases` is always `List.of()`, tokens/cost are zeros. `ClaudeAgentModel.call()` discards all tool calls, thinking blocks, and cost data. The infrastructure exists but isn't wired: `SessionLogParser.parse()` can convert `Iterator<ParsedMessage>` → `PhaseCapture`, and `InvocationResult` already has a `List<PhaseCapture> phases` field.

Also discovered: `com.tuvium:claude-sdk-capture` (experiment-core) duplicates `io.github.markpollack:claude-code-capture` (agent-journal) — identical `PhaseCapture` record, different coordinates.

**Work items**:
- [x] FIX prompt log truncation in `DefaultClaudeSyncClient.java` (line 220): change `Math.min(50, ...)` to `Math.min(200, ...)` and log full length
  - Repo: `~/community/claude-agent-sdk-java`
  - `./mvnw install -DskipTests`
- [x] WIRE `SessionLogParser.parse()` in `ClaudeAgentModel.call()` (lines 247-270):
  - Replace manual `Iterator<ParsedMessage>` text-only loop with `SessionLogParser.parse(response, "agent-run", prompt)`
  - Store resulting `PhaseCapture` in `AgentResponseMetadata` providerFields under key `"phaseCapture"`
  - Also populate `inputTokens`, `outputTokens`, `thinkingTokens`, `totalCostUsd` from capture
  - Added `TeeingIterator` inner class for backward-compatible `messageListener` support
  - Repo: `~/community/agent-client/agent-models/spring-ai-claude-agent`
- [x] ADD typed accessor `getPhaseCapture()` on `AgentClientResponse`
  - Returns `PhaseCapture` from `agentResponse.getMetadata().get("phaseCapture")`
  - Repo: `~/community/agent-client/spring-ai-agent-client`
  - `./mvnw install -DskipTests` (agent-client)
- [x] CONSOLIDATE PhaseCapture coordinates in experiment-core:
  - Remove: `com.tuvium:claude-sdk-capture` dependency
  - Add: `io.github.markpollack:claude-code-capture:0.1.0-SNAPSHOT`
  - Update imports: `com.tuvium.claude.capture` → `io.github.markpollack.journal.claude` (7 files: 5 main + 2 test + 1 inline FQN)
  - Repo: `~/tuvium/projects/tuvium-experiment-driver/experiment-core`
  - `./mvnw install -pl experiment-core -DskipTests` — 372 tests pass
- [x] UPDATE `CodeCoverageAgentInvoker` to extract and forward phases:
  - Extract `PhaseCapture` from `AgentClientResponse.getPhaseCapture()`
  - Pass to `InvocationResult.completed(phases, inputTokens, outputTokens, ...)`
  - Repo: `~/projects/code-coverage-experiment`
- [x] VERIFY: `./mvnw test` — 21 tests pass
- [x] SMOKE TEST: `--variant control --item gs-rest-service` — verify `invocationResult.phases` is non-empty in results JSON with tool calls, thinking, non-zero tokens/cost

**Exit criteria**:
- [x] Results JSON contains structured agent exhaust (phases with tool calls, thinking blocks)
- [x] Token counts and cost are non-zero in InvocationResult
- [x] PhaseCapture coordinates consolidated to `io.github.markpollack:claude-code-capture`
- [x] All tests pass: `./mvnw test` (21 tests + 372 experiment-core tests)
- [x] Create: `plans/learnings/step-2.2b-exhaust-capture.md`
- [x] Update `CLAUDE.md` with distilled learnings (folded into Stage 3 consolidation)
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT (across multiple commits: upstream changes + `e7696fd`)

**Deliverables**: End-to-end agent exhaust capture, consolidated PhaseCapture coordinates

---

### Step 2.2c: Golden Test Comparison Judge

**Status**: Complete

**Context**: SWE-bench style evaluation — compare agent-written tests against reference golden tests from Spring developers using JavaParser AST analysis.

**Work items**:
- [x] ADD `com.github.javaparser:javaparser-core:3.26.3` to pom.xml
- [x] IMPLEMENT `GoldenTestComparisonJudge` — 5 structural dimensions (test_method_coverage, annotation_alignment, import_alignment, assertion_style, injection_pattern)
- [x] WIRE to tier 2 in `ExperimentApp.buildJuryFactory()` with `REJECT_ON_ANY_FAIL` policy
- [x] WRITE 13 unit tests in `GoldenTestComparisonJudgeTest`
- [x] VERIFY: `./mvnw test` — 34 tests pass

**Exit criteria**:
- [x] GoldenTestComparisonJudge compiles and passes tests
- [x] All tests pass: `./mvnw test` — 34 tests
- [x] COMMIT (`311a5ad`)

**Deliverables**: `GoldenTestComparisonJudge.java`, `GoldenTestComparisonJudgeTest.java`

---

### Step 2.2: Golden Dataset Pivot + Full Suite Run

**Status**: Complete

**Context**: Original dataset had 71-92% baseline coverage — agents did nothing. Pivoted to SWE-bench style: strip all tests, save Spring developers' tests as reference, agents write from scratch (0% baseline).

**Work items**:
- [x] UPDATE `materialize.sh` — save reference tests + strip test sources from `before/`
- [x] REWRITE prompts for "write from scratch" (v0-naive, v1-hardened, v2-with-kb)
- [x] UPDATE `CodeCoverageAgentInvoker` — zero-baseline prompt, skip baseline build when no tests
- [x] WIRE `DefaultEfficiencyEvaluator` via `EfficiencyConfig.defaults()` in `ExperimentApp`
- [x] SWITCH model to `claude-sonnet-4-6`
- [x] RUN full suite: `./mvnw compile exec:java -Dexec.args="--run-all-variants"` (1h39m)
- [x] VERIFY results: all 4 variants × 5 guides produced results with phases, tokens, cost

**Results summary**:
| Variant | Pass Rate | Avg T3 | Avg Efficiency | Cost |
|---------|-----------|--------|----------------|------|
| Control | 100% | 0.62 | 0.878 | $4.57 |
| Variant-A | 100% | 0.80 | 0.937 | $4.17 |
| Variant-B | 100% | 0.697 | 0.837 | $4.98 |
| Variant-C | 100% | 0.757 | 0.823 | $4.55 |

**Known issues** (resolved in Step 3.0):
- ~~Efficiency scores missing from variant-b/c~~ — was a run selection bug (stale overlapping results in index.json)
- Coverage metadata in `invocationResult.metadata`, not `item.metadata` — handled in ETL

**Exit criteria**:
- [x] All 4 variants run with full exhaust capture
- [x] Results in `results/code-coverage-experiment/`
- [x] Create: `plans/learnings/step-2.2-full-suite.md`
- [x] Update `CLAUDE.md` with distilled learnings (folded into Stage 3 consolidation)
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT (`8c13d2b`, `f317194`, `0e93a8d`)

**Deliverables**: Full suite results (20 items), golden dataset, efficiency scoring

---

### Step 2.3: Stage 2 Consolidation

**Entry criteria**:
- [x] All Stage 2 steps complete (2.0–2.2c)
- [x] Read: all `plans/learnings/step-2.*` files from this stage

**Work items**:
- [x] COMMIT golden judge + outstanding uncommitted changes (`311a5ad`)
- [x] COMPACT learnings from Stage 2 into `plans/learnings/LEARNINGS.md` (folded into Step 3.3)
- [x] UPDATE `CLAUDE.md` with distilled learnings

**Exit criteria**:
- [x] All uncommitted work committed
- [x] `LEARNINGS.md` updated with Stage 2 compacted summary (done in Step 3.3)
- [x] Create: `plans/learnings/step-2.3-stage2-summary.md`
- [x] Update `CLAUDE.md` with distilled learnings
- [x] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Updated `LEARNINGS.md` covering Stages 1-2, clean git state

---

## Stage 3: Data Analysis Pipeline

### Step 3.0: Commit + Investigate Efficiency Gap

**Entry criteria**:
- [x] Stage 2 complete
- [x] Read: `plans/learnings/LEARNINGS.md` — compacted learnings through Stage 2
- [x] Read: `plans/inbox/python-data-analysis-stack.md` — analysis pipeline plan
- [x] Read: `plans/inbox/golden-judge-handoff.md` — golden judge context

**Context**: Full suite run complete (4 variants × 5 guides, Sonnet). Two data quality issues to resolve before analysis: (1) efficiency scores missing from variant-b/c, (2) golden judge uncommitted. All results use the latest full-suite run (timestamps 07:16-08:32 UTC 2026-03-03).

**Work items**:
- [x] COMMIT golden judge + outstanding uncommitted changes (pom.xml, GoldenTestComparisonJudge.java, GoldenTestComparisonJudgeTest.java, ExperimentApp.java)
- [x] INVESTIGATE efficiency gap: why `efficiency.*` scores absent from variant-b/c:
  - Root cause: stale overlapping run entries in index.json — not a code bug
  - All 4 variants have efficiency scores when using correct run IDs
- [x] DOCUMENT findings in `analysis/data-quality-notes.md`

**Exit criteria**:
- [x] All uncommitted work committed (`311a5ad`)
- [x] Efficiency gap root cause identified — run selection bug, not missing data
- [x] Create: `plans/learnings/step-3.0-data-quality.md`
- [x] Update `CLAUDE.md` with distilled learnings
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT (`311a5ad`)

**Deliverables**: Clean git state, efficiency gap analysis, `analysis/data-quality-notes.md`

---

### Step 3.1: Python Environment + ETL

**Entry criteria**:
- [x] Step 3.0 complete
- [x] Read: `plans/learnings/step-3.0-data-quality.md` — prior step learnings
- [x] Read: `plans/inbox/python-data-analysis-stack.md` — full analysis plan

**Reference** (patterns to follow):
- `/home/mark/tuvium/projects/spring-ai-project-maint/scripts/parse_git_history.py` — raw → parquet ETL
- `/home/mark/tuvium/projects/spring-ai-project-maint/scripts/compute_mvi.py` — DuckDB query → parquet
- Pattern: plain Python, no CLI framework, `duckdb.connect()` in-process, `if __name__ == "__main__": main()`

**Work items**:
- [x] CREATE `requirements.txt`: `duckdb`, `pandas>=2.0`, `matplotlib>=3.7`, `numpy>=1.24`
- [x] SETUP Python env: `uv venv && uv pip install -r requirements.txt`
- [x] CREATE directories: `data/curated/`, `analysis/figures/`, `analysis/tables/`, `analysis/cards/`
- [x] ADD to `.gitignore`: `data/curated/`, `*.parquet`, `.venv/`
- [x] WRITE `scripts/load_results.py` — ETL: read result JSON → normalize into 3 parquet files:

  **`data/curated/runs.parquet`** — one row per variant run:
  `run_id, variant, model, timestamp, pass_rate, total_cost_usd, total_duration_ms, item_count, run_group`

  **`data/curated/item_results.parquet`** — one row per item per run:
  `run_id, variant, item_slug, passed, cost_usd, duration_ms`
  Coverage: `coverage_baseline, coverage_final, coverage_delta` (from `invocationResult.metadata`, parse string→float)
  Scores: `t0_build, t1_preservation, t2_improvement, t3_adherence` (from `scores` map)
  Efficiency: `eff_build_errors, eff_cost, eff_recovery_cycles, eff_composite` (nullable)
  Golden: `golden_similarity` (nullable — only after re-run with golden judge)
  Tokens: `input_tokens, output_tokens, thinking_tokens`

  **`data/curated/judge_details.parquet`** — one row per judge criterion per item:
  `run_id, item_slug, judge_name, criterion_name, score, evidence`
  Extracted from `verdict.subVerdicts[].individual[].checks[]`

  **Key field mappings**:
  - `item.scores["CommandJudge"]` → `t0_build`
  - `item.scores["CoveragePreservationJudge"]` → `t1_preservation`
  - `item.scores["CoverageImprovementJudge"]` → `t2_improvement`
  - `item.scores["Judge#1"]` → `t3_adherence`
  - `item.scores["efficiency.composite"]` → `eff_composite`
  - `item.invocationResult.metadata.finalCoverage` → `coverage_final`

  **Run selection**: latest `index.json` entry per variant. Tag `run_group = "full-suite-2026-03-03"`.

- [x] VERIFY: run ETL, check parquet with `duckdb.sql("SELECT variant, count(*) FROM '...' GROUP BY variant")`
- [x] VERIFY: 5 items per variant, 20 total rows

**Exit criteria**:
- [x] Python environment working (`uv venv` + dependencies)
- [x] `scripts/load_results.py` produces 3 parquet files
- [x] DuckDB queries work against parquet
- [x] Create: `plans/learnings/step-3.1-etl.md`
- [x] Update `CLAUDE.md` with Python env + run instructions
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT (`50a634f`)

**Deliverables**: Python env, `scripts/load_results.py`, parquet files in `data/curated/`

---

### Step 3.2: Variant Comparison + Visualization

**Entry criteria**:
- [x] Step 3.1 complete (parquet files available)
- [x] Read: `plans/learnings/step-3.1-etl.md` — prior step learnings

**Reference**:
- `/home/mark/tuvium/projects/spring-ai-project-maint/scripts/plot_quadrants.py` — DuckDB → matplotlib

**Work items**:
- [x] WRITE `scripts/variant_comparison.py` — core ablation analysis
- [x] WRITE `scripts/plot_variant_radar.py` — radar/spider chart
- [x] WRITE `scripts/generate_item_cards.py` — per-item detail cards
- [x] RUN all scripts, review outputs

**Exit criteria**:
- [x] `analysis/tables/variant-comparison.md` — aggregate scores
- [x] `analysis/figures/variant-radar.png` — three-dimension radar
- [x] `analysis/cards/*.md` — per-item detail cards (5 items)
- [x] Create: `plans/learnings/step-3.2-variant-comparison.md`
- [x] Update `CLAUDE.md` with distilled learnings
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT (`50a634f`)

**Deliverables**: Variant comparison table, radar chart, item cards

---

### Step 3.3: Stage 3 Consolidation

**Entry criteria**:
- [x] All Stage 3 steps complete (3.0–3.2)
- [x] Read: all `plans/learnings/step-3.*` files from this stage

**Work items**:
- [x] COMPACT learnings from Stage 3 into `plans/learnings/LEARNINGS.md`
- [x] UPDATE `CLAUDE.md` with distilled learnings
- [x] WRITE `analysis/findings-summary.md` — executive summary

**Exit criteria**:
- [x] `LEARNINGS.md` updated with Stage 3 compacted summary
- [x] `analysis/findings-summary.md` — one-page findings summary
- [x] Create: `plans/learnings/step-3.3-stage3-summary.md`
- [x] Update `CLAUDE.md` with distilled learnings
- [x] Update `ROADMAP.md` checkboxes
- [x] COMMIT (`cb2976a`)

**Deliverables**: Updated `LEARNINGS.md` covering Stages 1-3, findings summary

---

## Stage 4: Two-Phase Variant (variant-d)

> **Rationale**: Initial results show KB injection (variant-b, variant-c) doesn't beat the hardened prompt (variant-a) on practice quality. The two-layer value model predicts this: knowledge without structured orchestration doesn't help. The fix is a two-phase explore-then-act variant proven in the refactoring agent (80%→100% improvement). See `plans/inbox/two-phase-variant-and-next-steps.md` for full analysis.

### Step 4.0: Context Review

**Entry criteria**:
- [x] Stage 3 complete
- [x] Read: `plans/learnings/LEARNINGS.md` — compacted learnings through Stage 3
- [x] Read: `analysis/findings-summary.md` — key findings from Stage 3 analysis
- [x] Read: `plans/inbox/two-phase-variant-and-next-steps.md` — two-phase design + rationale
- [x] Read: `plans/inbox/session-continuity-for-two-phase.md` — ClaudeSyncClient decision + rationale

**Work items**:
- [x] REVIEW Stage 3 findings: variant-a (hardened prompt, no KB) beats variant-b/c (with KB) on T3 adherence
- [x] REVIEW two-layer value model diagnosis: current KB variants have Layer 2 (knowledge) without structured Layer 1 (orchestration)
- [x] REVIEW refactoring-agent two-phase pattern (ClaudeSyncClient connect→query)
- [x] VERIFY `ClaudeSyncClient` is available in `claude-agent-sdk-java` dependency (session continuity between explore and act phases)
- [x] VERIFY `claude-code-capture` dependency already in pom.xml (provides `SessionLogParser.parse()`)
- [x] DOCUMENT design decisions in learnings

**Exit criteria**:
- [x] ClaudeSyncClient API understood and available
- [x] Create: `plans/learnings/step-4.0-context-review.md`
- [x] Update `ROADMAP.md` checkboxes

**Deliverables**: Design decisions documented, ClaudeSyncClient API confirmed

---

### Step 4.1: Implement Two-Phase CodeCoverageAgentInvoker

**Entry criteria**:
- [x] Step 4.0 complete
- [x] Read: `plans/learnings/step-4.0-context-review.md` — prior step learnings

**Context**: The current `CodeCoverageAgentInvoker.invoke()` makes one `AgentClient` call. The two-phase variant needs two turns in one `ClaudeSyncClient` session so the agent retains understanding between explore and act. Pattern proven in `RefactoringAgent.java`.

**Implementation approach**: Extract `AbstractCoverageAgentInvoker` base class with shared pre/post steps (compile check, JaCoCo injection, baseline measurement, knowledge copy, final coverage measurement, metadata enrichment). Two subclasses:
- `CodeCoverageAgentInvoker` — single `AgentClient` call (existing, refactored to extend base)
- `TwoPhaseCodeCoverageAgentInvoker` — two `ClaudeSyncClient` turns (new)

**Work items**:
- [x] ADD `claude-code-sdk` dependency to `pom.xml`
- [x] ADD `actPromptFile` field to `VariantSpec` record (nullable, with `isTwoPhase()` convenience method)
- [x] UPDATE `ExperimentVariantConfig` to parse `actPromptFile` from YAML
- [x] EXTRACT `AbstractCoverageAgentInvoker` base class from `CodeCoverageAgentInvoker`
- [x] REFACTOR `CodeCoverageAgentInvoker` to extend base class (verified no behavioral change — 34 tests pass)
- [x] IMPLEMENT `TwoPhaseCodeCoverageAgentInvoker` extending base class
- [x] UPDATE `ExperimentApp.createInvoker()` — dispatch on `variant.isTwoPhase()` → two-phase invoker
- [x] VERIFY: `./mvnw test` — all 34 existing tests pass after refactoring

**Exit criteria**:
- [x] Two-phase invoker compiles and integrates with ExperimentApp
- [x] All tests pass: `./mvnw test` — 34 tests
- [x] Create: `plans/learnings/step-4.1-two-phase-invoker.md`
- [x] Update `ROADMAP.md` checkboxes

**Deliverables**: `AbstractCoverageAgentInvoker`, refactored `CodeCoverageAgentInvoker`, `TwoPhaseCodeCoverageAgentInvoker`, `VariantSpec.actPromptFile`

---

### Step 4.2: Explore + Act Prompts

**Entry criteria**:
- [x] Step 4.1 complete
- [x] Read: `plans/learnings/step-4.1-two-phase-invoker.md` — prior step learnings

**Work items**:
- [x] WRITE `prompts/v3-explore.txt` — explore phase prompt:
  - Read project structure and source code
  - Read knowledge files in `knowledge/` (start with index.md routing table)
  - Analyze what the application does
  - Write `TEST_PLAN.md` with: which test classes to create and why, which Spring test annotations to use and why, which assertion patterns apply, which edge cases to test
  - Do NOT write any test code yet
- [x] WRITE `prompts/v3-act.txt` — act phase prompt:
  - Read `TEST_PLAN.md`
  - Implement tests according to plan
  - Run `./mvnw test` to verify compilation and passing
- [x] ADD variant-d to `experiment-config.yaml`:
  ```yaml
  - name: variant-d
    promptFile: v3-explore.txt
    actPromptFile: v3-act.txt
    knowledgeDir: knowledge
    knowledgeFiles:
      - index.md
  ```
- [x] VERIFY: `./mvnw compile` — config parses, all imports resolve

**Exit criteria**:
- [x] Both prompt files written and reviewed
- [x] Variant-d in experiment-config.yaml
- [x] Config parsing works (`./mvnw compile` succeeds)
- [x] Create: `plans/learnings/step-4.2-prompts.md`
- [x] Update `ROADMAP.md` checkboxes

**Deliverables**: `v3-explore.txt`, `v3-act.txt`, variant-d config entry

---

### Step 4.3: Single-Item Vibe Check (gs-rest-service)

**Entry criteria**:
- [x] Step 4.2 complete
- [ ] Read: `plans/learnings/step-4.2-prompts.md` — prior step learnings

**Context**: Before committing to a multi-hour full suite run, validate the two-phase approach on the cleanest signal item. gs-rest-service is most representative (simple REST controller), has clear golden standard (2 @Test methods, @SpringBootTest + @AutoConfigureMockMvc + MockMvc), and runs fastest.

**Work items**:
- [ ] RUN: `./mvnw compile exec:java -Dexec.args="--variant variant-d --item gs-rest-service"` (from plain terminal, ~15 min)
- [ ] INSPECT `TEST_PLAN.md` in workspace — did the agent absorb KB guidance?
  - Does it mention `@WebMvcTest` or `@SpringBootTest`? (KB recommendation)
  - Does it mention `MockMvc`? (golden standard pattern)
  - Does it identify edge cases from KB? (e.g., error handling, custom parameters)
- [ ] COMPARE variant-d scores against variant-a on gs-rest-service:
  - T3 practice adherence (primary metric)
  - Golden test comparison (new — first real data for this judge)
  - Coverage delta
  - Cost (two-phase may be more expensive due to double invocation)
- [ ] RUN analysis: update `load_results.py` with variant-d run ID, regenerate comparison table
- [ ] DECIDE: does variant-d beat variant-a on gs-rest-service? If yes → proceed to full suite. If no → diagnose (inspect TEST_PLAN.md, check context utilization) before committing hours.

**Exit criteria**:
- [ ] Variant-d vibe check complete on gs-rest-service
- [ ] TEST_PLAN.md inspected for KB absorption evidence
- [ ] Comparison against variant-a documented
- [ ] Go/no-go decision for full suite run
- [ ] Create: `plans/learnings/step-4.3-vibe-check.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Vibe check results, TEST_PLAN.md analysis, go/no-go decision

---

### Step 4.4: Stage 4 Consolidation

**Entry criteria**:
- [ ] All Stage 4 steps complete (4.0–4.3)
- [ ] Read: all `plans/learnings/step-4.*` files from this stage

**Work items**:
- [ ] COMPACT learnings from Stage 4 into `plans/learnings/LEARNINGS.md`
- [ ] UPDATE `CLAUDE.md` with distilled learnings
- [ ] DOCUMENT: two-phase pattern findings, TEST_PLAN.md as diagnostic artifact, ClaudeSyncClient integration notes

**Exit criteria**:
- [ ] `LEARNINGS.md` updated with Stage 4 compacted summary
- [ ] Create: `plans/learnings/step-4.4-stage4-summary.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Updated `LEARNINGS.md` covering Stages 1-4, stage summary

---

## Stage 5: Full Suite Re-Run + Comparative Analysis

> **Rationale**: Run all 5 variants (control, variant-a through variant-d) with golden judge active. This produces the complete dataset: golden_similarity scores (missing from initial run), variant-d two-phase results, and a clean comparison of all approaches. One run, all data.

### Step 5.0: Full Suite Run (5 variants, golden judge active)

**Entry criteria**:
- [ ] Stage 4 complete (variant-d validated on gs-rest-service)
- [ ] Read: `plans/learnings/LEARNINGS.md` — compacted learnings through Stage 4
- [ ] Read: `plans/learnings/step-4.3-vibe-check.md` — vibe check results
- [ ] Go decision from Step 4.3

**Work items**:
- [ ] VERIFY `GoldenTestComparisonJudge` is wired in `ExperimentApp.buildJuryFactory()` tier 2
- [ ] VERIFY all 5 variants present in `experiment-config.yaml`
- [ ] VERIFY `./mvnw test` — all tests pass
- [ ] RUN full suite: `./mvnw compile exec:java -Dexec.args="--run-all-variants"` (from plain terminal, ~2+ hours)
- [ ] VERIFY results: all 5 variants × 5 guides produced results with phases, tokens, cost, golden_similarity

**Exit criteria**:
- [ ] All 5 variants × 5 items have complete results including golden_similarity
- [ ] Results in `results/code-coverage-experiment/`
- [ ] Create: `plans/learnings/step-5.0-full-suite-rerun.md`
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Complete 25-item result set (5 variants × 5 guides) with all scores

---

### Step 5.1: Updated Analysis Pipeline

**Entry criteria**:
- [ ] Step 5.0 complete
- [ ] Read: `plans/learnings/step-5.0-full-suite-rerun.md` — prior step learnings

**Work items**:
- [ ] UPDATE `scripts/load_results.py` — add variant-d run IDs, run group tag
- [ ] ADD `golden_similarity` column to ETL (from `GoldenTestComparisonJudge` scores)
- [ ] RE-RUN full analysis pipeline: `load_results.py` → `variant_comparison.py` → `plot_variant_radar.py` → `generate_item_cards.py`
- [ ] ADD variant-d to radar chart and comparison table
- [ ] REVIEW golden_similarity scores — do they correlate with T3? Do they add signal beyond practice adherence?
- [ ] REVIEW variant-d vs variant-a — the thesis test: does structured L1 + rich L2 beat good implicit L1, no L2?

**Exit criteria**:
- [ ] Analysis outputs include variant-d and golden_similarity
- [ ] Thesis test result documented (variant-d vs variant-a)
- [ ] Create: `plans/learnings/step-5.1-analysis-update.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Updated analysis tables, radar chart, item cards with all 5 variants + golden scores

---

### Step 5.2: Thesis Validation Analysis

**Entry criteria**:
- [ ] Step 5.1 complete
- [ ] Read: `plans/learnings/step-5.1-analysis-update.md` — prior step learnings

**Work items**:
- [ ] ANSWER key thesis questions from the data:
  - Q1: Does structured knowledge consumption (variant-d) beat unstructured (variant-b/c)?
  - Q2: Does variant-d beat the hardened prompt (variant-a)?
  - Q3: Does TEST_PLAN.md show higher golden_similarity? (knowledge absorption → expert-like patterns)
  - Q4: What's the cost trade-off? (two-phase may cost more but produce better quality)
  - Q5: Where does the two-phase approach help most? (per-item delta analysis)
- [ ] WRITE `analysis/thesis-validation.md` — structured findings answering each question with data
- [ ] IDENTIFY next experiment iteration priorities:
  - If variant-d wins → scale to Pet Clinic, cross-model comparison
  - If variant-d loses → diagnose (inspect TEST_PLAN.md artifacts, consider prompt refinement)

**Exit criteria**:
- [ ] `analysis/thesis-validation.md` written with data-backed conclusions
- [ ] Next iteration priorities identified
- [ ] Create: `plans/learnings/step-5.2-thesis-validation.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Thesis validation analysis, next iteration priorities

---

### Step 5.3: Stage 5 Consolidation

**Entry criteria**:
- [ ] All Stage 5 steps complete (5.0–5.2)
- [ ] Read: all `plans/learnings/step-5.*` files from this stage

**Work items**:
- [ ] COMPACT learnings from Stage 5 into `plans/learnings/LEARNINGS.md`
- [ ] UPDATE `CLAUDE.md` with distilled learnings
- [ ] UPDATE `analysis/findings-summary.md` with thesis validation results

**Exit criteria**:
- [ ] `LEARNINGS.md` updated with Stage 5 compacted summary
- [ ] `analysis/findings-summary.md` updated
- [ ] Create: `plans/learnings/step-5.3-stage5-summary.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Updated `LEARNINGS.md` covering Stages 1-5, updated findings summary

---

## Stage 6: Graduation + Future Iterations

### Step 6.0: Pet Clinic Dataset Expansion

**Entry criteria**:
- [ ] Stage 5 complete
- [ ] Read: `plans/learnings/LEARNINGS.md` — compacted learnings through Stage 5
- [ ] Variant-d validated and best variant identified

**Context**: Pet Clinic has genuine complexity (multi-layer: controller → service → repository, multiple domain entities, form handling, validation, mixed web + JPA testing). Simple guides hit 85-100% coverage easily — ceiling effects make it hard to discriminate variants. Pet Clinic is where KB advantage should be most visible.

**Work items**:
- [ ] UPDATE `materialize.sh` — add per-item source URL support (different GitHub org, no `complete/` subdirectory)
- [ ] ADD spring-petclinic to `dataset/items.yaml`
- [ ] MATERIALIZE and verify: `./materialize.sh`
- [ ] ADD Pet Clinic-specific knowledge to `knowledge/` (if needed)
- [ ] RUN best variant + variant-d on Pet Clinic
- [ ] COMPARE results against simple guides — does complexity reveal KB advantage?

**Exit criteria**:
- [ ] Pet Clinic dataset item working
- [ ] Results compared against simple guides
- [ ] Create: `plans/learnings/step-6.0-pet-clinic.md`
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Pet Clinic as dataset item, comparative results

---

### Step 6.1: Cross-Model Variant

**Entry criteria**:
- [ ] Step 6.0 complete (or can run in parallel with Pet Clinic)
- [ ] Read: `plans/learnings/step-6.0-pet-clinic.md`

**Context**: The ultimate thesis test — does `Haiku + KB + structured execution` beat `Sonnet/Opus + no KB`? This transforms "knowledge helps" into "knowledge + cheap model beats expensive model."

**Work items**:
- [ ] CONFIGURE cross-model variants in experiment-config.yaml (Haiku + variant-d, Opus + control)
- [ ] RUN cross-model comparison on gs-rest-service first (vibe check)
- [ ] IF promising → run on full suite + Pet Clinic
- [ ] ANALYZE cost-vs-quality: Haiku+KB cost vs Sonnet/Opus cost with equivalent quality

**Exit criteria**:
- [ ] Cross-model comparison data collected
- [ ] Cost-vs-quality analysis documented
- [ ] Create: `plans/learnings/step-6.1-cross-model.md`
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Cross-model comparison results, cost-quality analysis

---

### Step 6.2: Graduate Best Variant

**Entry criteria**:
- [ ] Steps 6.0-6.1 complete (or at minimum Stage 5 thesis validation done)

**Work items**:
- [ ] EXTRACT best variant → standalone agent project
- [ ] PACKAGE for ACP marketplace (deferred)
- [ ] DOCUMENT the final configuration that won

**Exit criteria**:
- [ ] Best variant extracted as standalone project
- [ ] Create: `plans/learnings/step-6.2-graduation.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Standalone agent project from best variant

---

### Step 6.3: Stage 6 Consolidation

**Entry criteria**:
- [ ] All Stage 6 steps complete
- [ ] Read: all `plans/learnings/step-6.*` files

**Work items**:
- [ ] COMPACT learnings from Stage 6 into `plans/learnings/LEARNINGS.md`
- [ ] WRITE final experiment report: `analysis/experiment-report.md`

**Exit criteria**:
- [ ] `LEARNINGS.md` covers all stages
- [ ] Final experiment report written
- [ ] Create: `plans/learnings/step-6.3-stage6-summary.md`
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Complete `LEARNINGS.md`, final experiment report

---

## Learnings Structure

```
plans/learnings/
├── LEARNINGS.md                       # Tier 1: Compacted summary (Stages 1-3, updated each stage)
├── step-1.3-dataset.md                # Tier 2: Per-step details
├── step-1.4-test-quality-judge.md
├── step-1.5-stage1-summary.md
├── step-2.0-bootstrap.md
├── step-2.1-knowledge-injection.md
├── step-2.2a-pipeline-validation.md
├── step-2.2b-exhaust-capture.md
├── step-2.2-full-suite.md
├── step-2.3-stage2-summary.md
├── step-3.0-data-quality.md
├── step-3.1-etl.md
├── step-3.2-variant-comparison.md
├── step-3.3-stage3-summary.md
├── step-4.0-context-review.md         # Stage 4: Two-Phase Variant
├── step-4.1-two-phase-invoker.md
├── step-4.2-prompts.md
├── step-4.3-vibe-check.md
├── step-4.4-stage4-summary.md
├── step-5.0-full-suite-rerun.md       # Stage 5: Full Suite Re-Run
├── step-5.1-analysis-update.md
├── step-5.2-thesis-validation.md
├── step-5.3-stage5-summary.md
├── step-6.0-pet-clinic.md             # Stage 6: Graduation + Future
├── step-6.1-cross-model.md
├── step-6.2-graduation.md
└── step-6.3-stage6-summary.md
```

---

## Conventions

### Commit Convention

Every step ends with a git commit. Use this format:

```
Step X.Y: Brief description of what was done
```

### Step Entry Criteria Convention

Every step's entry criteria must include:

```markdown
- [ ] Previous step complete
- [ ] Read: `plans/learnings/step-{PREV}-{topic}.md` — prior step learnings
```

### Step Exit Criteria Convention

Every step's exit criteria must include:

```markdown
- [ ] All tests pass: `./mvnw test`
- [ ] Create: `plans/learnings/step-X.Y-topic.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT
```

---

## Revision History

| Timestamp | Change | Trigger |
|-----------|--------|---------|
| 2026-03-01 | Initial — Steps 1.0-1.2 complete via forge bootstrapping session | Genesis session |
| 2026-03-01 | Step 1.3 complete — dataset populated, verified, materialization configured | Dataset setup session |
| 2026-03-01 | Upgraded to Forge methodology format, expanded Step 1.4, added consolidation steps | Plan-to-roadmap conversion |
| 2026-03-01 | Step 1.4 design finalized after 4 review rounds; confirmed ClaudeAgentOptions API (timeout, allowedTools, workingDirectory priority) | Design review v4 sign-off |
| 2026-03-01 | Step 1.4a complete — agent-journal created, claude-code-capture promoted, messageListener added to ClaudeAgentModel, e2e IT verified | Exhaust capture prerequisite |
| 2026-03-02 | Step 1.4 judge design v4 — fixed quality bar replaces adaptive two-phase design; 3 criteria (assertion quality, slice usage, edge cases), same prompt for all variants; updated VISION + DESIGN | Judge design review v4 |
| 2026-03-02 | Judge design v5 — criteria derived from KB (not hardcoded); KB as forkable policy layer; diagnostic feedback loop; thesis sharpened to "knowledge + orchestration > model"; cross-model follow-on planned | Online review session + AIAnalyzer pattern |
| 2026-03-02 | Pre-flight review: split scoring (functional + adherence, never combined); "practice adherence" framing; thesis as hypothesis under investigation; planned iterations (Pet Clinic, cross-model, SWE-bench) | Pre-flight validity review |
| 2026-03-02 | Judge rubric authored: 6 criteria distilled from 13 KB files into `prompts/judge-practice-adherence.txt` + traceability doc. Refined with zero-tests escape, N/A domains, evidence format. | Rubric authoring session |
| 2026-03-02 | Step 1.4 complete: TestQualityJudge implemented (11 tests passing), JuryFactory wiring ready, learnings captured | Implementation session |
| 2026-03-02 | Step 1.5 complete: Stage 1 consolidated — LEARNINGS.md compacted, CLAUDE.md updated, all tests pass | Consolidation session |
| 2026-03-02 | Stage 2 expanded: added bootstrap wiring (2.0-2.1) before experiment runs (2.2-2.3); JIT knowledge injection design | Plan-to-roadmap conversion |
| 2026-03-02 | Step 2.2a complete: pipeline validation — fixed metadata pass-through, String baseline parsing, added --item CLI filter | Pipeline validation session |
| 2026-03-03 | Fixed VerdictExtractor to recurse into subVerdicts — all 4 tiers now surface in results | Variant-a debugging |
| 2026-03-03 | Ran control (60% pass, 3/5) and variant-a (40% pass, 2/5) — zero exhaust data, can't diagnose failures | Initial variant runs |
| 2026-03-03 | Added Step 2.2b: wire agent exhaust capture via SessionLogParser + consolidate PhaseCapture coordinates | Agent exhaust gap discovered |
| 2026-03-03 | Replaced Stage 3 stubs with full data analysis pipeline (Steps 3.0-3.4) + Stage 4 graduation. Added Step 3.0 data audit for missing phases/efficiency/journal data. DuckDB + Python stack based on spring-ai-project-maint pattern. | Plan-to-roadmap from python-data-analysis-stack.md |
| 2026-03-03 | Marked Stage 2 complete (full suite run done). Added Step 2.2c (golden judge). Consolidated Step 2.3/2.4 into 2.3. Replaced Stage 3 with detailed ETL + analysis steps. Step 3.0 = commit + efficiency gap. Step 3.1 = Python ETL. Step 3.2 = variant comparison + visualization. Step 3.3 = consolidation. | Post-full-suite-run analysis planning |
| 2026-03-03 | Added Stage 4 (two-phase variant-d), Stage 5 (full suite re-run with golden judge + thesis validation), Stage 6 (Pet Clinic, cross-model, graduation). Driven by finding that KB injection didn't beat hardened prompt — two-layer value model diagnosis → structured L1 + L2 needed. | Research conversation session — Stripe analysis + two-phase design |
