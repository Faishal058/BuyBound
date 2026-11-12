import { Badge } from '../../../components/ui/Badge';
import { Card } from '../../../components/ui/Card';
import { REASON_CODE_EXPLANATIONS, formatDateTime } from '../../shared/meta';
import type { GuardianDecision } from '../../../services/api/types';

const decisionMeta = {
  ALLOW: { label: 'Approved', tone: 'success' as const },
  HOLD: { label: 'On Hold', tone: 'warning' as const },
  BLOCK: { label: 'Blocked', tone: 'danger' as const },
};

export function DecisionTrace({ decision }: { decision: GuardianDecision }) {
  const meta = decisionMeta[decision.decision];

  return (
    <Card
      title="Safety Rule Evaluation"
      subtitle="Checks run automatically against your spending limits before any PayPal charge."
    >
      <div className="mb-4">
        <Badge tone={meta.tone} dot className="text-sm">
          {meta.label}
        </Badge>
      </div>
      <div className="mb-4 flex flex-col gap-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-faint">Check Results</p>
        <ul className="flex flex-col gap-2">
          {decision.reasonCodes.map((code) => (
            <li key={code} className="rounded-lg border border-border bg-background px-3.5 py-2.5 text-sm text-ink">
              {REASON_CODE_EXPLANATIONS[code] ?? code}
            </li>
          ))}
        </ul>
      </div>
      <p className="text-xs text-muted">Checked at {formatDateTime(decision.evaluatedAt)}</p>
    </Card>
  );
}
