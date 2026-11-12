import { api } from './client';
import type {
  ActiveMandateTelemetry,
  AgGridAiQueryResponse,
  ApproveResponse,
  AuditEvent,
  CapturePayPalOrderResponse,
  Channel3SearchResponse,
  CreatePayPalOrderRequest,
  CreatePayPalOrderResponse,
  CreateWatchRequest,
  DemoCaptureResponse,
  EvaluationRequest,
  EvaluationResponse,
  GuardianDecision,
  HealthResponse,
  HuntWatchResponse,
  HuntedOffer,
  InfoResponse,
  InstantBuyRequest,
  InstantBuyResponse,
  LoginRequest,
  Mandate,
  MarketplaceResponse,
  PayPalConfigResponse,
  PayPalOrderVerificationResult,
  ParsedWatchBlueprint,
  PauseWatchResponse,
  PurchaseActivityItem,
  PurchaseIntent,
  RedTeamAttackResponse,
  RedTeamAttackType,
  RevokeWatchResponse,
  RuntimeGuardrailSettings,
  SignupRequest,
  SubmitOfferRequest,
  SubmitOfferResponse,
  UpdateMandateRequest,
  UpdateProfileRequest,
  UserProfile,
  Watch,
} from './types';

/** One function per backend endpoint — pages never call fetch directly. */

export const healthApi = {
  check: (): Promise<HealthResponse> => api.get<HealthResponse>('/api/health'),
};

export const infoApi = {
  get: (): Promise<InfoResponse> => api.get<InfoResponse>('/api/info'),
};

export const watchesApi = {
  list: (): Promise<Watch[]> => api.get<Watch[]>('/api/watches'),
  create: (request: CreateWatchRequest): Promise<Watch> =>
    api.post<Watch>('/api/watches', request),
  get: (watchId: string): Promise<Watch> => api.get<Watch>(`/api/watches/${watchId}`),
  mandate: (watchId: string): Promise<Mandate> =>
    api.get<Mandate>(`/api/watches/${watchId}/mandate`),
  intents: (watchId: string): Promise<PurchaseIntent[]> =>
    api.get<PurchaseIntent[]>(`/api/watches/${watchId}/purchase-intents`),
  pause: (watchId: string): Promise<PauseWatchResponse> =>
    api.post<PauseWatchResponse>(`/api/watches/${watchId}/pause`),
  revoke: (watchId: string): Promise<RevokeWatchResponse> =>
    api.post<RevokeWatchResponse>(`/api/watches/${watchId}/revoke`),
  killSwitch: (): Promise<{ status: string; revokedWatches: number; revokedMandates: number; message: string }> =>
    api.post('/api/watches/kill-switch'),
  submitOffer: (watchId: string, request: SubmitOfferRequest): Promise<SubmitOfferResponse> =>
    api.post<SubmitOfferResponse>(`/api/watches/${watchId}/offers`, request),
  hunt: (watchId: string, simulateDrop = false): Promise<HuntWatchResponse> =>
    api.post<HuntWatchResponse>(
      simulateDrop ? `/api/watches/${watchId}/hunt?simulateDrop=true` : `/api/watches/${watchId}/hunt`,
    ),
  huntNow: (): Promise<{ status: string; watchesEvaluated: number; message: string }> =>
    api.post('/api/watches/hunt-now'),
};

export const mandatesApi = {
  active: (): Promise<ActiveMandateTelemetry> =>
    api.get<ActiveMandateTelemetry>('/api/mandates/active'),
  connectPayPal: (mandateId: string): Promise<{
    mandateId: string;
    setupTokenId: string;
    status: string;
    approvalUrl: string;
    message: string;
  }> => api.post(`/api/mandates/${mandateId}/connect-paypal`),
  completePayPal: (mandateId: string, setupTokenId: string): Promise<{
    mandateId: string;
    vaultConnected?: boolean;
    maskedVaultToken?: string;
    paypalPayerEmail: string;
    status: string;
    message: string;
  }> => api.post(`/api/mandates/${mandateId}/complete-paypal`, { setupTokenId }),
  killSwitch: (mandateId: string): Promise<{
    status: string;
    revokedMandates: number;
    pausedWatches: number;
    releasedReservations: number;
    message: string;
  }> => api.post(`/api/mandates/${mandateId}/kill-switch`),
  restore: (mandateId: string): Promise<{
    status: string;
    restoredMandates: number;
    reactivatedWatches: number;
    message: string;
  }> => api.post(`/api/mandates/${mandateId}/restore`),
  update: (mandateId: string, request: UpdateMandateRequest): Promise<ActiveMandateTelemetry> =>
    api.put<ActiveMandateTelemetry>(`/api/mandates/${mandateId}`, request),
  disconnectPayPal: (): Promise<ActiveMandateTelemetry> =>
    api.post<ActiveMandateTelemetry>('/api/mandates/disconnect-paypal'),
  resetReservations: (): Promise<{ status: string; releasedCount: number; message: string }> =>
    api.post('/api/mandates/reset-reservations'),
};

export const userApi = {
  getProfile: (): Promise<UserProfile> => api.get<UserProfile>('/api/user/profile'),
  login: (request: LoginRequest): Promise<UserProfile> =>
    api.post<UserProfile>('/api/user/login', request),
  signup: (request: SignupRequest): Promise<UserProfile> =>
    api.post<UserProfile>('/api/user/signup', request),
  updateProfile: (request: UpdateProfileRequest): Promise<UserProfile> =>
    api.put<UserProfile>('/api/user/profile', request),
  resetWallet: (): Promise<UserProfile> =>
    api.post<UserProfile>('/api/user/reset-wallet'),
  disconnectPayPal: (): Promise<UserProfile> =>
    api.post<UserProfile>('/api/user/disconnect-paypal'),
  logout: (): Promise<UserProfile> =>
    api.post<UserProfile>('/api/user/logout'),
};

export const aiApi = {
  parseWatch: (prompt: string): Promise<ParsedWatchBlueprint> =>
    api.post<ParsedWatchBlueprint>('/api/ai/parse-watch', { prompt }),
  gridQuery: (query: string, view: 'offers' | 'intents'): Promise<AgGridAiQueryResponse> =>
    api.post<AgGridAiQueryResponse>('/api/ai/grid-query', { query, view }),
};

export interface MarketplaceQueryParams {
  q?: string;
  category?: string;
  minPrice?: number;
  maxPrice?: number;
  sort?: 'savings' | 'price_asc' | 'price_desc' | 'best_match';
  limit?: number;
}

export const catalogApi = {
  search: (q: string, limit = 8, category?: string): Promise<Channel3SearchResponse> => {
    const query = new URLSearchParams({ q, limit: String(limit) });
    if (category && category !== 'All Categories') query.set('category', category);
    return api.get<Channel3SearchResponse>(`/api/catalog/search?${query.toString()}`);
  },
  marketplace: (params: MarketplaceQueryParams = {}): Promise<MarketplaceResponse> => {
    const query = new URLSearchParams();
    if (params.q && params.q.trim()) query.set('q', params.q.trim());
    if (params.category && params.category !== 'All Categories') query.set('category', params.category);
    if (params.minPrice !== undefined) query.set('minPrice', String(params.minPrice));
    if (params.maxPrice !== undefined) query.set('maxPrice', String(params.maxPrice));
    if (params.sort) query.set('sort', params.sort);
    if (params.limit !== undefined) query.set('limit', String(params.limit));
    const suffix = query.toString();
    return api.get<MarketplaceResponse>(`/api/catalog/marketplace${suffix ? `?${suffix}` : ''}`);
  },
  lookup: (url: string): Promise<Channel3SearchResponse> =>
    api.post<Channel3SearchResponse>('/api/catalog/lookup', { url }),
};

export const purchasesApi = {
  listAllIntents: (): Promise<PurchaseIntent[]> => api.get<PurchaseIntent[]>('/api/purchase-intents'),
  listAllOffers: (): Promise<HuntedOffer[]> => api.get<HuntedOffer[]>('/api/offers'),
  activity: (): Promise<PurchaseActivityItem[]> =>
    api.get<PurchaseActivityItem[]>('/api/purchases/activity'),
  approveHold: (intentId: string): Promise<DemoCaptureResponse> =>
    api.post<DemoCaptureResponse>(`/api/purchases/${intentId}/approve-hold`),
  rejectHold: (intentId: string): Promise<DemoCaptureResponse> =>
    api.post<DemoCaptureResponse>(`/api/purchases/${intentId}/reject-hold`),
  syncPayPal: (intentId: string): Promise<DemoCaptureResponse> =>
    api.post<DemoCaptureResponse>(`/api/purchases/${intentId}/sync-paypal`),
  instantBuy: (request: InstantBuyRequest): Promise<InstantBuyResponse> =>
    api.post<InstantBuyResponse>('/api/purchases/instant-buy', request),
};

export const paypalApi = {
  config: (): Promise<PayPalConfigResponse> => api.get<PayPalConfigResponse>('/api/paypal/config'),
  startLogin: (): Promise<{ setupTokenId: string; approvalUrl: string; status: string }> =>
    api.post('/api/paypal/start-login'),
  vaultLoginCallback: (setupTokenId: string): Promise<ActiveMandateTelemetry> =>
    api.post<ActiveMandateTelemetry>('/api/paypal/vault-login-callback', { setupTokenId }),
  oauthCallback: (code: string): Promise<ActiveMandateTelemetry> =>
    api.post<ActiveMandateTelemetry>('/api/paypal/oauth-callback', { code }),
  createOrder: (request: CreatePayPalOrderRequest): Promise<CreatePayPalOrderResponse> =>
    api.post<CreatePayPalOrderResponse>('/api/paypal/create-order', request),
  captureOrder: (paypalOrderId: string, mockResponseCode?: string): Promise<CapturePayPalOrderResponse> =>
    api.post<CapturePayPalOrderResponse>(
      `/api/paypal/capture-order/${encodeURIComponent(paypalOrderId)}`,
      mockResponseCode ? { mockResponseCode } : undefined,
    ),
  verifyOrder: (paypalOrderId: string): Promise<PayPalOrderVerificationResult> =>
    api.get<PayPalOrderVerificationResult>(`/api/paypal/orders/${encodeURIComponent(paypalOrderId)}/verify`),
};

export const demoApi = {
  redTeamAttack: (attackType: RedTeamAttackType, watchId?: string): Promise<RedTeamAttackResponse> =>
    api.post<RedTeamAttackResponse>('/api/demo/red-team-attack', {
      attackType,
      ...(watchId ? { watchId } : {}),
    }),
};

export const intentsApi = {
  get: (intentId: string): Promise<PurchaseIntent> =>
    api.get<PurchaseIntent>(`/api/purchase-intents/${intentId}`),
  decision: (intentId: string): Promise<GuardianDecision> =>
    api.get<GuardianDecision>(`/api/purchase-intents/${intentId}/guardian-decision`),
  approve: (intentId: string): Promise<ApproveResponse> =>
    api.post<ApproveResponse>(`/api/purchase-intents/${intentId}/approve`),
  demoCapture: (intentId: string): Promise<DemoCaptureResponse> =>
    api.post<DemoCaptureResponse>(`/api/purchase-intents/${intentId}/demo-capture`),
  capture: (intentId: string): Promise<DemoCaptureResponse> =>
    api.post<DemoCaptureResponse>(`/api/purchase-intents/${intentId}/capture`),
};

export const guardianApi = {
  evaluate: (request: EvaluationRequest): Promise<EvaluationResponse> =>
    api.post<EvaluationResponse>('/api/guardian/evaluations', request),
  getRuntimeSettings: (): Promise<RuntimeGuardrailSettings> =>
    api.get<RuntimeGuardrailSettings>('/api/guardian/runtime-settings'),
  updateRuntimeSettings: (settings: RuntimeGuardrailSettings): Promise<RuntimeGuardrailSettings> =>
    api.put<RuntimeGuardrailSettings>('/api/guardian/runtime-settings', settings),
};

export interface AuditListParams {
  limit?: number;
  eventType?: string;
  correlationId?: string;
}

export const auditApi = {
  list: (params: AuditListParams = {}): Promise<AuditEvent[]> => {
    const query = new URLSearchParams();
    if (params.limit !== undefined) query.set('limit', String(params.limit));
    if (params.eventType && params.eventType.trim().length > 0)
      query.set('eventType', params.eventType.trim());
    if (params.correlationId && params.correlationId.trim().length > 0)
      query.set('correlationId', params.correlationId.trim());
    const suffix = query.toString();
    return api.get<AuditEvent[]>(`/api/audit-events${suffix ? `?${suffix}` : ''}`);
  },
};
