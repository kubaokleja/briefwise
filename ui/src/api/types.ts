// Readable names for the generated API types. schema.d.ts is generated from docs/openapi.json (`npm run gen:api`);
// never edit it by hand. CI fails when it is out of date (ADR 0007).
import type { components } from './schema';

type Schemas = components['schemas'];

export type Portfolio = Schemas['PortfolioResponse'];
export type Position = Schemas['PositionResponse'];
export type DailyMove = Schemas['DailyMoveResponse'];
export type PricePoint = Schemas['PricePointResponse'];

/** Spring Boot Actuator health. Not part of the OpenAPI spec (actuator endpoints are not documented there). */
export interface Health {
  status: 'UP' | 'DOWN' | 'OUT_OF_SERVICE' | 'UNKNOWN';
}
