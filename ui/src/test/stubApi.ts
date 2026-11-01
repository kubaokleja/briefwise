import { vi } from 'vitest';

/**
 * Answers each request with its own response, like the real backend would. Keys are the exact URL (GET) or
 * `"POST <url>"`. An unexpected request fails the test, so a wrong URL is caught.
 */
export function stubApi(routes: Record<string, () => Response | Promise<Response>>) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
    const route = routes[method === 'GET' ? url : `${method} ${url}`];
    if (!route) {
      throw new Error(`Unexpected request: ${method} ${url}`);
    }
    return route();
  });
}

/** An RFC 9457 problem response, as the backend's ApiExceptionHandler writes it. */
export function problem(status: number, title: string, detail?: string, correlationId = 'test-ref-1'): Response {
  return new Response(JSON.stringify({ title, status, detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json', 'X-Correlation-ID': correlationId },
  });
}
