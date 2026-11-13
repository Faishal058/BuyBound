import { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../../context/authState';
import { paypalApi } from '../../../services/api/endpoints';
import { toApiError } from '../../../services/api/client';
import { Spinner } from '../../../components/ui/Spinner';

export function PayPalOAuthCallbackPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { refreshProfile } = useAuth();
  const processedRef = useRef<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [statusText, setStatusText] = useState('Verifying PayPal Sandbox Authentication…');

  useEffect(() => {
    const isCancelled = searchParams.get('paypal_cancelled') === 'true';
    if (isCancelled) {
      sessionStorage.removeItem('buybound_pending_login_setup_token');
      navigate('/login?paypal_cancelled=true', { replace: true });
      return;
    }

    const isVaultLogin = searchParams.get('paypal_vault_login') === 'true';
    const isOAuth = searchParams.get('paypal_oauth') === 'true';
    const oauthCode = searchParams.get('code');
    const oauthError = searchParams.get('error_description') ?? searchParams.get('error');

    if (oauthError) {
      setErrorMessage(`PayPal Sandbox returned: ${oauthError}`);
      return;
    }

    if (isVaultLogin) {
      const setupToken =
        searchParams.get('approval_session_id') ??
        searchParams.get('setup_token') ??
        searchParams.get('token') ??
        sessionStorage.getItem('buybound_pending_login_setup_token');

      if (!setupToken || processedRef.current === setupToken) return;
      processedRef.current = setupToken;

      setStatusText('Linking PayPal Sandbox Vault Payment Token…');

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
          setErrorMessage(toApiError(err).message);
        }
      })();
      return;
    }

    if (isOAuth && oauthCode) {
      if (processedRef.current === oauthCode) return;
      processedRef.current = oauthCode;

      setStatusText('Exchanging PayPal OAuth authorization code…');

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
          setErrorMessage(toApiError(err).message);
        }
      })();
    }
  }, [navigate, refreshProfile, searchParams]);

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-[#FAF9F5] px-4">
      <div className="w-full max-w-md rounded-3xl border border-black/10 bg-white p-8 text-center shadow-lg">
        <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-[#0070BA]/10 text-[#0070BA]">
          <svg className="h-8 w-8" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <path d="M7.076 21.337H2.47a.641.641 0 0 1-.633-.74L4.944 3.72a.789.789 0 0 1 .78-.662h6.844c2.89 0 5.093.684 6.198 1.927 1.05 1.18 1.34 2.809.864 4.84-.712 3.037-2.73 5.15-5.998 5.15h-2.31a.79.79 0 0 0-.78.662l-1.466 5.7z" />
          </svg>
        </div>

        <h1 className="font-display text-xl font-bold text-[#1F1E1B]">
          {errorMessage ? 'Authentication Error' : 'Connecting to PayPal Sandbox'}
        </h1>

        <p className="mt-2 text-sm text-[#6A6860]">
          {errorMessage ? errorMessage : statusText}
        </p>

        {!errorMessage ? (
          <div className="mt-6 flex justify-center">
            <Spinner label={statusText} />
          </div>
        ) : (
          <div className="mt-6">
            <button
              onClick={() => navigate('/login', { replace: true })}
              className="inline-flex min-h-[44px] items-center justify-center rounded-full bg-[#0070BA] px-6 text-sm font-semibold text-white shadow-sm hover:bg-[#005EA6]"
            >
              Back to Sign In
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

