package io.github.markpollack.experiment.coverage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.judge.context.ExecutionStatus;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;
import org.springaicommunity.judge.score.NumericalScore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GoldenTestComparisonJudgeTest {

	@TempDir
	Path tempDir;

	private Path workspace;

	private Path referenceDir;

	private GoldenTestComparisonJudge judge;

	@BeforeEach
	void setUp() throws IOException {
		workspace = tempDir.resolve("workspace");
		referenceDir = tempDir.resolve("reference");
		judge = new GoldenTestComparisonJudge();
	}

	private JudgmentContext contextWith(Path workspace, Path referenceDir) {
		return JudgmentContext.builder()
			.goal("Improve test coverage")
			.workspace(workspace)
			.executionTime(Duration.ofSeconds(1))
			.startedAt(Instant.now())
			.status(ExecutionStatus.SUCCESS)
			.metadata(referenceDir != null ? Map.of("expectedDir", referenceDir) : Map.of())
			.build();
	}

	private void writeTestFile(Path baseDir, String relativePath, String content) throws IOException {
		Path file = baseDir.resolve(relativePath);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}

	// --- Reference test fixture ---

	private static final String REFERENCE_TEST = """
			package com.example;

			import org.junit.jupiter.api.Test;
			import org.springframework.beans.factory.annotation.Autowired;
			import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
			import org.springframework.boot.test.context.SpringBootTest;
			import org.springframework.test.web.servlet.MockMvc;

			import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
			import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
			import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

			@SpringBootTest
			@AutoConfigureMockMvc
			class GreetingControllerTest {

			    @Autowired
			    MockMvc mockMvc;

			    @Test
			    void shouldReturnDefaultGreeting() throws Exception {
			        mockMvc.perform(get("/greeting"))
			            .andExpect(status().isOk())
			            .andExpect(jsonPath("$.content").value("Hello, World!"));
			    }

			    @Test
			    void shouldReturnCustomGreeting() throws Exception {
			        mockMvc.perform(get("/greeting").param("name", "Spring"))
			            .andExpect(status().isOk())
			            .andExpect(jsonPath("$.content").value("Hello, Spring!"));
			    }
			}
			""";

	@Test
	void matchingStructure_highScore() throws IOException {
		// Agent wrote very similar test to reference
		String agentTest = """
				package com.example;

				import org.junit.jupiter.api.Test;
				import org.springframework.beans.factory.annotation.Autowired;
				import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
				import org.springframework.boot.test.context.SpringBootTest;
				import org.springframework.test.web.servlet.MockMvc;

				import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
				import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
				import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

				@SpringBootTest
				@AutoConfigureMockMvc
				class GreetingControllerTests {

				    @Autowired
				    MockMvc mockMvc;

				    @Test
				    void greetingDefault() throws Exception {
				        mockMvc.perform(get("/greeting"))
				            .andExpect(status().isOk())
				            .andExpect(jsonPath("$.content").value("Hello, World!"));
				    }

				    @Test
				    void greetingWithName() throws Exception {
				        mockMvc.perform(get("/greeting").param("name", "Test"))
				            .andExpect(status().isOk())
				            .andExpect(jsonPath("$.content").value("Hello, Test!"));
				    }
				}
				""";

		writeTestFile(referenceDir, "src/test/java/com/example/GreetingControllerTest.java", REFERENCE_TEST);
		writeTestFile(workspace, "src/test/java/com/example/GreetingControllerTests.java", agentTest);

		Judgment result = judge.judge(contextWith(workspace, referenceDir));

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		double score = ((NumericalScore) result.score()).value();
		assertThat(score).isGreaterThan(0.8); // High similarity
		assertThat(result.checks()).hasSize(5);
	}

	@Test
	void differentApproach_partialScore() throws IOException {
		// Agent uses @WebMvcTest instead of @SpringBootTest — same tests, different annotations
		String agentTest = """
				package com.example;

				import org.junit.jupiter.api.Test;
				import org.springframework.beans.factory.annotation.Autowired;
				import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
				import org.springframework.test.web.servlet.MockMvc;

				import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
				import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

				@WebMvcTest
				class GreetingControllerTests {

				    @Autowired
				    MockMvc mockMvc;

				    @Test
				    void greetingWorks() throws Exception {
				        mockMvc.perform(get("/greeting"))
				            .andExpect(status().isOk());
				    }
				}
				""";

		writeTestFile(referenceDir, "src/test/java/com/example/GreetingControllerTest.java", REFERENCE_TEST);
		writeTestFile(workspace, "src/test/java/com/example/GreetingControllerTests.java", agentTest);

		Judgment result = judge.judge(contextWith(workspace, referenceDir));

		double score = ((NumericalScore) result.score()).value();
		// Partial: fewer tests (1 vs 2), different annotations (WebMvcTest vs SpringBootTest+AutoConfigureMockMvc),
		// fewer assertions
		assertThat(score).isBetween(0.15, 0.7);
		assertThat(result.checks()).hasSize(5);
	}

	@Test
	void noAgentTests_failWithZero() throws IOException {
		writeTestFile(referenceDir, "src/test/java/com/example/GreetingControllerTest.java", REFERENCE_TEST);
		// Create workspace with no test files
		Files.createDirectories(workspace.resolve("src/main/java"));

		Judgment result = judge.judge(contextWith(workspace, referenceDir));

		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
		double score = ((NumericalScore) result.score()).value();
		assertThat(score).isEqualTo(0.0);
		assertThat(result.reasoning()).contains("No agent test files");
	}

	@Test
	void noReferenceDir_abstain() {
		Judgment result = judge.judge(contextWith(workspace, null));

		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
		assertThat(result.reasoning()).contains("No reference directory");
	}

	@Test
	void missingReferenceDir_abstain() {
		Path nonExistent = tempDir.resolve("does-not-exist");
		Judgment result = judge.judge(contextWith(workspace, nonExistent));

		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
	}

	@Test
	void unparseableAgentFile_regexFallback() throws IOException {
		// Deliberately broken Java that JavaParser can't parse
		String brokenJava = """
				package com.example;

				import org.junit.jupiter.api.Test;
				import org.springframework.boot.test.context.SpringBootTest;

				@SpringBootTest
				class BrokenTest {
				    @Test void foo( {  // broken syntax
				        assertThat(true).isTrue();
				    }
				}
				""";

		writeTestFile(referenceDir, "src/test/java/com/example/GreetingControllerTest.java", REFERENCE_TEST);
		writeTestFile(workspace, "src/test/java/com/example/BrokenTest.java", brokenJava);

		Judgment result = judge.judge(contextWith(workspace, referenceDir));

		// Should still produce a score via regex fallback
		assertThat(result.status()).isIn(JudgmentStatus.PASS, JudgmentStatus.FAIL);
		double score = ((NumericalScore) result.score()).value();
		assertThat(score).isGreaterThan(0.0);
		assertThat(result.metadata().get("usedFallback")).isEqualTo(true);
	}

	@Test
	void compositeCalculation_knownInputs() throws IOException {
		// Agent has exactly 1 of 2 test methods, matching annotations, matching imports,
		// matching assertions, matching injection
		writeTestFile(referenceDir, "src/test/java/com/example/GreetingControllerTest.java", REFERENCE_TEST);
		writeTestFile(workspace, "src/test/java/com/example/GreetingControllerTests.java", REFERENCE_TEST);

		Judgment result = judge.judge(contextWith(workspace, referenceDir));

		double score = ((NumericalScore) result.score()).value();
		// Identical files → all dimensions should be 1.0 → composite = 1.0
		assertThat(score).isCloseTo(1.0, within(0.01));
	}

	@Test
	void jaccardSimilarity_emptyBothSets_returnsOne() {
		assertThat(GoldenTestComparisonJudge.jaccard(Set.of(), Set.of())).isEqualTo(1.0);
	}

	@Test
	void jaccardSimilarity_disjointSets_returnsZero() {
		assertThat(GoldenTestComparisonJudge.jaccard(Set.of("a", "b"), Set.of("c", "d"))).isEqualTo(0.0);
	}

	@Test
	void jaccardSimilarity_identicalSets_returnsOne() {
		assertThat(GoldenTestComparisonJudge.jaccard(Set.of("a", "b"), Set.of("a", "b"))).isEqualTo(1.0);
	}

	@Test
	void jaccardSimilarity_partialOverlap() {
		// {a, b, c} ∩ {b, c, d} = {b, c}, union = {a, b, c, d} → 2/4 = 0.5
		assertThat(GoldenTestComparisonJudge.jaccard(Set.of("a", "b", "c"), Set.of("b", "c", "d")))
			.isCloseTo(0.5, within(0.01));
	}

	@Test
	void agentWritesMoreTests_cappedAtOne() throws IOException {
		// Reference has 2 tests, agent has 4 — test_method_coverage should cap at 1.0
		String agentTest = """
				package com.example;

				import org.junit.jupiter.api.Test;
				import org.springframework.beans.factory.annotation.Autowired;
				import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
				import org.springframework.boot.test.context.SpringBootTest;
				import org.springframework.test.web.servlet.MockMvc;

				import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
				import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
				import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

				@SpringBootTest
				@AutoConfigureMockMvc
				class GreetingControllerTests {

				    @Autowired
				    MockMvc mockMvc;

				    @Test void test1() throws Exception {
				        mockMvc.perform(get("/greeting")).andExpect(status().isOk());
				    }
				    @Test void test2() throws Exception {
				        mockMvc.perform(get("/greeting")).andExpect(jsonPath("$.content").value("Hello, World!"));
				    }
				    @Test void test3() throws Exception {
				        mockMvc.perform(get("/greeting").param("name", "A")).andExpect(status().isOk());
				    }
				    @Test void test4() throws Exception {
				        mockMvc.perform(get("/greeting").param("name", "B")).andExpect(status().isOk());
				    }
				}
				""";

		writeTestFile(referenceDir, "src/test/java/com/example/GreetingControllerTest.java", REFERENCE_TEST);
		writeTestFile(workspace, "src/test/java/com/example/GreetingControllerTests.java", agentTest);

		Judgment result = judge.judge(contextWith(workspace, referenceDir));

		// test_method_coverage should be capped at 1.0 (4/2 → 1.0)
		assertThat(result.checks().stream().filter(c -> c.name().equals("test_method_coverage")).findFirst())
			.isPresent()
			.get()
			.satisfies(c -> assertThat(c.passed()).isTrue());

		double score = ((NumericalScore) result.score()).value();
		assertThat(score).isGreaterThan(0.7); // High similarity overall
	}

	@Test
	void noReferenceTests_abstain() throws IOException {
		// Reference dir exists but has no test files
		Files.createDirectories(referenceDir.resolve("src/test/java"));
		writeTestFile(workspace, "src/test/java/com/example/SomeTest.java", """
				package com.example;
				import org.junit.jupiter.api.Test;
				class SomeTest {
				    @Test void foo() {}
				}
				""");

		Judgment result = judge.judge(contextWith(workspace, referenceDir));

		assertThat(result.status()).isEqualTo(JudgmentStatus.ABSTAIN);
	}

}
