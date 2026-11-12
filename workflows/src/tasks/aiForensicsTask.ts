import { task, type TaskContext } from '@renderinc/sdk/workflows';
import type { ForensicsInput, ForensicsResult } from '../types/index.js';

const INJECTION_PATTERNS = [
  /ignore previous instructions/i,
  /system prompt/i,
  /override safety/i,
  /jailbreak/i,
  /execute command/i,
  /eval\(/i,
  /set price to 0/i,
  /free shipping override/i
];

export const aiForensicsTask = task(
  {
    name: 'aiForensicsTask',
    retry: {
      maxRetries: 2,
      waitDurationMs: 500,
    },
    timeoutSeconds: 45,
    plan: 'flex',
  },
  async function runAiForensics(ctx: TaskContext, input: ForensicsInput): Promise<ForensicsResult> {
    const { offer, referencePrice, expectedKeywords } = input;
    console.log(`[aiForensicsTask] Inspecting offer "${offer.productTitle}" ($${offer.price}) from ${offer.merchant}`);

    const suspiciousPhrases: string[] = [];

    // 1. Prompt Injection Firewall Check
    for (const pattern of INJECTION_PATTERNS) {
      if (pattern.test(offer.productTitle) || pattern.test(offer.sellerName)) {
        suspiciousPhrases.push(`Detected prompt injection pattern: ${pattern}`);
      }
    }

    // 2. Price Sanity & Anomaly Check (e.g. 95% below market is often a scam or accessory listing)
    let riskScore = 0.05;
    if (referencePrice > 0 && offer.price < referencePrice * 0.25) {
      suspiciousPhrases.push(`Extreme price anomaly: offer price $${offer.price} is >75% below reference $${referencePrice}`);
      riskScore += 0.6;
    }

    // 3. Keyword / SKU Authenticity Matching
    let matchedKeywords = 0;
    if (expectedKeywords && expectedKeywords.length > 0) {
      const lowerTitle = offer.productTitle.toLowerCase();
      for (const kw of expectedKeywords) {
        if (lowerTitle.includes(kw.toLowerCase())) {
          matchedKeywords++;
        }
      }
    }
    const matchScore = expectedKeywords && expectedKeywords.length > 0
      ? matchedKeywords / expectedKeywords.length
      : 1.0;

    if (matchScore < 0.4) {
      suspiciousPhrases.push(`Low keyword match (${Math.round(matchScore * 100)}%): possible mismatched variant or accessory`);
      riskScore += 0.35;
    }

    const promptInjectionDetected = suspiciousPhrases.some(p => p.includes('prompt injection'));
    if (promptInjectionDetected) {
      riskScore = 1.0;
    }

    const authentic = riskScore < 0.4 && !promptInjectionDetected;

    console.log(`[aiForensicsTask] Result for ${offer.id}: authentic=${authentic}, risk=${riskScore.toFixed(2)}, match=${(matchScore * 100).toFixed(0)}%`);

    return {
      offerId: offer.id,
      authentic,
      matchScore: Math.round(matchScore * 100) / 100,
      promptInjectionDetected,
      suspiciousPhrases,
      riskScore: Math.round(riskScore * 100) / 100,
      explanation: authentic
        ? `Passed all AI forensics checks. High SKU confidence (${(matchScore * 100).toFixed(0)}%) with no prompt injection detected.`
        : `Flagged by security firewall: ${suspiciousPhrases.join('; ')}`
    };
  }
);
