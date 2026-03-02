# Design: Code Coverage Experiment

## Architecture

This experiment uses the standard agent experiment loop:

```
ExperimentApp → ExperimentRunner → CodeCoverageAgentInvoker → CascadedJury → ResultStore
                                                                                 ↓
                                                                       ComparisonEngine
                                                                                 ↓
                                                                     GrowthStoryReporter
```

## Domain Agent

**AgentInvoker implementation:** `CodeCoverageAgentInvoker`

Workflow per dataset item:
1. Verify project compiles (`./mvnw clean compile`)
2. Measure baseline coverage (`./mvnw clean test jacoco:report` → `JaCoCoReportParser`)
3. Build prompt from variant's prompt file + baseline metrics
4. Invoke agent via `AgentClient.goal(prompt).workingDirectory(workspace).run()`
5. Measure final coverage
6. Store baseline/final metrics in `InvocationResult` metadata for judges

The agent operates autonomously on the workspace — it reads code, adds/modifies tests, and may add the JaCoCo Maven plugin if missing.

## Judges

| Judge | Tier | Type | Policy | What it checks |
|-------|------|------|--------|---------------|
| BuildSuccessJudge | 0 | Deterministic | REJECT_ON_ANY_FAIL | Project compiles and tests pass after agent |
| CoveragePreservationJudge | 1 | Deterministic | REJECT_ON_ANY_FAIL | Coverage didn't regress from baseline |
| CoverageImprovementJudge | 2 | Deterministic | ACCEPT_ON_ALL_PASS | Normalized coverage improvement score |
| TestQualityJudge | 3 | Agent-based | FINAL_TIER | Fixed quality bar derived from KB — same for all variants |

Tier 0–2 judges are "off the shelf" from `agent-judge-exec`. Tier 3 (`TestQualityJudge`) is the custom domain piece.

### TestQualityJudge: Fixed Quality Bar from KB

The judge uses a **single fixed prompt** (`prompts/judge-quality.txt`), applied identically to all variants. The prompt was authored by reading the KB and distilling best practices into concrete evaluation criteria. It is a static artifact — the judge does NOT read the KB at runtime.

```
Knowledge base (source of truth)
    ↓ author reads, distills (once)    ↓ subset per variant
Judge prompt                          Agent KB files (knowledge/)
(static file, fixed bar)             (progressively more per variant)
    ↓ same for all variants               ↓
Scores ←─────────────────────────── Agent output
```

The judge prompt encodes the full KB's perspective as a fixed quality bar. The agent gets a progressively larger slice of the KB at runtime. The delta between variants measures how much of the quality bar the agent could reach with the resources it was given.

**Why fixed, not adaptive per-variant:** The judge is the target; the variants are different attempts to hit it. A fixed bar means:
- The audience understands the evaluation ("same bar for everyone")
- The LLM's built-in knowledge is rewarded, not penalized
- The growth story shows what knowledge injection *added on top of* what the model already knew

**Why a static prompt, not runtime KB navigation:** A judge that reads the KB at runtime is non-deterministic — different reads could yield different criteria and scores across runs. The judge prompt is a versioned artifact. If the KB evolves between experiment cycles, the judge prompt is updated as a deliberate step, not automatically. The `TestQualityJudge` implementation is generic — it takes the prompt file path as input.

**Implementation:** Agent-based (uses `ClaudeAgentModel` with read-only tools: `Read`, `Glob`, `Grep`) to navigate `src/main/` and `src/test/`. Returns JSON with per-criterion scores and evidence strings. Final score is weighted average → `NumericalScore.normalized()`.

### Knowledge Base as Configurable Policy

The KB is not a fixed answer key — it's a **swappable opinion layer**. The experiment validates the *mechanism* (does KB injection produce measurable adherence?), not the *opinions* (are these the right idioms?).

The KB should be structured for forkability:

| Layer | Content | Fork frequency |
|-------|---------|---------------|
| **Principles** | Universal: test at the right layer, assert specific values, cover error paths | Rarely — shared across teams |
| **Idioms** | Team-specific: AssertJ over Hamcrest, `@WebMvcTest` over `@SpringBootTest`, Mockito BDD style | Primary fork target |
| **Config patterns** | Project-specific: JaCoCo exclusions, Maven plugin config, Boot version–specific APIs | Per-project |

A team forking the KB would touch idioms heavily and leave principles mostly alone. This separation also structures the judge prompt — score principles universally, score idioms against whatever the current KB says.

### Diagnostic Feedback Loop

Judge scores are not just pass/fail — they're diagnostic. The evidence strings in the JSON output tell you *why* a criterion scored low, and that maps to a specific improvement lever. This follows the pattern from the refactoring-agent `AIAnalyzer` batch analysis:

```
Judge score + evidence
    ↓ diagnose
    ├── Knowledge gap?      → score low on criterion KB addresses    → refine KB file
    ├── Orchestration gap?  → high tool usage, retries, wrong order  → restructure prompt
    ├── Tool gap?           → agent reconstructs computable info     → build dedicated tool
    ├── Model gap?          → consistent failure despite good KB     → try stronger model
    └── iterate
```

The levers are ordered by iteration cost: KB refinement is cheap and fast, model upgrade is expensive and slow. The experiment loop gives you data to know which lever to pull first.

### Future: Modernization Advisor (not a judge)

A separate concern from quality judging: detecting where existing test patterns could be upgraded to newer Boot idioms (e.g., `MockMvc` → `RestTestClient` on Boot 4+). This is **not a judge** — judges score the agent's output against a quality bar. Modernization advice is a recommendation for the project maintainer, orthogonal to coverage improvement. It could be a standalone report or a separate agent, but it should not influence the coverage agent's behavior during an experiment run. The coverage agent's job is to follow existing conventions and add coverage, not refactor test style.

## Variants

| Variant | Prompt | Knowledge | Navigation | Expected Outcome |
|---------|--------|-----------|-----------|-----------------|
| control | v0-naive.txt ("Improve test coverage to 80%") | none | N/A | Low coverage, poor test quality |
| variant-a | v1-hardened.txt (constraints, version detection, compile gate) | none | N/A | Better coverage, LLM's built-in knowledge only |
| variant-b | v2-with-kb.txt | coverage-mechanics/ (3 files) | Direct — read upfront | Good coverage, basic best practices |
| variant-c | v2-with-kb.txt | Full KB via index.md (coverage-mechanics + 8 spring-testing files) | JIT — routing tables, version-aware | Best coverage, domain-specific patterns per project type |

## Dataset

5 Spring Getting Started guides — small, well-structured projects that represent common Spring Boot patterns:

| Item | Boot Version | URL | Pattern | Existing Tests |
|------|-------------|-----|---------|---------------|
| gs-rest-service | 4.0.3 | spring-guides/gs-rest-service | REST controller | Yes — `RestTestClient` |
| gs-accessing-data-jpa | 4.0.3 | spring-guides/gs-accessing-data-jpa | JPA repository | Yes — `@DataJpaTest` |
| gs-securing-web | 4.0.3 | spring-guides/gs-securing-web | Spring Security | Yes — `MockMvc` + Security DSL |
| gs-reactive-rest-service | 4.0.2 | spring-guides/gs-reactive-rest-service | WebFlux reactive | Yes — `WebTestClient` |
| gs-messaging-stomp-websocket | 3.5.11 | spring-guides/gs-messaging-stomp-websocket | WebSocket messaging | Yes — STOMP integration |

Each guide's `complete/` subdirectory is used as the workspace. All guides already have tests — the agent's job is to add coverage following existing conventions, not introduce new patterns.

**Version awareness:** 4 of 5 guides use Boot 4.x (Spring Framework 7), one uses Boot 3.x. The agent prompt instructs version detection from `pom.xml`. The KB contains version-specific guidance (e.g., `RestTestClient` on Boot 4+, `MockMvc` on Boot 3.x). This creates a natural test of version-aware behavior.

## Knowledge Files

| File | Content | Used by |
|------|---------|---------|
| **coverage-mechanics/** | | |
| coverage-fundamentals.md | Line/branch/method coverage, meaningful test criteria, what NOT to cover | variant-b, variant-c |
| jacoco-patterns.md | JaCoCo Maven plugin config, report structure, common issues | variant-b, variant-c |
| spring-test-slices.md | Quick decision tree: @WebMvcTest vs @DataJpaTest vs plain JUnit | variant-b, variant-c |
| common-gaps.md | Negative guidance: don't test records/main/config, DO test error handling | variant-b, variant-c |
| **spring-testing/** (via JIT navigation) | | |
| jpa-testing-cheatsheet.md + deep dive | @DataJpaTest, TestEntityManager, derived queries, Testcontainers | variant-c |
| mvc-rest-testing-patterns.md | @WebMvcTest, MockMvc, RestTestClient (Boot 4), validation, error handling | variant-c |
| security-testing-patterns.md | @WithMockUser, CSRF, JWT, OAuth2, @PreAuthorize | variant-c |
| webflux-testing-patterns.md | @WebFluxTest, WebTestClient, StepVerifier, SSE | variant-c |
| websocket-stomp-testing-patterns.md | StompClient, BlockingQueue, @SendToUser, RANDOM_PORT | variant-c |
| assertj-mockito-idioms.md | AssertJ patterns, BDDMockito, ArgumentCaptor, @MockitoBean | variant-c |
| cross-cutting-testing-patterns.md | Testing pyramid, context caching, Boot 4 readiness, anti-patterns | variant-c |

Knowledge files are derived from the same KB that generates the judge prompt. They teach the agent to hit the target the judge already knows about. The experiment measures how much of that target each variant can reach.

## Knowledge Base Architecture

### Two-Tier KB with JIT Navigation

The agent doesn't get the entire KB injected into its prompt. It navigates at runtime using file tools (Read, Glob, Grep) — the same JIT context pattern proven in the refactoring-agent, where "Haiku + knowledge stores beats Sonnet without them."

```
knowledge/
├── index.md                        ← top-level router (always read first)
├── coverage-mechanics/             ← small, universally relevant (read upfront)
│   ├── coverage-fundamentals.md
│   ├── jacoco-patterns.md
│   ├── common-gaps.md
│   └── spring-test-slices.md
└── spring-testing/                 ← rich domain KB (navigate via routing tables)
    ├── index.md                    ← question routing + version-aware navigation
    ├── jpa-testing-cheatsheet.md
    ├── jpa-repository-testing-best-practices.md
    ├── mvc-rest-testing-patterns.md
    ├── security-testing-patterns.md
    ├── webflux-testing-patterns.md
    ├── websocket-stomp-testing-patterns.md
    ├── assertj-mockito-idioms.md
    └── cross-cutting-testing-patterns.md
```

`spring-testing/` is symlinked from `tuvium-knowledge` — the authoritative source. At graduation, `cp -rL` resolves symlinks into a self-contained snapshot.

### Variant KB Access

| Variant | What the agent sees | Navigation |
|---------|-------------------|-----------|
| control | Nothing | N/A |
| variant-a | Nothing | N/A |
| variant-b | `coverage-mechanics/` only (3 files) | Direct — small enough to read upfront |
| variant-c | Full KB via `index.md` | JIT — navigates routing tables, reads only what's relevant to the code being tested |

The ablation between variant-b and variant-c isolates the effect of rich, navigable domain knowledge (8 testing-pattern files with version-aware guidance) vs. basic coverage mechanics alone.

### KB Version Tracking

The KB will evolve between experiment cycles. To attribute score deltas to KB changes, record the git SHA of `tuvium-knowledge` at experiment start:

```bash
git -C ~/tuvium/projects/tuvium-knowledge rev-parse HEAD
```

Current SHA: `5dda7feb3969f84b997a6948f35826d5d0772961` (2026-03-02).

Store this in experiment output alongside variant results. The `KnowledgeManifest` in experiment-core should be extended to track external KB versions if it doesn't already.
