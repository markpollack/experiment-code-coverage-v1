package io.github.markpollack.experiment.coverage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;

import ai.tuvium.experiment.agent.AgentInvocationException;
import ai.tuvium.experiment.agent.AgentInvoker;
import ai.tuvium.experiment.agent.InvocationContext;
import ai.tuvium.experiment.agent.InvocationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springaicommunity.agents.client.AgentClient;
import org.springaicommunity.agents.client.AgentClientResponse;
import org.springaicommunity.agents.claude.ClaudeAgentModel;
import org.springaicommunity.agents.claude.ClaudeAgentOptions;
import org.springaicommunity.agents.model.AgentModel;
import org.springaicommunity.judge.coverage.JaCoCoReportParser;
import org.springaicommunity.judge.coverage.JaCoCoReportParser.CoverageMetrics;
import org.springaicommunity.judge.exec.util.MavenBuildRunner;
import org.springaicommunity.judge.exec.util.MavenBuildRunner.BuildResult;

/**
 * Invokes an AI agent to improve JUnit test coverage on a Spring Boot Maven project.
 *
 * <p>Workflow:</p>
 * <ol>
 *   <li>Measure baseline coverage via JaCoCo</li>
 *   <li>Copy knowledge files into workspace (if configured)</li>
 *   <li>Invoke agent with coverage improvement prompt</li>
 *   <li>Measure final coverage</li>
 *   <li>Store baseline/final metrics in result metadata</li>
 * </ol>
 */
public class CodeCoverageAgentInvoker implements AgentInvoker {

	private static final Logger logger = LoggerFactory.getLogger(CodeCoverageAgentInvoker.class);

	@Nullable
	private final Path knowledgeSourceDir;

	@Nullable
	private final List<String> knowledgeFiles;

	public CodeCoverageAgentInvoker() {
		this(null, null);
	}

	public CodeCoverageAgentInvoker(@Nullable Path knowledgeSourceDir, @Nullable List<String> knowledgeFiles) {
		this.knowledgeSourceDir = knowledgeSourceDir;
		this.knowledgeFiles = knowledgeFiles;
	}

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

		// 3. Copy knowledge files into workspace (if configured)
		copyKnowledge(workspace);

		// 4. Invoke agent with prompt
		logger.info("Step 4: Invoking agent (model={})", context.model());
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

		// 5. Measure final coverage
		logger.info("Step 5: Measuring final coverage");
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

	/**
	 * Copy knowledge files into the workspace for the agent to discover.
	 * If knowledgeFiles contains "index.md", copies the entire knowledge tree
	 * (variant-c: JIT navigation from index). Otherwise copies only the listed
	 * files preserving relative paths (variant-b: targeted files).
	 */
	void copyKnowledge(Path workspace) {
		if (knowledgeSourceDir == null || knowledgeFiles == null || knowledgeFiles.isEmpty()) {
			return;
		}

		Path targetDir = workspace.resolve("knowledge");

		if (knowledgeFiles.contains("index.md")) {
			// Full tree copy — agent navigates via index.md
			logger.info("Step 3: Copying full knowledge tree from {}", knowledgeSourceDir);
			copyDirectoryRecursively(knowledgeSourceDir, targetDir);
		}
		else {
			// Targeted file copy — only specific files
			logger.info("Step 3: Copying {} targeted knowledge files", knowledgeFiles.size());
			for (String relativePath : knowledgeFiles) {
				Path source = knowledgeSourceDir.resolve(relativePath);
				Path target = targetDir.resolve(relativePath);
				try {
					Files.createDirectories(target.getParent());
					Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
					logger.debug("Copied knowledge file: {}", relativePath);
				}
				catch (IOException ex) {
					throw new UncheckedIOException("Failed to copy knowledge file: " + relativePath, ex);
				}
			}
		}
	}

	private void copyDirectoryRecursively(Path source, Path target) {
		try {
			Files.walkFileTree(source, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					Path targetSubDir = target.resolve(source.relativize(dir));
					Files.createDirectories(targetSubDir);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Path targetFile = target.resolve(source.relativize(file));
					Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
					return FileVisitResult.CONTINUE;
				}
			});
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to copy knowledge directory: " + source, ex);
		}
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
