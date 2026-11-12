import { Button } from './Button';

interface ErrorBannerProps {
  message: string;
  onRetry?: () => void;
  className?: string;
}

/** Non-blocking error display with an optional retry action. */
export function ErrorBanner({ message, onRetry, className = '' }: ErrorBannerProps) {
  return (
    <div
      role="alert"
      className={`flex items-start justify-between gap-4 rounded-xl border border-danger/30 bg-danger-bg px-4 py-3 shadow-sm ${className}`}
    >
      <p className="text-sm font-medium text-danger">{message}</p>
      {onRetry && (
        <Button variant="secondary" onClick={onRetry} className="shrink-0">
          Retry
        </Button>
      )}
    </div>
  );
}
