package io.github.markpollack.experiment.coverage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.markpollack.experiment.agent.AgentInvocationException;
import io.github.markpollack.experiment.agent.AgentInvoker;
import io.github.markpollack.experiment.agent.InvocationContext;
import io.github.markpollack.experiment.agent.InvocationResult;
import io.github.markpollack.journal.claude.PhaseCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springaicommunity.judge.coverage.JaCoCoReportParser;
import org.springaicommunity.judge.coverage.JaCoCoReportParser.CoverageMetrics;
import org.springaicommunity.judge.exec.util.MavenBuildRunner;
import org.springaicommunity.judge.exec.util.MavenBuildRunner.BuildResult;

/**
 * Base class for coverage agent invokers. Provides the shared workflow:
 * compile check → JaCoCo injection → baseline measurement → knowledge copy →
 * [agent invocation] → final coverage measurement → metadata enrichment.
 *
 * <p>Subclasses implement {@link #invokeAgent(InvocationContext, CoverageMetrics)}
 * to define how the agent is actually called (single-phase vs two-phase).
 */
public abstract class AbstractCoverageAgentInvoker implements AgentInvoker {

	private static final Logger logger = LoggerFactory.getLogger(AbstractCoverageAgentInvoker.class);

	@Nullable
	private final Path knowledgeSourceDir;

	@Nullable
	private final List<String> knowledgeFiles;

	protected AbstractCoverageAgentInvoker(@Nullable Path knowledgeSourceDir,
			@Nullable List<String> knowledgeFiles) {
		this.knowledgeSourceDir = knowledgeSourceDir;
		this.knowledgeFiles = knowledgeFiles;
	}

	@Override
	public final InvocationResult invoke(InvocationContext context) throws AgentInvocationException {
		long startTime = System.currentTimeMillis();
		Path workspace = context.workspacePath();

		String itemSlug = context.metadata().getOrDefault("itemId", workspace.getFileName().toString());
		logger.info("=== Coverage Agent: {} ===", itemSlug);

		// 1. Verify baseline builds
		logger.info("Step 1: Verifying project compiles");
		BuildResult compileResult = MavenBuildRunner.runBuild(workspace, 5, "clean", "compile");
		if (!compileResult.success()) {
			return InvocationResult.error("Project does not compile: " + compileResult.output(),
					context.metadata());
		}

		// 1b. Generate project analysis report for SAE variants
		ProjectAnalyzer.analyze(workspace);

		// 2. Ensure JaCoCo plugin
		ensureJaCoCoPlugin(workspace);

		// 3. Measure baseline coverage
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

		// 4. Copy knowledge files into workspace
		copyKnowledge(workspace);

		// 5. Invoke agent (subclass-specific)
		AgentResult agentResult;
		try {
			agentResult = invokeAgent(context, baseline);
		}
		catch (Exception ex) {
			logger.error("Agent execution failed", ex);
			return InvocationResult.error("Agent execution failed: " + ex.getMessage(),
					context.metadata());
		}

		// 6. Measure final coverage
		logger.info("Measuring final coverage");
		CoverageMetrics finalCov = measureCoverage(workspace);
		double improvement = finalCov.lineCoverage() - baseline.lineCoverage();
		logger.info("Final coverage: line={}%, branch={}% (improvement: {}pp)",
				finalCov.lineCoverage(), finalCov.branchCoverage(), improvement);

		long durationMs = System.currentTimeMillis() - startTime;

		// Enrich metadata with coverage metrics for judges
		Map<String, String> enrichedMetadata = new HashMap<>(context.metadata());
		enrichedMetadata.put("baselineCoverage", String.valueOf(baseline.lineCoverage()));
		enrichedMetadata.put("finalCoverage", String.valueOf(finalCov.lineCoverage()));
		enrichedMetadata.put("baselineBranchCoverage", String.valueOf(baseline.branchCoverage()));
		enrichedMetadata.put("finalBranchCoverage", String.valueOf(finalCov.branchCoverage()));
		enrichedMetadata.put("coverageImprovement", String.valueOf(improvement));

		return InvocationResult.fromPhases(agentResult.phases(), durationMs,
				agentResult.sessionId(), enrichedMetadata);
	}

	/**
	 * Invoke the agent and return phase captures. Subclasses define the invocation
	 * strategy (single call vs multi-turn session).
	 *
	 * @param context invocation context with workspace, prompt, model
	 * @param baseline measured baseline coverage metrics
	 * @return agent result with phase captures
	 */
	protected abstract AgentResult invokeAgent(InvocationContext context, CoverageMetrics baseline)
			throws Exception;

	/**
	 * Build the prompt with baseline coverage appended.
	 */
	protected String buildPrompt(String basePrompt, CoverageMetrics baseline) {
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
	 * Copy knowledge files into the workspace for the agent to discover.
	 * If knowledgeFiles contains "index.md", copies the entire knowledge tree.
	 * Otherwise copies only the listed files preserving relative paths.
	 */
	void copyKnowledge(Path workspace) {
		if (knowledgeSourceDir == null || knowledgeFiles == null || knowledgeFiles.isEmpty()) {
			return;
		}

		Path targetDir = workspace.resolve("knowledge");

		if (knowledgeFiles.contains("index.md")) {
			logger.info("Step 4: Copying full knowledge tree from {}", knowledgeSourceDir);
			copyDirectoryRecursively(knowledgeSourceDir, targetDir);
		}
		else {
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
				updated = pom.replace("</plugins>", JACOCO_PLUGIN_SNIPPET + "    </plugins>");
			}
			else if (pom.contains("</build>")) {
				updated = pom.replace("</build>",
						"    <plugins>\n" + JACOCO_PLUGIN_SNIPPET + "    </plugins>\n  </build>");
			}
			else {
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

	protected CoverageMetrics measureCoverage(Path workspace) {
		BuildResult result = MavenBuildRunner.runBuild(workspace, 10, "clean", "test", "jacoco:report");
		if (result.success()) {
			return JaCoCoReportParser.parse(workspace);
		}
		logger.warn("Test execution failed during coverage measurement: {}",
				result.output().substring(0, Math.min(500, result.output().length())));
		return new CoverageMetrics(0.0, 0.0, 0.0, 0, 0, 0, 0, 0, 0, "Tests failed");
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

	/**
	 * Result from the agent invocation phase, carrying phase captures and session ID.
	 */
	protected record AgentResult(List<PhaseCapture> phases, @Nullable String sessionId) {
	}

}
