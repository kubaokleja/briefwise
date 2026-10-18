package com.briefwise.aiops;

import org.springframework.stereotype.Component;

/**
 * The only class other contexts may import from {@code aiops}. Planned: recordGeneration, checkAndRecordUsage, getCostSummary, findReplay (M1).
 * Methods are added with the milestone that implements them (DESIGN 6.2); no logic here, delegate only.
 */
@Component
public class AiopsFacade {
}
