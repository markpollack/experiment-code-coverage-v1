package io.github.markpollack.experiment.coverage;

import java.nio.file.Path;
import java.util.List;

import io.github.markpollack.experiment.agent.InvocationContext;
import io.github.markpollack.journal.claude.PhaseCapture;
import io.github.markpollack.journal.claude.SessionLogParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springaicommunity.claude.agent.sdk.ClaudeClient;
import org.springaicommunity.claude.agent.sdk.ClaudeSyncClient;
import org.springaicommunity.claude.agent.sdk.config.PermissionMode;
import org.springaicommunity.judge.coverage.JaCoCoReportParser.CoverageMetrics;

/**
 * Two-phase agent invoker: explore then act, within a single Claude session.
 *
 * <p>Phase 1 (explore): agent reads knowledge base, understands codebase, creates a plan.
 * Phase 2 (act): agent executes the plan, writing tests with full context from exploration.
 *
 * <p>Session continuity means the act phase inherits all context from the explore phase
 * — the agent doesn't need to re-read files or re-discover project structure.
 */
public class TwoPhaseCodeCoverageAgentInvoker extends AbstractCoverageAgentInvoker {

	private static final Logger logger = LoggerFactory.getLogger(TwoPhaseCodeCoverageAgentInvoker.class);

	private final String actPromptTemplate;

	public TwoPhaseCodeCoverageAgentInvoker(
			@Nullable Path knowledgeSourceDir,
			@Nullable List<String> knowledgeFiles,
			String actPromptTemplate) {
		super(knowledgeSourceDir, knowledgeFiles);
		this.actPromptTemplate = actPromptTemplate;
	}

	@Override
	protected AgentResult invokeAgent(InvocationContext context, CoverageMetrics baseline) throws Exception {
		Path workspace = context.workspacePath();
		String model = context.model();

		String explorePrompt = buildPrompt(context.prompt(), baseline);
		String actPrompt = buildPrompt(actPromptTemplate, baseline);

		logger.info("Step 5: Two-phase invocation (model={})", model);

		try (ClaudeSyncClient client = ClaudeClient.sync()
				.workingDirectory(workspace)
				.model(model)
				.permissionMode(PermissionMode.DANGEROUSLY_SKIP_PERMISSIONS)
				.build()) {

			// Phase 1: Explore
			logger.info("Phase 1: Explore — reading KB and understanding codebase");
			client.connect(explorePrompt);
			PhaseCapture explore = SessionLogParser.parse(
					client.receiveResponse(), "explore", explorePrompt);
			logger.info("Explore complete: {} turns, {} in + {} out tokens, ${}",
					explore.numTurns(), explore.inputTokens(), explore.outputTokens(),
					String.format("%.4f", explore.totalCostUsd()));

			// Phase 2: Act
			logger.info("Phase 2: Act — writing tests using explored context");
			client.query(actPrompt);
			PhaseCapture act = SessionLogParser.parse(
					client.receiveResponse(), "act", actPrompt);
			logger.info("Act complete: {} turns, {} in + {} out tokens, ${}",
					act.numTurns(), act.inputTokens(), act.outputTokens(),
					String.format("%.4f", act.totalCostUsd()));

			List<PhaseCapture> phases = List.of(explore, act);
			String sessionId = act.sessionId() != null ? act.sessionId() : explore.sessionId();

			return new AgentResult(phases, sessionId);
		}
	}

}
