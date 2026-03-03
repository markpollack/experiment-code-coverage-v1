package io.github.markpollack.experiment.coverage;

import java.util.List;

import org.springframework.lang.Nullable;

/**
 * Specification for a single experiment variant. Each variant represents a
 * different configuration of prompt, knowledge, and judges to evaluate.
 *
 * @param name variant identifier (e.g., "control", "variant-a")
 * @param promptFile filename in prompts/ directory (explore prompt for two-phase)
 * @param actPromptFile filename in prompts/ for act phase (null for single-phase)
 * @param knowledgeDir relative path to knowledge directory (null for no knowledge)
 * @param knowledgeFiles specific knowledge files to include
 * @param judgeOverrides judge configuration overrides for this variant
 * @param agent agent type: null = Claude Code (default), "loopy" = LoopyAgent
 * @param model model override: null = use defaultModel from config
 * @param baseUrl API base URL override: null = use default (Anthropic API)
 * @param apiKey API key override: null = use ANTHROPIC_API_KEY env var
 */
public record VariantSpec(
		String name,
		String promptFile,
		@Nullable String actPromptFile,
		String knowledgeDir,
		List<String> knowledgeFiles,
		java.util.Map<String, String> judgeOverrides,
		@Nullable String agent,
		@Nullable String model,
		@Nullable String baseUrl,
		@Nullable String apiKey) {

	public VariantSpec(String name, String promptFile, String knowledgeDir, List<String> knowledgeFiles) {
		this(name, promptFile, null, knowledgeDir, knowledgeFiles, null, null, null, null, null);
	}

	public VariantSpec(String name, String promptFile, @Nullable String actPromptFile,
			String knowledgeDir, List<String> knowledgeFiles) {
		this(name, promptFile, actPromptFile, knowledgeDir, knowledgeFiles, null, null, null, null, null);
	}

	/** Whether this variant uses a two-phase (explore + act) invocation pattern. */
	public boolean isTwoPhase() {
		return actPromptFile != null;
	}

	/** Whether this variant uses the Loopy agent instead of Claude Code. */
	public boolean isLoopy() {
		return "loopy".equals(agent);
	}

}
