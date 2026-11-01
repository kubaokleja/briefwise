import type { EventType, Relevance, Sentiment, UnclassifiedReason } from '../api/types';

// Display rules for news labels. Colour is never the only carrier of meaning (DESIGN 15): every sentiment has an icon
// and a word as well. Exhaustive Records make the compiler fail when the backend adds a value.

export interface SentimentDisplay {
  label: string;
  icon: string;
  className: string;
}

const SENTIMENT: Record<Sentiment, SentimentDisplay> = {
  POSITIVE: { label: 'Positive', icon: '▲', className: 'text-up' },
  NEUTRAL: { label: 'Neutral', icon: '■', className: 'text-neutral' },
  NEGATIVE: { label: 'Negative', icon: '▼', className: 'text-down' },
};

export function sentimentDisplay(sentiment: Sentiment): SentimentDisplay {
  return SENTIMENT[sentiment];
}

const RELEVANCE: Record<Relevance, string> = { HIGH: 'High', MEDIUM: 'Medium', LOW: 'Low' };

export function relevanceLabel(relevance: Relevance): string {
  return RELEVANCE[relevance];
}

const EVENT_TYPE: Record<EventType, string> = {
  EARNINGS: 'Earnings',
  GUIDANCE: 'Guidance',
  M_AND_A: 'M&A',
  REGULATORY: 'Regulatory',
  LITIGATION: 'Litigation',
  MANAGEMENT: 'Management',
  MACRO: 'Macro',
  PRODUCT: 'Product',
  ANALYST: 'Analyst',
  OTHER: 'Other',
};

export function eventTypeLabel(eventType: EventType): string {
  return EVENT_TYPE[eventType];
}

// Plain-language reasons for an RM; the technical reason stays in the server log and ai_generation_log.
const UNCLASSIFIED_REASON: Record<UnclassifiedReason, string> = {
  INVALID_OUTPUT: 'the AI answer was not usable',
  PROVIDER_ERROR: 'the AI service was unavailable',
  INJECTION_SUSPECTED: 'the text looked like an instruction to the AI and was kept away from it',
  RETRIES_EXHAUSTED: 'several attempts failed',
};

export function unclassifiedReasonText(reason: UnclassifiedReason): string {
  return UNCLASSIFIED_REASON[reason];
}
