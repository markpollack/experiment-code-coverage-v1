# Loopy + Haiku Variant for Code Coverage Experiment

> Written from research-conversation-agent session, 2026-03-04.
> Motivation: DevNexus talk needs "expensive black box vs cheap agent you built" contrast.
> Simplest implementation path — no AgentClient wrapper, no SDK, no subprocess.

---

## Why This Matters

The thesis is `knowledge + structured execution > model`. The strongest proof:

- **Claude Code** (Sonnet, off-the-shelf, ~$0.50/run) vs **Loopy + Haiku** (your own agent, cheap model, good KB, ~$0.02/run)
- If Loopy+Haiku+KB matches or beats Claude Code on code-coverage, that's the headline
- Audience takeaway: "they built their own Claude Code in ~13 Java classes and it competed with the real thing"

---

## What MiniAgent Already Provides

MiniAgent (vendored in Loopy, ~13 classes) is a Spring AI `ChatClient` with an agent loop:

```java
MiniAgent agent = MiniAgent.builder()
    .model(chatModel)                    // Any Spring AI ChatModel — Haiku, Sonnet, Ollama
    .config(MiniAgentConfig.builder()
        .workingDirectory(workspace)
        .systemPrompt(prompt)
        .maxTurns(50)
        .build())
    .sessionMemory()                     // In-memory, preserves context across run() calls
    .build();

MiniAgentResult result = agent.run(task);
// result.totalTokens(), result.toolCallsExecuted(), result.estimatedCost()
```

Tools included: BashTool, FileSystemTools, GlobTool, GrepTool, SubmitTool, TodoWriteTool, TaskTool.

**Session continuity is free**: with `sessionMemory()`, each `run()` call sees the full conversation history. Plan+act is just two `run()` calls on the same instance. No subprocess management, no SDK, no ClaudeSyncClient equivalent needed.

---

## Implementation: LoopyCoverageAgentInvoker

### The Simplest Path

Don't wrap Loopy in AgentClient. Don't write a loopy-agent-sdk. Construct MiniAgent directly in a new invoker class. The experiment-driver already has the template-method pattern for this.

### Approach: Depend on Loopy Directly (Programmatic API)

We own both codebases. No vendoring, no subprocess, no AgentClient wrapper. Add Loopy as a Maven dependency and call it programmatically.

**Step 1 — Add programmatic API to Loopy** (`LoopyAgent.java`, new public class):

```java
// io.github.markpollack.loopy.LoopyAgent — the programmatic entry point
// Thin stable API over Loopy internals. Experiment-driver consumes this.
public class LoopyAgent {

    public static Builder builder() { ... }

    /** Run a task. With session memory, context is preserved across calls. */
    public LoopyResult run(String task) { ... }

    /** Clear session memory, start fresh. */
    public void clearSession() { ... }

    public record LoopyResult(
        String status,          // COMPLETED, TURN_LIMIT_REACHED, TIMEOUT, etc.
        String output,
        int turnsCompleted,
        int toolCallsExecuted,
        long totalTokens,
        double estimatedCost
    ) {}

    public static class Builder {
        public Builder model(String modelId) { ... }      // "claude-haiku-4-5-20251001"
        public Builder workingDirectory(Path dir) { ... }
        public Builder systemPrompt(String prompt) { ... }
        public Builder maxTurns(int turns) { ... }
        public Builder sessionMemory(boolean enabled) { ... }  // default true
        public LoopyAgent build() { ... }
    }
}
```

Internally creates `AnthropicChatModel` from model ID + `ANTHROPIC_API_KEY` env var, wires MiniAgent with tools, session memory, etc. All Loopy internals stay internal. The experiment only sees `LoopyAgent`.

When Wiggum memory lands in Loopy, `LoopyAgent` gets it automatically — no experiment-driver changes needed.

**Step 2 — Add Loopy dependency to experiment-driver** (pom.xml):

```xml
<dependency>
    <groupId>io.github.markpollack</groupId>
    <artifactId>loopy</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Requires `cd ~/projects/loopy && ./mvnw install` first. Pulls in Spring AI transitively.

**Step 3 — Write LoopyCoverageAgentInvoker** (~60 lines):

```java
public class LoopyCoverageAgentInvoker extends AbstractCoverageAgentInvoker {

    @Override
    protected AgentResult invokeAgent(InvocationContext context, CoverageMetrics baseline)
            throws Exception {

        LoopyAgent agent = LoopyAgent.builder()
            .model(context.model())
            .workingDirectory(context.workspace())
            .systemPrompt(context.systemPrompt())
            .maxTurns(80)
            .sessionMemory(true)
            .build();

        if (context.variant().isTwoPhase()) {
            // Plan + act — session memory preserves context between calls
            String explorePrompt = buildPrompt(context.prompt(), baseline);
            LoopyResult exploreResult = agent.run(explorePrompt);
            PhaseCapture explore = toPhaseCapture(exploreResult, "explore", explorePrompt);

            LoopyResult actResult = agent.run(context.actPrompt());
            PhaseCapture act = toPhaseCapture(actResult, "act", context.actPrompt());

            return new AgentResult(List.of(explore, act), UUID.randomUUID().toString());
        } else {
            String prompt = buildPrompt(context.prompt(), baseline);
            LoopyResult result = agent.run(prompt);
            PhaseCapture capture = toPhaseCapture(result, "single", prompt);
            return new AgentResult(List.of(capture), UUID.randomUUID().toString());
        }
    }

    private PhaseCapture toPhaseCapture(LoopyResult result, String phase, String prompt) {
        return PhaseCapture.builder()
            .phase(phase)
            .prompt(prompt)
            .output(result.output())
            .status(result.status())
            .totalTokens(result.totalTokens())
            .toolCallsExecuted(result.toolCallsExecuted())
            .estimatedCost(result.estimatedCost())
            .build();
    }
}
```

### Variant Configuration (experiment-config.yaml)

Add new variants:

```yaml
  # Loopy + Haiku — cheap model, same knowledge
  - name: loopy-haiku-control
    agent: loopy
    model: claude-haiku-4-5-20251001
    promptFile: v0-naive.txt
    knowledgeFiles: []

  - name: loopy-haiku-kb
    agent: loopy
    model: claude-haiku-4-5-20251001
    promptFile: v2-with-kb.txt
    knowledgeFiles:
      - index.md

  # Loopy + Sonnet — same model as Claude Code, your own agent
  - name: loopy-sonnet-kb
    agent: loopy
    model: claude-sonnet-4-6
    promptFile: v2-with-kb.txt
    knowledgeFiles:
      - index.md
```

### Dispatcher Update (ExperimentApp.java)

```java
private AgentInvoker createInvoker(VariantSpec variant) {
    if ("loopy".equals(variant.agent())) {
        return new LoopyCoverageAgentInvoker(...);
    } else if (variant.isTwoPhase()) {
        return new TwoPhaseCodeCoverageAgentInvoker(...);
    } else {
        return new CodeCoverageAgentInvoker(...);
    }
}
```

---

## What This Tests

| Comparison | What it isolates |
|-----------|-----------------|
| Claude Code (Sonnet) vs Loopy (Sonnet), same KB | Agent quality: does the off-the-shelf agent outperform a simple loop? |
| Claude Code (Sonnet) vs Loopy (Haiku), same KB | Model vs knowledge: can a cheap model + good KB beat an expensive agent? |
| Loopy (Haiku) control vs Loopy (Haiku) + KB | Knowledge value on a cheap model: does KB help MORE when the model is weaker? |
| Loopy (Haiku) vs Loopy (Sonnet), same KB | Model scaling: is the Sonnet premium worth it when you have good knowledge? |

The DevNexus headline comparison: **Claude Code Sonnet (~$0.50/run) vs Loopy Haiku + KB (~$0.02/run)**.

---

## Exhaust Capture Gap

MiniAgentResult gives us tokens and estimated cost, but NOT the full conversation log (individual messages, tool call details). For the experiment this is acceptable — we get the metrics we need for comparison. Full exhaust capture (à la spring-ai-exhaust) is a future enhancement.

What we capture:
- ✓ Total tokens (input + output)
- ✓ Tool calls executed count
- ✓ Estimated cost
- ✓ Status (completed, turn limit, timeout)
- ✓ Final output text
- ✗ Per-turn token breakdown
- ✗ Individual tool call details
- ✗ Thinking/reasoning content

For the ablation comparison, the aggregate metrics are sufficient.

---

## Sequencing

1. **Add `LoopyAgent` class to Loopy** — stable programmatic API (~100 lines), `./mvnw install`
2. **Add Loopy dependency** to experiment-driver pom.xml
3. **Write LoopyCoverageAgentInvoker** (~60 lines)
4. **Add loopy-haiku-control variant** to experiment-config.yaml
5. **Smoke test**: `--variant loopy-haiku-control --item gs-rest-service`
6. **Add loopy-haiku-kb and loopy-sonnet-kb variants**
7. **Run full comparison**: all variants × all items
8. **Compare**: Claude Code vs Loopy+Haiku in results table

Steps 1-5 should take one session. The key risk is whether Haiku has enough capability for the code-coverage task — it might struggle with complex test generation. That's data, not a problem.

---

## Why This Approach (Not AgentClient, Not Vendoring)

**Not AgentClient**: AgentClient wraps external CLI agents via subprocess. Loopy runs in-process — same JVM, no process overhead, session continuity via ChatMemory.

**Not vendoring MiniAgent**: We own Loopy. Depending on it directly means when Wiggum memory lands, or when tools improve, the experiment gets those upgrades for free. No code to copy and maintain.

**Not subprocess (`loopy -p`)**: Works for single-shot but awkward for plan+act (two prompts, one session). Programmatic API is cleaner and gives us direct access to result metrics.

The `LoopyAgent` API also serves as the future seam for an AgentClient wrapper — useful later for Spring AI Bench ("bring your agent") but not needed for this experiment.

---

## Connection to Talk

Act 4 (The Benchmark) can show this comparison:

> "Claude Code — Anthropic's flagship coding agent. $0.50 per run. Sonnet 4.6."
> "Loopy — 13 Java classes. Spring AI. Haiku. $0.02 per run."
> "Same task. Same judges. Same grading."
> [Show results table]
> "Knowledge and structure beat model. Twenty-five times cheaper."

That's the water cooler moment.
