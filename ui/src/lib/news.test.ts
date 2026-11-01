import { describe, expect, it } from 'vitest';
import { eventTypeLabel, relevanceLabel, sentimentDisplay, unclassifiedReasonText } from './news';

describe('news labels', () => {
  it('gives every sentiment a word and an icon, not only a colour', () => {
    for (const sentiment of ['POSITIVE', 'NEUTRAL', 'NEGATIVE'] as const) {
      const display = sentimentDisplay(sentiment);
      expect(display.label).not.toBe('');
      expect(display.icon).not.toBe('');
    }
    expect(sentimentDisplay('NEGATIVE')).toMatchObject({ label: 'Negative', className: 'text-down' });
  });

  it('spells out abbreviations and reasons', () => {
    expect(eventTypeLabel('M_AND_A')).toBe('M&A');
    expect(relevanceLabel('HIGH')).toBe('High');
    expect(unclassifiedReasonText('INJECTION_SUSPECTED')).toContain('kept away');
  });
});
