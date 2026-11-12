import { useEffect, useId, useRef, type ReactNode } from 'react';
import { Button } from './Button';

interface DialogProps {
  open: boolean;
  title: string;
  children: ReactNode;
  confirmLabel: string;
  confirmVariant?: 'primary' | 'danger' | 'paypal';
  confirming?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}

/** Accessible native modal <dialog> with focus management, Esc handling, and backdrop light-dismiss. */
export function Dialog({
  open,
  title,
  children,
  confirmLabel,
  confirmVariant = 'primary',
  confirming = false,
  onConfirm,
  onClose,
}: DialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const pressedOutsideRef = useRef(false);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;

    if (open) {
      if (!dialog.open && typeof dialog.showModal === 'function') {
        dialog.showModal();
      }
    } else if (dialog.open && typeof dialog.close === 'function') {
      dialog.close();
    }
  }, [open]);

  if (!open) return null;

  const isOutside = (e: React.PointerEvent<HTMLDialogElement>, dialog: HTMLDialogElement) => {
    const rect = dialog.getBoundingClientRect();
    return (
      e.clientX < rect.left ||
      e.clientX > rect.right ||
      e.clientY < rect.top ||
      e.clientY > rect.bottom
    );
  };

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby={titleId}
      onCancel={(e) => {
        e.preventDefault();
        if (!confirming) onClose();
      }}
      onPointerDown={(e) => {
        if (dialogRef.current) {
          pressedOutsideRef.current = isOutside(e, dialogRef.current);
        }
      }}
      onPointerUp={(e) => {
        if (dialogRef.current && pressedOutsideRef.current && isOutside(e, dialogRef.current) && !confirming) {
          onClose();
        }
        pressedOutsideRef.current = false;
      }}
      className="m-auto w-full max-w-md rounded-2xl border border-border bg-surface p-6 shadow-xl text-ink"
    >
      <h2 id={titleId} className="font-display text-lg font-semibold text-ink">
        {title}
      </h2>
      <div className="mt-3 text-sm text-muted leading-relaxed">{children}</div>
      <div className="mt-6 flex justify-end gap-3">
        <Button variant="secondary" onClick={onClose} disabled={confirming} autoFocus>
          Cancel
        </Button>
        <Button variant={confirmVariant} onClick={onConfirm} loading={confirming}>
          {confirmLabel}
        </Button>
      </div>
    </dialog>
  );
}
