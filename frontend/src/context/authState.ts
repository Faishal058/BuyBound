import { createContext, useContext } from 'react';
import type {
  LoginRequest,
  SignupRequest,
  UpdateProfileRequest,
  UserProfile,
} from '../services/api/types';

export interface AuthContextValue {
  user: UserProfile | null;
  isAuthenticated: boolean;
  loading: boolean;
  refreshProfile: () => Promise<UserProfile | null>;
  login: (req: LoginRequest) => Promise<UserProfile>;
  signup: (req: SignupRequest) => Promise<UserProfile>;
  updateProfile: (req: UpdateProfileRequest) => Promise<UserProfile>;
  resetWallet: () => Promise<UserProfile>;
  disconnectPayPal: () => Promise<UserProfile>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function formatShippingAddress(profile: UserProfile | null | undefined): string | null {
  if (!profile) return null;
  const parts = [
    profile.shippingStreet,
    profile.shippingCity,
    [profile.shippingState, profile.shippingPostalCode].filter(Boolean).join(' '),
    profile.shippingCountry,
  ]
    .map((p) => (p ?? '').trim())
    .filter(Boolean);
  return parts.length > 0 ? parts.join(', ') : null;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used inside <AuthProvider>.');
  }
  return ctx;
}

export function useOptionalAuth(): AuthContextValue | null {
  return useContext(AuthContext);
}

