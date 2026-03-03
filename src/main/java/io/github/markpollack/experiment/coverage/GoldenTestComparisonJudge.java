package io.github.markpollack.experiment.coverage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.judge.DeterministicJudge;
import org.springaicommunity.judge.context.JudgmentContext;
import org.springaicommunity.judge.result.Check;
import org.springaicommunity.judge.result.Judgment;
import org.springaicommunity.judge.result.JudgmentStatus;
import org.springaicommunity.judge.score.NumericalScore;

/**
 * Compares agent-written tests against reference golden tests using AST analysis.
 * Measures structural similarity across five dimensions: test method count,
 * annotation alignment, import alignment, assertion style, and injection patterns.
 */
public class GoldenTestComparisonJudge extends DeterministicJudge {

	private static final Logger logger = LoggerFactory.getLogger(GoldenTestComparisonJudge.class);

	static final double WEIGHT_TEST_METHOD_COVERAGE = 0.20;

	static final double WEIGHT_ANNOTATION_ALIGNMENT = 0.25;

	static final double WEIGHT_IMPORT_ALIGNMENT = 0.20;

	static final double WEIGHT_ASSERTION_STYLE = 0.20;

	static final double WEIGHT_INJECTION_PATTERN = 0.15;

	private static final Set<String> SPRING_TEST_ANNOTATIONS = Set.of("SpringBootTest", "WebMvcTest", "DataJpaTest",
			"WebFluxTest", "RestClientTest", "JsonTest", "JdbcTest", "AutoConfigureMockMvc",
			"AutoConfigureRestTestClient", "AutoConfigureWebTestClient", "MockBean", "SpyBean", "MockitoBean",
			"ExtendWith", "ContextConfiguration", "TestConfiguration");

	private static final Set<String> ASSERTION_METHODS = Set.of("assertThat", "assertEquals", "assertNotNull",
			"assertTrue", "assertFalse", "assertThrows", "verify", "isOk", "isCreated", "isBadRequest", "jsonPath",
			"content", "status", "expect", "andExpect");

	private static final Set<String> SPRING_IMPORT_PREFIXES = Set.of("org.springframework.", "org.junit.",
			"org.assertj.", "org.mockito.", "org.hamcrest.");

	public GoldenTestComparisonJudge() {
		super("GoldenTestComparisonJudge",
				"Compares agent-written tests against reference golden tests using AST analysis");
	}

	@Override
	public Judgment judge(JudgmentContext context) {
		Path workspace = context.workspace();
		Object expectedDirObj = context.metadata().get("expectedDir");

		if (!(expectedDirObj instanceof Path referenceDir) || !Files.isDirectory(referenceDir)) {
			return Judgment.abstain("No reference directory available");
		}

		try {
			List<Path> agentTests = findJavaTestFiles(workspace.resolve("src/test"));
			List<Path> referenceTests = findJavaTestFiles(referenceDir.resolve("src/test"));

			if (agentTests.isEmpty()) {
				return Judgment.builder()
					.score(NumericalScore.normalized(0.0))
					.status(JudgmentStatus.FAIL)
					.reasoning("No agent test files found")
					.build();
			}

			if (referenceTests.isEmpty()) {
				return Judgment.abstain("No reference test files found in " + referenceDir);
			}

			TestStructure agentStructure = extractStructure(agentTests);
			TestStructure referenceStructure = extractStructure(referenceTests);

			double testMethodScore = testMethodCoverage(agentStructure, referenceStructure);
			double annotationScore = jaccard(agentStructure.annotations, referenceStructure.annotations);
			double importScore = jaccard(agentStructure.springImports, referenceStructure.springImports);
			double assertionScore = assertionStyleScore(agentStructure, referenceStructure);
			double injectionScore = jaccard(agentStructure.injectedTypes, referenceStructure.injectedTypes);

			double composite = WEIGHT_TEST_METHOD_COVERAGE * testMethodScore
					+ WEIGHT_ANNOTATION_ALIGNMENT * annotationScore + WEIGHT_IMPORT_ALIGNMENT * importScore
					+ WEIGHT_ASSERTION_STYLE * assertionScore + WEIGHT_INJECTION_PATTERN * injectionScore;

			List<Check> checks = new ArrayList<>();
			checks.add(check("test_method_coverage", testMethodScore,
					"Agent: %d, Reference: %d".formatted(agentStructure.testMethodCount,
							referenceStructure.testMethodCount)));
			checks.add(check("annotation_alignment", annotationScore,
					"Agent: %s, Reference: %s".formatted(agentStructure.annotations, referenceStructure.annotations)));
			checks.add(check("import_alignment", importScore,
					"Agent: %d imports, Reference: %d imports".formatted(agentStructure.springImports.size(),
							referenceStructure.springImports.size())));
			checks.add(check("assertion_style", assertionScore,
					"Agent: %d assertions, Reference: %d assertions".formatted(agentStructure.assertionCount,
							referenceStructure.assertionCount)));
			checks.add(check("injection_pattern", injectionScore,
					"Agent: %s, Reference: %s".formatted(agentStructure.injectedTypes,
							referenceStructure.injectedTypes)));

			JudgmentStatus status = composite >= 0.3 ? JudgmentStatus.PASS : JudgmentStatus.FAIL;

			return Judgment.builder()
				.score(NumericalScore.normalized(composite))
				.status(status)
				.reasoning("Golden test comparison: composite=%.3f (%s)".formatted(composite,
						agentStructure.usedFallback || referenceStructure.usedFallback ? "with regex fallback"
								: "AST only"))
				.checks(checks)
				.metadata("agentTestFiles", agentTests.size())
				.metadata("referenceTestFiles", referenceTests.size())
				.metadata("usedFallback", agentStructure.usedFallback || referenceStructure.usedFallback)
				.build();
		}
		catch (IOException ex) {
			return Judgment.error("Failed to read test files: " + ex.getMessage(), ex);
		}
	}

	List<Path> findJavaTestFiles(Path testRoot) throws IOException {
		if (!Files.isDirectory(testRoot)) {
			return List.of();
		}
		try (Stream<Path> walk = Files.walk(testRoot)) {
			return walk.filter(p -> p.toString().endsWith(".java"))
				.filter(p -> p.getFileName().toString().contains("Test")
						|| p.getFileName().toString().contains("Tests"))
				.toList();
		}
	}

	TestStructure extractStructure(List<Path> testFiles) {
		TestStructure combined = new TestStructure();
		for (Path file : testFiles) {
			try {
				String source = Files.readString(file);
				try {
					CompilationUnit cu = StaticJavaParser.parse(source);
					extractFromAST(cu, combined);
				}
				catch (Exception parseEx) {
					logger.debug("JavaParser failed for {}, falling back to regex", file, parseEx);
					extractFromRegex(source, combined);
					combined.usedFallback = true;
				}
			}
			catch (IOException ex) {
				logger.warn("Could not read test file: {}", file, ex);
			}
		}
		return combined;
	}

	private void extractFromAST(CompilationUnit cu, TestStructure structure) {
		// Test methods
		cu.findAll(MethodDeclaration.class).stream().filter(m -> m.getAnnotationByName("Test").isPresent()).forEach(m -> {
			structure.testMethodCount++;
		});

		// Annotations (class-level + method-level)
		cu.findAll(AnnotationExpr.class).forEach(ann -> {
			String name = ann.getNameAsString();
			if (SPRING_TEST_ANNOTATIONS.contains(name)) {
				structure.annotations.add(name);
			}
		});

		// Imports
		cu.findAll(ImportDeclaration.class).forEach(imp -> {
			String importName = imp.getNameAsString();
			for (String prefix : SPRING_IMPORT_PREFIXES) {
				if (importName.startsWith(prefix)) {
					// Normalize: strip wildcard, use up to 3rd segment
					String normalized = normalizeImport(importName);
					structure.springImports.add(normalized);
					break;
				}
			}
		});

		// Assertion calls
		cu.findAll(MethodCallExpr.class).forEach(call -> {
			String name = call.getNameAsString();
			if (ASSERTION_METHODS.contains(name)) {
				structure.assertionCount++;
				structure.assertionStyles.add(name);
			}
		});

		// Injection patterns
		cu.findAll(FieldDeclaration.class).forEach(field -> {
			boolean injected = field.getAnnotationByName("Autowired").isPresent()
					|| field.getAnnotationByName("MockBean").isPresent()
					|| field.getAnnotationByName("MockitoBean").isPresent()
					|| field.getAnnotationByName("SpyBean").isPresent()
					|| field.getAnnotationByName("Mock").isPresent();
			if (injected) {
				field.getVariables().forEach(v -> {
					structure.injectedTypes.add(v.getTypeAsString());
				});
			}
		});
	}

	void extractFromRegex(String source, TestStructure structure) {
		// @Test methods
		Matcher testMatcher = Pattern.compile("@Test\\s").matcher(source);
		while (testMatcher.find()) {
			structure.testMethodCount++;
		}

		// Annotations
		Matcher annMatcher = Pattern.compile("@(\\w+)").matcher(source);
		while (annMatcher.find()) {
			String name = annMatcher.group(1);
			if (SPRING_TEST_ANNOTATIONS.contains(name)) {
				structure.annotations.add(name);
			}
		}

		// Spring/test imports
		Matcher importMatcher = Pattern.compile("import\\s+(static\\s+)?(\\S+);").matcher(source);
		while (importMatcher.find()) {
			String importName = importMatcher.group(2);
			for (String prefix : SPRING_IMPORT_PREFIXES) {
				if (importName.startsWith(prefix)) {
					structure.springImports.add(normalizeImport(importName));
					break;
				}
			}
		}

		// Assertion calls
		for (String method : ASSERTION_METHODS) {
			Matcher assertMatcher = Pattern.compile("\\b" + Pattern.quote(method) + "\\s*\\(").matcher(source);
			while (assertMatcher.find()) {
				structure.assertionCount++;
				structure.assertionStyles.add(method);
			}
		}

		// Injection
		Matcher injectionMatcher = Pattern
			.compile("@(?:Autowired|MockBean|MockitoBean|SpyBean|Mock)\\s+(?:private\\s+)?(?:final\\s+)?(\\w+)")
			.matcher(source);
		while (injectionMatcher.find()) {
			structure.injectedTypes.add(injectionMatcher.group(1));
		}

		structure.usedFallback = true;
	}

	private String normalizeImport(String importName) {
		// Normalize to package-level grouping (3 segments): org.springframework.boot → org.springframework.boot
		String[] parts = importName.split("\\.");
		if (parts.length >= 3) {
			return parts[0] + "." + parts[1] + "." + parts[2];
		}
		return importName;
	}

	private double testMethodCoverage(TestStructure agent, TestStructure reference) {
		if (reference.testMethodCount == 0) {
			return 1.0;
		}
		return Math.min((double) agent.testMethodCount / reference.testMethodCount, 1.0);
	}

	private double assertionStyleScore(TestStructure agent, TestStructure reference) {
		if (reference.assertionCount == 0) {
			return 1.0;
		}
		double countRatio = Math.min((double) agent.assertionCount / reference.assertionCount, 1.0);
		double styleOverlap = jaccard(agent.assertionStyles, reference.assertionStyles);
		return countRatio * styleOverlap;
	}

	static double jaccard(Set<String> a, Set<String> b) {
		if (a.isEmpty() && b.isEmpty()) {
			return 1.0;
		}
		Set<String> union = new HashSet<>(a);
		union.addAll(b);
		Set<String> intersection = new HashSet<>(a);
		intersection.retainAll(b);
		return (double) intersection.size() / union.size();
	}

	private Check check(String name, double score, String detail) {
		if (score >= 0.5) {
			return Check.pass(name, "%.2f — %s".formatted(score, detail));
		}
		return Check.fail(name, "%.2f — %s".formatted(score, detail));
	}

	static class TestStructure {

		int testMethodCount;

		Set<String> annotations = new HashSet<>();

		Set<String> springImports = new HashSet<>();

		int assertionCount;

		Set<String> assertionStyles = new HashSet<>();

		Set<String> injectedTypes = new HashSet<>();

		boolean usedFallback;

	}

}
