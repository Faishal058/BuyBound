import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { ErrorBanner } from '../../../components/ui/ErrorBanner';
import { Spinner } from '../../../components/ui/Spinner';
import { toApiError } from '../../../services/api/client';
import { guardianApi, mandatesApi, watchesApi } from '../../../services/api/endpoints';
import { formatUsd } from '../../shared/meta';
import type {
  ActiveMandateTelemetry,
  NewMerchantBehavior,
  RuntimeGuardrailSettings,
} from '../../../services/api/types';

const DEFAULT_RUNTIME: RuntimeGuardrailSettings = {
  demoBypassEnabled: false,
  strictSellerVerification: true,
  guardianAutoApproveDemoMode: false,
  rateLimitDisabled: false,
  ignoreOvernightWindow: false,
};

export function AgentSettingsPage() {
  const [telemetry, setTelemetry] = useState<ActiveMandateTelemetry | null>(null);
  const [runtime, setRuntime] = useState<RuntimeGuardrailSettings>(DEFAULT_RUNTIME);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Mandate & Policy form fields
  const [agentStatus, setAgentStatus] = useState<'ACTIVE' | 'PAUSED' | 'REVOKED'>('ACTIVE');
  const [newMerchantBehavior, setNewMerchantBehavior] = useState<NewMerchantBehavior>('HOLD');
  const [maxUnitPrice, setMaxUnitPrice] = useState('260.00');
  const [maxTotal, setMaxTotal] = useState('260.00');
  const [maxWeeklySpend, setMaxWeeklySpend] = useState('500.00');
  const [requireApprovalAbove, setRequireApprovalAbove] = useState('');
  const [allowedMerchantsText, setAllowedMerchantsText] = useState(
    'nike.com, bestbuy.com, target.com, walmart.com, macys.com',
  );

  const [savingPolicy, setSavingPolicy] = useState(false);
  const [savingRuntime, setSavingRuntime] = useState(false);
  const [resettingBudget, setResettingBudget] = useState(false);
  const [huntingNow, setHuntingNow] = useState(false);
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'danger' | 'info'; text: string } | null>(null);

  const loadAll = useCallback(async () => {
    setError(null);
    try {
      const [tel, rt] = await Promise.all([
        mandatesApi.active(),
        guardianApi.getRuntimeSettings().catch(() => DEFAULT_RUNTIME),
      ]);
      setTelemetry(tel);
      setRuntime(rt);

      const statusUpper = (tel.status || 'ACTIVE').toUpperCase();
      if (statusUpper === 'PAUSED' || statusUpper === 'REVOKED' || statusUpper === 'ACTIVE') {
        setAgentStatus(statusUpper);
      }
      const behaviorUpper = (tel.newMerchantBehavior || 'HOLD').toUpperCase();
      if (
        behaviorUpper === 'ALLOW' ||
        behaviorUpper === 'HOLD' ||
        behaviorUpper === 'REQUIRE_APPROVAL' ||
        behaviorUpper === 'BLOCK'
      ) {
        setNewMerchantBehavior(behaviorUpper);
      }
      if (tel.maxUnitPrice) setMaxUnitPrice(Number(tel.maxUnitPrice).toFixed(2));
      if (tel.maxUnitPrice) setMaxTotal(Number(tel.maxUnitPrice).toFixed(2));
      if (tel.maxWeeklySpend) setMaxWeeklySpend(Number(tel.maxWeeklySpend).toFixed(2));
      if (tel.allowedMerchants && tel.allowedMerchants.length > 0) {
        setAllowedMerchantsText(tel.allowedMerchants.join(', '));
      }
    } catch (err) {
      setError(toApiError(err).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadAll();
  }, [loadAll]);

  const parseDomains = () =>
    allowedMerchantsText
      .split(/[,\n]/)
      .map((s) => s.trim().toLowerCase())
      .filter(Boolean);

  const handleSaveMandate = async () => {
    if (!telemetry?.mandateId) {
      setFeedback({ tone: 'danger', text: 'No mandate found yet. Create a watch or alert first.' });
      return;
    }
    setSavingPolicy(true);
    setFeedback(null);
    try {
      const unitNum = Math.max(1, Number(maxUnitPrice) || 260);
      const totalNum = Math.max(unitNum, Number(maxTotal) || unitNum);
      const weeklyNum = Math.max(totalNum, Number(maxWeeklySpend) || 500);
      const approvalNum = requireApprovalAbove.trim()
        ? Math.max(0, Number(requireApprovalAbove))
        : undefined;

      const updated = await mandatesApi.update(telemetry.mandateId, {
        status: agentStatus,
        newMerchantBehavior,
        maxUnitPrice: unitNum,
        maxTotal: totalNum,
        maxWeeklySpend: weeklyNum,
        ...(approvalNum !== undefined ? { requireHumanApprovalAbove: approvalNum } : {}),
        allowedMerchantIds: parseDomains(),
      });
      setTelemetry(updated);
      setFeedback({
        tone: 'success',
        text: `Mandate & Agent Policy saved (${agentStatus} · ${newMerchantBehavior} · $${unitNum.toFixed(2)} item / $${weeklyNum.toFixed(2)} weekly cap).`,
      });
      window.dispatchEvent(new CustomEvent('buybound:refresh'));
    } catch (err) {
      setFeedback({ tone: 'danger', text: toApiError(err).message });
    } finally {
      setSavingPolicy(false);
    }
  };

  const handleSaveRuntime = async (next: RuntimeGuardrailSettings, label?: string) => {
    setSavingRuntime(true);
    setFeedback(null);
    try {
      const saved = await guardianApi.updateRuntimeSettings(next);
      setRuntime(saved);
      setFeedback({
        tone: 'success',
        text: label ?? 'Runtime security & demo guardrails updated immediately.',
      });
    } catch (err) {
      setFeedback({ tone: 'danger', text: toApiError(err).message });
    } finally {
      setSavingRuntime(false);
    }
  };

  const applyPreset = async (mode: 'demo' | 'strict') => {
    setSavingPolicy(true);
    setSavingRuntime(true);
    setFeedback(null);
    try {
      const nextRuntime: RuntimeGuardrailSettings =
        mode === 'demo'
          ? {
              demoBypassEnabled: true,
              strictSellerVerification: false,
              guardianAutoApproveDemoMode: true,
              rateLimitDisabled: true,
              ignoreOvernightWindow: true,
            }
          : DEFAULT_RUNTIME;

      const savedRuntime = await guardianApi.updateRuntimeSettings(nextRuntime);
      setRuntime(savedRuntime);

      if (telemetry?.mandateId) {
        const nextMerchants =
          mode === 'demo'
            ? Array.from(
                new Set([
                  ...parseDomains(),
                  'nike.com',
                  'bestbuy.com',
                  'target.com',
                  'walmart.com',
                  'macys.com',
                  'ebay.com',
                ]),
              )
            : ['nike.com', 'bestbuy.com', 'target.com', 'macys.com'];

        const updatedTel = await mandatesApi.update(
          telemetry.mandateId,
          mode === 'demo'
            ? {
                status: 'ACTIVE',
                newMerchantBehavior: 'ALLOW',
                maxUnitPrice: 1000,
                maxTotal: 1000,
                maxWeeklySpend: 5000,
                requireHumanApprovalAbove: 10000,
                allowedMerchantIds: nextMerchants,
              }
            : {
                status: 'ACTIVE',
                newMerchantBehavior: 'HOLD',
                maxUnitPrice: 210,
                maxTotal: 210,
                maxWeeklySpend: 500,
                allowedMerchantIds: nextMerchants,
              },
        );
        setTelemetry(updatedTel);
      }

      await loadAll();
      window.dispatchEvent(new CustomEvent('buybound:refresh'));
      setFeedback({
        tone: mode === 'demo' ? 'success' : 'info',
        text:
          mode === 'demo'
            ? 'Demo Mode Activated: Full Autonomous Agent Buy enabled ($1,000/item, $5,000/wk, ALLOW new merchants, strict seller & rate-limit blocks relaxed).'
            : 'Strict CISO Security Mode Restored: Full seller telemetry, merchant allowlist HOLD, rate limiting, and tight caps enforced.',
      });
    } catch (err) {
      setFeedback({ tone: 'danger', text: toApiError(err).message });
    } finally {
      setSavingPolicy(false);
      setSavingRuntime(false);
    }
  };

  const handleResetReservations = async () => {
    setResettingBudget(true);
    setFeedback(null);
    try {
      const res = await mandatesApi.resetReservations();
      await loadAll();
      window.dispatchEvent(new CustomEvent('buybound:refresh'));
      setFeedback({ tone: 'success', text: res.message });
    } catch (err) {
      setFeedback({ tone: 'danger', text: toApiError(err).message });
    } finally {
      setResettingBudget(false);
    }
  };

  const handleRunHuntNow = async () => {
    setHuntingNow(true);
    setFeedback(null);
    try {
      const res = await watchesApi.huntNow();
      await loadAll();
      window.dispatchEvent(new CustomEvent('buybound:refresh'));
      setFeedback({ tone: 'info', text: res.message });
    } catch (err) {
      setFeedback({ tone: 'danger', text: toApiError(err).message });
    } finally {
      setHuntingNow(false);
    }
  };

  return (
    <div className="flex flex-col gap-7">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <span className="inline-flex items-center gap-1.5 rounded-full bg-brand-peach-soft px-3 py-1 text-xs font-semibold text-brand-coral">
            Autonomous Agent &amp; CISO Demo Control Center
          </span>
          <h1 className="mt-2 font-display text-2xl font-bold text-ink">Agent Settings &amp; Guardrail Controls</h1>
          <p className="mt-1 text-sm text-muted">
            Switch seamlessly between strict CISO enforcement and live PayPal Sandbox demo automation, adjust spending mandates, and reset locked spend reservations on demand.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2.5">
          <Button
            variant="primary"
            loading={savingPolicy || savingRuntime}
            onClick={() => void applyPreset('demo')}
          >
            Demo Mode: Full Autonomous Agent Buy
          </Button>
          <Button
            variant="secondary"
            loading={savingPolicy || savingRuntime}
            onClick={() => void applyPreset('strict')}
          >
            Strict CISO Security Mode
          </Button>
        </div>
      </div>

      {feedback && (
        <div
          role="status"
          className={`rounded-xl border px-4 py-3 text-sm font-medium ${
            feedback.tone === 'success'
              ? 'border-[#16A34A]/30 bg-success-bg text-success'
              : feedback.tone === 'danger'
                ? 'border-[#DC2626]/30 bg-danger-bg text-danger'
                : 'border-brand-border bg-brand-ivory text-ink'
          }`}
        >
          {feedback.text}
        </div>
      )}

      {loading && <Spinner label="Loading agent mandate and CISO guardrail settings…" />}
      {error && !loading && <ErrorBanner message={error} onRetry={() => void loadAll()} />}

      {!loading && !error && (
        <>
          {/* Live Telemetry & Quick Agent Operations */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div className="rounded-2xl border border-brand-border bg-white p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-muted">Agent Mandate Status</p>
              <div className="mt-2 flex items-center gap-2">
                <Badge tone={telemetry?.status === 'ACTIVE' ? 'success' : 'warning'} dot>
                  {telemetry?.status ?? 'ACTIVE'}
                </Badge>
                <span className="text-xs text-muted">
                  New store: <strong>{telemetry?.newMerchantBehavior ?? newMerchantBehavior}</strong>
                </span>
              </div>
            </div>

            <div className="rounded-2xl border border-brand-border bg-white p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-muted">Weekly Allowance Remaining</p>
              <p className="mt-1 mono font-display text-2xl font-bold text-ink">
                {formatUsd(telemetry?.remainingBudget ?? '0')}
              </p>
              <p className="text-xs text-muted">
                Spent/Locked: {formatUsd(telemetry?.weeklySpent ?? '0')} / {formatUsd(telemetry?.maxWeeklySpend ?? '500')}
              </p>
            </div>

            <div className="rounded-2xl border border-brand-border bg-white p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-muted">PayPal Sandbox Account</p>
              <p className="mt-1 font-display text-base font-bold text-ink truncate">
                {telemetry?.paypalPayerEmail ?? telemetry?.paypalPayerId ?? 'Interactive Sandbox Ready'}
              </p>
              <p className="text-xs text-muted">
                Tracked Balance: <strong className="mono">{formatUsd(telemetry?.sandboxBalance ?? '5000.00')}</strong>
              </p>
            </div>

            <div className="rounded-2xl border border-brand-border bg-white p-4 flex flex-col justify-between gap-2">
              <div className="flex flex-col gap-2">
                <Button
                  variant="secondary"
                  className="!min-h-[36px] !text-xs w-full"
                  loading={resettingBudget}
                  onClick={() => void handleResetReservations()}
                >
                  Reset Locked Spend Reservations
                </Button>
                <Button
                  variant="primary"
                  className="!min-h-[36px] !text-xs w-full"
                  loading={huntingNow}
                  onClick={() => void handleRunHuntNow()}
                >
                  Run Agent Hunt Now ({telemetry?.activeWatchesCount ?? 0} Active)
                </Button>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
            {/* Mandate & Policy Controls */}
            <Card
              title="Mandate &amp; Spending Policy"
              subtitle="Controls what the autonomous agent can approve, hold, or execute without human sign-off."
            >
              <div className="space-y-4">
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <div>
                    <label htmlFor="agent-status-select" className="block text-xs font-semibold text-ink mb-1">
                      Agent Mandate Status
                    </label>
                    <select
                      id="agent-status-select"
                      value={agentStatus}
                      onChange={(e) => setAgentStatus(e.target.value as 'ACTIVE' | 'PAUSED' | 'REVOKED')}
                      className="w-full min-h-[40px] rounded-xl border border-border-strong bg-surface px-3 py-2 text-sm text-ink focus:border-brand-coral focus:outline-none"
                    >
                      <option value="ACTIVE">ACTIVE (Autonomous Hunt Enabled)</option>
                      <option value="PAUSED">PAUSED (Monitoring Only)</option>
                      <option value="REVOKED">REVOKED (Emergency Kill Switch)</option>
                    </select>
                  </div>

                  <div>
                    <label htmlFor="new-merchant-select" className="block text-xs font-semibold text-ink mb-1">
                      New / Unlisted Merchant Policy
                    </label>
                    <select
                      id="new-merchant-select"
                      value={newMerchantBehavior}
                      onChange={(e) => setNewMerchantBehavior(e.target.value as NewMerchantBehavior)}
                      className="w-full min-h-[40px] rounded-xl border border-border-strong bg-surface px-3 py-2 text-sm text-ink focus:border-brand-coral focus:outline-none"
                    >
                      <option value="ALLOW">ALLOW (Auto-Approve Verified Stores)</option>
                      <option value="HOLD">HOLD (Queue for 1-Click Confirmation)</option>
                      <option value="REQUIRE_APPROVAL">REQUIRE_APPROVAL (Manual Sign-Off)</option>
                      <option value="BLOCK">BLOCK (Strict Allowlist Only)</option>
                    </select>
                  </div>
                </div>

                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <div>
                    <label htmlFor="max-unit-input" className="block text-xs font-semibold text-ink mb-1">
                      Max Per-Item Cap ($ USD)
                    </label>
                    <input
                      id="max-unit-input"
                      type="number"
                      min="1"
                      step="0.01"
                      value={maxUnitPrice}
                      onChange={(e) => setMaxUnitPrice(e.target.value)}
                      className="mono w-full min-h-[40px] rounded-xl border border-border-strong bg-surface px-3 py-2 text-sm text-ink focus:border-brand-coral focus:outline-none"
                    />
                  </div>
                  <div>
                    <label htmlFor="max-total-input" className="block text-xs font-semibold text-ink mb-1">
                      Max Order Total ($ USD)
                    </label>
                    <input
                      id="max-total-input"
                      type="number"
                      min="1"
                      step="0.01"
                      value={maxTotal}
                      onChange={(e) => setMaxTotal(e.target.value)}
                      className="mono w-full min-h-[40px] rounded-xl border border-border-strong bg-surface px-3 py-2 text-sm text-ink focus:border-brand-coral focus:outline-none"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <div>
                    <label htmlFor="max-weekly-input" className="block text-xs font-semibold text-ink mb-1">
                      Weekly Allowance Cap ($ USD)
                    </label>
                    <input
                      id="max-weekly-input"
                      type="number"
                      min="1"
                      step="0.01"
                      value={maxWeeklySpend}
                      onChange={(e) => setMaxWeeklySpend(e.target.value)}
                      className="mono w-full min-h-[40px] rounded-xl border border-border-strong bg-surface px-3 py-2 text-sm text-ink focus:border-brand-coral focus:outline-none"
                    />
                  </div>
                  <div>
                    <label htmlFor="human-approval-above-input" className="block text-xs font-semibold text-ink mb-1">
                      Require Human Approval Above ($ USD, optional)
                    </label>
                    <input
                      id="human-approval-above-input"
                      type="number"
                      min="0"
                      step="0.01"
                      placeholder="Leave blank for none"
                      value={requireApprovalAbove}
                      onChange={(e) => setRequireApprovalAbove(e.target.value)}
                      className="mono w-full min-h-[40px] rounded-xl border border-border-strong bg-surface px-3 py-2 text-sm text-ink focus:border-brand-coral focus:outline-none"
                    />
                  </div>
                </div>

                <div>
                  <label htmlFor="allowed-merchants-input" className="block text-xs font-semibold text-ink mb-1">
                    Allowed Merchant Domains (comma-separated)
                  </label>
                  <textarea
                    id="allowed-merchants-input"
                    rows={2}
                    value={allowedMerchantsText}
                    onChange={(e) => setAllowedMerchantsText(e.target.value)}
                    className="mono w-full rounded-xl border border-border-strong bg-surface px-3 py-2 text-xs text-ink focus:border-brand-coral focus:outline-none"
                  />
                </div>

                <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
                  <Link to="/app/purchases" className="text-xs font-semibold text-brand-coral hover:underline">
                    View Orders &amp; Live PayPal Activity →
                  </Link>
                  <Button variant="primary" loading={savingPolicy} onClick={() => void handleSaveMandate()}>
                    Save Mandate Policy
                  </Button>
                </div>
              </div>
            </Card>

            {/* Security & Demo Guardrails */}
            <Card
              title="Security &amp; Demo Guardrail Switches"
              subtitle="Runtime toggles (defaulting to strict CISO mode on boot). Changes take effect immediately."
            >
              <div className="space-y-3.5">
                {[
                  {
                    key: 'demoBypassEnabled' as const,
                    label: 'Demo Guardrail Bypass',
                    desc: 'Relaxes strict checkout payload/SSRF/seller-telemetry blocks for smooth live video demos.',
                    badge: runtime.demoBypassEnabled ? 'RELAXED FOR DEMO' : 'CISO STRICT',
                  },
                  {
                    key: 'strictSellerVerification' as const,
                    label: 'Strict Seller Reputation Verification',
                    desc: 'Enforces minimum seller star rating and zero negative review red flags before checkout.',
                    badge: runtime.strictSellerVerification ? 'ENFORCED' : 'RELAXED',
                  },
                  {
                    key: 'guardianAutoApproveDemoMode' as const,
                    label: 'Guardian Auto-Approve Demo Mode',
                    desc: 'Upgrades HOLD / unfamiliar merchant decisions into ALLOW so agent deals settle without stalls.',
                    badge: runtime.guardianAutoApproveDemoMode ? 'AUTO-ALLOW' : 'STANDARD',
                  },
                  {
                    key: 'rateLimitDisabled' as const,
                    label: 'Disable Per-IP Rate Limiting',
                    desc: 'Skips 429 burst throttling on hunt, checkout, and AI endpoints during repeated demo clicks.',
                    badge: runtime.rateLimitDisabled ? 'DISABLED' : 'ACTIVE',
                  },
                  {
                    key: 'ignoreOvernightWindow' as const,
                    label: 'Ignore Overnight Execution Window',
                    desc: 'Allows scheduled and manual overnight drop hunts to run at any hour of the day.',
                    badge: runtime.ignoreOvernightWindow ? '24/7 DEMO' : 'SCHEDULED',
                  },
                ].map((item) => {
                  const checked = runtime[item.key];
                  return (
                    <label
                      key={item.key}
                      className="flex items-start justify-between gap-4 rounded-xl border border-brand-border bg-brand-ivory/50 p-3.5 cursor-pointer hover:bg-brand-ivory transition-colors"
                    >
                      <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="font-semibold text-sm text-ink">{item.label}</span>
                          <Badge tone={checked && item.key !== 'strictSellerVerification' ? 'warning' : 'neutral'}>
                            {item.badge}
                          </Badge>
                        </div>
                        <p className="mt-1 text-xs text-muted">{item.desc}</p>
                      </div>
                      <input
                        type="checkbox"
                        checked={checked}
                        disabled={savingRuntime}
                        onChange={(e) => {
                          const next = { ...runtime, [item.key]: e.target.checked };
                          void handleSaveRuntime(next);
                        }}
                        className="mt-1 h-4 w-4 accent-brand-coral shrink-0"
                      />
                    </label>
                  );
                })}
              </div>
            </Card>
          </div>
        </>
      )}
    </div>
  );
}

