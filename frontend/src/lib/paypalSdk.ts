export interface PayPalButtonsComponent {
  render: (container: HTMLElement | string) => Promise<void>;
  close?: () => Promise<void>;
}

export interface PayPalNamespace {
  Buttons?: (options: {
    style?: {
      layout?: 'vertical' | 'horizontal';
      color?: 'gold' | 'blue' | 'silver' | 'white' | 'black';
      shape?: 'rect' | 'pill';
      label?: 'paypal' | 'checkout' | 'buynow' | 'pay';
      height?: number;
    };
    createOrder: () => Promise<string>;
    onApprove: (data: { orderID: string; payerID?: string }) => Promise<void>;
    onCancel?: () => void;
    onError?: (err: unknown) => void;
  }) => PayPalButtonsComponent;
}

declare global {
  interface Window {
    paypal?: PayPalNamespace;
    __buyboundPayPalSdkPromise?: Record<string, Promise<PayPalNamespace>>;
  }
}

/**
 * Dynamically loads the official PayPal JS SDK from paypal.com using the public Sandbox clientId
 * served by GET /api/paypal/config. Never embeds secrets or hardcoded client IDs.
 */
export function loadPayPalSdk(clientId: string, currency = 'USD'): Promise<PayPalNamespace> {
  if (!clientId || !clientId.trim()) {
    return Promise.reject(new Error('PayPal clientId is not configured on the backend.'));
  }

  const key = `${clientId.trim()}:${currency}`;
  if (typeof window !== 'undefined' && window.paypal?.Buttons) {
    return Promise.resolve(window.paypal);
  }

  window.__buyboundPayPalSdkPromise = window.__buyboundPayPalSdkPromise ?? {};
  if (key in window.__buyboundPayPalSdkPromise) {
    return window.__buyboundPayPalSdkPromise[key];
  }

  const promise = new Promise<PayPalNamespace>((resolve, reject) => {
    const existing = document.querySelector<HTMLScriptElement>(
      `script[data-buybound-paypal-sdk="${key}"]`,
    );
    if (existing && window.paypal?.Buttons) {
      resolve(window.paypal);
      return;
    }

    const script = existing ?? document.createElement('script');
    script.src = `https://www.paypal.com/sdk/js?client-id=${encodeURIComponent(clientId.trim())}&currency=${encodeURIComponent(currency)}&intent=capture`;
    script.async = true;
    script.dataset.buyboundPaypalSdk = key;
    script.onload = () => {
      if (window.paypal?.Buttons) {
        resolve(window.paypal);
      } else {
        reject(new Error('PayPal JS SDK loaded without Buttons component.'));
      }
    };
    script.onerror = () => {
      reject(new Error('Could not load the PayPal JS SDK from paypal.com.'));
    };

    if (!existing) {
      document.head.appendChild(script);
    }
  });

  window.__buyboundPayPalSdkPromise[key] = promise;
  return promise;
}

