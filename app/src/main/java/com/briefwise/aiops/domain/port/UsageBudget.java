package com.briefwise.aiops.domain.port;

import com.briefwise.aiops.domain.model.BudgetStatus;
import com.briefwise.aiops.domain.model.CostSummary;
import com.briefwise.shared.domain.model.PricingTier;
import com.briefwise.shared.domain.model.TokenCount;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Inbound port: daily LLM budgets per feature (ADR 0008). Days are UTC dates. */
public interface UsageBudget {

    /**
     * Code gate before a call.
     *
     * @throws com.briefwise.shared.domain.exception.UsageLimitExceededException if today's budget is used up
     * @throws com.briefwise.aiops.domain.model.NoBudgetConfiguredException if the feature has no budget
     */
    BudgetStatus ensureAvailable(String feature);

    /**
     * Adds a finished call to today's totals. Never throws for an exceeded budget: the money is already spent,
     * and the next {@link #ensureAvailable} refuses.
     *
     * @throws com.briefwise.aiops.domain.model.UnpricedModelException if the model has no configured price
     * @throws com.briefwise.aiops.domain.model.NoBudgetConfiguredException if the feature has no budget
     */
    BudgetStatus recordUsage(String feature, String model, TokenCount tokens, PricingTier tier);

    /**
     * What the tokens cost on the model at the tier's price, e.g. to estimate a batch before submitting it.
     *
     * @throws com.briefwise.aiops.domain.model.UnpricedModelException if the model has no configured price
     */
    BigDecimal costOf(String model, TokenCount tokens, PricingTier tier);

    /** @throws IllegalArgumentException if {@code from} is after {@code to} */
    CostSummary summarize(LocalDate from, LocalDate to);
}
