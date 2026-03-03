package io.github.markpollack.experiment.coverage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.agents.client.AgentClient;
import org.springaicommunity.agents.client.AgentClient.AgentClientRequestSpec;
import org.springaicommunity.agents.client.AgentClientResponse;
import org.springaicommunity.agents.model.AgentGeneration;
import org.springaicommunity.agents.model.AgentResponse;
import org.springaicommunity.judge.context.ExecutionStatus;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;
import org.springaicommunity.judge.score.NumericalScore;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

class TestQualityJudgeTest {

	@TempDir
	Path tempDir;

	private Path judgePromptPath;

	private Path workspace;

	@BeforeEach
	void setUp() throws IOException {
		judgePromptPath = tempDir.resolve("judge-prompt.txt");
		Files.writeString(judgePromptPath, "Evaluate the test suite.");

		workspace = tempDir.resolve("workspace");
		Path testDir = workspace.resolve("src/test/java/com/example");
		Files.createDirectories(testDir);
		Files.writeString(testDir.resolve("SomeTest.java"), "class SomeTest {}");
	}

	private JudgmentContext buildContext() {
		return JudgmentContext.builder()
			.goal("Improve test coverage")
			.workspace(workspace)
			.executionTime(Duration.ofMinutes(5))
			.startedAt(Instant.now())
			.status(ExecutionStatus.SUCCESS)
			.build();
	}

	private Function<Path, AgentClient> mockAgentFactory(String agentOutput) {
		AgentClient mockClient = mock(AgentClient.class);
		AgentClientRequestSpec mockSpec = mock(AgentClientRequestSpec.class);

		AgentGeneration generation = new AgentGeneration(agentOutput);
		AgentResponse agentResponse = new AgentResponse(List.of(generation));
		AgentClientResponse response = new AgentClientResponse(agentResponse);

		given(mockClient.goal(anyString())).willReturn(mockSpec);
		given(mockSpec.workingDirectory(any(Path.class))).willReturn(mockSpec);
		given(mockSpec.run()).willReturn(response);

		return path -> mockClient;
	}

	@Test
	void validJsonResponse_returnsCorrectScores() {
		String agentOutput = """
				{
				  "boot_version": "4.x",
				  "criteria": [
				    { "name": "test_slice_selection", "score": 0.8, "evidence": "Uses @WebMvcTest correctly" },
				    { "name": "assertion_quality", "score": 0.6, "evidence": "AssertJ used but shallow" },
				    { "name": "error_and_edge_case_coverage", "score": 0.4, "evidence": "Only happy paths" },
				    { "name": "domain_specific_test_patterns", "score": 0.7, "evidence": "Good MockMvc usage" },
				    { "name": "coverage_target_selection", "score": 0.9, "evidence": "No vanity tests" },
				    { "name": "version_aware_patterns", "score": 0.8, "evidence": "Uses @MockitoBean" }
				  ]
				}
				""";

		TestQualityJudge judge = new TestQualityJudge(mockAgentFactory(agentOutput), judgePromptPath);
		Judgment result = judge.judge(buildContext());

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.checks()).hasSize(6);
		assertThat(result.metadata().get("boot_version")).isEqualTo("4.x");
		assertThat(result.metadata().get("criteria_count")).isEqualTo(6);

		// Average: (0.8 + 0.6 + 0.4 + 0.7 + 0.9 + 0.8) / 6 = 0.7
		double avgScore = ((NumericalScore) result.score()).value();
		assertThat(avgScore).isCloseTo(0.7, within(0.01));
	}

	@Test
	void lowScores_returnsFail() {
		String agentOutput = """
				{
				  "boot_version": "3.x",
				  "criteria": [
				    { "name": "test_slice_selection", "score": 0.2, "evidence": "All @SpringBootTest" },
				    { "name": "assertion_quality", "score": 0.3, "evidence": "Trivial assertions" }
				  ]
				}
				""";

		TestQualityJudge judge = new TestQualityJudge(mockAgentFactory(agentOutput), judgePromptPath);
		Judgment result = judge.judge(buildContext());

		// Average: 0.25 < 0.5 threshold
		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void noTestFiles_returnsFailWithZeroScore() throws IOException {
		Path emptyWorkspace = tempDir.resolve("empty-workspace");
		Files.createDirectories(emptyWorkspace.resolve("src/main/java"));

		JudgmentContext context = JudgmentContext.builder()
			.goal("test")
			.workspace(emptyWorkspace)
			.executionTime(Duration.ZERO)
			.startedAt(Instant.now())
			.status(ExecutionStatus.SUCCESS)
			.build();

		TestQualityJudge judge = new TestQualityJudge(mockAgentFactory("unused"), judgePromptPath);
		Judgment result = judge.judge(context);

		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
		assertThat(result.reasoning()).contains("No test classes");
		double score = ((NumericalScore) result.score()).value();
		assertThat(score).isEqualTo(0.0);
	}

	@Test
	void malformedOutput_returnsError() {
		String agentOutput = "I couldn't parse the project properly, here are some thoughts...";

		TestQualityJudge judge = new TestQualityJudge(mockAgentFactory(agentOutput), judgePromptPath);
		Judgment result = judge.judge(buildContext());

		assertThat(result.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(result.reasoning()).contains("No JSON found");
	}

	@Test
	void jsonMissingCriteria_returnsError() {
		String agentOutput = """
				{ "boot_version": "4.x" }
				""";

		TestQualityJudge judge = new TestQualityJudge(mockAgentFactory(agentOutput), judgePromptPath);
		Judgment result = judge.judge(buildContext());

		assertThat(result.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(result.reasoning()).contains("missing 'criteria'");
	}

	@Test
	void emptyCriteriaArray_returnsError() {
		String agentOutput = """
				{ "boot_version": "4.x", "criteria": [] }
				""";

		TestQualityJudge judge = new TestQualityJudge(mockAgentFactory(agentOutput), judgePromptPath);
		Judgment result = judge.judge(buildContext());

		assertThat(result.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(result.reasoning()).contains("empty criteria");
	}

	@Test
	void outOfRangeScores_areClamped() {
		String agentOutput = """
				{
				  "boot_version": "4.x",
				  "criteria": [
				    { "name": "over", "score": 1.5, "evidence": "too high" },
				    { "name": "under", "score": -0.3, "evidence": "too low" }
				  ]
				}
				""";

		TestQualityJudge judge = new TestQualityJudge(mockAgentFactory(agentOutput), judgePromptPath);
		Judgment result = judge.judge(buildContext());

		// Clamped: 1.0 + 0.0 = 1.0, avg = 0.5
		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS); // 0.5 >= 0.5 threshold
		double score = ((NumericalScore) result.score()).value();
		assertThat(score).isCloseTo(0.5, within(0.01));
	}

	@Test
	void agentException_returnsError() {
		Function<Path, AgentClient> failingFactory = path -> {
			AgentClient mockClient = mock(AgentClient.class);
			AgentClientRequestSpec mockSpec = mock(AgentClientRequestSpec.class);
			given(mockClient.goal(anyString())).willReturn(mockSpec);
			given(mockSpec.workingDirectory(any(Path.class))).willReturn(mockSpec);
			given(mockSpec.run()).willThrow(new RuntimeException("Agent crashed"));
			return mockClient;
		};

		TestQualityJudge judge = new TestQualityJudge(failingFactory, judgePromptPath);
		Judgment result = judge.judge(buildContext());

		assertThat(result.status()).isEqualTo(JudgmentStatus.ERROR);
		assertThat(result.reasoning()).contains("Judge agent execution failed");
	}

	@Test
	void jsonEmbeddedInText_extractsCorrectly() {
		String agentOutput = """
				Here is my evaluation of the test suite:

				```json
				{
				  "boot_version": "4.x",
				  "criteria": [
				    { "name": "test_slice_selection", "score": 0.7, "evidence": "Mostly good" }
				  ]
				}
				```

				That concludes the evaluation.
				""";

		TestQualityJudge judge = new TestQualityJudge(mockAgentFactory(agentOutput), judgePromptPath);
		Judgment result = judge.judge(buildContext());

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS); // 0.7 >= 0.5
		assertThat(result.checks()).hasSize(1);
	}

	@Test
	void customPassThreshold_respected() {
		String agentOutput = """
				{
				  "boot_version": "4.x",
				  "criteria": [
				    { "name": "c1", "score": 0.6, "evidence": "ok" }
				  ]
				}
				""";

		// With 0.7 threshold, 0.6 should fail
		TestQualityJudge judge = new TestQualityJudge(mockAgentFactory(agentOutput), judgePromptPath, 0.7);
		Judgment result = judge.judge(buildContext());

		assertThat(result.status()).isEqualTo(JudgmentStatus.FAIL);
	}

	@Test
	void parseJudgment_directlyTestable() {
		String json = """
				{
				  "boot_version": "3.x",
				  "criteria": [
				    { "name": "c1", "score": 0.8, "evidence": "good" }
				  ]
				}
				""";

		TestQualityJudge judge = new TestQualityJudge(mockAgentFactory("unused"), judgePromptPath);
		Judgment result = judge.parseJudgment(json);

		assertThat(result.status()).isEqualTo(JudgmentStatus.PASS);
		assertThat(result.checks()).hasSize(1);
		assertThat(result.checks().get(0).name()).isEqualTo("c1");
	}

}
