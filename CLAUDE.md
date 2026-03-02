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

**Current state**: Stage 1 steps 1.0–1.3 complete (project scaffolded, AgentInvoker implemented, prompts/knowledge written, dataset populated and verified). Stage 1.4 (TestQualityJudge) and Stage 2 (variant execution) are next.

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
├── knowledge/                  # Domain knowledge (4 files + index)
│   ├── index.md
│   ├── coverage-fundamentals.md
│   ├── jacoco-patterns.md
│   ├── spring-test-slices.md
│   └── common-gaps.md
├── prompts/                    # Per-variant prompts
│   ├── v0-naive.txt
│   ├── v1-hardened.txt
│   └── v2-with-kb.txt
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

## Knowledge Extraction Backlog

Domain knowledge injected into the agent lives in `knowledge/`. It must be self-contained in this repo — no references to external paths at runtime. When adding new knowledge files, extract and adapt content into this project's `knowledge/` directory.

Candidates for future variants (deeper knowledge injection):
- JPA / data-access testing best practices
- Other Spring domain-specific testing patterns as identified from experiment results

## Origin

Scaffolded by `markpollack/forge` from `markpollack/agent-experiment-template`.
