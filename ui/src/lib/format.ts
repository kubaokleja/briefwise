// Number formatting for a banking UI (DESIGN 15): fixed decimals, grouping, unit or currency always shown.
// Components render the result in IBM Plex Mono with tabular figures, right-aligned, so columns line up.

const LOCALE = 'en-US';

/** `1234.5, 'USD'` -> `"1,234.50 USD"`. The ISO code (not a symbol) is unambiguous for CHF, USD, EUR alike. */
export function formatAmount(value: number, currency: string, fractionDigits = 2): string {
  const number = new Intl.NumberFormat(LOCALE, {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  }).format(value);
  return `${number} ${currency}`;
}

/**
 * Takes percent points: `1.234` -> `"1.23%"`. With `signed`, positive values get a `+` (for changes).
 * Callers holding a fraction (e.g. a position weight of 0.3) multiply by 100 first.
 */
export function formatPercent(points: number, options: { signed?: boolean; fractionDigits?: number } = {}): string {
  const { signed = false, fractionDigits = 2 } = options;
  const number = new Intl.NumberFormat(LOCALE, {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
    signDisplay: signed ? 'exceptZero' : 'auto',
  }).format(points);
  return `${number}%`;
}

/**
 * A timestamp for people: `"28 Oct 2026, 05:00"` in the reader's time zone (pass `timeZone` for a fixed one, e.g. in
 * tests). The API sends ISO-8601 UTC instants.
 */
export function formatDateTime(iso: string, timeZone?: string): string {
  return new Intl.DateTimeFormat('en-GB', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
    timeZone,
  }).format(new Date(iso));
}

/** A calendar date from the API (`"2026-10-27"`) as `"27 Oct 2026"`; no time zone shift, it is a date, not an instant. */
export function formatDate(isoDate: string): string {
  return new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' }).format(
    new Date(`${isoDate}T00:00:00Z`),
  );
}
