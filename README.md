# Code Coverage Experiment v1

AI agent code coverage experiment on 5 Spring Getting Started guides plus
spring-petclinic. Measures how prompt engineering and knowledge injection affect
AI-generated JUnit test quality and coverage.

Blog post: [I Read My Agent's Diary](https://lab.pollack.ai/i-read-my-agents-diary/)

---

## Dataset

6 benchmark items from [Spring Getting Started guides](https://spring.io/guides):

| # | Item | Description |
|---|------|-------------|
| 1 | gs-rest-service | REST controller |
| 2 | gs-accessing-data-jpa | JPA repository |
| 3 | gs-securing-web | Spring Security |
| 4 | gs-reactive-rest-service | WebFlux |
| 5 | gs-messaging-stomp-websocket | STOMP messaging |
| 6 | spring-petclinic | Multi-layer Spring app (bucket B) |

## Variants

| Name | Prompt | Knowledge | Phase |
|------|--------|-----------|-------|
| control | [v0-naive.txt](prompts/v0-naive.txt) | none | single |
| variant-a | [v1-hardened.txt](prompts/v1-hardened.txt) | none | single |
| variant-b | [v2-with-kb.txt](prompts/v2-with-kb.txt) | 3 KB files | single |
| variant-c | [v2-with-kb.txt](prompts/v2-with-kb.txt) | full KB (index.md) | single |
| variant-d | [v3-explore.txt](prompts/v3-explore.txt) + [v3-act.txt](prompts/v3-act.txt) | full KB | two-phase |
| variant-e | [v4-forge-plan.txt](prompts/v4-forge-plan.txt) + [v4-forge-act.txt](prompts/v4-forge-act.txt) | full KB | two-phase |

Model ablation variants (claude-haiku, loopy-haiku, loopy-qwen3-coder, etc.) are
defined in `experiment-config.yaml`.

Agent: Claude Sonnet 4.6 (default).

## Key Results

The hardened prompt (variant-a) is the single biggest lever: T3 practice adherence
score +0.18 over naive control. Knowledge injection (variants b-c) did not improve
over prompt alone on these simple guides. Two-phase explore/act (variant-d) shows
promise on the more complex petclinic item. Full Markov analysis in
`analysis/markov-findings.md`.

## Reproduce

This experiment uses [markpollack/agent-experiment](https://github.com/markpollack/agent-experiment)
as the experiment driver framework.

```bash
# Build
./mvnw compile

# Run a single variant on one item
./mvnw compile exec:java -Dexec.args="--variant control --item gs-rest-service"

# Run all variants
./mvnw compile exec:java -Dexec.args="--run-all-variants"
```

Requires Java 21, Claude API key, and the experiment driver dependencies.
See `experiment-config.yaml` for variant definitions.

## Analysis

```bash
uv venv && uv pip install -r requirements.txt
.venv/bin/python scripts/load_results.py
MPLBACKEND=Agg .venv/bin/python scripts/make_markov_analysis.py
```

Outputs: transition matrices, Sankey diagrams, fundamental matrix analysis in
`docs/latex/figures/` and `analysis/`.

## Related

- [experiment-code-coverage-v2](https://github.com/markpollack/experiment-code-coverage-v2) -- sequel with SkillsJars and N=3 runs
- [markov-agent-analysis](https://github.com/markpollack/markov-agent-analysis) -- Markov chain analysis library
- [lab.pollack.ai](https://lab.pollack.ai) -- experiment writeups

## License

[Business Source License 1.1](LICENSE) -- changes to Apache 2.0 on 2030-03-24.
