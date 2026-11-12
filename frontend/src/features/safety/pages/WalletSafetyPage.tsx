import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { PayPalVaultModal } from '../../../components/modals/PayPalVaultModal';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { ErrorBanner } from '../../../components/ui/ErrorBanner';
import { Spinner } from '../../../components/ui/Spinner';
import { TextField } from '../../../components/ui/TextField';
import { toApiError } from '../../../services/api/client';
import { demoApi, mandatesApi, watchesApi } from '../../../services/api/endpoints';
import { REASON_CODE_EXPLANATIONS, formatStoreName, formatUsd } from '../../shared/meta';
import type {
  ActiveMandateTelemetry,
  ReasonCode,
  RedTeamAttackResponse,
  RedTeamAttackType,
  Watch,
} from '../../../services/api/types';

const SCAM_TESTS: Array<{
  type: RedTeamAttackType;
  label: string;
  description: string;
  outcomeLabel: string;
}> = [
  {
    type: 'POISONED_BUNDLE_899',
    label: 'Test Over-Budget Bundle Trick ($899)',
    description:
      'Simulates a seller listing that tries to override your spending rules with a $899 expensive bundle.',
    outcomeLabel: 'Blocked Automatically',
  },
  {
    type: 'ACCESSORY_EARPAD_BAIT',
    label: 'Test Wrong-Item Accessory Bait ($49 Ear Pads)',
    description:
      'Simulates a cheap replacement accessory trying to masquerade as full headphones to trick price checks.',
    outcomeLabel: 'Blocked Automatically',
  },
  {
    type: 'HIDDEN_VIP_SUBSCRIPTION',
    label: 'Test Hidden Monthly Subscription Trap ($29.99/mo)',
    description:
      'Simulates a listing hiding a recurring $29.99/month membership club fee inside the product details.',
    outcomeLabel: 'Blocked Automatically',
  },
  {
    type: 'UNAPPROVED_SHADY_SHOP',
    label: 'Test Unknown Store Hold ($215)',
    description:
      'Simulates a real deal from an unfamiliar retailer not on your trusted stores allowlist.',
    outcomeLabel: 'Paused for Your Review',
  },
];

export function WalletSafetyPage() {
  const [telemetry, setTelemetry] = useState<ActiveMandateTelemetry | null>(null);
  const [watches, setWatches] = useState<Watch[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [vaultModalOpen, setVaultModalOpen] = useState(false);
  const [switching, setSwitching] = useState(false);
  const [switchMessage, setSwitchMessage] = useState<string | null>(null);
  const [switchError, setSwitchError] = useState<string | null>(null);

  const [runningTest, setRunningTest] = useState<RedTeamAttackType | null>(null);
  const [testResult, setTestResult] = useState<RedTeamAttackResponse | null>(null);
  const [testError, setTestError] = useState<string | null>(null);

  const [limitMaxUnit, setLimitMaxUnit] = useState('260');
  const [limitMaxWeekly, setLimitMaxWeekly] = useState('500');
  const [limitMerchantsText, setLimitMerchantsText] = useState('bestbuy.com, target.com, walmart.com, amazon.com');
  const [savingLimits, setSavingLimits] = useState(false);
  const [limitsMessage, setLimitsMessage] = useState<string | null>(null);
  const [limitsError, setLimitsError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    setError(null);
    try {
      const [activeTel, watchList] = await Promise.all([mandatesApi.active(), watchesApi.list()]);
      setTelemetry(activeTel);
      setWatches(watchList);
      if (activeTel.maxUnitPrice) {
        setLimitMaxUnit(String(Number(activeTel.maxUnitPrice)));
      }
      if (activeTel.maxWeeklySpend) {
        setLimitMaxWeekly(String(Number(activeTel.maxWeeklySpend)));
      }
      if (activeTel.allowedMerchants && activeTel.allowedMerchants.length > 0) {
        setLimitMerchantsText(activeTel.allowedMerchants.join(', '));
      }
    } catch (err) {
      setError(toApiError(err).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
    const onRefresh = () => void loadData();
    window.addEventListener('buybound:refresh', onRefresh);
    return () => window.removeEventListener('buybound:refresh', onRefresh);
  }, [loadData]);

  const isPaused = telemetry?.status === 'REVOKED';
  const activeWatchId = watches.find((w) => w.status === 'ACTIVE')?.id ?? watches[0]?.id;

  const handleDisableBuying = async () => {
    if (!telemetry?.mandateId) return;
    setSwitching(true);
    setSwitchMessage(null);
    setSwitchError(null);
    try {
      const res = await mandatesApi.killSwitch(telemetry.mandateId);
      setSwitchMessage(res.message);
      await loadData();
      window.dispatchEvent(new CustomEvent('buybound:refresh'));
    } catch (err) {
      setSwitchError(toApiError(err).message);
    } finally {
      setSwitching(false);
    }
  };

  const handleEnableBuying = async () => {
    if (!telemetry?.mandateId) return;
    setSwitching(true);
    setSwitchMessage(null);
    setSwitchError(null);
    try {
      const res = await mandatesApi.restore(telemetry.mandateId);
      setSwitchMessage(res.message);
      await loadData();
      window.dispatchEvent(new CustomEvent('buybound:refresh'));
    } catch (err) {
      setSwitchError(toApiError(err).message);
    } finally {
      setSwitching(false);
    }
  };

  const handleRunTest = async (type: RedTeamAttackType) => {
    setRunningTest(type);
    setTestError(null);
    setTestResult(null);
    try {
      const res = await demoApi.redTeamAttack(type, activeWatchId);
      setTestResult(res);
      await loadData();
      window.dispatchEvent(new CustomEvent('buybound:refresh'));
    } catch (err) {
      setTestError(toApiError(err).message);
    } finally {
      setRunningTest(null);
    }
  };

  const handleSaveLimits = async (e: FormEvent) => {
    e.preventDefault();
    if (!telemetry?.mandateId) return;
    const unitNum = Number(limitMaxUnit);
    const weeklyNum = Number(limitMaxWeekly);
    if (!Number.isFinite(unitNum) || unitNum <= 0 || !Number.isFinite(weeklyNum) || weeklyNum <= 0) {
      setLimitsError('Please enter valid positive dollar amounts for both spending limits.');
      return;
    }

    const merchants = limitMerchantsText
      .split(',')
      .map((s) => s.trim().toLowerCase())
      .filter(Boolean);

    setSavingLimits(true);
    setLimitsMessage(null);
    setLimitsError(null);
    try {
      const updated = await mandatesApi.update(telemetry.mandateId, {
        maxUnitPrice: unitNum,
        maxTotal: unitNum,
        maxWeeklySpend: weeklyNum,
        allowedMerchantIds: merchants,
      });
      setTelemetry(updated);
      setLimitsMessage(
        `Spending limits updated: ${formatUsd(updated.maxUnitPrice ?? unitNum)} per-item cap and ${formatUsd(updated.maxWeeklySpend)} weekly budget across ${merchants.length || 1} approved stores.`,
      );
      window.dispatchEvent(new CustomEvent('buybound:refresh'));
    } catch (err) {
      setLimitsError(toApiError(err).message);
    } finally {
      setSavingLimits(false);
    }
  };

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="font-display text-2xl font-bold text-ink">Wallet &amp; Safety Settings</h1>
          <p className="mt-1 text-sm text-muted">
            Manage your PayPal connection, hard spending limits, one-tap buying kill switch, and scam shield tests.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Badge tone="success" dot>
            CISO Security &amp; DLP Shield: Active
          </Badge>
          <Link
            to="/audit"
            className="inline-flex min-h-[38px] items-center rounded-lg border border-border-strong bg-surface px-3.5 py-1.5 text-xs font-semibold text-ink hover:bg-surface-muted"
          >
            View Full Safety Activity Log →
          </Link>
        </div>
      </div>

      {loading && <Spinner label="Loading wallet and safety settings…" />}
      {error && !loading && <ErrorBanner message={error} onRetry={() => void loadData()} />}

      {!loading && telemetry && (
        <>
          {/* PayPal Connection & Spending Limits Card */}
          <Card
            title="PayPal Connection & Spending Limits"
            subtitle="Hard limits enforced before PayPal is ever allowed to process an order."
            action={
              <Badge tone={isPaused ? 'warning' : telemetry.paypalConnected ? 'success' : 'info'} dot>
                {isPaused
                  ? 'Buying Disabled'
                  : telemetry.paypalConnected
                    ? 'PayPal Connected'
                    : 'Ready to Connect'}
              </Badge>
            }
          >
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <div className="rounded-xl border border-border bg-background p-4">
                <p className="text-xs font-medium text-faint">PayPal Sandbox Wallet</p>
                <p className="mt-1 truncate text-sm font-semibold text-ink">
                  {telemetry.paypalConnected
                    ? telemetry.paypalPayerEmail ?? 'Connected Sandbox Buyer'
                    : 'Not signed in yet'}
                </p>
                {telemetry.paypalConnected && telemetry.paypalPayerId && (
                  <p className="mono mt-0.5 text-xs font-semibold text-[#0070BA]">
                    Payer ID: {telemetry.paypalPayerId}
                  </p>
                )}
                {telemetry.paypalConnected && telemetry.paypalPayerName && (
                  <p className="mt-0.5 text-xs text-muted">
                    {telemetry.paypalPayerName}
                  </p>
                )}
                <button
                  type="button"
                  onClick={() => setVaultModalOpen(true)}
                  className="mt-2 text-xs font-semibold text-[#0070BA] hover:underline"
                >
                  {telemetry.paypalConnected ? 'Manage PayPal Sandbox login' : 'Log in with PayPal Sandbox →'}
                </button>
              </div>

              <div className="rounded-xl border border-border bg-background p-4">
                <p className="text-xs font-medium text-faint">Safety Rule ID</p>
                <p className="mono mt-1 truncate text-xs font-semibold text-ink" title={telemetry.mandateId ?? ''}>
                  {telemetry.mandateId ?? 'None created'}
                </p>
                <p className="mt-2 text-xs text-muted">
                  Trusted stores:{' '}
                  {(telemetry.allowedMerchants ?? ['bestbuy.com', 'macys.com', 'target.com'])
                    .slice(0, 3)
                    .map(formatStoreName)
                    .join(', ')}
                </p>
              </div>

              <div className="rounded-xl border border-border bg-background p-4">
                <p className="text-xs font-medium text-faint">Per-Item Limit</p>
                <p className="mono mt-1 text-2xl font-bold text-ink">
                  {formatUsd(telemetry.maxUnitPrice ?? '260.00')}
                </p>
                <p className="mt-1 text-xs text-muted">No single purchase may exceed this amount</p>
              </div>

              <div className="rounded-xl border border-border bg-background p-4">
                <p className="text-xs font-medium text-faint">Weekly Budget Limit</p>
                <p className="mono mt-1 text-2xl font-bold text-ink">{formatUsd(telemetry.maxWeeklySpend)}</p>
                <p className="mt-1 text-xs text-success">
                  <strong className="mono">{formatUsd(telemetry.remainingBudget)}</strong> remaining this week
                </p>
              </div>
            </div>

            {switchMessage && (
              <div role="status" className="mt-5 rounded-xl border border-border bg-background px-4 py-3 text-sm font-medium text-ink">
                {switchMessage}
              </div>
            )}
            {switchError && (
              <div role="alert" className="mt-5 rounded-xl border border-[#DC2626]/25 bg-danger-bg px-4 py-3 text-sm text-danger">
                {switchError}
              </div>
            )}

            {/* One-Tap Kill Switch / Re-Enable Bar */}
            <div className="mt-6 flex flex-col justify-between gap-4 rounded-xl border border-border bg-background p-5 sm:flex-row sm:items-center">
              <div>
                <h3 className="font-display text-base font-bold text-ink">
                  {isPaused ? 'Automated Buying is Paused' : 'One-Tap Emergency Kill Switch'}
                </h3>
                <p className="mt-1 text-sm text-muted">
                  {isPaused
                    ? 'Your watches are paused and PayPal auto-checkout is turned off. Re-enable whenever you are ready.'
                    : 'Instantly pause all price watches and revoke automated PayPal checkout permission with one tap.'}
                </p>
              </div>

              <div className="flex shrink-0 flex-wrap gap-3">
                <Button
                  variant="danger"
                  disabled={isPaused || !telemetry.mandateId || switching}
                  loading={!isPaused && switching}
                  onClick={() => void handleDisableBuying()}
                >
                  Disable Agent Buying
                </Button>
                <Button
                  variant="primary"
                  disabled={!isPaused || !telemetry.mandateId || switching}
                  loading={isPaused && switching}
                  onClick={() => void handleEnableBuying()}
                >
                  Re-Enable Buying
                </Button>
              </div>
            </div>
          </Card>

          {/* Editable Spending Limits Card */}
          <Card
            title="Update My Spending Limits"
            subtitle="Raise or tighten your per-item cap, weekly allowance, and approved store list before shopping in the Marketplace."
          >
            <form onSubmit={(e) => void handleSaveLimits(e)} className="space-y-5">
              <div className="flex flex-wrap items-center gap-2 text-xs">
                <span className="font-medium text-muted">Quick presets:</span>
                <button
                  type="button"
                  onClick={() => {
                    setLimitMaxUnit('260');
                    setLimitMaxWeekly('500');
                  }}
                  className="rounded-full border border-border bg-background px-3 py-1 font-medium text-ink hover:border-brand-coral"
                >
                  Standard Safe ($260 / $500)
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setLimitMaxUnit('1500');
                    setLimitMaxWeekly('3000');
                    if (!limitMerchantsText.includes('amazon.com')) {
                      setLimitMerchantsText('bestbuy.com, target.com, walmart.com, amazon.com, bhphotovideo.com, nike.com');
                    }
                  }}
                  className="rounded-full border border-border bg-background px-3 py-1 font-medium text-ink hover:border-brand-coral"
                >
                  Laptops &amp; Phones ($1,500 / $3,000)
                </button>
              </div>

              <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                <TextField
                  id="safety-max-unit"
                  label="Max Per-Item Spend Limit ($ USD)"
                  type="number"
                  min="1"
                  step="1"
                  required
                  value={limitMaxUnit}
                  onChange={(e) => setLimitMaxUnit(e.target.value)}
                  hint="Any item priced above this limit is automatically blocked before checkout."
                />

                <TextField
                  id="safety-max-weekly"
                  label="Weekly Budget Limit ($ USD)"
                  type="number"
                  min="1"
                  step="1"
                  required
                  value={limitMaxWeekly}
                  onChange={(e) => setLimitMaxWeekly(e.target.value)}
                  hint="Total combined spend allowed across all stores during the current week."
                />
              </div>

              <TextField
                id="safety-allowed-stores"
                label="Approved Stores List (comma-separated domains)"
                value={limitMerchantsText}
                onChange={(e) => setLimitMerchantsText(e.target.value)}
                hint="Offers from stores outside this list trigger a safety hold for your manual approval."
              />

              {limitsMessage && (
                <div role="status" className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-success/30 bg-success-bg px-4 py-3 text-xs font-medium text-ink">
                  <span>{limitsMessage}</span>
                  <Link to="/app/shop" className="font-semibold text-brand-coral hover:underline">
                    Return to Shop All Products →
                  </Link>
                </div>
              )}

              {limitsError && (
                <div role="alert" className="rounded-xl border border-danger/30 bg-danger-bg px-4 py-3 text-xs font-medium text-danger">
                  {limitsError}
                </div>
              )}

              <div className="flex justify-end">
                <Button type="submit" variant="primary" loading={savingLimits} disabled={!telemetry.mandateId}>
                  Save Spending Limits
                </Button>
              </div>
            </form>
          </Card>

          {/* Interactive "Test Our Scam Protection" Simulator */}
          <Card
            title="Test Our Scam Protection"
            subtitle="Curious what happens if a seller tries to trick the system? Test our safety guard live:"
          >
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              {SCAM_TESTS.map((test) => (
                <div
                  key={test.type}
                  className="flex flex-col justify-between gap-4 rounded-xl border border-border bg-background p-5"
                >
                  <div>
                    <div className="flex items-center justify-between gap-2">
                      <h3 className="font-display text-sm font-bold text-ink">{test.label}</h3>
                      <Badge tone={test.type === 'UNAPPROVED_SHADY_SHOP' ? 'warning' : 'danger'}>
                        {test.outcomeLabel}
                      </Badge>
                    </div>
                    <p className="mt-2 text-xs leading-relaxed text-muted">{test.description}</p>
                  </div>
                  <div>
                    <Button
                      variant={test.type === 'UNAPPROVED_SHADY_SHOP' ? 'secondary' : 'danger'}
                      loading={runningTest === test.type}
                      disabled={runningTest !== null}
                      onClick={() => void handleRunTest(test.type)}
                    >
                      {test.label}
                    </Button>
                  </div>
                </div>
              ))}
            </div>

            {testError && (
              <div role="alert" className="mt-5 rounded-xl border border-[#DC2626]/25 bg-danger-bg px-4 py-3 text-sm text-danger">
                {testError}
              </div>
            )}

            {testResult && (
              <div
                role="status"
                className="mt-5 rounded-xl border border-border bg-surface p-5 shadow-custom"
              >
                <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-3">
                  <div className="flex items-center gap-2.5">
                    <Badge tone={testResult.decision.decision === 'BLOCK' ? 'danger' : 'warning'} dot>
                      {testResult.decision.decision === 'BLOCK' ? 'Blocked Automatically' : 'Paused for Review'}
                    </Badge>
                    <h3 className="font-display text-base font-bold text-ink">{testResult.attackLabel}</h3>
                  </div>
                  <span className="inline-flex items-center rounded-full border border-[#16A34A]/25 bg-success-bg px-3 py-1 text-xs font-bold text-success">
                    $0.00 Charged to PayPal
                  </span>
                </div>

                <p className="mt-3 text-sm font-medium text-ink">{testResult.summary}</p>

                <div className="mt-3 rounded-lg border border-border bg-background p-3.5 text-xs text-muted">
                  <p className="font-semibold text-ink">Why BuyBound stopped this purchase:</p>
                  <p className="mt-1">{testResult.aiForensics.aiForensicsSummary}</p>
                  {testResult.decision.reasonCodes.length > 0 && (
                    <ul className="mt-2 space-y-1">
                      {testResult.decision.reasonCodes.map((code) => (
                        <li key={code} className="text-ink">
                          • {REASON_CODE_EXPLANATIONS[code as ReasonCode] ?? code}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </div>
            )}
          </Card>
        </>
      )}

      <PayPalVaultModal
        open={vaultModalOpen}
        mandateId={telemetry?.mandateId ?? null}
        connected={Boolean(telemetry?.paypalConnected)}
        vaultToken={telemetry?.maskedVaultToken}
        payerEmail={telemetry?.paypalPayerEmail}
        payerId={telemetry?.paypalPayerId}
        sandboxBalance={telemetry?.sandboxBalance}
        onClose={() => setVaultModalOpen(false)}
        onConnected={() => {
          void loadData();
          window.dispatchEvent(new CustomEvent('buybound:refresh'));
        }}
      />
    </div>
  );
}

