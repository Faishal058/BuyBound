export interface WatchTarget {
  id: string | number;
  productName: string;
  targetPrice: number;
  maxPrice: number;
  currency: string;
  preferredRetailer?: string;
  condition?: 'NEW' | 'REFURBISHED' | 'ANY';
  userEmail?: string;
  status?: string;
}

export interface StoreOffer {
  id: string;
  merchant: string;
  productTitle: string;
  price: number;
  currency: string;
  url: string;
  inStock: boolean;
  condition: string;
  sellerName: string;
  sellerRating: number;
  sellerReviewCount: number;
  sellerVerified: boolean;
  discoveredAt: string;
  rawTelemetry?: Record<string, unknown>;
}

export interface StoreScanInput {
  watch: WatchTarget;
  merchants?: string[];
}

export interface StoreScanResult {
  watchId: string | number;
  totalOffersFound: number;
  lowestPrice: number;
  offers: StoreOffer[];
  scannedAt: string;
}

export interface ForensicsInput {
  offer: StoreOffer;
  referencePrice: number;
  expectedKeywords: string[];
}

export interface ForensicsResult {
  offerId: string;
  authentic: boolean;
  matchScore: number; // 0.0 to 1.0
  promptInjectionDetected: boolean;
  suspiciousPhrases: string[];
  riskScore: number; // 0.0 (safe) to 1.0 (critical danger)
  explanation: string;
}

export interface SellerReputationInput {
  merchant: string;
  sellerName: string;
  rating: number;
  reviewCount: number;
  verified: boolean;
}

export interface SellerReputationResult {
  sellerName: string;
  trusted: boolean;
  trustScore: number; // 0.0 to 1.0
  telemetryPass: boolean;
  redFlags: string[];
  recommendation: 'APPROVE' | 'FLAG_FOR_REVIEW' | 'REJECT';
}

export interface SettlementInput {
  watch: WatchTarget;
  winningOffer: StoreOffer;
  forensics: ForensicsResult;
  sellerTrust: SellerReputationResult;
  demoBypass?: boolean;
}

export interface SettlementResult {
  success: boolean;
  watchId: string | number;
  purchaseId?: string;
  orderId?: string;
  settledAmount: number;
  savings: number;
  paymentMethod: 'PAYPAL_VAULT' | 'PAYPAL_INSTANT_ORDER' | 'WALLET_BALANCE';
  status: 'SETTLED' | 'HELD_FOR_CONFIRMATION' | 'FAILED';
  receiptUrl?: string;
  message: string;
}

export interface OvernightDropWorkflowInput {
  watches?: WatchTarget[];
  triggerSource?: 'SCHEDULED_CRON' | 'MANUAL_DISPATCH' | 'WEBHOOK';
  maxConcurrentHunts?: number;
  autoSettleThresholdSavingsPercent?: number;
}

export interface OvernightDropWorkflowOutput {
  executionId: string;
  timestamp: string;
  totalWatchesProcessed: number;
  dealsDiscovered: number;
  dealsSettled: number;
  totalSavingsUsd: number;
  results: {
    watchId: string | number;
    productName: string;
    bestOffer?: StoreOffer;
    settlement?: SettlementResult;
    status: 'BOUGHT' | 'TARGET_NOT_MET' | 'SECURITY_BLOCKED' | 'ERROR';
    reason?: string;
  }[];
}

