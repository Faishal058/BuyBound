import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { PayPalCheckoutModal, type CheckoutSession } from '../../../components/modals/PayPalCheckoutModal';
import { VerifiedReceiptModal } from '../../../components/modals/VerifiedReceiptModal';
import { OvernightActivityReportCard } from '../../../components/reports/OvernightActivityReportCard';
import { PayPalActivityFeed } from '../../../components/reports/PayPalActivityFeed';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { EmptyState } from '../../../components/ui/EmptyState';
import { ErrorBanner } from '../../../components/ui/ErrorBanner';
import { Spinner } from '../../../components/ui/Spinner';
import { toApiError } from '../../../services/api/client';
import { mandatesApi, paypalApi, purchasesApi, watchesApi } from '../../../services/api/endpoints';
import { INTENT_STATUS_META, formatDateTime, formatStoreName, formatUsd } from '../../shared/meta';
import { handleProductImageError, resolveProductImageSrc } from '../../shared/productImages';
import type {
  ActiveMandateTelemetry,
  HuntedOffer,
  PurchaseActivityItem,
  PurchaseIntent,
  Watch,
} from '../../../services/api/types';

export function PurchasesPage() {
  const navigate = useNavigate();
  const [intents, setIntents] = useState<PurchaseIntent[]>([]);
  const [watches, setWatches] = useState<Watch[]>([]);
  const [offers, setOffers] = useState<HuntedOffer[]>([]);
  const [activityItems, setActivityItems] = useState<PurchaseActivityItem[]>([]);
  const [telemetry, setTelemetry] = useState<ActiveMandateTelemetry | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [actingId, setActingId] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [checkoutSession, setCheckoutSession] = useState<CheckoutSession | null>(null);
  const [receiptTarget, setReceiptTarget] = useState<{
    intent: PurchaseIntent;
    title: string;
    payerEmail?: string;
    payerName?: string;
    newBalance?: string;
  } | null>(null);

  const loadData = useCallback(async () => {
    setError(null);
    try {
      const [intentList, watchList, offerList, activeTel, actList] = await Promise.all([
        purchasesApi.listAllIntents(),
        watchesApi.list(),
        purchasesApi.listAllOffers().catch(() => []),
        mandatesApi.active().catch(() => null),
        purchasesApi.activity().catch(() => []),
      ]);
      setIntents(intentList);
      setWatches(watchList);
      setOffers(offerList);
      setActivityItems(actList);
      if (activeTel) setTelemetry(activeTel);
    } catch (err) {
      setError(toApiError(err).message);
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

  const watchById = useMemo(() => {
    const map = new Map<string, Watch>();
    for (const w of watches) {
      map.set(w.id, w);
    }
    return map;
  }, [watches]);

  const heldDeals = useMemo(
    () =>
      intents.filter(
        (i) =>
          i.status === 'HELD_FOR_APPROVAL' ||
          i.status === 'AWAITING_APPROVAL' ||
          i.status === 'AWAITING_PAYPAL_SETTLEMENT' ||
          i.status === 'APPROVED' ||
          i.status === 'ORDER_CREATED',
      ),
    [intents],
  );

  const completedPurchases = useMemo(
    () => intents.filter((i) => i.status === 'CAPTURED' || i.status === 'SUCCEEDED'),
    [intents],
  );

  const blockedOrDeclined = useMemo(
    () => intents.filter((i) => i.status === 'BLOCKED' || i.status === 'REJECTED_BY_USER'),
    [intents],
  );

  const latestOvernightReport = useMemo(() => {
    const latestIntent = completedPurchases[0] ?? heldDeals[0];

    if (latestIntent) {
      const watch = watchById.get(latestIntent.watchId) ?? null;
      const watchOffers = offers.filter((o) => o.watchId === latestIntent.watchId);
      return {
        watch,
        intent: latestIntent,
        offers: watchOffers.length > 0 ? watchOffers : offers,
      };
    }

    if (offers.length > 0) {
      const firstWatch = watchById.get(offers[0].watchId) ?? watches[0] ?? null;
      const watchOffers = firstWatch
        ? offers.filter((o) => o.watchId === firstWatch.id)
        : offers;
      return {
        watch: firstWatch,
        intent: null,
        offers: watchOffers,
      };
    }

    return null;
  }, [completedPurchases, heldDeals, offers, watchById, watches]);

  const getItemTitle = (intent: PurchaseIntent) =>
    watchById.get(intent.watchId)?.productName ?? intent.canonicalSku ?? 'Tracked Product';

  const handleOpenPayPalCheckout = async (intent: PurchaseIntent, title: string) => {
    setActingId(intent.id);
    setFeedback(null);
    setActionError(null);
    try {
      const orderRes = await paypalApi.createOrder({ intentId: intent.id });
      if (orderRes.blocked) {
        setActionError(orderRes.message);
        return;
      }
      setCheckoutSession({
        order: orderRes,
        title,
        merchantId: intent.merchantId,
        price: intent.maxTotal,
        baselinePrice: intent.offerSnapshot?.baselinePrice,
      });
    } catch (err) {
      setActionError(toApiError(err).message);
    } finally {
      setActingId(null);
    }
  };

  const handleRejectHold = async (intent: PurchaseIntent) => {
    setActingId(intent.id);
    setFeedback(null);
    setActionError(null);
    try {
      const res = await purchasesApi.rejectHold(intent.id);
      setFeedback(res.message);
      await loadData();
      window.dispatchEvent(new CustomEvent('buybound:refresh'));
    } catch (err) {
      setActionError(toApiError(err).message);
    } finally {
      setActingId(null);
    }
  };

  const handleSyncPayPal = async (intent: PurchaseIntent) => {
    setActingId(intent.id);
    setFeedback(null);
    setActionError(null);
    try {
      const res = await purchasesApi.syncPayPal(intent.id);
      setFeedback(res.message);
      await loadData();
      window.dispatchEvent(new CustomEvent('buybound:refresh'));
    } catch (err) {
      setActionError(toApiError(err).message);
    } finally {
      setActingId(null);
    }
  };

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-bold text-ink">Purchases &amp; Receipts</h1>
          <p className="mt-1 text-sm text-muted">
            Approve deals waiting for your sign-off and open printable PayPal receipts proving what you saved.
          </p>
        </div>
        <Button variant="secondary" onClick={() => void loadData()}>
          Refresh Purchases
        </Button>
      </div>

      {feedback && (
        <div role="status" className="rounded-xl border border-[#16A34A]/25 bg-success-bg px-4 py-3 text-sm font-medium text-success">
          {feedback}
        </div>
      )}
      {actionError && (
        <div role="alert" className="rounded-xl border border-[#DC2626]/25 bg-danger-bg px-4 py-3 text-sm font-medium text-danger">
          {actionError}
        </div>
      )}

      {loading && <Spinner label="Loading purchases and holds…" />}
      {error && !loading && <ErrorBanner message={error} onRetry={() => void loadData()} />}

      {/* Formal Overnight Activity Report */}
      {!loading && !error && latestOvernightReport && (
        <OvernightActivityReportCard
          watch={latestOvernightReport.watch}
          intent={latestOvernightReport.intent}
          offers={latestOvernightReport.offers}
          offersScanned={latestOvernightReport.offers.length}
          onViewReceipt={(intent, title) =>
            setReceiptTarget({
              intent,
              title,
              payerEmail: telemetry?.paypalPayerEmail,
              payerName: telemetry?.paypalPayerName,
              newBalance: telemetry?.sandboxBalance,
            })
          }
          secondaryActionLabel="Inspect Seller Audit Grid"
          onSecondaryAction={() => navigate('/app/offers')}
        />
      )}

      {/* Needs Your Approval Banner (if any HOLD deals exist) */}
      {!loading && !error && heldDeals.length > 0 && (
        <section aria-label="Deals needing your approval" className="rounded-2xl border border-[#D97706]/35 bg-warning-bg p-6">
          <div className="flex flex-wrap items-center justify-between gap-2 mb-4">
            <div>
              <span className="inline-flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-warning">
                <span aria-hidden="true" className="h-2 w-2 rounded-full bg-warning-dot" />
                Needs Your Approval ({heldDeals.length})
              </span>
              <h2 className="mt-1 font-display text-lg font-bold text-ink">
                Deals paused by your spending &amp; store rules
              </h2>
            </div>
          </div>

          <div className="flex flex-col gap-3">
            {heldDeals.map((intent) => {
              const title = getItemTitle(intent);
              const busy = actingId === intent.id;
              const isHold = intent.status === 'HELD_FOR_APPROVAL' || intent.status === 'AWAITING_APPROVAL';
              const snap = intent.offerSnapshot;

              return (
                <article
                  key={intent.id}
                  className="flex flex-col justify-between gap-4 rounded-xl border border-[#D97706]/25 bg-surface p-5 sm:flex-row sm:items-center"
                >
                  <div className="flex items-start gap-3.5 min-w-0 flex-1">
                    <img
                      src={resolveProductImageSrc(snap?.imageUrl, null, title)}
                      alt={title}
                      loading="lazy"
                      referrerPolicy="no-referrer"
                      onError={(e) => handleProductImageError(e, null, title)}
                      className="h-14 w-14 shrink-0 rounded-xl border border-brand-border bg-white object-cover"
                    />
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <h3 className="font-display text-base font-bold text-ink">{title}</h3>
                        <Badge tone="warning" dot>
                          {INTENT_STATUS_META[intent.status]?.label ?? 'On Hold'}
                        </Badge>
                      </div>
                      <p className="mt-1 text-sm text-muted">
                        Found for <strong className="mono text-ink">{formatUsd(intent.maxTotal)}</strong> at{' '}
                        <strong className="text-ink">{formatStoreName(intent.merchantId)}</strong>
                        {snap?.baselinePrice && Number(snap.baselinePrice) > Number(intent.maxTotal) && (
                          <> (normal price <span className="line-through">{formatUsd(snap.baselinePrice)}</span>)</>
                        )}
                        {intent.failureReason ? ` — ${intent.failureReason}` : ' — waiting for your confirmation before paying.'}
                      </p>
                    </div>
                  </div>

                  <div className="flex flex-wrap items-center gap-2.5 shrink-0">
                    <Button
                      variant="paypal"
                      loading={busy}
                      disabled={actingId !== null}
                      onClick={() => void handleOpenPayPalCheckout(intent, title)}
                    >
                      {intent.status === 'AWAITING_PAYPAL_SETTLEMENT' || intent.status === 'ORDER_CREATED'
                        ? 'Agent Locked Deal — Complete PayPal Sandbox Payment'
                        : 'Approve & Pay with PayPal'}
                    </Button>
                    {isHold && (
                      <Button
                        variant="secondary"
                        disabled={actingId !== null}
                        onClick={() => void handleRejectHold(intent)}
                      >
                        Cancel Deal
                      </Button>
                    )}

                    {intent.approvalUrl && (
                      <Button
                        variant="secondary"
                        loading={busy}
                        onClick={() => void handleSyncPayPal(intent)}
                      >
                        Sync Capture
                      </Button>
                    )}
                  </div>
                </article>
              );
            })}
          </div>
        </section>
      )}

      {/* Live PayPal Sandbox & Autonomous Agent Activity Panel */}
      {!loading && !error && (
        <Card
          title="PayPal Sandbox &amp; Purchase Activity"
          subtitle="Real-time ledger of Direct Human Buys and Autonomous Agent executions, PayPal Order/Capture IDs, and live PayPal API verification."
        >
          <PayPalActivityFeed
            items={activityItems}
            onRefresh={() => void loadData()}
            onCompletePayPalPayment={(item) => {
              const matching = intents.find((i) => i.id === item.intentId);
              if (matching) {
                void handleOpenPayPalCheckout(matching, item.productName);
              }
            }}
          />
        </Card>
      )}

      {/* Completed Purchases & Printable Receipts */}
      {!loading && !error && (
        <Card
          title="Completed PayPal Purchases"
          subtitle="Click any purchase below to view or print your PayPal Purchase Receipt."
        >
          {completedPurchases.length === 0 ? (
            <EmptyState
              title="No completed purchases yet"
              description="When a deal passes all spending rules or you confirm an order, your PayPal receipt will appear here."
              action={
                <Link to="/app/shop">
                  <Button>Shop All Products</Button>
                </Link>
              }
            />
          ) : (
            <div className="divide-y divide-border rounded-xl border border-border overflow-hidden">
              {completedPurchases.map((intent) => {
                const title = getItemTitle(intent);
                const snap = intent.offerSnapshot;
                const savings = snap?.verifiedSavings;

                return (
                  <div
                    key={intent.id}
                    className="flex flex-col justify-between gap-4 bg-surface p-4 hover:bg-background/60 transition-colors sm:flex-row sm:items-center"
                  >
                    <div className="flex items-start gap-3.5 min-w-0">
                      <img
                        src={resolveProductImageSrc(snap?.imageUrl, null, title)}
                        alt={title}
                        loading="lazy"
                        referrerPolicy="no-referrer"
                        onError={(e) => handleProductImageError(e, null, title)}
                        className="h-12 w-12 shrink-0 rounded-xl border border-brand-border bg-white object-cover"
                      />
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <button
                            type="button"
                            onClick={() => setReceiptTarget({ intent, title })}
                            className="text-left font-display text-base font-bold text-ink hover:text-brand-coral transition-colors"
                          >
                            {title}
                          </button>
                          <Badge tone="success" dot>
                            Paid with PayPal
                          </Badge>
                          {savings && Number(savings) > 0 && (
                            <Badge tone="success">You Saved {formatUsd(savings)}</Badge>
                          )}
                        </div>
                        <p className="mt-1 text-xs text-muted">
                          Store: <strong className="text-ink">{formatStoreName(intent.merchantId)}</strong> · Paid{' '}
                          <span className="mono font-semibold text-ink">{formatUsd(intent.maxTotal)}</span>
                          {snap?.baselinePrice && (
                            <> (Retail {formatUsd(snap.baselinePrice)})</>
                          )}
                          {' '}· {formatDateTime(intent.updatedAt)}
                        </p>
                        <p className="mono mt-1 text-[11px] text-faint">
                          PayPal Order: {intent.providerOrderId ?? '—'}
                          {intent.paypalCaptureId ? ` · Capture: ${intent.paypalCaptureId}` : ''}
                        </p>
                      </div>
                    </div>

                    <div className="flex items-center gap-2 shrink-0">
                      <Button variant="secondary" onClick={() => setReceiptTarget({ intent, title })}>
                        Open Receipt
                      </Button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </Card>
      )}

      {/* Blocked & Declined Activity Summary */}
      {!loading && !error && blockedOrDeclined.length > 0 && (
        <Card
          title="Blocked Scams & Declined Deals"
          subtitle="Transactions stopped by your rules before PayPal was ever charged ($0.00 spent)."
        >
          <div className="divide-y divide-border rounded-xl border border-border overflow-hidden">
            {blockedOrDeclined.slice(0, 8).map((intent) => (
              <div
                key={intent.id}
                className="flex flex-wrap items-center justify-between gap-3 bg-surface px-4 py-3 text-sm"
              >
                <div className="min-w-0">
                  <p className="font-medium text-ink">
                    {getItemTitle(intent)} · <span className="text-muted">{formatStoreName(intent.merchantId)}</span>
                  </p>
                  <p className="text-xs text-muted">{intent.failureReason ?? 'Blocked before PayPal checkout.'}</p>
                </div>
                <div className="flex items-center gap-3 shrink-0">
                  <span className="mono text-xs text-faint line-through">{formatUsd(intent.maxTotal)}</span>
                  <Badge tone={intent.status === 'BLOCKED' ? 'danger' : 'neutral'}>
                    {intent.status === 'BLOCKED' ? 'Blocked · $0 Charged' : 'Declined'}
                  </Badge>
                </div>
              </div>
            ))}
          </div>
        </Card>
      )}

      <PayPalCheckoutModal
        session={checkoutSession}
        vaultConnected={Boolean(telemetry?.vaultConnected)}
        onClose={() => setCheckoutSession(null)}
        onCaptured={async ({ intent, capture, message }) => {
          const title = checkoutSession?.title ?? getItemTitle(intent);
          setCheckoutSession(null);
          setFeedback(message);
          await loadData();
          setReceiptTarget({
            intent,
            title,
            payerEmail: capture?.payerEmail ?? telemetry?.paypalPayerEmail,
            payerName: capture?.payerName ?? telemetry?.paypalPayerName,
            newBalance: capture?.newSandboxBalance ?? undefined,
          });
        }}
      />

      <VerifiedReceiptModal
        intent={receiptTarget?.intent ?? null}
        productTitle={receiptTarget?.title}
        payerEmail={receiptTarget?.payerEmail}
        payerName={receiptTarget?.payerName}
        newBalance={receiptTarget?.newBalance}
        onClose={() => setReceiptTarget(null)}
      />
    </div>
  );
}

