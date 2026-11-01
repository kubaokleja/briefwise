import type { ReactNode } from 'react';
import { Link } from 'react-router';
import type { Portfolio, PortfolioDailyMove, RelevantNews } from '../api/types';
import { useApi } from '../api/useApi';
import { Percent } from '../components/Num';
import { Empty, ErrorPanel, Loading } from '../components/States';
import { formatDate } from '../lib/format';

/**
 * Dashboard v0 (M1): one tile per portfolio with today's move and the count of relevant news. Alert counts (M3),
 * briefs awaiting approval (M4) and the last pipeline run come with their features.
 */
export function DashboardPage() {
  const portfolios = useApi<Portfolio[]>('/api/portfolios');
  return (
    <div className="max-w-5xl space-y-6">
      <h1 className="text-xl font-semibold">Dashboard</h1>
      {portfolios.status === 'loading' && <Loading label="Loading portfolios" />}
      {portfolios.status === 'error' && <ErrorPanel error={portfolios.error} onRetry={portfolios.reload} />}
      {portfolios.status === 'success' &&
        (portfolios.data.length === 0 ? (
          <Empty title="No portfolios yet" hint="Synthetic portfolios are seeded by Flyway (V5)." />
        ) : (
          <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {portfolios.data.map((p) => (
              <li key={p.id}>
                <PortfolioTile portfolio={p} />
              </li>
            ))}
          </ul>
        ))}
    </div>
  );
}

function PortfolioTile({ portfolio }: { portfolio: Portfolio }) {
  const base = `/api/portfolios/${portfolio.id}`;
  return (
    <article
      aria-labelledby={`tile-${portfolio.id}`}
      className="h-full rounded border border-line bg-surface-1 p-4 hover:border-accent/50"
    >
      <h2 id={`tile-${portfolio.id}`} className="font-medium">
        <Link to={`/portfolios/${portfolio.id}`} className="hover:text-accent">
          {portfolio.name}
        </Link>
      </h2>
      <p className="text-xs text-muted">
        {portfolio.riskProfile.charAt(0) + portfolio.riskProfile.slice(1).toLowerCase()} ·{' '}
        {portfolio.positions.length} positions
      </p>
      <dl className="mt-4 space-y-2 text-sm">
        <DailyMoveMetric path={`${base}/daily-move`} />
        <RelevantNewsMetric path={`${base}/news/relevant`} portfolioId={portfolio.id} />
      </dl>
    </article>
  );
}

function Metric({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <dt className="text-muted">{label}</dt>
      <dd className="text-right">{children}</dd>
    </div>
  );
}

/** Small inline error for a tile: the tile stays usable, the reference still points to the log line. */
function MetricError({ correlationId }: { correlationId: string }) {
  return (
    <span className="text-xs text-down" title={`Reference: ${correlationId}`}>
      <span aria-hidden="true">✕ </span>unavailable
    </span>
  );
}

function DailyMoveMetric({ path }: { path: string }) {
  const move = useApi<PortfolioDailyMove>(path);
  return (
    <Metric label="Today">
      {move.status === 'loading' && <span className="text-muted">…</span>}
      {move.status === 'error' && <MetricError correlationId={move.error.correlationId} />}
      {move.status === 'success' &&
        (move.data.changePercent === null ? (
          <span className="text-muted">no prices</span>
        ) : (
          <span className="flex flex-col items-end">
            <Percent points={move.data.changePercent} change />
            <span className="text-xs text-muted">
              {move.data.asOf && <>as of {formatDate(move.data.asOf)}</>}
              {move.data.unpriced.length > 0 && (
                <>
                  {' · '}covers <Percent points={move.data.pricedWeight * 100} />
                </>
              )}
            </span>
          </span>
        ))}
    </Metric>
  );
}

function RelevantNewsMetric({ path, portfolioId }: { path: string; portfolioId: string }) {
  const news = useApi<RelevantNews[]>(path);
  return (
    <Metric label="Relevant news (24 h)">
      {news.status === 'loading' && <span className="text-muted">…</span>}
      {news.status === 'error' && <MetricError correlationId={news.error.correlationId} />}
      {news.status === 'success' && (
        <Link to={`/news?portfolio=${portfolioId}`} className="font-mono tabular-nums hover:text-accent">
          {news.data.length}
        </Link>
      )}
    </Metric>
  );
}
