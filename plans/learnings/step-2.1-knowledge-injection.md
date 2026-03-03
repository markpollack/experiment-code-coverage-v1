# Step 2.1: Add JIT Knowledge Injection — Learnings

**Completed**: 2026-03-02

## What was done

- Added `@Nullable Path knowledgeSourceDir` and `@Nullable List<String> knowledgeFiles` fields to `CodeCoverageAgentInvoker` with a two-arg constructor alongside the existing no-arg constructor
- Implemented `copyKnowledge(Path workspace)` in `invoke()` between baseline measurement and agent invocation:
  - If `knowledgeFiles` contains `index.md` → copies entire `knowledgeSourceDir` recursively (variant-c: full KB tree for JIT navigation)
  - Otherwise → copies only listed files preserving relative paths (variant-b: 3 targeted files)
  - Target: `workspace/knowledge/` directory
- Wired `ExperimentApp.createInvoker(VariantSpec)` to pass knowledge config when variant has `knowledgeDir`
- Added 6 unit tests for knowledge copying (`KnowledgeInjectionTest`)

## Key decisions

### copyKnowledge() is package-private

Made `copyKnowledge(Path workspace)` package-private so tests can call it directly without needing to set up the full `InvocationContext` chain. Same pattern as `parseJudgment()` in `TestQualityJudge`.

### Index.md as tree-copy signal

The presence of `index.md` in the knowledge files list signals "copy the entire directory tree." This is a convention, not a flag — the YAML config for variant-c simply lists `index.md` as the only file, and the code interprets this as "the agent will JIT-navigate from the index."

### Knowledge copied into workspace, not referenced externally

Files are copied into `workspace/knowledge/` so the agent sees them alongside the project it's working on. No external path references — the workspace is self-contained.

## Verification

- `./mvnw test` — 17 tests pass (11 TestQualityJudge + 6 KnowledgeInjection)
- `./mvnw compile` — clean
