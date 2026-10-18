import { formatAmount, formatPercent } from '../lib/format';

// Numbers are always monospaced with tabular figures, so digits line up in columns. Right alignment is set by the
// table cell (`text-right`), not here, because the same number may also appear inline.
const NUMBER = 'font-mono tabular-nums whitespace-nowrap';

export function Amount({ value, currency }: { value: number; currency: string }) {
  return <span className={NUMBER}>{formatAmount(value, currency)}</span>;
}

/**
 * A percentage in percent points. With `change`, the value is signed and coloured up/down, and an arrow carries the
 * direction too, because colour must never be the only carrier of meaning (DESIGN 15).
 */
export function Percent({ points, change = false }: { points: number; change?: boolean }) {
  if (!change) {
    return <span className={NUMBER}>{formatPercent(points)}</span>;
  }
  const direction = points > 0 ? 'up' : points < 0 ? 'down' : 'flat';
  const style = { up: 'text-up', down: 'text-down', flat: 'text-neutral' }[direction];
  const arrow = { up: '▲', down: '▼', flat: '■' }[direction];
  return (
    <span className={`${NUMBER} ${style}`}>
      <span aria-hidden="true">{arrow} </span>
      {formatPercent(points, { signed: true })}
    </span>
  );
}
