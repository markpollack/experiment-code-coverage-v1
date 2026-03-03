package io.github.markpollack.experiment.coverage;

import java.util.List;

import io.github.markpollack.experiment.dataset.DatasetManager;
import org.springframework.lang.Nullable;

/**
 * Top-level experiment configuration loaded from experiment-config.yaml.
 * Defines the experiment name, default model, timeout, and the list of
 * variant specifications to run.
 *
 * @param experimentName human-readable experiment name
 * @param defaultModel default LLM model identifier
 * @param timeoutMinutes per-item timeout in minutes
 * @param variants ordered list of variant specifications
 * @param datasetManager configured dataset manager
 * @param itemSlugFilter optional slug to filter to a single dataset item
 */
public record ExperimentVariantConfig(
		String experimentName,
		String defaultModel,
		int timeoutMinutes,
		List<VariantSpec> variants,
		DatasetManager datasetManager,
		@Nullable String itemSlugFilter) {

	public ExperimentVariantConfig(String experimentName, String defaultModel, int timeoutMinutes,
			List<VariantSpec> variants, DatasetManager datasetManager) {
		this(experimentName, defaultModel, timeoutMinutes, variants, datasetManager, null);
	}

	/**
	 * Return a copy with item filtering applied.
	 */
	public ExperimentVariantConfig withItemFilter(String itemSlug) {
		return new ExperimentVariantConfig(experimentName, defaultModel, timeoutMinutes,
				variants, datasetManager, itemSlug);
	}

}
