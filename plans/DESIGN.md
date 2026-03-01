# Design: {{PROJECT_NAME}}

## Architecture

This experiment uses the standard agent experiment loop:

```
ExperimentApp → ExperimentRunner → AgentInvoker → CascadedJury → ResultStore
                                                                    ↓
                                                          ComparisonEngine
                                                                    ↓
                                                        GrowthStoryReporter
```

## Domain Agent

**AgentInvoker implementation:** `{{Domain}}AgentInvoker`

_Describe what the agent does, how it interacts with the workspace, and what
domain-specific setup/teardown is needed._

## Judges

| Judge | Tier | Type | What it checks |
|-------|------|------|---------------|
| _BuildSuccessJudge_ | 0 | Deterministic | _Build compiles and tests pass_ |
| _{{Domain}}Judge_ | 2 | Custom | _Domain-specific evaluation_ |

## Variants

| Variant | Prompt | Knowledge | Expected Outcome |
|---------|--------|-----------|-----------------|
| control | naive | none | Baseline |
| variant-a | improved | none | Prompt effect |
| variant-b | improved | KB files | Knowledge effect |

## Dataset

_Describe the benchmark dataset: what projects, how many items, what makes them
representative._
