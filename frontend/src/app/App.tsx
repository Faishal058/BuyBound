import { BrowserRouter, useNavigate } from 'react-router-dom';
import { useEffect } from 'react';
import { AuthProvider } from '../context/AuthContext';
import { AppRoutes } from './router';

function HashMigration() {
  const navigate = useNavigate();

  useEffect(() => {
    if (window.location.hash && window.location.hash.startsWith('#/')) {
      const cleanPath = window.location.hash.slice(1);
      navigate(cleanPath, { replace: true });
    }
  }, [navigate]);

  return null;
}

/**
 * Application shell using BrowserRouter.
 * Starts from the public landing page (/) and guides users cleanly through
 * /app/watches, /app/offers, /app/purchases, and /app/safety.
 */
export function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <HashMigration />
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  );
}
