import { useCallback, useEffect, useState } from 'react';
import { ApiError, apiGet } from './client';

export type ApiState<T> =
  | { status: 'loading' }
  | { status: 'success'; data: T }
  | { status: 'error'; error: ApiError };

type Settled<T> = Exclude<ApiState<T>, { status: 'loading' }>;

/**
 * Loads `path` when the component mounts (and again when `path` changes or `reload()` is called).
 *
 * "Loading" is derived rather than stored: the hook remembers which request its last result belongs to, and any
 * other request is still loading. That avoids setting state synchronously inside the effect, which React's lint
 * rules flag because it causes an extra render. Leaving the screen aborts the request.
 */
export function useApi<T>(path: string): ApiState<T> & { reload: () => void } {
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<{ key: string; state: Settled<T> } | null>(null);
  const key = `${path}#${attempt}`;

  useEffect(() => {
    const controller = new AbortController();
    apiGet<T>(path, { signal: controller.signal }).then(
      (data) => setResult({ key, state: { status: 'success', data } }),
      (error: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        const apiError =
          error instanceof ApiError ? error : new ApiError(0, 'Unexpected error', String(error), 'n/a');
        setResult({ key, state: { status: 'error', error: apiError } });
      },
    );
    return () => controller.abort();
  }, [path, key]);

  const reload = useCallback(() => setAttempt((n) => n + 1), []);

  if (result === null || result.key !== key) {
    return { status: 'loading', reload };
  }
  return { ...result.state, reload };
}
