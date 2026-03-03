package io.github.markpollack.experiment.coverage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.markpollack.experiment.diagnostic.EfficiencyConfig;
import io.github.markpollack.experiment.dataset.DatasetManager;
import io.github.markpollack.experiment.comparison.ComparisonEngine;
import io.github.markpollack.experiment.comparison.ComparisonResult;
import io.github.markpollack.experiment.comparison.DefaultComparisonEngine;
import io.github.markpollack.experiment.dataset.FileSystemDatasetManager;
import io.github.markpollack.experiment.result.ExperimentResult;
import io.github.markpollack.experiment.runner.ExperimentConfig;
import io.github.markpollack.experiment.runner.ExperimentRunner;
import io.github.markpollack.experiment.store.ActiveSession;
import io.github.markpollack.experiment.store.FileSystemResultStore;
import io.github.markpollack.experiment.store.FileSystemSessionStore;
import io.github.markpollack.experiment.store.ResultStore;
import io.github.markpollack.experiment.store.RunSessionStatus;
import io.github.markpollack.experiment.store.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springaicommunity.judge.coverage.CoverageImprovementJudge;
import org.springaicommunity.judge.coverage.CoveragePreservationJudge;
import org.springaicommunity.judge.exec.BuildSuccessJudge;
import org.springaicommunity.judge.jury.Jury;
import org.springaicommunity.judge.jury.TierPolicy;
import org.yaml.snakeyaml.Yaml;

/**
 * Pre-wired experiment application. Reads variant configurations, iterates through
 * each variant, runs the experiment loop, and produces a growth story comparing results
 * across variants.
 *
 * <p>Domain-specific projects customize this by providing:
 * <ul>
 *   <li>A concrete {@link CodeCoverageAgentInvoker} (created per-variant)</li>
 *   <li>Custom judges (if any)</li>
 *   <li>Knowledge files and prompts per variant</li>
 * </ul>
 */
public class ExperimentApp {

	private static final Logger logger = LoggerFactory.getLogger(ExperimentApp.class);

	private static final DateTimeFormatter SESSION_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
		.withZone(ZoneOffset.UTC);

	private final ExperimentVariantConfig variantConfig;

	private final JuryFactory juryFactory;

	private final ResultStore resultStore;

	private final SessionStore sessionStore;

	private final ComparisonEngine comparisonEngine;

	private final GrowthStoryReporter reporter;

	private final Path projectRoot;

	public ExperimentApp(ExperimentVariantConfig variantConfig, JuryFactory juryFactory,
			ResultStore resultStore, SessionStore sessionStore, Path projectRoot) {
		this.variantConfig = variantConfig;
		this.juryFactory = juryFactory;
		this.resultStore = resultStore;
		this.sessionStore = sessionStore;
		this.comparisonEngine = new DefaultComparisonEngine(resultStore);
		this.reporter = new GrowthStoryReporter(projectRoot.resolve("analysis"));
		this.projectRoot = projectRoot;
	}

	/**
	 * Run a single variant experiment within a session.
	 */
	public ExperimentResult runVariant(VariantSpec variant, String sessionName) {
		logger.info("Running variant: {} (session: {})", variant.name(), sessionName);

		Jury jury = juryFactory.build(variant);
		AbstractCoverageAgentInvoker invoker = createInvoker(variant);

		String model = variant.model() != null ? variant.model() : variantConfig.defaultModel();

		ExperimentConfig config = ExperimentConfig.builder()
			.experimentName(variantConfig.experimentName())
			.datasetDir(projectRoot.resolve("dataset"))
			.promptTemplate(loadPrompt(variant))
			.model(model)
			.perItemTimeout(Duration.ofMinutes(variantConfig.timeoutMinutes()))
			.knowledgeBaseDir(variant.knowledgeDir() != null ? projectRoot.resolve(variant.knowledgeDir()) : null)
			.preserveWorkspaces(true)
			.outputDir(projectRoot.resolve("results"))
			.efficiencyConfig(EfficiencyConfig.defaults())
			.build();

		DatasetManager datasetManager = variantConfig.itemSlugFilter() != null
				? new SlugFilteringDatasetManager(variantConfig.datasetManager(), variantConfig.itemSlugFilter())
				: variantConfig.datasetManager();

		ExperimentRunner runner = new ExperimentRunner(
				datasetManager, jury, resultStore, sessionStore, config);

		ActiveSession activeSession = new ActiveSession(
				sessionName, variantConfig.experimentName(), variant.name());

		ExperimentResult result = runner.run(invoker, activeSession);

		logger.info("========================================");
		logger.info("  VARIANT '{}' COMPLETE", variant.name());
		logger.info("  Pass rate: {}", String.format("%.1f%%", result.passRate() * 100));
		logger.info("  Cost: ${}", String.format("%.4f", result.totalCostUsd()));
		logger.info("  Duration: {}s", result.totalDurationMs() / 1000);
		logger.info("========================================");

		return result;
	}

	/**
	 * Run all variants in sequence within a single session.
	 */
	public void runAllVariants() {
		List<VariantSpec> variants = variantConfig.variants();
		String sessionName = SESSION_NAME_FORMAT.format(Instant.now());

		logger.info("Running {} variants for experiment '{}' (session: {})",
				variants.size(), variantConfig.experimentName(), sessionName);

		sessionStore.createSession(sessionName, variantConfig.experimentName(), Map.of());

		try {
			ExperimentResult previousResult = null;

			for (VariantSpec variant : variants) {
				ExperimentResult result = runVariant(variant, sessionName);

				if (previousResult != null) {
					ComparisonResult comparison = comparisonEngine.compare(result, previousResult);
					reporter.appendComparison(variant.name(), comparison);
				}
				else {
					reporter.appendBaseline(variant.name(), comparisonEngine.summarize(result));
				}

				previousResult = result;
			}

			reporter.generateGrowthStory();
			logger.info("Growth story written to analysis/growth-story.md");
			sessionStore.finalizeSession(sessionName, variantConfig.experimentName(),
					RunSessionStatus.COMPLETED);
		}
		catch (Exception ex) {
			sessionStore.finalizeSession(sessionName, variantConfig.experimentName(),
					RunSessionStatus.FAILED);
			throw ex;
		}
	}

	/**
	 * Create a per-variant invoker with optional knowledge injection.
	 * Dispatches based on agent type and phase configuration.
	 */
	AbstractCoverageAgentInvoker createInvoker(VariantSpec variant) {
		Path knowledgeSourceDir = variant.knowledgeDir() != null
				? projectRoot.resolve(variant.knowledgeDir()) : null;
		List<String> knowledgeFiles = variant.knowledgeFiles();
		boolean hasKnowledge = knowledgeSourceDir != null && !knowledgeFiles.isEmpty();

		if (variant.isLoopy()) {
			String model = variant.model() != null ? variant.model() : variantConfig.defaultModel();
			return new LoopyCoverageAgentInvoker(
					hasKnowledge ? knowledgeSourceDir : null,
					hasKnowledge ? knowledgeFiles : null,
					model, variant.baseUrl(), variant.apiKey(),
					Set.of("Task"));
		}

		if (variant.isTwoPhase()) {
			String actPrompt = loadPromptFile(variant.actPromptFile());
			return new TwoPhaseCodeCoverageAgentInvoker(
					hasKnowledge ? knowledgeSourceDir : null,
					hasKnowledge ? knowledgeFiles : null,
					actPrompt);
		}

		if (hasKnowledge) {
			return new CodeCoverageAgentInvoker(knowledgeSourceDir, knowledgeFiles);
		}
		return new CodeCoverageAgentInvoker();
	}

	private String loadPrompt(VariantSpec variant) {
		return loadPromptFile(variant.promptFile());
	}

	private String loadPromptFile(String promptFileName) {
		Path promptPath = projectRoot.resolve("prompts").resolve(promptFileName);
		try {
			return Files.readString(promptPath);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to load prompt: " + promptPath, ex);
		}
	}

	/**
	 * Load experiment configuration from a YAML file.
	 * @param configPath path to experiment-config.yaml
	 * @return parsed configuration with a {@link FileSystemDatasetManager}
	 */
	@SuppressWarnings("unchecked")
	static ExperimentVariantConfig loadConfig(Path configPath) {
		Yaml yaml = new Yaml();
		Map<String, Object> raw;
		try (InputStream in = Files.newInputStream(configPath)) {
			raw = yaml.load(in);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to load config: " + configPath, ex);
		}

		String experimentName = (String) raw.get("experimentName");
		String defaultModel = (String) raw.get("defaultModel");
		int timeoutMinutes = (int) raw.get("timeoutMinutes");

		List<Map<String, Object>> rawVariants = (List<Map<String, Object>>) raw.get("variants");
		List<VariantSpec> variants = new ArrayList<>();
		for (Map<String, Object> rv : rawVariants) {
			String name = (String) rv.get("name");
			String promptFile = (String) rv.get("promptFile");
			String actPromptFile = (String) rv.get("actPromptFile");
			String knowledgeDir = (String) rv.get("knowledgeDir");
			List<String> knowledgeFiles = rv.get("knowledgeFiles") != null
					? (List<String>) rv.get("knowledgeFiles")
					: List.of();
			String agent = (String) rv.get("agent");
			String model = (String) rv.get("model");
			String baseUrl = (String) rv.get("baseUrl");
			String apiKey = (String) rv.get("apiKey");
			variants.add(new VariantSpec(name, promptFile, actPromptFile, knowledgeDir, knowledgeFiles,
					null, agent, model, baseUrl, apiKey));
		}

		return new ExperimentVariantConfig(
				experimentName, defaultModel, timeoutMinutes,
				List.copyOf(variants), new FileSystemDatasetManager());
	}

	/**
	 * Build the standard 4-tier jury factory.
	 */
	static JuryFactory buildJuryFactory(Path projectRoot) {
		return JuryFactory.builder()
			.addJudge(0, BuildSuccessJudge.maven("clean", "test"))
			.tierPolicy(0, TierPolicy.REJECT_ON_ANY_FAIL)
			.addJudge(1, new CoveragePreservationJudge())
			.tierPolicy(1, TierPolicy.REJECT_ON_ANY_FAIL)
			.addJudge(2, new CoverageImprovementJudge())
			.addJudge(2, new GoldenTestComparisonJudge())
			.tierPolicy(2, TierPolicy.REJECT_ON_ANY_FAIL)
			.addJudge(3, new TestQualityJudge(
					TestQualityJudge.defaultAgentClientFactory("claude-sonnet-4-6", Duration.ofMinutes(3)),
					projectRoot.resolve("prompts/judge-practice-adherence.txt")))
			.tierPolicy(3, TierPolicy.FINAL_TIER)
			.build();
	}

	/**
	 * Main entry point. Usage:
	 * <pre>
	 *   ./mvnw compile exec:java -Dexec.args="--variant control"
	 *   ./mvnw compile exec:java -Dexec.args="--variant control --item gs-rest-service"
	 *   ./mvnw compile exec:java -Dexec.args="--run-all-variants"
	 * </pre>
	 */
	public static void main(String[] args) {
		// Force IPv4 for Tailscale compatibility (Java may attempt IPv6 and hang)
		System.setProperty("java.net.preferIPv4Stack", "true");

		Path projectRoot = Path.of(System.getProperty("user.dir"));

		// Parse CLI arguments
		String targetVariant = null;
		String targetItem = null;
		boolean runAll = false;

		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "--variant" -> {
					if (i + 1 >= args.length) {
						logger.error("--variant requires a variant name");
						System.exit(1);
					}
					targetVariant = args[++i];
				}
				case "--item" -> {
					if (i + 1 >= args.length) {
						logger.error("--item requires an item slug");
						System.exit(1);
					}
					targetItem = args[++i];
				}
				case "--run-all-variants" -> runAll = true;
				case "--project-root" -> {
					if (i + 1 >= args.length) {
						logger.error("--project-root requires a path");
						System.exit(1);
					}
					projectRoot = Path.of(args[++i]);
				}
				default -> {
					logger.error("Unknown argument: {}", args[i]);
					System.exit(1);
				}
			}
		}

		if (targetVariant == null && !runAll) {
			logger.error("Usage: --variant <name> | --run-all-variants [--item <slug>]");
			System.exit(1);
		}

		// Load config
		ExperimentVariantConfig variantConfig = loadConfig(projectRoot.resolve("experiment-config.yaml"));

		// Apply item filter if specified
		if (targetItem != null) {
			variantConfig = variantConfig.withItemFilter(targetItem);
			logger.info("Filtering to single item: {}", targetItem);
		}

		final ExperimentVariantConfig config = variantConfig;
		logger.info("Loaded experiment '{}' with {} variants (model={}, timeout={}min)",
				config.experimentName(), config.variants().size(),
				config.defaultModel(), config.timeoutMinutes());

		// Wire components
		Path resultsDir = projectRoot.resolve("results");
		ResultStore resultStore = new FileSystemResultStore(resultsDir);
		SessionStore sessionStore = new FileSystemSessionStore(resultsDir);
		JuryFactory juryFactory = buildJuryFactory(projectRoot);

		ExperimentApp app = new ExperimentApp(config, juryFactory, resultStore, sessionStore, projectRoot);

		// Dispatch
		if (runAll) {
			app.runAllVariants();
		}
		else {
			String variantName = targetVariant;
			VariantSpec variant = config.variants().stream()
				.filter(v -> v.name().equals(variantName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"Unknown variant: " + variantName + ". Available: "
								+ config.variants().stream().map(VariantSpec::name).toList()));

			// Single-variant run also uses a session for consistent layout
			String sessionName = SESSION_NAME_FORMAT.format(Instant.now());
			sessionStore.createSession(sessionName, config.experimentName(), Map.of());
			try {
				app.runVariant(variant, sessionName);
				sessionStore.finalizeSession(sessionName, config.experimentName(),
						RunSessionStatus.COMPLETED);
			}
			catch (Exception ex) {
				sessionStore.finalizeSession(sessionName, config.experimentName(),
						RunSessionStatus.FAILED);
				throw ex;
			}
		}
	}

}
