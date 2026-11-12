import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { Dialog } from '../../../components/ui/Dialog';
import { EmptyState } from '../../../components/ui/EmptyState';
import { ErrorBanner } from '../../../components/ui/ErrorBanner';
import { Spinner } from '../../../components/ui/Spinner';
import { useResource } from '../../../hooks/useResource';
import { isApiError, toApiError } from '../../../services/api/client';
import { intentsApi } from '../../../services/api/endpoints';
import { formatDateTime, formatMoney, formatStoreName } from '../../shared/meta';
import { DecisionTrace } from '../components/DecisionTrace';
import { IntentStatusBadge } from '../components/IntentStatusBadge';
import { OfferSnapshotCard } from '../components/OfferSnapshotCard';
import { StatusTimeline } from '../components/StatusTimeline';

export function IntentDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const intent = useResource(() => intentsApi.get(id), [id]);
  const decision = useResource(
    () =>
      intentsApi.decision(id).catch((err: unknown) => {
        if (isApiError(err) && err.status === 404) return null;
        throw err;
      }),
    [id],
  );

  const [confirmApprove, setConfirmApprove] = useState(false);
  const [acting, setActing] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const refreshAll = () => {
    intent.reload();
    decision.reload();
  };

  const handleApprove = async () => {
    setActing(true);
    setActionError(null);
    setNotice(null);
    try {
      const response = await intentsApi.approve(id);
      setNotice(response.message || 'Purchase approved and verified against your spending rules.');
      refreshAll();
    } catch (err) {
      setActionError(toApiError(err).message);
    } finally {
      setActing(false);
      setConfirmApprove(false);
    }
  };

  if (intent.loading) return <Spinner label="Loading purchase details…" />;
  if (intent.error || !intent.data) {
    return <ErrorBanner message={intent.error?.message ?? 'Purchase not found.'} onRetry={intent.reload} />;
  }

  const data = intent.data;
  const canApprove = data.status === 'AWAITING_APPROVAL' || data.status === 'HELD_FOR_APPROVAL';

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <Link to="/app/purchases" className="text-xs font-medium text-brand-coral hover:underline">
            ← Back to Purchases &amp; Receipts
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="font-display text-2xl font-bold text-ink">Purchase Details</h1>
            <IntentStatusBadge status={data.status} />
          </div>
          <p className="mono mt-1 text-xs text-faint">{data.id}</p>
        </div>
        {canApprove && (
          <Button variant="paypal" onClick={() => setConfirmApprove(true)}>
            Approve &amp; Buy
          </Button>
        )}
      </div>

      {notice && (
        <div role="status" className="rounded-lg border border-[#16A34A]/25 bg-success-bg px-4 py-3 text-sm text-success">
          {notice}
        </div>
      )}
      {actionError && <ErrorBanner message={actionError} />}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card title="Order Summary">
          <dl className="flex flex-col">
            {[
              ['Store', formatStoreName(data.merchantId)],
              data.canonicalSku ? ['Item / SKU', data.canonicalSku] : null,
              ['Quantity', String(data.quantity)],
              ['Total', formatMoney(data.maxTotal, data.currency)],
              data.providerOrderId ? ['PayPal Order ID', data.providerOrderId] : null,
              ['Created', formatDateTime(data.createdAt)],
              ['Updated', formatDateTime(data.updatedAt)],
            ]
              .filter((row): row is [string, string] => row !== null)
              .map(([label, value]) => (
                <div key={label} className="flex items-baseline justify-between gap-4 border-b border-border py-2.5 last:border-0">
                  <dt className="text-sm text-muted">{label}</dt>
                  <dd className="mono text-sm font-medium text-ink text-right break-all">{value}</dd>
                </div>
              ))}
          </dl>
        </Card>
        <Card title="Progress">
          <StatusTimeline status={data.status} />
        </Card>
      </div>

      {decision.loading && <Spinner label="Loading safety check results…" />}
      {decision.error && <ErrorBanner message={decision.error.message} onRetry={decision.reload} />}
      {!decision.loading && !decision.error && decision.data && <DecisionTrace decision={decision.data} />}
      {!decision.loading && !decision.error && !decision.data && (
        <EmptyState title="No safety check recorded yet" description="Checks appear here once a store offer is evaluated." />
      )}

      {data.offerSnapshot && <OfferSnapshotCard offer={data.offerSnapshot} />}

      <Dialog
        open={confirmApprove}
        title="Approve this purchase?"
        confirmLabel="Approve and verify"
        confirmVariant="paypal"
        confirming={acting}
        onConfirm={handleApprove}
        onClose={() => setConfirmApprove(false)}
      >
        <p>BuyBound will re-check current store pricing and spending limits before PayPal charges anything.</p>
      </Dialog>
    </div>
  );
}
