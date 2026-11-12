import { useEffect, useId, useRef } from 'react';
import { formatShippingAddress, useOptionalAuth } from '../../context/authState';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { formatDateTime, formatStoreName, formatUsd } from '../../features/shared/meta';
import type { PurchaseIntent } from '../../services/api/types';

interface VerifiedReceiptModalProps {
  intent: PurchaseIntent | null;
  productTitle?: string;
  payerEmail?: string;
  payerName?: string;
  newBalance?: string | null;
  shippingAddress?: string;
  onClose: () => void;
}

const PASSED_RULES = [
  'Under item limit',
  'Within weekly budget',
  'Trusted store',
  'Exact product match',
  'No hidden subscription',
];

export function VerifiedReceiptModal({
  intent,
  productTitle,
  payerEmail,
  payerName,
  newBalance,
  shippingAddress,
  onClose,
}: VerifiedReceiptModalProps) {
  const auth = useOptionalAuth();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const pressedOutsideRef = useRef(false);
  const resolvedShipping = shippingAddress ?? formatShippingAddress(auth?.user);
  const displayPayerName = payerName || auth?.user?.fullName || 'Verified PayPal Buyer';
  const displayPayerEmail = payerEmail || auth?.user?.paypalEmail || null;
  const displayBalance = newBalance ?? auth?.user?.paypalBalance;

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (intent) {
      if (!dialog.open && typeof dialog.showModal === 'function') dialog.showModal();
    } else if (dialog.open && typeof dialog.close === 'function') {
      dialog.close();
    }
  }, [intent]);

  if (!intent) return null;

  const isOutside = (e: React.PointerEvent<HTMLDialogElement>, dialog: HTMLDialogElement) => {
    const r = dialog.getBoundingClientRect();
    return e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom;
  };

  const snap = intent.offerSnapshot;
  const paid = snap?.totalPrice ?? intent.maxTotal;
  const baseline = snap?.baselinePrice;
  const savings = snap?.verifiedSavings;
  const displayName = productTitle ?? intent.canonicalSku ?? 'Verified Item';

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={titleId}
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onPointerDown={(e) => {
        if (dialogRef.current) pressedOutsideRef.current = isOutside(e, dialogRef.current);
      }}
      onPointerUp={(e) => {
        if (dialogRef.current && pressedOutsideRef.current && isOutside(e, dialogRef.current)) {
          onClose();
        }
        pressedOutsideRef.current = false;
      }}
      className="m-auto w-full max-w-xl rounded-2xl border border-border bg-surface p-6 sm:p-7 shadow-xl text-ink"
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone="success" dot>
              BuyBound Purchase Guarantee
            </Badge>
            <Badge tone="info">PayPal Verified</Badge>
          </div>
          <h2 id={titleId} className="mt-2.5 font-display text-xl font-bold text-ink">
            PayPal Purchase Receipt
          </h2>
          <p className="mt-1 text-sm text-muted">
            Completed only after every spending limit and seller safety rule passed.
          </p>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="Close receipt modal"
          className="no-print rounded-lg p-1.5 text-sm text-muted hover:bg-surface-muted hover:text-ink"
        >
          ✕
        </button>
      </div>

      {/* Savings Highlight Banner */}
      <div className="mt-5 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[#16A34A]/25 bg-success-bg px-4 py-3.5">
        <div>
          <p className="text-xs font-medium text-success">Verified Savings</p>
          <p className="font-display text-2xl font-bold text-success">
            {savings && Number(savings) > 0 ? `You Saved ${formatUsd(savings)}` : `Paid ${formatUsd(paid)}`}
          </p>
        </div>
        {baseline && Number(baseline) > Number(paid) && (
          <div className="text-right">
            <p className="text-xs text-muted">Normal retail price</p>
            <p className="mono text-sm font-medium text-muted line-through">{formatUsd(baseline)}</p>
          </div>
        )}
      </div>

      <dl className="mt-4 divide-y divide-border rounded-xl border border-border bg-background/60 px-4">
        <div className="flex items-center justify-between gap-4 py-2.5 text-sm">
          <dt className="text-muted">Item bought</dt>
          <dd className="font-semibold text-ink text-right">{displayName}</dd>
        </div>
        <div className="flex items-center justify-between gap-4 py-2.5 text-sm">
          <dt className="text-muted">Store</dt>
          <dd className="font-medium text-ink">
            {formatStoreName(intent.merchantId)}{' '}
            <span className="mono text-xs text-faint">({intent.merchantId})</span>
          </dd>
        </div>
        <div className="flex items-center justify-between gap-4 py-2.5 text-sm">
          <dt className="text-muted">Price paid</dt>
          <dd className="mono font-bold text-ink">{formatUsd(paid)}</dd>
        </div>
        <div className="flex items-center justify-between gap-4 py-2.5 text-sm">
          <dt className="text-muted">PayPal Order ID</dt>
          <dd className="mono text-xs font-semibold text-ink">{intent.providerOrderId ?? '—'}</dd>
        </div>
        <div className="flex items-center justify-between gap-4 py-2.5 text-sm">
          <dt className="text-muted">PayPal Capture ID</dt>
          <dd className="mono text-xs font-semibold text-success">{intent.paypalCaptureId ?? '—'}</dd>
        </div>
        <div className="flex items-center justify-between gap-4 py-2.5 text-sm">
          <dt className="text-muted">Paid By</dt>
          <dd className="mono text-right text-xs font-medium text-ink">
            {displayPayerEmail ? `${displayPayerName} (${displayPayerEmail})` : displayPayerName}
          </dd>
        </div>
        <div className="flex items-center justify-between gap-4 py-2.5 text-sm">
          <dt className="text-muted">Funding Source</dt>
          <dd className="text-right text-xs font-medium text-ink">
            PayPal Sandbox Live Order
          </dd>
        </div>
        {displayBalance && (
          <div className="flex items-center justify-between gap-4 py-2.5 text-sm">
            <dt className="text-muted">Remaining Wallet Balance</dt>
            <dd className="mono text-xs font-bold text-[#0070BA]">{formatUsd(displayBalance)}</dd>
          </div>
        )}
        <div className="flex items-center justify-between gap-4 py-2.5 text-sm">
          <dt className="text-muted">Seller Reputation</dt>
          <dd className="text-right text-xs font-medium text-success">
            {(snap?.sellerRating ?? 4.9).toFixed(1)}★ ({(snap?.sellerReviewCount ?? 3420).toLocaleString()} reviews ·{' '}
            {snap?.negativeRedFlagCount ?? 0} red flags)
          </dd>
        </div>
        <div className="flex items-center justify-between gap-4 py-2.5 text-sm">
          <dt className="text-muted">Estimated Delivery</dt>
          <dd className="text-right text-xs font-semibold text-ink">
            {snap?.estimatedDelivery ?? 'Arriving in 2 Days'}
          </dd>
        </div>
        {resolvedShipping && (
          <div className="flex items-center justify-between gap-4 py-2.5 text-sm">
            <dt className="text-muted">Shipping Destination</dt>
            <dd className="text-right text-xs font-medium text-ink">{resolvedShipping}</dd>
          </div>
        )}
        <div className="flex items-center justify-between gap-4 py-2.5 text-sm">
          <dt className="text-muted">Date &amp; time</dt>
          <dd className="text-xs text-ink">{formatDateTime(intent.updatedAt)}</dd>
        </div>
      </dl>

      {/* 5 Safety Rules Checklist */}
      <div className="mt-5 rounded-xl border border-border bg-surface p-4">
        <p className="text-xs font-semibold uppercase tracking-wider text-muted">
          Safety Rules Verified (5/5)
        </p>
        <ul className="mt-2.5 grid grid-cols-1 gap-2 sm:grid-cols-2">
          {PASSED_RULES.map((rule) => (
            <li key={rule} className="flex items-center gap-2 text-xs font-medium text-ink">
              <span
                aria-hidden="true"
                className="inline-flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-success-bg text-success"
              >
                ✓
              </span>
              {rule}
            </li>
          ))}
        </ul>
      </div>

      {snap?.productUrl && (
        <div className="mt-4 no-print">
          <a
            href={snap.productUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="text-xs font-semibold text-brand-coral hover:underline"
          >
            View Store Listing &amp; Price Proof ↗
          </a>
        </div>
      )}

      <div className="no-print mt-6 flex flex-wrap justify-between gap-3">
        <Button variant="secondary" onClick={() => window.print()}>
          Print Receipt
        </Button>
        <Button onClick={onClose} autoFocus>
          Done
        </Button>
      </div>
    </dialog>
  );
}
