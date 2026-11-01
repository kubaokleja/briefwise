import { Link, useParams } from 'react-router';
import type { NewsSummary, Portfolio, PortfolioDailyMove } from '../api/types';
import { useApi } from '../api/useApi';
import { AiMarker } from '../components/AiMarker';
import { Percent } from '../components/Num';
import { Empty, ErrorPanel, Loading } from '../components/States';
import { formatDate, formatDateTime } from '../lib/format';
import { safeHref } from '../lib/links';

/** Portfolio v0 (M1): positions with today's move and the daily AI news summary. Volatility and alerts come in M3. */
export function PortfolioPage() {
  const { portfolioId = '' } = useParams();
  const base = `/api/portfolios/${encodeURIComponent(portfolioId)}`;
  const portfolio = useApi<Portfolio>(base);

  if (portfolio.status === 'loading') {
    return <Loading label="Loading portfolio" />;
  }
  if (portfolio.status === 'error') {
    return portfolio.error.status === 404 || portfolio.error.status === 400 ? (
      <Empty title="Portfolio not found" hint="Pick a portfolio on the Dashboard." />
    ) : (
      <ErrorPanel error={portfolio.error} onRetry={portfolio.reload} />
    );
  }
  const p = portfolio.data;
  return (
    <div className="max-w-5xl space-y-6">
      <div>
        <p className="text-xs text-muted">
          <Link to="/" className="hover:text-text">
            Dashboard
          </Link>{' '}
          / Portfolio
        </p>
        <h1 className="mt-1 text-xl font-semibold">{p.name}</h1>
        <p className="text-sm text-muted">
          {p.riskProfile.charAt(0) + p.riskProfile.slice(1).toLowerCase()} · {p.positions.length} positions ·{' '}
          <Link to={`/news?portfolio=${p.id}`} className="underline hover:text-text">
            News for this portfolio
          </Link>
        </p>
      </div>
      <DailyMoveSection path={`${base}/daily-move`} />
      <SummarySection path={`${base}/news/summary`} />
    </div>
  );
}

function DailyMoveSection({ path }: { path: string }) {
  const move = useApi<PortfolioDailyMove>(path);
  return (
    <section aria-labelledby="move-heading" className="rounded border border-line bg-surface-1 p-4">
      <h2 id="move-heading" className="mb-3 text-sm font-medium text-muted">
        Positions and daily move
      </h2>
      {move.status === 'loading' && <Loading label="Loading prices" />}
      {move.status === 'error' && <ErrorPanel error={move.error} onRetry={move.reload} />}
      {move.status === 'success' && <DailyMoveTable move={move.data} />}
    </section>
  );
}

function DailyMoveTable({ move }: { move: PortfolioDailyMove }) {
  return (
    <>
      <p className="mb-4 flex flex-wrap items-baseline gap-x-3 text-sm">
        <span className="text-muted">Portfolio today</span>
        {move.changePercent === null ? (
          <span className="text-muted">No prices yet</span>
        ) : (
          <span className="text-lg">
            <Percent points={move.changePercent} change />
          </span>
        )}
        {move.asOf && <span className="text-xs text-muted">as of {formatDate(move.asOf)}</span>}
        {move.unpriced.length > 0 && move.changePercent !== null && (
          <span className="text-xs text-warning">
            covers <Percent points={move.pricedWeight * 100} /> of the portfolio weight
          </span>
        )}
      </p>
      <table className="w-full text-sm">
        <thead className="text-left text-xs text-muted">
          <tr className="border-b border-line">
            <th className="py-2 font-medium">Ticker</th>
            <th className="py-2 text-right font-medium">Weight</th>
            <th className="py-2 text-right font-medium">Daily change</th>
            <th className="py-2 text-right font-medium">Contribution</th>
            <th className="py-2 text-right font-medium">Close date</th>
          </tr>
        </thead>
        <tbody>
          {move.positions.map((p) => (
            <tr key={p.ticker} className="border-b border-line/60 last:border-0">
              <td className="py-2 font-mono">{p.ticker}</td>
              <td className="py-2 text-right">
                <Percent points={p.weight * 100} />
              </td>
              <td className="py-2 text-right">
                <Percent points={p.changePercent} change />
              </td>
              <td className="py-2 text-right">
                <Percent points={p.contribution} change />
              </td>
              <td className="py-2 text-right text-muted">{formatDate(p.date)}</td>
            </tr>
          ))}
          {move.unpriced.map((ticker) => (
            <tr key={ticker} className="border-b border-line/60 last:border-0">
              <td className="py-2 font-mono">{ticker}</td>
              <td className="py-2 text-right text-muted" colSpan={4}>
                No price
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}

function SummarySection({ path }: { path: string }) {
  const summary = useApi<NewsSummary>(path);
  return (
    <section aria-labelledby="summary-heading" className="rounded border border-line bg-surface-1 p-4">
      <h2 id="summary-heading" className="mb-3 text-sm font-medium text-muted">
        Daily news summary
      </h2>
      {summary.status === 'loading' && <Loading label="Loading summary" />}
      {summary.status === 'error' &&
        (summary.error.status === 404 ? (
          <Empty title="No summary yet" hint='The news pipeline writes it. Run it from the News page ("Run news now").' />
        ) : (
          <ErrorPanel error={summary.error} onRetry={summary.reload} />
        ))}
      {summary.status === 'success' && <Summary summary={summary.data} />}
    </section>
  );
}

/**
 * The summary as stored by the pipeline. Model output and quoted headlines are rendered as text; every point links to
 * its cited articles, and the AI marker is always shown (ADR 0007).
 */
export function Summary({ summary }: { summary: NewsSummary }) {
  if (summary.empty) {
    return (
      <Empty
        title="No relevant news today"
        hint={`Nothing of high or medium relevance in the last 24 hours (${formatDateTime(summary.generatedAt)}).`}
      />
    );
  }
  // Number each cited article once, in order of first citation, so points can refer to "[1]".
  const sources: { id: string; href: string | undefined; title: string }[] = [];
  const numberOf = (sourceId: string, href: string | undefined, title: string) => {
    let index = sources.findIndex((s) => s.id === sourceId);
    if (index === -1) {
      sources.push({ id: sourceId, href, title });
      index = sources.length - 1;
    }
    return index + 1;
  };
  const points = summary.points.map((point) => ({
    text: point.text,
    refs: point.citations.map((c) => numberOf(c.sourceId, safeHref(c.locator), c.excerpt || c.sourceId)),
  }));

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <AiMarker
          state={summary.fallback ? { kind: 'fallback' } : { kind: 'draft' }}
          model={summary.model}
          promptVersion={summary.promptVersion}
          sourcesHref="#summary-sources"
        />
        <span className="text-xs text-muted">{formatDateTime(summary.generatedAt)}</span>
      </div>
      <ul className="list-disc space-y-2 pl-5 text-sm">
        {points.map((point, i) => (
          <li key={i}>
            {point.text}{' '}
            {point.refs.map((n) => (
              <a key={n} href={`#source-${n}`} className="font-mono text-xs text-accent hover:underline">
                [{n}]
              </a>
            ))}
          </li>
        ))}
      </ul>
      {summary.violations.length > 0 && (
        <div role="alert" className="rounded border border-warning/40 bg-warning/10 px-3 py-2 text-xs">
          <p className="font-medium text-warning">
            <span aria-hidden="true">▲ </span>The compliance check flagged this text
          </p>
          <ul className="mt-1 space-y-0.5 text-text">
            {summary.violations.map((v, i) => (
              <li key={i}>
                <span className="font-mono">{v.rule}</span>: “{v.offendingText}”
              </li>
            ))}
          </ul>
        </div>
      )}
      <div id="summary-sources">
        <h3 className="text-xs font-medium text-muted">Sources</h3>
        <ol className="mt-1 space-y-0.5 text-xs">
          {sources.map((s, i) => (
            <li key={s.id} id={`source-${i + 1}`}>
              <span className="font-mono text-muted">[{i + 1}]</span>{' '}
              {s.href ? (
                <a href={s.href} target="_blank" rel="noopener noreferrer" className="hover:text-accent hover:underline">
                  {s.title}
                </a>
              ) : (
                s.title
              )}
            </li>
          ))}
        </ol>
      </div>
      <p className="border-t border-line pt-3 text-xs text-muted">{summary.disclaimer}</p>
    </div>
  );
}
