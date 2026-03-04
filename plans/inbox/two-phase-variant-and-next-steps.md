# Two-Phase Variant Design + Experiment Strategy Reset

> Written from research-conversation-agent session, 2026-03-03. Context: initial 4-variant results show KB injection (variant-b, variant-c) doesn't beat hardened prompt (variant-a) on practice quality. 5-hour runtime for full suite. Need a faster iteration loop before committing to multi-hour runs.

---

## The Problem: KB Injection Didn't Help As Expected

Best fully-passing 5-item runs:

| Variant | KB? | Practice Quality (Judge#1) | Final Coverage | Cost |
|---------|-----|---------------------------|----------------|------|
| Control | No | 0.620 | 95.6% | $4.57 |
| Variant-A (hardened prompt) | No | **0.803** | 86.1% | ~$4.65 |
| Variant-B (3 KB files) | Yes | 0.697 | 95.6% | $4.98 |
| Variant-C (full KB + JIT nav) | Yes | 0.757 | 87.9% | $4.55 |

Variant-A wins practice quality without any KB. KB variants beat control but don't beat the hardened prompt alone. The full suite took ~5 hours. Model is Sonnet throughout — haven't even tested Haiku+KB vs Sonnet-no-KB yet.

---

## Why This Happened — The Research Explains It

Three mechanisms from the academic literature predict this outcome:

### 1. Context Pollution / "Dumb Zone" (Horthy, AIE Summit 2025)

Dex Horthy (Human Layer CEO) quantified: **around 40% context utilization, diminishing returns begin**. The current KB variants dump 5 files (~14KB) into the workspace and hope the agent reads them at the right time. That's context budget spent on orientation rather than action.

SWE-agent measured this precisely (Yang et al. 2024): 30-line window → 14.3% resolve rate. 100-line optimal window → **18.0%**. Full file visible → **12.7%** — *worse than no file*. More context is not better. There's a Goldilocks zone.

Qodo's production data: 60% of all MCP tool calls go to context MCPs, not action MCPs. Agents spend most of their time *learning about the situation*, not *acting on it*. The KB variants are adding learning burden without structuring how the agent consumes it.

### 2. The Two-Layer Value Model Predicts This

**Origin**: Hypothesis H5 ("MiniAgent hypothesis") from the Feb 5 working session, refined on Feb 13 when GAIA evidence forced a critical distinction. Formalized as a finding on Feb 25 (`tuvium-agentic-patterns-research/findings/two-layer-value-model.md`).

**The refinement event**: GAIA (Mialon et al. 2023) showed GPT-4 with plugins scores only **15%** vs humans at **92%**. If H5 were simply "more knowledge = better," GAIA should have worked. It didn't. The session distinguished:

- **Per-step reasoning** (Layer 2 — knowledge): KB can substitute for this. If the agent knows the API diff, it doesn't need to reason about syntax.
- **Multi-step orchestration** (Layer 1 — structured execution): KB does NOT substitute for this. GAIA's agents have knowledge access but can't reliably sequence 10+ steps.

**The diagnostic table**:

| Configuration | Prediction | Evidence |
|---|---|---|
| L1 yes, L2 no | Reliable execution, domain-inappropriate results | Copilot migration: 100% coverage, 39.75% test pass |
| L1 no, L2 yes | Correct knowledge, unreliable composition | GAIA: GPT-4 + plugins = 15% |
| L1 yes, L2 yes | Reliable + domain-appropriate | RefAgent: 90% test pass, LLM-agnostic |
| L1 no, L2 no | Neither | Baseline GPT-4 on SWE-bench: 1.96% |

**Key evidence sources**: RefAgent ablation (removing context retrieval degrades performance even with architecture preserved — both layers independently necessary), Copilot migration (100% syntactic coverage but 39.75% correctness — L1 without L2), SWE-bench trajectory (38x improvement, ~3-5x from model, rest from scaffolding), Cemri et al. (1,642 traces, 44.7% of failures are system design — +15.6% from architectural change alone).

**The thesis connection**: `knowledge + structured execution > model` maps directly to `L2 + L1 > raw model capability`. The two-layer model is the empirical substrate of the thesis equation.

**What this means for the experiment**: The current KB variants have **Layer 2 without structured Layer 1** for knowledge consumption. We dump files and hope. The hardened prompt (variant-a) has **better implicit Layer 1** — the prompt structures how the agent thinks, even without KB. We need to add structured orchestration around knowledge consumption.

### 3. Passive vs. Active Knowledge Activation (Stripe vs. Tuvium)

Stripe's "Minions" system (1,300+ PRs/week, zero human-written code, hundreds of millions of lines of Ruby) solves the same problem differently.

**Stripe's rules files**: Scoped markdown files (`.mdc` format, Cursor-compatible) with glob-pattern frontmatter. When an agent enters a directory matching the glob, the relevant rules file is **automatically injected into context**. No agent decision required.

```
.<tool-name>/
  rules/
    <rule-name>.mdc    # frontmatter: { globs: ["src/payments/**"] }
```

Key properties:
- **Passive activation**: The agent harness monitors file access patterns and injects matching rules automatically. No `index.md` navigation, no routing table lookup, no agent decision about what to read.
- **Scoped, not global**: "Due to the size of the repository they can't have unconditional job rules." Each rules file applies only to its glob-matched directories.
- **Standardized**: Same rule files consumed by Minions (unattended), Cursor (interactive), and Claude Code (interactive). Three tools, one knowledge source.
- **No explicit "read the KB" step**: The agent just works on the task. Context arrives as a side effect of which files it touches.

**Tuvium's routing tables (current approach)**: Active navigation — the agent must decide to read `index.md`, understand the routing table, identify relevant knowledge directories, read those files, then start working. That's several turns of cognitive overhead before any productive work begins.

**The gap**: Stripe's approach requires zero agent turns for knowledge acquisition. Ours requires 4-7 file reads (root index → topic index → detail files) before the agent can start the actual task. On small guides where the model's built-in knowledge is already decent, the navigation cost may exceed the knowledge benefit.

**Stripe's blueprint engine** adds structured orchestration (Layer 1) on top: workflows interleave deterministic steps (lint, test, git) with agent reasoning steps. Quote from IndyDevDan analysis: "Agents plus code beats agents alone, and agents plus code beats code alone." The agent never runs open-ended — each step has constrained scope and tools.

**What we can borrow**: We can't easily implement passive glob-triggered activation (that requires harness-level integration). But we **can** implement the other half of Stripe's approach — structured orchestration via plan-then-execute phases. And we can reduce navigation cost by pre-loading relevant KB content in the explore prompt rather than making the agent discover it.

---

## The Fix: Two-Phase Explore-Then-Act Variant

The refactoring agent already implements this and saw **80% → 100% pass rate improvement** when the pattern was introduced (documented in `step-3.2-developer-prompting-insight.md`).

### How It Works in the Refactoring Agent

```java
try (ClaudeSyncClient client = ClaudeClient.sync(options).workingDirectory(workingDir).build()) {
    // Phase 1: Explore — read project + knowledge, produce understanding
    client.connect(explorePrompt);
    explorePhase = SessionLogParser.parse(client.receiveResponse(), "explore", explorePrompt);

    // Phase 2: Act — execute task using session memory from Phase 1
    client.query(actPrompt);
    actPhase = SessionLogParser.parse(client.receiveResponse(), "act", actPrompt);
}
```

Two turns in one `ClaudeSyncClient` session. Claude retains full memory between turns. The explore phase reads all files + knowledge and produces a prose summary. The act phase says "Based on your project analysis, apply this change now."

Key design insight: *"This is the same principle behind chain-of-thought prompting: decompose a hard problem into easier sub-problems. The difference is that explore-then-act is enforced structurally (separate SDK turns) rather than hoping the model will self-decompose."*

### Adapted for Code-Coverage Experiment

**New variant (variant-d or variant-c-v2)** with two `AgentClient` calls:

**Phase 1 — Explore + Plan**:
"Read the project structure. Read the knowledge files in `knowledge/`. Analyze the existing source code to understand what the application does. Based on the knowledge and the project structure, write a test plan to `TEST_PLAN.md`:
- Which test classes to create and why
- Which Spring test annotations to use (e.g., @WebMvcTest vs @SpringBootTest) and why
- Which assertion patterns apply to this project type
- Which edge cases the reference knowledge suggests testing
Do NOT write any test code yet."

**Phase 2 — Execute**:
"Read `TEST_PLAN.md`. Implement the tests according to your plan. Run `./mvnw test` to verify they compile and pass."

### Why This Should Work

1. **Solves context pollution**: The explore phase consumes KB content and compresses it into a focused plan. The act phase works from the compressed plan, staying in the "smart zone."

2. **Provides Layer 1 for knowledge consumption**: Instead of hoping the agent reads the right files at the right time, we structurally enforce that knowledge reading happens before code writing.

3. **Parallels Stripe's blueprint engine**: Deterministic structure (phase separation) + agent reasoning (within each phase). "The model does not run the system. The system runs the model."

4. **The plan artifact is observable**: `TEST_PLAN.md` shows whether the agent actually absorbed the KB guidance. If the plan mentions `@WebMvcTest` and `MockMvc` (from the KB), we know knowledge flowed. If it doesn't, we know the agent ignored the KB — diagnostic signal we don't currently have.

5. **Matches Horthy's RPI workflow**: Research (read project + KB) → Plan (write TEST_PLAN.md) → Implement (write tests). Between each phase: intentional compaction. He demonstrated this producing a one-shot PR on a 300,000-line Rust codebase.

### Implementation in CodeCoverageAgentInvoker

The current `invoke()` method makes one `AgentClient` call. The two-phase variant would:

1. First call: explore prompt with KB content referenced (or pre-loaded in prompt)
2. Agent writes `TEST_PLAN.md` to workspace
3. Second call: act prompt referencing the plan
4. Agent writes tests, runs `./mvnw test`

This requires either:
- Two separate `AgentClient` invocations (simpler, but loses session context)
- Switching to `ClaudeSyncClient` for session continuity (matches refactoring-agent pattern, preserves context between phases)

The `ClaudeSyncClient` approach is better — the agent retains its understanding of the project structure from Phase 1. The refactoring agent already proves this pattern at `~/tuvium/projects/refactoring-agent/agent/src/main/java/com/tuvium/agent/RefactoringAgent.java`.

---

## Experiment Strategy: Single-Item Exploration First

### The Problem with Running Everything

- 5 items × 4 variants = 20 agent runs
- ~5 hours for a full sweep
- Model not yet varied (all Sonnet so far)
- No data analysis pipeline built yet
- Can't tell if changes helped without the analysis stack

### Recommended Single Item: `gs-rest-service`

Why this one:
- **Most representative**: simple REST controller, the canonical Spring Boot app
- **Known reference**: 2 @Test methods, @SpringBootTest + @AutoConfigureMockMvc + MockMvc — clear golden standard
- **Consistent results**: all variants achieved 71-100% coverage on this item, so there's signal to compare
- **Fastest**: simple project, compiles quickly, tests run fast
- **Best KB fit**: the knowledge base covers REST testing patterns extensively (spring-test-slices, common-gaps)

### Proposed Exploration Sequence

**Phase A: Build the analysis stack (no new agent runs)**
1. Set up Python env: `uv venv && uv pip install duckdb pandas matplotlib numpy`
2. Write `scripts/load_results.py` — ETL existing results into parquet
3. Write basic `scripts/variant_comparison.py` — aggregate table from existing data
4. Vibe check: do the numbers tell a coherent story? Can we see the variant-a > variant-b/c pattern in the data?

**Phase B: Single-item two-phase variant (1 agent run, ~15 min)**
1. Implement two-phase `CodeCoverageAgentInvoker` variant
2. Write new prompts: `v3-explore.txt`, `v3-act.txt`
3. Run `--variant variant-d --item gs-rest-service`
4. Inspect `TEST_PLAN.md` — did the agent absorb KB guidance?
5. Compare Judge#1 score to existing variants on gs-rest-service
6. Compare GoldenTestComparisonJudge score (new judge, first real data)

**Phase C: Decide whether to scale**
- If variant-d (two-phase + KB) beats variant-a (hardened prompt, no KB) on gs-rest-service → run all 5 items
- If not → diagnose why (inspect TEST_PLAN.md, check context utilization) before committing hours
- Either way, the analysis stack is now built and ready for when full runs happen

### The WebSocket Problem

`gs-messaging-stomp-websocket` consistently scored lowest (0.45-0.50 on `domain_specific_test_patterns`) across all variants. This likely indicates:
- No consensus on how to test WebSocket/STOMP in Spring Boot
- The model's training data has sparse WebSocket testing examples
- The KB doesn't have WebSocket-specific testing guidance

This is actually a good thing for the thesis — it's the item where KB advantage should be most visible, if we add WebSocket testing knowledge to the KB. But for the exploration phase, skip it — use gs-rest-service where the signal is cleanest.

### Pet Clinic: Next Iteration

Pet Clinic is explicitly planned as a future target (VISION.md, ROADMAP.md, PITCH.md). The current guides hit 85-100% coverage easily — ceiling effects make it hard to discriminate variants. Pet Clinic has genuine complexity:
- Multi-layer (controller → service → repository)
- Multiple domain entities with relationships
- Form handling, validation, error pages
- Mixed web + JPA testing

Adding it requires a small `materialize.sh` update (different GitHub org, no `complete/` subdirectory — ~10-line script change). Save for after the two-phase variant proves itself on the simple guides.

---

## Updated Variant Table

| Variant | Prompt | Knowledge | Phase | Tests |
|---------|--------|-----------|-------|-------|
| control | v0-naive | none | single | Baseline |
| variant-a | v1-hardened | none | single | Prompt improvement |
| variant-b | v2-with-kb | 3 KB files | single | Knowledge effect |
| variant-c | v2-with-kb | full KB | single | Knowledge depth |
| **variant-d** | **v3-explore + v3-act** | **full KB** | **two-phase** | **Structured knowledge consumption** |

The thesis test becomes: does variant-d (structured L1 + rich L2) beat variant-a (good implicit L1, no L2)?

---

## Key References

| Topic | Path |
|-------|------|
| Two-Layer Value Model | `~/tuvium/projects/tuvium-agentic-patterns-research/findings/two-layer-value-model.md` |
| H5 MiniAgent Finding | `~/tuvium/projects/tuvium-agentic-patterns-research/findings/h5-miniagent-knowledge.md` |
| JIT Retrieval Finding | `~/tuvium/projects/tuvium-agentic-patterns-research/findings/jit-retrieval.md` |
| Stripe Journal | `~/tuvium/projects/tuvium-research-conversation-agent/journal/2026-03-02-stripe-agentic-engineering.md` |
| Stripe Blog Posts | `~/tuvium/projects/tuvium-research-conversation-agent/conversations/ongoing/stripe-agentic-engineering/` |
| Refactoring Agent Two-Phase | `~/tuvium/projects/refactoring-agent/agent/src/main/java/com/tuvium/agent/RefactoringAgent.java` |
| Developer Prompting Insight | `~/tuvium/projects/refactoring-agent/plans/learnings/step-3.2-developer-prompting-insight.md` |
| AIE Summit Context & Knowledge | `~/tuvium/projects/tuvium-agentic-patterns-research/papers/summaries/aie-summit-2025-context-and-knowledge.md` |
| SWE-agent Window Size | `~/tuvium/projects/tuvium-agentic-patterns-research/papers/summaries/yang-2024-swe-agent.md` |
| Horthy RPI Workflow | Same AIE Summit file, Dex Horthy section |
| Golden Judge Handoff | `plans/inbox/golden-judge-handoff.md` |
