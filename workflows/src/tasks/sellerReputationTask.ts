import { task, type TaskContext } from '@renderinc/sdk/workflows';
import type { SellerReputationInput, SellerReputationResult } from '../types/index.js';

const KNOWN_DIRECT_MERCHANTS = new Set([
  'amazon',
  'best buy',
  'target',
  'walmart',
  'b&h photo',
  'apple',
  'sony',
  'newegg',
  'adorama'
]);

export const sellerReputationTask = task(
  {
    name: 'sellerReputationTask',
    retry: {
      maxRetries: 2,
      waitDurationMs: 500,
    },
    timeoutSeconds: 30,
    plan: 'flex',
  },
  async function runSellerReputation(ctx: TaskContext, input: SellerReputationInput): Promise<SellerReputationResult> {
    const { merchant, sellerName, rating, reviewCount, verified } = input;
    const redFlags: string[] = [];

    const isDirectOfficial = KNOWN_DIRECT_MERCHANTS.has(merchant.toLowerCase()) || 
                             sellerName.toLowerCase().includes('official') || 
                             sellerName.toLowerCase().includes('direct');

    let trustScore = 0.5;

    // Direct official retailers automatically start with high trust
    if (isDirectOfficial) {
      trustScore += 0.4;
    }

    // Rating checks
    if (rating >= 4.5) {
      trustScore += 0.2;
    } else if (rating >= 4.0) {
      trustScore += 0.1;
    } else if (rating < 3.5 && !isDirectOfficial) {
      redFlags.push(`Low seller rating: ${rating} / 5.0`);
      trustScore -= 0.3;
    }

    // Review count checks
    if (reviewCount >= 500) {
      trustScore += 0.1;
    } else if (reviewCount < 10 && !isDirectOfficial) {
      redFlags.push(`Very low review volume: only ${reviewCount} reviews recorded`);
      trustScore -= 0.3;
    }

    if (!verified && !isDirectOfficial) {
      redFlags.push('Seller is unverified on the marketplace');
      trustScore -= 0.15;
    }

    trustScore = Math.max(0.0, Math.min(1.0, trustScore));

    const telemetryPass = trustScore >= 0.7 && redFlags.length === 0;

    let recommendation: 'APPROVE' | 'FLAG_FOR_REVIEW' | 'REJECT' = 'REJECT';
    if (telemetryPass) {
      recommendation = 'APPROVE';
    } else if (trustScore >= 0.5) {
      recommendation = 'FLAG_FOR_REVIEW';
    }

    console.log(`[sellerReputationTask] Seller "${sellerName}" evaluated: score=${trustScore.toFixed(2)}, recommendation=${recommendation}`);

    return {
      sellerName,
      trusted: telemetryPass,
      trustScore: Math.round(trustScore * 100) / 100,
      telemetryPass,
      redFlags,
      recommendation
    };
  }
);

