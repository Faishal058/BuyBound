import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { PayPalCheckoutModal, type CheckoutSession } from '../PayPalCheckoutModal';
import { VerifiedReceiptModal } from '../VerifiedReceiptModal';
import { paypalApi } from '../../../services/api/endpoints';
import { useState } from 'react';
import type { CapturePayPalOrderResponse, PurchaseIntent } from '../../../services/api/types';

vi.mock('../../../services/api/endpoints', async () => {
  const actual = await vi.importActual<typeof import('../../../services/api/endpoints')>(
    '../../../services/api/endpoints',
  );
  return {
    ...actual,
    paypalApi: {
      ...actual.paypalApi,
      config: vi.fn(),
      captureOrder: vi.fn(),
    },
  };
});

function buildSession(): CheckoutSession {
  return {
    title: 'Sony WH-1000XM5 Wireless Headphones',
    merchantId: 'bestbuy.com',
    price: '179.00',
    baselinePrice: '399.99',
    order: {
      intentId: 'intent-1',
      paypalOrderId: '8L8915293N1545906',
      approveUrl: 'https://www.sandbox.paypal.com/checkoutnow?token=8L8915293N1545906&prompt=login',
      blocked: false,
      message: 'Ready',
      intent: {
        id: 'intent-1',
        watchId: 'watch-1',
        mandateId: 'mandate-1',
        mandateVersion: 1,
        status: 'ORDER_CREATED',
        merchantId: 'bestbuy.com',
        canonicalSku: 'sony-xm5',
        quantity: 1,
        currency: 'USD',
        maxTotal: '179.00',
        policyVersion: '1.0',
        evidenceHash: 'hash-1',
        idempotencyKey: 'idem-1',
        providerOrderId: '8L8915293N1545906',
        createdAt: '2026-11-12T00:00:00Z',
        updatedAt: '2026-11-12T00:00:00Z',
      },
    },
  };
}

function Harness({ vaultConnected = false }: { vaultConnected?: boolean }) {
  const [session, setSession] = useState<CheckoutSession | null>(() => buildSession());
  const [receipt, setReceipt] = useState<{
    intent: PurchaseIntent;
    capture?: CapturePayPalOrderResponse;
  } | null>(null);

  return (
    <div>
      <PayPalCheckoutModal
        session={session}
        vaultConnected={vaultConnected}
        onClose={() => setSession(null)}
        onCaptured={({ intent, capture }) => {
          setSession(null);
          setReceipt({ intent, capture });
        }}
      />
      <VerifiedReceiptModal
        intent={receipt?.intent ?? null}
        productTitle="Sony WH-1000XM5 Wireless Headphones"
        payerEmail={receipt?.capture?.payerEmail}
        payerName={receipt?.capture?.payerName}
        newBalance={receipt?.capture?.newSandboxBalance}
        onClose={() => setReceipt(null)}
      />
    </div>
  );
}

describe('PayPalCheckoutModal & VerifiedReceiptModal live Sandbox flow', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    if (!HTMLDialogElement.prototype.showModal) {
      HTMLDialogElement.prototype.showModal = function showModal() {
        this.setAttribute('open', '');
      };
    }
    if (!HTMLDialogElement.prototype.close) {
      HTMLDialogElement.prototype.close = function close() {
        this.removeAttribute('open');
      };
    }
    vi.mocked(paypalApi.config).mockResolvedValue({
      clientId: '',
      mode: 'sandbox',
      currency: 'USD',
      configured: false,
      oauthAuthorizeUrl: '',
    });
    vi.mocked(paypalApi.captureOrder).mockResolvedValue({
      intent: {
        id: 'intent-1',
        watchId: 'watch-1',
        mandateId: 'mandate-1',
        mandateVersion: 1,
        status: 'CAPTURED',
        merchantId: 'bestbuy.com',
        canonicalSku: 'sony-xm5',
        quantity: 1,
        currency: 'USD',
        maxTotal: '179.00',
        policyVersion: '1.0',
        evidenceHash: 'hash-1',
        idempotencyKey: 'idem-1',
        providerOrderId: '8L8915293N1545906',
        paypalCaptureId: 'CAP-LIVE-8L8915293N',
        createdAt: '2026-11-12T00:00:00Z',
        updatedAt: '2026-11-12T00:01:00Z',
      },
      captureId: 'CAP-LIVE-8L8915293N',
      paypalOrderId: '8L8915293N1545906',
      payerEmail: 'realbuyer@example.com',
      payerName: 'Alex Rivera',
      deductedAmount: '179.00',
      message: 'Live PayPal Sandbox capture completed (CAP-LIVE-8L8915293N)',
    });
  });

  it('requires Vault setup for 1-click checkout and forwards optional PayPal-Mock-Response negative tests', async () => {
    render(<Harness vaultConnected={false} />);

    expect(screen.getByRole('button', { name: /Run 1-Click Autonomous Vault Checkout/i })).toBeDisabled();

    fireEvent.change(screen.getByLabelText(/Negative Testing/i), {
      target: { value: 'INSTRUMENT_DECLINED' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Verify & Capture Order/i }));

    await waitFor(() => {
      expect(paypalApi.captureOrder).toHaveBeenCalledWith('8L8915293N1545906', 'INSTRUMENT_DECLINED');
    });

    await waitFor(() => {
      expect(screen.getByText('PayPal Purchase Receipt')).toBeInTheDocument();
      expect(screen.getByText('CAP-LIVE-8L8915293N')).toBeInTheDocument();
      expect(screen.getByText('Alex Rivera (realbuyer@example.com)')).toBeInTheDocument();
      expect(screen.getByText('PayPal Sandbox Live Order')).toBeInTheDocument();
    });
  });

  it('settles via Run 1-Click Autonomous Vault Checkout only when verified Vault is connected', async () => {
    render(<Harness vaultConnected />);

    const vaultBtn = screen.getByRole('button', { name: /Run 1-Click Autonomous Vault Checkout/i });
    expect(vaultBtn).not.toBeDisabled();
    fireEvent.click(vaultBtn);

    await waitFor(() => {
      expect(paypalApi.captureOrder).toHaveBeenCalledWith('8L8915293N1545906', undefined);
    });

    await waitFor(() => {
      expect(screen.getByText('PayPal Purchase Receipt')).toBeInTheDocument();
      expect(screen.getByText('CAP-LIVE-8L8915293N')).toBeInTheDocument();
    });
  });
});
