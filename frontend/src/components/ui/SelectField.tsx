import type { ReactNode, SelectHTMLAttributes } from 'react';

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  error?: string;
  hint?: string;
  id: string;
  children: ReactNode;
}

export function SelectField({ label, error, hint, id, children, className = '', ...rest }: SelectFieldProps) {
  return (
    <div className={`flex flex-col gap-1 ${className}`}>
      <label htmlFor={id} className="block text-sm font-medium text-ink">
        {label}
        {rest.required && (
          <span aria-hidden="true" className="text-danger">
            {' '}
            *
          </span>
        )}
      </label>
      {hint && !error && <p className="text-xs text-faint">{hint}</p>}
      <select
        id={id}
        aria-invalid={error ? true : undefined}
        className={`min-h-[44px] w-full rounded-xl border bg-surface px-3.5 py-2 text-sm text-ink focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 transition-all ${
          error ? 'border-danger' : 'border-border-strong hover:border-brand-coral/50'
        }`}
        {...rest}
      >
        {children}
      </select>
      {error && (
        <p role="alert" className="text-xs font-medium text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
