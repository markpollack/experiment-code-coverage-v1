# gs-accessing-data-jpa

## Scores by Variant

| Variant | Cov% | T3 | Eff | Eff:BE | Eff:Cost | Eff:RC | Cost | In Tok | Out Tok | Think |
|---------|------|-----|------|--------|----------|--------|------|--------|---------|-------|
| control | 94.6 | 0.6 | 0.766 | 0.75 | 0.81 | 0.75 | $0.95 | 28 | 9270 | 1901 |
| variant-a | 94.6 | 0.7 | 0.951 | 1.0 | 0.816 | 1.0 | $0.92 | 28 | 12428 | 2999 |
| variant-b | 94.6 | 0.65 | 0.951 | 1.0 | 0.817 | 1.0 | $0.92 | 25 | 11094 | 2649 |
| variant-c | 94.6 | 0.68 | 0.76 | 0.75 | 0.788 | 0.75 | $1.06 | 34 | 11229 | 2386 |

## T3 Practice Adherence — Criterion Details

### control

| Criterion | Score | Evidence |
|-----------|-------|----------|
| assertion_quality | 0.70 | 0.70 — CustomerRepositoryTest.java:40 uses AssertJ extracting + containsExactlyInAnyOrder — excellent behavioral asserti |
| command_execution | — | Command executed successfully |
| coverage_improved | — | +94.6 pp (0.0% → 94.6%) |
| coverage_target_selection | 0.30 | 0.30 — CustomerTest.java tests trivial entity accessors (constructor, getFirstName, getLastName, toString) — these are h |
| domain_specific_test_patterns | 0.50 | 0.50 — CustomerRepositoryTest.java uses @DataJpaTest (correct) and tests the custom derived query findByLastName (line 3 |
| error_and_edge_case_coverage | 0.50 | 0.50 — CustomerRepositoryTest.java:44-49 (findByLastNameReturnsEmptyListWhenNoMatch) tests the empty-result path — good. |
| line_coverage_preserved | — | Drop -94.6% <= 5.0% threshold |
| test_slice_selection | 0.80 | 0.80 — CustomerRepositoryTest.java uses @DataJpaTest (correct slice for repository tests); CustomerTest.java uses plain  |
| version_aware_patterns | 0.80 | 0.80 — CustomerRepositoryTest.java imports @DataJpaTest from org.springframework.boot.data.jpa.test.autoconfigure.DataJp |

### variant-a

| Criterion | Score | Evidence |
|-----------|-------|----------|
| assertion_quality | 0.80 | 0.80 — CustomerRepositoryTest.java uses AssertJ with extracting(Customer::getFirstName).containsExactlyInAnyOrder('Jack' |
| command_execution | — | Command executed successfully |
| coverage_improved | — | +94.6 pp (0.0% → 94.6%) |
| coverage_target_selection | 0.50 | 0.50 — CustomerRepositoryTest.java tests the two custom query methods findByLastName and findById (good), but also expen |
| domain_specific_test_patterns | 0.50 | 0.50 — CustomerRepositoryTest.java uses TestEntityManager.persistAndFlush() for data setup in most tests (correct). Howe |
| error_and_edge_case_coverage | 0.80 | 0.80 — CustomerRepositoryTest.java covers: findByLastName_returnsEmptyList_whenNoMatch (empty-result path), findByLastNa |
| line_coverage_preserved | — | Drop -94.6% <= 5.0% threshold |
| test_slice_selection | 0.80 | 0.80 — CustomerRepositoryTest.java correctly uses @DataJpaTest for the repository layer. AccessingDataJpaApplicationTest |
| version_aware_patterns | 0.80 | 0.80 — CustomerRepositoryTest.java uses Boot 4.x-appropriate import paths: org.springframework.boot.data.jpa.test.autoco |

### variant-b

| Criterion | Score | Evidence |
|-----------|-------|----------|
| assertion_quality | 0.80 | 0.80 — CustomerRepositoryTest.java uses AssertJ extracting(Customer::getFirstName).containsExactlyInAnyOrder("Alice","Bo |
| command_execution | — | Command executed successfully |
| coverage_improved | — | +94.6 pp (0.0% → 94.6%) |
| coverage_target_selection | 0.50 | 0.50 — CustomerTest.java targets the Customer entity's constructor, getters, and toString — low-value tests on compiler- |
| domain_specific_test_patterns | 0.50 | 0.50 — CustomerRepositoryTest.java correctly uses TestEntityManager for test data setup rather than the repository under |
| error_and_edge_case_coverage | 0.50 | 0.50 — findByLastName_returnsEmptyListWhenNoMatch covers the no-match edge case. No test for findById with a non-existen |
| line_coverage_preserved | — | Drop -94.6% <= 5.0% threshold |
| test_slice_selection | 0.80 | 0.80 — CustomerRepositoryTest.java uses @DataJpaTest (correct slice for JPA); CustomerTest.java uses plain JUnit with no |
| version_aware_patterns | 0.80 | 0.80 — CustomerRepositoryTest.java uses Boot 4.x relocated import paths: org.springframework.boot.data.jpa.test.autoconf |

### variant-c

| Criterion | Score | Evidence |
|-----------|-------|----------|
| assertion_quality | 0.50 | 0.50 — CustomerRepositoryTest.java has strong AssertJ assertions — extracting(Customer::getFirstName).containsExactlyInA |
| command_execution | — | Command executed successfully |
| coverage_improved | — | +94.6 pp (0.0% → 94.6%) |
| coverage_target_selection | 0.50 | 0.50 — CustomerTest.java tests the Customer entity's constructor and toString() — both are trivial (getters and a String |
| domain_specific_test_patterns | 0.50 | 0.50 — CustomerRepositoryTest.java correctly uses TestEntityManager for test data setup (not the repository under test)  |
| error_and_edge_case_coverage | 0.80 | 0.80 — CustomerRepositoryTest.java includes findByLastName_returnsEmptyListWhenNoMatch() which tests the no-results boun |
| line_coverage_preserved | — | Drop -94.6% <= 5.0% threshold |
| test_slice_selection | 0.80 | 0.80 — CustomerRepositoryTest.java correctly uses @DataJpaTest for repository-layer tests; CustomerTest.java is plain JU |
| version_aware_patterns | 1.00 | 1.00 — CustomerRepositoryTest.java uses Boot 4.x-specific import paths throughout: @DataJpaTest from org.springframework |
