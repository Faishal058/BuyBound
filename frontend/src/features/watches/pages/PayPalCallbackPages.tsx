import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { Spinner } from '../../../components/ui/Spinner';
import { mandatesApi, purchasesApi } from '../../../services/api/endpoints';
import { toApiError } from '../../../services/api/client';

export function VaultSuccessPage() {
  const [params] = useSearchParams();
  const [status, setStatus] = useState<'working' | 'done' | 'error'>('working');
  const [message, setMessage] = useState('Finishing your PayPal account connection…');

  useEffect(() => {
    const setupToken =
      params.get('approval_session_id') ??
      params.get('setup_token') ??
      sessionStorage.getItem('buybound_pending_setup_token');
    const mandateId = sessionStorage.getItem('buybound_pending_mandate_id');

    if (!setupToken || !mandateId) {
      setStatus('error');
      setMessage('Could not verify your PayPal approval session. Return to Wallet & Safety Settings and try connecting again.');
      return;
    }

    mandatesApi
      .completePayPal(mandateId, setupToken)
      .then((res) => {
        sessionStorage.removeItem('buybound_pending_setup_token');
        sessionStorage.removeItem('buybound_pending_mandate_id');
        setStatus('done');
        setMessage(`PayPal connected cleanly (${res.paypalPayerEmail ?? 'Verified account'}).`);
        window.dispatchEvent(new CustomEvent('buybound:refresh'));
        if (typeof BroadcastChannel !== 'undefined') {
          const channel = new BroadcastChannel('buybound-paypal');
          channel.postMessage({ type: 'paypal-vault-connected' });
          channel.close();
        }
      })
      .catch((err) => {
        setStatus('error');
        setMessage(toApiError(err).message);
      });
  }, [params]);

  return (
    <div className="mx-auto max-w-lg py-10">
      <Card title="PayPal Account Connection">
        {status === 'working' && <Spinner label={message} />}
        {status === 'done' && (
          <div className="rounded-xl border border-[#16A34A]/25 bg-success-bg p-4 text-sm text-success">
            {message}
          </div>
        )}
        {status === 'error' && (
          <div className="rounded-xl border border-[#DC2626]/25 bg-danger-bg p-4 text-sm text-danger">
            {message}
          </div>
        )}
        <div className="mt-5 flex gap-3">
          <Link to="/app/watches">
            <Button>Go to Price Alerts</Button>
          </Link>
          <Link to="/app/safety">
            <Button variant="secondary">Wallet &amp; Safety</Button>
          </Link>
        </div>
      </Card>
    </div>
  );
}

export function PayPalReturnPage() {
  const [params] = useSearchParams();
  const [status, setStatus] = useState<'working' | 'done' | 'error'>('working');
  const [message, setMessage] = useState('Confirming and completing your PayPal order…');

  useEffect(() => {
    const intentId = params.get('intentId');
    if (!intentId) {
      setStatus('error');
      setMessage('Missing order reference from PayPal.');
      return;
    }

    purchasesApi
      .syncPayPal(intentId)
      .then((res) => {
        setStatus('done');
        setMessage(res.message);
        window.dispatchEvent(new CustomEvent('buybound:refresh'));
      })
      .catch((err) => {
        setStatus('error');
        setMessage(toApiError(err).message);
      });
  }, [params]);

  return (
    <div className="mx-auto max-w-lg py-10">
      <Card title="PayPal Checkout Confirmation">
        {status === 'working' && <Spinner label={message} />}
        {status === 'done' && (
          <div className="rounded-xl border border-[#16A34A]/25 bg-success-bg p-4 text-sm text-success">
            {message}
          </div>
        )}
        {status === 'error' && (
          <div className="rounded-xl border border-[#DC2626]/25 bg-danger-bg p-4 text-sm text-danger">
            {message}
          </div>
        )}
        <div className="mt-5">
          <Link to="/app/purchases">
            <Button>View Purchases &amp; Receipts</Button>
          </Link>
        </div>
      </Card>
    </div>
  );
}
