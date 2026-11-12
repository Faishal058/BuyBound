import type { BackendErrorBody } from './types';

/**
 * Typed error for API failures. `status` is the HTTP status code, or 0 when
 * the backend could not be reached at all. `message` is the backend's message
 * when it supplied one, otherwise a safe generic fallback.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly body: unknown;

  constructor(status: number, message: string, body?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

function baseUrl(): string {
  const raw = import.meta.env.VITE_API_BASE_URL as string | undefined;
  if (raw && raw.trim().length > 0) {
    const trimmed = raw.trim().replace(/\/+$/, '');
    if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
      return trimmed;
    }
    return `https://${trimmed}`;
  }
  return 'http://localhost:8080';
}

export function getApiBaseUrl(): string {
  return baseUrl();
}

function parseJson(text: string): unknown {
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return undefined;
  }
}

function errorMessage(status: number, body: unknown): string {
  if (body && typeof body === 'object') {
    const maybe = (body as BackendErrorBody).message;
    if (typeof maybe === 'string' && maybe.trim().length > 0) return maybe;
  }
  if (status === 0) return 'Could not reach the BuyBound API. Is the backend running?';
  return `Request failed with status ${status}.`;
}

let inMemorySessionToken: string | null = null;

export function setApiSessionToken(token: string | null | undefined): void {
  inMemorySessionToken = token && token.trim().length > 0 ? token.trim() : null;
}

function resolveSessionToken(): string | null {
  if (inMemorySessionToken) return inMemorySessionToken;
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem('buybound_auth_user');
    if (!raw) return null;
    const parsed = JSON.parse(raw) as { sessionToken?: string | null };
    if (parsed && typeof parsed.sessionToken === 'string' && parsed.sessionToken.trim().length > 0) {
      inMemorySessionToken = parsed.sessionToken.trim();
      return inMemorySessionToken;
    }
  } catch {
    // Ignore storage read errors
  }
  return null;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  const sessionToken = resolveSessionToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init?.headers as Record<string, string> | undefined),
  };
  if (sessionToken) {
    headers['X-BuyBound-Session'] = sessionToken;
  }

  try {
    response = await fetch(`${baseUrl()}${path}`, {
      ...init,
      credentials: 'include',
      headers,
    });
  } catch {
    throw new ApiError(0, errorMessage(0, undefined));
  }

  const headerToken = response.headers?.get?.('X-BuyBound-Session');
  if (headerToken !== null && headerToken !== undefined) {
    setApiSessionToken(headerToken.trim().length > 0 ? headerToken : null);
  }

  const text = await response.text();
  const body: unknown = text.length > 0 ? parseJson(text) : undefined;

  if (!response.ok) {
    throw new ApiError(response.status, errorMessage(response.status, body), body);
  }
  return body as T;
}

export const api = {
  get: <T>(path: string): Promise<T> => request<T>(path),
  post: <T>(path: string, payload?: unknown): Promise<T> =>
    request<T>(path, {
      method: 'POST',
      body: payload === undefined ? undefined : JSON.stringify(payload),
    }),
  put: <T>(path: string, payload?: unknown): Promise<T> =>
    request<T>(path, {
      method: 'PUT',
      body: payload === undefined ? undefined : JSON.stringify(payload),
    }),
};

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error;
  if (error instanceof Error) return new ApiError(0, error.message);
  return new ApiError(0, 'An unexpected error occurred.');
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}
