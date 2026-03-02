# Roadmap: Code Coverage Experiment

> **Created**: 2026-03-01
> **Last updated**: 2026-03-01
> **Status**: Stage 1 in progress (Steps 1.0–1.3 complete, Prereq 1.4a complete)

## Overview

Grow a code coverage improvement agent through 4 variants across 5 Spring Getting Started guides. Demonstrate that knowledge injection > prompt engineering > model choice. Stage 1 builds all infrastructure (invoker, judges, dataset). Stage 2 runs variants and collects data. Stage 3 analyzes results.

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
- **One fixed judge prompt (`prompts/judge-quality.txt`), applied identically to all variants.** Authored by reading the full KB and distilling best practices into concrete evaluation criteria. Static artifact — the judge does NOT read the KB at runtime.
- **Criteria come from the KB authorship, not from code.** The `TestQualityJudge` code is generic — it takes the prompt file path as input. If the KB evolves between experiment cycles, the judge prompt is updated as a deliberate versioned step.
- **Rewards built-in LLM knowledge**: if the model already knows `@WebMvcTest` without KB injection, it scores. The growth story shows what knowledge adds *on top of* what the model already knows.
- **KB is a forkable policy layer**: the experiment validates the mechanism (does KB injection produce measurable adherence?), not the opinions. Any team can fork the KB and get a matching judge.
- **Diagnostic feedback**: judge evidence strings map to improvement levers (knowledge gap, orchestration gap, tool gap, model gap) per the refactoring-agent `AIAnalyzer` pattern.

*Implementation (unchanged from v1–v3):*
- Agent-based judge (not `LLMJudge`): uses `AgentClient`/`ClaudeAgentModel` for filesystem navigation
- No `agent-judge-llm` dependency needed — reuse existing `spring-ai-agent-client` + `spring-ai-claude-agent`
- `NumericalScore.normalized()` (continuous 0-1) for gradient in analysis
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
- [ ] WRITE judge prompt (`prompts/judge-quality.txt`):
  - Author reads the full KB and distills best practices into fixed evaluation criteria
  - The prompt is a static artifact — the judge does NOT read the KB at runtime
  - Include concrete criteria with scoring rubric (what scores 0.2 vs 0.8)
  - Constrain output to JSON with per-criterion scores + evidence strings
  - Same prompt for all variants, all runs — the fixed quality bar
  - If the KB evolves between experiment cycles, update the judge prompt as a deliberate versioned step
- [ ] IMPLEMENT `TestQualityJudge` implementing `Judge` directly:
  - Constructor takes agent factory (functional interface), judge prompt path, pass threshold
  - `judge()`: copy workspace to temp dir for isolation
  - Check for test files; if none → `Judgment.fail()` score 0.0
  - Load judge prompt, invoke agent with JSON-only output constraint
  - Invoke agent synchronously (try/catch), use agent-level timeout if available
  - Parse outermost `{...}` from agent output, clamp scores to [0.0, 1.0]
  - Compute weighted average → `NumericalScore.normalized()`
  - Return `Judgment` with `Check` entries per criterion, raw scores in metadata
  - Error handling: agent failure or unparseable output → `Judgment.error()`
  - Clean up temp workspace copy
  - Judge code is generic — criteria are in the prompt file, not in Java
- [ ] WIRE UP `JuryFactory` to accept agent factory, register `TestQualityJudge` at Tier 3 with `FINAL_TIER` policy
- [ ] WRITE unit test `TestQualityJudgeTest`:
  - Mock agent factory → wire full fluent chain (`goal().workingDirectory().run()`)
  - Verify correct score computation (criteria from mock agent JSON response)
  - Verify no-test-files → FAIL with score 0.0
  - Verify malformed agent output → ERROR (not uncaught exception)
  - Verify agent exception → ERROR
  - Verify score clamping for out-of-range values
- [ ] VERIFY: `./mvnw compile` and `./mvnw test` pass

**Exit criteria**:
- [ ] TestQualityJudge compiles and passes tests
- [ ] All tests pass: `./mvnw test`
- [ ] Create: `plans/learnings/step-1.4-test-quality-judge.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: `TestQualityJudge.java`, `TestQualityJudgeTest.java`, updated `JuryFactory.java`

---

### Step 1.5: Stage 1 Consolidation and Review

**Entry criteria**:
- [ ] All Stage 1 steps complete (1.0–1.4)
- [ ] Read: all `plans/learnings/step-1.*` files from this stage

**Work items**:
- [ ] COMPACT learnings from all Stage 1 steps into `plans/learnings/LEARNINGS.md`
  - Key discoveries that changed the approach
  - Patterns established during implementation
  - Deviations from design with rationale
  - Common pitfalls to avoid in future stages
- [ ] UPDATE `CLAUDE.md` with distilled learnings from the full stage
- [ ] VERIFY project compiles and all tests pass: `./mvnw clean test`

**Exit criteria**:
- [ ] `LEARNINGS.md` updated with compacted summary covering Stage 1
- [ ] Create: `plans/learnings/step-1.5-stage1-summary.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Compacted `LEARNINGS.md`, stage summary, clean `CLAUDE.md`

---

## Stage 2: Variant Execution

### Step 2.0: Run Control Variant

**Entry criteria**:
- [ ] Stage 1 complete
- [ ] Read: `plans/learnings/LEARNINGS.md` — Stage 1 compacted learnings
- [ ] Read: `plans/learnings/step-1.5-stage1-summary.md` — stage summary

**Work items**:
- [ ] RUN control variant (v0-naive, no KB) on all 5 guides
- [ ] VERIFY results are stored correctly in results/ directory
- [ ] REVIEW baseline growth story
- [ ] RECORD baseline coverage numbers

**Exit criteria**:
- [ ] Control results in results/ directory
- [ ] Baseline coverage numbers recorded
- [ ] Create: `plans/learnings/step-2.0-control.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: Control variant results, baseline coverage data

---

### Step 2.1: Run All Variants

**Entry criteria**:
- [ ] Step 2.0 complete
- [ ] Read: `plans/learnings/step-2.0-control.md` — prior step learnings

**Work items**:
- [ ] RUN variant-a (v1-hardened, no KB)
- [ ] RUN variant-b (v2-with-kb, 3 KB files)
- [ ] RUN variant-c (v2-with-kb, 4 KB files)
- [ ] GENERATE growth story with all variant comparisons

**Exit criteria**:
- [ ] `analysis/growth-story.md` generated with all 4 variants
- [ ] Coverage improvement data validates hypothesis (KB > prompt > baseline)
- [ ] Create: `plans/learnings/step-2.1-results.md`
- [ ] Update `CLAUDE.md` with distilled learnings
- [ ] Update `ROADMAP.md` checkboxes
- [ ] COMMIT

**Deliverables**: All variant results, comparative growth story

---

### Step 2.2: Stage 2 Consolidation

**Entry criteria**:
- [ ] All Stage 2 steps complete (2.0–2.1)
- [ ] Read: all `plans/learnings/step-2.*` files from this stage

**Work items**:
- [ ] COMPACT learnings from Stage 2 into `plans/learnings/LEARNINGS.md`
- [ ] UPDATE `CLAUDE.md` with distilled learnings

**Exit criteria**:
- [ ] `LEARNINGS.md` updated with Stage 2 compacted summary
- [ ] Create: `plans/learnings/step-2.2-stage2-summary.md`
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
├── LEARNINGS.md                    # Tier 1: Compacted summary
├── step-1.3-dataset.md             # Tier 2: Per-step details
├── step-1.4-test-quality-judge.md
├── step-1.5-stage1-summary.md
├── step-2.0-control.md
├── step-2.1-results.md
├── step-2.2-stage2-summary.md
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
