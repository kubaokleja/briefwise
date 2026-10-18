// The only place that talks HTTP. Every request carries an X-Correlation-ID, so an error in the UI can be matched
// to the server log line (the backend echoes the ID and puts it in the MDC). Later: CSRF header and cookie auth (M4).

export const CORRELATION_HEADER = 'X-Correlation-ID';

/**
 * A failed API call. `status` 0 means the server was not reached (network error, app not running).
 * `correlationId` is shown in the UI as a reference for the logs.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly title: string;
  readonly detail: string | undefined;
  readonly correlationId: string;

  constructor(status: number, title: string, detail: string | undefined, correlationId: string) {
    super(detail ? `${title}: ${detail}` : title);
    this.name = 'ApiError';
    this.status = status;
    this.title = title;
    this.detail = detail;
    this.correlationId = correlationId;
  }
}

/** RFC 9457 problem response, as produced by the backend's ApiExceptionHandler. */
interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
}

export async function apiGet<T>(path: string, init: { signal?: AbortSignal } = {}): Promise<T> {
  const correlationId = crypto.randomUUID();

  let response: Response;
  try {
    response = await fetch(path, {
      method: 'GET',
      headers: { Accept: 'application/json', [CORRELATION_HEADER]: correlationId },
      signal: init.signal,
    });
  } catch (error) {
    // Cancelling is not a failure; let the caller see the AbortError and ignore it.
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error;
    }
    throw new ApiError(0, 'Server not reachable', 'Is the app running?', correlationId);
  }

  // The server may replace a malformed ID with its own; prefer the one it actually logged.
  const loggedId = response.headers.get(CORRELATION_HEADER) ?? correlationId;

  if (!response.ok) {
    const problem = await readProblem(response);
    throw new ApiError(
      response.status,
      problem?.title ?? (response.statusText || 'Request failed'),
      problem?.detail,
      loggedId,
    );
  }
  return (await response.json()) as T;
}

async function readProblem(response: Response): Promise<ProblemDetail | undefined> {
  const contentType = response.headers.get('Content-Type') ?? '';
  if (!contentType.includes('json')) {
    return undefined;
  }
  try {
    return (await response.json()) as ProblemDetail;
  } catch {
    return undefined;
  }
}
