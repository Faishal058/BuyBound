import { task, type TaskContext } from '@renderinc/sdk/workflows';
import type { StoreScanInput, StoreScanResult, StoreOffer } from '../types/index.js';

export const parallelStoreScanTask = task(
  {
    name: 'parallelStoreScanTask',
    retry: {
      maxRetries: 3,
      waitDurationMs: 1000,
      backoffScaling: 2.0,
    },
    timeoutSeconds: 60,
    plan: 'flex',
  },
  async function runStoreScan(ctx: TaskContext, input: StoreScanInput): Promise<StoreScanResult> {
    const { watch } = input;
    const merchants = input.merchants && input.merchants.length > 0 
      ? input.merchants 
      : ['Amazon', 'Best Buy', 'Target', 'Walmart', 'B&H Photo'];

    console.log(`[parallelStoreScanTask] Scanning ${merchants.length} merchants for: "${watch.productName}" (Target: $${watch.targetPrice})`);

    // In production with Channel3 API:
    // const channel3Url = process.env.CHANNEL3_BASE_URL || 'https://api.trychannel3.com/v1';
    // fetch(`${channel3Url}/search?q=${encodeURIComponent(watch.productName)}`, { headers: { 'x-api-key': process.env.CHANNEL3_API_KEY } });

    // Generate simulated live merchant pricing and inventory telemetry across channels
    const offers: StoreOffer[] = merchants.map((merchant, idx) => {
      // Simulate real-world price variations around the target price
      // One or two merchants hit below target during overnight drop windows
      const varianceFactor = 0.88 + ((idx * 17 + watch.productName.length) % 25) / 100;
      const computedPrice = Math.round((watch.targetPrice * varianceFactor) * 100) / 100;
      const isLowest = computedPrice < watch.targetPrice;

      return {
        id: `off_${merchant.toLowerCase().replace(/[^a-z0-9]/g, '')}_${watch.id}_${Date.now()}`,
        merchant,
        productTitle: watch.productName,
        price: computedPrice,
        currency: watch.currency || 'USD',
        url: `https://www.${merchant.toLowerCase().replace(/[^a-z0-9]/g, '')}.com/p/${watch.id}`,
        inStock: true,
        condition: watch.condition || 'NEW',
        sellerName: `${merchant} Direct Official`,
        sellerRating: 4.6 + (idx % 4) * 0.1,
        sellerReviewCount: 1500 + idx * 450,
        sellerVerified: true,
        discoveredAt: new Date().toISOString(),
        rawTelemetry: {
          scannedByTaskRunId: ctx.metadata?.taskRunId || 'local-run',
          merchantResponseMs: 120 + idx * 35,
          referencePrice: watch.targetPrice,
          priceDropDetected: isLowest
        }
      };
    });

    const lowestPrice = offers.reduce((min, o) => Math.min(min, o.price), Number.MAX_VALUE);

    console.log(`[parallelStoreScanTask] Scan complete. Found ${offers.length} offers. Lowest: $${lowestPrice}`);

    return {
      watchId: watch.id,
      totalOffersFound: offers.length,
      lowestPrice,
      offers,
      scannedAt: new Date().toISOString()
    };
  }
);
