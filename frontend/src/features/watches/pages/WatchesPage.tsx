import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { PayPalCheckoutModal, type CheckoutSession } from '../../../components/modals/PayPalCheckoutModal';
import { VerifiedReceiptModal } from '../../../components/modals/VerifiedReceiptModal';
import { OvernightActivityReportCard } from '../../../components/reports/OvernightActivityReportCard';
import { OvernightOfferAuditGrid } from '../../../components/tables/OvernightOfferAuditGrid';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { EmptyState } from '../../../components/ui/EmptyState';
import { ErrorBanner } from '../../../components/ui/ErrorBanner';
import { Spinner } from '../../../components/ui/Spinner';
import { toApiError } from '../../../services/api/client';
import { aiApi, catalogApi, paypalApi, purchasesApi, watchesApi } from '../../../services/api/endpoints';
import { formatStoreName, formatUsd } from '../../shared/meta';
import { handleProductImageError, resolveProductImageSrc } from '../../shared/productImages';
import type { HuntedOffer, HuntWatchResponse, PurchaseIntent, Watch } from '../../../services/api/types';

const EXAMPLE_PROMPTS = [
  'Sony WH-1000XM5 headphones under $250, max $260 from Best Buy or Macy\'s',
  'Apple AirPods Pro 2 USB-C under $189, max $210 at Best Buy or Target',
  'Dyson Airwrap Multi-Styler under $470, max $500 from Target or Walmart',
];

const HUNT_STEPS = [
  'Channel3 Drop Alert Received',
  'AI Authenticity & SKU Forensics',
  'Guardian Price & Seller Trust Check',
  'PayPal Sandbox Autonomous Settlement',
];

function formatCurrentClockTime(): string {
  return new Date().toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

export function WatchesPage() {
  const navigate = useNavigate();

  // Form states for creating a watch
  const [plainPrompt, setPlainPrompt] = useState(EXAMPLE_PROMPTS[0]);
  const [productLink, setProductLink] = useState('');
  const [productName, setProductName] = useState('Sony WH-1000XM5 headphones');
  const [buyBelowPrice, setBuyBelowPrice] = useState('250.00');
  const [maxItemLimit, setMaxItemLimit] = useState('260.00');
  const [trustedStores, setTrustedStores] = useState('bestbuy.com, macys.com, target.com');
  const [baselineRetail, setBaselineRetail] = useState<string>('399.99');
  const [skuHint, setSkuHint] = useState<string | undefined>(undefined);
  const [variantHint, setVariantHint] = useState<string | undefined>(undefined);
  const [aiNote, setAiNote] = useState<string | null>(null);

  const [autoFilling, setAutoFilling] = useState(false);
  const [lookingUpUrl, setLookingUpUrl] = useState(false);
  const [savingWatch, setSavingWatch] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [formSuccess, setFormSuccess] = useState<string | null>(null);

  // Active watches + purchase state
  const [watches, setWatches] = useState<Watch[]>([]);
  const [intents, setIntents] = useState<PurchaseIntent[]>([]);
  const [offers, setOffers] = useState<HuntedOffer[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // Live 4-step Check Stores flow
  const [huntingId, setHuntingId] = useState<string | null>(null);
  const [simulatingDrop, setSimulatingDrop] = useState(false);
  const [selectedSimWatchId, setSelectedSimWatchId] = useState<string>('');
  const [huntStepIndex, setHuntStepIndex] = useState<number>(-1);
  const [stepTimestamps, setStepTimestamps] = useState<string[]>([]);
  const [huntResult, setHuntResult] = useState<HuntWatchResponse | null>(null);
  const [huntError, setHuntError] = useState<string | null>(null);

  // Receipt modal & PayPal settlement checkout modal
  const [receiptTarget, setReceiptTarget] = useState<{ intent: PurchaseIntent; title: string } | null>(null);
  const [checkoutSession, setCheckoutSession] = useState<CheckoutSession | null>(null);
  const [openingCheckout, setOpeningCheckout] = useState(false);

  const loadData = useCallback(async () => {
    setLoadError(null);
    try {
      const [watchList, intentList, offerList] = await Promise.all([
        watchesApi.list(),
        purchasesApi.listAllIntents(),
        purchasesApi.listAllOffers().catch(() => []),
      ]);
      setWatches(watchList);
      setIntents(intentList);
      setOffers(offerList);
    } catch (err) {
      setLoadError(toApiError(err).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
    const onRefresh = () => void loadData();
    window.addEventListener('buybound:refresh', onRefresh);
    return () => window.removeEventListener('buybound:refresh', onRefresh);
  }, [loadData]);

  const capturedByWatchId = useMemo(() => {
    const map = new Map<string, PurchaseIntent>();
    for (const intent of intents) {
      if (intent.status === 'CAPTURED' && !map.has(intent.watchId)) {
        map.set(intent.watchId, intent);
      }
    }
    return map;
  }, [intents]);

  const heldByWatchId = useMemo(() => {
    const map = new Map<string, PurchaseIntent>();
    for (const intent of intents) {
      if (
        (intent.status === 'HELD_FOR_APPROVAL' || intent.status === 'AWAITING_APPROVAL') &&
        !map.has(intent.watchId)
      ) {
        map.set(intent.watchId, intent);
      }
    }
    return map;
  }, [intents]);

  const handleAutoFillRules = async () => {
    if (!plainPrompt.trim()) return;
    setAutoFilling(true);
    setFormError(null);
    setFormSuccess(null);
    try {
      const bp = await aiApi.parseWatch(plainPrompt.trim());
      setProductName(bp.title);
      setBuyBelowPrice(bp.targetPrice);
      setMaxItemLimit(bp.maxItemPrice);
      setTrustedStores(bp.allowedMerchants.join(', '));
      setSkuHint(bp.sku);
      setVariantHint(bp.variant);
      setAiNote(bp.aiExplanation);
      setFormSuccess('Rules auto-filled below. Review or adjust any limit before saving.');
    } catch (err) {
      setFormError(toApiError(err).message);
    } finally {
      setAutoFilling(false);
    }
  };

  const handleUrlLookup = async () => {
    if (!productLink.trim()) return;
    setLookingUpUrl(true);
    setFormError(null);
    setFormSuccess(null);
    try {
      const res = await catalogApi.lookup(productLink.trim());
      if (res.productTitle) setProductName(res.productTitle);
      if (res.baselineMarketPrice) setBaselineRetail(res.baselineMarketPrice);
      setFormSuccess(
        `Retail price checked: "${res.productTitle ?? 'Product'}" normally retails at ${formatUsd(res.baselineMarketPrice ?? '0')}.`,
      );
    } catch (err) {
      setFormError(toApiError(err).message);
    } finally {
      setLookingUpUrl(false);
    }
  };

  const handleSaveWatch = async (e: FormEvent) => {
    e.preventDefault();
    setFormError(null);
    setFormSuccess(null);

    const stores = trustedStores
      .split(',')
      .map((s) => s.trim().toLowerCase())
      .filter(Boolean);

    if (!productName.trim()) {
      setFormError('Please enter a product name.');
      return;
    }
    if (Number.isNaN(Number(buyBelowPrice)) || Number(buyBelowPrice) <= 0) {
      setFormError('Buy Below Price must be greater than $0.');
      return;
    }
    if (Number.isNaN(Number(maxItemLimit)) || Number(maxItemLimit) < Number(buyBelowPrice)) {
      setFormError('Max Per-Item Limit must be at least your Buy Below Price.');
      return;
    }
    if (stores.length === 0) {
      setFormError('Add at least one trusted store (for example: bestbuy.com, target.com).');
      return;
    }

    setSavingWatch(true);
    try {
      const created = await watchesApi.create({
        productName: productName.trim(),
        sku: skuHint,
        variant: variantHint,
        merchantId: stores[0],
        currency: 'USD',
        targetPrice: Number(buyBelowPrice).toFixed(2),
        maxUnitPrice: Number(maxItemLimit).toFixed(2),
        maxTotal: Number(maxItemLimit).toFixed(2),
        maxQuantity: 1,
        allowedMerchantIds: stores,
        maxWeeklySpend: '500.00',
        requiresHumanApproval: false,
        naturalLanguagePrompt: plainPrompt.trim() || undefined,
        referenceProductUrl: productLink.trim() || undefined,
        baselineMarketPrice: baselineRetail && Number(baselineRetail) > 0 ? Number(baselineRetail).toFixed(2) : undefined,
      });
      setFormSuccess(`Watch saved for "${created.productName}". Click "Check Stores Now" below to scan live prices.`);
      await loadData();
      window.dispatchEvent(new CustomEvent('buybound:refresh'));
    } catch (err) {
      setFormError(toApiError(err).message);
    } finally {
      setSavingWatch(false);
    }
  };

  const handleCheckStores = async (watch: Watch, simulateDrop = false) => {
    const startTs = formatCurrentClockTime();
    setHuntingId(watch.id);
    setHuntStepIndex(0);
    setStepTimestamps([startTs]);
    setHuntResult(null);
    setHuntError(null);

    const recordStep = (index: number) => {
      setHuntStepIndex(index);
      const ts = formatCurrentClockTime();
      setStepTimestamps((prev) => {
        const next = [...prev];
        next[index] = ts;
        return next;
      });
    };

    const t1 = window.setTimeout(() => recordStep(1), 450);
    const t2 = window.setTimeout(() => recordStep(2), 900);
    const t3 = window.setTimeout(() => recordStep(3), 1400);

    try {
      const res = await watchesApi.hunt(watch.id, simulateDrop);
      window.clearTimeout(t1);
      window.clearTimeout(t2);
      window.clearTimeout(t3);
      const doneTs = res.huntedAt
        ? new Date(res.huntedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
        : formatCurrentClockTime();
      setStepTimestamps((prev) => [
        prev[0] ?? startTs,
        prev[1] ?? startTs,
        prev[2] ?? doneTs,
        doneTs,
      ]);
      setHuntStepIndex(4);
      setHuntResult(res);
      await loadData();
      window.dispatchEvent(new CustomEvent('buybound:refresh'));
    } catch (err) {
      window.clearTimeout(t1);
      window.clearTimeout(t2);
      window.clearTimeout(t3);
      setHuntStepIndex(-1);
      setHuntError(toApiError(err).message);
    } finally {
      setHuntingId(null);
    }
  };

  const activeWatches = useMemo(
    () => watches.filter((w) => w.status === 'ACTIVE'),
    [watches],
  );

  const selectedSimWatch = useMemo(() => {
    if (selectedSimWatchId) {
      const matched = activeWatches.find((w) => w.id === selectedSimWatchId);
      if (matched) return matched;
    }
    return activeWatches[0] ?? null;
  }, [activeWatches, selectedSimWatchId]);

  const handleSimulateDrop = async () => {
    setSimulatingDrop(true);
    setHuntError(null);
    setFormError(null);

    try {
      let targetWatch = selectedSimWatch;
      if (!targetWatch) {
        const stores = trustedStores
          .split(',')
          .map((s) => s.trim().toLowerCase())
          .filter(Boolean);
        const safeName = productName.trim() || 'Watched Product';
        const targetVal = Number(buyBelowPrice) > 0 ? Number(buyBelowPrice).toFixed(2) : '200.00';
        const capVal = Number(maxItemLimit) >= Number(targetVal) ? Number(maxItemLimit).toFixed(2) : targetVal;
        targetWatch = await watchesApi.create({
          productName: safeName,
          sku: skuHint,
          variant: variantHint,
          merchantId: stores[0] || 'bestbuy.com',
          currency: 'USD',
          targetPrice: targetVal,
          maxUnitPrice: capVal,
          maxTotal: capVal,
          maxQuantity: 1,
          allowedMerchantIds: stores.length > 0 ? stores : ['bestbuy.com', 'target.com', 'walmart.com'],
          maxWeeklySpend: '1000.00',
          minSellerRating: 4.5,
          requiresHumanApproval: false,
          naturalLanguagePrompt: plainPrompt.trim() || undefined,
          baselineMarketPrice: baselineRetail && Number(baselineRetail) > 0 ? Number(baselineRetail).toFixed(2) : undefined,
        });
      }

      await handleCheckStores(targetWatch, true);
    } catch (err) {
      setHuntError(toApiError(err).message);
    } finally {
      setSimulatingDrop(false);
    }
  };

  const dropReportData = useMemo(() => {
    if (huntResult) {
      return {
        watch: huntResult.watch,
        intent: huntResult.selectedIntent ?? null,
        offers: huntResult.evaluatedOffers ?? [],
        offersScanned: huntResult.offersScanned,
        triggeredAt: huntResult.huntedAt,
      };
    }
    const latestIntent = intents.find((i) => i.status === 'CAPTURED') ?? intents[0];
    if (latestIntent) {
      const watch = watches.find((w) => w.id === latestIntent.watchId) ?? null;
      const watchOffers = offers.filter((o) => o.watchId === latestIntent.watchId);
      if (watch || watchOffers.length > 0) {
        return {
          watch,
          intent: latestIntent,
          offers: watchOffers,
          offersScanned: watchOffers.length,
          triggeredAt: latestIntent.updatedAt ?? latestIntent.createdAt,
        };
      }
    }
    return null;
  }, [huntResult, intents, offers, watches]);

  const simulateButtonLabel = selectedSimWatch
    ? `Simulate Price Drop (${selectedSimWatch.productName} — ${formatUsd(selectedSimWatch.targetPrice)} Target)`
    : 'Simulate Price Drop';

  return (
    <div className="flex flex-col gap-8">
      {/* Page Title & Dynamic Price Drop Simulation */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-bold text-ink">Price Alerts &amp; Auto-Buy</h1>
          <p className="mt-1 text-sm text-muted">
            Set target drop alerts, hard price ceilings, and seller trust guardrails. BuyBound evaluates live drops for any watched product and only settles via PayPal when every rule passes.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2.5">
          {activeWatches.length > 1 && (
            <label className="sr-only" htmlFor="sim-watch-select">
              Select active watch for price drop simulation
            </label>
          )}
          {activeWatches.length > 1 && (
            <select
              id="sim-watch-select"
              value={selectedSimWatch?.id ?? ''}
              onChange={(e) => setSelectedSimWatchId(e.target.value)}
              className="min-h-[40px] max-w-[230px] rounded-xl border border-border-strong bg-surface px-3 py-2 text-xs font-medium text-ink focus:border-brand-coral focus:outline-none"
            >
              {activeWatches.map((w) => (
                <option key={w.id} value={w.id}>
                  {w.productName} ({formatUsd(w.targetPrice)})
                </option>
              ))}
            </select>
          )}
          <Button
            type="button"
            variant="primary"
            loading={simulatingDrop}
            disabled={huntingId !== null}
            onClick={() => void handleSimulateDrop()}
          >
            {simulateButtonLabel}
          </Button>
          <Button variant="secondary" onClick={() => void loadData()}>
            Refresh Alerts
          </Button>
        </div>
      </div>

      {/* Live 4-Step Drop Execution Timeline & Formal Drop Activity Report */}
      {(huntStepIndex >= 0 || huntResult || huntError || dropReportData) && (
        <div className="flex flex-col gap-5" aria-live="polite">
          {(huntStepIndex >= 0 || huntError || huntResult) && (
            <section className="rounded-xl border border-brand-border bg-surface p-5 shadow-custom">
              <div className="mb-3 flex items-center justify-between gap-2">
                <div>
                  <h2 className="font-display text-sm font-bold text-ink">
                    Autonomous Drop Execution Timeline
                  </h2>
                  <p className="text-xs text-muted">
                    Real-time guardrail sequence verifying price band, SKU authenticity, and seller reputation before PayPal capture.
                  </p>
                </div>
                {huntResult && (
                  <button
                    type="button"
                    onClick={() => {
                      setHuntResult(null);
                      setHuntStepIndex(-1);
                    }}
                    className="text-xs text-muted underline hover:text-ink"
                  >
                    Hide Timeline
                  </button>
                )}
              </div>

              {huntStepIndex >= 0 && (
                <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-4">
                  {HUNT_STEPS.map((label, idx) => {
                    const done = huntStepIndex > idx;
                    const active = huntStepIndex === idx && huntingId !== null;
                    const ts = stepTimestamps[idx];
                    return (
                      <div
                        key={label}
                        className={`flex items-start gap-2 rounded-xl border px-3 py-2.5 text-xs font-medium ${
                          done
                            ? 'border-success/30 bg-success-bg text-success'
                            : active
                              ? 'border-brand-coral bg-brand-peach-soft text-brand-coral'
                              : 'border-brand-border bg-brand-ivory text-faint'
                        }`}
                      >
                        <span aria-hidden="true" className="mt-0.5 shrink-0">
                          {done ? '✓' : active ? '●' : `${idx + 1}.`}
                        </span>
                        <div className="min-w-0">
                          {ts && <span className="mono block text-[10px] opacity-80">{ts}</span>}
                          <span className="block">{label}</span>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}

              {huntError && (
                <div
                  role="alert"
                  className="mt-3 rounded-lg border border-[#DC2626]/25 bg-danger-bg px-4 py-3 text-sm text-danger"
                >
                  {huntError}
                </div>
              )}

              {huntResult && (
                <div className="mt-3 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-brand-border bg-brand-ivory/70 px-3.5 py-2.5 text-xs">
                  <span className="font-medium text-ink">{huntResult.summary}</span>
                  <div className="flex flex-wrap items-center gap-2">
                    {(huntResult.selectedIntent?.status === 'AWAITING_PAYPAL_SETTLEMENT' ||
                      huntResult.selectedIntent?.status === 'ORDER_CREATED') && (
                      <Button
                        variant="paypal"
                        loading={openingCheckout}
                        onClick={async () => {
                          if (!huntResult.selectedIntent) return;
                          setOpeningCheckout(true);
                          setHuntError(null);
                          try {
                            const orderRes = await paypalApi.createOrder({
                              intentId: huntResult.selectedIntent.id,
                            });
                            setCheckoutSession({
                              order: orderRes,
                              title: huntResult.watch.productName,
                              merchantId: huntResult.selectedIntent.merchantId,
                              price: huntResult.selectedIntent.maxTotal,
                              baselinePrice: huntResult.baselineMarketPrice,
                            });
                          } catch (err) {
                            setHuntError(toApiError(err).message);
                          } finally {
                            setOpeningCheckout(false);
                          }
                        }}
                      >
                        Agent Locked Deal — Click to Complete PayPal Sandbox Payment
                      </Button>
                    )}
                    {(huntResult.selectedIntent?.status === 'HELD_FOR_APPROVAL' ||
                      huntResult.selectedIntent?.status === 'AWAITING_APPROVAL' ||
                      huntResult.decision?.decision === 'HOLD') && (
                      <Button variant="primary" onClick={() => navigate('/app/purchases')}>
                        Review Held Deal
                      </Button>
                    )}
                  </div>
                </div>
              )}
            </section>
          )}

          {dropReportData && (
            <OvernightActivityReportCard
              watch={dropReportData.watch}
              intent={dropReportData.intent}
              offers={dropReportData.offers}
              offersScanned={dropReportData.offersScanned}
              triggeredAt={dropReportData.triggeredAt}
              onViewReceipt={(intent, title) => setReceiptTarget({ intent, title })}
              secondaryActionLabel="Compare Store Offers"
              onSecondaryAction={() => navigate('/app/offers')}
            />
          )}

          {dropReportData && dropReportData.offers.length > 0 && (
            <Card
              title="Drop Forensics & Seller Audit Table"
              subtitle="Interactive AG Grid Community audit showing Store, SKU Match, Total Price vs. Cap, Seller Rating & Red Flags, and Guardian Verdict."
            >
              <OvernightOfferAuditGrid
                offers={dropReportData.offers}
                intents={intents}
                onRefresh={loadData}
                showTabs={false}
                compact
              />
            </Card>
          )}
        </div>
      )}

      {/* Create a Price Alert Card */}
      <Card
        title="Create a Price Alert"
        subtitle="Describe what you want in plain English or fill in the 4 spending limits directly."
      >
        <form onSubmit={handleSaveWatch} className="flex flex-col gap-5" noValidate>
          {/* Plain English input + Example chips */}
          <div>
            <label htmlFor="nl-watch-input" className="block text-sm font-medium text-ink">
              What do you want to buy, and at what price?
            </label>
            <div className="mt-2 flex flex-wrap gap-2">
              {EXAMPLE_PROMPTS.map((chip) => (
                <button
                  key={chip}
                  type="button"
                  onClick={() => setPlainPrompt(chip)}
                  className="rounded-full border border-brand-border bg-brand-ivory px-3 py-1 text-xs font-medium text-muted hover:border-brand-coral hover:text-ink transition-colors"
                >
                  {chip.split(',')[0]}
                </button>
              ))}
            </div>
            <div className="mt-2.5 flex flex-col gap-2.5 sm:flex-row sm:items-start">
              <textarea
                id="nl-watch-input"
                rows={2}
                value={plainPrompt}
                onChange={(e) => setPlainPrompt(e.target.value)}
                placeholder='e.g. "Sony WH-1000XM5 headphones under $250, max $260"'
                className="flex-1 rounded-xl border border-border-strong bg-surface px-3.5 py-2.5 text-sm text-ink placeholder:text-faint focus:border-brand-coral focus:outline-none focus:ring-2 focus:ring-brand-coral/15"
              />
              <Button
                type="button"
                variant="secondary"
                loading={autoFilling}
                onClick={() => void handleAutoFillRules()}
                className="shrink-0 sm:min-h-[62px]"
              >
                Auto-Fill Rules
              </Button>
            </div>
            {aiNote && <p className="mt-1.5 text-xs text-brand-coral font-medium">{aiNote}</p>}
          </div>

          {/* 4-Field Clean Rules Form */}
          <div className="grid grid-cols-1 gap-4 rounded-xl border border-brand-border bg-brand-ivory p-4 sm:grid-cols-2 lg:grid-cols-4">
            <div className="flex flex-col gap-1">
              <label htmlFor="watch-product-name" className="text-xs font-semibold text-ink">
                Product Name
              </label>
              <input
                id="watch-product-name"
                type="text"
                required
                value={productName}
                onChange={(e) => setProductName(e.target.value)}
                className="min-h-[42px] rounded-xl border border-border-strong bg-surface px-3 py-2 text-sm text-ink focus:border-brand-coral focus:outline-none"
              />
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor="watch-buy-below" className="text-xs font-semibold text-ink">
                Buy Below Price ($)
              </label>
              <input
                id="watch-buy-below"
                type="text"
                inputMode="decimal"
                required
                value={buyBelowPrice}
                onChange={(e) => setBuyBelowPrice(e.target.value)}
                className="mono min-h-[42px] rounded-xl border border-border-strong bg-surface px-3 py-2 text-sm text-ink focus:border-brand-coral focus:outline-none"
              />
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor="watch-max-limit" className="text-xs font-semibold text-ink">
                Max Per-Item Limit ($)
              </label>
              <input
                id="watch-max-limit"
                type="text"
                inputMode="decimal"
                required
                value={maxItemLimit}
                onChange={(e) => setMaxItemLimit(e.target.value)}
                className="mono min-h-[42px] rounded-xl border border-border-strong bg-surface px-3 py-2 text-sm text-ink focus:border-brand-coral focus:outline-none"
              />
            </div>

            <div className="flex flex-col gap-1">
              <label htmlFor="watch-trusted-stores" className="text-xs font-semibold text-ink">
                Trusted Stores
              </label>
              <input
                id="watch-trusted-stores"
                type="text"
                required
                value={trustedStores}
                onChange={(e) => setTrustedStores(e.target.value)}
                placeholder="bestbuy.com, target.com"
                className="min-h-[42px] rounded-xl border border-border-strong bg-surface px-3 py-2 text-sm text-ink focus:border-brand-coral focus:outline-none"
              />
            </div>
          </div>

          {/* Optional Paste Product Link */}
          <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
            <div className="flex-1">
              <label htmlFor="watch-product-link" className="block text-xs font-medium text-muted">
                Paste Product Link (optional — pulls normal retail price for savings verification)
              </label>
              <input
                id="watch-product-link"
                type="url"
                value={productLink}
                onChange={(e) => setProductLink(e.target.value)}
                placeholder="https://www.bestbuy.com/site/..."
                className="mt-1 min-h-[42px] w-full rounded-xl border border-border-strong bg-surface px-3 py-2 text-sm text-ink placeholder:text-faint focus:border-brand-coral focus:outline-none"
              />
            </div>
            <Button
              type="button"
              variant="secondary"
              loading={lookingUpUrl}
              disabled={!productLink.trim()}
              onClick={() => void handleUrlLookup()}
            >
              Pull Normal Retail Price
            </Button>
          </div>

          {formSuccess && (
            <div role="status" className="rounded-lg border border-[#16A34A]/25 bg-success-bg px-4 py-2.5 text-sm font-medium text-success">
              {formSuccess}
            </div>
          )}
          {formError && (
            <div role="alert" className="rounded-lg border border-[#DC2626]/25 bg-danger-bg px-4 py-2.5 text-sm font-medium text-danger">
              {formError}
            </div>
          )}

          <div className="flex flex-wrap items-center justify-between gap-4 border-t border-brand-border pt-4">
            <p className="text-xs text-muted">
              Reference retail price: <strong className="mono text-ink">{formatUsd(baselineRetail)}</strong> · Protected
              by your weekly limit
            </p>
            <Button type="submit" variant="primary" loading={savingWatch}>
              Save &amp; Start Watching
            </Button>
          </div>
        </form>
      </Card>

      {/* Active Watches Grid */}
      <section>
        <div className="mb-4 flex items-center justify-between gap-4">
          <h2 className="font-display text-lg font-semibold text-ink">Active Price Watches</h2>
          <span className="text-xs font-medium text-muted">
            {watches.length} watch{watches.length === 1 ? '' : 'es'} configured
          </span>
        </div>

        {loading && <Spinner label="Loading your price watches…" />}
        {loadError && !loading && <ErrorBanner message={loadError} onRetry={() => void loadData()} />}

        {!loading && !loadError && watches.length === 0 && (
          <EmptyState
            title="No price watches yet"
            description="Describe an item above and click Save & Start Watching to create your first watch."
          />
        )}

        {!loading && !loadError && watches.length > 0 && (
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
            {watches.map((w) => {
              const capturedIntent = capturedByWatchId.get(w.id);
              const heldIntent = heldByWatchId.get(w.id);

              const badge = capturedIntent
                ? { label: 'Purchased', tone: 'success' as const }
                : w.status === 'ACTIVE'
                  ? { label: 'Watching', tone: 'info' as const }
                  : { label: 'Paused', tone: 'warning' as const };

              const isPaused = w.status !== 'ACTIVE';

              return (
                <article
                  key={w.id}
                  className="flex flex-col justify-between gap-4 rounded-xl border border-border bg-surface p-5 shadow-custom"
                >
                  <div>
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex items-start gap-3 min-w-0">
                        <img
                          src={resolveProductImageSrc(w.imageUrl, null, w.productName)}
                          alt={w.productName}
                          loading="lazy"
                          referrerPolicy="no-referrer"
                          onError={(e) => handleProductImageError(e, null, w.productName)}
                          className="h-14 w-14 shrink-0 rounded-xl border border-brand-border bg-white object-cover"
                        />
                        <Link
                          to={`/watches/${w.id}`}
                          className="font-display text-base font-bold text-ink hover:text-brand-coral transition-colors line-clamp-2"
                        >
                          {w.productName}
                        </Link>
                      </div>
                      <Badge tone={badge.tone} dot>
                        {badge.label}
                      </Badge>
                    </div>

                    <div className="mt-4 grid grid-cols-2 gap-3 rounded-lg border border-border bg-background p-3 text-xs">
                      <div>
                        <span className="block text-faint">Target Price</span>
                        <span className="mono text-base font-bold text-ink">{formatUsd(w.targetPrice)}</span>
                      </div>
                      <div>
                        <span className="block text-faint">Normal Retail</span>
                        <span className="mono text-sm text-muted line-through">
                          {w.baselineMarketPrice ? formatUsd(w.baselineMarketPrice) : '—'}
                        </span>
                      </div>
                    </div>

                    <p className="mt-3 text-xs text-muted">
                      Primary store: <strong className="text-ink">{formatStoreName(w.merchantId)}</strong>
                    </p>
                  </div>

                  <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border pt-3">
                    <Button
                      variant="primary"
                      disabled={isPaused || huntingId !== null}
                      loading={huntingId === w.id}
                      onClick={() => void handleCheckStores(w)}
                    >
                      Check Stores Now
                    </Button>

                    <div className="flex items-center gap-2">
                      {capturedIntent && (
                        <button
                          type="button"
                          onClick={() => setReceiptTarget({ intent: capturedIntent, title: w.productName })}
                          className="text-xs font-semibold text-success hover:underline"
                        >
                          View Receipt
                        </button>
                      )}
                      {heldIntent && (
                        <Link to="/app/purchases" className="text-xs font-semibold text-warning hover:underline">
                          Review Hold
                        </Link>
                      )}
                      {!capturedIntent && !heldIntent && (
                        <Link to={`/watches/${w.id}`} className="text-xs font-medium text-muted hover:text-ink">
                          Rules →
                        </Link>
                      )}
                    </div>
                  </div>
                </article>
              );
            })}
          </div>
        )}
      </section>

      <PayPalCheckoutModal
        session={checkoutSession}
        vaultConnected={false}
        onClose={() => setCheckoutSession(null)}
        onCaptured={async ({ intent, capture, message }) => {
          const title = checkoutSession?.title ?? 'Order';
          setCheckoutSession(null);
          await loadData();
          window.dispatchEvent(new CustomEvent('buybound:refresh'));
          setReceiptTarget({ intent, title });
          setHuntError(null);
          if (huntResult) {
            setHuntResult({
              ...huntResult,
              selectedIntent: intent,
              summary: `${message}${capture?.captureId ? ` (Capture ID: ${capture.captureId})` : ''}`,
            });
          }
        }}
      />

      <VerifiedReceiptModal
        intent={receiptTarget?.intent ?? null}
        productTitle={receiptTarget?.title}
        onClose={() => setReceiptTarget(null)}
      />
    </div>
  );
}

