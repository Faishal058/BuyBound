import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';

vi.hoisted(() => {
  if (typeof window !== 'undefined' && !window.matchMedia) {
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: (query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => {},
        removeListener: () => {},
        addEventListener: () => {},
        removeEventListener: () => {},
        dispatchEvent: () => false,
      }),
    });
  }
});

import { AuthProvider } from '../../../context/AuthContext';
import { AppRoutes } from '../../../app/router';
import { Header } from '../../../components/layout/Header';
import { AuthPage } from '../../auth/pages/AuthPage';
import { LandingPage } from '../../landing/pages/LandingPage';
import { ProfilePage } from '../pages/ProfilePage';
import { PayPalVaultModal } from '../../../components/modals/PayPalVaultModal';
import { mandatesApi, paypalApi, userApi } from '../../../services/api/endpoints';
import type { ActiveMandateTelemetry, UserProfile } from '../../../services/api/types';

vi.mock('../../../services/api/endpoints', async () => {
  const actual = await vi.importActual<typeof import('../../../services/api/endpoints')>(
    '../../../services/api/endpoints',
  );
  return {
    ...actual,
    mandatesApi: {
      ...actual.mandatesApi,
      active: vi.fn(),
    },
    userApi: {
      ...actual.userApi,
      getProfile: vi.fn(),
      login: vi.fn(),
      signup: vi.fn(),
      updateProfile: vi.fn(),
      resetWallet: vi.fn(),
      disconnectPayPal: vi.fn(),
      logout: vi.fn(),
    },
    paypalApi: {
      ...actual.paypalApi,
      config: vi.fn(),
      oauthCallback: vi.fn(),
    },
  };
});

function sampleTelemetry(paypalConnected = false): ActiveMandateTelemetry {
  return {
    mandateId: 'm-1',
    watchId: 'w-1',
    status: 'ACTIVE',
    currency: 'USD',
    maxWeeklySpend: '500.00',
    weeklySpent: '0.00',
    remainingBudget: '500.00',
    activeWatchesCount: 3,
    totalWatchesCount: 3,
    paypalConnected,
    paypalPayerEmail: paypalConnected ? 'realbuyer@example.com' : undefined,
    paypalPayerId: paypalConnected ? 'PAYER-LIVE-123' : undefined,
    totalVerifiedSavings: '0.00',
    blockedThreatsCount: 0,
    heldApprovalsCount: 0,
    capturedCount: 0,
    totalCommissionEarned: '0.00',
    maxUnitPrice: '500.00',
    allowedMerchants: ['bestbuy.com', 'target.com'],
    paypalPayerName: paypalConnected ? 'Alex Rivera' : undefined,
  };
}

function sampleProfile(loggedIn: boolean): UserProfile {
  return {
    id: loggedIn ? 'u-1' : null,
    fullName: loggedIn ? 'Alex Rivera' : null,
    email: loggedIn ? 'alex@example.com' : null,
    phone: loggedIn ? '+1 415 555 0100' : null,
    shippingStreet: '742 Evergreen Way',
    shippingCity: 'San Francisco',
    shippingState: 'CA',
    shippingPostalCode: '94107',
    shippingCountry: 'United States',
    notifyPriceDrops: true,
    notifyHeldSms: true,
    notifyReceiptEmails: true,
    paypalEmail: loggedIn ? 'realbuyer@example.com' : null,
    paypalPayerId: loggedIn ? 'PAYER-LIVE-123' : null,
    loggedIn,
    paypalConnected: loggedIn,
    paypalBalance: null,
    weeklyLimit: '500.00',
    maxItemPrice: '500.00',
    spentThisWeek: '0.00',
    remainingWeeklyBudget: '500.00',
    currency: 'USD',
  };
}

describe('Header & ProfilePage authentication states', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.localStorage.clear();
    if (!HTMLDialogElement.prototype.showModal) {
      HTMLDialogElement.prototype.showModal = function showModal() {
        this.setAttribute('open', '');
      };
    }
    if (!HTMLDialogElement.prototype.close) {
      HTMLDialogElement.prototype.close = function close() {
        this.removeAttribute('open');
      };
    }
    if (!window.matchMedia) {
      Object.defineProperty(window, 'matchMedia', {
        writable: true,
        value: vi.fn().mockImplementation((query: string) => ({
          matches: false,
          media: query,
          onchange: null,
          addListener: vi.fn(),
          removeListener: vi.fn(),
          addEventListener: vi.fn(),
          removeEventListener: vi.fn(),
          dispatchEvent: vi.fn(),
        })),
      });
    }
    vi.mocked(mandatesApi.active).mockResolvedValue(sampleTelemetry(false));
    vi.mocked(paypalApi.config).mockResolvedValue({
      clientId: 'test-client-id',
      mode: 'sandbox',
      currency: 'USD',
      configured: true,
      oauthAuthorizeUrl: 'https://www.sandbox.paypal.com/signin/authorize?client_id=test-client-id',
    });
  });

  it('keeps unified inline navigation without Log In, Sign Up, or demo budget pill when logged out', async () => {
    vi.mocked(userApi.getProfile).mockResolvedValue(sampleProfile(false));

    render(
      <MemoryRouter>
        <AuthProvider>
          <Header />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByRole('link', { name: 'Shop Products' })).toBeInTheDocument();
      expect(screen.getByRole('link', { name: 'Wallet & Safety' })).toBeInTheDocument();
    });

    expect(screen.queryByRole('link', { name: 'Log In' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Sign Up' })).not.toBeInTheDocument();
    expect(screen.queryByText(/Weekly budget:/i)).not.toBeInTheDocument();
  });

  it('removes Log In and Sign Up from LandingPage navbar and routes Start Shopping to /login when logged out', async () => {
    vi.mocked(userApi.getProfile).mockResolvedValue(sampleProfile(false));

    render(
      <MemoryRouter>
        <AuthProvider>
          <LandingPage />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => {
      const ctas = screen.getAllByRole('link', { name: 'Start Shopping' });
      expect(ctas.length).toBeGreaterThan(0);
      for (const cta of ctas) {
        expect(cta).toHaveAttribute('href', '/login');
      }
    });

    expect(screen.queryByRole('link', { name: /^Log In$/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /^Sign Up$/i })).not.toBeInTheDocument();
  });

  it('shows PayPal connection status and User Profile dropdown menu with Log Out when authenticated', async () => {
    const loggedInProfile = sampleProfile(true);
    vi.mocked(userApi.getProfile).mockResolvedValue(loggedInProfile);
    vi.mocked(userApi.logout).mockResolvedValue(sampleProfile(false));
    vi.mocked(mandatesApi.active).mockResolvedValue(sampleTelemetry(true));

    render(
      <MemoryRouter>
        <AuthProvider>
          <Header />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText('Connected')).toBeInTheDocument();
      expect(screen.getByText('Alex')).toBeInTheDocument();
    });

    const profileTrigger = screen.getByRole('button', { name: /Alex/i });
    fireEvent.click(profileTrigger);

    expect(screen.getByText('Alex Rivera')).toBeInTheDocument();
    expect(screen.getByText('My Profile & Account')).toBeInTheDocument();

    fireEvent.click(screen.getByText('Log Out'));
    await waitFor(() => {
      expect(userApi.logout).toHaveBeenCalledTimes(1);
    });
  });

  it('allows updating personal profile fields and resetting weekly spend reservations', async () => {
    const loggedInProfile = sampleProfile(true);
    vi.mocked(userApi.getProfile).mockResolvedValue(loggedInProfile);
    vi.mocked(userApi.updateProfile).mockResolvedValue({
      ...loggedInProfile,
      shippingCity: 'Seattle',
    });
    vi.mocked(userApi.resetWallet).mockResolvedValue(loggedInProfile);

    render(
      <MemoryRouter>
        <AuthProvider>
          <ProfilePage />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByLabelText(/Full Name/i)).toHaveValue('Alex Rivera');
    });

    fireEvent.change(screen.getByLabelText(/City/i), { target: { value: 'Seattle' } });
    fireEvent.click(screen.getByRole('button', { name: /Save Changes/i }));

    await waitFor(() => {
      expect(userApi.updateProfile).toHaveBeenCalledWith(
        expect.objectContaining({
          fullName: 'Alex Rivera',
          shippingCity: 'Seattle',
        }),
      );
    });

    fireEvent.click(screen.getByRole('button', { name: /Reset Weekly Spend Reservations/i }));
    await waitFor(() => {
      expect(userApi.resetWallet).toHaveBeenCalledTimes(1);
    });
  });

  it('requires password on AuthPage sign-in and exposes live PayPal Sandbox OAuth login', async () => {
    vi.mocked(userApi.getProfile).mockResolvedValue(sampleProfile(false));
    vi.mocked(userApi.login).mockResolvedValue(sampleProfile(true));

    render(
      <MemoryRouter initialEntries={['/login']}>
        <AuthProvider>
          <AuthPage initialTab="signin" />
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByRole('button', { name: /Continue with PayPal Sandbox/i })).toBeInTheDocument();
    expect(screen.queryByText(/Fill My Sandbox Demo Account/i)).not.toBeInTheDocument();

    const emailInput = await screen.findByLabelText(/Email Address/i);
    fireEvent.change(emailInput, { target: { value: 'alex@example.com' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign In to BuyBound' }));

    expect(await screen.findByText(/Email address and password are required/i)).toBeInTheDocument();
    expect(userApi.login).not.toHaveBeenCalled();

    fireEvent.change(screen.getByPlaceholderText('Enter password'), { target: { value: 'SecretPass99' } });
    fireEvent.click(screen.getByRole('button', { name: 'Sign In to BuyBound' }));

    await waitFor(() => {
      expect(userApi.login).toHaveBeenCalledWith({
        email: 'alex@example.com',
        password: 'SecretPass99',
      });
    });
  });

  it('clears storage and redirects to / on Log Out, and blocks protected routes afterwards', async () => {
    function LocationObserver() {
      const loc = useLocation();
      return <div data-testid="current-path">{loc.pathname}</div>;
    }

    const loggedInProfile = sampleProfile(true);
    window.localStorage.setItem('buybound_auth_user', JSON.stringify(loggedInProfile));
    window.sessionStorage.setItem('demo_session_marker', 'active');
    vi.mocked(userApi.getProfile).mockResolvedValue(loggedInProfile);
    vi.mocked(userApi.logout).mockResolvedValue(sampleProfile(false));

    render(
      <MemoryRouter initialEntries={['/app/watches']}>
        <AuthProvider>
          <LocationObserver />
          <Header />
        </AuthProvider>
      </MemoryRouter>,
    );

    const profileTrigger = await screen.findByRole('button', { name: /Alex/i });
    fireEvent.click(profileTrigger);

    vi.mocked(userApi.getProfile).mockResolvedValue(sampleProfile(false));
    fireEvent.click(screen.getByText('Log Out'));

    await waitFor(() => {
      expect(screen.getByTestId('current-path')).toHaveTextContent('/');
    });
    expect(window.localStorage.getItem('buybound_auth_user')).toBeNull();
    expect(window.sessionStorage.getItem('demo_session_marker')).toBeNull();

    // Navigating to a protected route while logged out redirects to /login
    render(
      <MemoryRouter initialEntries={['/app/watches']}>
        <AuthProvider>
          <LocationObserver />
          <AppRoutes />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getAllByTestId('current-path').some((el) => el.textContent === '/login')).toBe(true);
    });
  });

  it('navigates directly to / on Log Out inside AppRoutes (does not get hijacked to /login)', async () => {
    function LocationObserver() {
      const loc = useLocation();
      return <div data-testid="routes-path">{loc.pathname}</div>;
    }

    const loggedInProfile = sampleProfile(true);
    window.localStorage.setItem('buybound_auth_user', JSON.stringify(loggedInProfile));
    window.sessionStorage.setItem('paypal_temp_session', 'live-123');
    vi.mocked(userApi.getProfile).mockResolvedValue(loggedInProfile);
    vi.mocked(userApi.logout).mockResolvedValue(sampleProfile(false));

    render(
      <MemoryRouter initialEntries={['/app/profile']}>
        <AuthProvider>
          <LocationObserver />
          <AppRoutes />
        </AuthProvider>
      </MemoryRouter>,
    );

    const logoutBtn = await screen.findByRole('button', { name: /Log Out of BuyBound/i });
    vi.mocked(userApi.getProfile).mockResolvedValue(sampleProfile(false));
    fireEvent.click(logoutBtn);

    await waitFor(() => {
      expect(screen.getByTestId('routes-path')).toHaveTextContent('/');
    });
    expect(window.localStorage.getItem('buybound_auth_user')).toBeNull();
    expect(window.sessionStorage.getItem('paypal_temp_session')).toBeNull();
  });

  it('renders real-time PayPal Sandbox OAuth and Vault setup options in PayPalVaultModal without mock accounts', async () => {
    render(
      <PayPalVaultModal
        open
        mandateId="m-1"
        connected={false}
        onClose={() => {}}
        onConnected={() => {}}
      />,
    );

    expect(await screen.findByRole('button', { name: /Continue with PayPal Sandbox/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Setup Auto-Buy Vault Token/i })).toBeInTheDocument();
    expect(screen.queryByText(/Fill My Sandbox Demo Account/i)).not.toBeInTheDocument();
  });

  it('purges sensitive PII from localStorage and only stores minimal session display metadata', async () => {
    const profileWithPii = sampleProfile(true);
    vi.mocked(userApi.getProfile).mockResolvedValue(profileWithPii);

    render(
      <MemoryRouter initialEntries={['/app/profile']}>
        <AuthProvider>
          <ProfilePage />
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(window.localStorage.getItem('buybound_auth_user')).not.toBeNull();
    });
    const stored = JSON.parse(window.localStorage.getItem('buybound_auth_user') ?? '{}') as Record<string, unknown>;
    expect(stored.fullName).toBe('Alex Rivera');
    expect(stored.email).toBe('alex@example.com');
    expect(stored.phone).toBeUndefined();
    expect(stored.shippingStreet).toBeUndefined();
    expect(stored.shippingPostalCode).toBeUndefined();
    expect(stored.paypalPayerId).toBeUndefined();
  });
});
