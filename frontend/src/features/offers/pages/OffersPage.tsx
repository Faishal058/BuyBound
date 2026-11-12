import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { OvernightOfferAuditGrid } from '../../../components/tables/OvernightOfferAuditGrid';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { ErrorBanner } from '../../../components/ui/ErrorBanner';
import { Spinner } from '../../../components/ui/Spinner';
import { toApiError } from '../../../services/api/client';
import { purchasesApi } from '../../../services/api/endpoints';
import type { HuntedOffer, PurchaseIntent } from '../../../services/api/types';

export function OffersPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const urlQuery = searchParams.get('q') ?? '';
  const [offers, setOffers] = useState<HuntedOffer[]>([]);
  const [intents, setIntents] = useState<PurchaseIntent[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const loadAll = useCallback(async () => {
    setLoadError(null);
    try {
      const [offerList, intentList] = await Promise.all([
        purchasesApi.listAllOffers(),
        purchasesApi.listAllIntents(),
      ]);
      setOffers(offerList);
      setIntents(intentList);
    } catch (err) {
      setLoadError(toApiError(err).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadAll();
    const onRefresh = () => void loadAll();
    window.addEventListener('buybound:refresh', onRefresh);
    return () => window.removeEventListener('buybound:refresh', onRefresh);
  }, [loadAll]);

  const counts = useMemo(() => {
    let approved = 0;
    let hold = 0;
    let blocked = 0;
    for (const o of offers) {
      if (o.guardianDecision === 'ALLOW') approved++;
      else if (o.guardianDecision === 'HOLD') hold++;
      else if (o.guardianDecision === 'BLOCK') blocked++;
    }
    return { total: offers.length, approved, hold, blocked };
  }, [offers]);

  const handleClearUrlQuery = useCallback(() => {
    if (urlQuery) {
      setSearchParams({});
    }
  }, [setSearchParams, urlQuery]);

  return (
    <div className="flex flex-col gap-6">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-bold text-ink">Store Offers &amp; Compare</h1>
          <p className="mt-1 text-sm text-muted">
            Live prices checked across retailers, compared side-by-side with seller trust verification and spending guardrails.
          </p>
        </div>
        <Button variant="secondary" onClick={() => void loadAll()}>
          Refresh Offers
        </Button>
      </div>

      {/* Summary Strip */}
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <div className="rounded-xl border border-border bg-surface p-4 shadow-custom">
          <p className="text-xs font-medium text-muted">Offers Checked</p>
          <p className="mono mt-1 text-2xl font-bold text-ink">{counts.total}</p>
        </div>
        <div className="rounded-xl border border-border bg-surface p-4 shadow-custom">
          <p className="text-xs font-medium text-muted">Approved</p>
          <p className="mono mt-1 text-2xl font-bold text-success">{counts.approved}</p>
        </div>
        <div className="rounded-xl border border-border bg-surface p-4 shadow-custom">
          <p className="text-xs font-medium text-muted">Needs My Approval</p>
          <p className="mono mt-1 text-2xl font-bold text-warning">{counts.hold}</p>
        </div>
        <div className="rounded-xl border border-border bg-surface p-4 shadow-custom">
          <p className="text-xs font-medium text-muted">Scams &amp; Traps Blocked</p>
          <p className="mono mt-1 text-2xl font-bold text-danger">{counts.blocked}</p>
        </div>
      </div>

      <Card>
        {loading && <Spinner label="Loading live store offers…" />}
        {loadError && !loading && <ErrorBanner message={loadError} onRetry={() => void loadAll()} />}

        {!loading && !loadError && (
          <OvernightOfferAuditGrid
            offers={offers}
            intents={intents}
            onRefresh={loadAll}
            initialQuery={urlQuery}
            onClearUrlQuery={handleClearUrlQuery}
          />
        )}
      </Card>
    </div>
  );
}
