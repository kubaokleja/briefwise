import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { App } from './App';
import { stubApi } from './test/stubApi';

describe('App routing', () => {
  it('opens the screen of the URL and marks its navigation link as current', async () => {
    stubApi({ '/api/news?limit=50': () => Response.json([]), '/api/portfolios': () => Response.json([]) });

    render(
      <MemoryRouter initialEntries={['/news']}>
        <App />
      </MemoryRouter>,
    );

    expect(await screen.findByRole('heading', { name: 'News' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'News' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Dashboard' })).not.toHaveAttribute('aria-current');
  });

  it('shows "page not found" for an unknown URL', () => {
    render(
      <MemoryRouter initialEntries={['/nowhere']}>
        <App />
      </MemoryRouter>,
    );

    expect(screen.getByText('Page not found')).toBeInTheDocument();
  });
});
