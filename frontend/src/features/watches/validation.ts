import type { CreateWatchRequest, SubmitOfferRequest } from '../../services/api/types';

const MONEY_PATTERN = /^(0|[1-9][0-9]*)(\.[0-9]{1,2})?$/;
const CURRENCY_PATTERN = /^[A-Z]{3}$/;

export interface WatchFormValues {
  productName: string;
  productId: string;
  sku: string;
  variant: string;
  merchantId: string;
  currency: string;
  targetPrice: string;
  maxUnitPrice: string;
  maxTotal: string;
  maxQuantity: string;
  allowedMerchantIds: string;
  requiresHumanApproval: boolean;
}

export type WatchFormErrors = Partial<Record<keyof WatchFormValues, string>>;

export const EMPTY_WATCH_FORM: WatchFormValues = {
  productName: '',
  productId: '',
  sku: '',
  variant: '',
  merchantId: '',
  currency: '',
  targetPrice: '',
  maxUnitPrice: '',
  maxTotal: '',
  maxQuantity: '1',
  allowedMerchantIds: '',
  requiresHumanApproval: true,
};

function required(value: string): boolean {
  return value.trim().length > 0;
}

export function validateWatchForm(values: WatchFormValues): WatchFormErrors {
  const errors: WatchFormErrors = {};

  if (!required(values.productName)) errors.productName = 'Product name is required.';
  if (!required(values.merchantId)) errors.merchantId = 'Merchant ID is required.';
  if (!required(values.currency)) {
    errors.currency = 'Currency is required.';
  } else if (!CURRENCY_PATTERN.test(values.currency.trim())) {
    errors.currency = 'Currency must be a 3-letter ISO code, e.g. USD.';
  }

  const moneyFields: Array<'targetPrice' | 'maxUnitPrice' | 'maxTotal'> = [
    'targetPrice',
    'maxUnitPrice',
    'maxTotal',
  ];
  const labels: Record<string, string> = {
    targetPrice: 'Target price',
    maxUnitPrice: 'Maximum unit price',
    maxTotal: 'Maximum total',
  };
  for (const field of moneyFields) {
    const value = values[field].trim();
    if (!required(value)) {
      errors[field] = `${labels[field]} is required.`;
    } else if (!MONEY_PATTERN.test(value)) {
      errors[field] = `${labels[field]} must be a decimal amount like 249.99.`;
    }
  }

  const quantity = values.maxQuantity.trim();
  if (!required(quantity)) {
    errors.maxQuantity = 'Maximum quantity is required.';
  } else if (!/^[1-9][0-9]*$/.test(quantity)) {
    errors.maxQuantity = 'Maximum quantity must be a whole number of at least 1.';
  } else if (Number(quantity) > 10) {
    errors.maxQuantity = 'Maximum quantity cannot exceed 10.';
  }

  return errors;
}

export function toCreateWatchRequest(values: WatchFormValues): CreateWatchRequest {
  const trim = (s: string) => s.trim();
  const optional = (s: string): string | undefined => {
    const t = trim(s);
    return t.length > 0 ? t : undefined;
  };
  const merchants = values.allowedMerchantIds
    .split(',')
    .map((m) => m.trim())
    .filter((m) => m.length > 0);

  return {
    productName: trim(values.productName),
    productId: optional(values.productId),
    sku: optional(values.sku),
    variant: optional(values.variant),
    merchantId: trim(values.merchantId),
    currency: trim(values.currency).toUpperCase(),
    targetPrice: trim(values.targetPrice),
    maxUnitPrice: trim(values.maxUnitPrice),
    maxTotal: trim(values.maxTotal),
    maxQuantity: Number(trim(values.maxQuantity)),
    allowedMerchantIds: merchants.length > 0 ? merchants : undefined,
    requiresHumanApproval: values.requiresHumanApproval,
  };
}

export interface OfferFormValues {
  merchantId: string;
  canonicalSku: string;
  variant: string;
  quantity: string;
  currency: string;
  unitPrice: string;
  totalPrice: string;
  source: string;
}

export type OfferFormErrors = Partial<Record<keyof OfferFormValues, string>>;

export const EMPTY_OFFER_FORM: OfferFormValues = {
  merchantId: '',
  canonicalSku: '',
  variant: '',
  quantity: '1',
  currency: '',
  unitPrice: '',
  totalPrice: '',
  source: 'demo-fixture',
};

export function validateOfferForm(values: OfferFormValues): OfferFormErrors {
  const errors: OfferFormErrors = {};
  if (!required(values.merchantId)) errors.merchantId = 'Merchant ID is required.';
  if (!required(values.currency)) {
    errors.currency = 'Currency is required.';
  } else if (!CURRENCY_PATTERN.test(values.currency.trim())) {
    errors.currency = 'Currency must be a 3-letter ISO code, e.g. USD.';
  }
  const quantity = values.quantity.trim();
  if (!required(quantity)) {
    errors.quantity = 'Quantity is required.';
  } else if (!/^[1-9][0-9]*$/.test(quantity)) {
    errors.quantity = 'Quantity must be a whole number of at least 1.';
  }
  for (const field of ['unitPrice', 'totalPrice'] as const) {
    const value = values[field].trim();
    if (!required(value)) {
      errors[field] = 'This price is required.';
    } else if (!MONEY_PATTERN.test(value)) {
      errors[field] = 'Must be a decimal amount like 249.99.';
    }
  }
  return errors;
}

export function toSubmitOfferRequest(values: OfferFormValues): SubmitOfferRequest {
  const trim = (s: string) => s.trim();
  const optional = (s: string): string | undefined => {
    const t = trim(s);
    return t.length > 0 ? t : undefined;
  };
  return {
    merchantId: trim(values.merchantId),
    canonicalSku: optional(values.canonicalSku),
    variant: optional(values.variant),
    quantity: Number(trim(values.quantity)),
    currency: trim(values.currency).toUpperCase(),
    unitPrice: trim(values.unitPrice),
    totalPrice: trim(values.totalPrice),
    source: optional(values.source),
  };
}
