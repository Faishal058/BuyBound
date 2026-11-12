import { useState } from 'react';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { toApiError } from '../../services/api/client';
import { paypalApi } from '../../services/api/endpoints';
import { formatDateTime, formatStoreName, formatUsd } from '../../features/shared/meta';
import { handleProductImageError, resolveProductImageSrc } from '../../features/shared/productImages';
import type { PayPalOrderVerificationResult, PurchaseActivityItem } from '../../services/api/types';

interface PayPalActivityFeedProps {
  items: PurchaseActivityItem[];
  onCompletePayPalPayment?: (item: PurchaseActivityItem) => void;
  onRefresh?: () => void;
}

export function PayPalActivityFeed({
  items,
  onCompletePayPalPayment,
  onRefresh,
}: PayPalActivityFeedProps) {
  const [verifyingOrderId, setVerifyingOrderId] = useState<string | null>(null);
  const [verifiedMap, setVerifiedMap] = useState<Record<string, PayPalOrderVerificationResult>>({});
  const [verifyErrors, setVerifyErrors] = useState<Record<string, string>>({});

  const handleVerifyLive = async (orderId: string) => {
    setVerifyingOrderId(orderId);
    setVerifyErrors((prev) => ({ ...prev, [orderId]: '' }));
    try {
      const live = await paypalApi.verifyOrder(orderId);
      setVerifiedMap((prev) => ({ ...prev, [orderId]: live }));
      if (live.status === 'COMPLETED' && onRefresh) {
        onRefresh();
      }
    } catch (err) {
      setVerifyErrors((prev) => ({ ...prev, [orderId]: toApiError(err).message }));
    } finally {
      setVerifyingOrderId(null);
    }
  };

  if (items.length === 0) {
    return (
      <div className="rounded-xl border border-brand-border bg-white p-6 text-center text-sm text-muted">
        No PayPal Sandbox or Autonomous Agent activity recorded yet. Start a Direct Buy Now or Agent Hunt from Shop Products!
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {items.map((item) => {
        const isCompleted = item.intentStatus === 'CAPTURED' || item.intentStatus === 'SUCCEEDED';
        const needsPayPalPayment =
          !isCompleted &&
          (item.intentStatus === 'AWAITING_PAYPAL_SETTLEMENT' ||
            item.intentStatus === 'ORDER_CREATED' ||
            item.intentStatus === 'APPROVED');
        const liveResult = item.paypalOrderId ? verifiedMap[item.paypalOrderId] : undefined;
        const verifyErr = item.paypalOrderId ? verifyErrors[item.paypalOrderId] : undefined;

        return (
          <article
            key={item.intentId}
            className="flex flex-col gap-3 rounded-2xl border border-brand-border bg-white p-4 shadow-sm"
          >
            <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-start">
              <div className="flex items-start gap-3.5 min-w-0 flex-1">
                <img
                  src={resolveProductImageSrc(item.imageUrl, null, item.productName)}
                  alt={item.productName}
                  loading="lazy"
                  referrerPolicy="no-referrer"
                  onError={(e) => handleProductImageError(e, null, item.productName)}
                  className="h-14 w-14 shrink-0 rounded-xl border border-brand-border bg-white object-cover"
                />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="font-display text-base font-bold text-ink">{item.productName}</h3>
                    <Badge tone={item.purchaseMode === 'DIRECT_HUMAN' ? 'info' : 'neutral'}>
                      {item.purchaseMode === 'DIRECT_HUMAN' ? 'DIRECT HUMAN BUY' : 'AI AGENT AUTO-BUY'}
                    </Badge>
                    <Badge
                      tone={
                        isCompleted
                          ? 'success'
                          : needsPayPalPayment
                            ? 'warning'
                            : item.intentStatus === 'BLOCKED' || item.intentStatus === 'FAILED'
                              ? 'danger'
                              : 'neutral'
                      }
                      dot
                    >
                      {item.paymentStatusLabel}
                    </Badge>
                  </div>

                  <p className="mt-1 text-xs text-muted">
                    Merchant: <strong className="text-ink">{formatStoreName(item.merchantDomain)}</strong> ·
                    Amount: <strong className="mono text-ink">{formatUsd(item.amount)} {item.currency}</strong> ·{' '}
                    {formatDateTime(item.timestamp)}
                    {item.updatedSandboxWalletBalance && (
                      <> · Wallet Balance: <strong className="mono text-ink">{formatUsd(item.updatedSandboxWalletBalance)}</strong></>
                    )}
                  </p>

                  <div className="mt-1.5 flex flex-wrap gap-x-4 gap-y-1 text-[11px] text-faint mono">
                    <span>Order ID: {item.paypalOrderId ?? '—'}</span>
                    <span>Capture ID: {item.paypalCaptureId ?? 'Pending Settlement'}</span>
                    <span>Payer: {item.payerEmail ?? item.payerId ?? 'PayPal Sandbox Buyer'}</span>
                  </div>

                  {item.auditSummary && (
                    <p className="mt-1.5 text-xs text-ink-muted">{item.auditSummary}</p>
                  )}
                </div>
              </div>

              <div className="flex flex-wrap items-center gap-2 shrink-0">
                {needsPayPalPayment && onCompletePayPalPayment && (
                  <Button
                    variant="paypal"
                    onClick={() => onCompletePayPalPayment(item)}
                  >
                    Agent Locked Deal — Complete PayPal Payment
                  </Button>
                )}

                {item.paypalOrderId && (
                  <Button
                    variant="secondary"
                    className="!min-h-[36px] !text-xs"
                    loading={verifyingOrderId === item.paypalOrderId}
                    onClick={() => void handleVerifyLive(item.paypalOrderId!)}
                  >
                    Verify Live in PayPal
                  </Button>
                )}

                <a
                  href="https://www.sandbox.paypal.com/myaccount/transactions"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex min-h-[36px] items-center rounded-xl border border-brand-border bg-brand-ivory px-3 py-1.5 text-xs font-semibold text-brand-coral hover:bg-brand-peach-soft transition-colors"
                >
                  Open PayPal Sandbox ↗
                </a>
              </div>
            </div>

            {liveResult && (
              <div className="rounded-xl border border-[#16A34A]/25 bg-success-bg/60 px-3.5 py-2.5 text-xs text-ink">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <strong>Live PayPal Orders v2 Verification: {liveResult.status}</strong>
                  <span className="mono text-faint">Order {liveResult.orderId}</span>
                </div>
                <p className="mt-0.5 text-muted">
                  {liveResult.message}
                  {liveResult.payerEmail ? ` · Payer: ${liveResult.payerEmail}` : ''}
                  {liveResult.amount ? ` · Amount: ${liveResult.amount} ${liveResult.currency ?? 'USD'}` : ''}
                </p>
              </div>
            )}

            {verifyErr && (
              <p className="text-xs font-medium text-danger">{verifyErr}</p>
            )}
          </article>
        );
      })}
    </div>
  );
}

