# Roadmap: {{PROJECT_NAME}}

> **Created**: {{DATETIME}}
> **Status**: Not started

## Overview

Implementation roadmap for the {{PROJECT_NAME}} experiment.

---

## Stage 1: Project Setup

### Step 1.0: Design Review

**Entry criteria**:
- [ ] Read: `plans/VISION-TEMPLATE.md`
- [ ] Read: `plans/DESIGN-TEMPLATE.md`

**Work items**:
- [ ] REVIEW design for completeness
- [ ] FILL IN domain-specific sections
- [ ] RENAME templates to remove `-TEMPLATE` suffix

**Exit criteria**:
- [ ] VISION.md and DESIGN.md populated with domain content
- [ ] Create: `plans/learnings/step-1.0-design-review.md`
- [ ] COMMIT

### Step 1.1: Implement AgentInvoker

**Entry criteria**:
- [ ] Step 1.0 complete

**Work items**:
- [ ] RENAME `TemplateAgentInvoker` to `{{Domain}}AgentInvoker`
- [ ] IMPLEMENT domain-specific agent invocation logic
- [ ] WIRE UP AgentClient with appropriate model

**Exit criteria**:
- [ ] AgentInvoker compiles and passes basic test
- [ ] Create: `plans/learnings/step-1.1-agent-invoker.md`
- [ ] COMMIT

### Step 1.2: Configure Judges

**Entry criteria**:
- [ ] Step 1.1 complete

**Work items**:
- [ ] CONFIGURE deterministic judges (build, preservation)
- [ ] IMPLEMENT domain-specific judges (if any)
- [ ] WIRE UP JuryFactory with correct tier policies

**Exit criteria**:
- [ ] Jury builds and judges a mock context
- [ ] Create: `plans/learnings/step-1.2-judges.md`
- [ ] COMMIT

### Step 1.3: Populate Dataset

**Entry criteria**:
- [ ] Step 1.2 complete

**Work items**:
- [ ] POPULATE `dataset/items.yaml` with benchmark items
- [ ] VERIFY each dataset item builds and tests pass
- [ ] CONFIGURE workspace materialization

**Exit criteria**:
- [ ] All dataset items resolve and build
- [ ] Create: `plans/learnings/step-1.3-dataset.md`
- [ ] COMMIT

---

## Stage 2: Variant Execution

### Step 2.0: Write Prompts and Knowledge

**Work items**:
- [ ] WRITE prompt files for each variant
- [ ] WRITE knowledge files
- [ ] CONFIGURE variant specs in experiment-config.yaml

### Step 2.1: Run Variants

**Work items**:
- [ ] RUN all variants: `--run-all-variants`
- [ ] REVIEW growth story output
- [ ] ANALYZE results

**Exit criteria**:
- [ ] `analysis/growth-story.md` generated with all variants
- [ ] Create: `plans/learnings/step-2.1-results.md`
- [ ] COMMIT
