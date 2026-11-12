import { task, type TaskContext } from '@renderinc/sdk/workflows';
import type { SettlementInput, SettlementResult } from '../types/index.js';
import { BackendClient } from '../client/backendClient.js';

export const settlementTask = task(
  {
    name: 'settlementTask',
    retry: {
      maxRetries: 3,
      waitDurationMs: 1500,
      backoffScaling: 1.5,
    },
    timeoutSeconds: 60,
    plan: 'flex',
  },
  async function runSettlement(ctx: TaskContext, input: SettlementInput): Promise<SettlementResult> {
    const { watch, winningOffer, forensics, sellerTrust, demoBypass } = input;

    console.log(`[settlementTask] Executing settlement for Watch #${watch.id} ("${watch.productName}") at $${winningOffer.price} via ${winningOffer.merchant}`);

    // Pre-flight check: ensure forensics and trust passed or demo bypass is enabled
    if (!demoBypass) {
      if (!forensics.authentic || forensics.promptInjectionDetected) {
        return {
          success: false,
          watchId: watch.id,
          settledAmount: 0,
          savings: 0,
          paymentMethod: 'PAYPAL_INSTANT_ORDER',
          status: 'FAILED',
          message: `Settlement blocked by AI Forensics: ${forensics.explanation}`
        };
      }

      if (sellerTrust.recommendation === 'REJECT') {
        return {
          success: false,
          watchId: watch.id,
          settledAmount: 0,
          savings: 0,
          paymentMethod: 'PAYPAL_INSTANT_ORDER',
          status: 'FAILED',
          message: `Settlement blocked by Seller Reputation: ${sellerTrust.redFlags.join(', ')}`
        };
      }
    }

    const client = new BackendClient();
    const result = await client.executeSettlement(watch.id, winningOffer, demoBypass);

    console.log(`[settlementTask] Settlement finished with status=${result.status}, orderId=${result.orderId}`);
    return result;
  }
);
