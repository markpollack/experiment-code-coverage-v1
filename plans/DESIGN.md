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
1. Verify project compiles (`mvn clean compile`)
2. Measure baseline coverage (`mvn clean test jacoco:report` → `JaCoCoReportParser`)
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
| TestQualityJudge | 3 | LLM-driven | FINAL_TIER | BDD semantics, meaningful assertions, proper naming |

Tier 0–2 judges are "off the shelf" from `agent-judge-exec`. Tier 3 (`TestQualityJudge`) is the custom domain piece — an LLM evaluates whether the generated tests follow BDD patterns, have meaningful assertions, and use proper Spring test slices.

## Variants

| Variant | Prompt | Knowledge | Expected Outcome |
|---------|--------|-----------|-----------------|
| control | v0-naive.txt ("Improve test coverage to 80%") | none | ~50% line coverage |
| variant-a | v1-hardened.txt (detailed constraints, examples) | none | ~65% line coverage |
| variant-b | v2-with-kb.txt (hardened + "read knowledge/") | coverage-fundamentals, jacoco-patterns, spring-test-slices | ~82% line coverage |
| variant-c | v2-with-kb.txt | all above + common-gaps | ~85% line coverage |

## Dataset

5 Spring Getting Started guides — small, well-structured projects that represent common Spring Boot patterns:

| Item | URL | Pattern |
|------|-----|---------|
| gs-rest-service | spring-guides/gs-rest-service | REST controller |
| gs-accessing-data-jpa | spring-guides/gs-accessing-data-jpa | JPA repository |
| gs-securing-web | spring-guides/gs-securing-web | Spring Security |
| gs-reactive-rest-service | spring-guides/gs-reactive-rest-service | WebFlux reactive |
| gs-messaging-stomp-websocket | spring-guides/gs-messaging-stomp-websocket | WebSocket messaging |

Each guide's `complete/` subdirectory is used as the workspace.

## Knowledge Files

| File | Content | Used by |
|------|---------|---------|
| coverage-fundamentals.md | Line/branch/method coverage, meaningful test criteria, what NOT to cover | variant-b, variant-c |
| jacoco-patterns.md | JaCoCo Maven plugin config, report structure, common issues | variant-b, variant-c |
| spring-test-slices.md | @WebMvcTest vs @DataJpaTest vs plain JUnit decision tree | variant-b, variant-c |
| common-gaps.md | Negative guidance: don't test records/main/config, DO test error handling | variant-c only |
