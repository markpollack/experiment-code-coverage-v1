# Code Coverage Experiment

Agent experiment: improve JUnit test coverage on Spring Boot projects using AI agents with progressive knowledge injection. Default build tool is Maven; Gradle support is planned as an agent option.

## Build

**Always use `./mvnw` (Maven Wrapper) instead of `mvn`.** If a project lacks `mvnw`, install it first: `mvn wrapper:wrapper`.

```bash
./mvnw compile     # Compile
./mvnw test        # Run tests
./mvnw package     # Build JAR
```

## Implementation Progress

**Source of truth**: `plans/ROADMAP.md`

**Current state**: Stage 2 in progress. Step 2.0 (bootstrap wiring) complete. ExperimentApp.main() is runnable with CLI args.

## Architecture

```
ExperimentApp → ExperimentRunner → CodeCoverageAgentInvoker → CascadedJury → ResultStore
                                                                                 ↓
                                                                       ComparisonEngine
                                                                                 ↓
                                                                     GrowthStoryReporter
```

### Pre-Wired (from template)

- `ExperimentApp` — Orchestrates variant execution, comparison, growth story generation
- `ExperimentVariantConfig` — Loads experiment-config.yaml
- `VariantSpec` — Per-variant specification (prompt, knowledge, judge overrides)
- `JuryFactory` — Builds CascadedJury from tier/judge configuration
- `GrowthStoryReporter` — Reads ExperimentResults → markdown growth story

### Domain-Specific (customized)

- `CodeCoverageAgentInvoker` — Measures baseline JaCoCo coverage, invokes agent, measures final coverage, enriches metadata for judges

### 4-Tier Jury

| Tier | Judge | Source | Policy |
|------|-------|--------|--------|
| 0 | BuildSuccessJudge | agent-judge-exec | REJECT_ON_ANY_FAIL |
| 1 | CoveragePreservationJudge | agent-judge-exec | REJECT_ON_ANY_FAIL |
| 2 | CoverageImprovementJudge | agent-judge-exec | ACCEPT_ON_ALL_PASS |
| 3 | TestQualityJudge | custom (agent-based via AgentClient) | FINAL_TIER |

### Variants

| Variant | Prompt | Knowledge | Tests |
|---------|--------|-----------|-------|
| control | v0-naive.txt | none | Prompt baseline |
| variant-a | v1-hardened.txt | none | Prompt improvement |
| variant-b | v2-with-kb.txt | 3 KB files | Knowledge effect |
| variant-c | v2-with-kb.txt | 4 KB files | Knowledge depth |

## Directory Structure

```
├── dataset/items.yaml          # 5 Spring guide benchmark items
├── experiment-config.yaml      # 4 variant definitions
├── knowledge/                  # Domain knowledge (JIT navigation)
│   ├── index.md                # Top-level router (always read first)
│   ├── coverage-mechanics/     # Small, universal (read upfront)
│   │   ├── coverage-fundamentals.md
│   │   ├── jacoco-patterns.md
│   │   ├── spring-test-slices.md
│   │   └── common-gaps.md
│   └── spring-testing/         # Rich domain KB (navigate via routing tables)
│       ├── index.md
│       └── (8 cheatsheets)
├── prompts/                    # Agent + judge prompts
│   ├── v0-naive.txt
│   ├── v1-hardened.txt
│   ├── v2-with-kb.txt
│   ├── judge-practice-adherence.txt           # Fixed rubric — 6 criteria, same for all variants
│   └── judge-practice-adherence-traceability.md
├── results/                    # Experiment results (generated)
└── plans/                      # VISION, DESIGN, ROADMAP
```

## Dependencies

**Always prefer reading local source code over `javap` to understand how dependency APIs work.** See memory files for local source paths.

| Dependency | Coordinates |
|-----------|-------------|
| experiment-core | ai.tuvium 0.1.0-SNAPSHOT |
| agent-judge-core + agent-judge-exec | org.springaicommunity 0.9.0-SNAPSHOT |
| spring-ai-agent-client + spring-ai-claude-agent | org.springaicommunity.agents 0.10.0-SNAPSHOT |
| Spring AI | org.springframework.ai 2.0.0-SNAPSHOT |

## Stage 1 Learnings (distilled)

For full details: `plans/learnings/LEARNINGS.md`

**API gotchas**:
- `AgentClient.AgentClientRequestSpec` is a nested interface, not top-level
- `AgentGeneration` (not `AgentResult`) — model result type with `getOutput()`
- `NumericalScore.normalized()` throws on out-of-bounds — always clamp LLM scores first
- `workingDirectory` priority: request-level > goal-level > builder default > cwd

**Design patterns**:
- `Function<Path, AgentClient>` factory for testability — mock factory, not static methods
- Package-private parsing methods for unit tests (separate from integration)
- Fixed judge rubric applied identically to all variants; KB informs authorship, not runtime
- Two scoring dimensions (functional T0-T2 + adherence T3) reported separately, never combined

## Stage 2 Learnings (distilled)

**Bootstrap wiring** (Step 2.0):
- `ExperimentApp.main()` wires: `loadConfig()` → `buildJuryFactory()` → `FileSystemResultStore` → dispatch
- `AgentInvoker` removed from constructor — per-variant `CodeCoverageAgentInvoker` created via `createInvoker(VariantSpec)` in `runVariant()`
- `CoverageImprovementJudge` existed in agent-judge source but was missing from installed JAR — re-install from `~/community/agent-judge` was needed. Always verify installed JARs match source.

**Knowledge injection** (Step 2.1):
- `CodeCoverageAgentInvoker` has optional `knowledgeSourceDir` + `knowledgeFiles` — copies files into `workspace/knowledge/` before agent invocation
- `index.md` in knowledgeFiles triggers full recursive tree copy (variant-c); otherwise only listed files are copied (variant-b)
- `copyKnowledge()` is package-private for direct unit testing (same pattern as `parseJudgment()`)

## Knowledge Extraction Backlog

Domain knowledge injected into the agent lives in `knowledge/`. It must be self-contained in this repo — no references to external paths at runtime. When adding new knowledge files, extract and adapt content into this project's `knowledge/` directory.

Candidates for future variants (deeper knowledge injection):
- JPA / data-access testing best practices
- Other Spring domain-specific testing patterns as identified from experiment results

## Origin

Scaffolded by `markpollack/forge` from `markpollack/agent-experiment-template`.
