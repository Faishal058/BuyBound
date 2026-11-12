import { useEffect, useId, useRef, useState } from 'react';
import { Button } from '../ui/Button';
import { loadPayPalSdk } from '../../lib/paypalSdk';
import { mandatesApi, paypalApi } from '../../services/api/endpoints';
import { toApiError } from '../../services/api/client';

interface PayPalVaultModalProps {
  open: boolean;
  mandateId: string | null;
  connected: boolean;
  vaultToken?: string;
  payerEmail?: string;
  payerId?: string;
  sandboxBalance?: string;
  onClose: () => void;
  onConnected: () => void;
}

export function PayPalVaultModal({
  open,
  mandateId,
  connected,
  vaultToken,
  payerEmail,
  payerId,
  onClose,
  onConnected,
}: PayPalVaultModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const pressedOutsideRef = useRef(false);

  const [step, setStep] = useState<'idle' | 'pending_approval' | 'done'>('idle');
  const [setupTokenId, setSetupTokenId] = useState<string | null>(null);
  const [approvalUrl, setApprovalUrl] = useState<string | null>(null);
  const [oauthAuthorizeUrl, setOauthAuthorizeUrl] = useState<string>('');
  const [sdkReady, setSdkReady] = useState(false);
  const [loading, setLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open) {
      if (!dialog.open && typeof dialog.showModal === 'function') dialog.showModal();
      setError(null);
      void paypalApi
        .config()
        .then(async (cfg) => {
          if (cfg.oauthAuthorizeUrl) setOauthAuthorizeUrl(cfg.oauthAuthorizeUrl);
          if (cfg.configured && cfg.clientId) {
            await loadPayPalSdk(cfg.clientId, cfg.currency || 'USD').catch(() => undefined);
            setSdkReady(true);
          }
        })
        .catch(() => undefined);
    } else if (dialog.open && typeof dialog.close === 'function') {
      dialog.close();
    }
  }, [open]);

  useEffect(() => {
    if (!open || typeof BroadcastChannel === 'undefined') return;
    const channel = new BroadcastChannel('buybound-paypal');
    channel.onmessage = () => {
      setStep('done');
      setStatusMessage('PayPal Sandbox verification received.');
      onConnected();
    };
    return () => channel.close();
  }, [onConnected, open]);

  if (!open) return null;

  const isOutside = (e: React.PointerEvent<HTMLDialogElement>, dialog: HTMLDialogElement) => {
    const r = dialog.getBoundingClientRect();
    return e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom;
  };

  const handleOpenOAuthLogin = () => {
    if (!oauthAuthorizeUrl) {
      setError('PayPal Client ID is not configured on the backend (.env PAYPAL_CLIENT_ID).');
      return;
    }
    setError(null);
    setStatusMessage(
      'PayPal Sandbox login window opened (www.sandbox.paypal.com). Sign in with your Sandbox Personal buyer email and password.',
    );
    window.open(oauthAuthorizeUrl, 'paypal_sandbox_login', 'width=540,height=720');
  };

  const handleInitSetup = async () => {
    if (!mandateId) {
      setError('Create a price watch first before setting up a PayPal Vault token.');
      return;
    }
    setLoading(true);
    setError(null);
    setStatusMessage(null);
    try {
      const res = await mandatesApi.connectPayPal(mandateId);
      setSetupTokenId(res.setupTokenId);
      setApprovalUrl(res.approvalUrl);
      sessionStorage.setItem('buybound_pending_setup_token', res.setupTokenId);
      sessionStorage.setItem('buybound_pending_mandate_id', mandateId);
      setStep('pending_approval');
      setStatusMessage(
        'Setup token issued by PayPal Sandbox (/v3/vault/setup-tokens). Sign in and approve in PayPal below, then verify.',
      );
    } catch (err) {
      setError(toApiError(err).message);
    } finally {
      setLoading(false);
    }
  };

  const handleExchangeToken = async () => {
    if (!mandateId || !setupTokenId) return;
    setLoading(true);
    setError(null);
    try {
      await mandatesApi.completePayPal(mandateId, setupTokenId);
      sessionStorage.removeItem('buybound_pending_setup_token');
      sessionStorage.removeItem('buybound_pending_mandate_id');
      setStep('done');
      setStatusMessage('Verified PayPal Sandbox Vault payment token linked cleanly.');
      window.dispatchEvent(new CustomEvent('buybound:refresh'));
      onConnected();
    } catch (err) {
      setError(toApiError(err).message);
    } finally {
      setLoading(false);
    }
  };

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
      className="m-auto w-full max-w-lg rounded-2xl border border-border bg-surface p-6 shadow-xl text-ink"
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 id={titleId} className="font-display text-xl font-bold text-ink">
            Connect PayPal Sandbox Account
          </h2>
          <p className="mt-1 text-sm text-muted leading-relaxed">
            Authenticate directly via live PayPal Sandbox (`www.sandbox.paypal.com`) or authorize a Vault v3 payment
            token verified against `api-m.sandbox.paypal.com`.
          </p>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="Close PayPal Sandbox Login dialog"
          className="rounded-lg p-1.5 text-sm text-muted hover:bg-surface-muted hover:text-ink"
        >
          ✕
        </button>
      </div>

      {connected ? (
        <div className="mt-4 rounded-xl border border-[#16A34A]/25 bg-success-bg p-4 text-sm">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="font-semibold text-success">PayPal Sandbox Connected</p>
            <span className="text-xs font-semibold text-[#0070BA]">Verified by PayPal</span>
          </div>
          {payerEmail && <p className="mt-1 text-xs text-ink">Buyer Email: {payerEmail}</p>}
          {payerId && <p className="mono mt-0.5 text-[11px] text-muted">Payer ID: {payerId}</p>}
          {vaultToken && <p className="mono mt-0.5 text-[11px] text-muted">Vault Token: {vaultToken}</p>}
        </div>
      ) : (
        <div className="mt-4 rounded-xl border border-brand-border bg-brand-ivory p-3.5 text-xs text-muted">
          Status: <strong className="text-ink">Not connected yet</strong> · Sign in with your real PayPal Sandbox
          Personal buyer account below.
        </div>
      )}

      {error && (
        <div role="alert" className="mt-4 rounded-xl border border-[#DC2626]/25 bg-danger-bg p-3 text-sm text-danger">
          {error}
        </div>
      )}

      {statusMessage && (
        <div role="status" className="mt-4 rounded-xl border border-border bg-background p-3 text-sm text-ink">
          {statusMessage}
        </div>
      )}

      <div className="mt-5 flex flex-col gap-3 rounded-xl border border-[#0070BA]/25 bg-[#0070BA]/5 p-4">
        <div className="flex items-center justify-between gap-2">
          <p className="text-xs font-bold uppercase tracking-wider text-[#0070BA]">
            Live PayPal Sandbox OAuth &amp; Vault v3
          </p>
          {sdkReady && (
            <span className="text-[11px] font-medium text-success">JS SDK Loaded</span>
          )}
        </div>

        <div className="flex flex-col gap-2 sm:flex-row">
          <Button variant="paypal" onClick={handleOpenOAuthLogin} className="flex-1">
            Continue with PayPal Sandbox ↗
          </Button>
          {step === 'idle' && (
            <Button variant="secondary" onClick={handleInitSetup} loading={loading} disabled={!mandateId}>
              Setup Auto-Buy Vault Token
            </Button>
          )}
        </div>

        {step === 'pending_approval' && (
          <div className="mt-2 flex flex-col gap-3 rounded-xl border border-border bg-white p-4">
            <p className="text-xs font-medium text-muted">
              Vault Setup Token: <span className="mono text-ink">{setupTokenId}</span>
            </p>
            {approvalUrl && (
              <button
                type="button"
                onClick={() => {
                  window.open(approvalUrl, 'paypal_vault_approval', 'width=540,height=720');
                }}
                className="inline-flex min-h-[40px] items-center justify-center gap-2 rounded-lg bg-[#0070BA] px-4 py-2 text-xs font-semibold text-white hover:bg-[#005EA6] transition-colors"
              >
                Sign in &amp; Approve Vault in PayPal ↗
              </button>
            )}
            <Button onClick={handleExchangeToken} loading={loading}>
              Verify &amp; Link Vault Token
            </Button>
          </div>
        )}
      </div>

      <div className="mt-6 flex justify-end">
        <Button variant="secondary" onClick={onClose}>
          Close
        </Button>
      </div>
    </dialog>
  );
}
