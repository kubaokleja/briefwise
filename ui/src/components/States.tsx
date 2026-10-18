import type { ApiError } from '../api/client';

// The three states every screen must handle (DESIGN 15). Domain-specific states (UNCLASSIFIED news, budget
// exceeded, ...) build on these in later milestones.

export function Loading({ label = 'Loading' }: { label?: string }) {
  return (
    <p role="status" className="animate-pulse py-6 text-sm text-muted">
      {label}…
    </p>
  );
}

export function Empty({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="rounded border border-dashed border-line px-4 py-6 text-sm">
      <p className="text-text">{title}</p>
      {hint && <p className="mt-1 text-muted">{hint}</p>}
    </div>
  );
}

/** Shows what failed and the correlation ID, so the matching server log line can be found. */
export function ErrorPanel({ error, onRetry }: { error: ApiError; onRetry?: () => void }) {
  return (
    <div role="alert" className="rounded border border-down/40 bg-down/10 px-4 py-3 text-sm">
      <p className="font-medium text-down">
        <span aria-hidden="true">✕ </span>
        {error.title}
      </p>
      {error.detail && <p className="mt-1 text-text">{error.detail}</p>}
      <p className="mt-2 text-muted">
        Reference: <span className="font-mono">{error.correlationId}</span>
        {error.status > 0 && <> · HTTP {error.status}</>}
      </p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-3 rounded border border-line px-3 py-1 text-text hover:bg-surface-3"
        >
          Retry
        </button>
      )}
    </div>
  );
}
