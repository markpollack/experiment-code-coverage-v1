# Roadmap: Code Coverage Experiment

> **Created**: 2026-03-01
> **Last updated**: 2026-03-03
> **Status**: Stage 2 in progress. Step 2.2b (agent exhaust capture) code complete — wired across 4 repos. Smoke test pending. Next: Step 2.2 (re-run control with exhaust capture).

## Overview

Grow a code coverage improvement agent through 4 variants across 5 Spring Getting Started guides. Test the hypothesis that knowledge injection > prompt engineering > model choice. Stage 1 builds all infrastructure (invoker, judges, dataset). Stage 2 wires bootstrap + knowledge injection, then runs variants and collects data. Stage 3 audits data quality then builds a DuckDB + Python analysis pipeline (ETL, variant comparison, growth story, sensitivity analysis) following the spring-ai-project-maint pattern. Stage 4 graduates the best variant. This experiment calibrates the methodology; harder targets and cross-model comparison are planned iterations.

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
- [ ] SMOKE TEST: `--variant control --item gs-rest-service` — verify `invocationResult.phases` is non-empty in results JSON with tool calls, thinking, non-zero tokens/cost

**Exit criteria**:
- [ ] Results JSON contains structured agent exhaust (phases with tool calls, thinking blocks)
- [ ] Token counts and cost are non-zero in InvocationResult
- [x] PhaseCapture coordinates consolidated to `io.github.markpollack:claude-code-capture`
- [x] All tests pass: `./mvnw test` (21 tests + 372 experiment-core tests)
- [ ] Create: `plans/learnings/step-2.2b-exhaust-capture.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: End-to-end agent exhaust capture, consolidated PhaseCapture coordinates

---

### Step 2.2: Re-run Control Variant (with exhaust capture)

**Entry criteria**:
- [ ] Step 2.2b complete
- [ ] Read: `plans/learnings/step-2.2b-exhaust-capture.md` — prior step learnings
- [ ] Dataset materialized: `./dataset/materialize.sh --verify` passes

**Prior runs (without exhaust)**:
- Control: 60% pass rate (3/5). Baselines 57-92%. Agent added trivial `main()` tests.
- Variant-a: 40% pass rate (2/5). Hardened prompt prevented trivial tests but agent couldn't find alternatives on 3 items.
- Both runs have zero exhaust data — can't diagnose failures.

**Work items**:
- [ ] RUN control variant: `~/scripts/claude-run-stream.sh "./mvnw compile exec:java -Dexec.args='--variant control'"`
  - Model: claude-haiku-4-5-20251001, timeout: 15 min/item, prompt: v0-naive.txt, no knowledge
- [ ] VERIFY results contain non-empty phases with tool calls, thinking, tokens, cost
- [ ] REVIEW agent exhaust for gs-rest-service — what did the agent actually do?
- [ ] RECORD baseline coverage, jury verdicts (T0-T3), and exhaust summary per item

**Exit criteria**:
- [ ] Control results in `results/` with full exhaust capture
- [ ] Agent behavior documented from exhaust data
- [ ] Create: `plans/learnings/step-2.2-control.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Control variant results with full agent exhaust, behavior analysis

---

### Step 2.3: Run All Variants

**Entry criteria**:
- [ ] Step 2.2 complete
- [ ] Read: `plans/learnings/step-2.2-control.md` — prior step learnings (especially pipeline issues)

**Work items**:
- [ ] RUN variant-a: `--variant variant-a` (v1-hardened prompt, no KB)
- [ ] RUN variant-b: `--variant variant-b` (v2-with-kb prompt, 3 KB files)
- [ ] RUN variant-c: `--variant variant-c` (v2-with-kb prompt, full KB via index.md)
- [ ] ALTERNATIVELY: `--run-all-variants` (runs all 4 sequentially with growth story generation)
- [ ] VERIFY growth story generated at `analysis/growth-story.md`
- [ ] RECORD per-variant: pass rate, coverage improvement, T3 practice adherence scores, cost, tokens

**Exit criteria**:
- [ ] All 4 variants run successfully (or failures documented)
- [ ] `analysis/growth-story.md` generated with all variant comparisons
- [ ] Coverage data validates or refutes hypothesis (KB > prompt > baseline)
- [ ] Create: `plans/learnings/step-2.3-results.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: All variant results, comparative growth story

---

### Step 2.4: Stage 2 Consolidation

**Entry criteria**:
- [ ] All Stage 2 steps complete (2.0–2.3)
- [ ] Read: all `plans/learnings/step-2.*` files from this stage

**Work items**:
- [ ] COMPACT learnings from Stage 2 into `plans/learnings/LEARNINGS.md`
  - Bootstrap patterns (config loading, component wiring, per-variant invokers)
  - JIT knowledge injection approach and results
  - Pipeline issues encountered and resolved
  - Experimental results summary (hypothesis confirmed/refuted?)
  - Cost/performance observations
- [ ] UPDATE `CLAUDE.md` with distilled learnings

**Exit criteria**:
- [ ] `LEARNINGS.md` updated with Stage 2 compacted summary
- [ ] Create: `plans/learnings/step-2.4-stage2-summary.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Updated `LEARNINGS.md` covering Stages 1-2

---

## Stage 3: Data Analysis Pipeline

### Step 3.0: Audit and Debug Result Data

**Entry criteria**:
- [ ] Stage 2 complete (all variants run)
- [ ] Read: `plans/learnings/LEARNINGS.md` — compacted learnings through Stage 2
- [ ] Read: `plans/inbox/python-data-analysis-stack.md` — analysis pipeline plan

**Context**: Results data has known gaps from the evolving pipeline. Early control/variant-a runs (pre-exhaust-capture) have zero phases, zero tokens, zero cost. Efficiency scores are only present in runs after the `DefaultEfficiencyEvaluator` was wired. Current audit (2026-03-03):

| Variant | Items | With phases | With efficiency |
|---------|-------|-------------|-----------------|
| control | 23 | 15 | 7 |
| variant-a | 20 | 15 | 5 |
| variant-b | 12 | 12 | 5 |
| variant-c | 12 | 12 | 5 |

Agent journal data (tool calls, thinking blocks, cost breakdowns within phases) may also be incomplete or absent in early runs.

**Work items**:
- [ ] WRITE `scripts/audit_results.py` — scan all result JSON files and report:
  - Per-variant: total items, items with phases, items with efficiency scores, items with non-zero tokens/cost
  - Per-item: which fields are populated vs zero/empty
  - Identify which runs are pre-exhaust-capture vs post (timestamp boundary)
  - Identify which runs are pre-efficiency-evaluator vs post
  - Check agent journal data completeness (tool calls, thinking blocks within phases)
- [ ] RUN audit script, save output to `analysis/data-audit.md`
- [ ] DECIDE: re-run incomplete variants with current pipeline, or mark early runs as "pre-instrumentation" and exclude from analysis?
  - Re-running is cleaner but costs agent invocation time
  - Excluding is faster but reduces sample size
- [ ] IF RE-RUNNING: execute re-runs for items missing phases/efficiency scores
- [ ] IF EXCLUDING: document exclusion criteria so `load_results.py` can filter
- [ ] VERIFY: all retained data has complete phases, tokens, cost, and efficiency scores

**Exit criteria**:
- [ ] `scripts/audit_results.py` written and working
- [ ] `analysis/data-audit.md` documents the state of all result data
- [ ] Decision made and documented: re-run vs exclude
- [ ] All retained result data is complete (no zero-phase, zero-token gaps)
- [ ] Create: `plans/learnings/step-3.0-data-audit.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: `scripts/audit_results.py`, `analysis/data-audit.md`, clean dataset for analysis

---

### Step 3.1: Python Environment and ETL

**Entry criteria**:
- [ ] Step 3.0 complete (clean, audited result data)
- [ ] Read: `plans/learnings/step-3.0-data-audit.md` — prior step learnings
- [ ] Read: `plans/inbox/python-data-analysis-stack.md` — analysis pipeline plan

**Reference**: Study the spring-ai-project-maint patterns before writing:
- `/home/mark/tuvium/projects/spring-ai-project-maint/plans/DESIGN.md` — data model, DuckDB schema, design decisions
- `/home/mark/tuvium/projects/spring-ai-project-maint/scripts/parse_git_history.py` — raw → parquet ETL pattern
- `/home/mark/tuvium/projects/spring-ai-project-maint/scripts/compute_mvi.py` — DuckDB query → parquet output pattern

**Work items**:
- [ ] CREATE `requirements.txt` in project root: `duckdb`, `pandas>=2.0`, `matplotlib>=3.7`, `numpy>=1.24`
- [ ] CREATE Python venv: `uv venv && uv pip install -r requirements.txt`
- [ ] CREATE directory structure: `data/curated/`, `analysis/figures/`, `analysis/tables/`, `analysis/cards/`
- [ ] ADD `data/curated/` and `*.parquet` to `.gitignore`
- [ ] WRITE `scripts/load_results.py` — ETL: read all `results/**/index.json` → normalize into:
  - `data/curated/runs.parquet` (run_id, variant, model, timestamp, config)
  - `data/curated/item_results.parquet` (per-item: coverage, scores, efficiency, cost, tokens)
  - `data/curated/judge_scores.parquet` (per-criterion detail)
  - Apply exclusion filters from Step 3.0 audit
- [ ] VERIFY: run `scripts/load_results.py`, inspect parquet files with ad-hoc DuckDB queries
- [ ] VERIFY: row counts match expected items per variant

**Exit criteria**:
- [ ] Python environment set up with all dependencies
- [ ] `scripts/load_results.py` produces clean parquet files from result JSON
- [ ] DuckDB can query parquet files (verify with simple SELECT)
- [ ] Create: `plans/learnings/step-3.1-etl.md`
- [ ] Update `CLAUDE.md` with distilled learnings (Python env, run instructions)
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Python environment, `scripts/load_results.py`, parquet files in `data/curated/`

---

### Step 3.2: Variant Comparison and Thesis Queries

**Entry criteria**:
- [ ] Step 3.1 complete (parquet files available)
- [ ] Read: `plans/learnings/step-3.1-etl.md` — prior step learnings

**Reference**: Study visualization patterns:
- `/home/mark/tuvium/projects/spring-ai-project-maint/scripts/plot_quadrants.py` — DuckDB → matplotlib
- `/home/mark/tuvium/projects/spring-ai-project-maint/scripts/sensitivity_analysis.py` — weight sweep

**Work items**:
- [ ] WRITE `scripts/variant_comparison.py` — core ablation analysis:
  - Per-variant aggregates: avg coverage gain, adherence, efficiency, cost, build success %
  - Per-variant per-item breakdown
  - Output: `analysis/tables/variant-comparison.md`
- [ ] RUN thesis queries (conversational analysis workflow — tell Claude what to see, iterate):
  - Q1: Does knowledge improve correctness? (coverage delta by variant)
  - Q2: Does knowledge improve efficiency? (efficiency composite by variant)
  - Q3: Does model matter less than knowledge? (expensive+no-KB vs cheap+full-KB)
  - Q5: Where does knowledge help most? (per-item delta control vs variant-c)
- [ ] WRITE `scripts/plot_variant_radar.py` — radar/spider chart: one polygon per variant showing correctness, adherence, efficiency
- [ ] WRITE `scripts/plot_cost_vs_quality.py` — scatter: cost (x) vs coverage gain (y), colored by variant
- [ ] GENERATE figures to `analysis/figures/`

**Exit criteria**:
- [ ] `analysis/tables/variant-comparison.md` — aggregate scores across all variants
- [ ] `analysis/figures/variant-radar.png` — three-dimensional comparison
- [ ] `analysis/figures/cost-vs-quality.png` — cost/quality tradeoff
- [ ] Thesis queries answered with data (documented in analysis/)
- [ ] Create: `plans/learnings/step-3.2-variant-comparison.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Variant comparison tables, radar charts, cost/quality plots, thesis query answers

---

### Step 3.3: Growth Story and Sensitivity Analysis

**Entry criteria**:
- [ ] Step 3.2 complete
- [ ] Read: `plans/learnings/step-3.2-variant-comparison.md` — prior step learnings

**Work items**:
- [ ] WRITE `scripts/growth_story.py` — track improvement across iterations of the growth loop (run → judge → add knowledge → run again). Line chart showing scores over iterations.
  - Q4: What's the growth loop trajectory? (iteration-over-iteration for variant-c)
- [ ] WRITE `scripts/sensitivity_analysis.py` — vary efficiency weights, check if variant ordering is stable. Follow the spring-ai-project-maint pattern.
- [ ] WRITE `scripts/generate_item_cards.py` — per-item detail cards (markdown): coverage before/after, judge scores, efficiency breakdown, agent trajectory summary
- [ ] GENERATE cards to `analysis/cards/`
- [ ] REVIEW item cards for outliers (items where variant-c underperformed control — why?)

**Exit criteria**:
- [ ] `analysis/figures/growth-trajectory.png` — iteration-over-iteration improvement
- [ ] `analysis/tables/sensitivity.md` — weight sensitivity results
- [ ] `analysis/cards/*.md` — per-item detail cards
- [ ] Outlier analysis documented
- [ ] Create: `plans/learnings/step-3.3-growth-sensitivity.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Growth trajectory chart, sensitivity analysis, per-item cards, outlier analysis

---

### Step 3.4: Stage 3 Consolidation

**Entry criteria**:
- [ ] All Stage 3 steps complete (3.0–3.3)
- [ ] Read: all `plans/learnings/step-3.*` files from this stage

**Work items**:
- [ ] COMPACT learnings from Stage 3 into `plans/learnings/LEARNINGS.md`
  - Data quality issues discovered and resolved
  - ETL patterns (JSON → parquet → DuckDB)
  - Key findings from variant comparison (hypothesis confirmed/refuted?)
  - Conversational data science workflow observations
  - Which visualizations were most useful for the narrative
- [ ] UPDATE `CLAUDE.md` with distilled learnings
- [ ] WRITE `analysis/findings-summary.md` — executive summary of all analysis findings

**Exit criteria**:
- [ ] `LEARNINGS.md` updated with Stage 3 compacted summary
- [ ] `analysis/findings-summary.md` — one-page summary of key findings
- [ ] Create: `plans/learnings/step-3.4-stage3-summary.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Updated `LEARNINGS.md` covering Stages 1-3, findings summary

---

## Stage 4: Graduation

### Step 4.0: Graduate Best Variant

**Entry criteria**:
- [ ] Stage 3 complete
- [ ] Read: `plans/learnings/LEARNINGS.md` — compacted learnings through Stage 3
- [ ] Read: `analysis/findings-summary.md` — key findings

**Work items**:
- [ ] EXTRACT best variant → standalone agent project
- [ ] PACKAGE for ACP marketplace (deferred)

**Exit criteria**:
- [ ] Best variant extracted
- [ ] Create: `plans/learnings/step-4.0-graduation.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Standalone agent project from best variant

**Planned future iterations** (out of scope for this run, design in from the start):
- Pet Clinic + harder repos — genuine complexity where KB advantage matters
- Cross-model variant (Haiku+KB vs Sonnet/Opus with no KB) — transforms "knowledge helps" into "knowledge + cheap model beats expensive model"
- SWE-bench Lite (N=150) — paper-grade evidence with external ground truth (resolve rate), zero circularity

---

## Learnings Structure

```
plans/learnings/
├── LEARNINGS.md                       # Tier 1: Compacted summary (Stages 1-3)
├── step-1.3-dataset.md                # Tier 2: Per-step details
├── step-1.4-test-quality-judge.md
├── step-1.5-stage1-summary.md
├── step-2.0-bootstrap.md
├── step-2.1-knowledge-injection.md
├── step-2.2a-pipeline-validation.md
├── step-2.2b-exhaust-capture.md
├── step-2.2-control.md
├── step-2.3-results.md
├── step-2.4-stage2-summary.md
├── step-3.0-data-audit.md
├── step-3.1-etl.md
├── step-3.2-variant-comparison.md
├── step-3.3-growth-sensitivity.md
├── step-3.4-stage3-summary.md
└── step-4.0-graduation.md
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
