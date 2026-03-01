package io.github.markpollack.experiment.coverage;

import java.util.List;

import ai.tuvium.experiment.dataset.DatasetManager;

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
 */
public record ExperimentVariantConfig(
		String experimentName,
		String defaultModel,
		int timeoutMinutes,
		List<VariantSpec> variants,
		DatasetManager datasetManager) {

}
