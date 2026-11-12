import { task, type TaskContext } from '@renderinc/sdk/workflows';
import type { 
  OvernightDropWorkflowInput, 
  OvernightDropWorkflowOutput, 
  WatchTarget,
  StoreOffer,
  SettlementResult
} from '../types/index.js';
import { parallelStoreScanTask } from '../tasks/storeScannerTask.js';
import { aiForensicsTask } from '../tasks/aiForensicsTask.js';
import { sellerReputationTask } from '../tasks/sellerReputationTask.js';
import { settlementTask } from '../tasks/settlementTask.js';
import { BackendClient } from '../client/backendClient.js';

export const overnightDropWorkflow = task(
  {
    name: 'overnightDropWorkflow',
    retry: {
      maxRetries: 2,
      waitDurationMs: 2000,
    },
    timeoutSeconds: 300,
    plan: 'flex',
  },
  async function runOvernightDrop(
    ctx: TaskContext, 
    input: OvernightDropWorkflowInput = {}
  ): Promise<OvernightDropWorkflowOutput> {
    const executionId = `wf_drop_${Date.now()}`;
    console.log(`[overnightDropWorkflow] Starting distributed overnight drop run: ${executionId}`);

    const client = new BackendClient();
    
    // 1. Fetch active user watches (or use input watches if provided)
    const watches: WatchTarget[] = (input.watches && input.watches.length > 0)
      ? input.watches
      : await client.fetchActiveWatches();

    console.log(`[overnightDropWorkflow] Loaded ${watches.length} active watch targets to orchestrate.`);

    let dealsDiscovered = 0;
    let dealsSettled = 0;
    let totalSavingsUsd = 0;
    const results: OvernightDropWorkflowOutput['results'] = [];

    // 2. Dispatch parallel scans across instances
    // In Render Workflows, each ctx.run() runs on its own isolated instance
    const scanPromises = watches.map(async (watch) => {
      try {
        console.log(`[overnightDropWorkflow] Dispatching parallelStoreScanTask for Watch #${watch.id} ("${watch.productName}")`);
        const scanResult = await ctx.run(parallelStoreScanTask, { watch });

        // Filter offers that beat the user's target price
        const eligibleOffers = scanResult.offers
          .filter(o => o.inStock && o.price <= watch.targetPrice)
          .sort((a, b) => a.price - b.price);

        if (eligibleOffers.length === 0) {
          return {
            watchId: watch.id,
            productName: watch.productName,
            status: 'TARGET_NOT_MET' as const,
            reason: `Lowest offer ($${scanResult.lowestPrice.toFixed(2)}) did not breach target price ($${watch.targetPrice.toFixed(2)}).`
          };
        }

        const bestOffer: StoreOffer = eligibleOffers[0];
        dealsDiscovered++;
        console.log(`[overnightDropWorkflow] Deal discovered for "${watch.productName}": $${bestOffer.price.toFixed(2)} vs target $${watch.targetPrice.toFixed(2)} at ${bestOffer.merchant}`);

        // 3. Concurrently dispatch AI Forensics & Seller Trust tasks across instances
        const expectedKeywords = watch.productName.split(/\s+/).filter(w => w.length > 3);
        const [forensicsResult, sellerTrustResult] = await Promise.all([
          ctx.run(aiForensicsTask, {
            offer: bestOffer,
            referencePrice: watch.targetPrice,
            expectedKeywords
          }),
          ctx.run(sellerReputationTask, {
            merchant: bestOffer.merchant,
            sellerName: bestOffer.sellerName,
            rating: bestOffer.sellerRating,
            reviewCount: bestOffer.sellerReviewCount,
            verified: bestOffer.sellerVerified
          })
        ]);

        // 4. Security & Guardrails check
        if (!forensicsResult.authentic || forensicsResult.promptInjectionDetected) {
          console.warn(`[overnightDropWorkflow] Deal blocked for Watch #${watch.id} by AI Forensics.`);
          return {
            watchId: watch.id,
            productName: watch.productName,
            bestOffer,
            status: 'SECURITY_BLOCKED' as const,
            reason: `AI Forensics blocked offer: ${forensicsResult.explanation}`
          };
        }

        if (sellerTrustResult.recommendation === 'REJECT') {
          console.warn(`[overnightDropWorkflow] Deal blocked for Watch #${watch.id} by Seller Trust.`);
          return {
            watchId: watch.id,
            productName: watch.productName,
            bestOffer,
            status: 'SECURITY_BLOCKED' as const,
            reason: `Seller reputation rejected: ${sellerTrustResult.redFlags.join(', ')}`
          };
        }

        // 5. Dispatch Settlement Task on its own compute
        console.log(`[overnightDropWorkflow] Security verified. Dispatching settlementTask for Watch #${watch.id}`);
        const settlement: SettlementResult = await ctx.run(settlementTask, {
          watch,
          winningOffer: bestOffer,
          forensics: forensicsResult,
          sellerTrust: sellerTrustResult,
          demoBypass: false
        });

        if (settlement.success) {
          dealsSettled++;
          totalSavingsUsd += (watch.targetPrice - bestOffer.price);
        }

        return {
          watchId: watch.id,
          productName: watch.productName,
          bestOffer,
          settlement,
          status: settlement.success ? ('BOUGHT' as const) : ('TARGET_NOT_MET' as const),
          reason: settlement.message
        };

      } catch (err: any) {
        console.error(`[overnightDropWorkflow] Error processing Watch #${watch.id}: ${err.message}`);
        return {
          watchId: watch.id,
          productName: watch.productName,
          status: 'ERROR' as const,
          reason: err.message
        };
      }
    });

    const settledResults = await Promise.all(scanPromises);
    results.push(...settledResults);

    console.log(`[overnightDropWorkflow] Overnight drop workflow finished. Deals discovered: ${dealsDiscovered}, Deals settled: ${dealsSettled}, Total savings: $${totalSavingsUsd.toFixed(2)}`);

    return {
      executionId,
      timestamp: new Date().toISOString(),
      totalWatchesProcessed: watches.length,
      dealsDiscovered,
      dealsSettled,
      totalSavingsUsd: Math.round(totalSavingsUsd * 100) / 100,
      results
    };
  }
);
