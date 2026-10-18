import { describe, expect, it, vi } from 'vitest';
import { ApiError, apiGet, CORRELATION_HEADER } from './client';

function stubFetch(response: Response | Error) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async () => {
    if (response instanceof Error) {
      throw response;
    }
    return response;
  });
}

describe('apiGet', () => {
  it('sends a UUID correlation ID and returns the JSON body', async () => {
    const fetchMock = stubFetch(Response.json([{ id: 'p1' }]));

    const data = await apiGet<{ id: string }[]>('/api/portfolios');

    expect(data).toEqual([{ id: 'p1' }]);
    const headers = new Headers(fetchMock.mock.calls[0]?.[1]?.headers);
    expect(headers.get(CORRELATION_HEADER)).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('turns a ProblemDetail into an ApiError carrying the server-side correlation ID', async () => {
    stubFetch(
      new Response(JSON.stringify({ title: 'Not Found', status: 404, detail: 'Portfolio x not found' }), {
        status: 404,
        headers: { 'Content-Type': 'application/problem+json', [CORRELATION_HEADER]: 'server-id-1' },
      }),
    );

    const error = await apiGet('/api/portfolios/x').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 404,
      title: 'Not Found',
      detail: 'Portfolio x not found',
      correlationId: 'server-id-1',
    });
  });

  it('falls back to the status text when the body is not JSON', async () => {
    stubFetch(new Response('upstream down', { status: 502, statusText: 'Bad Gateway' }));

    await expect(apiGet('/api/portfolios')).rejects.toMatchObject({ status: 502, title: 'Bad Gateway' });
  });

  it('reports an unreachable server as status 0', async () => {
    stubFetch(new TypeError('Failed to fetch'));

    await expect(apiGet('/api/portfolios')).rejects.toMatchObject({ status: 0, title: 'Server not reachable' });
  });
});
