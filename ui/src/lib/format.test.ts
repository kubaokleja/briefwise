import { describe, expect, it } from 'vitest';
import { formatAmount, formatPercent } from './format';

describe('formatAmount', () => {
  it('groups thousands, fixes two decimals and appends the currency code', () => {
    expect(formatAmount(1234.5, 'USD')).toBe('1,234.50 USD');
  });

  it('keeps the sign of negative amounts', () => {
    expect(formatAmount(-0.456, 'CHF')).toBe('-0.46 CHF');
  });
});

describe('formatPercent', () => {
  it('formats percent points without a sign by default', () => {
    expect(formatPercent(30)).toBe('30.00%');
  });

  it('shows + and - for signed changes but no sign for zero', () => {
    expect(formatPercent(1.234, { signed: true })).toBe('+1.23%');
    expect(formatPercent(-0.5, { signed: true })).toBe('-0.50%');
    expect(formatPercent(0, { signed: true })).toBe('0.00%');
  });
});
