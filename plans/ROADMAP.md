# Roadmap: Code Coverage Experiment

> **Created**: 2026-03-01
> **Last updated**: 2026-03-02
> **Status**: Stage 1 complete (Steps 1.0–1.5). Ready for Stage 2.

## Overview

Grow a code coverage improvement agent through 4 variants across 5 Spring Getting Started guides. Test the hypothesis that knowledge injection > prompt engineering > model choice. Stage 1 builds all infrastructure (invoker, judges, dataset). Stage 2 wires bootstrap + knowledge injection, then runs variants and collects data. Stage 3 analyzes results. This experiment calibrates the methodology; harder targets and cross-model comparison are planned iterations.

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
- [ ] COMMIT

**Deliverables**: JIT knowledge injection via workspace file copying, per-variant invoker construction

---

### Step 2.2: Run Control Variant

**Entry criteria**:
- [ ] Step 2.1 complete
- [ ] Read: `plans/learnings/step-2.1-knowledge-injection.md` — prior step learnings
- [ ] Dataset materialized: `./dataset/materialize.sh --verify` passes

**Work items**:
- [ ] RUN control variant: `java -jar target/...jar --variant control`
  - Model: claude-haiku-4-5-20251001, timeout: 15 min/item, prompt: v0-naive.txt, no knowledge
  - 5 items × 15 min max = up to 75 min worst case
- [ ] VERIFY results stored in `results/` directory as JSON
- [ ] REVIEW preserved workspaces — did agent write tests? do they compile?
- [ ] RECORD baseline coverage numbers (per item: before/after line%, branch%)
- [ ] RECORD jury verdicts — which tiers pass/fail for each item?
- [ ] DIAGNOSE any failures — did the agent hit timeout? did builds break? did JaCoCo report correctly?

**Exit criteria**:
- [ ] Control results in `results/` directory
- [ ] Baseline coverage numbers recorded in learnings
- [ ] Jury verdicts recorded (T0-T3 per item)
- [ ] Any pipeline issues identified and fixed
- [ ] Create: `plans/learnings/step-2.2-control.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Control variant results, baseline coverage data, pipeline validation

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

## Stage 3: Analysis and Graduation

### Step 3.0: Analyze Results

**Entry criteria**:
- [ ] Stage 2 complete
- [ ] Read: `plans/learnings/LEARNINGS.md` — compacted learnings through Stage 2

**Work items**:
- [ ] ANALYZE growth story for patterns
- [ ] IDENTIFY which knowledge file had most impact
- [ ] DOCUMENT findings in `analysis/` directory

**Exit criteria**:
- [ ] Analysis documented in `analysis/`
- [ ] Create: `plans/learnings/step-3.0-analysis.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Analysis documentation, pattern identification

**Planned future iterations** (out of scope for this run, design in from the start):
- Pet Clinic + harder repos — genuine complexity where KB advantage matters
- Cross-model variant (Haiku+KB vs Sonnet/Opus with no KB) — transforms "knowledge helps" into "knowledge + cheap model beats expensive model"
- SWE-bench Lite (N=150) — paper-grade evidence with external ground truth (resolve rate), zero circularity

---

### Step 3.1: Graduate Best Variant

**Entry criteria**:
- [ ] Step 3.0 complete
- [ ] Read: `plans/learnings/step-3.0-analysis.md` — prior step learnings

**Work items**:
- [ ] EXTRACT best variant → standalone agent project
- [ ] PACKAGE for ACP marketplace (deferred)

**Exit criteria**:
- [ ] Best variant extracted
- [ ] Create: `plans/learnings/step-3.1-graduation.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Standalone agent project from best variant

---

## Learnings Structure

```
plans/learnings/
├── LEARNINGS.md                    # Tier 1: Compacted summary (Stages 1-2)
├── step-1.3-dataset.md             # Tier 2: Per-step details
├── step-1.4-test-quality-judge.md
├── step-1.5-stage1-summary.md
├── step-2.0-bootstrap.md
├── step-2.1-knowledge-injection.md
├── step-2.2-control.md
├── step-2.3-results.md
├── step-2.4-stage2-summary.md
├── step-3.0-analysis.md
└── step-3.1-graduation.md
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
