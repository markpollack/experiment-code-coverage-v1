# Step 4.1: Two-Phase Invoker — Learnings

## What Was Built

1. **`AbstractCoverageAgentInvoker`**: Base class with `invoke()` template method. Shared workflow: compile check → JaCoCo injection → baseline measurement → knowledge copy → `invokeAgent()` (abstract) → final coverage → metadata enrichment. Subclasses only implement `invokeAgent()`.

2. **`CodeCoverageAgentInvoker` refactored**: Now extends base class. Only contains `invokeAgent()` (single `AgentClient` call) and `createAgentModel()`. All pre/post logic moved to base. 34 existing tests pass — no behavioral change.

3. **`TwoPhaseCodeCoverageAgentInvoker`**: New subclass. Uses `ClaudeSyncClient` for two turns in one session:
   - `client.connect(explorePrompt)` → `SessionLogParser.parse()` → explore `PhaseCapture`
   - `client.query(actPrompt)` → `SessionLogParser.parse()` → act `PhaseCapture`
   - Returns `AgentResult` with both phases for `InvocationResult.fromPhases()` aggregation

4. **`ExperimentApp.createInvoker()` dispatch**: Checks `variant.isTwoPhase()` → creates `TwoPhaseCodeCoverageAgentInvoker` with pre-loaded act prompt template. The act prompt is loaded once at invoker creation, not per-item.

## Refactoring Pattern

The extract-base-class refactoring followed a clean pattern:
- `invoke()` is `final` in base — subclasses can't bypass the pre/post workflow
- `invokeAgent()` is `protected abstract` — the only extension point
- `buildPrompt()` is `protected` — both subclasses need it (single-phase for the one prompt, two-phase for both prompts)
- `measureCoverage()` is `protected` — visible to subclasses but not part of the public API
- `copyKnowledge()`, `ensureJaCoCoPlugin()`, `hasTestFiles()` remain package-private — used by base class only, but testable from same package

## Inner Record Pattern

`AgentResult(List<PhaseCapture> phases, @Nullable String sessionId)` — a protected inner record of the base class. Keeps the return type of `invokeAgent()` simple and self-documenting without polluting the package namespace.

## Type Change Gotcha

`ExperimentApp.runVariant()` declared the local variable as `CodeCoverageAgentInvoker` — had to change to `AbstractCoverageAgentInvoker` after `createInvoker()` return type changed. Caught by compiler immediately.
