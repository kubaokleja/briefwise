package com.briefwise.news;

import org.springframework.stereotype.Component;

/**
 * The only class other contexts may import from {@code news}. Planned: ingest, classifyPending, getRelevantNews, summarizeForPortfolio (M1).
 * Methods are added with the milestone that implements them (DESIGN 6.2); no logic here, delegate only.
 */
@Component
public class NewsFacade {
}
