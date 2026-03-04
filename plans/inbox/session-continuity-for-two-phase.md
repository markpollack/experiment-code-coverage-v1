# Session Continuity Required for Two-Phase Variant (variant-d)

> Written from research-conversation-agent session, 2026-03-03.

## Decision

**variant-d must use `claude-code-sdk` (`ClaudeSyncClient`), not two independent `AgentClient` calls.**

The two-phase explore-then-act pattern requires session continuity — the model retains its full reasoning context from the explore phase when it starts the act phase. Two independent `AgentClient` calls would be two cold starts with no shared memory.

## Why session continuity matters

With `ClaudeSyncClient` (one session, two turns):
1. Explore phase: agent reads project structure, reads KB, writes TEST_PLAN.md
2. Act phase: agent implements tests — **remembering everything it noticed during exploration**

The agent doesn't just have the TEST_PLAN.md artifact. It remembers *why* it chose `@WebMvcTest` over `@SpringBootTest`, which files confused it, what patterns it noticed in the source code. The plan file is a lossy compression of the explore phase's reasoning.

With two independent `AgentClient` calls:
1. Explore phase: agent reads project, writes TEST_PLAN.md
2. Act phase: agent starts fresh — only knows what's in its prompt + TEST_PLAN.md

The reasoning context is lost. The act phase might make different decisions than the explore phase intended, because it can't see the reasoning behind the plan.

## The refactoring agent proves this

`RefactoringAgent.java` uses exactly this pattern:

```java
try (ClaudeSyncClient client = ClaudeClient.sync(options).workingDirectory(workingDir).build()) {
    // Phase 1: Explore
    client.connect(explorePrompt);
    explorePhase = SessionLogParser.parse(client.receiveResponse(), "explore", explorePrompt);

    // Phase 2: Act — full session memory from Phase 1 preserved
    client.query(actPrompt);
    actPhase = SessionLogParser.parse(client.receiveResponse(), "act", actPrompt);
}
```

Two turns, one session. The 80% → 100% pass rate improvement came with this pattern.

## What this means for the code-coverage experiment

### New dependency

Add `claude-code-sdk` alongside the existing `agent-client`:

```xml
<!-- Claude Code SDK — session continuity for two-phase variant -->
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>claude-code-sdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Source: `~/community/claude-agent-sdk-java/`. Must be installed locally (`./mvnw install` in that repo).

### Implementation sketch

variant-d's `CodeCoverageAgentInvoker` (or a new `TwoPhaseAgentInvoker`):

```java
try (ClaudeSyncClient client = ClaudeClient.sync(options)
        .workingDirectory(workspace)
        .build()) {

    // Phase 1: Explore + Plan
    client.connect(explorePrompt);  // reads project, reads KB, writes TEST_PLAN.md
    ParsedMessage exploreResult = client.receiveResponse();

    // Phase 2: Act
    client.query(actPrompt);        // implements tests per plan, runs ./mvnw test
    ParsedMessage actResult = client.receiveResponse();
}
```

### Exhaust capture

The existing `claude-sdk-capture` bridge (`~/tuvium/projects/refactoring-agent/claude-sdk-capture/`) converts `ClaudeSyncClient` responses into tracking events. The code-coverage experiment's exhaust capture (Step 2.2b) currently uses `AgentClient` response parsing. variant-d will need the SDK capture path instead, or a unified adapter.

Consider: the `spring-ai-exhaust` project (`~/tuvium/projects/spring-ai-exhaust/`) extracts from `ChatResponse`. If variant-d uses `ClaudeSyncClient` directly (not Spring AI), it needs the SDK capture path from refactoring-agent.

## The broader gap: agent-client needs session support

This isn't unique to our experiment. Any multi-phase agent pattern (plan+act, explore+implement, draft+review) needs session continuity. The current `AgentClient` API models single request-response interactions.

What's needed in agent-client:
- A `Session` or `Conversation` abstraction that maintains message history across multiple `send()` calls
- Internally, this means threading the `messages` array (Anthropic API) or using `previous_response_id` (OpenAI)
- `ClaudeSyncClient` does this via the Claude CLI's subprocess — the CLI maintains the conversation

This is worth raising as a feature gap for agent-client. Every multi-phase pattern we've built (refactoring-agent, code-coverage variant-d, PR review pipeline) needs it. Currently we work around it by using `claude-code-sdk` directly, but that's Claude-specific. The abstraction should live in agent-client so it works across providers.

## Action items

1. **Immediate**: Add `claude-code-sdk` dependency to code-coverage-experiment for variant-d
2. **Immediate**: Implement `TwoPhaseAgentInvoker` using `ClaudeSyncClient` pattern from refactoring-agent
3. **Near-term**: Wire exhaust capture for SDK responses (reuse `claude-sdk-capture` bridge or adapt)
4. **Future**: Add session/conversation support to agent-client API (provider-agnostic multi-turn)
