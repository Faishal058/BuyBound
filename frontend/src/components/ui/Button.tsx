import type { ButtonHTMLAttributes, ReactNode } from 'react';

type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost' | 'paypal';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  loading?: boolean;
  children: ReactNode;
}

const variantClasses: Record<ButtonVariant, string> = {
  primary:
    'bg-primary text-white hover:bg-primary-dark disabled:bg-primary/40 focus-visible:outline-primary shadow-sm hover:shadow-md hover:shadow-primary/20',
  paypal:
    'bg-[#0070BA] text-white hover:bg-[#005EA6] disabled:bg-[#0070BA]/40 focus-visible:outline-[#0070BA] shadow-sm',
  secondary:
    'bg-surface text-ink border border-border-strong hover:bg-surface-muted disabled:text-faint disabled:border-border shadow-sm',
  danger: 'bg-danger text-white hover:brightness-95 disabled:bg-danger/40 shadow-sm focus-visible:outline-danger',
  ghost: 'bg-transparent text-primary hover:bg-primary-light disabled:text-faint',
};

export function Button({
  variant = 'primary',
  loading = false,
  disabled,
  children,
  className = '',
  type = 'button',
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={`inline-flex min-h-[40px] items-center justify-center gap-2 rounded-full px-5 py-2 text-sm font-medium transition-all duration-200 disabled:cursor-not-allowed ${variantClasses[variant]} ${className}`}
      {...rest}
    >
      {loading && (
        <span
          aria-hidden="true"
          className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent"
        />
      )}
      {children}
    </button>
  );
}
