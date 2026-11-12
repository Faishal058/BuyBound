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
import { watchesApi } from '../../../services/api/endpoints';
import { formatDateTime, formatMoney, formatStoreName } from '../../shared/meta';
import { IntentsTable } from '../components/IntentsTable';
import { MandateCard } from '../components/MandateCard';
import { WatchStatusBadge } from '../components/WatchStatusBadge';

export function WatchDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const watch = useResource(() => watchesApi.get(id), [id]);
  const intents = useResource(() => watchesApi.intents(id), [id]);
  const [confirmAction, setConfirmAction] = useState<'pause' | 'revoke' | null>(null);
  const [acting, setActing] = useState(false);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const mandate = useResource(
    () =>
      watchesApi.mandate(id).catch((err: unknown) => {
        if (isApiError(err) && err.status === 404) return null;
        if (isApiError(err) && err.status === 409) return null;
        throw err;
      }),
    [id],
  );
  const mandateLoadError =
    mandate.error && mandate.error.status !== 404 && mandate.error.status !== 409 ? mandate.error.message : null;

  const handleConfirm = async () => {
    if (!confirmAction) return;
    setActing(true);
    setActionError(null);
    setActionMessage(null);
    try {
      if (confirmAction === 'pause') {
        await watchesApi.pause(id);
        setActionMessage('Watch paused. Automatic buying is turned off for this item.');
      } else {
        await watchesApi.revoke(id);
        setActionMessage('Watch disabled permanently.');
      }
      watch.reload();
      intents.reload();
    } catch (err) {
      setActionError(toApiError(err).message);
    } finally {
      setActing(false);
      setConfirmAction(null);
    }
  };

  if (watch.loading) return <Spinner label="Loading price watch…" />;
  if (watch.error || !watch.data) {
    return <ErrorBanner message={watch.error?.message ?? 'Watch not found.'} onRetry={watch.reload} />;
  }

  const w = watch.data;
  const canAct = w.status === 'ACTIVE';

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <Link to="/app/watches" className="text-xs font-medium text-brand-coral hover:underline">
            ← Back to Price Alerts
          </Link>
          <div className="mt-1 flex items-center gap-3">
            <h1 className="font-display text-2xl font-bold text-ink">{w.productName}</h1>
            <WatchStatusBadge status={w.status} />
          </div>
          <p className="mt-1 text-sm text-muted">
            Preferred store: {formatStoreName(w.merchantId)} · Buy below {formatMoney(w.targetPrice, w.currency)} ·
            Updated {formatDateTime(w.updatedAt)}
          </p>
        </div>
        <div className="flex gap-3">
          <Button variant="secondary" disabled={!canAct} onClick={() => setConfirmAction('pause')}>
            Pause Watch
          </Button>
          <Button variant="danger" disabled={!canAct} onClick={() => setConfirmAction('revoke')}>
            Disable Permanently
          </Button>
        </div>
      </div>

      {actionMessage && (
        <div role="status" className="rounded-lg border border-[#16A34A]/25 bg-success-bg px-4 py-3 text-sm text-success">
          {actionMessage}
        </div>
      )}
      {actionError && <ErrorBanner message={actionError} />}

      {mandate.loading && <Spinner label="Loading spending rules…" />}
      {mandateLoadError && <ErrorBanner message={mandateLoadError} onRetry={mandate.reload} />}
      {!mandate.loading && !mandateLoadError && mandate.data && <MandateCard mandate={mandate.data} />}
      {!mandate.loading && !mandateLoadError && !mandate.data && (
        <EmptyState
          title="Spending rules are currently paused"
          description="Re-enable buying inside Wallet & Safety Settings to reactivate automated buying for this item."
        />
      )}

      <Card
        title="Checked Deals & Purchase History"
        subtitle="Deals evaluated under this watch."
        action={
          <Link to="/app/purchases">
            <Button variant="secondary">Open Purchases &amp; Receipts</Button>
          </Link>
        }
      >
        {intents.loading && <Spinner label="Loading history…" />}
        {intents.error && <ErrorBanner message={intents.error.message} onRetry={intents.reload} />}
        {!intents.loading && !intents.error && intents.data && intents.data.length === 0 && (
          <EmptyState
            title="No deals checked yet"
            description='Return to Price Alerts and click "Check Stores Now" to scan live store prices.'
          />
        )}
        {!intents.loading && !intents.error && intents.data && intents.data.length > 0 && (
          <IntentsTable intents={intents.data} />
        )}
      </Card>

      <Dialog
        open={confirmAction !== null}
        title={confirmAction === 'pause' ? 'Pause this price watch?' : 'Permanently disable this watch?'}
        confirmLabel={confirmAction === 'pause' ? 'Pause Watch' : 'Disable Watch'}
        confirmVariant={confirmAction === 'revoke' ? 'danger' : 'primary'}
        confirming={acting}
        onConfirm={handleConfirm}
        onClose={() => setConfirmAction(null)}
      >
        {confirmAction === 'pause' ? (
          <p>BuyBound will stop checking prices or buying this item automatically until you turn it back on.</p>
        ) : (
          <p>Disabling turns off future automated buying for this item permanently.</p>
        )}
      </Dialog>
    </div>
  );
}
