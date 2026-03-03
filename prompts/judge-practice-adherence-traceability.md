# Judge Practice Adherence — Traceability

> **Refined 2026-03-02**: Added zero-tests escape hatch, N/A guidance for criterion 4, multi-file evidence format.

Maps each criterion in `prompts/judge-practice-adherence.txt` back to its source KB file(s).

## 1. TEST SLICE SELECTION

| Source KB File | What it contributes |
|---|---|
| `coverage-mechanics/spring-test-slices.md` | Decision tree: @WebMvcTest vs @DataJpaTest vs @WebFluxTest vs plain JUnit vs @SpringBootTest. Annotation quick reference table with scope and speed. |
| `spring-testing/cross-cutting-testing-patterns.md` | Testing pyramid — slice preference order (§Testing Pyramid). "Use the narrowest slice" rule. Context caching pitfalls from non-standardized mock sets. |
| `spring-testing/mvc-rest-testing-patterns.md` | Anti-pattern: "Using @SpringBootTest for every controller test — use @WebMvcTest, 10x faster." Scoping: @WebMvcTest(controllers=...). |
| `spring-testing/jpa-testing-cheatsheet.md` | Anti-pattern: "Loading @SpringBootTest for repository tests — use @DataJpaTest, 90% less infrastructure." |
| `spring-testing/webflux-testing-patterns.md` | Anti-pattern: "Using @SpringBootTest for all WebFlux tests — use @WebFluxTest for controller tests." |
| `spring-testing/websocket-stomp-testing-patterns.md` | Exception: WebSocket tests *require* @SpringBootTest(RANDOM_PORT) — MockMvc cannot upgrade to WS. |

## 2. ASSERTION QUALITY

| Source KB File | What it contributes |
|---|---|
| `coverage-mechanics/coverage-fundamentals.md` | Meaningful vs vanity coverage distinction. Example of vanity: `new Greeting(1, "test")` with no assertions. |
| `spring-testing/assertj-mockito-idioms.md` | AssertJ core patterns (extracting, filteredOn, assertThatThrownBy). BDDMockito preference (given/willReturn over when/thenReturn). ArgumentCaptor for side-effect verification. Common mistakes: over-verifying, mocking value objects. |
| `spring-testing/cross-cutting-testing-patterns.md` | "Behavior > Implementation" table — assert observable outcomes (HTTP status, re-fetched entity state, StepVerifier signals), not internal calls. Universal anti-pattern: "Verifying mocks instead of behavior — brittle, couples to implementation." |
| `spring-testing/mvc-rest-testing-patterns.md` | Anti-pattern: "Verifying mock calls instead of HTTP behavior." jsonPath assertion patterns. |

## 3. ERROR AND EDGE CASE COVERAGE

| Source KB File | What it contributes |
|---|---|
| `coverage-mechanics/common-gaps.md` | Priority list: error handling paths (#1), conditional branches, input validation, integration points. Explicit examples of what TO test vs what NOT to test. |
| `spring-testing/mvc-rest-testing-patterns.md` | Testing @RestControllerAdvice/exception handlers. Request validation (expecting 400). |
| `spring-testing/security-testing-patterns.md` | Anti-pattern: "Not testing 401/403 paths — always add unauthenticated + wrong-role tests." CSRF on mutating requests. |
| `spring-testing/webflux-testing-patterns.md` | StepVerifier error signal testing (expectError, expectErrorMatches). |
| `spring-testing/websocket-stomp-testing-patterns.md` | Testing error frames. |

## 4. DOMAIN-SPECIFIC TEST PATTERNS

| Source KB File | What it contributes |
|---|---|
| `spring-testing/jpa-testing-cheatsheet.md` | TestEntityManager for setup, flush+clear before read (§@DataJpaTest + TestEntityManager). Anti-pattern: "save() then findById() without flush()+clear()." "Do NOT use the repository under test to insert test data." |
| `spring-testing/jpa-repository-testing-best-practices.md` | @Transactional trap (§3), @Modifying query testing pattern (§9), Testcontainers over H2 (§2), DDD aggregate testing (§7). |
| `spring-testing/mvc-rest-testing-patterns.md` | MockMvc patterns, contentType on POST, jsonPath, scoped @WebMvcTest. RestTestClient pattern for Boot 4. |
| `spring-testing/webflux-testing-patterns.md` | WebTestClient and StepVerifier patterns. Anti-pattern: ".block() in tests — use StepVerifier." "thenCancel() on infinite streams." |
| `spring-testing/security-testing-patterns.md` | @WithMockUser, jwt() post-processor, oauth2Login(), csrf(). Anti-pattern: "Disabling security in tests (addFilters=false) — false confidence." |
| `spring-testing/websocket-stomp-testing-patterns.md` | RANDOM_PORT + StompClient, BlockingQueue pattern, session disconnect in teardown. Anti-pattern: "Using MockMvc for WebSocket endpoint." "Thread.sleep() for synchronization — use BlockingQueue or Awaitility." |
| `spring-testing/cross-cutting-testing-patterns.md` | Universal anti-patterns table covering all domains. |

## 5. COVERAGE TARGET SELECTION

| Source KB File | What it contributes |
|---|---|
| `coverage-mechanics/coverage-fundamentals.md` | What NOT to cover: records, main(), framework-generated code, trivial getters/setters, configuration classes. |
| `coverage-mechanics/common-gaps.md` | What NOT to test (records, main, config, generated code) vs what TO test (error handling, validation, conditional logic, edge cases). Coverage improvement priority order. |
| `spring-testing/jpa-testing-cheatsheet.md` | Anti-pattern: "Testing save() and findById() only — test YOUR query methods, not Spring Data plumbing." |
| `spring-testing/jpa-repository-testing-best-practices.md` | §10 What NOT to Test: inherited CRUD methods, derived query methods (framework-validated), schema generation. DO test: custom @Query, @Modifying, projections, constraints, aggregate round-trips. |

## 6. VERSION-AWARE PATTERNS

| Source KB File | What it contributes |
|---|---|
| `spring-testing/cross-cutting-testing-patterns.md` | Boot 4 Readiness Checklist (§Boot 4): @MockBean→@MockitoBean, @SpyBean→@MockitoSpyBean, RestTestClient, package relocations (@WebMvcTest, @DataJpaTest, TestEntityManager moved). AOT safety rules. |
| `spring-testing/mvc-rest-testing-patterns.md` | Boot 3→4 table: @WebMvcTest import path change, RestTestClient via @AutoConfigureRestTestClient, ProblemDetail default. |
| `spring-testing/jpa-testing-cheatsheet.md` | Boot 3→4 table: @DataJpaTest and TestEntityManager import path changes, Hibernate 7.x. |
| `spring-testing/security-testing-patterns.md` | Boot 3→4: Security 7 with AuthorizationManager, component-based config, jwt() same API. |
| `spring-testing/webflux-testing-patterns.md` | Boot 3→4: @MockBean→@MockitoBean, Reactor 3.7.x+. |
| `spring-testing/websocket-stomp-testing-patterns.md` | Boot 3→4: Security 7 component-based WebSocket config. |
| `spring-testing/assertj-mockito-idioms.md` | Boot 3→4: @MockBean deprecated, use @MockitoBean. |
| `spring-testing/index.md` | Version-Aware Navigation table: key differences per Boot version, which file documents each. Agent rule: detect Boot version from pom.xml. |
