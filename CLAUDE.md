# Agent Experiment Template

Standard template for agent experiments with pre-wired experiment loop. Used by `markpollack/forge` to scaffold new experiment projects.

## What's Pre-Wired

- `ExperimentApp` — Orchestrates variant execution, comparison, and growth story generation
- `ExperimentVariantConfig` — Top-level config loaded from experiment-config.yaml
- `VariantSpec` — Per-variant specification (prompt, knowledge, judge overrides)
- `JuryFactory` — Builds CascadedJury from tier/judge configuration
- `GrowthStoryReporter` — Reads ExperimentResults → markdown growth story

## What You Customize (3 Pluggable Pieces)

1. **AgentInvoker** — Rename `TemplateAgentInvoker` to `{Domain}AgentInvoker`, implement domain-specific agent invocation
2. **Custom Judges** — Implement domain-specific judges for Tier 2 (FINAL_TIER)
3. **Knowledge Files** — Write domain knowledge in `knowledge/` directory

## Directory Structure

```
├── dataset/items.yaml          # Benchmark dataset items
├── knowledge/index.md          # Knowledge routing table
├── prompts/default.txt         # Prompt templates per variant
├── results/                    # Experiment results (generated)
└── plans/                      # VISION, DESIGN, ROADMAP templates
```

## Dependencies

- `experiment-core` (ai.tuvium) — ExperimentRunner, ComparisonEngine, ResultStore
- `agent-judge-core` + `agent-judge-exec` — Judge, Jury, CascadedJury, deterministic judges
- `spring-ai-agent-client` — AgentClient for agent invocation
