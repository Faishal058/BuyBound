import { useState, type FormEvent } from 'react';
import { Button } from '../../../components/ui/Button';
import { TextField } from '../../../components/ui/TextField';
import {
  EMPTY_WATCH_FORM,
  toCreateWatchRequest,
  validateWatchForm,
  type WatchFormErrors,
  type WatchFormValues,
} from '../validation';
import type { CreateWatchRequest } from '../../../services/api/types';

interface WatchFormProps {
  initialValues?: WatchFormValues;
  submitting: boolean;
  submitLabel: string;
  onSubmit: (request: CreateWatchRequest) => void;
}

export function WatchForm({
  initialValues = EMPTY_WATCH_FORM,
  submitting,
  submitLabel,
  onSubmit,
}: WatchFormProps) {
  const [values, setValues] = useState<WatchFormValues>(initialValues);
  const [errors, setErrors] = useState<WatchFormErrors>({});
  const [touched, setTouched] = useState(false);

  const set = (field: keyof WatchFormValues) => (value: string | boolean) => {
    setValues((prev) => ({ ...prev, [field]: value }));
    if (touched) setErrors(validateWatchForm({ ...values, [field]: value }));
  };

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    const nextErrors = validateWatchForm(values);
    setErrors(nextErrors);
    setTouched(true);
    if (Object.keys(nextErrors).length === 0) {
      onSubmit(toCreateWatchRequest(values));
    }
  };

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <TextField
          id="productName"
          label="Product name"
          required
          value={values.productName}
          onChange={(e) => set('productName')(e.target.value)}
          error={errors.productName}
          placeholder="Sony WH-1000XM5 headphones"
          className="sm:col-span-2"
        />
        <TextField
          id="productId"
          label="Product ID"
          value={values.productId}
          onChange={(e) => set('productId')(e.target.value)}
          error={errors.productId}
          hint="Catalogue product identifier, if known."
        />
        <TextField
          id="merchantId"
          label="Merchant ID"
          required
          value={values.merchantId}
          onChange={(e) => set('merchantId')(e.target.value)}
          error={errors.merchantId}
          placeholder="merchant-001"
        />
        <TextField
          id="sku"
          label="SKU"
          value={values.sku}
          onChange={(e) => set('sku')(e.target.value)}
          error={errors.sku}
        />
        <TextField
          id="variant"
          label="Variant"
          value={values.variant}
          onChange={(e) => set('variant')(e.target.value)}
          error={errors.variant}
          placeholder="Black"
        />
        <TextField
          id="currency"
          label="Currency"
          required
          value={values.currency}
          onChange={(e) => set('currency')(e.target.value.toUpperCase())}
          error={errors.currency}
          placeholder="USD"
          maxLength={3}
          hint="3-letter ISO code."
        />
        <TextField
          id="targetPrice"
          label="Target price"
          required
          inputMode="decimal"
          value={values.targetPrice}
          onChange={(e) => set('targetPrice')(e.target.value)}
          error={errors.targetPrice}
          placeholder="249.99"
          hint="Buy when the price reaches this."
        />
        <TextField
          id="maxUnitPrice"
          label="Maximum unit price"
          required
          inputMode="decimal"
          value={values.maxUnitPrice}
          onChange={(e) => set('maxUnitPrice')(e.target.value)}
          error={errors.maxUnitPrice}
          placeholder="279.99"
          hint="Hard cap per unit. The Guardian never exceeds it."
        />
        <TextField
          id="maxTotal"
          label="Maximum total"
          required
          inputMode="decimal"
          value={values.maxTotal}
          onChange={(e) => set('maxTotal')(e.target.value)}
          error={errors.maxTotal}
          placeholder="299.99"
        />
        <TextField
          id="maxQuantity"
          label="Maximum quantity"
          required
          inputMode="numeric"
          value={values.maxQuantity}
          onChange={(e) => set('maxQuantity')(e.target.value)}
          error={errors.maxQuantity}
        />
        <TextField
          id="allowedMerchantIds"
          label="Allowed merchant IDs"
          value={values.allowedMerchantIds}
          onChange={(e) => set('allowedMerchantIds')(e.target.value)}
          error={errors.allowedMerchantIds}
          placeholder="merchant-001, merchant-002"
          hint="Comma-separated. Purchases outside this list are blocked."
          className="sm:col-span-2"
        />
      </div>
      <label className="flex items-center gap-2 text-sm text-ink">
        <input
          type="checkbox"
          checked={values.requiresHumanApproval}
          onChange={(e) => set('requiresHumanApproval')(e.target.checked)}
          className="h-4 w-4 accent-[#1663d9]"
        />
        Require human approval (new watches start in HOLD-by-default mode)
      </label>
      <div>
        <Button type="submit" loading={submitting}>
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}
