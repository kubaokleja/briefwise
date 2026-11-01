import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { problem, stubApi } from '../test/stubApi';
import { DashboardPage } from './DashboardPage';

const ID = 'p1';
const PORTFOLIO = { id: ID, clientId: 'c1', name: 'Core Balanced', riskProfile: 'BALANCED', positions: [] };
const MOVE = { portfolioId: ID, asOf: '2026-10-27', changePercent: -0.42, pricedWeight: 1, positions: [], unpriced: [] };

function renderPage() {
  return render(
    <MemoryRouter>
      <DashboardPage />
    </MemoryRouter>,
  );
}

describe('DashboardPage', () => {
  it('shows a tile per portfolio with the daily move and the relevant news count', async () => {
    stubApi({
      '/api/portfolios': () => Response.json([PORTFOLIO]),
      [`/api/portfolios/${ID}/daily-move`]: () => Response.json(MOVE),
      [`/api/portfolios/${ID}/news/relevant`]: () => Response.json([{ id: 'n1' }, { id: 'n2' }]),
    });

    renderPage();

    const tile = await screen.findByRole('article', { name: 'Core Balanced' });
    expect(within(tile).getByRole('link', { name: 'Core Balanced' })).toHaveAttribute('href', `/portfolios/${ID}`);
    expect(await within(tile).findByText(/-0\.42%/)).toBeInTheDocument();
    expect(await within(tile).findByRole('link', { name: '2' })).toHaveAttribute('href', `/news?portfolio=${ID}`);
  });

  it('keeps a tile usable when one of its numbers fails', async () => {
    stubApi({
      '/api/portfolios': () => Response.json([PORTFOLIO]),
      [`/api/portfolios/${ID}/daily-move`]: () => problem(500, 'Internal Server Error'),
      [`/api/portfolios/${ID}/news/relevant`]: () => Response.json([]),
    });

    renderPage();

    const tile = await screen.findByRole('article', { name: 'Core Balanced' });
    expect(await within(tile).findByText('unavailable')).toBeInTheDocument();
    expect(await within(tile).findByRole('link', { name: '0' })).toBeInTheDocument();
  });

  it('shows the empty state without portfolios', async () => {
    stubApi({ '/api/portfolios': () => Response.json([]) });

    renderPage();

    expect(await screen.findByText('No portfolios yet')).toBeInTheDocument();
  });
});
