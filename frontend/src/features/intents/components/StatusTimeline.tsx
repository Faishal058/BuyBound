import { INTENT_STATUS_META } from '../../shared/meta';
import type { IntentStatus } from '../../../services/api/types';

const MILESTONES: IntentStatus[][] = [
  ['CREATED'],
  ['OFFER_VALIDATED'],
  ['GUARDIAN_EVALUATED', 'AWAITING_APPROVAL', 'HELD_FOR_APPROVAL', 'BLOCKED'],
  ['SPEND_RESERVED'],
  ['ORDER_CREATE_PENDING', 'ORDER_CREATED'],
  ['CAPTURE_PENDING', 'CAPTURED', 'REFUND_PENDING', 'REFUNDED'],
];

const TERMINAL_NOTE: Partial<Record<IntentStatus, string>> = {
  BLOCKED: 'Safety rules blocked this listing before PayPal was touched ($0.00 charged).',
  FAILED: 'PayPal could not complete this payment. Your weekly budget was not reduced.',
  RECONCILIATION_REQUIRED: 'Waiting for final confirmation from PayPal.',
  CANCELLED: 'Cancelled. No charge will be made.',
  REJECTED_BY_USER: 'Declined by you. No charge was made.',
  EXPIRED: 'This store quote expired before checkout finished.',
  REFUND_PENDING: 'A refund is in progress.',
  REFUNDED: 'Refund completed.',
};

function stepIndex(status: IntentStatus): number {
  const index = MILESTONES.findIndex((group) => group.includes(status));
  return index === -1 ? 0 : index;
}

export function StatusTimeline({ status }: { status: IntentStatus }) {
  const current = stepIndex(status);
  const labels = [
    'Price found',
    'Product verified',
    'Safety rules checked',
    'Budget held',
    'PayPal order created',
    'Paid',
  ];
  const note = TERMINAL_NOTE[status];

  return (
    <div>
      <ol className="flex flex-col gap-0">
        {labels.map((label, index) => {
          const done = index < current;
          const active = index === current;
          return (
            <li key={label} className="flex gap-3">
              <div className="flex flex-col items-center" aria-hidden="true">
                <span
                  className={`mt-1 h-3 w-3 rounded-full border-2 ${
                    done
                      ? 'border-success-dot bg-success-dot'
                      : active
                        ? 'border-brand-coral bg-brand-coral'
                        : 'border-border-strong bg-surface'
                  }`}
                />
                {index < labels.length - 1 && <span className="h-6 w-0.5 bg-border" />}
              </div>
              <div className="pb-5">
                <p className={`text-sm font-medium ${active ? 'text-ink' : done ? 'text-muted' : 'text-faint'}`}>
                  {label}
                </p>
                {active && <p className="text-xs text-muted">{INTENT_STATUS_META[status].label}</p>}
              </div>
            </li>
          );
        })}
      </ol>
      {note && (
        <p className="rounded-lg border border-border bg-background px-4 py-3 text-sm text-muted">{note}</p>
      )}
    </div>
  );
}
