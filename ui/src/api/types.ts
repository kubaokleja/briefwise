// Readable names for the generated API types. schema.d.ts is generated from docs/openapi.json (`npm run gen:api`);
// never edit it by hand. CI fails when it is out of date (ADR 0007).
import type { components } from './schema';

type Schemas = components['schemas'];

export type Portfolio = Schemas['PortfolioResponse'];
export type Position = Schemas['PositionResponse'];
export type DailyMove = Schemas['DailyMoveResponse'];
export type PricePoint = Schemas['PricePointResponse'];
export type PortfolioDailyMove = Schemas['PortfolioDailyMoveResponse'];
export type PositionMove = Schemas['PositionMoveResponse'];

export type NewsFeedItem = Schemas['NewsFeedItemResponse'];
export type Sentiment = NonNullable<NewsFeedItem['sentiment']>;
export type Relevance = NonNullable<NewsFeedItem['relevance']>;
export type EventType = NonNullable<NewsFeedItem['eventType']>;
export type UnclassifiedReason = NonNullable<NewsFeedItem['unclassifiedReason']>;
export type RelevantNews = Schemas['RelevantNewsResponse'];
export type NewsSummary = Schemas['SummaryResponse'];
export type SummaryPoint = Schemas['PointResponse'];
export type Citation = Schemas['CitationResponse'];

export type JobRun = Schemas['JobRunView'];
export type JobStep = Schemas['JobStepView'];

/** Spring Boot Actuator health. Not part of the OpenAPI spec (actuator endpoints are not documented there). */
export interface Health {
  status: 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN';
}
