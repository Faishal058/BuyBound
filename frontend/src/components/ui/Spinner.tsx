interface SpinnerProps {
  label?: string;
  className?: string;
}

export function Spinner({ label = 'Loading…', className = '' }: SpinnerProps) {
  return (
    <div className={`flex items-center justify-center gap-3 py-10 ${className}`} role="status">
      <span
        aria-hidden="true"
        className="inline-block h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent"
      />
      <span className="text-sm text-muted">{label}</span>
    </div>
  );
}
