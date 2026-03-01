package io.github.markpollack.experiment.coverage;

import java.util.List;

/**
 * Specification for a single experiment variant. Each variant represents a
 * different configuration of prompt, knowledge, and judges to evaluate.
 *
 * @param name variant identifier (e.g., "control", "variant-a")
 * @param promptFile filename in prompts/ directory
 * @param knowledgeDir relative path to knowledge directory (null for no knowledge)
 * @param knowledgeFiles specific knowledge files to include
 * @param judgeOverrides judge configuration overrides for this variant
 */
public record VariantSpec(
		String name,
		String promptFile,
		String knowledgeDir,
		List<String> knowledgeFiles,
		java.util.Map<String, String> judgeOverrides) {

	public VariantSpec(String name, String promptFile, String knowledgeDir, List<String> knowledgeFiles) {
		this(name, promptFile, knowledgeDir, knowledgeFiles, null);
	}

}
