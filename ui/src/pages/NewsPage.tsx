import { useId, useState } from 'react';
import { useSearchParams } from 'react-router';
import { ApiError, apiPost } from '../api/client';
import type { JobRun, NewsFeedItem, Portfolio } from '../api/types';
import { useApi } from '../api/useApi';
import { AiMarker } from '../components/AiMarker';
import { Empty, ErrorPanel, Loading } from '../components/States';
import { formatDateTime } from '../lib/format';
import { safeHref } from '../lib/links';
import { eventTypeLabel, relevanceLabel, sentimentDisplay, unclassifiedReasonText } from '../lib/news';

const FEED_LIMIT = 50;

/** The news feed (DESIGN 15 "News"): every item with its classification, or why it has none. */
export function NewsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const portfolioId = searchParams.get('portfolio') ?? '';
  const feedPath = `/api/news?limit=${FEED_LIMIT}${portfolioId ? `&portfolioId=${encodeURIComponent(portfolioId)}` : ''}`;
  const feed = useApi<NewsFeedItem[]>(feedPath);

  return (
    <div className="max-w-4xl space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <h1 className="text-xl font-semibold">News</h1>
        <PortfolioFilter
          value={portfolioId}
          onChange={(id) => setSearchParams(id ? { portfolio: id } : {})}
        />
      </div>
      <RunNewsPanel onFinished={feed.reload} />
      <section aria-label="News feed">
        {feed.status === 'loading' && <Loading label="Loading news" />}
        {feed.status === 'error' && <ErrorPanel error={feed.error} onRetry={feed.reload} />}
        {feed.status === 'success' &&
          (feed.data.length === 0 ? (
            <Empty
              title="No news yet"
              hint={
                portfolioId
                  ? 'Nothing was fetched for the holdings of this portfolio. Try "Run news now".'
                  : 'Run the news pipeline to fetch and classify news for held companies.'
              }
            />
          ) : (
            <ul className="space-y-3">
              {feed.data.map((item) => (
                <li key={item.id}>
                  <NewsItemCard item={item} />
                </li>
              ))}
            </ul>
          ))}
      </section>
    </div>
  );
}

function PortfolioFilter({ value, onChange }: { value: string; onChange: (portfolioId: string) => void }) {
  const portfolios = useApi<Portfolio[]>('/api/portfolios');
  const id = useId();
  const options = portfolios.status === 'success' ? portfolios.data : [];
  return (
    <div className="flex items-center gap-2 text-sm">
      <label htmlFor={id} className="text-muted">
        Portfolio
      </label>
      <select
        id={id}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="rounded border border-line bg-surface-2 px-2 py-1 text-text"
      >
        <option value="">All holdings</option>
        {options.map((p) => (
          <option key={p.id} value={p.id}>
            {p.name}
          </option>
        ))}
      </select>
    </div>
  );
}

export function NewsItemCard({ item }: { item: NewsFeedItem }) {
  const href = safeHref(item.url);
  return (
    <article className="rounded border border-line bg-surface-1 p-4">
      <h2 className="text-sm font-medium">
        {/* Headlines are third-party text: rendered as text, linked only if the URL is http(s). */}
        {href ? (
          <a href={href} target="_blank" rel="noopener noreferrer" className="hover:text-accent hover:underline">
            {item.headline}
          </a>
        ) : (
          item.headline
        )}
      </h2>
      <p className="mt-1 text-xs text-muted">
        {item.source} · <time dateTime={item.publishedAt}>{formatDateTime(item.publishedAt)}</time>
        {item.tickers.length > 0 && (
          <>
            {' · '}
            <span className="font-mono">{item.tickers.join(', ')}</span>
          </>
        )}
      </p>
      <div className="mt-3">
        <Classification item={item} />
      </div>
    </article>
  );
}

function Classification({ item }: { item: NewsFeedItem }) {
  if (item.status === 'RECEIVED') {
    return (
      <p className="text-xs text-muted">
        <span aria-hidden="true">◌ </span>Awaiting classification
      </p>
    );
  }
  if (item.status === 'UNCLASSIFIED' || !item.sentiment || !item.relevance || !item.eventType) {
    return (
      <p className="text-xs text-warning">
        <span aria-hidden="true">▲ </span>
        Not classified
        {item.unclassifiedReason && <>: {unclassifiedReasonText(item.unclassifiedReason)}</>}
      </p>
    );
  }
  const sentiment = sentimentDisplay(item.sentiment);
  return (
    <div className="space-y-2">
      <p className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs">
        <span className={`font-medium ${sentiment.className}`}>
          <span aria-hidden="true">{sentiment.icon} </span>
          {sentiment.label}
        </span>
        <span className="text-muted">
          Relevance <span className="text-text">{relevanceLabel(item.relevance)}</span>
        </span>
        <span className="rounded bg-surface-3 px-1.5 py-0.5 text-text">{eventTypeLabel(item.eventType)}</span>
      </p>
      {item.rationale && <Rationale text={item.rationale} sourceHref={safeHref(item.url)} />}
    </div>
  );
}

/** The model's reason for the labels, folded by default. Model output: rendered as text, always with the AI marker. */
function Rationale({ text, sourceHref }: { text: string; sourceHref: string | undefined }) {
  const [open, setOpen] = useState(false);
  const id = useId();
  return (
    <div>
      <button
        type="button"
        aria-expanded={open}
        aria-controls={id}
        onClick={() => setOpen((o) => !o)}
        className="text-xs text-muted hover:text-text"
      >
        <span aria-hidden="true">{open ? '▾ ' : '▸ '}</span>
        Why this classification?
      </button>
      {open && (
        <div id={id} className="mt-2 space-y-2 border-l-2 border-accent-muted pl-3">
          <AiMarker state={{ kind: 'draft' }} sourcesHref={sourceHref} />
          <p className="text-sm text-text">{text}</p>
        </div>
      )}
    </div>
  );
}

type RunState =
  | { status: 'idle' }
  | { status: 'running' }
  | { status: 'done'; run: JobRun }
  | { status: 'failed'; error: ApiError };

const STEP_STATUS: Record<JobRun['status'], { icon: string; className: string }> = {
  SUCCEEDED: { icon: '✓', className: 'text-up' },
  SKIPPED: { icon: '↷', className: 'text-warning' },
  FAILED: { icon: '✕', className: 'text-down' },
  TIMED_OUT: { icon: '◷', className: 'text-warning' },
};

/**
 * A used-up LLM budget arrives as a SKIPPED step whose message names the budget (JobService turns
 * UsageLimitExceededException and budget deferrals into SKIPPED, ADR 0016 / 0021), not as an HTTP error.
 */
const BUDGET_MESSAGE = /budget/i;

function budgetUsedUp(run: JobRun): boolean {
  return run.steps.some((step) => step.status === 'SKIPPED' && BUDGET_MESSAGE.test(step.message));
}

function BudgetNotice({ reference }: { reference?: string }) {
  return (
    <div role="alert" className="mt-3 rounded border border-warning/40 bg-warning/10 px-4 py-3">
      <p className="font-medium text-warning">
        <span aria-hidden="true">▲ </span>Daily AI budget used up
      </p>
      <p className="mt-1 text-text">New items stay unclassified until the budget resets at 00:00 UTC.</p>
      {reference && (
        <p className="mt-2 text-muted">
          Reference: <span className="font-mono">{reference}</span>
        </p>
      )}
    </div>
  );
}

/**
 * "Run news now" (ADR 0007): runs the news-ingest job through the admin endpoint. The request returns after ingest,
 * publish and sweep; classification of new items continues in the background through Kafka, and the daily summaries
 * are made by the scheduled run. A used-up budget shows as a notice, from the step statuses (the endpoint answers
 * 200) or, should an endpoint ever refuse with it, from a 429.
 */
function RunNewsPanel({ onFinished }: { onFinished: () => void }) {
  const [state, setState] = useState<RunState>({ status: 'idle' });

  async function run() {
    setState({ status: 'running' });
    try {
      const result = await apiPost<JobRun>('/api/admin/jobs/news-ingest/run');
      setState({ status: 'done', run: result });
    } catch (error) {
      setState({
        status: 'failed',
        error: error instanceof ApiError ? error : new ApiError(0, 'Unexpected error', String(error), 'n/a'),
      });
    }
    onFinished();
  }

  return (
    <section aria-label="News pipeline" className="rounded border border-line bg-surface-1 p-4 text-sm">
      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          onClick={run}
          disabled={state.status === 'running'}
          className="rounded border border-accent/60 px-3 py-1 font-medium text-accent hover:bg-accent-muted/40 disabled:cursor-wait disabled:opacity-60"
        >
          {state.status === 'running' ? 'Running…' : 'Run news now'}
        </button>
        <p className="text-xs text-muted">Fetches news for held companies and classifies it with the AI model.</p>
      </div>
      {state.status === 'running' && (
        <p role="status" className="mt-3 animate-pulse text-muted">
          Fetching news. This can take a minute.
        </p>
      )}
      {state.status === 'failed' &&
        (state.error.status === 429 ? (
          <BudgetNotice reference={state.error.correlationId} />
        ) : (
          <div className="mt-3">
            <ErrorPanel error={state.error} />
          </div>
        ))}
      {state.status === 'done' && budgetUsedUp(state.run) && <BudgetNotice />}
      {state.status === 'done' && <RunResult run={state.run} />}
    </section>
  );
}

function RunResult({ run }: { run: JobRun }) {
  const overall = STEP_STATUS[run.status];
  return (
    <div role="status" className="mt-3 space-y-2">
      <p className={`font-medium ${overall.className}`}>
        <span aria-hidden="true">{overall.icon} </span>
        Run {run.status.toLowerCase().replace('_', ' ')} · {formatDateTime(run.endedAt)}
      </p>
      <ul className="space-y-1 text-xs">
        {run.steps.map((step) => {
          const s = STEP_STATUS[step.status];
          return (
            <li key={step.name}>
              <span className={s.className}>
                <span aria-hidden="true">{s.icon} </span>
                {step.name}: {step.status.toLowerCase().replace('_', ' ')}
              </span>
              {step.message && <span className="text-muted"> ({step.message})</span>}
            </li>
          );
        })}
      </ul>
      <p className="text-xs text-muted">
        New items are classified in the background; items marked "Awaiting classification" update on refresh.
      </p>
    </div>
  );
}
