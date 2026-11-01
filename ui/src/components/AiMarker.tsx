import { formatDate } from '../lib/format';

// Transparency marker for AI-generated text (EU AI Act Art. 50, DESIGN 15, ADR 0007). It is never removed: a reader
// must always see that a model wrote the text, whether a person reviewed it, and where its sources are. The wording is
// fixed by ADR 0007; change it there first.

export type AiMarkerState =
  | { kind: 'draft' }
  | { kind: 'fallback' }
  | { kind: 'approved'; reviewer: string; date: string }
  | { kind: 'rejected'; reviewer: string };

/** The marker sentence for a state, exactly as ADR 0007 words it. */
export function aiMarkerText(state: AiMarkerState): string {
  switch (state.kind) {
    case 'draft':
      return 'AI draft, not reviewed';
    case 'fallback':
      return 'Built from a template (AI unavailable)';
    case 'approved':
      return `AI-generated · reviewed and approved by ${state.reviewer}, ${formatDate(state.date)}`;
    case 'rejected':
      return `AI draft, rejected by ${state.reviewer}`;
  }
}

interface AiMarkerProps {
  state: AiMarkerState;
  /** Model id and prompt version, when the text came from a model; shown so a reader can trace the output. */
  model?: string | null;
  promptVersion?: string | null;
  /** Where the sources of the text are (an anchor on the same page or an article). */
  sourcesHref?: string;
}

export function AiMarker({ state, model, promptVersion, sourcesHref }: AiMarkerProps) {
  const text = aiMarkerText(state);
  const tone = state.kind === 'fallback' ? 'border-warning/50 text-warning' : 'border-accent/50 text-accent';
  const provenance = [model, promptVersion && `prompt ${promptVersion}`].filter(Boolean).join(' · ');
  return (
    <p role="note" aria-label={text} className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs">
      <span className={`rounded border px-1.5 py-0.5 font-medium ${tone}`}>
        <span aria-hidden="true">{state.kind === 'fallback' ? '▣ ' : '✦ '}</span>
        {text}
      </span>
      {provenance && <span className="font-mono text-muted">{provenance}</span>}
      {sourcesHref && (
        <a href={sourcesHref} className="text-muted underline hover:text-text">
          Sources
        </a>
      )}
    </p>
  );
}
