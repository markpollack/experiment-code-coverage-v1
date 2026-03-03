package io.github.markpollack.experiment.coverage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for knowledge file copying in {@link CodeCoverageAgentInvoker}.
 */
class KnowledgeInjectionTest {

	@TempDir
	Path tempDir;

	@Test
	void noKnowledge_copiesNothing() throws IOException {
		Path workspace = tempDir.resolve("workspace");
		Files.createDirectories(workspace);

		CodeCoverageAgentInvoker invoker = new CodeCoverageAgentInvoker();
		invoker.copyKnowledge(workspace);

		assertThat(workspace.resolve("knowledge")).doesNotExist();
	}

	@Test
	void targetedFiles_copiesOnlyListedFiles() throws IOException {
		// Set up source knowledge directory
		Path knowledgeSource = tempDir.resolve("knowledge-src");
		Path mechanicsDir = knowledgeSource.resolve("coverage-mechanics");
		Files.createDirectories(mechanicsDir);
		Files.writeString(mechanicsDir.resolve("coverage-fundamentals.md"), "# Fundamentals");
		Files.writeString(mechanicsDir.resolve("jacoco-patterns.md"), "# JaCoCo");
		Files.writeString(mechanicsDir.resolve("spring-test-slices.md"), "# Slices");
		Files.writeString(mechanicsDir.resolve("common-gaps.md"), "# Gaps — should NOT be copied");

		Path workspace = tempDir.resolve("workspace");
		Files.createDirectories(workspace);

		List<String> files = List.of(
				"coverage-mechanics/coverage-fundamentals.md",
				"coverage-mechanics/jacoco-patterns.md",
				"coverage-mechanics/spring-test-slices.md");

		CodeCoverageAgentInvoker invoker = new CodeCoverageAgentInvoker(knowledgeSource, files);
		invoker.copyKnowledge(workspace);

		// Verify targeted files were copied
		Path targetKb = workspace.resolve("knowledge");
		assertThat(targetKb.resolve("coverage-mechanics/coverage-fundamentals.md")).hasContent("# Fundamentals");
		assertThat(targetKb.resolve("coverage-mechanics/jacoco-patterns.md")).hasContent("# JaCoCo");
		assertThat(targetKb.resolve("coverage-mechanics/spring-test-slices.md")).hasContent("# Slices");

		// Verify unlisted file was NOT copied
		assertThat(targetKb.resolve("coverage-mechanics/common-gaps.md")).doesNotExist();
	}

	@Test
	void indexMd_copiesEntireTree() throws IOException {
		// Set up source knowledge directory with nested structure
		Path knowledgeSource = tempDir.resolve("knowledge-src");
		Files.createDirectories(knowledgeSource.resolve("coverage-mechanics"));
		Files.createDirectories(knowledgeSource.resolve("spring-testing"));
		Files.writeString(knowledgeSource.resolve("index.md"), "# Knowledge Index");
		Files.writeString(knowledgeSource.resolve("coverage-mechanics/fundamentals.md"), "# Fund");
		Files.writeString(knowledgeSource.resolve("spring-testing/web-mvc.md"), "# WebMvc");

		Path workspace = tempDir.resolve("workspace");
		Files.createDirectories(workspace);

		CodeCoverageAgentInvoker invoker = new CodeCoverageAgentInvoker(
				knowledgeSource, List.of("index.md"));
		invoker.copyKnowledge(workspace);

		// Verify entire tree was copied
		Path targetKb = workspace.resolve("knowledge");
		assertThat(targetKb.resolve("index.md")).hasContent("# Knowledge Index");
		assertThat(targetKb.resolve("coverage-mechanics/fundamentals.md")).hasContent("# Fund");
		assertThat(targetKb.resolve("spring-testing/web-mvc.md")).hasContent("# WebMvc");
	}

	@Test
	void nullKnowledgeDir_copiesNothing() throws IOException {
		Path workspace = tempDir.resolve("workspace");
		Files.createDirectories(workspace);

		CodeCoverageAgentInvoker invoker = new CodeCoverageAgentInvoker(null, List.of("file.md"));
		invoker.copyKnowledge(workspace);

		assertThat(workspace.resolve("knowledge")).doesNotExist();
	}

	@Test
	void emptyKnowledgeFiles_copiesNothing() throws IOException {
		Path workspace = tempDir.resolve("workspace");
		Files.createDirectories(workspace);

		CodeCoverageAgentInvoker invoker = new CodeCoverageAgentInvoker(
				tempDir.resolve("kb"), List.of());
		invoker.copyKnowledge(workspace);

		assertThat(workspace.resolve("knowledge")).doesNotExist();
	}

	@Test
	void missingSourceFile_throwsException() throws IOException {
		Path knowledgeSource = tempDir.resolve("knowledge-src");
		Files.createDirectories(knowledgeSource);

		Path workspace = tempDir.resolve("workspace");
		Files.createDirectories(workspace);

		CodeCoverageAgentInvoker invoker = new CodeCoverageAgentInvoker(
				knowledgeSource, List.of("nonexistent.md"));

		assertThatThrownBy(() -> invoker.copyKnowledge(workspace))
				.isInstanceOf(java.io.UncheckedIOException.class)
				.hasMessageContaining("nonexistent.md");
	}

}
