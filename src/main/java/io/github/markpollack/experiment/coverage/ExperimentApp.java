package io.github.markpollack.experiment.coverage;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import ai.tuvium.experiment.agent.AgentInvoker;
import ai.tuvium.experiment.comparison.ComparisonEngine;
import ai.tuvium.experiment.comparison.ComparisonResult;
import ai.tuvium.experiment.comparison.DefaultComparisonEngine;
import ai.tuvium.experiment.result.ExperimentResult;
import ai.tuvium.experiment.result.KnowledgeManifest;
import ai.tuvium.experiment.runner.ExperimentConfig;
import ai.tuvium.experiment.runner.ExperimentRunner;
import ai.tuvium.experiment.store.ResultStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.judge.jury.Jury;

/**
 * Pre-wired experiment application. Reads variant configurations, iterates through
 * each variant, runs the experiment loop, and produces a growth story comparing results
 * across variants.
 *
 * <p>Domain-specific projects customize this by providing:
 * <ul>
 *   <li>A concrete {@link AgentInvoker} implementation</li>
 *   <li>Custom judges (if any)</li>
 *   <li>Knowledge files and prompts per variant</li>
 * </ul>
 */
public class ExperimentApp {

	private static final Logger logger = LoggerFactory.getLogger(ExperimentApp.class);

	private final ExperimentVariantConfig variantConfig;

	private final AgentInvoker agentInvoker;

	private final JuryFactory juryFactory;

	private final ResultStore resultStore;

	private final ComparisonEngine comparisonEngine;

	private final GrowthStoryReporter reporter;

	private final Path projectRoot;

	public ExperimentApp(ExperimentVariantConfig variantConfig, AgentInvoker agentInvoker, JuryFactory juryFactory,
			ResultStore resultStore, Path projectRoot) {
		this.variantConfig = variantConfig;
		this.agentInvoker = agentInvoker;
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

		ExperimentConfig config = ExperimentConfig.builder()
			.experimentName(variantConfig.experimentName() + "/" + variant.name())
			.datasetDir(projectRoot.resolve("dataset"))
			.promptTemplate(loadPrompt(variant))
			.model(variantConfig.defaultModel())
			.perItemTimeout(Duration.ofMinutes(variantConfig.timeoutMinutes()))
			.knowledgeBaseDir(variant.knowledgeDir() != null ? projectRoot.resolve(variant.knowledgeDir()) : null)
			.preserveWorkspaces(true)
			.outputDir(projectRoot.resolve("results"))
			.build();

		ExperimentRunner runner = new ExperimentRunner(
				variantConfig.datasetManager(), jury, resultStore, config);

		ExperimentResult result = runner.run(agentInvoker);

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

	private String loadPrompt(VariantSpec variant) {
		Path promptPath = projectRoot.resolve("prompts").resolve(variant.promptFile());
		try {
			return java.nio.file.Files.readString(promptPath);
		}
		catch (java.io.IOException ex) {
			throw new java.io.UncheckedIOException("Failed to load prompt: " + promptPath, ex);
		}
	}

	/**
	 * Main entry point. Usage: java -jar experiment.jar [--variant name | --run-all-variants]
	 */
	public static void main(String[] args) {
		logger.info("Agent Experiment Template");
		logger.info("========================");
		logger.info("");
		logger.info("This is a template project. To use it:");
		logger.info("1. Implement your AgentInvoker (replace TemplateAgentInvoker)");
		logger.info("2. Configure variants in experiment-config.yaml");
		logger.info("3. Add prompts, knowledge files, and dataset items");
		logger.info("4. Run with: --run-all-variants or --variant <name>");
	}

}
