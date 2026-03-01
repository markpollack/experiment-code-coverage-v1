package io.github.markpollack.experiment.coverage;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import ai.tuvium.experiment.agent.AgentInvocationException;
import ai.tuvium.experiment.agent.AgentInvoker;
import ai.tuvium.experiment.agent.InvocationContext;
import ai.tuvium.experiment.agent.InvocationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.agents.client.AgentClient;
import org.springaicommunity.agents.client.AgentClientResponse;
import org.springaicommunity.agents.claude.ClaudeAgentModel;
import org.springaicommunity.agents.claude.ClaudeAgentOptions;
import org.springaicommunity.agents.model.AgentModel;
import org.springaicommunity.judge.coverage.JaCoCoReportParser;
import org.springaicommunity.judge.coverage.JaCoCoReportParser.CoverageMetrics;
import org.springaicommunity.judge.exec.util.MavenBuildRunner;
import org.springaicommunity.judge.exec.util.MavenBuildRunner.BuildResult;
import org.springaicommunity.judge.exec.util.MavenTestRunner;

/**
 * Invokes an AI agent to improve JUnit test coverage on a Spring Boot Maven project.
 *
 * <p>Workflow:</p>
 * <ol>
 *   <li>Measure baseline coverage via JaCoCo</li>
 *   <li>Invoke agent with coverage improvement prompt</li>
 *   <li>Measure final coverage</li>
 *   <li>Store baseline/final metrics in result metadata</li>
 * </ol>
 */
public class CodeCoverageAgentInvoker implements AgentInvoker {

	private static final Logger logger = LoggerFactory.getLogger(CodeCoverageAgentInvoker.class);

	@Override
	public InvocationResult invoke(InvocationContext context) throws AgentInvocationException {
		long startTime = System.currentTimeMillis();
		Path workspace = context.workspacePath();

		logger.info("=== Coverage Agent: {} ===", workspace.getFileName());

		// 1. Verify baseline builds
		logger.info("Step 1: Verifying project compiles");
		BuildResult compileResult = MavenBuildRunner.runBuild(workspace, 5, "clean", "compile");
		if (!compileResult.success()) {
			return InvocationResult.error("Project does not compile: " + compileResult.output(),
					context.metadata());
		}

		// 2. Measure baseline coverage
		logger.info("Step 2: Measuring baseline coverage");
		CoverageMetrics baseline = measureCoverage(workspace);
		logger.info("Baseline coverage: line={}%, branch={}%",
				baseline.lineCoverage(), baseline.branchCoverage());

		// 3. Invoke agent with prompt (prompt includes knowledge if variant uses it)
		logger.info("Step 3: Invoking agent (model={})", context.model());
		AgentModel agentModel = createAgentModel(context.model(), workspace);
		AgentClient client = AgentClient.create(agentModel);

		String prompt = buildPrompt(context.prompt(), baseline);

		AgentClientResponse response;
		try {
			response = client.goal(prompt).workingDirectory(workspace).run();
		}
		catch (Exception ex) {
			long durationMs = System.currentTimeMillis() - startTime;
			logger.error("Agent execution failed", ex);
			return InvocationResult.error("Agent execution failed: " + ex.getMessage(),
					context.metadata());
		}

		// 4. Measure final coverage
		logger.info("Step 4: Measuring final coverage");
		CoverageMetrics finalCov = measureCoverage(workspace);
		double improvement = finalCov.lineCoverage() - baseline.lineCoverage();
		logger.info("Final coverage: line={}%, branch={}% (improvement: {}pp)",
				finalCov.lineCoverage(), finalCov.branchCoverage(), improvement);

		long durationMs = System.currentTimeMillis() - startTime;

		// Store coverage metrics in metadata for judges to consume
		Map<String, String> enrichedMetadata = new java.util.HashMap<>(context.metadata());
		enrichedMetadata.put("baselineCoverage", String.valueOf(baseline.lineCoverage()));
		enrichedMetadata.put("finalCoverage", String.valueOf(finalCov.lineCoverage()));
		enrichedMetadata.put("baselineBranchCoverage", String.valueOf(baseline.branchCoverage()));
		enrichedMetadata.put("finalBranchCoverage", String.valueOf(finalCov.branchCoverage()));
		enrichedMetadata.put("coverageImprovement", String.valueOf(improvement));

		return InvocationResult.completed(
				List.of(),      // phases — no phase capture in this simple invoker
				0,              // inputTokens — not tracked at this level
				0,              // outputTokens
				0,              // thinkingTokens
				0.0,            // costUsd
				durationMs,
				null,           // sessionId
				enrichedMetadata
		);
	}

	private String buildPrompt(String basePrompt, CoverageMetrics baseline) {
		StringBuilder sb = new StringBuilder(basePrompt);
		sb.append("\n\n## Current Coverage Metrics\n");
		sb.append("- Line coverage: ").append(baseline.lineCoverage()).append("%\n");
		sb.append("- Branch coverage: ").append(baseline.branchCoverage()).append("%\n");
		if (baseline.lineCoverage() == 0.0) {
			sb.append("\nNote: No JaCoCo plugin detected. You will need to add it to the pom.xml.\n");
		}
		return sb.toString();
	}

	private CoverageMetrics measureCoverage(Path workspace) {
		try {
			// Try running tests with JaCoCo report generation
			BuildResult result = MavenBuildRunner.runBuild(workspace, 10, "clean", "test", "jacoco:report");
			if (result.success()) {
				return JaCoCoReportParser.parse(workspace);
			}
		}
		catch (Exception ex) {
			logger.debug("Coverage measurement failed (JaCoCo may not be configured): {}", ex.getMessage());
		}
		// Return zeros if JaCoCo isn't configured — agent will add it
		return new CoverageMetrics(0.0, 0.0, 0.0, 0, 0, 0, 0, 0, 0, "No JaCoCo report");
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
