package io.github.markpollack.experiment.coverage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springaicommunity.judge.Judge;
import org.springaicommunity.judge.jury.CascadedJury;
import org.springaicommunity.judge.jury.Jury;
import org.springaicommunity.judge.jury.ConsensusStrategy;
import org.springaicommunity.judge.jury.SimpleJury;
import org.springaicommunity.judge.jury.TierConfig;
import org.springaicommunity.judge.jury.TierPolicy;
/**
 * Factory for building {@link Jury} instances from variant specifications.
 * Pre-wired with standard tier structure:
 * <ul>
 *   <li>Tier 0: Deterministic gates (build success, preservation) — REJECT_ON_ANY_FAIL</li>
 *   <li>Tier 1: Structural analysis — ACCEPT_ON_ALL_PASS</li>
 *   <li>Tier 2: Domain-specific evaluation — FINAL_TIER</li>
 * </ul>
 *
 * <p>Domain projects extend this to register their custom judges at the appropriate tier.</p>
 */
public class JuryFactory {

	private final Map<Integer, List<Judge>> tierJudges;

	private final Map<Integer, TierPolicy> tierPolicies;

	public JuryFactory(Map<Integer, List<Judge>> tierJudges, Map<Integer, TierPolicy> tierPolicies) {
		this.tierJudges = tierJudges;
		this.tierPolicies = tierPolicies;
	}

	/**
	 * Build a jury for the given variant. Subclasses can override to add
	 * variant-specific judges.
	 */
	public Jury build(VariantSpec variant) {
		List<TierConfig> tiers = new ArrayList<>();

		for (var entry : tierJudges.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
			int tierNum = entry.getKey();
			List<Judge> judges = entry.getValue();
			TierPolicy policy = tierPolicies.getOrDefault(tierNum, TierPolicy.FINAL_TIER);

			SimpleJury.Builder juryBuilder = SimpleJury.builder()
					.votingStrategy(new ConsensusStrategy());
			for (Judge judge : judges) {
				juryBuilder.judge(judge);
			}
			SimpleJury tierJury = juryBuilder.build();
			tiers.add(new TierConfig("tier-" + tierNum, tierJury, policy));
		}

		CascadedJury.Builder cascadeBuilder = CascadedJury.builder();
		for (TierConfig tier : tiers) {
			cascadeBuilder.tier(tier.name(), tier.jury(), tier.policy());
		}
		return cascadeBuilder.build();
	}

	/**
	 * Create a builder for JuryFactory.
	 */
	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private final Map<Integer, List<Judge>> tierJudges = new java.util.TreeMap<>();

		private final Map<Integer, TierPolicy> tierPolicies = new java.util.HashMap<>();

		public Builder addJudge(int tier, Judge judge) {
			tierJudges.computeIfAbsent(tier, k -> new ArrayList<>()).add(judge);
			return this;
		}

		public Builder tierPolicy(int tier, TierPolicy policy) {
			tierPolicies.put(tier, policy);
			return this;
		}

		public JuryFactory build() {
			return new JuryFactory(tierJudges, tierPolicies);
		}

	}

}
