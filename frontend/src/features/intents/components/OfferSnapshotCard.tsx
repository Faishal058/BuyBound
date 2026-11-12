import { Card } from '../../../components/ui/Card';
import { formatDateTime, formatMoney, formatStoreName } from '../../shared/meta';
import type { OfferSnapshot } from '../../../services/api/types';

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-4 border-b border-border py-2.5 last:border-0">
      <dt className="text-sm text-muted">{label}</dt>
      <dd className="mono text-sm font-medium text-ink text-right">{value}</dd>
    </div>
  );
}

export function OfferSnapshotCard({ offer }: { offer: OfferSnapshot }) {
  return (
    <Card title="Store Listing Snapshot" subtitle="Exact store offer details verified at the time of check.">
      <dl>
        <Row label="Store" value={formatStoreName(offer.merchantId)} />
        {offer.canonicalSku && <Row label="Item / SKU" value={offer.canonicalSku} />}
        <Row label="Quantity" value={String(offer.quantity)} />
        <Row label="Offer price" value={formatMoney(offer.totalPrice, offer.currency)} />
        {offer.baselinePrice && <Row label="Normal retail price" value={formatMoney(offer.baselinePrice, offer.currency)} />}
        <Row label="Checked at" value={formatDateTime(offer.observedAt)} />
      </dl>
    </Card>
  );
}
