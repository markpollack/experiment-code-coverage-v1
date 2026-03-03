# gs-rest-service

## Scores by Variant

| Variant | Cov% | T3 | Eff | Eff:BE | Eff:Cost | Eff:RC | Cost | In Tok | Out Tok | Think |
|---------|------|-----|------|--------|----------|--------|------|--------|---------|-------|
| control | 100.0 | 0.67 | 0.869 | 0.875 | 0.851 | 0.875 | $0.75 | 18 | 9032 | 2226 |
| variant-a | 71.4 | 0.88 | 0.939 | 1.0 | 0.77 | 1.0 | $1.15 | 41 | 11208 | 1322 |
| variant-b | 100.0 | 0.85 | 0.76 | 0.75 | 0.786 | 0.75 | $1.07 | 32 | 11576 | 1945 |
| variant-c | 71.4 | 0.82 | 0.963 | 1.0 | 0.86 | 1.0 | $0.7 | 18 | 10355 | 1823 |

## T3 Practice Adherence — Criterion Details

### control

| Criterion | Score | Evidence |
|-----------|-------|----------|
| assertion_quality | 0.50 | 0.50 — GreetingControllerTest.java greetingWithDefaultName and greetingWithCustomName use jsonPath with specific expecte |
| command_execution | — | Command executed successfully |
| coverage_improved | — | +100.0 pp (0.0% → 100.0%) |
| coverage_target_selection | 0.50 | 0.50 — GreetingControllerTest.java targets meaningful controller behavior — good. However, GreetingTest.java explicitly  |
| domain_specific_test_patterns | 0.80 | 0.80 — MVC domain: GreetingControllerTest.java correctly uses MockMvc with jsonPath assertions and scopes @WebMvcTest to |
| error_and_edge_case_coverage | 0.20 | 0.20 — GreetingControllerTest.java covers only happy-path GET requests. No tests for missing or empty name parameter val |
| line_coverage_preserved | — | Drop -100.0% <= 5.0% threshold |
| test_slice_selection | 1.00 | 1.00 — GreetingControllerTest.java uses @WebMvcTest(GreetingController.class) — correctly scoped to the specific control |
| version_aware_patterns | 1.00 | 1.00 — GreetingControllerTest.java imports org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest — the correct B |

### variant-a

| Criterion | Score | Evidence |
|-----------|-------|----------|
| assertion_quality | 0.80 | 0.80 — GreetingControllerTest.java uses AssertJ fluent assertions throughout: bodyJson().extractingPath("$.content").asS |
| command_execution | — | Command executed successfully |
| coverage_improved | — | +71.4 pp (0.0% → 71.4%) |
| coverage_target_selection | 1.00 | 1.00 — GreetingControllerTest.java tests only the controller endpoint behavior (content values, ID increment, default pa |
| domain_specific_test_patterns | 1.00 | 1.00 — MVC domain: GreetingControllerTest.java uses MockMvcTester (Boot 4.x MVC test API) with bodyJson().extractingPath |
| error_and_edge_case_coverage | 0.50 | 0.50 — GreetingControllerTest.java covers one edge case — greetingWithEmptyNameFallsBackToDefault() tests the empty-stri |
| line_coverage_preserved | — | Drop -71.4% <= 5.0% threshold |
| test_slice_selection | 1.00 | 1.00 — GreetingControllerTest.java uses @WebMvcTest(GreetingController.class) — scoped to the specific controller, the n |
| version_aware_patterns | 1.00 | 1.00 — GreetingControllerTest.java imports org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest — the Boot 4.x  |

### variant-b

| Criterion | Score | Evidence |
|-----------|-------|----------|
| assertion_quality | 0.80 | 0.80 — shouldReturnDefaultGreeting and shouldReturnGreetingWithCustomName use bodyJson().extractingPath("$.content").isE |
| command_execution | — | Command executed successfully |
| coverage_improved | — | +100.0 pp (0.0% → 100.0%) |
| coverage_target_selection | 1.00 | 1.00 — All four tests target behavioral logic in GreetingController (content formatting, counter increment). Greeting.ja |
| domain_specific_test_patterns | 0.80 | 0.80 — MVC domain: uses MockMvcTester (Boot 4.x AssertJ-based API) with bodyJson().extractingPath() for JSON assertions; |
| error_and_edge_case_coverage | 0.50 | 0.50 — Only happy-path tests are present (default name, custom name, id presence, counter increment). Production code ha |
| line_coverage_preserved | — | Drop -100.0% <= 5.0% threshold |
| test_slice_selection | 1.00 | 1.00 — GreetingControllerTest.java uses @WebMvcTest(GreetingController.class) — scoped to the specific controller, no @S |
| version_aware_patterns | 1.00 | 1.00 — Boot 4.x project: import uses org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest (Boot 4.x path, not t |

### variant-c

| Criterion | Score | Evidence |
|-----------|-------|----------|
| assertion_quality | 0.80 | 0.80 — GreetingControllerTest.java uses AssertJ fluent assertions with MockMvcTester, hasStatusOk(), bodyJson().extracti |
| command_execution | — | Command executed successfully |
| coverage_improved | — | +71.4 pp (0.0% → 71.4%) |
| coverage_target_selection | 0.80 | 0.80 — GreetingControllerTest.java tests the controller endpoint and its behavioral logic (default name, custom name, id |
| domain_specific_test_patterns | 0.80 | 0.80 — MVC domain: MockMvcTester (Boot 4.x equivalent) used correctly with @WebMvcTest(GreetingController.class); bodyJs |
| error_and_edge_case_coverage | 0.50 | 0.50 — GreetingControllerTest.java tests the default-name and custom-name happy paths plus id increment behavior. No tes |
| line_coverage_preserved | — | Drop -71.4% <= 5.0% threshold |
| test_slice_selection | 1.00 | 1.00 — GreetingControllerTest.java uses @WebMvcTest(GreetingController.class) — scoped to the specific controller, no @S |
| version_aware_patterns | 1.00 | 1.00 — GreetingControllerTest.java uses org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest (Boot 4.x import p |
