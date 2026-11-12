import { useEffect, useId, useRef } from 'react';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { REASON_CODE_EXPLANATIONS, formatStoreName, formatUsd } from '../../features/shared/meta';
import type { GuardianDecisionValue, ReasonCode, RuleCheckDetail } from '../../services/api/types';

export interface GuardianProofTarget {
  title: string;
  merchantId: string;
  price?: string;
  baselinePrice?: string;
  decision: GuardianDecisionValue;
  reasonCodes: string[];
  ruleChecks: RuleCheckDetail[];
  aiSummary?: string;
  aiConfidence?: string;
  evidenceHash?: string;
}

interface GuardianProofModalProps {
  target: GuardianProofTarget | null;
  onClose: () => void;
}

const DECISION_LABEL: Record<GuardianDecisionValue, { label: string; tone: 'success' | 'warning' | 'danger' }> = {
  ALLOW: { label: 'Approved', tone: 'success' },
  HOLD: { label: 'On Hold', tone: 'warning' },
  BLOCK: { label: 'Blocked', tone: 'danger' },
};

export function GuardianProofModal({ target, onClose }: GuardianProofModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const pressedOutsideRef = useRef(false);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (target) {
      if (!dialog.open && typeof dialog.showModal === 'function') dialog.showModal();
    } else if (dialog.open && typeof dialog.close === 'function') {
      dialog.close();
    }
  }, [target]);

  if (!target) return null;

  const isOutside = (e: React.PointerEvent<HTMLDialogElement>, dialog: HTMLDialogElement) => {
    const r = dialog.getBoundingClientRect();
    return e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom;
  };

  const decisionMeta = DECISION_LABEL[target.decision];

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={titleId}
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onPointerDown={(e) => {
        if (dialogRef.current) pressedOutsideRef.current = isOutside(e, dialogRef.current);
      }}
      onPointerUp={(e) => {
        if (dialogRef.current && pressedOutsideRef.current && isOutside(e, dialogRef.current)) {
          onClose();
        }
        pressedOutsideRef.current = false;
      }}
      className="m-auto w-full max-w-2xl rounded-2xl border border-border bg-surface p-6 sm:p-7 shadow-xl text-ink"
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="flex flex-wrap items-center gap-2.5">
            <h2 id={titleId} className="font-display text-xl font-bold text-ink">
              Spending &amp; Safety Check Details
            </h2>
            <Badge tone={decisionMeta.tone} dot>
              {decisionMeta.label}
            </Badge>
          </div>
          <p className="mt-1 text-sm text-muted">
            {target.title} · <span className="font-semibold text-ink">{formatStoreName(target.merchantId)}</span>
            {target.price && <> · {formatUsd(target.price)}</>}
          </p>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="Close safety details dialog"
          className="rounded-lg p-1.5 text-sm text-muted hover:bg-surface-muted hover:text-ink"
        >
          ✕
        </button>
      </div>

      {target.aiSummary && (
        <div className="mt-4 rounded-xl border border-border bg-background p-4">
          <div className="flex items-center justify-between gap-2">
            <span className="text-xs font-semibold uppercase tracking-wider text-brand-coral">
              Product Verification
            </span>
            {target.aiConfidence && (
              <span className="mono text-xs text-muted">Match score: {Math.round(Number(target.aiConfidence))}%</span>
            )}
          </div>
          <p className="mt-1 text-sm text-ink leading-relaxed">{target.aiSummary}</p>
        </div>
      )}

      <div className="mt-5">
        <h3 className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted">
          Rule-by-Rule Results
        </h3>
        {target.ruleChecks && target.ruleChecks.length > 0 ? (
          <div className="divide-y divide-border rounded-xl border border-border overflow-hidden">
            {target.ruleChecks.map((check) => (
              <div key={check.ruleName} className="flex items-center justify-between gap-3 bg-surface px-4 py-3 text-sm">
                <div className="min-w-0">
                  <p className="font-medium text-ink">{check.ruleName}</p>
                  <p className="mono text-xs text-faint">
                    Rule limit: {check.expectedThreshold} · Found: {check.actualValue}
                  </p>
                </div>
                <Badge
                  tone={check.passed ? 'success' : target.decision === 'HOLD' ? 'warning' : 'danger'}
                  dot
                >
                  {check.passed ? 'Passed' : target.decision === 'HOLD' ? 'Paused' : 'Blocked'}
                </Badge>
              </div>
            ))}
          </div>
        ) : (
          <div className="flex flex-col gap-2">
            {target.reasonCodes.map((code) => (
              <div key={code} className="rounded-xl border border-border bg-background px-4 py-3 text-sm">
                <p className="font-medium text-ink">
                  {REASON_CODE_EXPLANATIONS[code as ReasonCode] ?? 'Checked against your spending limits.'}
                </p>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="mt-6 flex justify-end">
        <Button onClick={onClose} autoFocus>
          Done
        </Button>
      </div>
    </dialog>
  );
}
