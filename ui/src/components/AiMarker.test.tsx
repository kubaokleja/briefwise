import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { AiMarker, aiMarkerText } from './AiMarker';

describe('aiMarkerText', () => {
  it('uses the wording fixed in ADR 0007 for every state', () => {
    expect(aiMarkerText({ kind: 'draft' })).toBe('AI draft, not reviewed');
    expect(aiMarkerText({ kind: 'fallback' })).toBe('Built from a template (AI unavailable)');
    expect(aiMarkerText({ kind: 'approved', reviewer: 'rm.anna', date: '2026-10-28' })).toBe(
      'AI-generated · reviewed and approved by rm.anna, 28 Oct 2026',
    );
    expect(aiMarkerText({ kind: 'rejected', reviewer: 'rm.anna' })).toBe('AI draft, rejected by rm.anna');
  });
});

describe('AiMarker', () => {
  it('is announced as a note and shows provenance and a link to the sources', () => {
    render(<AiMarker state={{ kind: 'draft' }} model="claude-haiku-5-5" promptVersion="v1" sourcesHref="#sources" />);

    expect(screen.getByRole('note', { name: 'AI draft, not reviewed' })).toBeInTheDocument();
    expect(screen.getByText('claude-haiku-5-5 · prompt v1')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Sources' })).toHaveAttribute('href', '#sources');
  });

  it('shows no provenance when the model is unknown', () => {
    render(<AiMarker state={{ kind: 'fallback' }} model={null} promptVersion={null} />);

    const note = screen.getByRole('note', { name: 'Built from a template (AI unavailable)' });
    expect(note).not.toHaveTextContent('prompt');
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });
});
