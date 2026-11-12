import { useCallback, useEffect, useId, useRef, useState } from 'react';
import type { KeyboardEvent as ReactKeyboardEvent } from 'react';
import { Link, NavLink, useNavigate } from 'react-router-dom';
import { Button } from '../ui/Button';
import { Dialog } from '../ui/Dialog';
import { useAuth } from '../../context/authState';
import { mandatesApi, watchesApi } from '../../services/api/endpoints';
import { toApiError } from '../../services/api/client';
import { formatUsd } from '../../features/shared/meta';
import type { ActiveMandateTelemetry } from '../../services/api/types';
import { APP_NAV_ITEMS, type NavIconKey } from './nav';

function NavTabIcon({ icon }: { icon: NavIconKey }) {
  switch (icon) {
    case 'bag':
      return (
        <svg className="h-4 w-4 shrink-0" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4ZM3 6h18M16 10a4 4 0 0 1-8 0" />
        </svg>
      );
    case 'bell':
      return (
        <svg className="h-4 w-4 shrink-0" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 0 1-3.46 0" />
        </svg>
      );
    case 'tag':
      return (
        <svg className="h-4 w-4 shrink-0" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" d="M20.59 13.41 13.42 20.58a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82Z" />
          <line x1="7" y1="7" x2="7.01" y2="7" />
        </svg>
      );
    case 'receipt':
      return (
        <svg className="h-4 w-4 shrink-0" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" d="M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1-2-1Z" />
          <path strokeLinecap="round" strokeLinejoin="round" d="M8 8h8M8 12h8M8 16h5" />
        </svg>
      );
    case 'shield':
      return (
        <svg className="h-4 w-4 shrink-0" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z" />
          <path strokeLinecap="round" strokeLinejoin="round" d="m9 12 2 2 4-4" />
        </svg>
      );
    case 'sliders':
      return (
        <svg className="h-4 w-4 shrink-0" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" aria-hidden="true">
          <line x1="4" y1="21" x2="4" y2="14" />
          <line x1="4" y1="10" x2="4" y2="3" />
          <line x1="12" y1="21" x2="12" y2="12" />
          <line x1="12" y1="8" x2="12" y2="3" />
          <line x1="20" y1="21" x2="20" y2="16" />
          <line x1="20" y1="12" x2="20" y2="3" />
          <line x1="1" y1="14" x2="7" y2="14" />
          <line x1="9" y1="8" x2="15" y2="8" />
          <line x1="17" y1="16" x2="23" y2="16" />
        </svg>
      );
  }
}

function getInitials(name: string | null | undefined, email: string | null | undefined): string {
  const source = (name ?? '').trim();
  if (source.length > 0) {
    const parts = source.split(/\s+/).filter(Boolean);
    if (parts.length >= 2) {
      return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
    }
    return source.slice(0, 2).toUpperCase();
  }
  const fallback = (email ?? 'BB').trim();
  return fallback.slice(0, 2).toUpperCase();
}

function getFirstName(name: string | null | undefined, email: string | null | undefined): string {
  const cleanName = (name ?? '').trim();
  if (cleanName.length > 0 && !cleanName.includes('@')) {
    return cleanName.split(/\s+/)[0];
  }
  const emailLocal = (email ?? '').split('@')[0]?.trim();
  if (emailLocal && !emailLocal.startsWith('sb-')) {
    return emailLocal.split(/[._-]/)[0] || 'Account';
  }
  return 'Account';
}

export function Header() {
  const navigate = useNavigate();
  const { user, isAuthenticated, logout } = useAuth();
  const [telemetry, setTelemetry] = useState<ActiveMandateTelemetry | null>(null);
  const [killSwitchOpen, setKillSwitchOpen] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [acting, setActing] = useState(false);
  const [notification, setNotification] = useState<{ text: string; tone: 'danger' | 'success' } | null>(null);

  const menuTriggerId = useId();
  const mobileNavId = useId();
  const menuContainerRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuGroupRef = useRef<HTMLDivElement>(null);
  const enterOnOpenRef = useRef(false);

  const supportsNativeFocusgroup =
    typeof HTMLElement !== 'undefined' && 'focusGroup' in HTMLElement.prototype;

  const loadTelemetry = useCallback(async () => {
    try {
      const data = await mandatesApi.active();
      setTelemetry(data);
    } catch {
      // Keep current header bar visible if backend briefly restarts
    }
  }, []);

  useEffect(() => {
    void loadTelemetry();
    const onRefresh = () => {
      void loadTelemetry();
    };
    window.addEventListener('buybound:refresh', onRefresh);
    return () => window.removeEventListener('buybound:refresh', onRefresh);
  }, [loadTelemetry]);

  useEffect(() => {
    if (!menuOpen) return;

    if (enterOnOpenRef.current) {
      enterOnOpenRef.current = false;
      const firstItem = menuGroupRef.current?.querySelector<HTMLElement>('a, button');
      firstItem?.focus({ preventScroll: true });
    }

    const handleOutsidePointer = (event: PointerEvent) => {
      if (!menuContainerRef.current?.contains(event.target as Node)) {
        setMenuOpen(false);
      }
    };
    const handleOutsideFocus = (event: FocusEvent) => {
      if (!menuContainerRef.current?.contains(event.target as Node)) {
        setMenuOpen(false);
      }
    };
    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setMenuOpen(false);
        triggerRef.current?.focus();
      }
    };

    document.addEventListener('pointerdown', handleOutsidePointer);
    document.addEventListener('focusin', handleOutsideFocus);
    document.addEventListener('keydown', handleEscape);
    return () => {
      document.removeEventListener('pointerdown', handleOutsidePointer);
      document.removeEventListener('focusin', handleOutsideFocus);
      document.removeEventListener('keydown', handleEscape);
    };
  }, [menuOpen]);

  const isPaused = telemetry?.status === 'REVOKED';
  const heldCount = telemetry?.heldApprovalsCount ?? 0;
  const paypalConnected = Boolean(user?.paypalConnected || telemetry?.paypalConnected);
  const weeklyBudgetLeft = telemetry?.remainingBudget ?? user?.remainingWeeklyBudget;
  const hasPositiveBudget = weeklyBudgetLeft !== undefined && weeklyBudgetLeft !== null && Number(weeklyBudgetLeft) > 0;

  const handlePauseAll = async () => {
    setActing(true);
    try {
      const res = telemetry?.mandateId
        ? await mandatesApi.killSwitch(telemetry.mandateId)
        : await watchesApi.killSwitch();
      setNotification({ text: res.message, tone: 'danger' });
      setKillSwitchOpen(false);
      await loadTelemetry();
      window.dispatchEvent(new CustomEvent('buybound:refresh'));
    } catch (err: unknown) {
      setNotification({ text: toApiError(err).message, tone: 'danger' });
      setKillSwitchOpen(false);
    } finally {
      setActing(false);
    }
  };

  const handleRestoreAll = async () => {
    if (!telemetry?.mandateId) {
      navigate('/app/safety');
      return;
    }
    setActing(true);
    try {
      const res = await mandatesApi.restore(telemetry.mandateId);
      setNotification({ text: res.message, tone: 'success' });
      await loadTelemetry();
      window.dispatchEvent(new CustomEvent('buybound:refresh'));
    } catch (err: unknown) {
      setNotification({ text: toApiError(err).message, tone: 'danger' });
    } finally {
      setActing(false);
    }
  };

  const handleLogout = async () => {
    setMenuOpen(false);
    navigate('/', { replace: true });
    await logout();
  };

  const handleTriggerKeyDown = (event: ReactKeyboardEvent<HTMLButtonElement>) => {
    if (event.key === 'ArrowDown' || event.key === 'Enter' || event.key === ' ') {
      enterOnOpenRef.current = true;
      if (event.key === 'ArrowDown') {
        event.preventDefault();
        if (!menuOpen) {
          setMenuOpen(true);
        } else {
          const firstItem = menuGroupRef.current?.querySelector<HTMLElement>('a, button');
          firstItem?.focus({ preventScroll: true });
        }
      }
    }
  };

  const handleMenuKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Tab') {
      setMenuOpen(false);
      return;
    }
    if (supportsNativeFocusgroup) return;
    if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return;

    const items = Array.from(
      menuGroupRef.current?.querySelectorAll<HTMLElement>('a, button') ?? [],
    ).filter((el) => !el.hasAttribute('disabled'));
    if (items.length === 0) return;

    const current = items.indexOf(document.activeElement as HTMLElement);
    const nextIndex =
      event.key === 'Home'
        ? 0
        : event.key === 'End'
          ? items.length - 1
          : current < 0
            ? 0
            : (current + (event.key === 'ArrowUp' ? -1 : 1) + items.length) % items.length;
    event.preventDefault();
    items[nextIndex].focus({ preventScroll: true });
  };

  const initials = getInitials(user?.fullName, user?.email ?? user?.paypalEmail);
  const shortName = getFirstName(user?.fullName, user?.email ?? user?.paypalEmail);
  const displayName = user?.fullName ?? 'Shopper Account';
  const displayEmail = user?.email ?? user?.paypalEmail ?? '';

  return (
    <header className="sticky top-0 z-40 border-b border-brand-border/90 bg-[#FAF7F2]/95 backdrop-blur-md shadow-[0_1px_0_rgba(41,39,36,0.03)] no-print">
      <a href="#main-content" className="skip-link visually-hidden">
        Skip to main content
      </a>

      {/* Full-width Left-Aligned Professional Navbar */}
      <div className="w-full px-4 sm:px-6 lg:px-8">
        <div className="flex min-h-[64px] flex-wrap items-center justify-between gap-4 py-2 lg:flex-nowrap">
          {/* Left Group: Brand flush left + clean inline navigation */}
          <div className="flex items-center gap-5 min-w-0">
            <Link to="/" className="flex shrink-0 items-center gap-2.5 group" aria-label="BuyBound Home">
              <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-tr from-brand-coral to-[#F78C74] text-white shadow-sm shadow-brand-coral/20 transition-transform group-hover:scale-105">
                <svg
                  aria-hidden="true"
                  fill="none"
                  height="18"
                  stroke="currentColor"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth="2.2"
                  viewBox="0 0 24 24"
                  width="18"
                >
                  <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-2Z" />
                  <path d="M3 6h18" />
                  <path d="M16 10a4 4 0 0 1-8 0" />
                </svg>
              </div>
              <span className="font-display text-xl font-bold tracking-tight text-brand-charcoal flex items-center">
                BuyBound<span className="ml-1 inline-block h-1.5 w-1.5 rounded-full bg-brand-coral" />
              </span>
            </Link>

            <span className="hidden lg:block h-5 w-px bg-brand-border" aria-hidden="true" />

            {/* Desktop Inline Navigation (borderless, uncrowded spacing) */}
            <nav
              aria-label="Primary navigation"
              className="hidden lg:flex lg:items-center lg:gap-1"
            >
              {APP_NAV_ITEMS.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) =>
                    `bb-nav-link inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap rounded-lg px-3 py-1.5 text-[13px] transition-colors ${
                      isActive
                        ? 'bg-brand-peach-soft text-brand-coral font-semibold'
                        : 'font-medium text-brand-charcoal-muted hover:bg-white/80 hover:text-brand-charcoal'
                    }`
                  }
                >
                  <NavTabIcon icon={item.icon} />
                  <span>{item.label}</span>
                  {item.to === '/app/purchases' && heldCount > 0 && (
                    <span
                      title={`${heldCount} order${heldCount > 1 ? 's' : ''} on hold awaiting approval`}
                      className="inline-flex h-4 min-w-[16px] items-center justify-center rounded-full bg-warning-bg border border-warning/35 px-1 text-[10px] font-bold text-warning"
                    >
                      {heldCount}
                    </span>
                  )}
                </NavLink>
              ))}
            </nav>
          </div>

          {/* Right Utility Group: Compact Status Controls & Account */}
          <div className="ml-auto flex shrink-0 items-center gap-2.5">
            {isAuthenticated && (
              <>
                <Link
                  to="/app/safety"
                  aria-label="CISO Security & DLP Shield: Active"
                  title="CISO Security & DLP Shield: Active — Scoped Virtual Allowance, DLP Redaction, Webhook Replay Guard & Rate Limiting"
                  className="hidden 2xl:inline-flex h-8.5 items-center gap-1.5 whitespace-nowrap rounded-lg border border-[#16A34A]/25 bg-success-bg/70 px-2.5 py-1.5 text-xs font-medium text-success transition-colors hover:border-[#16A34A]/45"
                >
                  <svg
                    aria-hidden="true"
                    className="h-3.5 w-3.5 shrink-0"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2.2"
                    viewBox="0 0 24 24"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z" />
                    <path strokeLinecap="round" strokeLinejoin="round" d="m9 12 2 2 4-4" />
                  </svg>
                  <span>Shield Active</span>
                </Link>

                <Link
                  to="/app/safety"
                  title={
                    hasPositiveBudget
                      ? `PayPal Sandbox ${paypalConnected ? 'Connected' : 'Ready'} · ${formatUsd(weeklyBudgetLeft)} weekly cap left`
                      : 'PayPal Sandbox connection — manage Wallet & Safety'
                  }
                  className="inline-flex h-8.5 items-center gap-1.5 whitespace-nowrap rounded-lg border border-[#0070BA]/25 bg-white px-2.5 py-1.5 text-xs font-semibold text-brand-charcoal shadow-[0_1px_2px_rgba(41,39,36,0.03)] transition-colors hover:border-[#0070BA]/50 hover:bg-[#F5FAFF]"
                >
                  <span
                    aria-hidden="true"
                    className={`h-2 w-2 shrink-0 rounded-full ${paypalConnected ? 'bg-[#0070BA]' : 'bg-amber-400'}`}
                  />
                  <span className="text-brand-charcoal-muted font-medium">PayPal</span>
                  <strong className="font-bold text-[#0070BA]">
                    {paypalConnected ? 'Connected' : 'Connect'}
                  </strong>
                </Link>

                {isPaused ? (
                  <Button
                    variant="secondary"
                    loading={acting}
                    onClick={() => void handleRestoreAll()}
                    className="!min-h-[34px] !rounded-lg !px-3 !py-1.5 !text-xs whitespace-nowrap"
                  >
                    Resume Buying
                  </Button>
                ) : (
                  <button
                    type="button"
                    onClick={() => setKillSwitchOpen(true)}
                    className="hidden sm:inline-flex h-8.5 items-center gap-1.5 whitespace-nowrap rounded-lg border border-danger/30 bg-white px-2.5 py-1.5 text-xs font-semibold text-danger shadow-[0_1px_2px_rgba(41,39,36,0.03)] transition-colors hover:border-danger hover:bg-danger hover:text-white"
                  >
                    <svg
                      aria-hidden="true"
                      className="h-3.5 w-3.5 shrink-0"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                      viewBox="0 0 24 24"
                    >
                      <circle cx="12" cy="12" r="9" />
                      <path strokeLinecap="round" d="M10 9v6M14 9v6" />
                    </svg>
                    <span>Pause Buying</span>
                  </button>
                )}

                {/* Clean Profile Chip & Dropdown */}
                <div ref={menuContainerRef} className="relative">
                  <button
                    ref={triggerRef}
                    id={menuTriggerId}
                    type="button"
                    aria-haspopup="menu"
                    aria-expanded={menuOpen}
                    onClick={() => setMenuOpen((prev) => !prev)}
                    onKeyDown={handleTriggerKeyDown}
                    className={`inline-flex h-8.5 items-center gap-2 whitespace-nowrap rounded-lg border pl-1.5 pr-2.5 py-1 text-left shadow-[0_1px_2px_rgba(41,39,36,0.03)] transition-colors ${
                      menuOpen
                        ? 'border-brand-coral bg-brand-peach-soft/70 text-brand-charcoal'
                        : 'border-brand-border bg-white text-brand-charcoal hover:border-brand-coral/40'
                    }`}
                  >
                    <span
                      aria-hidden="true"
                      className="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-brand-coral text-[11px] font-bold text-white"
                    >
                      {initials}
                    </span>
                    <span className="hidden sm:inline text-[13px] font-semibold text-brand-charcoal">
                      {shortName}
                    </span>
                    <svg
                      aria-hidden="true"
                      className={`h-3.5 w-3.5 shrink-0 text-brand-charcoal-muted transition-transform ${menuOpen ? 'rotate-180' : ''}`}
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                      viewBox="0 0 24 24"
                    >
                      <path strokeLinecap="round" strokeLinejoin="round" d="m6 9 6 6 6-6" />
                    </svg>
                  </button>

                  {menuOpen && (
                    <div
                      ref={menuGroupRef}
                      {...({ focusgroup: 'menu nomemory' } as Record<string, string>)}
                      role={supportsNativeFocusgroup ? undefined : 'menu'}
                      aria-labelledby={menuTriggerId}
                      onKeyDown={handleMenuKeyDown}
                      className="absolute right-0 mt-2 w-64 origin-top-right rounded-2xl border border-brand-border bg-white p-1.5 shadow-xl shadow-brand-charcoal/10 z-50"
                    >
                      <div className="px-3 py-2.5 border-b border-brand-border/70 mb-1">
                        <p className="text-xs font-bold text-brand-charcoal truncate">{displayName}</p>
                        <p className="text-[11px] text-brand-charcoal-muted truncate">{displayEmail}</p>
                        <p className="mt-1.5 inline-flex items-center gap-1.5 rounded-full bg-success-bg px-2 py-0.5 text-[10px] font-semibold text-success">
                          <span className="h-1.5 w-1.5 rounded-full bg-success" aria-hidden="true" />
                          CISO Security &amp; DLP Shield: Active
                        </p>
                      </div>

                      <Link
                        to="/app/profile"
                        role={supportsNativeFocusgroup ? undefined : 'menuitem'}
                        onClick={() => setMenuOpen(false)}
                        className="flex w-full items-center gap-2.5 rounded-xl px-3 py-2 text-sm font-medium text-brand-charcoal transition-colors hover:bg-brand-peach-soft hover:text-brand-coral focus:bg-brand-peach-soft focus:outline-none"
                      >
                        My Profile &amp; Account
                      </Link>

                      <Link
                        to="/audit"
                        role={supportsNativeFocusgroup ? undefined : 'menuitem'}
                        onClick={() => setMenuOpen(false)}
                        className="flex w-full items-center gap-2.5 rounded-xl px-3 py-2 text-sm font-medium text-brand-charcoal transition-colors hover:bg-brand-peach-soft hover:text-brand-coral focus:bg-brand-peach-soft focus:outline-none"
                      >
                        Safety Activity Log
                      </Link>

                      <hr className="my-1.5 border-brand-border" />

                      <button
                        type="button"
                        role={supportsNativeFocusgroup ? undefined : 'menuitem'}
                        onClick={() => void handleLogout()}
                        className="flex w-full items-center gap-2.5 rounded-xl px-3 py-2 text-left text-sm font-semibold text-danger transition-colors hover:bg-danger-bg focus:bg-danger-bg focus:outline-none"
                      >
                        Log Out
                      </button>
                    </div>
                  )}
                </div>
              </>
            )}

            {/* Responsive toggle visible below lg so navigation never crowds or wraps awkwardly */}
            <button
              type="button"
              aria-expanded={mobileNavOpen}
              aria-controls={mobileNavId}
              aria-label={mobileNavOpen ? 'Close navigation menu' : 'Open navigation menu'}
              onClick={() => setMobileNavOpen((v) => !v)}
              className="inline-flex h-9 w-9 items-center justify-center rounded-lg border border-brand-border bg-white text-brand-charcoal transition-colors hover:border-brand-coral/40 lg:hidden"
            >
              <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" aria-hidden="true">
                {mobileNavOpen ? (
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18 18 6M6 6l12 12" />
                ) : (
                  <path strokeLinecap="round" strokeLinejoin="round" d="M4 7h16M4 12h16M4 17h16" />
                )}
              </svg>
            </button>
          </div>
        </div>

        {/* Mobile Drawer Navigation */}
        {mobileNavOpen && (
          <nav
            id={mobileNavId}
            aria-label="Mobile navigation"
            className="flex flex-col gap-1 border-t border-brand-border/80 pt-3 pb-3 lg:hidden"
          >
            {APP_NAV_ITEMS.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                onClick={() => setMobileNavOpen(false)}
                className={({ isActive }) =>
                  `bb-nav-link inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm transition-colors ${
                    isActive
                      ? 'bg-brand-peach-soft font-semibold text-brand-coral'
                      : 'font-medium text-brand-charcoal-muted hover:bg-white hover:text-brand-charcoal'
                  }`
                }
              >
                <NavTabIcon icon={item.icon} />
                <span>{item.label}</span>
              </NavLink>
            ))}

            {isAuthenticated && !isPaused && (
              <button
                type="button"
                onClick={() => {
                  setMobileNavOpen(false);
                  setKillSwitchOpen(true);
                }}
                className="mt-1 inline-flex items-center justify-center gap-1.5 rounded-lg border border-danger/30 bg-white px-3 py-2 text-xs font-semibold text-danger sm:hidden"
              >
                Pause Buying
              </button>
            )}
          </nav>
        )}
      </div>

      {notification && (
        <div
          role="status"
          className={`border-t px-4 py-2 text-xs font-medium ${
            notification.tone === 'danger'
              ? 'border-danger/20 bg-danger-bg text-danger'
              : 'border-success/20 bg-success-bg text-success'
          }`}
        >
          <div className="page-shell flex items-center justify-between gap-4">
            <span>{notification.text}</span>
            <button type="button" onClick={() => setNotification(null)} className="underline hover:no-underline">
              Dismiss
            </button>
          </div>
        </div>
      )}

      <Dialog
        open={killSwitchOpen}
        title="Pause all automated buying?"
        confirmLabel="Pause All Buying"
        confirmVariant="danger"
        confirming={acting}
        onConfirm={handlePauseAll}
        onClose={() => setKillSwitchOpen(false)}
      >
        <p className="font-medium text-ink">
          This immediately pauses every active price watch and revokes automatic PayPal checkout permission.
        </p>
        <p className="mt-2 text-sm text-muted">
          No deals will be purchased until you turn buying back on inside Wallet &amp; Safety Settings.
        </p>
      </Dialog>
    </header>
  );
}
