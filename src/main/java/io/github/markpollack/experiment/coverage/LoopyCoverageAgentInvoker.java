package io.github.markpollack.experiment.coverage;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import io.github.markpollack.experiment.agent.InvocationContext;
import io.github.markpollack.journal.claude.PhaseCapture;
import io.github.markpollack.loopy.LoopyAgent;
import io.github.markpollack.loopy.LoopyAgent.LoopyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springaicommunity.judge.coverage.JaCoCoReportParser.CoverageMetrics;

/**
 * Single-phase agent invoker using LoopyAgent (lightweight Spring AI agent).
 * Designed for cost-efficient variants using smaller models like Haiku.
 */
public class LoopyCoverageAgentInvoker extends AbstractCoverageAgentInvoker {

	private static final Logger logger = LoggerFactory.getLogger(LoopyCoverageAgentInvoker.class);

	private final String model;

	@Nullable
	private final String baseUrl;

	@Nullable
	private final String apiKey;

	private final Set<String> disabledTools;

	public LoopyCoverageAgentInvoker(@Nullable Path knowledgeSourceDir,
			@Nullable List<String> knowledgeFiles, String model,
			@Nullable String baseUrl, @Nullable String apiKey,
			Set<String> disabledTools) {
		super(knowledgeSourceDir, knowledgeFiles);
		this.model = model;
		this.baseUrl = baseUrl;
		this.apiKey = apiKey;
		this.disabledTools = disabledTools != null ? disabledTools : Set.of();
	}

	@Override
	protected AgentResult invokeAgent(InvocationContext context, CoverageMetrics baseline) {
		Path workspace = context.workspacePath();

		logger.info("Step 5: Invoking LoopyAgent (model={}, baseUrl={})", model,
				baseUrl != null ? baseUrl : "default");

		var builder = LoopyAgent.builder()
				.model(model)
				.workingDirectory(workspace)
				.maxTurns(300)
				.costLimit(20.0)
				.timeout(java.time.Duration.ofMinutes(45))
				.sessionMemory(false)
				.compactionThreshold(0.3)
				.disabledTools(disabledTools);
		if (baseUrl != null) {
			builder.baseUrl(baseUrl);
		}
		if (apiKey != null) {
			builder.apiKey(apiKey);
		}
		LoopyAgent agent = builder.build();

		String prompt = buildPrompt(context.prompt(), baseline);
		long startMs = System.currentTimeMillis();
		LoopyResult result = agent.run(prompt);
		long durationMs = System.currentTimeMillis() - startMs;

		logger.info("LoopyAgent complete: status={}, turns={}, toolCalls={}, in={}, out={}, ${}",
				result.status(), result.turnsCompleted(), result.toolCallsExecuted(),
				result.inputTokens(), result.outputTokens(),
				String.format("%.4f", result.estimatedCost()));

		boolean isError = !Set.of("COMPLETED", "COST_LIMIT_EXCEEDED", "TURN_LIMIT_REACHED")
				.contains(result.status());

		PhaseCapture capture = new PhaseCapture(
				"loopy",                          // phaseName
				prompt,                           // promptText
				(int) result.inputTokens(),       // inputTokens
				(int) result.outputTokens(),      // outputTokens
				0,                                // thinkingTokens
				durationMs,                       // durationMs
				durationMs,                       // apiDurationMs
				result.estimatedCost(),           // totalCostUsd
				null,                             // sessionId (no session for Loopy)
				result.turnsCompleted(),          // numTurns
				isError,                          // isError
				result.output(),                  // textOutput
				List.of(),                        // thinkingBlocks
				List.of(),                        // toolUses
				null                              // rawResult
		);

		return new AgentResult(List.of(capture), null);
	}

}
