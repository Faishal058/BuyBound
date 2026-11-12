import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { gsap, useGSAP } from '../../../lib/gsap';
import { loadPayPalSdk } from '../../../lib/paypalSdk';
import { useAuth } from '../../../context/authState';
import { paypalApi } from '../../../services/api/endpoints';
import { toApiError } from '../../../services/api/client';

interface AuthPageProps {
  initialTab?: 'signin' | 'signup';
}

export function AuthPage({ initialTab = 'signin' }: AuthPageProps) {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { login, signup, refreshProfile } = useAuth();
  const pageRef = useRef<HTMLDivElement>(null);
  const processedOAuthRef = useRef<string | null>(null);
  const formHeadingId = useId();
  const passwordHelpId = useId();

  const [activeTab, setActiveTab] = useState<'signin' | 'signup'>(initialTab);

  const [signInEmail, setSignInEmail] = useState('');
  const [signInPassword, setSignInPassword] = useState('');
  const [showSignInPassword, setShowSignInPassword] = useState(false);

  const [signUpName, setSignUpName] = useState('');
  const [signUpEmail, setSignUpEmail] = useState('');
  const [signUpPhone, setSignUpPhone] = useState('');
  const [signUpPassword, setSignUpPassword] = useState('');
  const [showSignUpPassword, setShowSignUpPassword] = useState(false);
  const [signUpPayPalEmail, setSignUpPayPalEmail] = useState('');
  const [currency, setCurrency] = useState<'USD' | 'EUR' | 'GBP'>('USD');
  const [agreeTerms, setAgreeTerms] = useState(true);

  const [oauthAuthorizeUrl, setOauthAuthorizeUrl] = useState('');
  const [paypalConfigured, setPaypalConfigured] = useState(false);
  const [paypalSdkReady, setPaypalSdkReady] = useState(false);
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  useEffect(() => {
    setActiveTab(initialTab);
    setErrorMsg(null);
  }, [initialTab]);

  useEffect(() => {
    let cancelled = false;
    void paypalApi
      .config()
      .then(async (cfg) => {
        if (cancelled) return;
        setPaypalConfigured(Boolean(cfg.configured));
        if (cfg.oauthAuthorizeUrl) setOauthAuthorizeUrl(cfg.oauthAuthorizeUrl);
        if (cfg.configured && cfg.clientId) {
          await loadPayPalSdk(cfg.clientId, cfg.currency || 'USD').catch(() => undefined);
          if (!cancelled) setPaypalSdkReady(true);
        }
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const isVaultLogin = searchParams.get('paypal_vault_login') === 'true';
    const isCancelled = searchParams.get('paypal_cancelled') === 'true';
    const isOAuth = searchParams.get('paypal_oauth') === 'true';
    const oauthCode = searchParams.get('code');
    const oauthError = searchParams.get('error_description') ?? searchParams.get('error');

    if (isCancelled) {
      sessionStorage.removeItem('buybound_pending_login_setup_token');
      setErrorMsg('PayPal Sandbox sign-in was cancelled.');
      const next = new URLSearchParams(searchParams);
      next.delete('paypal_cancelled');
      setSearchParams(next, { replace: true });
      return;
    }

    if (isVaultLogin) {
      const setupToken =
        searchParams.get('approval_session_id') ??
        searchParams.get('setup_token') ??
        searchParams.get('token') ??
        sessionStorage.getItem('buybound_pending_login_setup_token');
      if (!setupToken || processedOAuthRef.current === setupToken) {
        return;
      }
      processedOAuthRef.current = setupToken;
      setLoading(true);
      setErrorMsg(null);
      void (async () => {
        try {
          const tel = await paypalApi.vaultLoginCallback(setupToken);
          sessionStorage.removeItem('buybound_pending_login_setup_token');
          await refreshProfile();
          window.dispatchEvent(new CustomEvent('buybound:refresh'));
          if (typeof BroadcastChannel !== 'undefined') {
            const channel = new BroadcastChannel('buybound-paypal');
            channel.postMessage({ type: 'paypal-oauth-connected', payerId: tel.paypalPayerId });
            channel.close();
          }
          if (window.opener && !window.opener.closed) {
            window.close();
            return;
          }
          navigate('/app/shop', { replace: true });
        } catch (err: unknown) {
          setErrorMsg(toApiError(err).message);
          const next = new URLSearchParams(searchParams);
          next.delete('paypal_vault_login');
          next.delete('approval_session_id');
          next.delete('setup_token');
          next.delete('token');
          setSearchParams(next, { replace: true });
        } finally {
          setLoading(false);
        }
      })();
      return;
    }

    if (isOAuth && oauthError) {
      setErrorMsg(`PayPal Sandbox sign-in returned: ${oauthError}`);
      const next = new URLSearchParams(searchParams);
      next.delete('paypal_oauth');
      next.delete('error');
      next.delete('error_description');
      setSearchParams(next, { replace: true });
      return;
    }

    if (!isOAuth || !oauthCode || processedOAuthRef.current === oauthCode) {
      return;
    }

    processedOAuthRef.current = oauthCode;
    setLoading(true);
    setErrorMsg(null);
    void (async () => {
      try {
        const tel = await paypalApi.oauthCallback(oauthCode);
        await refreshProfile();
        window.dispatchEvent(new CustomEvent('buybound:refresh'));
        if (typeof BroadcastChannel !== 'undefined') {
          const channel = new BroadcastChannel('buybound-paypal');
          channel.postMessage({ type: 'paypal-oauth-connected', payerId: tel.paypalPayerId });
          channel.close();
        }
        if (window.opener && !window.opener.closed) {
          window.close();
          return;
        }
        navigate('/app/shop', { replace: true });
      } catch (err: unknown) {
        setErrorMsg(toApiError(err).message);
        const next = new URLSearchParams(searchParams);
        next.delete('paypal_oauth');
        next.delete('code');
        next.delete('scope');
        setSearchParams(next, { replace: true });
      } finally {
        setLoading(false);
      }
    })();
  }, [navigate, refreshProfile, searchParams, setSearchParams]);

  useGSAP(
    () => {
      const mm = gsap.matchMedia();
      mm.add('(prefers-reduced-motion: no-preference)', () => {
        gsap
          .timeline({ defaults: { ease: 'power2.out' } })
          .from('[data-auth-promo-copy] > *', { y: 14, autoAlpha: 0, duration: 0.4, stagger: 0.06 })
          .from('[data-auth-promo-card]', { y: 18, autoAlpha: 0, duration: 0.5 }, '-=0.25')
          .from('[data-auth-form]', { y: 12, autoAlpha: 0, duration: 0.45 }, 0.05);
      });
      return () => mm.revert();
    },
    { scope: pageRef },
  );

  const handlePayPalOAuthLogin = async () => {
    if (!paypalConfigured && !oauthAuthorizeUrl) {
      setErrorMsg('PayPal Client ID is not configured on the backend (.env PAYPAL_CLIENT_ID).');
      return;
    }
    setLoading(true);
    setErrorMsg(null);
    try {
      const res = await paypalApi.startLogin();
      if (res.setupTokenId) {
        sessionStorage.setItem('buybound_pending_login_setup_token', res.setupTokenId);
      }
      window.location.assign(res.approvalUrl);
    } catch (err: unknown) {
      setErrorMsg(toApiError(err).message);
      setLoading(false);
    }
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setErrorMsg(null);

    if (activeTab === 'signin') {
      const cleanEmail = signInEmail.trim();
      if (!cleanEmail || !cleanEmail.includes('@')) {
        setErrorMsg('Please enter a valid email address.');
        return;
      }
      if (!signInPassword || signInPassword.trim().length === 0) {
        setErrorMsg('Email address and password are required.');
        return;
      }
    } else {
      if (!signUpName.trim()) {
        setErrorMsg('Full name is required.');
        return;
      }
      if (!signUpEmail.trim() || !signUpEmail.trim().includes('@')) {
        setErrorMsg('Please enter a valid email address.');
        return;
      }
      if (!signUpPassword || signUpPassword.length < 6) {
        setErrorMsg('Password must be at least 6 characters.');
        return;
      }
    }

    setLoading(true);
    try {
      if (activeTab === 'signin') {
        await login({
          email: signInEmail.trim(),
          password: signInPassword,
        });
      } else {
        await signup({
          fullName: signUpName.trim(),
          email: signUpEmail.trim(),
          phone: signUpPhone.trim() || undefined,
          password: signUpPassword,
          paypalEmail: signUpPayPalEmail.trim() || undefined,
          currency,
        });
      }
      navigate('/app/shop', { replace: true });
    } catch (err: unknown) {
      setErrorMsg(toApiError(err).message);
    } finally {
      setLoading(false);
    }
  };

  const inputClass =
    'w-full min-h-[48px] px-4 bg-white border border-brand-border rounded-xl text-sm text-brand-charcoal placeholder:text-brand-charcoal-muted/60 focus:outline-none focus:ring-2 focus:ring-brand-coral/25 focus:border-brand-coral transition-colors';

  return (
    <div ref={pageRef} className="min-h-screen w-full bg-brand-ivory text-brand-charcoal">
      <div className="grid grid-cols-1 lg:grid-cols-[50fr_50fr] lg:min-h-screen w-full">
        {/* Left warm editorial panel */}
        <section className="relative flex flex-col gap-6 lg:gap-8 p-6 sm:p-10 lg:p-12 bg-gradient-to-br from-brand-peach/40 via-brand-peach-soft to-brand-ivory border-b lg:border-b-0 lg:border-r border-brand-border">
          <div className="flex items-center justify-between gap-4">
            <Link to="/" className="flex items-center gap-3 group" aria-label="BuyBound home">
              <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-brand-coral to-brand-coral-hover flex items-center justify-center text-white shadow-sm">
                <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4Z" />
                  <path d="M3 6h18" />
                  <path d="M16 10a4 4 0 0 1-8 0" />
                </svg>
              </div>
              <span className="font-display text-2xl font-bold tracking-tight text-brand-charcoal">
                BuyBound<span className="text-brand-coral">.</span>
              </span>
            </Link>

            <Link
              to="/"
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-full bg-white/85 border border-brand-border text-xs font-semibold text-brand-charcoal-muted hover:text-brand-charcoal"
            >
              <span>← Back to Home</span>
            </Link>
          </div>

          <div className="flex-1 flex flex-col justify-center gap-8 w-full max-w-[600px] mx-auto">
            <div data-auth-promo-copy className="flex flex-col gap-4">
              <span className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-white/85 border border-brand-coral/25 w-fit shadow-sm">
                <span className="w-2 h-2 rounded-full bg-brand-coral" aria-hidden="true" />
                <span className="font-mono text-[11px] tracking-widest uppercase font-semibold text-brand-coral">
                  Live PayPal Sandbox Integration
                </span>
              </span>

              <h1 className="font-display text-3xl sm:text-4xl lg:text-[42px] font-bold text-brand-charcoal tracking-tight leading-[1.1]">
                Stop missing live price drops.{' '}
                <span className="text-brand-coral">Shop inside your rules.</span>
              </h1>

              <p className="text-base sm:text-lg text-brand-charcoal-muted max-w-[520px] leading-relaxed">
                Sign in or create an account with your email and password, or authenticate live via{' '}
                <strong className="text-[#0070BA]">PayPal Sandbox</strong> (`api-m.sandbox.paypal.com`) with strict
                server-side spending guardrails.
              </p>
            </div>

            <div data-auth-promo-card className="hidden lg:block w-full max-w-[520px]">
              <div className="bg-white rounded-2xl p-6 shadow-md border border-brand-border">
                <div className="flex items-center justify-between pb-4 border-b border-brand-border">
                  <div className="flex items-center gap-2.5">
                    <span className="w-2.5 h-2.5 rounded-full bg-emerald-500" aria-hidden="true" />
                    <span className="font-display font-semibold text-sm text-brand-charcoal">
                      Guardian Checkout Protection
                    </span>
                  </div>
                  <span className="text-[11px] font-semibold text-[#0070BA] bg-[#0070BA]/10 px-2.5 py-1 rounded-full">
                    {paypalSdkReady ? 'PayPal SDK Live' : 'Sandbox API Ready'}
                  </span>
                </div>

                <div className="mt-4 flex items-center justify-between bg-brand-ivory rounded-xl p-4 border border-brand-border">
                  <div>
                    <h2 className="font-display font-semibold text-base text-brand-charcoal">
                      Server-Verified PayPal Orders
                    </h2>
                    <p className="text-xs text-brand-charcoal-muted mt-0.5">
                      OAuth 2.0 · Vault v3 · Live Orders v2 Capture
                    </p>
                  </div>
                  <span className="font-mono text-xs font-bold text-emerald-700 bg-emerald-50 border border-emerald-200 px-2.5 py-1 rounded-full">
                    No Stubs
                  </span>
                </div>
              </div>
            </div>
          </div>

          <p className="text-xs font-medium text-brand-charcoal-muted">Your rules. Your limits. Your control.</p>
        </section>

        {/* Right auth card */}
        <section className="relative flex items-center justify-center p-6 sm:p-10 lg:p-12 bg-brand-ivory">
          <div data-auth-form className="w-full max-w-[480px] bg-white rounded-2xl p-6 sm:p-8 shadow-md border border-brand-border">
            <div className="relative bg-brand-peach-soft p-1 rounded-full flex items-center mb-6 border border-brand-peach">
              <button
                type="button"
                onClick={() => {
                  setActiveTab('signin');
                  setErrorMsg(null);
                  navigate('/login', { replace: true });
                }}
                aria-pressed={activeTab === 'signin'}
                className={`flex-1 py-2.5 px-4 rounded-full text-sm font-semibold transition-colors ${
                  activeTab === 'signin'
                    ? 'bg-brand-coral text-white shadow-sm'
                    : 'text-brand-charcoal-muted hover:text-brand-charcoal'
                }`}
              >
                Sign In
              </button>
              <button
                type="button"
                onClick={() => {
                  setActiveTab('signup');
                  setErrorMsg(null);
                  navigate('/signup', { replace: true });
                }}
                aria-pressed={activeTab === 'signup'}
                className={`flex-1 py-2.5 px-4 rounded-full text-sm font-semibold transition-colors ${
                  activeTab === 'signup'
                    ? 'bg-brand-coral text-white shadow-sm'
                    : 'text-brand-charcoal-muted hover:text-brand-charcoal'
                }`}
              >
                Create Account
              </button>
            </div>

            <div className="mb-3 inline-flex items-center gap-2 rounded-full border border-[#0070BA]/25 bg-[#0070BA]/10 px-3 py-1">
              <span className="h-2 w-2 rounded-full bg-[#0070BA]" aria-hidden="true" />
              <span className="text-[11px] font-bold uppercase tracking-wider text-[#0070BA]">
                PayPal Sandbox Authentication
              </span>
            </div>

            <h2 id={formHeadingId} className="font-display text-2xl sm:text-3xl font-bold text-brand-charcoal tracking-tight">
              {activeTab === 'signin' ? 'Sign in to BuyBound.' : 'Create your account.'}
            </h2>
            <p className="text-sm text-brand-charcoal-muted mt-1.5 mb-5">
              {activeTab === 'signin'
                ? 'Sign in with your registered email and password, or continue directly with your live PayPal Sandbox account.'
                : 'Register with your name, email, and password to manage price watches, PayPal connections, and verified orders.'}
            </p>

            {errorMsg && (
              <div
                role="alert"
                aria-live="assertive"
                className="mb-4 rounded-xl border border-danger/30 bg-danger-bg px-3.5 py-2.5 text-xs font-medium text-danger"
              >
                {errorMsg}
              </div>
            )}

            <button
              type="button"
              disabled={loading || (!paypalConfigured && !oauthAuthorizeUrl)}
              onClick={() => void handlePayPalOAuthLogin()}
              className="mb-4 flex w-full min-h-[46px] items-center justify-center gap-2 rounded-full bg-[#0070BA] hover:bg-[#005EA6] disabled:opacity-50 px-5 text-sm font-semibold text-white shadow-sm transition-colors"
            >
              <span>Continue with PayPal Sandbox</span>
              <span aria-hidden="true">↗</span>
            </button>

            <div className="relative my-4 flex items-center">
              <div className="flex-grow border-t border-brand-border" />
              <span className="mx-3 shrink text-[11px] font-semibold uppercase tracking-wider text-brand-charcoal-muted">
                or use BuyBound credentials
              </span>
              <div className="flex-grow border-t border-brand-border" />
            </div>

            <form
              method="POST"
              action={activeTab === 'signin' ? '/api/user/login' : '/api/user/signup'}
              aria-labelledby={formHeadingId}
              noValidate
              onSubmit={(e) => void handleSubmit(e)}
              className="space-y-4"
            >
              {activeTab === 'signup' && (
                <>
                  <div>
                    <label className="block text-xs font-semibold text-brand-charcoal mb-1.5" htmlFor="signup-name">
                      Full Name <span aria-hidden="true" className="text-brand-coral">*</span>
                    </label>
                    <input
                      id="signup-name"
                      name="fullName"
                      type="text"
                      required
                      autoComplete="name"
                      enterKeyHint="next"
                      value={signUpName}
                      onChange={(e) => setSignUpName(e.target.value)}
                      placeholder="Your full name"
                      className={inputClass}
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-brand-charcoal mb-1.5" htmlFor="signup-phone">
                      Phone Number <span className="text-brand-charcoal-muted font-normal">(optional)</span>
                    </label>
                    <input
                      id="signup-phone"
                      name="phone"
                      type="tel"
                      autoComplete="tel"
                      enterKeyHint="next"
                      value={signUpPhone}
                      onChange={(e) => setSignUpPhone(e.target.value)}
                      placeholder="+1 (555) 000-0000"
                      className={inputClass}
                    />
                  </div>
                </>
              )}

              <div>
                <label className="block text-xs font-semibold text-brand-charcoal mb-1.5" htmlFor="auth-email-field">
                  Email Address <span aria-hidden="true" className="text-brand-coral">*</span>
                </label>
                <input
                  id="auth-email-field"
                  name="email"
                  type="email"
                  required
                  spellCheck={false}
                  autoComplete="username"
                  enterKeyHint="next"
                  value={activeTab === 'signin' ? signInEmail : signUpEmail}
                  onChange={(e) => {
                    if (errorMsg) setErrorMsg(null);
                    if (activeTab === 'signin') setSignInEmail(e.target.value);
                    else setSignUpEmail(e.target.value);
                  }}
                  placeholder="you@example.com"
                  className={inputClass}
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-brand-charcoal mb-1.5" htmlFor="auth-password-field">
                  Password <span aria-hidden="true" className="text-brand-coral">*</span>
                </label>
                <div className="relative">
                  <input
                    id="auth-password-field"
                    name="password"
                    type={(activeTab === 'signin' ? showSignInPassword : showSignUpPassword) ? 'text' : 'password'}
                    required
                    minLength={activeTab === 'signup' ? 6 : undefined}
                    autoComplete={activeTab === 'signin' ? 'current-password' : 'new-password'}
                    enterKeyHint={activeTab === 'signup' ? 'next' : 'done'}
                    aria-describedby={activeTab === 'signup' ? passwordHelpId : undefined}
                    value={activeTab === 'signin' ? signInPassword : signUpPassword}
                    onChange={(e) => {
                      if (errorMsg) setErrorMsg(null);
                      if (activeTab === 'signin') setSignInPassword(e.target.value);
                      else setSignUpPassword(e.target.value);
                    }}
                    placeholder="Enter password"
                    className={`${inputClass} pr-16`}
                  />
                  <button
                    type="button"
                    aria-pressed={activeTab === 'signin' ? showSignInPassword : showSignUpPassword}
                    aria-label={
                      (activeTab === 'signin' ? showSignInPassword : showSignUpPassword)
                        ? 'Hide password'
                        : 'Show password'
                    }
                    onClick={() =>
                      activeTab === 'signin'
                        ? setShowSignInPassword((v) => !v)
                        : setShowSignUpPassword((v) => !v)
                    }
                    className="absolute right-3 top-1/2 -translate-y-1/2 min-h-[36px] px-2 text-xs font-semibold text-brand-charcoal-muted hover:text-brand-charcoal"
                  >
                    {(activeTab === 'signin' ? showSignInPassword : showSignUpPassword) ? 'Hide' : 'Show'}
                  </button>
                </div>
                {activeTab === 'signup' && (
                  <p id={passwordHelpId} className="mt-1 text-[11px] text-brand-charcoal-muted">
                    Use at least 6 characters. Passwords are BCrypt salted and hashed in PostgreSQL.
                  </p>
                )}
              </div>

              {activeTab === 'signup' && (
                <>
                  <div>
                    <label className="block text-xs font-semibold text-brand-charcoal mb-1.5" htmlFor="signup-paypal-email">
                      PayPal Sandbox Email <span className="text-brand-charcoal-muted font-normal">(optional)</span>
                    </label>
                    <input
                      id="signup-paypal-email"
                      name="paypalEmail"
                      type="email"
                      spellCheck={false}
                      autoComplete="email"
                      enterKeyHint="done"
                      value={signUpPayPalEmail}
                      onChange={(e) => setSignUpPayPalEmail(e.target.value)}
                      placeholder="your-sandbox-buyer@personal.example.com"
                      className={inputClass}
                    />
                  </div>

                  <div>
                    <span className="block text-xs font-semibold text-brand-charcoal mb-1.5">Default currency</span>
                    <div className="grid grid-cols-3 gap-2">
                      {(['USD', 'EUR', 'GBP'] as const).map((curr) => (
                        <button
                          key={curr}
                          type="button"
                          onClick={() => setCurrency(curr)}
                          aria-pressed={currency === curr}
                          className={`h-10 rounded-xl text-xs font-semibold transition-colors ${
                            currency === curr
                              ? 'bg-brand-coral text-white'
                              : 'bg-brand-peach-soft text-brand-charcoal border border-brand-border'
                          }`}
                        >
                          {curr}
                        </button>
                      ))}
                    </div>
                  </div>

                  <div className="flex items-start gap-2.5 pt-1">
                    <input
                      id="signup-terms"
                      name="agreeTerms"
                      type="checkbox"
                      checked={agreeTerms}
                      onChange={(e) => setAgreeTerms(e.target.checked)}
                      className="mt-0.5 h-4 w-4 accent-brand-coral"
                      required
                    />
                    <label htmlFor="signup-terms" className="text-xs text-brand-charcoal-muted">
                      I accept BuyBound&apos;s automated price-drop &amp; spending limit rules.
                    </label>
                  </div>
                </>
              )}

              <button
                type="submit"
                disabled={loading}
                className="w-full min-h-[48px] rounded-full bg-brand-coral hover:bg-brand-coral-hover disabled:opacity-60 text-white text-sm font-semibold shadow-md transition-colors"
              >
                {loading
                  ? activeTab === 'signin'
                    ? 'Signing In…'
                    : 'Creating Account…'
                  : activeTab === 'signin'
                    ? 'Sign In to BuyBound'
                    : 'Create Account'}
              </button>
            </form>
          </div>
        </section>
      </div>
    </div>
  );
}
