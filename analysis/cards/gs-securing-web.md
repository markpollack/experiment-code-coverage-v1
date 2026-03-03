# gs-securing-web

## Scores by Variant

| Variant | Cov% | T3 | Eff | Eff:BE | Eff:Cost | Eff:RC | Cost | In Tok | Out Tok | Think |
|---------|------|-----|------|--------|----------|--------|------|--------|---------|-------|
| control | 91.3 | 0.83 | 0.946 | 1.0 | 0.796 | 1.0 | $1.02 | 20 | 10764 | 1586 |
| variant-a | 91.3 | 0.88 | 0.948 | 1.0 | 0.806 | 1.0 | $0.97 | 33 | 11212 | 1746 |
| variant-b | 91.3 | 0.78 | 0.647 | 0.625 | 0.706 | 0.625 | $1.47 | 38 | 16712 | 3094 |
| variant-c | 91.3 | 0.78 | 0.864 | 0.875 | 0.834 | 0.875 | $0.83 | 22 | 10096 | 2005 |

## T3 Practice Adherence — Criterion Details

### control

| Criterion | Score | Evidence |
|-----------|-------|----------|
| assertion_quality | 0.80 | 0.80 — SecuringWebApplicationTests.java uses meaningful observable-behavior assertions: status().isOk(), view().name('he |
| command_execution | — | Command executed successfully |
| coverage_improved | — | +91.3 pp (0.0% → 91.3%) |
| coverage_target_selection | 0.80 | 0.80 — WebSecurityConfigTest.java targets meaningful bean logic: passwordEncoder() encoding/matching behavior, userDetai |
| domain_specific_test_patterns | 0.90 | 0.90 — SecuringWebApplicationTests.java uses @WithMockUser for authenticated tests, formLogin() security request builder |
| error_and_edge_case_coverage | 0.80 | 0.80 — SecuringWebApplicationTests.java tests unauthenticated access to /hello (redirect to /login), login with wrong pa |
| line_coverage_preserved | — | Drop -91.3% <= 5.0% threshold |
| test_slice_selection | 0.80 | 0.80 — WebSecurityConfigTest.java uses plain JUnit with no Spring context (direct instantiation of WebSecurityConfig) —  |
| version_aware_patterns | 0.90 | 0.90 — SecuringWebApplicationTests.java imports org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc  |

### variant-a

| Criterion | Score | Evidence |
|-----------|-------|----------|
| assertion_quality | 0.90 | 0.90 — UserDetailsServiceTest.java uses AssertJ throughout: extracting("authority").containsExactly("ROLE_USER"), assert |
| command_execution | — | Command executed successfully |
| coverage_improved | — | +91.3 pp (0.0% → 91.3%) |
| coverage_target_selection | 0.80 | 0.80 — SecuringWebApplication.main() is not tested (correct). MvcConfig @Configuration is not directly unit-tested but i |
| domain_specific_test_patterns | 0.90 | 0.90 — Security domain: WebSecurityConfigTest.java uses @WithMockUser for authenticated access tests; uses .with(csrf()) |
| error_and_edge_case_coverage | 0.90 | 0.90 — WebSecurityConfigTest.java covers: unauthenticated redirect to /login (helloRequiresAuthentication_redirectsToLog |
| line_coverage_preserved | — | Drop -91.3% <= 5.0% threshold |
| test_slice_selection | 0.80 | 0.80 — WebSecurityConfigTest.java uses @WebMvcTest (not scoped to a specific controller class, but no explicit @Controll |
| version_aware_patterns | 1.00 | 1.00 — WebSecurityConfigTest.java imports org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest — the Boot 4.x p |

### variant-b

| Criterion | Score | Evidence |
|-----------|-------|----------|
| assertion_quality | 0.80 | 0.80 — WebSecurityConfigTest.java uses AssertJ throughout with specific domain-value assertions: assertThat(user.getUser |
| command_execution | — | Command executed successfully |
| coverage_improved | — | +91.3 pp (0.0% → 91.3%) |
| coverage_target_selection | 0.50 | 0.50 — WebSecurityConfigTest.java directly tests a @Configuration class (WebSecurityConfig) — the rubric calls out @Conf |
| domain_specific_test_patterns | 0.80 | 0.80 — WebMvcSecurityTest.java uses MockMvcTester (Boot 4 API) with @WebMvcTest and @Import(WebSecurityConfig.class) — s |
| error_and_edge_case_coverage | 0.80 | 0.80 — WebMvcSecurityTest.java covers the unauthenticated path (helloRedirectsUnauthenticatedToLogin verifies 302+Locati |
| line_coverage_preserved | — | Drop -91.3% <= 5.0% threshold |
| test_slice_selection | 0.80 | 0.80 — WebMvcSecurityTest.java uses @WebMvcTest (correct MVC slice) rather than @SpringBootTest; WebSecurityConfigTest.j |
| version_aware_patterns | 1.00 | 1.00 — WebMvcSecurityTest.java imports org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest — the correct Boot  |

### variant-c

| Criterion | Score | Evidence |
|-----------|-------|----------|
| assertion_quality | 0.80 | 0.80 — SecuringWebApplicationTests.java uses AssertJ-based MockMvcTester assertions with specific observable values: has |
| command_execution | — | Command executed successfully |
| coverage_improved | — | +91.3 pp (0.0% → 91.3%) |
| coverage_target_selection | 0.80 | 0.80 — SecuringWebApplicationTests.java focuses on security behavior (authentication, authorization, CSRF, logout). No t |
| domain_specific_test_patterns | 0.80 | 0.80 — MVC domain: uses MockMvcTester (Boot 4.x fluent API) — correct; includes .with(csrf()) on all POST requests (logi |
| error_and_edge_case_coverage | 0.80 | 0.80 — SecuringWebApplicationTests.java covers: unauthenticated redirect to /login (helloPageRequiresAuthentication, hel |
| line_coverage_preserved | — | Drop -91.3% <= 5.0% threshold |
| test_slice_selection | 0.50 | 0.50 — SecuringWebApplicationTests.java uses @SpringBootTest + @AutoConfigureMockMvc for all tests. The production code  |
| version_aware_patterns | 1.00 | 1.00 — SecuringWebApplicationTests.java uses the Boot 4.x import path org.springframework.boot.webmvc.test.autoconfigure |
