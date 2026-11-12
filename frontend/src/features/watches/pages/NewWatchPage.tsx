import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Card } from '../../../components/ui/Card';
import { ErrorBanner } from '../../../components/ui/ErrorBanner';
import { toApiError } from '../../../services/api/client';
import { watchesApi } from '../../../services/api/endpoints';
import type { CreateWatchRequest } from '../../../services/api/types';
import { WatchForm } from '../components/WatchForm';

export function NewWatchPage() {
  const navigate = useNavigate();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (request: CreateWatchRequest) => {
    setSubmitting(true);
    setError(null);
    try {
      const watch = await watchesApi.create(request);
      navigate(`/watches/${watch.id}`);
    } catch (err) {
      setError(toApiError(err).message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <div>
        <Link to="/app/watches" className="text-xs font-medium text-brand-coral hover:underline">
          ← Back to Price Alerts
        </Link>
        <h1 className="mt-1 font-display text-2xl font-bold text-ink">New Price Alert</h1>
        <p className="mt-1 text-sm text-muted">
          Set the product you want, your target price, and the hard spending limits BuyBound will enforce.
        </p>
      </div>
      {error && <ErrorBanner message={error} />}
      <Card>
        <WatchForm submitting={submitting} submitLabel="Save Price Alert" onSubmit={handleSubmit} />
      </Card>
    </div>
  );
}
