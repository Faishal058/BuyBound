import { describe, expect, it } from 'vitest';
import {
  EMPTY_WATCH_FORM,
  toCreateWatchRequest,
  validateWatchForm,
} from '../validation';

describe('validateWatchForm', () => {
  it('requires the mandatory fields', () => {
    const errors = validateWatchForm({ ...EMPTY_WATCH_FORM, currency: '', maxQuantity: '' });

    expect(errors.productName).toBeDefined();
    expect(errors.merchantId).toBeDefined();
    expect(errors.currency).toBeDefined();
    expect(errors.targetPrice).toBeDefined();
    expect(errors.maxUnitPrice).toBeDefined();
    expect(errors.maxTotal).toBeDefined();
    expect(errors.maxQuantity).toBeDefined();
  });

  it('rejects a malformed currency code', () => {
    const errors = validateWatchForm({ ...EMPTY_WATCH_FORM, currency: 'usd1' });
    expect(errors.currency).toMatch(/3-letter/i);
  });

  it('rejects malformed money amounts', () => {
    const errors = validateWatchForm({
      ...EMPTY_WATCH_FORM,
      targetPrice: '12.345',
      maxUnitPrice: 'abc',
      maxTotal: '-5',
    });
    expect(errors.targetPrice).toBeDefined();
    expect(errors.maxUnitPrice).toBeDefined();
    expect(errors.maxTotal).toBeDefined();
  });

  it('rejects out-of-range quantities', () => {
    expect(validateWatchForm({ ...EMPTY_WATCH_FORM, maxQuantity: '0' }).maxQuantity).toBeDefined();
    expect(validateWatchForm({ ...EMPTY_WATCH_FORM, maxQuantity: '11' }).maxQuantity).toBeDefined();
    expect(validateWatchForm({ ...EMPTY_WATCH_FORM, maxQuantity: '2.5' }).maxQuantity).toBeDefined();
  });

  it('accepts a fully valid form', () => {
    const errors = validateWatchForm({
      ...EMPTY_WATCH_FORM,
      productName: 'Sony WH-1000XM5',
      merchantId: 'merchant-001',
      currency: 'USD',
      targetPrice: '249.99',
      maxUnitPrice: '279.99',
      maxTotal: '299.99',
      maxQuantity: '2',
    });
    expect(errors).toEqual({});
  });
});

describe('toCreateWatchRequest', () => {
  it('trims, uppercases currency, and maps optional fields', () => {
    const request = toCreateWatchRequest({
      ...EMPTY_WATCH_FORM,
      productName: '  Sony WH-1000XM5  ',
      merchantId: 'merchant-001',
      currency: 'usd',
      targetPrice: '249.99',
      maxUnitPrice: '279.99',
      maxTotal: '299.99',
      maxQuantity: '2',
      allowedMerchantIds: 'merchant-001, merchant-002 ,',
      requiresHumanApproval: false,
    });

    expect(request).toEqual({
      productName: 'Sony WH-1000XM5',
      productId: undefined,
      sku: undefined,
      variant: undefined,
      merchantId: 'merchant-001',
      currency: 'USD',
      targetPrice: '249.99',
      maxUnitPrice: '279.99',
      maxTotal: '299.99',
      maxQuantity: 2,
      allowedMerchantIds: ['merchant-001', 'merchant-002'],
      requiresHumanApproval: false,
    });
  });
});
