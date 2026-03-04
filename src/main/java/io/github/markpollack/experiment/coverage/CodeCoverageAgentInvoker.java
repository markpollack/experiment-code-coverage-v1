package io.github.markpollack.experiment.coverage;

import java.nio.file.Path;
import java.util.List;

import ai.tuvium.experiment.agent.InvocationContext;
import io.github.markpollack.journal.claude.PhaseCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springaicommunity.agents.client.AgentClient;
import org.springaicommunity.agents.client.AgentClientResponse;
import org.springaicommunity.agents.claude.ClaudeAgentModel;
import org.springaicommunity.agents.claude.ClaudeAgentOptions;
import org.springaicommunity.agents.model.AgentModel;
import org.springaicommunity.judge.coverage.JaCoCoReportParser.CoverageMetrics;

/**
 * Single-phase agent invoker: one AgentClient call per dataset item.
 * Used for control, variant-a, variant-b, and variant-c.
 */
public class CodeCoverageAgentInvoker extends AbstractCoverageAgentInvoker {

	private static final Logger logger = LoggerFactory.getLogger(CodeCoverageAgentInvoker.class);

	public CodeCoverageAgentInvoker() {
		this(null, null);
	}

	public CodeCoverageAgentInvoker(@Nullable Path knowledgeSourceDir, @Nullable List<String> knowledgeFiles) {
		super(knowledgeSourceDir, knowledgeFiles);
	}

	@Override
	protected AgentResult invokeAgent(InvocationContext context, CoverageMetrics baseline) {
		Path workspace = context.workspacePath();

		logger.info("Step 5: Invoking agent (model={})", context.model());
		AgentModel agentModel = createAgentModel(context.model(), workspace);
		AgentClient client = AgentClient.create(agentModel);

		String prompt = buildPrompt(context.prompt(), baseline);

		AgentClientResponse response = client.goal(prompt).workingDirectory(workspace).run();

		PhaseCapture capture = response.getPhaseCapture();
		if (capture != null) {
			logger.info("Agent exhaust: {} turns, {} in + {} out tokens, ${}",
					capture.numTurns(), capture.inputTokens(), capture.outputTokens(),
					String.format("%.4f", capture.totalCostUsd()));
		}

		List<PhaseCapture> phases = capture != null ? List.of(capture) : List.of();
		String sessionId = capture != null ? capture.sessionId() : null;

		return new AgentResult(phases, sessionId);
	}

	private AgentModel createAgentModel(String model, Path workingDirectory) {
		ClaudeAgentOptions options = ClaudeAgentOptions.builder()
				.model(model)
				.yolo(true)
				.build();

		return ClaudeAgentModel.builder()
				.workingDirectory(workingDirectory)
				.defaultOptions(options)
				.build();
	}

}
