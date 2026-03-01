# Roadmap: Code Coverage Experiment

> **Created**: 2026-03-01
> **Last updated**: 2026-03-01
> **Status**: Stage 1 in progress (Steps 1.0–1.2 complete)

## Overview

Grow a code coverage improvement agent through 4 variants across 5 Spring Getting Started guides. Demonstrate that knowledge injection > prompt engineering > model choice.

---

## Stage 1: Project Setup

### Step 1.0: Design Review

**Status**: Complete (scaffolded by `markpollack/forge`)

**Deliverables**: VISION.md, DESIGN.md, ROADMAP.md populated with domain content. Project compiles.

---

### Step 1.1: Implement AgentInvoker

**Status**: Complete

**Deliverables**: `CodeCoverageAgentInvoker` — measures baseline/final JaCoCo coverage, invokes agent via AgentClient, enriches metadata for judges.

---

### Step 1.2: Write Prompts and Knowledge

**Status**: Complete

**Deliverables**: 3 prompt files (v0-naive, v1-hardened, v2-with-kb), 4 knowledge files (coverage-fundamentals, jacoco-patterns, spring-test-slices, common-gaps), experiment-config.yaml with 4 variants.

---

### Step 1.3: Populate and Verify Dataset

**Entry criteria**:
- [x] Steps 1.0–1.2 complete

**Work items**:
- [ ] CLONE 5 Spring guide repos into dataset/workspaces/
- [ ] VERIFY each guide's `complete/` subdirectory compiles: `mvn clean compile`
- [ ] VERIFY existing tests pass: `mvn test`
- [ ] CONFIGURE workspace materialization (git clone per item)

**Exit criteria**:
- [ ] All 5 dataset items resolve and build
- [ ] Create: `plans/learnings/step-1.3-dataset.md`
- [ ] COMMIT

---

### Step 1.4: Implement TestQualityJudge (Tier 3)

**Entry criteria**:
- [ ] Step 1.3 complete

**Work items**:
- [ ] IMPLEMENT `TestQualityJudge` — LLM-driven judge evaluating BDD semantics, meaningful assertions, proper test naming
- [ ] WIRE UP in JuryFactory at Tier 3 with FINAL_TIER policy
- [ ] WRITE unit test with mock LLM response

**Exit criteria**:
- [ ] TestQualityJudge compiles and passes basic test
- [ ] Create: `plans/learnings/step-1.4-test-quality-judge.md`
- [ ] COMMIT

---

## Stage 2: Variant Execution

### Step 2.0: Run Control Variant

**Entry criteria**:
- [ ] Stage 1 complete

**Work items**:
- [ ] RUN control variant (v0-naive, no KB) on all 5 guides
- [ ] VERIFY results are stored correctly
- [ ] REVIEW baseline growth story

**Exit criteria**:
- [ ] Control results in results/ directory
- [ ] Baseline coverage numbers recorded
- [ ] COMMIT

---

### Step 2.1: Run All Variants

**Entry criteria**:
- [ ] Step 2.0 complete

**Work items**:
- [ ] RUN variant-a (v1-hardened, no KB)
- [ ] RUN variant-b (v2-with-kb, 3 KB files)
- [ ] RUN variant-c (v2-with-kb, 4 KB files)
- [ ] GENERATE growth story with all variant comparisons

**Exit criteria**:
- [ ] `analysis/growth-story.md` generated with all 4 variants
- [ ] Coverage improvement data validates hypothesis (KB > prompt > baseline)
- [ ] Create: `plans/learnings/step-2.1-results.md`
- [ ] COMMIT

---

## Stage 3: Analysis and Graduation

### Step 3.0: Analyze Results

**Work items**:
- [ ] ANALYZE growth story for patterns
- [ ] IDENTIFY which knowledge file had most impact
- [ ] DOCUMENT findings in `analysis/` directory

### Step 3.1: Graduate Best Variant

**Work items**:
- [ ] EXTRACT best variant → standalone agent project
- [ ] PACKAGE for ACP marketplace (deferred)

---

## Learnings Structure

```
plans/learnings/
├── step-1.3-dataset.md
├── step-1.4-test-quality-judge.md
├── step-2.0-control.md
└── step-2.1-results.md
```

---

## Revision History

| Timestamp | Change | Trigger |
|-----------|--------|---------|
| 2026-03-01 | Initial — Steps 1.0-1.2 complete via forge bootstrapping session | Genesis session |
