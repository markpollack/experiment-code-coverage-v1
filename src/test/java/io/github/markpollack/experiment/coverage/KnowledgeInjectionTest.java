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
 * Tests for knowledge file copying and JaCoCo injection in {@link CodeCoverageAgentInvoker}.
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

	// ==================== JaCoCo injection tests ====================

	@Test
	void ensureJaCoCo_injectsPluginWhenMissing() throws IOException {
		Path workspace = tempDir.resolve("workspace");
		Files.createDirectories(workspace);
		Files.writeString(workspace.resolve("pom.xml"), """
				<project>
				  <build>
				    <plugins>
				      <plugin>
				        <groupId>org.springframework.boot</groupId>
				        <artifactId>spring-boot-maven-plugin</artifactId>
				      </plugin>
				    </plugins>
				  </build>
				</project>
				""");

		CodeCoverageAgentInvoker invoker = new CodeCoverageAgentInvoker();
		invoker.ensureJaCoCoPlugin(workspace);

		String pom = Files.readString(workspace.resolve("pom.xml"));
		assertThat(pom).contains("jacoco-maven-plugin");
		assertThat(pom).contains("prepare-agent");
		assertThat(pom).contains("<phase>test</phase>");
	}

	@Test
	void ensureJaCoCo_skipsWhenAlreadyPresent() throws IOException {
		Path workspace = tempDir.resolve("workspace");
		Files.createDirectories(workspace);
		String originalPom = """
				<project>
				  <build>
				    <plugins>
				      <plugin>
				        <groupId>org.jacoco</groupId>
				        <artifactId>jacoco-maven-plugin</artifactId>
				      </plugin>
				    </plugins>
				  </build>
				</project>
				""";
		Files.writeString(workspace.resolve("pom.xml"), originalPom);

		CodeCoverageAgentInvoker invoker = new CodeCoverageAgentInvoker();
		invoker.ensureJaCoCoPlugin(workspace);

		assertThat(Files.readString(workspace.resolve("pom.xml"))).isEqualTo(originalPom);
	}

	@Test
	void ensureJaCoCo_noPom_doesNotThrow() {
		Path workspace = tempDir.resolve("no-pom-workspace");

		CodeCoverageAgentInvoker invoker = new CodeCoverageAgentInvoker();
		invoker.ensureJaCoCoPlugin(workspace);
		// No exception — just logs a warning
	}

	@Test
	void ensureJaCoCo_noBuildSection_addsBuildAndPlugins() throws IOException {
		Path workspace = tempDir.resolve("workspace");
		Files.createDirectories(workspace);
		Files.writeString(workspace.resolve("pom.xml"), """
				<project>
				  <dependencies/>
				</project>
				""");

		CodeCoverageAgentInvoker invoker = new CodeCoverageAgentInvoker();
		invoker.ensureJaCoCoPlugin(workspace);

		String pom = Files.readString(workspace.resolve("pom.xml"));
		assertThat(pom).contains("jacoco-maven-plugin");
		assertThat(pom).contains("<build>");
		assertThat(pom).contains("<plugins>");
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
