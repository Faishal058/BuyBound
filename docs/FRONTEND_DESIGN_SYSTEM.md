# Frontend Design System

**Source of truth:** `frontend/src/styles/design-tokens.css`. This document
describes it; the CSS file defines it. No wireframes exist — the system was
built directly in code from these tokens.

## Palette

| Token | Value | Use |
|---|---|---|
| `--color-primary` | `#f16b50` | Primary actions, links, active nav (Coral Orange) |
| `--color-primary-dark` | `#e0553a` | Primary hover |
| `--color-primary-light` | `#fbe5db` | Soft peach tinted backgrounds & badges |
| `--color-accent` | `#e26348` | Warm secondary highlights |
| `--color-success` / `-bg` | `#0e7044` / `#e8f7ee` | ALLOW, CAPTURED, healthy states |
| `--color-warning` / `-bg` | `#b54708` / `#fff4e5` | HOLD, AWAITING_APPROVAL, degraded |
| `--color-danger` / `-bg` | `#c02619` / `#feeceb` | BLOCK, FAILED, destructive actions |
| `--color-info` / `-bg` | `#d95a40` / `#fdf2ec` | Neutral peach informational badges |
| `--color-background` | `#faf7f2` | Warm ivory page background |
| `--color-surface` | `#ffffff` | Elevated cards, dialogs, table headers |
| `--color-surface-muted` | `#f5efeb` | Warm zebra rows, wells |
| `--color-border` / `-strong` | `#eae3db` / `#d8cfc5` | Warm gray dividers, inputs |
| `--color-text` / `-muted` / `-faint` | `#292724` / `#66615b` / `#8c857d` | Deep charcoal text hierarchy |

Text on semantic backgrounds always pairs the strong color on the `-bg` tint
(e.g. `#c02619` on `#feeceb`) for readable contrast.

## Typography

- Sans: Plus Jakarta Sans / Inter with system fallbacks (`--font-sans`).
- Display: Space Grotesk for headings (`--font-display`).
- Mono: system mono stack for hashes, IDs, timestamps (`--font-mono`).
- Hierarchy: page titles `text-2xl font-bold font-display`, card titles via `Card`, body
  default, captions `text-sm text-muted`.

## Shape, spacing, shadow

- Radius: `--radius-sm: 8px` (inputs, chips), `--radius: 14px` (cards, buttons),
  `--radius-lg: 20px` (dialogs, hero cards).
- Spacing: Tailwind default scale; page container max-width with consistent
  padding via `PageContainer`/`AppLayout`.
- Shadows: `--shadow-sm` (cards), `--shadow-md` (dialogs, popovers), `custom-shadow`.

## Components

Shared in `frontend/src/components/ui/`: `Button` (primary/secondary/danger/
ghost, disabled with reason), `Card`, `Badge` (semantic color mapping used for
Guardian decisions and intent statuses), `Dialog` (confirm for pause/revoke),
`TextField`, `SelectField`, `Spinner`, `EmptyState`, `ErrorBanner` (with retry).
Data grids: AG Grid Community on the Dashboard watch table and the Audit Log.

## Decision/status color mapping

| State | Color |
|---|---|
| ALLOW / ACTIVE / CAPTURED / UP | success |
| HOLD / AWAITING_APPROVAL / PAUSED | warning |
| BLOCK / FAILED / REVOKED | danger |
| Pending / transitional states | info |

## Accessibility & responsive

- All interactive elements are keyboard-focusable with visible focus rings;
  dialogs trap focus and close on Escape.
- Form fields are labelled; validation errors are associated with inputs.
- Layout collapses to a single column below the `md` breakpoint; tables scroll
  horizontally rather than clipping.
- No information is conveyed by color alone — badges always carry text labels.

## What is not here

No dark mode, no marketing pages, no custom illustration set. The identity is
deliberately restrained: a professional fintech tool, not a consumer brand.
