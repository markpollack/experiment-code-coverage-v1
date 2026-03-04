# Step 4.2: Explore + Act Prompts — Learnings

## Prompt Design

### v3-explore.txt (Phase 1: Explore)
- **Key constraint**: "do NOT write any code yet" — forces the agent to read and plan before acting
- **Deliverable**: `TEST_PLAN.md` — structured plan documenting which test classes to create, which Spring annotations to use, which KB patterns apply, and which edge cases to cover
- **KB consumption**: Explicitly instructs agent to start with `knowledge/index.md` routing table, then follow references. This is the structured L1 orchestration that was missing from variant-b/c.
- **Version awareness**: Instructs agent to read pom.xml for Spring Boot version and use version-specific guidance from KB (Boot 3.x MockMvc vs Boot 4+ RestTestClient)

### v3-act.txt (Phase 2: Act)
- **Key instruction**: "Read TEST_PLAN.md to recall your analysis" — reconnects to explore phase output
- **Same quality constraints** as v1-hardened and v2-with-kb: meaningful tests, appropriate annotations, no PowerMock, prefer slice tests
- **Iterative verification**: Same compile→test→coverage loop as other variants

### Design Choice: Separate Baseline Append
Both prompts get baseline coverage appended via `buildPrompt()` in the base class. The explore prompt gets it so the agent understands the starting point. The act prompt gets it too — technically redundant since the agent remembers from the explore phase, but it's consistent and costs negligible tokens.

## Config Entry

```yaml
- name: variant-d
  promptFile: v3-explore.txt      # explore phase prompt
  actPromptFile: v3-act.txt       # act phase prompt (triggers two-phase dispatch)
  knowledgeDir: knowledge
  knowledgeFiles:
    - index.md                    # full KB tree, same as variant-c
```

The `actPromptFile` field is the dispatch signal — its presence causes `ExperimentApp.createInvoker()` to create a `TwoPhaseCodeCoverageAgentInvoker`.

## What's Different from variant-c

variant-c and variant-d get the same KB files. The difference is orchestration:
- **variant-c**: Single prompt says "read knowledge/ directory" — agent may skim or skip
- **variant-d**: Dedicated explore phase with no-code constraint forces thorough reading, then act phase executes with full context retained in session

This is the Layer 1 (orchestration) vs Layer 2 (knowledge) distinction from the two-layer value model.
