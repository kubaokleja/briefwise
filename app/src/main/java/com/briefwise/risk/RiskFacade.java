package com.briefwise.risk;

import org.springframework.stereotype.Component;

/**
 * The only class other contexts may import from {@code risk}. Planned: getForecast, getAlerts, evaluateAlerts (M3).
 * Methods are added with the milestone that implements them (DESIGN 6.2); no logic here, delegate only.
 */
@Component
public class RiskFacade {
}
