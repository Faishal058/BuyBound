/**
 * TypeScript mirrors of the BuyBound backend API contract
 * (contracts/openapi.yaml v0.3.0 + v0.5.0 Channel3 / AI Forensics / PayPal Vault extensions).
 */

export type WatchStatus = 'ACTIVE' | 'PAUSED' | 'REVOKED';

export type IntentStatus =
  | 'CREATED'
  | 'OFFER_VALIDATED'
  | 'GUARDIAN_EVALUATED'
  | 'AWAITING_APPROVAL'
  | 'HELD_FOR_APPROVAL'
  | 'AWAITING_PAYPAL_SETTLEMENT'
  | 'APPROVED'
  | 'SPEND_RESERVED'
  | 'ORDER_CREATE_PENDING'
  | 'ORDER_CREATED'
  | 'CAPTURE_PENDING'
  | 'CAPTURED'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'RECONCILIATION_REQUIRED'
  | 'CANCELLED'
  | 'REJECTED_BY_USER'
  | 'REFUND_PENDING'
  | 'REFUNDED'
  | 'EXPIRED'
  | 'BLOCKED';

export type GuardianDecisionValue = 'ALLOW' | 'HOLD' | 'BLOCK';

export type NewMerchantBehavior = 'ALLOW' | 'HOLD' | 'REQUIRE_APPROVAL' | 'BLOCK';

export type ReasonCode =
  | 'WITHIN_POLICY'
  | 'ALL_CHECKS_PASSED'
  | 'BELOW_TARGET_PRICE'
  | 'PRICE_ABOVE_MAX_TOTAL'
  | 'PRICE_EXCEEDS_MANDATE_LIMIT'
  | 'UNIT_PRICE_ABOVE_CAP'
  | 'MERCHANT_NOT_ALLOWLISTED'
  | 'QUANTITY_MISMATCH'
  | 'CURRENCY_MISMATCH'
  | 'STALE_OFFER'
  | 'INCOMPLETE_EVIDENCE'
  | 'MANDATE_REVOKED'
  | 'MANDATE_EXPIRED'
  | 'BUDGET_EXHAUSTED'
  | 'WEEKLY_BUDGET_EXCEEDED'
  | 'VELOCITY_EXCEEDED'
  | 'APPROVAL_REQUIRED'
  | 'POLICY_VERSION_MISMATCH'
  | 'SERVICE_ERROR'
  | 'PRODUCT_IDENTITY_UNCERTAIN'
  | 'NEW_MERCHANT_HOLD'
  | 'UNAPPROVED_MERCHANT_HOLD'
  | 'PRICE_DRIFT_DETECTED'
  | 'PROMPT_INJECTION_DETECTED'
  | 'SKU_MISMATCH_ACCESSORY_BAIT'
  | 'SUBSCRIPTION_TRAP_BLOCKED'
  | 'ANOMALOUS_DISCOUNT_HOLD'
  | 'NEW_USER_SENSITIVE_HOLD'
  | 'ITEM_PRICE_CAP_EXCEEDED'
  | 'SELLER_RATING_TOO_LOW'
  | 'SELLER_NEGATIVE_REVIEW_RED_FLAG'
  | 'WITHIN_DROP_GUARDRAIL_BAND';

export interface RuleCheckDetail {
  ruleName: string;
  passed: boolean;
  expectedThreshold: string;
  actualValue: string;
  reasonCode: string;
}

export interface HealthResponse {
  status: string;
}

export interface InfoResponse {
  name: string;
  version: string;
  paymentsMode: 'sandbox' | 'stub';
  discoveryMode: 'fixture' | 'channel3';
  aiProvider?: string;
  aiModel?: string;
  channel3Configured?: boolean;
  aiConfigured?: boolean;
}

export interface CreateWatchRequest {
  productName: string;
  productId?: string;
  sku?: string;
  variant?: string;
  merchantId: string;
  currency: string;
  targetPrice: string;
  maxUnitPrice: string;
  maxTotal: string;
  maxQuantity: number;
  allowedMerchantIds?: string[];
  requiresHumanApproval?: boolean;
  maxWeeklySpend?: string;
  naturalLanguagePrompt?: string;
  referenceProductUrl?: string;
  baselineMarketPrice?: string;
  minSellerRating?: number;
}

export interface Watch {
  id: string;
  productName: string;
  productId?: string;
  sku?: string;
  variant?: string;
  merchantId: string;
  mandateId: string;
  mandateVersion: number;
  currency: string;
  targetPrice: string;
  status: WatchStatus;
  naturalLanguagePrompt?: string;
  referenceProductUrl?: string;
  baselineMarketPrice?: string;
  imageUrl?: string;
  lastHuntedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Mandate {
  id: string;
  version: number;
  watchId: string;
  currency: string;
  maxUnitPrice: string;
  maxTotal: string;
  maxQuantity: number;
  allowedMerchantIds: string[];
  maxPurchasesPerDay: number;
  offerMaxAgeSeconds: number;
  newMerchantBehavior: NewMerchantBehavior;
  requireHumanApprovalAbove?: string;
  maxWeeklySpend?: string;
  status: string;
  vaultConnected?: boolean;
  maskedVaultToken?: string;
  paypalPayerEmail?: string;
  createdAt: string;
  revokedAt?: string;
  revocationReason?: string;
}

export interface ActiveMandateTelemetry {
  mandateId: string | null;
  watchId: string | null;
  status: string;
  currency: string;
  maxWeeklySpend: string;
  weeklySpent: string;
  remainingBudget: string;
  activeWatchesCount: number;
  totalWatchesCount: number;
  paypalConnected: boolean;
  vaultConnected?: boolean;
  maskedVaultToken?: string;
  paypalPayerEmail?: string;
  totalVerifiedSavings: string;
  blockedThreatsCount: number;
  heldApprovalsCount: number;
  capturedCount: number;
  totalCommissionEarned: string;
  maxUnitPrice?: string;
  allowedMerchants?: string[];
  sandboxBalance?: string;
  paypalPayerName?: string;
  paypalPayerId?: string;
  newMerchantBehavior?: string;
}

export interface OfferSnapshot {
  merchantId: string;
  canonicalSku?: string;
  variant?: string;
  quantity: number;
  currency: string;
  unitPrice: string;
  totalPrice: string;
  observedAt: string;
  source: string;
  evidenceHash: string;
  channel3ProductId?: string;
  productUrl?: string;
  imageUrl?: string;
  baselinePrice?: string;
  verifiedSavings?: string;
  commissionRate?: string;
  aiMatchConfidence?: string;
  aiExactSkuMatch?: boolean;
  aiPromptInjectionDetected?: boolean;
  aiSubscriptionTrapDetected?: boolean;
  aiForensicsSummary?: string;
  sellerRating?: number;
  sellerReviewCount?: number;
  verifiedSellerHistory?: boolean;
  negativeRedFlagCount?: number;
  sellerRedFlagSummary?: string;
  estimatedDelivery?: string;
}

export interface PurchaseIntent {
  id: string;
  watchId: string;
  mandateId: string;
  mandateVersion: number;
  status: IntentStatus;
  merchantId: string;
  canonicalSku?: string;
  variant?: string;
  quantity: number;
  currency: string;
  maxTotal: string;
  guardianDecisionId?: string;
  policyVersion?: string;
  evidenceHash?: string;
  offerSnapshot?: OfferSnapshot;
  idempotencyKey: string;
  providerOrderId?: string;
  paypalAuthorizationId?: string;
  paypalCaptureId?: string;
  approvalUrl?: string;
  failureReason?: string;
  purchaseMode?: string;
  createdAt: string;
  updatedAt: string;
}

export interface HuntedOffer {
  id: string;
  watchId: string;
  intentId?: string;
  channel3ProductId?: string;
  productTitle: string;
  merchantId: string;
  canonicalSku?: string;
  currency: string;
  offerPrice: string;
  baselinePrice?: string;
  verifiedSavings?: string;
  commissionRate?: string;
  productUrl?: string;
  imageUrl?: string;
  source: string;
  aiMatchConfidence?: string;
  aiExactSkuMatch: boolean;
  aiPromptInjectionDetected: boolean;
  aiSubscriptionTrapDetected: boolean;
  aiForensicsSummary?: string;
  guardianDecision: GuardianDecisionValue;
  reasonCodes: string[];
  ruleChecks: RuleCheckDetail[];
  observedAt: string;
  createdAt: string;
  sellerRating?: number;
  sellerReviewCount?: number;
  verifiedSellerHistory?: boolean;
  negativeRedFlagCount?: number;
  sellerRedFlagSummary?: string;
  estimatedDelivery?: string;
}

export interface GuardianDecision {
  id: string;
  intentId: string;
  mandateId: string;
  mandateVersion: number;
  decision: GuardianDecisionValue;
  reasonCodes: ReasonCode[];
  policyVersion: string;
  evidenceHash: string;
  evaluatedAt: string;
  expiresAt: string;
  ruleChecks?: RuleCheckDetail[];
}

export interface AuditEvent {
  id: string;
  eventType: string;
  entityType?: string;
  entityId?: string;
  correlationId: string;
  reasonCodes?: ReasonCode[];
  policyVersion?: string;
  summary?: string;
  occurredAt: string;
}

export interface SubmitOfferRequest {
  merchantId: string;
  canonicalSku?: string;
  variant?: string;
  quantity: number;
  currency: string;
  unitPrice: string;
  totalPrice: string;
  source?: string;
}

export interface SubmitOfferResponse {
  intent: PurchaseIntent;
  decision: GuardianDecision;
}

export interface ApproveResponse {
  intentId: string;
  status: string;
  message: string;
}

export interface DemoCaptureResponse {
  intentId: string;
  status: IntentStatus;
  simulated: boolean;
  providerReference?: string;
  message: string;
  paypalOrderId?: string;
  paypalAuthorizationId?: string;
  paypalCaptureId?: string;
  approvalUrl?: string;
}

export interface ParsedWatchBlueprint {
  title: string;
  query: string;
  sku?: string;
  variant?: string;
  targetPrice: string;
  maxItemPrice: string;
  weeklySpendLimit: string;
  allowedMerchants: string[];
  currency: string;
  blockSubscriptions: boolean;
  aiExplanation: string;
}

export interface AiOfferForensicsResult {
  aiMatchConfidence: string;
  exactSkuMatch: boolean;
  promptInjectionDetected: boolean;
  subscriptionTrapDetected: boolean;
  aiForensicsSummary: string;
  recommendedAction: string;
}

export interface AgGridAiQueryResponse {
  filterModel: Record<string, unknown>;
  sortModel: Array<{ colId: string; sort: 'asc' | 'desc' }>;
  groupColumns: string[];
  insightSummary: string;
}

export interface Channel3ProductOffer {
  channel3ProductId: string;
  title: string;
  description?: string;
  brand?: string;
  category?: string;
  merchantName: string;
  merchantDomain: string;
  currentPrice: string;
  baselinePrice: string;
  verifiedSavings: string;
  savingsPercent: string;
  currency: string;
  productUrl?: string;
  imageUrl?: string;
  availability: string;
  condition: string;
  commissionRatePercent: string;
  source: string;
  observedAt: string;
  sellerRating?: number;
  sellerReviewCount?: number;
  verifiedSellerHistory?: boolean;
  negativeRedFlagCount?: number;
  sellerRedFlagSummary?: string;
  estimatedDelivery?: string;
}

export interface Channel3SearchResponse {
  query: string;
  productTitle?: string;
  canonicalProductId?: string;
  baselineMarketPrice?: string;
  offers: Channel3ProductOffer[];
  fetchedAt: string;
}

export interface MarketplaceProduct {
  productId: string;
  title: string;
  brand?: string;
  category?: string;
  description?: string;
  imageUrl?: string;
  bestPrice: string;
  baselinePrice: string;
  bestSavings: string;
  savingsPercent: string;
  bestMerchantName: string;
  bestMerchantDomain: string;
  storeCount: number;
  offers: Channel3ProductOffer[];
}

export interface MarketplaceResponse {
  products: MarketplaceProduct[];
  categories: string[];
  totalCount: number;
  source: string;
  query: string;
}

export interface InstantBuyRequest {
  offer?: Channel3ProductOffer;
  mandateId?: string;
  watchId?: string;
  targetPrice?: string;
  naturalLanguagePrompt?: string;
  bypassAgent?: boolean;
  title?: string;
  price?: number | string;
  currency?: string;
  merchantName?: string;
  merchantDomain?: string;
  productUrl?: string;
  imageUrl?: string;
  channel3ProductId?: string;
}

export interface InstantBuyResponse {
  watch: Watch;
  offer: HuntedOffer;
  forensics: AiOfferForensicsResult;
  intent: PurchaseIntent;
  decision: GuardianDecision;
  paypalExecution?: DemoCaptureResponse;
  summary: string;
}

export interface UpdateMandateRequest {
  maxUnitPrice?: number;
  maxTotal?: number;
  maxWeeklySpend?: number;
  requireHumanApprovalAbove?: number;
  allowedMerchantIds?: string[];
  status?: 'ACTIVE' | 'PAUSED' | 'REVOKED';
  newMerchantBehavior?: NewMerchantBehavior | string;
}

export interface RuntimeGuardrailSettings {
  demoBypassEnabled: boolean;
  strictSellerVerification: boolean;
  guardianAutoApproveDemoMode: boolean;
  rateLimitDisabled: boolean;
  ignoreOvernightWindow: boolean;
}

export interface PurchaseActivityItem {
  intentId: string;
  watchId: string;
  mandateId: string;
  timestamp: string;
  productName: string;
  merchantName: string;
  merchantDomain: string;
  imageUrl?: string;
  productUrl?: string;
  purchaseMode: 'AI_AGENT' | 'DIRECT_HUMAN' | string;
  intentStatus: IntentStatus;
  paymentStatusLabel: string;
  paypalOrderId?: string;
  paypalCaptureId?: string;
  paypalAuthorizationId?: string;
  approvalUrl?: string;
  payerEmail?: string;
  payerId?: string;
  amount: string;
  currency: string;
  updatedSandboxWalletBalance?: string | null;
  auditSummary?: string;
  reasonCodes?: string;
}

export interface PayPalOrderVerificationResult {
  orderId: string;
  status: string;
  captureId?: string;
  payerEmail?: string;
  payerName?: string;
  payerId?: string;
  amount?: string;
  currency?: string;
  updateTime?: string;
  message: string;
}

export interface PayPalConfigResponse {
  clientId: string;
  mode: string;
  currency: string;
  configured: boolean;
  oauthAuthorizeUrl: string;
}

export interface CreatePayPalOrderRequest {
  productTitle?: string;
  merchantId?: string;
  price?: number;
  baselinePrice?: number;
  currency?: string;
  watchId?: string;
  mandateId?: string;
  intentId?: string;
  channel3ProductId?: string;
  imageUrl?: string;
  productUrl?: string;
  bypassAgent?: boolean;
}

export interface CreatePayPalOrderResponse {
  intentId: string;
  paypalOrderId?: string;
  approveUrl?: string;
  guardianDecision?: GuardianDecision;
  intent: PurchaseIntent;
  forensics?: AiOfferForensicsResult;
  blocked: boolean;
  message: string;
}

export interface CapturePayPalOrderResponse {
  intent: PurchaseIntent;
  captureId?: string;
  paypalOrderId: string;
  payerEmail?: string;
  payerName?: string;
  newSandboxBalance?: string | null;
  deductedAmount: string;
  message: string;
}

export interface HuntWatchResponse {
  watch: Watch;
  query: string;
  baselineMarketPrice?: string;
  offersScanned: number;
  evaluatedOffers: HuntedOffer[];
  selectedIntent?: PurchaseIntent;
  decision?: GuardianDecision;
  paypalExecution?: DemoCaptureResponse;
  huntedAt: string;
  summary: string;
}

export type RedTeamAttackType =
  | 'POISONED_BUNDLE_899'
  | 'ACCESSORY_EARPAD_BAIT'
  | 'HIDDEN_VIP_SUBSCRIPTION'
  | 'UNAPPROVED_SHADY_SHOP';

export interface RedTeamAttackResponse {
  attackType: string;
  attackLabel: string;
  evaluatedOffer: HuntedOffer;
  aiForensics: AiOfferForensicsResult;
  intent: PurchaseIntent;
  decision: GuardianDecision;
  summary: string;
}

export interface EvaluationPolicyInput {
  mandateId: string;
  version: number;
  currency: string;
  maxUnitPrice: string;
  maxTotal: string;
  maxQuantity: number;
  allowedMerchantIds: string[];
  maxPurchasesPerDay: number;
  offerMaxAgeSeconds: number;
  newMerchantBehavior: NewMerchantBehavior;
  requireHumanApprovalAbove?: string;
  maxWeeklySpend?: string;
  policyVersion: string;
  mandateStatus?: string;
}

export interface EvaluationOfferInput {
  merchantId: string;
  canonicalSku?: string;
  variant?: string;
  quantity: number;
  currency: string;
  unitPrice: string;
  totalPrice: string;
  observedAt: string;
  source?: string;
  evidenceHash?: string;
}

export interface EvaluationContextInput {
  purchasesToday?: number;
  remainingBudget?: string;
  now?: string;
}

export interface EvaluationRequest {
  policy: EvaluationPolicyInput;
  offer: EvaluationOfferInput;
  context?: EvaluationContextInput;
}

export interface EvaluationResponse {
  decision: GuardianDecisionValue;
  reasonCodes: ReasonCode[];
  policyVersion: string;
  evaluatedAt: string;
  expiresAt: string;
  ruleChecks?: RuleCheckDetail[];
}

export interface PauseWatchResponse {
  watchId: string;
  status: string;
}

export interface RevokeWatchResponse {
  watchId: string;
  status: string;
  message: string;
}

export interface BackendErrorBody {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
}

export interface UserProfile {
  id: string | null;
  fullName: string | null;
  email: string | null;
  phone: string | null;
  shippingStreet: string | null;
  shippingCity: string | null;
  shippingState: string | null;
  shippingPostalCode: string | null;
  shippingCountry: string | null;
  notifyPriceDrops: boolean;
  notifyHeldSms: boolean;
  notifyReceiptEmails: boolean;
  paypalEmail: string | null;
  paypalPayerId: string | null;
  loggedIn: boolean;
  paypalConnected: boolean;
  paypalBalance?: string | null;
  weeklyLimit: string;
  maxItemPrice: string;
  spentThisWeek: string;
  remainingWeeklyBudget: string;
  currency: string;
  createdAt?: string | null;
  updatedAt?: string | null;
  sessionToken?: string | null;
}

export interface LoginRequest {
  email: string;
  password: string;
  viaPayPal?: boolean;
  paypalEmail?: string;
  fullName?: string;
}

export interface SignupRequest {
  fullName: string;
  email: string;
  phone?: string;
  password: string;
  paypalEmail?: string;
  currency?: string;
  maxWeeklySpend?: string;
  maxUnitPrice?: string;
}

export interface UpdateProfileRequest {
  fullName?: string;
  email?: string;
  phone?: string;
  shippingStreet?: string;
  shippingCity?: string;
  shippingState?: string;
  shippingPostalCode?: string;
  shippingCountry?: string;
  notifyPriceDrops?: boolean;
  notifyHeldSms?: boolean;
  notifyReceiptEmails?: boolean;
  paypalEmail?: string;
}
