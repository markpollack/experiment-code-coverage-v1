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
import java.util.Set;

import ai.tuvium.experiment.diagnostic.DefaultEfficiencyEvaluator;
import ai.tuvium.experiment.diagnostic.EfficiencyConfig;
import ai.tuvium.experiment.diagnostic.EfficiencyReport;
import ai.tuvium.experiment.diagnostic.ReasoningContext;
import io.github.markpollack.journal.claude.PhaseCapture;
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

		// 2. Ensure JaCoCo plugin is in pom.xml so baseline measurement works
		ensureJaCoCoPlugin(workspace);

		// 3. Measure baseline coverage (skip if no test files exist)
		CoverageMetrics baseline;
		if (hasTestFiles(workspace)) {
			logger.info("Step 3: Measuring baseline coverage");
			baseline = measureCoverage(workspace);
			logger.info("Baseline coverage: line={}%, branch={}%",
					baseline.lineCoverage(), baseline.branchCoverage());
		}
		else {
			logger.info("Step 3: No test files found — baseline is 0%");
			baseline = new CoverageMetrics(0.0, 0.0, 0.0, 0, 0, 0, 0, 0, 0, "No tests");
		}

		// 4. Copy knowledge files into workspace (if configured)
		copyKnowledge(workspace);

		// 5. Invoke agent with prompt
		logger.info("Step 5: Invoking agent (model={})", context.model());
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

		// 6. Extract agent exhaust capture
		PhaseCapture capture = response.getPhaseCapture();
		if (capture != null) {
			logger.info("Agent exhaust: {} turns, {} in + {} out tokens, ${}",
					capture.numTurns(), capture.inputTokens(), capture.outputTokens(),
					String.format("%.4f", capture.totalCostUsd()));
		}

		// 7. Measure final coverage
		logger.info("Step 7: Measuring final coverage");
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

		List<PhaseCapture> phases = capture != null ? List.of(capture) : List.of();

		InvocationResult invocationResult = InvocationResult.completed(
				phases,
				capture != null ? capture.inputTokens() : 0,
				capture != null ? capture.outputTokens() : 0,
				capture != null ? capture.thinkingTokens() : 0,
				capture != null ? capture.totalCostUsd() : 0.0,
				durationMs,
				capture != null ? capture.sessionId() : null,
				enrichedMetadata
		);

		// 8. Evaluate efficiency from agent trajectory
		logger.info("Step 8: Evaluating efficiency");
		ReasoningContext reasoningContext = new ReasoningContext(
				null, null, Set.of(), phases,
				null, null, List.of(), workspace, null);
		EfficiencyReport efficiencyReport = new DefaultEfficiencyEvaluator()
				.evaluate(invocationResult, reasoningContext, EfficiencyConfig.defaults());
		for (var entry : efficiencyReport.scores().entrySet()) {
			enrichedMetadata.put(entry.getKey(), String.valueOf(entry.getValue()));
		}
		logger.info("Efficiency: composite={}, checks={}",
				String.format("%.3f", efficiencyReport.compositeScore()),
				efficiencyReport.checks().stream()
						.map(c -> c.metric() + "=" + String.format("%.3f", c.normalizedScore()))
						.toList());

		return invocationResult;
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
			logger.info("Step 4: Copying full knowledge tree from {}", knowledgeSourceDir);
			copyDirectoryRecursively(knowledgeSourceDir, targetDir);
		}
		else {
			// Targeted file copy — only specific files
			logger.info("Step 4: Copying {} targeted knowledge files", knowledgeFiles.size());
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

	private static final String JACOCO_PLUGIN_SNIPPET = """
			<plugin>
				<groupId>org.jacoco</groupId>
				<artifactId>jacoco-maven-plugin</artifactId>
				<version>0.8.12</version>
				<executions>
					<execution>
						<id>default</id>
						<goals>
							<goal>prepare-agent</goal>
						</goals>
					</execution>
					<execution>
						<id>report</id>
						<phase>test</phase>
						<goals>
							<goal>report</goal>
						</goals>
					</execution>
				</executions>
			</plugin>
			""";

	/**
	 * Ensure the JaCoCo Maven plugin is present in pom.xml so that baseline
	 * coverage measurement works even if the project doesn't ship with it.
	 */
	void ensureJaCoCoPlugin(Path workspace) {
		Path pomPath = workspace.resolve("pom.xml");
		if (!Files.isRegularFile(pomPath)) {
			logger.warn("No pom.xml found in workspace — skipping JaCoCo injection");
			return;
		}

		try {
			String pom = Files.readString(pomPath);
			if (pom.contains("jacoco-maven-plugin")) {
				logger.info("Step 2: JaCoCo plugin already present");
				return;
			}

			logger.info("Step 2: Injecting JaCoCo plugin into pom.xml");

			String updated;
			if (pom.contains("</plugins>")) {
				// Insert before closing </plugins> tag
				updated = pom.replace("</plugins>", JACOCO_PLUGIN_SNIPPET + "    </plugins>");
			}
			else if (pom.contains("</build>")) {
				// No <plugins> section — add one
				updated = pom.replace("</build>",
						"    <plugins>\n" + JACOCO_PLUGIN_SNIPPET + "    </plugins>\n  </build>");
			}
			else {
				// No <build> section at all — add before </project>
				updated = pom.replace("</project>",
						"  <build>\n    <plugins>\n" + JACOCO_PLUGIN_SNIPPET + "    </plugins>\n  </build>\n</project>");
			}

			Files.writeString(pomPath, updated);
			logger.info("JaCoCo plugin injected into {}", pomPath);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to read/write pom.xml for JaCoCo injection", ex);
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
		if (baseline.lineCoverage() == 0.0 && baseline.linesTotal() == 0) {
			sb.append("\n\n## Current State\n");
			sb.append("No tests exist yet. Coverage is 0%.\n");
			sb.append("JaCoCo is already configured. Run `./mvnw clean test jacoco:report` to generate coverage reports.\n");
		}
		else {
			sb.append("\n\n## Current Coverage Metrics\n");
			sb.append("- Line coverage: ").append(String.format("%.1f", baseline.lineCoverage())).append("%\n");
			sb.append("- Branch coverage: ").append(String.format("%.1f", baseline.branchCoverage())).append("%\n");
			sb.append("- Lines covered: ").append(baseline.linesCovered()).append("/").append(baseline.linesTotal()).append("\n");
			sb.append("\nNote: JaCoCo is already configured. Run `./mvnw clean test jacoco:report` to regenerate coverage.\n");
		}
		return sb.toString();
	}

	/**
	 * Check if the workspace has any Java test files under src/test/java/.
	 */
	boolean hasTestFiles(Path workspace) {
		Path testJavaDir = workspace.resolve("src/test/java");
		if (!Files.isDirectory(testJavaDir)) {
			return false;
		}
		try (var stream = Files.walk(testJavaDir)) {
			return stream.anyMatch(p -> p.toString().endsWith(".java"));
		}
		catch (IOException ex) {
			logger.warn("Failed to scan test directory: {}", ex.getMessage());
			return false;
		}
	}

	private CoverageMetrics measureCoverage(Path workspace) {
		BuildResult result = MavenBuildRunner.runBuild(workspace, 10, "clean", "test");
		if (result.success()) {
			return JaCoCoReportParser.parse(workspace);
		}
		logger.warn("Test execution failed during coverage measurement: {}", result.output().substring(0, Math.min(500, result.output().length())));
		return new CoverageMetrics(0.0, 0.0, 0.0, 0, 0, 0, 0, 0, 0, "Tests failed");
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
