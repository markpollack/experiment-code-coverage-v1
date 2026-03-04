# Step 4.0: Context Review — Learnings

## Design Decisions

1. **ClaudeSyncClient over AgentClient**: The two-phase pattern requires session continuity — the act phase must inherit all context (file reads, KB understanding) from the explore phase. `ClaudeSyncClient` from `claude-code-sdk` provides this via `connect()` → `query()` in one session. `AgentClient` creates a fresh session per call.

2. **Abstract base class extraction**: Shared pre/post workflow (compile check, JaCoCo injection, baseline measurement, knowledge copy, final coverage, metadata enrichment) extracted to `AbstractCoverageAgentInvoker`. Two concrete subclasses: single-phase (`CodeCoverageAgentInvoker`) and two-phase (`TwoPhaseCodeCoverageAgentInvoker`). This avoids duplicating ~100 lines of setup/teardown logic.

3. **`actPromptFile` on VariantSpec**: Nullable field — null means single-phase, non-null means two-phase. Cleaner than a boolean flag or separate variant type. `isTwoPhase()` convenience method for dispatch.

4. **`InvocationResult.fromPhases()` over manual construction**: The base class uses `fromPhases()` which aggregates tokens/cost from phase captures and detects errors. Simpler than the old manual `InvocationResult.completed()` call with individual field extraction.

## API Findings

- **ClaudeSyncClient builder**: `ClaudeClient.sync().workingDirectory(ws).model(m).permissionMode(DANGEROUSLY_SKIP_PERMISSIONS).build()`
- **Session flow**: `client.connect(prompt)` starts session, `client.query(prompt)` continues in same session
- **Exhaust capture**: `SessionLogParser.parse(client.receiveResponse(), phaseName, promptText)` → `PhaseCapture` — same parser used by single-phase `AgentClientResponse.getPhaseCapture()`
- **PermissionMode**: `DANGEROUSLY_SKIP_PERMISSIONS` for experiment sandboxes (same as `yolo(true)` in AgentClient)

## Dependencies Verified

- `claude-code-sdk` 1.0.0-SNAPSHOT installed from `~/community/claude-agent-sdk-java`
- `claude-code-capture` already in pom.xml (provides `SessionLogParser.parse()`)
- No new bridge code needed — all APIs compatible
