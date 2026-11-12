import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { WatchesPage } from '../pages/WatchesPage';
import { aiApi, purchasesApi, watchesApi } from '../../../services/api/endpoints';
import type { HuntedOffer, HuntWatchResponse, PurchaseIntent, Watch } from '../../../services/api/types';

vi.mock('../../../services/api/endpoints', async () => {
  const actual = await vi.importActual<typeof import('../../../services/api/endpoints')>(
    '../../../services/api/endpoints',
  );
  return {
    ...actual,
    watchesApi: {
      ...actual.watchesApi,
      list: vi.fn(),
      create: vi.fn(),
      hunt: vi.fn(),
    },
    purchasesApi: {
      ...actual.purchasesApi,
      listAllIntents: vi.fn(),
      listAllOffers: vi.fn(),
    },
    aiApi: {
      ...actual.aiApi,
      gridQuery: vi.fn(),
    },
  };
});

const SONY_WATCH: Watch = {
  id: 'watch-sony-001',
  productName: 'Sony WH-1000XM5 Wireless Headphones',
  sku: 'SONY-WH1000XM5',
  variant: 'Black',
  merchantId: 'bestbuy.com',
  mandateId: 'mandate-sony-001',
  mandateVersion: 1,
  currency: 'USD',
  targetPrice: '250.00',
  baselineMarketPrice: '398.00',
  status: 'ACTIVE',
  createdAt: '2026-11-12T10:00:00Z',
  updatedAt: '2026-11-12T10:00:00Z',
};

const CAPTURED_INTENT: PurchaseIntent = {
  id: 'intent-sony-001',
  watchId: SONY_WATCH.id,
  mandateId: SONY_WATCH.mandateId,
  mandateVersion: 1,
  status: 'CAPTURED',
  merchantId: 'bestbuy.com',
  canonicalSku: 'SONY-WH1000XM5',
  variant: 'Black',
  quantity: 1,
  currency: 'USD',
  maxTotal: '260.00',
  idempotencyKey: 'idem-sony-001',
  providerOrderId: '5O190127TN364715T',
  paypalCaptureId: '2GG279541U471931P',
  offerSnapshot: {
    merchantId: 'bestbuy.com',
    canonicalSku: 'SONY-WH1000XM5',
    variant: 'Black',
    quantity: 1,
    currency: 'USD',
    unitPrice: '254.00',
    totalPrice: '254.00',
    observedAt: '2026-11-12T15:10:12Z',
    source: 'channel3-drop-alert',
    evidenceHash: 'hash-sony-offer',
    baselinePrice: '398.00',
    sellerRating: 4.9,
    sellerReviewCount: 3180,
    verifiedSellerHistory: true,
    negativeRedFlagCount: 0,
    sellerRedFlagSummary: 'None — Verified Authentic Retailer',
    estimatedDelivery: 'Arriving in 2 Days',
  },
  createdAt: '2026-11-12T15:10:15Z',
  updatedAt: '2026-11-12T15:10:15Z',
};

const MOCK_OFFERS: HuntedOffer[] = [
  {
    id: 'offer-shady',
    watchId: SONY_WATCH.id,
    productTitle: SONY_WATCH.productName,
    merchantId: 'flash-drop-liquidator.net',
    canonicalSku: 'SONY-WH1000XM5-UNVERIFIED',
    currency: 'USD',
    offerPrice: '244.00',
    baselinePrice: '398.00',
    verifiedSavings: '154.00',
    source: 'channel3-drop-alert',
    aiExactSkuMatch: true,
    aiPromptInjectionDetected: false,
    aiSubscriptionTrapDetected: false,
    guardianDecision: 'BLOCK',
    reasonCodes: ['SELLER_NEGATIVE_REVIEW_RED_FLAG', 'SELLER_RATING_TOO_LOW'],
    ruleChecks: [],
    observedAt: '2026-11-12T15:10:12Z',
    createdAt: '2026-11-12T15:10:12Z',
    sellerRating: 2.7,
    sellerReviewCount: 17,
    verifiedSellerHistory: false,
    negativeRedFlagCount: 12,
    sellerRedFlagSummary: '12 counterfeit / non-delivery complaints in past 30 days',
    estimatedDelivery: 'Unverified 10–14 Days',
  },
  {
    id: 'offer-bestbuy',
    watchId: SONY_WATCH.id,
    intentId: CAPTURED_INTENT.id,
    productTitle: SONY_WATCH.productName,
    merchantId: 'bestbuy.com',
    canonicalSku: 'SONY-WH1000XM5',
    currency: 'USD',
    offerPrice: '254.00',
    baselinePrice: '398.00',
    verifiedSavings: '144.00',
    source: 'channel3-drop-alert',
    aiExactSkuMatch: true,
    aiPromptInjectionDetected: false,
    aiSubscriptionTrapDetected: false,
    guardianDecision: 'ALLOW',
    reasonCodes: ['WITHIN_DROP_GUARDRAIL_BAND', 'WITHIN_POLICY'],
    ruleChecks: [],
    observedAt: '2026-11-12T15:10:12Z',
    createdAt: '2026-11-12T15:10:12Z',
    sellerRating: 4.9,
    sellerReviewCount: 3180,
    verifiedSellerHistory: true,
    negativeRedFlagCount: 0,
    sellerRedFlagSummary: 'None — Verified Authentic Retailer',
    estimatedDelivery: 'Arriving in 2 Days',
  },
  {
    id: 'offer-reseller',
    watchId: SONY_WATCH.id,
    productTitle: SONY_WATCH.productName,
    merchantId: 'flightclub.com',
    canonicalSku: 'SONY-WH1000XM5-OVERCAP',
    currency: 'USD',
    offerPrice: '295.00',
    baselinePrice: '398.00',
    verifiedSavings: '103.00',
    source: 'channel3-drop-alert',
    aiExactSkuMatch: true,
    aiPromptInjectionDetected: false,
    aiSubscriptionTrapDetected: false,
    guardianDecision: 'BLOCK',
    reasonCodes: ['ITEM_PRICE_CAP_EXCEEDED'],
    ruleChecks: [],
    observedAt: '2026-11-12T15:10:12Z',
    createdAt: '2026-11-12T15:10:12Z',
    sellerRating: 4.9,
    sellerReviewCount: 2140,
    verifiedSellerHistory: true,
    negativeRedFlagCount: 0,
    sellerRedFlagSummary: 'None — Verified Consignment Reseller',
    estimatedDelivery: 'Arriving in 3–5 Days',
  },
];

const HUNT_RESPONSE: HuntWatchResponse = {
  watch: SONY_WATCH,
  query: SONY_WATCH.productName,
  baselineMarketPrice: '398.00',
  offersScanned: 3,
  evaluatedOffers: MOCK_OFFERS,
  selectedIntent: CAPTURED_INTENT,
  decision: {
    id: 'dec-sony-001',
    intentId: CAPTURED_INTENT.id,
    mandateId: SONY_WATCH.mandateId,
    mandateVersion: 1,
    decision: 'ALLOW',
    reasonCodes: ['WITHIN_DROP_GUARDRAIL_BAND', 'WITHIN_POLICY'],
    ruleChecks: [],
    policyVersion: '2.0',
    evidenceHash: 'hash-sony',
    evaluatedAt: '2026-11-12T15:10:13Z',
    expiresAt: '2026-11-12T15:25:13Z',
  },
  huntedAt: '2026-11-12T15:10:15Z',
  summary: 'Autonomous PayPal checkout completed at Best Buy for $254.00.',
};

describe('WatchesPage Dynamic Price Drop Simulation & Audit Grid', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    HTMLDialogElement.prototype.showModal = vi.fn(function (this: HTMLDialogElement) {
      this.open = true;
    });
    HTMLDialogElement.prototype.close = vi.fn(function (this: HTMLDialogElement) {
      this.open = false;
    });
    vi.mocked(watchesApi.list).mockResolvedValue([SONY_WATCH]);
    vi.mocked(purchasesApi.listAllIntents).mockResolvedValue([]);
    vi.mocked(purchasesApi.listAllOffers).mockResolvedValue([]);
    vi.mocked(watchesApi.hunt).mockResolvedValue(HUNT_RESPONSE);
    vi.mocked(aiApi.gridQuery).mockResolvedValue({
      filterModel: { sellerRating: { filterType: 'number', type: 'greaterThanOrEqual', filter: 4.5 } },
      sortModel: [],
      groupColumns: [],
      insightSummary: 'Filtered drop audit for 4.5★+ trusted sellers.',
    });
  });

  it('triggers dynamic Simulate Price Drop for the active watch, shows dynamic report timestamps, and renders AG Grid forensics', async () => {
    render(
      <MemoryRouter>
        <WatchesPage />
      </MemoryRouter>,
    );

    const simulateBtn = await screen.findByRole('button', {
      name: /Simulate Price Drop \(Sony WH-1000XM5 Wireless Headphones — \$250\.00 Target\)/i,
    });

    vi.mocked(purchasesApi.listAllIntents).mockResolvedValue([CAPTURED_INTENT]);
    vi.mocked(purchasesApi.listAllOffers).mockResolvedValue(MOCK_OFFERS);

    fireEvent.click(simulateBtn);

    await waitFor(() => {
      expect(watchesApi.hunt).toHaveBeenCalledWith(SONY_WATCH.id, true);
    });

    // Dynamic report without hardcoded 3:00 AM / 03:04:15 AM timestamps
    expect(screen.queryByText(/03:04:15 AM/)).not.toBeInTheDocument();
    expect(await screen.findByText(/Automated Log/i)).toBeTruthy();
    expect(screen.getByText(/Autonomous Order Placed — Arriving in 2 Days/i)).toBeTruthy();
    expect(screen.getByText(/Seller Verified: 4\.9★ \(3,180 Reviews · 0 Red Flags\)/i)).toBeTruthy();
    expect(screen.getByText(/Within \$250\.00–\$260\.00 Guardrail \(\$254\.00 Total\)/i)).toBeTruthy();
    expect(screen.getByText(/Settled via PayPal Sandbox/i)).toBeTruthy();

    // AG Grid Forensics Table cells + AI smart filter invocation
    expect(screen.getByText('Drop Forensics & Seller Audit Table')).toBeTruthy();
    expect((await screen.findAllByText(/12 Red Flags/i)).length).toBeGreaterThanOrEqual(2);

    const searchInput = screen.getByLabelText(/Filter offers using natural language/i);
    fireEvent.change(searchInput, {
      target: { value: 'Show approved offers with 4.5+ seller rating' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Filter Deals' }));

    await waitFor(() => {
      expect(aiApi.gridQuery).toHaveBeenCalledWith(
        'Show approved offers with 4.5+ seller rating',
        'offers',
      );
    });
  });
});
