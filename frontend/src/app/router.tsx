import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { AppLayout } from '../components/layout/AppLayout';
import { EmptyState } from '../components/ui/EmptyState';
import { Spinner } from '../components/ui/Spinner';
import { useAuth } from '../context/authState';
import { AuditPage } from '../features/audit/pages/AuditPage';
import { AuthPage } from '../features/auth/pages/AuthPage';
import { IntentDetailPage } from '../features/intents/pages/IntentDetailPage';
import { LandingPage } from '../features/landing/pages/LandingPage';
import { OffersPage } from '../features/offers/pages/OffersPage';
import { ProfilePage } from '../features/profile/pages/ProfilePage';
import { PurchasesPage } from '../features/purchases/pages/PurchasesPage';
import { WalletSafetyPage } from '../features/safety/pages/WalletSafetyPage';
import { AgentSettingsPage } from '../features/agent/pages/AgentSettingsPage';
import { ShopPage } from '../features/shop/pages/ShopPage';
import { NewWatchPage } from '../features/watches/pages/NewWatchPage';
import { PayPalReturnPage, VaultSuccessPage } from '../features/watches/pages/PayPalCallbackPages';
import { WatchDetailPage } from '../features/watches/pages/WatchDetailPage';
import { WatchesPage } from '../features/watches/pages/WatchesPage';

function NotFoundPage() {
  return (
    <EmptyState
      title="Page not found"
      description="The page you are looking for does not exist."
    />
  );
}

/**
 * Enforces authenticated user session across all protected app routes.
 * Redirects unauthenticated visitors and signed-out users back to /login.
 */
function RequireAuth() {
  const { isAuthenticated, loading } = useAuth();
  const location = useLocation();

  if (loading && !isAuthenticated) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <Spinner label="Verifying your BuyBound session…" />
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return <AppLayout />;
}

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/landing" element={<Navigate to="/" replace />} />
      <Route path="/login" element={<AuthPage initialTab="signin" />} />
      <Route path="/signup" element={<AuthPage initialTab="signup" />} />
      <Route path="/auth" element={<AuthPage />} />

      <Route element={<RequireAuth />}>
        {/* Primary consumer fintech workflow */}
        <Route path="/app" element={<Navigate to="/app/watches" replace />} />
        <Route path="/app/shop" element={<ShopPage />} />
        <Route path="/explore" element={<Navigate to="/app/shop" replace />} />
        <Route path="/app/watches" element={<WatchesPage />} />
        <Route path="/app/offers" element={<OffersPage />} />
        <Route path="/app/purchases" element={<PurchasesPage />} />
        <Route path="/app/safety" element={<WalletSafetyPage />} />
        <Route path="/app/agent-settings" element={<AgentSettingsPage />} />
        <Route path="/app/profile" element={<ProfilePage />} />
        <Route path="/profile" element={<Navigate to="/app/profile" replace />} />

        {/* Detail & PayPal callback routes */}
        <Route path="/watches/new" element={<NewWatchPage />} />
        <Route path="/watches/:id" element={<WatchDetailPage />} />
        <Route path="/intents/:id" element={<IntentDetailPage />} />
        <Route path="/audit" element={<AuditPage />} />
        <Route path="/vault/success" element={<VaultSuccessPage />} />
        <Route path="/paypal/return" element={<PayPalReturnPage />} />

        {/* Clean redirects for legacy routes */}
        <Route path="/dashboard" element={<Navigate to="/app/watches" replace />} />
        <Route path="/watches" element={<Navigate to="/app/watches" replace />} />
        <Route path="/offers" element={<Navigate to="/app/offers" replace />} />
        <Route path="/purchases" element={<Navigate to="/app/purchases" replace />} />
        <Route path="/safety" element={<Navigate to="/app/safety" replace />} />
        <Route path="/settings" element={<Navigate to="/app/safety" replace />} />
        <Route path="/guardian" element={<Navigate to="/app/safety" replace />} />
        <Route path="/policies" element={<Navigate to="/app/safety" replace />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
