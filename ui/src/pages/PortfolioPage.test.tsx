import { render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
import type { NewsSummary, PortfolioDailyMove } from '../api/types';
import { problem, stubApi } from '../test/stubApi';
import { PortfolioPage } from './PortfolioPage';

const ID = 'b0000000-0000-4000-8000-000000000002';
const BASE = `/api/portfolios/${ID}`;

const PORTFOLIO = {
  id: ID,
  clientId: 'c1',
  name: 'Core Balanced',
  riskProfile: 'BALANCED',
  positions: [
    { ticker: 'AAPL', quantity: 800, weight: 0.15 },
    { ticker: 'SPY', quantity: 500, weight: 0.3 },
  ],
};

const MOVE: PortfolioDailyMove = {
  portfolioId: ID,
  asOf: '2026-10-27',
  changePercent: 0.375,
  pricedWeight: 0.15,
  positions: [
    { ticker: 'AAPL', weight: 0.15, date: '2026-10-27', close: 102.5, changePercent: 2.5, contribution: 0.375 },
  ],
  unpriced: ['SPY'],
};

const SUMMARY: NewsSummary = {
  portfolioId: ID,
  summaryDate: '2026-10-28',
  generatedAt: '2026-10-28T05:10:00Z',
  points: [
    {
      text: 'AAPL: Apple reported revenue below estimates.',
      citations: [{ type: 'NEWS', sourceId: 'n1', locator: 'https://news.example.com/a', excerpt: 'Apple misses' }],
    },
  ],
  disclaimer: 'This is not investment advice.',
  aiGenerated: true,
  fallback: false,
  empty: false,
  model: 'claude-haiku-5-5',
  promptVersion: 'v1',
  violations: [],
};

function renderPage(routes: Record<string, () => Response>) {
  stubApi({ [BASE]: () => Response.json(PORTFOLIO), ...routes });
  return render(
    <MemoryRouter initialEntries={[`/portfolios/${ID}`]}>
      <Routes>
        <Route path="/portfolios/:portfolioId" element={<PortfolioPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

const withSummary = (summary: () => Response) => ({
  [`${BASE}/daily-move`]: () => Response.json(MOVE),
  [`${BASE}/news/summary`]: summary,
});

describe('PortfolioPage', () => {
  it('shows the weighted daily move, its coverage and the unpriced positions', async () => {
    renderPage(withSummary(() => Response.json(SUMMARY)));

    expect(await screen.findByRole('heading', { name: 'Core Balanced' })).toBeInTheDocument();
    const section = screen.getByRole('region', { name: 'Positions and daily move' });
    expect(await within(section).findByText(/covers/)).toHaveTextContent('15.00%');
    expect(within(section).getByRole('row', { name: /AAPL/ })).toHaveTextContent('+2.50%');
    expect(within(section).getByRole('row', { name: /SPY/ })).toHaveTextContent('No price');
  });

  it('shows the AI summary with its marker, numbered sources and the disclaimer', async () => {
    renderPage(withSummary(() => Response.json(SUMMARY)));

    const section = await screen.findByRole('region', { name: 'Daily news summary' });
    expect(await within(section).findByRole('note', { name: 'AI draft, not reviewed' })).toBeInTheDocument();
    expect(within(section).getByText('claude-haiku-5-5 · prompt v1')).toBeInTheDocument();
    expect(within(section).getByRole('link', { name: '[1]' })).toHaveAttribute('href', '#source-1');
    expect(within(section).getByRole('link', { name: 'Apple misses' })).toHaveAttribute(
      'href',
      'https://news.example.com/a',
    );
    expect(within(section).getByText('This is not investment advice.')).toBeInTheDocument();
  });

  it('marks a template summary as built without AI and shows open violations', async () => {
    const fallback: NewsSummary = {
      ...SUMMARY,
      aiGenerated: false,
      fallback: true,
      model: null,
      promptVersion: null,
      violations: [{ rule: 'NO_RECOMMENDATION', offendingText: 'buy now' }],
    };
    renderPage(withSummary(() => Response.json(fallback)));

    const section = await screen.findByRole('region', { name: 'Daily news summary' });
    expect(
      await within(section).findByRole('note', { name: 'Built from a template (AI unavailable)' }),
    ).toBeInTheDocument();
    expect(within(section).getByRole('alert')).toHaveTextContent('NO_RECOMMENDATION');
    expect(within(section).getByRole('alert')).toHaveTextContent('buy now');
  });

  it('shows "no summary yet" for a 404', async () => {
    renderPage(withSummary(() => problem(404, 'Not Found')));

    expect(await screen.findByText('No summary yet')).toBeInTheDocument();
  });

  it('shows an empty summary as "no relevant news"', async () => {
    renderPage(withSummary(() => Response.json({ ...SUMMARY, empty: true, aiGenerated: false, points: [] })));

    expect(await screen.findByText('No relevant news today')).toBeInTheDocument();
  });

  it('shows "not found" for an unknown portfolio', async () => {
    renderPage({ [BASE]: () => problem(404, 'Not Found') });

    expect(await screen.findByText('Portfolio not found')).toBeInTheDocument();
  });
});
