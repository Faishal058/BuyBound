import { Card } from '../../../components/ui/Card';
import { formatMoney, formatStoreName } from '../../shared/meta';
import type { Mandate } from '../../../services/api/types';

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-4 border-b border-border py-2.5 last:border-0">
      <dt className="text-sm text-muted">{label}</dt>
      <dd className="mono text-sm font-medium text-ink text-right break-all">{value}</dd>
    </div>
  );
}

/** Read-only view of spending rules configured for a price watch. */
export function MandateCard({ mandate }: { mandate: Mandate }) {
  return (
    <Card title="Spending & Safety Rules">
      <dl>
        <Row label="Status" value={mandate.status === 'ACTIVE' ? 'Active' : 'Paused'} />
        <Row label="Max per-item limit" value={formatMoney(mandate.maxUnitPrice, mandate.currency)} />
        <Row label="Max order total" value={formatMoney(mandate.maxTotal, mandate.currency)} />
        <Row
          label="Trusted stores"
          value={mandate.allowedMerchantIds.map(formatStoreName).join(', ')}
        />
        {mandate.maxWeeklySpend && (
          <Row label="Weekly spending limit" value={formatMoney(mandate.maxWeeklySpend, mandate.currency)} />
        )}
        <Row
          label="Unknown stores"
          value={mandate.newMerchantBehavior === 'HOLD' ? 'Hold for your approval' : 'Block automatically'}
        />
      </dl>
    </Card>
  );
}
