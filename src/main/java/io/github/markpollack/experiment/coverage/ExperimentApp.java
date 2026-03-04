package io.github.markpollack.experiment.coverage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ai.tuvium.experiment.diagnostic.EfficiencyConfig;
import ai.tuvium.experiment.dataset.DatasetManager;
import ai.tuvium.experiment.comparison.ComparisonEngine;
import ai.tuvium.experiment.comparison.ComparisonResult;
import ai.tuvium.experiment.comparison.DefaultComparisonEngine;
import ai.tuvium.experiment.dataset.FileSystemDatasetManager;
import ai.tuvium.experiment.result.ExperimentResult;
import ai.tuvium.experiment.runner.ExperimentConfig;
import ai.tuvium.experiment.runner.ExperimentRunner;
import ai.tuvium.experiment.store.FileSystemResultStore;
import ai.tuvium.experiment.store.ResultStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private final ExperimentVariantConfig variantConfig;

	private final JuryFactory juryFactory;

	private final ResultStore resultStore;

	private final ComparisonEngine comparisonEngine;

	private final GrowthStoryReporter reporter;

	private final Path projectRoot;

	public ExperimentApp(ExperimentVariantConfig variantConfig, JuryFactory juryFactory,
			ResultStore resultStore, Path projectRoot) {
		this.variantConfig = variantConfig;
		this.juryFactory = juryFactory;
		this.resultStore = resultStore;
		this.comparisonEngine = new DefaultComparisonEngine(resultStore);
		this.reporter = new GrowthStoryReporter(projectRoot.resolve("analysis"));
		this.projectRoot = projectRoot;
	}

	/**
	 * Run a single variant experiment.
	 */
	public ExperimentResult runVariant(VariantSpec variant) {
		logger.info("Running variant: {}", variant.name());

		Jury jury = juryFactory.build(variant);
		AbstractCoverageAgentInvoker invoker = createInvoker(variant);

		ExperimentConfig config = ExperimentConfig.builder()
			.experimentName(variantConfig.experimentName() + "/" + variant.name())
			.datasetDir(projectRoot.resolve("dataset"))
			.promptTemplate(loadPrompt(variant))
			.model(variantConfig.defaultModel())
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
				datasetManager, jury, resultStore, config);

		ExperimentResult result = runner.run(invoker);

		logger.info("Variant '{}' complete: passRate={}, cost=${}",
				variant.name(),
				String.format("%.1f%%", result.passRate() * 100),
				String.format("%.4f", result.totalCostUsd()));

		return result;
	}

	/**
	 * Run all variants in sequence, comparing each against the previous.
	 */
	public void runAllVariants() {
		List<VariantSpec> variants = variantConfig.variants();
		logger.info("Running {} variants for experiment '{}'", variants.size(), variantConfig.experimentName());

		ExperimentResult previousResult = null;

		for (VariantSpec variant : variants) {
			ExperimentResult result = runVariant(variant);

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
	}

	/**
	 * Create a per-variant invoker with optional knowledge injection.
	 * Dispatches to two-phase invoker when actPromptFile is set.
	 */
	AbstractCoverageAgentInvoker createInvoker(VariantSpec variant) {
		Path knowledgeSourceDir = variant.knowledgeDir() != null
				? projectRoot.resolve(variant.knowledgeDir()) : null;
		List<String> knowledgeFiles = variant.knowledgeFiles();
		boolean hasKnowledge = knowledgeSourceDir != null && !knowledgeFiles.isEmpty();

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
			variants.add(new VariantSpec(name, promptFile, actPromptFile, knowledgeDir, knowledgeFiles));
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
		ResultStore resultStore = new FileSystemResultStore(projectRoot.resolve("results"));
		JuryFactory juryFactory = buildJuryFactory(projectRoot);

		ExperimentApp app = new ExperimentApp(config, juryFactory, resultStore, projectRoot);

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
			app.runVariant(variant);
		}
	}

}
