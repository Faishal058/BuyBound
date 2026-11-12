import type { ReactNode } from 'react';

export type BadgeTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger';

const toneClasses: Record<BadgeTone, string> = {
  neutral: 'bg-surface-muted text-muted border-border',
  info: 'bg-info-bg text-info border-[#0070BA]/25',
  success: 'bg-success-bg text-success border-[#16A34A]/25',
  warning: 'bg-warning-bg text-warning border-[#D97706]/30',
  danger: 'bg-danger-bg text-danger border-[#DC2626]/25',
};

const dotClasses: Record<BadgeTone, string> = {
  neutral: 'bg-faint',
  info: 'bg-[#0070BA]',
  success: 'bg-success-dot',
  warning: 'bg-warning-dot',
  danger: 'bg-danger-dot',
};

interface BadgeProps {
  tone?: BadgeTone;
  dot?: boolean;
  children: ReactNode;
  title?: string;
  className?: string;
}

export function Badge({ tone = 'neutral', dot = false, children, title, className = '' }: BadgeProps) {
  return (
    <span
      title={title}
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium whitespace-nowrap ${toneClasses[tone]} ${className}`}
    >
      {dot && <span aria-hidden="true" className={`h-1.5 w-1.5 rounded-full ${dotClasses[tone]}`} />}
      {children}
    </span>
  );
}
