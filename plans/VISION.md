# Vision: Code Coverage Experiment

## Problem Statement

AI agents can improve JUnit test coverage on Spring Boot projects, but the impact of prompt engineering vs. domain knowledge injection is not well understood. We need ablation data showing which factor (prompt quality, knowledge breadth, knowledge depth) drives the most improvement.

## Hypothesis

Domain knowledge files injected into the agent's context will produce larger coverage improvements than prompt engineering alone. Specifically:
- Hardened prompts (v1) will improve over naive prompts (v0) by ~15pp
- Adding 3 knowledge files (v2+KB) will improve over hardened prompts by ~17pp
- Adding the 4th knowledge file (common-gaps) will add ~3pp more

## Success Criteria

- [ ] Agent achieves ≥80% line coverage on majority of benchmark items (variant-b or variant-c)
- [ ] Knowledge ablation shows measurable improvement (variant-b > variant-a by ≥10pp)
- [ ] Growth story demonstrates progressive improvement across all 4 variants
- [ ] Results reproducible across 5 Spring Getting Started guides

## Scope

**In scope:**
- 4 variants (control, hardened prompt, hardened+3KB, hardened+4KB)
- 5 Spring Boot Getting Started guides as benchmark dataset
- JaCoCo line and branch coverage as primary metrics
- CascadedJury evaluation (build success, coverage preservation, coverage improvement, test quality)

**Out of scope:**
- Model comparison (Claude vs Gemini) — deferred to bake-off phase
- MiniAgent contrast — separate experiment
- Production agent extraction — deferred to graduation phase
