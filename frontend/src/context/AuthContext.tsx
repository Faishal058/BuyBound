import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { AuthContext, type AuthContextValue } from './authState';
import { setApiSessionToken } from '../services/api/client';
import { userApi } from '../services/api/endpoints';
import type {
  LoginRequest,
  SignupRequest,
  UpdateProfileRequest,
  UserProfile,
} from '../services/api/types';

const STORAGE_KEY = 'buybound_auth_user';
const LOGGED_OUT_KEY = 'buybound_logged_out';

/** Minimal non-sensitive session display metadata allowed in localStorage (no PII or PayPal IDs). */
interface MinimalSessionCache {
  id: string | null;
  fullName: string | null;
  email: string | null;
  loggedIn: boolean;
  sessionToken?: string | null;
}

function readCachedUser(): UserProfile | null {
  if (typeof window === 'undefined') return null;
  try {
    if (window.localStorage.getItem(LOGGED_OUT_KEY) === 'true') return null;
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as MinimalSessionCache;
    if (!parsed || !parsed.loggedIn) return null;
    if (parsed.sessionToken) {
      setApiSessionToken(parsed.sessionToken);
    }
    return {
      id: parsed.id ?? null,
      fullName: parsed.fullName ?? null,
      email: parsed.email ?? null,
      phone: null,
      shippingStreet: null,
      shippingCity: null,
      shippingState: null,
      shippingPostalCode: null,
      shippingCountry: 'United States',
      notifyPriceDrops: true,
      notifyHeldSms: true,
      notifyReceiptEmails: true,
      paypalEmail: null,
      paypalPayerId: null,
      loggedIn: true,
      paypalConnected: false,
      paypalBalance: null,
      weeklyLimit: '500.00',
      maxItemPrice: '260.00',
      spentThisWeek: '0.00',
      remainingWeeklyBudget: '500.00',
      currency: 'USD',
      sessionToken: parsed.sessionToken ?? null,
    };
  } catch {
    return null;
  }
}

function clearAuthAndPayPalStorage() {
  if (typeof window === 'undefined') return;
  setApiSessionToken(null);
  try {
    window.localStorage.removeItem(STORAGE_KEY);
    window.localStorage.removeItem(LOGGED_OUT_KEY);
    for (let i = window.localStorage.length - 1; i >= 0; i -= 1) {
      const key = window.localStorage.key(i);
      if (key && (key.startsWith('buybound_') || key.toLowerCase().includes('paypal'))) {
        window.localStorage.removeItem(key);
      }
    }
    window.sessionStorage.clear();
  } catch {
    // Ignore storage quota errors in restricted browser modes.
  }
}

function writeCachedUser(profile: UserProfile | null) {
  if (typeof window === 'undefined') return;
  try {
    if (profile && profile.loggedIn) {
      if (profile.sessionToken) {
        setApiSessionToken(profile.sessionToken);
      }
      const existingRaw = window.localStorage.getItem(STORAGE_KEY);
      let prevToken: string | null | undefined = null;
      if (existingRaw) {
        try {
          prevToken = (JSON.parse(existingRaw) as MinimalSessionCache).sessionToken;
        } catch {
          prevToken = null;
        }
      }
      const minimal: MinimalSessionCache = {
        id: profile.id,
        fullName: profile.fullName,
        email: profile.email,
        loggedIn: true,
        sessionToken: profile.sessionToken ?? prevToken ?? null,
      };
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(minimal));
      window.localStorage.removeItem(LOGGED_OUT_KEY);
    } else {
      clearAuthAndPayPalStorage();
    }
  } catch {
    // Ignore storage quota errors in restricted browser modes.
  }
}

function notifyRefresh() {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('buybound:refresh'));
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(() => readCachedUser());
  const [loading, setLoading] = useState<boolean>(true);

  const syncFromBackend = useCallback(async (): Promise<UserProfile | null> => {
    try {
      const profile = await userApi.getProfile();
      if (profile.loggedIn) {
        setUser(profile);
        writeCachedUser(profile);
        return profile;
      }
      setUser(null);
      writeCachedUser(null);
      return null;
    } catch {
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void syncFromBackend();
    const onRefresh = () => {
      void syncFromBackend();
    };
    window.addEventListener('buybound:refresh', onRefresh);
    return () => window.removeEventListener('buybound:refresh', onRefresh);
  }, [syncFromBackend]);

  const commitSession = useCallback((profile: UserProfile, emitRefresh = true): UserProfile => {
    setUser(profile.loggedIn ? profile : null);
    writeCachedUser(profile.loggedIn ? profile : null);
    if (emitRefresh) notifyRefresh();
    return profile;
  }, []);

  const login = useCallback(
    async (req: LoginRequest) => {
      const profile = await userApi.login(req);
      return commitSession(profile);
    },
    [commitSession],
  );

  const signup = useCallback(
    async (req: SignupRequest) => {
      const profile = await userApi.signup(req);
      return commitSession(profile);
    },
    [commitSession],
  );

  const updateProfile = useCallback(
    async (req: UpdateProfileRequest) => {
      const profile = await userApi.updateProfile(req);
      return commitSession(profile);
    },
    [commitSession],
  );

  const resetWallet = useCallback(async () => {
    const profile = await userApi.resetWallet();
    if (profile.loggedIn) {
      commitSession(profile);
    } else {
      notifyRefresh();
    }
    return profile;
  }, [commitSession]);

  const disconnectPayPal = useCallback(async () => {
    const profile = await userApi.disconnectPayPal();
    return commitSession(profile);
  }, [commitSession]);

  const logout = useCallback(async () => {
    clearAuthAndPayPalStorage();
    try {
      await userApi.logout();
    } finally {
      clearAuthAndPayPalStorage();
      setUser(null);
      notifyRefresh();
    }
  }, []);

  const value: AuthContextValue = {
    user,
    isAuthenticated: Boolean(user?.loggedIn),
    loading,
    refreshProfile: syncFromBackend,
    login,
    signup,
    updateProfile,
    resetWallet,
    disconnectPayPal,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
