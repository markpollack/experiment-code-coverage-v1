# gs-messaging-stomp-websocket

## Scores by Variant

| Variant | Cov% | T3 | Eff | Eff:BE | Eff:Cost | Eff:RC | Cost | In Tok | Out Tok | Think |
|---------|------|-----|------|--------|----------|--------|------|--------|---------|-------|
| control | 92.3 | 0.5 | 0.972 | 1.0 | 0.895 | 1.0 | $0.52 | 12 | 7892 | 1204 |
| variant-a | 92.3 | 0.6 | 0.978 | 1.0 | 0.916 | 1.0 | $0.42 | 13 | 5877 | 726 |
| variant-b | 92.3 | 0.5 | 0.972 | 1.0 | 0.896 | 1.0 | $0.52 | 16 | 6625 | 980 |
| variant-c | 84.6 | 0.65 | 0.787 | 0.75 | 0.887 | 0.75 | $0.56 | 18 | 6598 | 1142 |

## T3 Practice Adherence — Criterion Details

### control

| Criterion | Score | Evidence |
|-----------|-------|----------|
| assertion_quality | 0.50 | 0.50 — GreetingControllerTest.testGreetingReturnsCorrectContent and testGreetingEscapesHtmlTags use assertEquals with sp |
| command_execution | — | Command executed successfully |
| coverage_improved | — | +92.3 pp (0.0% → 92.3%) |
| coverage_target_selection | 0.20 | 0.20 — GreetingTest.java (4 tests) and HelloMessageTest.java (5 tests) together account for 9 of 16 tests and exclusivel |
| domain_specific_test_patterns | 0.20 | 0.20 — The production code is a WebSocket/STOMP application (GreetingController uses @MessageMapping/@SendTo). Best prac |
| error_and_edge_case_coverage | 0.50 | 0.50 — GreetingControllerTest.java tests HTML tag escaping (<b>World</b>) and ampersand escaping — meaningful edge cases |
| line_coverage_preserved | — | Drop -92.3% <= 5.0% threshold |
| test_slice_selection | 0.80 | 0.80 — GreetingControllerTest.java and WebSocketConfigTest.java use direct instantiation/plain Mockito with no Spring co |
| version_aware_patterns | 0.80 | 0.80 — Project uses Spring Boot 3.5.11 (3.x). Tests correctly use plain Mockito @ExtendWith(MockitoExtension.class) + @M |

### variant-a

| Criterion | Score | Evidence |
|-----------|-------|----------|
| assertion_quality | 0.50 | 0.50 — GreetingControllerTest.java uses specific AssertJ assertions on content values (isEqualTo, doesNotContain, contai |
| command_execution | — | Command executed successfully |
| coverage_improved | — | +92.3 pp (0.0% → 92.3%) |
| coverage_target_selection | 0.50 | 0.50 — GreetingControllerTest.java correctly targets the HTML-escaping business logic — the only meaningful branch in Gr |
| domain_specific_test_patterns | 0.80 | 0.80 — GreetingIntegrationTest.java follows WebSocket/STOMP best practices: @SpringBootTest(RANDOM_PORT), StandardWebSoc |
| error_and_edge_case_coverage | 0.50 | 0.50 — GreetingControllerTest.java covers meaningful edge cases: XSS input (<script>), ampersand escaping, quote escapin |
| line_coverage_preserved | — | Drop -92.3% <= 5.0% threshold |
| test_slice_selection | 0.50 | 0.50 — GreetingIntegrationTest.java correctly uses @SpringBootTest(webEnvironment = RANDOM_PORT) with StompClient — text |
| version_aware_patterns | 0.80 | 0.80 — Project is Spring Boot 3.5.11. All test classes use Boot 3.x-compatible annotations: @SpringBootTest (correct for |

### variant-b

| Criterion | Score | Evidence |
|-----------|-------|----------|
| assertion_quality | 0.50 | 0.50 — GreetingControllerTest.java uses specific value assertions (isEqualTo) to verify domain-meaningful behavior — HTM |
| command_execution | — | Command executed successfully |
| coverage_improved | — | +92.3 pp (0.0% → 92.3%) |
| coverage_target_selection | 0.50 | 0.50 — HelloMessageTest.java tests getName()/setName() and constructor on a plain POJO — low-value coverage of compiler- |
| domain_specific_test_patterns | 0.20 | 0.20 — The production code is a WebSocket/STOMP application (WebSocketConfig, GreetingController with @MessageMapping/@S |
| error_and_edge_case_coverage | 0.50 | 0.50 — GreetingControllerTest.java covers HTML-injection edge cases (angle brackets, ampersand) — good edge-case discipl |
| line_coverage_preserved | — | Drop -92.3% <= 5.0% threshold |
| test_slice_selection | 0.50 | 0.50 — GreetingControllerTest.java instantiates GreetingController directly with no Spring context — appropriate for its |
| version_aware_patterns | 0.80 | 0.80 — Spring Boot 3.5.11 (Boot 3.x). No @MockBean or @SpyBean used anywhere, so there is no opportunity for version mis |

### variant-c

| Criterion | Score | Evidence |
|-----------|-------|----------|
| assertion_quality | 0.80 | 0.80 — GreetingControllerTest.java uses AssertJ fluent assertions with specific domain values throughout: isEqualTo("Hel |
| command_execution | — | Command executed successfully |
| coverage_improved | — | +84.6 pp (0.0% → 84.6%) |
| coverage_target_selection | 0.50 | 0.50 — GreetingControllerTest.java's four core tests target meaningful behavior (HTML escaping logic, greeting format).  |
| domain_specific_test_patterns | 0.20 | 0.20 — Production code is a WebSocket/STOMP application (GreetingController uses @MessageMapping + @SendTo, WebSocketCon |
| error_and_edge_case_coverage | 0.80 | 0.80 — GreetingControllerTest.java covers: normal case (greetingReturnsHelloWithName), XSS injection (greetingEscapesHtm |
| line_coverage_preserved | — | Drop -84.6% <= 5.0% threshold |
| test_slice_selection | 0.80 | 0.80 — GreetingControllerTest.java uses plain JUnit with no Spring context to test the controller's business logic — app |
| version_aware_patterns | 0.80 | 0.80 — Spring Boot version is 3.5.11 (Boot 3.x). GreetingControllerTest.java uses plain JUnit 5 + AssertJ — no mocking a |
