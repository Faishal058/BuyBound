import type { BadgeTone } from '../../components/ui/Badge';
import type { IntentStatus, ReasonCode, WatchStatus } from '../../services/api/types';

/** Human-readable labels and badge tones for purchase statuses. */
export const INTENT_STATUS_META: Record<IntentStatus, { label: string; tone: BadgeTone }> = {
  CREATED: { label: 'Created', tone: 'neutral' },
  OFFER_VALIDATED: { label: 'Deal checked', tone: 'info' },
  GUARDIAN_EVALUATED: { label: 'Rules checked', tone: 'info' },
  AWAITING_APPROVAL: { label: 'Awaiting approval', tone: 'warning' },
  HELD_FOR_APPROVAL: { label: 'On Hold — Needs approval', tone: 'warning' },
  AWAITING_PAYPAL_SETTLEMENT: { label: 'Agent Locked — Pay with PayPal', tone: 'warning' },
  APPROVED: { label: 'PayPal Approved', tone: 'info' },
  SPEND_RESERVED: { label: 'Spend reserved', tone: 'info' },
  ORDER_CREATE_PENDING: { label: 'Starting PayPal checkout', tone: 'info' },
  ORDER_CREATED: { label: 'Waiting for PayPal confirm', tone: 'info' },
  CAPTURE_PENDING: { label: 'Completing payment', tone: 'info' },
  CAPTURED: { label: 'Captured', tone: 'success' },
  SUCCEEDED: { label: 'Completed', tone: 'success' },
  FAILED: { label: 'Payment failed', tone: 'danger' },
  RECONCILIATION_REQUIRED: { label: 'Needs reconciliation', tone: 'warning' },
  CANCELLED: { label: 'Cancelled', tone: 'neutral' },
  REJECTED_BY_USER: { label: 'Declined by you', tone: 'neutral' },
  REFUND_PENDING: { label: 'Refund in progress', tone: 'warning' },
  REFUNDED: { label: 'Refunded', tone: 'success' },
  EXPIRED: { label: 'Expired', tone: 'warning' },
  BLOCKED: { label: 'Blocked', tone: 'danger' },
};

export const WATCH_STATUS_META: Record<WatchStatus, { label: string; tone: BadgeTone }> = {
  ACTIVE: { label: 'Watching', tone: 'success' },
  PAUSED: { label: 'Paused', tone: 'warning' },
  REVOKED: { label: 'Paused', tone: 'neutral' },
};

/** Plain-English explanations for every safety check reason code. */
export const REASON_CODE_EXPLANATIONS: Record<ReasonCode, string> = {
  WITHIN_POLICY: 'Passed all spending limits and store safety checks.',
  ALL_CHECKS_PASSED: 'Passed every spending limit, store allowlist, and scam check.',
  BELOW_TARGET_PRICE: 'Price is at or below your target buy price.',
  PRICE_ABOVE_MAX_TOTAL: 'Total price is above your per-item limit.',
  PRICE_EXCEEDS_MANDATE_LIMIT: 'Price is higher than your approved spending limit.',
  UNIT_PRICE_ABOVE_CAP: 'Per-item price is higher than your maximum limit.',
  MERCHANT_NOT_ALLOWLISTED: 'This store is not on your list of trusted stores.',
  QUANTITY_MISMATCH: 'Item quantity does not match what you requested.',
  CURRENCY_MISMATCH: 'Store currency does not match your PayPal limit currency.',
  STALE_OFFER: 'Store price quote expired before checkout could complete.',
  INCOMPLETE_EVIDENCE: 'Required listing details were missing, so checkout stayed paused.',
  MANDATE_REVOKED: 'Automated buying is currently paused by your safety switch.',
  MANDATE_EXPIRED: 'Your PayPal spending approval has expired.',
  BUDGET_EXHAUSTED: 'Not enough remaining weekly budget for this purchase.',
  WEEKLY_BUDGET_EXCEEDED: 'Your weekly spending limit has been reached.',
  VELOCITY_EXCEEDED: 'Daily purchase limit reached for this item.',
  APPROVAL_REQUIRED: 'Requires your approval before PayPal charges anything.',
  POLICY_VERSION_MISMATCH: 'Your spending rules changed since this deal was found.',
  SERVICE_ERROR: 'Price verification service could not confirm this listing safely.',
  PRODUCT_IDENTITY_UNCERTAIN: 'Could not confirm this is the exact product you want.',
  NEW_MERCHANT_HOLD: 'Unfamiliar store — paused so you can review before paying.',
  UNAPPROVED_MERCHANT_HOLD: 'Store is not in your trusted stores list — waiting for your OK.',
  PRICE_DRIFT_DETECTED: 'Store changed the price by more than 2% before checkout.',
  PROMPT_INJECTION_DETECTED: 'Suspicious seller instructions found inside the product listing.',
  SKU_MISMATCH_ACCESSORY_BAIT: 'Listing looks like an accessory, replacement part, or wrong item.',
  SUBSCRIPTION_TRAP_BLOCKED: 'Hidden recurring subscription or membership charge detected.',
  ANOMALOUS_DISCOUNT_HOLD: 'Unusually steep discount (over 55% off retail) paused for your review.',
  NEW_USER_SENSITIVE_HOLD: 'Paused for a quick human confirmation before charging PayPal.',
  ITEM_PRICE_CAP_EXCEEDED: 'Listing total exceeds your configured hard ceiling cap.',
  SELLER_RATING_TOO_LOW: 'Seller rating or verification history is below your minimum trust threshold.',
  SELLER_NEGATIVE_REVIEW_RED_FLAG: 'Seller flagged with verified negative buyer reviews / replica risk.',
  WITHIN_DROP_GUARDRAIL_BAND: 'Offer falls cleanly inside your target & hard ceiling guardrail band.',
};

export function formatDateTime(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

export function formatMoney(amount: string | number, currency = 'USD'): string {
  const n = typeof amount === 'number' ? amount : Number(amount);
  if (!Number.isNaN(n)) {
    return `${currency === 'USD' ? '$' : ''}${n.toFixed(2)}${currency === 'USD' ? '' : ` ${currency}`}`;
  }
  return `${amount} ${currency}`;
}

export function formatUsd(amount: string | number | undefined | null): string {
  if (amount === undefined || amount === null || amount === '') return '$0.00';
  const n = typeof amount === 'number' ? amount : Number(amount);
  if (Number.isNaN(n)) return `$${amount}`;
  return `$${n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export function shortId(id: string): string {
  return id.length > 8 ? `${id.slice(0, 8)}…` : id;
}

export function formatStoreName(domain: string): string {
  const map: Record<string, string> = {
    'bestbuy.com': 'Best Buy',
    'macys.com': "Macy's",
    'target.com': 'Target',
    'walmart.com': 'Walmart',
    'amazon.com': 'Amazon',
    'adorama.com': 'Adorama',
    'bhphotovideo.com': 'B&H Photo',
    'nike.com': 'Nike Official',
    'flightclub.com': 'Flight Club',
    'hyped-kicks-liquidator.net': 'Hyped Kicks Liquidator',
  };
  return map[domain.toLowerCase()] ?? domain;
}
