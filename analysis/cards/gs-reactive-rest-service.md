# gs-reactive-rest-service

## Scores by Variant

| Variant | Cov% | T3 | Eff | Eff:BE | Eff:Cost | Eff:RC | Cost | In Tok | Out Tok | Think |
|---------|------|-----|------|--------|----------|--------|------|--------|---------|-------|
| control | 100.0 | 0.5 | 0.837 | 0.875 | 0.734 | 0.875 | $1.33 | 31 | 18472 | 3243 |
| variant-a | 78.9 | 0.93 | 0.871 | 0.875 | 0.859 | 0.875 | $0.71 | 22 | 8618 | 998 |
| variant-b | 100.0 | 0.7 | 0.855 | 0.875 | 0.799 | 0.875 | $1.0 | 26 | 13465 | 2660 |
| variant-c | 100.0 | 0.85 | 0.742 | 0.75 | 0.722 | 0.75 | $1.39 | 42 | 13869 | 2174 |

## T3 Practice Adherence — Criterion Details

### control

| Criterion | Score | Evidence |
|-----------|-------|----------|
| assertion_quality | 0.50 | 0.50 — GreetingRouterTest.java uses WebTestClient with jsonPath ($.message) and typed-body assertions — good; GreetingCl |
| command_execution | — | Command executed successfully |
| coverage_improved | — | +100.0 pp (0.0% → 100.0%) |
| coverage_target_selection | 0.20 | 0.20 — GreetingTest.java consists entirely of 5 tests exercising the Greeting Java record — testGreetingMessage, testGre |
| domain_specific_test_patterns | 0.80 | 0.80 — WebFlux domain: GreetingClientTest.java uses StepVerifier without .block() (correct); GreetingHandlerTest.java us |
| error_and_edge_case_coverage | 0.20 | 0.20 — All tests target the single happy-path GET /hello 200 OK scenario; no test covers a request to an undefined route |
| line_coverage_preserved | — | Drop -100.0% <= 5.0% threshold |
| test_slice_selection | 0.50 | 0.50 — GreetingRouterTest.java uses @SpringBootTest(RANDOM_PORT) instead of @WebFluxTest, which would be the narrower sl |
| version_aware_patterns | 0.80 | 0.80 — GreetingRouterTest.java imports org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient — |

### variant-a

| Criterion | Score | Evidence |
|-----------|-------|----------|
| assertion_quality | 0.90 | 0.90 — GreetingRouterTest.java asserts specific domain value (greeting.message().isEqualTo("Hello, Spring!")) and uses j |
| command_execution | — | Command executed successfully |
| coverage_improved | — | +78.9 pp (0.0% → 78.9%) |
| coverage_target_selection | 1.00 | 1.00 — Greeting.java is a record — no test file exists for it (correctly skipped). ReactiveWebServiceApplication.main()  |
| domain_specific_test_patterns | 1.00 | 1.00 — WebFlux domain only (no JPA, security, or WebSocket present). GreetingRouterTest.java uses WebTestClient (not .bl |
| error_and_edge_case_coverage | 0.70 | 0.70 — GreetingRouterTest.java covers two error paths: wrong Accept type → 4xxClientError, unknown path → 404. GreetingH |
| line_coverage_preserved | — | Drop -78.9% <= 5.0% threshold |
| test_slice_selection | 1.00 | 1.00 — GreetingRouterTest.java uses @WebFluxTest (org.springframework.boot.webflux.test.autoconfigure.WebFluxTest) with  |
| version_aware_patterns | 1.00 | 1.00 — Boot version is 4.0.2. GreetingRouterTest.java imports org.springframework.boot.webflux.test.autoconfigure.WebFlu |

### variant-b

| Criterion | Score | Evidence |
|-----------|-------|----------|
| assertion_quality | 0.80 | 0.80 — GreetingRouterTest.java uses WebTestClient fluent assertions with expectStatus().isOk(), expectHeader().contentTy |
| command_execution | — | Command executed successfully |
| coverage_improved | — | +100.0 pp (0.0% → 100.0%) |
| coverage_target_selection | 0.80 | 0.80 — GreetingHandler, GreetingRouter, and GreetingClient are all tested with behavioral assertions; Greeting.java is a |
| domain_specific_test_patterns | 0.80 | 0.80 — WebFlux domain: GreetingRouterTest.java uses WebTestClient (not MockMvc) correctly; GreetingHandlerTest.java uses |
| error_and_edge_case_coverage | 0.50 | 0.50 — GreetingRouterTest.java includes unknownPathReturnsNotFound() testing a 404 path, which is good; no tests for inv |
| line_coverage_preserved | — | Drop -100.0% <= 5.0% threshold |
| test_slice_selection | 0.50 | 0.50 — GreetingRouterTest.java uses @SpringBootTest(webEnvironment=RANDOM_PORT) with bindToServer(), spinning up the ful |
| version_aware_patterns | 0.80 | 0.80 — Boot 4.x project; no deprecated @MockBean or @SpyBean annotations appear anywhere in the test suite — Mockito.moc |

### variant-c

| Criterion | Score | Evidence |
|-----------|-------|----------|
| assertion_quality | 0.80 | 0.80 — GreetingRouterTest uses WebTestClient with expectStatus().isOk(), expectHeader().contentType(MediaType.APPLICATIO |
| command_execution | — | Command executed successfully |
| coverage_improved | — | +100.0 pp (0.0% → 100.0%) |
| coverage_target_selection | 0.80 | 0.80 — No test for the Greeting record (constructor/accessors/equals) — correctly skipped. No test for ReactiveWebServic |
| domain_specific_test_patterns | 1.00 | 1.00 — Reactive domain only (no JPA, no security). GreetingHandlerTest uses StepVerifier.create(responseMono).assertNext |
| error_and_edge_case_coverage | 0.50 | 0.50 — GreetingRouterTest.unknownRouteReturnsNotFound() tests the 404 path. No tests cover wrong Accept header (e.g., te |
| line_coverage_preserved | — | Drop -100.0% <= 5.0% threshold |
| test_slice_selection | 1.00 | 1.00 — GreetingHandlerTest uses plain JUnit+no Spring context (appropriate for a standalone @Component handler); Greetin |
| version_aware_patterns | 1.00 | 1.00 — Spring Boot 4.0.2 project. GreetingRouterTest imports org.springframework.boot.webflux.test.autoconfigure.WebFlux |
