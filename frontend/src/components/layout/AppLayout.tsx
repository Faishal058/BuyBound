import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Outlet, useSearchParams } from 'react-router-dom';
import { Header } from './Header';
import { VerifiedReceiptModal } from '../modals/VerifiedReceiptModal';
import { toApiError } from '../../services/api/client';
import { paypalApi } from '../../services/api/endpoints';
import type { CapturePayPalOrderResponse } from '../../services/api/types';

interface AppLayoutProps {
  children?: ReactNode;
}

export function AppLayout({ children }: AppLayoutProps) {
  const [searchParams, setSearchParams] = useSearchParams();
  const processedTokenRef = useRef<string | null>(null);
  const [captureBanner, setCaptureBanner] = useState<{
    tone: 'success' | 'danger' | 'info';
    text: string;
  } | null>(null);
  const [receipt, setReceipt] = useState<CapturePayPalOrderResponse | null>(null);

  useEffect(() => {
    const token = searchParams.get('token');
    const payerId = searchParams.get('PayerID');
    const isCancel = searchParams.get('paypal_cancel') === 'true';
    const isOAuth = searchParams.get('paypal_oauth') === 'true';
    const oauthCode = searchParams.get('code');

    if (isOAuth && oauthCode && processedTokenRef.current !== oauthCode) {
      processedTokenRef.current = oauthCode;
      void (async () => {
        try {
          const tel = await paypalApi.oauthCallback(oauthCode);
          setCaptureBanner({
            tone: 'success',
            text: `Signed in with PayPal Sandbox (${tel.paypalPayerEmail ?? tel.paypalPayerId ?? 'Connected'}) · Wallet: $${Number(tel.sandboxBalance ?? '5000').toFixed(2)} USD`,
          });
          window.dispatchEvent(new CustomEvent('buybound:refresh'));
          if (typeof BroadcastChannel !== 'undefined') {
            const channel = new BroadcastChannel('buybound-paypal');
            channel.postMessage({ type: 'paypal-oauth-connected', payerId: tel.paypalPayerId });
            channel.close();
          }
        } catch (err) {
          setCaptureBanner({
            tone: 'danger',
            text: toApiError(err).message,
          });
        } finally {
          const next = new URLSearchParams(searchParams);
          next.delete('paypal_oauth');
          next.delete('code');
          next.delete('scope');
          setSearchParams(next, { replace: true });
        }
      })();
      return;
    }

    if (isCancel) {
      setCaptureBanner({
        tone: 'info',
        text: 'PayPal Sandbox checkout was cancelled. No funds were captured.',
      });
      const next = new URLSearchParams(searchParams);
      next.delete('paypal_cancel');
      next.delete('intentId');
      next.delete('token');
      setSearchParams(next, { replace: true });
      return;
    }

    if (!token || !payerId || processedTokenRef.current === token) {
      return;
    }

    processedTokenRef.current = token;
    void (async () => {
      try {
        const res = await paypalApi.captureOrder(token);
        setReceipt(res);
        setCaptureBanner({
          tone: 'success',
          text: res.message,
        });
        window.dispatchEvent(new CustomEvent('buybound:refresh'));
      } catch (err) {
        setCaptureBanner({
          tone: 'danger',
          text: toApiError(err).message,
        });
      } finally {
        const next = new URLSearchParams(searchParams);
        next.delete('token');
        next.delete('PayerID');
        next.delete('paypal_return');
        next.delete('intentId');
        setSearchParams(next, { replace: true });
      }
    })();
  }, [searchParams, setSearchParams]);

  return (
    <div className="flex min-h-screen flex-col bg-background text-ink">
      <Header />
      {captureBanner && (
        <div
          role={captureBanner.tone === 'danger' ? 'alert' : 'status'}
          className={`border-b px-4 py-3 text-sm font-medium ${
            captureBanner.tone === 'success'
              ? 'border-success/25 bg-success-bg text-success'
              : captureBanner.tone === 'danger'
                ? 'border-danger/25 bg-danger-bg text-danger'
                : 'border-brand-border bg-brand-peach-soft text-ink'
          }`}
        >
          <div className="page-shell flex flex-wrap items-center justify-between gap-3">
            <span>{captureBanner.text}</span>
            <button
              type="button"
              onClick={() => setCaptureBanner(null)}
              className="text-xs underline hover:no-underline"
            >
              Dismiss
            </button>
          </div>
        </div>
      )}
      <main id="main-content" tabIndex={-1} className="page-shell w-full flex-1 py-8">
        {children ?? <Outlet />}
      </main>
      <VerifiedReceiptModal
        intent={receipt?.intent ?? null}
        productTitle={receipt?.intent.canonicalSku}
        payerEmail={receipt?.payerEmail}
        payerName={receipt?.payerName}
        newBalance={receipt?.newSandboxBalance}
        onClose={() => setReceipt(null)}
      />
    </div>
  );
}
