import { useCallback, useEffect, useId, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { Badge } from '../../../components/ui/Badge';
import { PayPalVaultModal } from '../../../components/modals/PayPalVaultModal';
import { useAuth } from '../../../context/authState';
import { mandatesApi } from '../../../services/api/endpoints';
import { toApiError } from '../../../services/api/client';
import type { ActiveMandateTelemetry } from '../../../services/api/types';
import { formatUsd } from '../../shared/meta';

export function ProfilePage() {
  const navigate = useNavigate();
  const {
    user,
    isAuthenticated,
    updateProfile,
    resetWallet,
    disconnectPayPal,
    logout,
    refreshProfile,
  } = useAuth();

  const headingId = useId();
  const [telemetry, setTelemetry] = useState<ActiveMandateTelemetry | null>(null);
  const [vaultModalOpen, setVaultModalOpen] = useState(false);

  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [shippingStreet, setShippingStreet] = useState('');
  const [shippingCity, setShippingCity] = useState('');
  const [shippingState, setShippingState] = useState('');
  const [shippingPostalCode, setShippingPostalCode] = useState('');
  const [shippingCountry, setShippingCountry] = useState('United States');
  const [notifyPriceDrops, setNotifyPriceDrops] = useState(true);
  const [notifyHeldSms, setNotifyHeldSms] = useState(true);
  const [notifyReceiptEmails, setNotifyReceiptEmails] = useState(true);

  const [saving, setSaving] = useState(false);
  const [walletActing, setWalletActing] = useState(false);
  const [banner, setBanner] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null);

  const loadTelemetry = useCallback(async () => {
    try {
      const data = await mandatesApi.active();
      setTelemetry(data);
    } catch {
      // keep existing wallet values
    }
  }, []);

  useEffect(() => {
    void loadTelemetry();
  }, [loadTelemetry]);

  useEffect(() => {
    if (!user) return;
    setFullName(user.fullName ?? '');
    setEmail(user.email ?? '');
    setPhone(user.phone ?? '');
    setShippingStreet(user.shippingStreet ?? '');
    setShippingCity(user.shippingCity ?? '');
    setShippingState(user.shippingState ?? '');
    setShippingPostalCode(user.shippingPostalCode ?? '');
    setShippingCountry(user.shippingCountry ?? 'United States');
    setNotifyPriceDrops(user.notifyPriceDrops);
    setNotifyHeldSms(user.notifyHeldSms);
    setNotifyReceiptEmails(user.notifyReceiptEmails);
  }, [user]);

  const handleSaveProfile = async (e: FormEvent) => {
    e.preventDefault();
    setBanner(null);
    setSaving(true);
    try {
      await updateProfile({
        fullName: fullName.trim(),
        email: email.trim(),
        phone: phone.trim(),
        shippingStreet: shippingStreet.trim(),
        shippingCity: shippingCity.trim(),
        shippingState: shippingState.trim(),
        shippingPostalCode: shippingPostalCode.trim(),
        shippingCountry: shippingCountry.trim() || 'United States',
        notifyPriceDrops,
        notifyHeldSms,
        notifyReceiptEmails,
      });
      setBanner({
        tone: 'success',
        text: 'Personal details, shipping address, and notification preferences saved.',
      });
    } catch (err: unknown) {
      setBanner({ tone: 'danger', text: toApiError(err).message });
    } finally {
      setSaving(false);
    }
  };

  const handleResetWallet = async () => {
    setBanner(null);
    setWalletActing(true);
    try {
      await resetWallet();
      await loadTelemetry();
      setBanner({
        tone: 'success',
        text: 'Weekly spend reservations reset to $0.00.',
      });
    } catch (err: unknown) {
      setBanner({ tone: 'danger', text: toApiError(err).message });
    } finally {
      setWalletActing(false);
    }
  };

  const handleDisconnectPayPal = async () => {
    setBanner(null);
    setWalletActing(true);
    try {
      await disconnectPayPal();
      await loadTelemetry();
      setBanner({
        tone: 'success',
        text: 'Disconnected linked PayPal Sandbox buyer account. You can link another anytime.',
      });
    } catch (err: unknown) {
      setBanner({ tone: 'danger', text: toApiError(err).message });
    } finally {
      setWalletActing(false);
    }
  };

  const handleLogout = async () => {
    navigate('/', { replace: true });
    await logout();
  };

  const inputClass =
    'w-full min-h-[46px] rounded-xl border border-brand-border bg-white px-3.5 text-sm text-brand-charcoal placeholder:text-brand-charcoal-muted/60 focus:border-brand-coral focus:outline-none focus:ring-2 focus:ring-brand-coral/20';

  if (!isAuthenticated || !user) {
    return (
      <div className="mx-auto max-w-3xl space-y-6">
        <Card className="p-8 text-center">
          <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-brand-peach-soft text-brand-coral font-display font-bold text-lg">
            BB
          </div>
          <h1 className="font-display text-2xl font-bold text-brand-charcoal">
            Sign in to manage your BuyBound Profile
          </h1>
          <p className="mx-auto mt-2 max-w-lg text-sm text-brand-charcoal-muted">
            Sign in with your email and password or PayPal Sandbox account to edit shipping details, manage
            notification alerts, or review your spending limits.
          </p>
          <div className="mt-6 flex flex-wrap justify-center gap-3">
            <Link
              to="/login"
              className="inline-flex min-h-[44px] items-center rounded-full bg-brand-coral px-6 text-sm font-semibold text-white shadow-sm hover:bg-brand-coral-hover transition-colors"
            >
              Log In
            </Link>
            <Link
              to="/signup"
              className="inline-flex min-h-[44px] items-center rounded-full border border-brand-border bg-white px-6 text-sm font-semibold text-brand-charcoal hover:bg-brand-ivory transition-colors"
            >
              Create Account
            </Link>
          </div>
        </Card>
      </div>
    );
  }

  const linkedPayPalEmail = user.paypalEmail ?? telemetry?.paypalPayerEmail;
  const linkedPayerId = user.paypalPayerId ?? telemetry?.paypalPayerId;
  const paypalConnected = Boolean(user.paypalConnected || telemetry?.paypalConnected);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 id={headingId} className="font-display text-2xl sm:text-3xl font-bold text-brand-charcoal">
            My Profile &amp; Account
          </h1>
          <p className="mt-1 text-sm text-brand-charcoal-muted">
            Manage your personal contact information, default shipping address, PayPal Sandbox wallet, and session.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Badge tone={paypalConnected ? 'success' : 'neutral'}>
            {paypalConnected ? 'PayPal Sandbox Linked' : 'PayPal Not Linked'}
          </Badge>
        </div>
      </div>

      {banner && (
        <div
          role={banner.tone === 'danger' ? 'alert' : 'status'}
          aria-live="polite"
          className={`flex items-center justify-between gap-3 rounded-2xl border px-4 py-3 text-sm font-medium ${
            banner.tone === 'success'
              ? 'border-success/30 bg-success-bg text-success'
              : 'border-danger/30 bg-danger-bg text-danger'
          }`}
        >
          <span>{banner.text}</span>
          <button type="button" onClick={() => setBanner(null)} className="text-xs underline">
            Dismiss
          </button>
        </div>
      )}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1.35fr_1fr]">
        {/* Section A: Personal Details + Default Shipping Address + Notifications */}
        <Card className="p-6">
          <h2 className="font-display text-lg font-bold text-brand-charcoal">
            Section A — Personal Details &amp; Shipping
          </h2>
          <p className="mt-1 text-xs text-brand-charcoal-muted">
            Used for instant PayPal receipts and automated price-drop notifications.
          </p>

          <form method="POST" onSubmit={(e) => void handleSaveProfile(e)} className="mt-5 space-y-5">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div className="sm:col-span-2">
                <label htmlFor="profile-full-name" className="block text-xs font-semibold text-brand-charcoal mb-1.5">
                  Full Name <span aria-hidden="true" className="text-brand-coral">*</span>
                </label>
                <input
                  id="profile-full-name"
                  name="fullName"
                  type="text"
                  required
                  autoComplete="name"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  className={inputClass}
                />
              </div>

              <div>
                <label htmlFor="profile-email" className="block text-xs font-semibold text-brand-charcoal mb-1.5">
                  Email Address <span aria-hidden="true" className="text-brand-coral">*</span>
                </label>
                <input
                  id="profile-email"
                  name="email"
                  type="email"
                  required
                  spellCheck={false}
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className={inputClass}
                />
              </div>

              <div>
                <label htmlFor="profile-phone" className="block text-xs font-semibold text-brand-charcoal mb-1.5">
                  Phone Number
                </label>
                <input
                  id="profile-phone"
                  name="phone"
                  type="tel"
                  autoComplete="tel"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  placeholder="+1 (415) 555-0194"
                  className={inputClass}
                />
              </div>
            </div>

            <fieldset className="rounded-xl border border-brand-border bg-brand-ivory/50 p-4 space-y-3">
              <legend className="px-1.5 text-xs font-bold uppercase tracking-wider text-brand-charcoal">
                Default Shipping Address
              </legend>

              <div>
                <label htmlFor="profile-street" className="block text-xs font-semibold text-brand-charcoal mb-1">
                  Street Address
                </label>
                <input
                  id="profile-street"
                  name="shippingStreet"
                  type="text"
                  autoComplete="shipping address-line1"
                  value={shippingStreet}
                  onChange={(e) => setShippingStreet(e.target.value)}
                  placeholder="742 Evergreen Way"
                  className={inputClass}
                />
              </div>

              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div>
                  <label htmlFor="profile-city" className="block text-xs font-semibold text-brand-charcoal mb-1">
                    City
                  </label>
                  <input
                    id="profile-city"
                    name="shippingCity"
                    type="text"
                    autoComplete="shipping address-level2"
                    value={shippingCity}
                    onChange={(e) => setShippingCity(e.target.value)}
                    placeholder="San Francisco"
                    className={inputClass}
                  />
                </div>
                <div>
                  <label htmlFor="profile-state" className="block text-xs font-semibold text-brand-charcoal mb-1">
                    State / Province
                  </label>
                  <input
                    id="profile-state"
                    name="shippingState"
                    type="text"
                    autoComplete="shipping address-level1"
                    value={shippingState}
                    onChange={(e) => setShippingState(e.target.value)}
                    placeholder="CA"
                    className={inputClass}
                  />
                </div>
                <div>
                  <label htmlFor="profile-postal" className="block text-xs font-semibold text-brand-charcoal mb-1">
                    ZIP / Postal Code
                  </label>
                  <input
                    id="profile-postal"
                    name="shippingPostalCode"
                    type="text"
                    autoComplete="shipping postal-code"
                    value={shippingPostalCode}
                    onChange={(e) => setShippingPostalCode(e.target.value)}
                    placeholder="94107"
                    className={inputClass}
                  />
                </div>
                <div>
                  <label htmlFor="profile-country" className="block text-xs font-semibold text-brand-charcoal mb-1">
                    Country
                  </label>
                  <input
                    id="profile-country"
                    name="shippingCountry"
                    type="text"
                    autoComplete="shipping country-name"
                    value={shippingCountry}
                    onChange={(e) => setShippingCountry(e.target.value)}
                    placeholder="United States"
                    className={inputClass}
                  />
                </div>
              </div>
            </fieldset>

            <fieldset className="rounded-xl border border-brand-border bg-white p-4 space-y-2.5">
              <legend className="px-1.5 text-xs font-bold uppercase tracking-wider text-brand-charcoal">
                Notification Preferences
              </legend>

              <div className="flex items-center gap-2.5">
                <input
                  id="notify-drops"
                  type="checkbox"
                  checked={notifyPriceDrops}
                  onChange={(e) => setNotifyPriceDrops(e.target.checked)}
                  className="h-4 w-4 accent-brand-coral"
                />
                <label htmlFor="notify-drops" className="text-sm text-brand-charcoal">
                  Email price-drop &amp; restock alerts
                </label>
              </div>

              <div className="flex items-center gap-2.5">
                <input
                  id="notify-holds"
                  type="checkbox"
                  checked={notifyHeldSms}
                  onChange={(e) => setNotifyHeldSms(e.target.checked)}
                  className="h-4 w-4 accent-brand-coral"
                />
                <label htmlFor="notify-holds" className="text-sm text-brand-charcoal">
                  SMS alerts for held orders requiring approval
                </label>
              </div>

              <div className="flex items-center gap-2.5">
                <input
                  id="notify-receipts"
                  type="checkbox"
                  checked={notifyReceiptEmails}
                  onChange={(e) => setNotifyReceiptEmails(e.target.checked)}
                  className="h-4 w-4 accent-brand-coral"
                />
                <label htmlFor="notify-receipts" className="text-sm text-brand-charcoal">
                  Instant verified PayPal receipt confirmations
                </label>
              </div>
            </fieldset>

            <div className="flex flex-wrap items-center justify-end gap-3 pt-1">
              <Button type="submit" loading={saving}>
                Save Changes
              </Button>
            </div>
          </form>
        </Card>

        {/* Right column: Section B (PayPal Wallet Summary) + Section C (Security & Log Out) */}
        <div className="space-y-6">
          <Card className="p-6">
            <h2 className="font-display text-lg font-bold text-brand-charcoal">
              Section B — Connected PayPal Wallet
            </h2>
            <p className="mt-1 text-xs text-brand-charcoal-muted">
              Verified Personal Sandbox Buyer balance and weekly spend controls.
            </p>

            <div className="mt-4 rounded-xl border border-[#0070BA]/25 bg-[#0070BA]/5 p-4 space-y-2.5">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-brand-charcoal-muted">PayPal Sandbox Status</span>
                <span className={`text-sm font-bold ${paypalConnected ? 'text-success' : 'text-brand-charcoal-muted'}`}>
                  {paypalConnected ? 'Connected' : 'Not Connected'}
                </span>
              </div>
              <div className="flex items-center justify-between text-xs">
                <span className="text-brand-charcoal-muted">Linked Sandbox Account</span>
                <span className="mono font-semibold text-brand-charcoal">
                  {linkedPayPalEmail ?? 'Not connected yet'}
                </span>
              </div>
              {linkedPayerId && (
                <div className="flex items-center justify-between text-xs">
                  <span className="text-brand-charcoal-muted">PayPal Payer ID</span>
                  <span className="mono font-semibold text-brand-charcoal">{linkedPayerId}</span>
                </div>
              )}
              <div className="flex items-center justify-between text-xs border-t border-[#0070BA]/15 pt-2">
                <span className="text-brand-charcoal-muted">Spent This Week</span>
                <span className="mono font-semibold text-brand-charcoal">
                  {formatUsd(user.spentThisWeek)} of {formatUsd(user.weeklyLimit)}
                </span>
              </div>
            </div>

            <div className="mt-4 space-y-2.5">
              <Button
                type="button"
                variant="secondary"
                className="w-full justify-center"
                onClick={() => setVaultModalOpen(true)}
              >
                {paypalConnected ? 'Switch PayPal Account' : 'Connect PayPal Sandbox Account'}
              </Button>

              {paypalConnected && (
                <Button
                  type="button"
                  variant="ghost"
                  loading={walletActing}
                  className="w-full justify-center text-danger hover:bg-danger-bg"
                  onClick={() => void handleDisconnectPayPal()}
                >
                  Disconnect PayPal Account
                </Button>
              )}

              <Button
                type="button"
                variant="secondary"
                loading={walletActing}
                className="w-full justify-center border-brand-coral/30 text-brand-coral hover:bg-brand-peach-soft"
                onClick={() => void handleResetWallet()}
              >
                Reset Weekly Spend Reservations
              </Button>
            </div>
          </Card>

          <Card className="p-6">
            <h2 className="font-display text-lg font-bold text-brand-charcoal">
              Section C — Account Security &amp; Session
            </h2>
            <p className="mt-1 text-xs text-brand-charcoal-muted">
              Signed in as <strong className="text-brand-charcoal">{user.email}</strong>. Need to adjust automated
              item limits or merchant allowlists?
            </p>

            <div className="mt-4 flex flex-col gap-2.5">
              <Link
                to="/app/safety"
                className="inline-flex min-h-[42px] items-center justify-between rounded-xl border border-brand-border bg-brand-ivory px-4 text-sm font-medium text-brand-charcoal hover:border-brand-coral/40 transition-colors"
              >
                <span>Manage Spending Rules &amp; Merchant Allowlist</span>
                <span aria-hidden="true">→</span>
              </Link>
              <Link
                to="/app/purchases"
                className="inline-flex min-h-[42px] items-center justify-between rounded-xl border border-brand-border bg-brand-ivory px-4 text-sm font-medium text-brand-charcoal hover:border-brand-coral/40 transition-colors"
              >
                <span>View Orders &amp; PayPal Receipts</span>
                <span aria-hidden="true">→</span>
              </Link>
            </div>

            <div className="mt-5 border-t border-brand-border pt-4">
              <Button
                type="button"
                variant="danger"
                className="w-full justify-center"
                onClick={() => void handleLogout()}
              >
                Log Out of BuyBound
              </Button>
            </div>
          </Card>
        </div>
      </div>

      <PayPalVaultModal
        open={vaultModalOpen}
        mandateId={telemetry?.mandateId ?? null}
        connected={paypalConnected}
        vaultToken={telemetry?.maskedVaultToken}
        payerEmail={linkedPayPalEmail ?? undefined}
        payerId={linkedPayerId ?? undefined}
        onClose={() => setVaultModalOpen(false)}
        onConnected={() => {
          void loadTelemetry();
          void refreshProfile();
          window.dispatchEvent(new CustomEvent('buybound:refresh'));
        }}
      />
    </div>
  );
}
