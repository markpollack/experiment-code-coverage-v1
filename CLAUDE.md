# Code Coverage Experiment

Agent experiment: improve JUnit test coverage on Spring Boot Maven projects using AI agents with progressive knowledge injection.

## Build

```bash
mvn compile     # Compile
mvn test        # Run tests
mvn package     # Build JAR
```

## Implementation Progress

**Source of truth**: `plans/ROADMAP.md`

**Current state**: Stage 1 steps 1.0–1.2 complete (project scaffolded by `markpollack/forge`, AgentInvoker implemented, prompts and knowledge written). Stage 1.3 (dataset population) and Stage 2 (variant execution) are next.

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
| 3 | TestQualityJudge | custom (LLM-driven) | FINAL_TIER |

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

- `experiment-core` (ai.tuvium, 0.1.0-SNAPSHOT) — ExperimentRunner, ComparisonEngine, ResultStore
- `agent-judge-core` + `agent-judge-exec` (org.springaicommunity, 0.9.0-SNAPSHOT) — Judge, Jury, CascadedJury, deterministic judges
- `spring-ai-agent-client` + `spring-ai-claude-agent` (org.springaicommunity.agents, 0.10.0-SNAPSHOT) — AgentClient, ClaudeAgentModel

## Origin

Scaffolded by `markpollack/forge` from `markpollack/agent-experiment-template` using brief at `~/tuvium/projects/tuvium-research-conversation-agent/plans/coverage-agent-brief.yaml`.
