# Findings Summary — Code Coverage Experiment

> **Run**: Full suite 2026-03-03 | **Model**: claude-sonnet-4-6 | **Dataset**: 5 Spring Getting Started guides (0% baseline)

## Thesis Under Test

Does injecting domain-specific knowledge improve AI agent test-writing quality beyond what prompt engineering alone achieves?

## Result

**No — on simple Spring Getting Started guides, prompt hardening alone is the dominant lever.** Knowledge injection adds reading overhead without proportional quality improvement.

## Evidence

| Variant | Prompt | Knowledge | Avg T3 | Avg Eff | Cost |
|---------|--------|-----------|--------|---------|------|
| Control | naive | none | 0.62 | 0.878 | $4.57 |
| **Variant-A** | **hardened** | **none** | **0.80** | **0.937** | **$4.17** |
| Variant-B | hardened+KB | 3 targeted files | 0.697 | 0.837 | $4.98 |
| Variant-C | hardened+KB | full KB tree | 0.757 | 0.823 | $4.55 |

All 4 variants achieved 100% pass rate (T0-T2) across all 5 items.

## Three-Dimension Diagnostic Reading

### Correctness (Coverage)
Coverage hit ceiling: 85-100% regardless of variant. Spring Getting Started guides are too simple for coverage to discriminate. Not a useful signal at this difficulty level.

### Practice Adherence (T3)
The widest spread and most informative dimension:
- **Prompt structure matters most**: Control→Variant-A = +0.18 (biggest delta)
- **KB injection regresses quality**: Variant-A→Variant-B = -0.10 (targeted KB hurts)
- **More KB partially recovers**: Variant-B→Variant-C = +0.06 (JIT navigation helps vs targeted)
- **Discriminator item**: `gs-reactive-rest-service` shows 0.50→0.93 T3 spread — best for debugging

### Efficiency
Correlates with prompt quality. Fewer build errors → fewer recovery cycles → lower cost. Variant-A wins on all three efficiency sub-dimensions.

## Key Item-Level Observations

- **gs-reactive-rest-service**: Variant-A scored 0.93 T3 (highest single score) — correctly used `@WebFluxTest`, `StepVerifier`, Boot 4.x imports. Control scored 0.50 — used `@SpringBootTest` instead.
- **gs-accessing-data-jpa**: All variants clustered (0.60-0.70 T3) — JPA testing is simpler, less room for differentiation.
- **gs-messaging-stomp-websocket**: Hardest item across all variants (T3 0.50-0.65). WebSocket testing is genuinely complex — potential KB injection target.

## Interpretation

The model (Sonnet) already knows Spring testing best practices well enough for simple guides. The hardened prompt surfaces this knowledge by:
1. Explicitly requesting `@WebFluxTest`/`@WebMvcTest` slices over `@SpringBootTest`
2. Requiring Boot 4.x import patterns
3. Asking for error/edge-case coverage

KB injection on top of this adds file-reading tool calls that consume tokens without teaching the model anything it doesn't already know. The cost increase ($4.17 → $4.98) reflects this overhead.

## Threats to Validity

1. **Small dataset**: 5 items may not generalize. Variance is high at N=5.
2. **Ceiling effects**: Simple projects mask potential KB value on harder targets.
3. **Single model**: Sonnet is knowledgeable about Spring. A smaller/cheaper model might benefit more from KB.
4. **Single run**: No statistical confidence — one observation per cell.

## Next Steps

1. **Harder targets**: Pet Clinic, multi-module projects where coverage is genuinely challenging and KB has novel information to offer.
2. **Cross-model runs**: Test if KB helps a cheaper model (Haiku) close the gap with a more capable one (Sonnet/Opus).
3. **Golden judge re-run**: Full suite with `GoldenTestComparisonJudge` wired — compare agent tests to Spring developers' reference tests via AST analysis.
4. **Statistical power**: Multiple runs per variant to compute confidence intervals.
