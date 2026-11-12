import { useEffect, useId, useRef, useState } from 'react';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { loadPayPalSdk } from '../../lib/paypalSdk';
import { toApiError } from '../../services/api/client';
import { paypalApi, purchasesApi } from '../../services/api/endpoints';
import { formatStoreName, formatUsd } from '../../features/shared/meta';
import type {
  CapturePayPalOrderResponse,
  CreatePayPalOrderResponse,
  PurchaseIntent,
} from '../../services/api/types';

export interface CheckoutSession {
  order: CreatePayPalOrderResponse;
  title: string;
  merchantId: string;
  price: string;
  baselinePrice?: string;
}

interface PayPalCheckoutModalProps {
  session: CheckoutSession | null;
  vaultConnected?: boolean;
  onClose: () => void;
  onCaptured: (result: {
    intent: PurchaseIntent;
    capture?: CapturePayPalOrderResponse;
    message: string;
  }) => void;
}

const NEGATIVE_TEST_CODES = [
  { label: 'Live Sandbox Capture (None)', value: '' },
  { label: 'PayPal-Mock-Response: INSTRUMENT_DECLINED', value: 'INSTRUMENT_DECLINED' },
  { label: 'PayPal-Mock-Response: DUPLICATE_INVOICE_ID', value: 'DUPLICATE_INVOICE_ID' },
  { label: 'PayPal-Mock-Response: ORDER_NOT_APPROVED', value: 'ORDER_NOT_APPROVED' },
];

export function PayPalCheckoutModal({
  session,
  vaultConnected = false,
  onClose,
  onCaptured,
}: PayPalCheckoutModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const buttonsRef = useRef<HTMLDivElement>(null);
  const onCapturedRef = useRef(onCaptured);
  onCapturedRef.current = onCaptured;
  const titleId = useId();
  const mockSelectId = useId();
  const pressedOutsideRef = useRef(false);

  const [sdkStatus, setSdkStatus] = useState<'loading' | 'ready' | 'fallback'>('loading');
  const [capturing, setCapturing] = useState(false);
  const [vaultBusy, setVaultBusy] = useState(false);
  const [mockResponseCode, setMockResponseCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [statusNote, setStatusNote] = useState<string | null>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (session) {
      if (!dialog.open && typeof dialog.showModal === 'function') dialog.showModal();
    } else if (dialog.open && typeof dialog.close === 'function') {
      dialog.close();
      setMockResponseCode('');
    }
  }, [session]);

  const activeOrderId = session?.order.paypalOrderId ?? null;

  useEffect(() => {
    if (!activeOrderId) return;

    let cancelled = false;
    setSdkStatus('loading');
    setError(null);
    setStatusNote(null);

    const container = buttonsRef.current;
    if (container) container.innerHTML = '';

    void (async () => {
      try {
        const cfg = await paypalApi.config();
        if (cancelled) return;
        if (!cfg.configured || !cfg.clientId) {
          setSdkStatus('fallback');
          return;
        }
        const paypal = await loadPayPalSdk(cfg.clientId, cfg.currency || 'USD');
        if (cancelled || !paypal.Buttons || !buttonsRef.current) {
          if (!cancelled) setSdkStatus('fallback');
          return;
        }
        buttonsRef.current.innerHTML = '';
        await paypal
          .Buttons({
            style: { layout: 'vertical', color: 'blue', shape: 'pill', label: 'paypal', height: 44 },
            createOrder: async () => activeOrderId,
            onApprove: async (data) => {
              setCapturing(true);
              setError(null);
              try {
                const cap = await paypalApi.captureOrder(
                  data.orderID || activeOrderId,
                  mockResponseCode || undefined,
                );
                window.dispatchEvent(new CustomEvent('buybound:refresh'));
                onCapturedRef.current({
                  intent: cap.intent,
                  capture: cap,
                  message: cap.message,
                });
              } catch (err) {
                setError(toApiError(err).message);
              } finally {
                setCapturing(false);
              }
            },
            onCancel: () => {
              setStatusNote('PayPal checkout popup closed before approval. You can reopen it anytime.');
            },
            onError: () => {
              if (!cancelled) setSdkStatus('fallback');
            },
          })
          .render(buttonsRef.current);

        if (!cancelled) setSdkStatus('ready');
      } catch {
        if (!cancelled) setSdkStatus('fallback');
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [activeOrderId, mockResponseCode]);

  if (!session) return null;

  const isOutside = (e: React.PointerEvent<HTMLDialogElement>, dialog: HTMLDialogElement) => {
    const r = dialog.getBoundingClientRect();
    return e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom;
  };

  const handleManualSyncCapture = async () => {
    if (!session.order.paypalOrderId) return;
    setCapturing(true);
    setError(null);
    setStatusNote(null);
    try {
      const cap = await paypalApi.captureOrder(
        session.order.paypalOrderId,
        mockResponseCode || undefined,
      );
      window.dispatchEvent(new CustomEvent('buybound:refresh'));
      onCaptured({
        intent: cap.intent,
        capture: cap,
        message: cap.message,
      });
    } catch (err) {
      setError(toApiError(err).message);
    } finally {
      setCapturing(false);
    }
  };

  const handleOpenSandboxPopup = () => {
    const rawUrl =
      session.order.approveUrl ??
      (session.order.paypalOrderId
        ? `https://www.sandbox.paypal.com/checkoutnow?token=${session.order.paypalOrderId}&prompt=login`
        : null);
    if (!rawUrl) return;
    const url = rawUrl.includes('prompt=')
      ? rawUrl
      : `${rawUrl}${rawUrl.includes('?') ? '&' : '?'}prompt=login`;
    setStatusNote(
      'PayPal Sandbox login window opened. Enter your real Sandbox Personal buyer email and password to approve this order, then click Verify & Capture Order.',
    );
    window.open(url, 'paypal_sandbox_checkout', 'width=540,height=720');
  };

  const handleAutonomousVaultCheckout = async () => {
    setVaultBusy(true);
    setError(null);
    setStatusNote(null);
    try {
      if (session.order.paypalOrderId) {
        const cap = await paypalApi.captureOrder(
          session.order.paypalOrderId,
          mockResponseCode || undefined,
        );
        window.dispatchEvent(new CustomEvent('buybound:refresh'));
        onCaptured({
          intent: cap.intent,
          capture: cap,
          message: cap.message,
        });
        return;
      }

      const res = await purchasesApi.approveHold(session.order.intentId);
      window.dispatchEvent(new CustomEvent('buybound:refresh'));
      if (res.status === 'CAPTURED') {
        const refreshedIntent = await purchasesApi
          .listAllIntents()
          .then((list) => list.find((i) => i.id === res.intentId) ?? session.order.intent);
        onCaptured({
          intent: refreshedIntent,
          message: res.message,
        });
      } else {
        setStatusNote(res.message);
      }
    } catch (err) {
      setError(toApiError(err).message);
    } finally {
      setVaultBusy(false);
    }
  };

  const savings =
    session.baselinePrice && Number(session.baselinePrice) > Number(session.price)
      ? (Number(session.baselinePrice) - Number(session.price)).toFixed(2)
      : null;

  const isHold = session.order.guardianDecision?.decision === 'HOLD';

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
      className="m-auto w-full max-w-lg rounded-2xl border border-brand-border bg-white p-6 shadow-xl text-ink"
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone={isHold ? 'warning' : 'success'} dot>
              {isHold ? 'Shopper Hold Review Approved' : 'All Spending Limits Passed'}
            </Badge>
            <Badge tone="info">PayPal Sandbox Live</Badge>
          </div>
          <h2 id={titleId} className="mt-2 font-display text-xl font-bold text-ink">
            Complete PayPal Sandbox Checkout
          </h2>
          <p className="mt-1 text-xs text-muted">
            Sign in with your real PayPal Sandbox Personal buyer account via JS SDK or PayPal popup to execute live capture.
          </p>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="Close checkout dialog"
          className="rounded-lg p-1.5 text-sm text-muted hover:bg-brand-ivory hover:text-ink"
        >
          ✕
        </button>
      </div>

      {/* Verified Order Summary */}
      <div className="mt-4 rounded-xl border border-brand-border bg-brand-ivory p-4 text-sm">
        <div className="flex items-start justify-between gap-3">
          <div>
            <p className="font-display font-bold text-ink">{session.title}</p>
            <p className="mt-0.5 text-xs text-muted">
              Store: <strong className="text-ink">{formatStoreName(session.merchantId)}</strong>
              {savings && <> · Saves <strong className="text-success">{formatUsd(savings)}</strong> vs retail</>}
            </p>
          </div>
          <div className="text-right shrink-0">
            <span className="mono font-display text-xl font-bold text-ink">{formatUsd(session.price)}</span>
            {session.baselinePrice && Number(session.baselinePrice) > Number(session.price) && (
              <span className="block mono text-xs text-faint line-through">{formatUsd(session.baselinePrice)}</span>
            )}
          </div>
        </div>
        {session.order.paypalOrderId && (
          <p className="mt-2.5 border-t border-brand-border/70 pt-2 text-xs text-muted">
            PayPal Order ID: <span className="mono font-semibold text-ink">{session.order.paypalOrderId}</span>
          </p>
        )}
      </div>

      {error && (
        <div role="alert" className="mt-4 rounded-xl border border-danger/30 bg-danger-bg p-3 text-xs font-medium text-danger break-words">
          {error}
        </div>
      )}

      {statusNote && (
        <div role="status" className="mt-4 rounded-xl border border-border bg-background p-3 text-xs text-ink leading-relaxed">
          {statusNote}
        </div>
      )}

      {/* Option 1: Official PayPal Sandbox Button / Popup */}
      <div className="mt-5 space-y-3 rounded-xl border border-[#0070BA]/25 bg-[#0070BA]/5 p-4">
        <div className="flex items-center justify-between gap-2">
          <h3 className="font-display text-sm font-bold text-ink">
            1. Live PayPal Sandbox Login &amp; Capture
          </h3>
          <span className="text-[11px] font-semibold text-[#0070BA]">api-m.sandbox.paypal.com</span>
        </div>

        <div ref={buttonsRef} className="min-h-[46px]" />

        {sdkStatus === 'loading' && (
          <p className="text-xs text-muted">Loading official PayPal checkout button…</p>
        )}

        <div className="flex flex-col gap-2 sm:flex-row">
          <Button variant="paypal" onClick={handleOpenSandboxPopup} className="flex-1">
            Pay with PayPal Sandbox (Login Window) ↗
          </Button>
          <Button variant="secondary" loading={capturing} onClick={() => void handleManualSyncCapture()}>
            Verify &amp; Capture Order
          </Button>
        </div>

        <div>
          <label htmlFor={mockSelectId} className="block text-[11px] font-semibold text-muted mb-1">
            Negative Testing (`PayPal-Mock-Response` header)
          </label>
          <select
            id={mockSelectId}
            value={mockResponseCode}
            onChange={(e) => setMockResponseCode(e.target.value)}
            className="w-full min-h-[36px] rounded-lg border border-border bg-white px-2.5 text-xs text-ink"
          >
            {NEGATIVE_TEST_CODES.map((opt) => (
              <option key={opt.label} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Option 2: Autonomous Vault 1-Click */}
      <div className="mt-3 flex flex-col gap-2 rounded-xl border border-brand-border bg-brand-ivory/60 p-4">
        <div className="flex items-center justify-between gap-2">
          <h3 className="font-display text-xs font-bold uppercase tracking-wider text-muted">
            2. Autonomous Vault Mode
          </h3>
          <Badge tone={vaultConnected ? 'success' : 'neutral'}>
            {vaultConnected ? 'Vault Ready' : 'Requires Vault Setup'}
          </Badge>
        </div>
        <Button
          variant="secondary"
          disabled={!vaultConnected}
          loading={vaultBusy}
          onClick={() => void handleAutonomousVaultCheckout()}
        >
          Run 1-Click Autonomous Vault Checkout
        </Button>
      </div>
    </dialog>
  );
}
