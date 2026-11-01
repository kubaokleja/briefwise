import { describe, expect, it } from 'vitest';
import { formatAmount, formatDate, formatDateTime, formatPercent } from './format';

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

describe('formatDateTime', () => {
  it('shows day, month, year and 24-hour time in the given zone', () => {
    expect(formatDateTime('2026-10-28T05:07:00Z', 'UTC')).toBe('28 Oct 2026, 05:07');
    expect(formatDateTime('2026-10-28T05:07:00Z', 'Europe/Zurich')).toBe('28 Oct 2026, 06:07');
  });
});

describe('formatDate', () => {
  it('formats a calendar date without shifting it to another day', () => {
    expect(formatDate('2026-10-27')).toBe('27 Oct 2026');
  });
});
