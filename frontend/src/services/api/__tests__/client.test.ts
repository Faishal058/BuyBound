import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError, isApiError } from '../client';

function mockFetch(impl: () => Promise<unknown>) {
  vi.stubGlobal('fetch', vi.fn(impl));
}

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('api client', () => {
  it('returns parsed JSON on success and calls the configured base URL', async () => {
    const fetchMock = vi.fn(async () =>
      Promise.resolve({
        ok: true,
        status: 200,
        text: async () => '{"status":"UP"}',
      } as Response),
    );
    mockFetch(fetchMock);

    const result = await api.get<{ status: string }>('/api/health');

    expect(result).toEqual({ status: 'UP' });
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/health',
      expect.objectContaining({ headers: { 'Content-Type': 'application/json' } }),
    );
  });

  it('maps backend error envelopes to ApiError with the backend message', async () => {
    mockFetch(async () =>
      Promise.resolve({
        ok: false,
        status: 409,
        text: async () =>
          JSON.stringify({
            timestamp: '2026-10-12T00:00:00Z',
            status: 409,
            error: 'Conflict',
            message: 'Intent is not eligible for approval.',
            path: '/api/purchase-intents/abc/approve',
          }),
      } as Response),
    );

    const error = await api.post('/api/purchase-intents/abc/approve').catch((e: unknown) => e);

    expect(isApiError(error)).toBe(true);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(409);
    expect(apiError.message).toBe('Intent is not eligible for approval.');
  });

  it('falls back to a generic message when the error body is not JSON', async () => {
    mockFetch(async () =>
      Promise.resolve({
        ok: false,
        status: 500,
        text: async () => '<html>proxy error</html>',
      } as Response),
    );

    const error = await api.get('/api/watches').catch((e: unknown) => e);

    expect(isApiError(error)).toBe(true);
    expect((error as ApiError).message).toBe('Request failed with status 500.');
  });

  it('reports status 0 with a helpful message when the backend is unreachable', async () => {
    mockFetch(async () => Promise.reject(new TypeError('fetch failed')));

    const error = await api.get('/api/health').catch((e: unknown) => e);

    expect(isApiError(error)).toBe(true);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(0);
    expect(apiError.message).toMatch(/could not reach/i);
  });
});
