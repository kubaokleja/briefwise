import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { Portfolio } from '../api/types';
import { stubApi } from '../test/stubApi';
import { StatusPage } from './StatusPage';

const PORTFOLIO: Portfolio = {
  id: 'b0000000-0000-4000-8000-000000000002',
  clientId: 'a0000000-0000-4000-8000-000000000002',
  name: 'Core Balanced',
  riskProfile: 'BALANCED',
  positions: [
    { ticker: 'SPY', quantity: 100, weight: 0.35 },
    { ticker: 'AGG', quantity: 200, weight: 0.25 },
  ],
};

const healthUp = () => Response.json({ status: 'UP' });

describe('StatusPage', () => {
  it('shows the API as operational and lists portfolios with their largest position', async () => {
    stubApi({ '/actuator/health': healthUp, '/api/portfolios': () => Response.json([PORTFOLIO]) });

    render(<StatusPage />);

    expect(await screen.findByText('Operational')).toBeInTheDocument();
    expect(await screen.findByRole('cell', { name: 'Core Balanced' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Balanced' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'SPY 35.00%' })).toBeInTheDocument();
  });

  it('shows the empty state when there are no portfolios', async () => {
    stubApi({ '/actuator/health': healthUp, '/api/portfolios': () => Response.json([]) });

    render(<StatusPage />);

    expect(await screen.findByText('No portfolios yet')).toBeInTheDocument();
  });

  it('shows the error with its correlation ID so it can be found in the logs', async () => {
    stubApi({
      '/actuator/health': healthUp,
      '/api/portfolios': () =>
        new Response(JSON.stringify({ title: 'Internal Server Error', status: 500 }), {
          status: 500,
          headers: { 'Content-Type': 'application/problem+json', 'X-Correlation-ID': 'abc-123' },
        }),
    });

    render(<StatusPage />);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Internal Server Error');
    expect(alert).toHaveTextContent('Reference: abc-123');
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });
});
