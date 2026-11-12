import { useCallback, useEffect, useId, useMemo, useRef, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { GuardianProofModal, type GuardianProofTarget } from '../../../components/modals/GuardianProofModal';
import { PayPalCheckoutModal, type CheckoutSession } from '../../../components/modals/PayPalCheckoutModal';
import { VerifiedReceiptModal } from '../../../components/modals/VerifiedReceiptModal';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { Dialog } from '../../../components/ui/Dialog';
import { ErrorBanner } from '../../../components/ui/ErrorBanner';
import { Spinner } from '../../../components/ui/Spinner';
import { TextField } from '../../../components/ui/TextField';
import { toApiError } from '../../../services/api/client';
import { catalogApi, mandatesApi, paypalApi, watchesApi } from '../../../services/api/endpoints';
import type {
  ActiveMandateTelemetry,
  Channel3ProductOffer,
  Channel3SearchResponse,
  MarketplaceProduct,
  PurchaseIntent,
} from '../../../services/api/types';
import { REASON_CODE_EXPLANATIONS, formatStoreName, formatUsd } from '../../shared/meta';
import { handleProductImageError, resolveProductImageSrc } from '../../shared/productImages';

const ALL_CATEGORIES = 'All Categories';

type PriceBracketKey = 'all' | 'under_100' | '100_300' | '300_1000' | 'over_1000';

const PRICE_BRACKETS: Array<{ key: PriceBracketKey; label: string; min?: number; max?: number }> = [
  { key: 'all', label: 'All Prices' },
  { key: 'under_100', label: 'Under $100', max: 100 },
  { key: '100_300', label: '$100 – $300', min: 100, max: 300 },
  { key: '300_1000', label: '$300 – $1,000', min: 300, max: 1000 },
  { key: 'over_1000', label: 'Over $1,000', min: 1000 },
];

type SortOption = 'savings' | 'price_asc' | 'price_desc' | 'best_match';

function toMarketplaceProductsFromLookup(res: Channel3SearchResponse): MarketplaceProduct[] {
  if (!res.offers || res.offers.length === 0) return [];
  const sorted = [...res.offers].sort((a, b) => Number(a.currentPrice) - Number(b.currentPrice));
  const best = sorted[0];
  const baseline = Math.max(...sorted.map((o) => Number(o.baselinePrice || o.currentPrice)));
  const savings = Math.max(0, baseline - Number(best.currentPrice));
  const pct = baseline > 0 ? ((savings / baseline) * 100).toFixed(1) : '0.0';
  return [
    {
      productId: best.channel3ProductId || res.canonicalProductId || 'lookup-item',
      title: best.title || res.productTitle || 'Product Comparison',
      brand: best.brand,
      category: best.category || 'Featured',
      description: best.description,
      imageUrl: best.imageUrl,
      bestPrice: best.currentPrice,
      baselinePrice: baseline.toFixed(2),
      bestSavings: savings.toFixed(2),
      savingsPercent: pct,
      bestMerchantName: best.merchantName || formatStoreName(best.merchantDomain),
      bestMerchantDomain: best.merchantDomain,
      storeCount: new Set(sorted.map((o) => o.merchantDomain)).size,
      offers: sorted,
    },
  ];
}

export function ShopPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const initialQuery = searchParams.get('q') ?? '';
  const initialCategory = searchParams.get('category') ?? ALL_CATEGORIES;

  const [searchInput, setSearchInput] = useState(initialQuery);
  const [committedQuery, setCommittedQuery] = useState(initialQuery);
  const [selectedCategory, setSelectedCategory] = useState(initialCategory);
  const [priceBracket, setPriceBracket] = useState<PriceBracketKey>('all');
  const [sortBy, setSortBy] = useState<SortOption>('savings');

  const [products, setProducts] = useState<MarketplaceProduct[]>([]);
  const [categories, setCategories] = useState<string[]>([
    'Audio',
    'Phones & Tablets',
    'Laptops',
    'Gaming',
    'Sneakers & Fashion',
    'Home & Kitchen',
    'Watches',
    'Cameras',
  ]);
  const [catalogSource, setCatalogSource] = useState<string>('marketplace-catalog');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [telemetry, setTelemetry] = useState<ActiveMandateTelemetry | null>(null);
  const [buyingKey, setBuyingKey] = useState<string | null>(null);

  const [receiptIntent, setReceiptIntent] = useState<PurchaseIntent | null>(null);
  const [receiptTitle, setReceiptTitle] = useState<string | undefined>(undefined);
  const [receiptPayerEmail, setReceiptPayerEmail] = useState<string | undefined>(undefined);
  const [receiptPayerName, setReceiptPayerName] = useState<string | undefined>(undefined);
  const [receiptNewBalance, setReceiptNewBalance] = useState<string | undefined>(undefined);
  const [checkoutSession, setCheckoutSession] = useState<CheckoutSession | null>(null);
  const [proofTarget, setProofTarget] = useState<GuardianProofTarget | null>(null);

  const [outcomeBanner, setOutcomeBanner] = useState<{
    tone: 'success' | 'warning' | 'danger';
    title: string;
    detail: string;
    holdSession?: CheckoutSession;
    approvalUrl?: string;
    ctaTo?: string;
    ctaLabel?: string;
  } | null>(null);

  // Store Compare Modal state
  const [compareProduct, setCompareProduct] = useState<MarketplaceProduct | null>(null);
  const compareDialogRef = useRef<HTMLDialogElement>(null);
  const compareTitleId = useId();
  const searchInputId = useId();
  const sortSelectId = useId();

  // Set Auto-Buy Watch Modal state
  const [watchTargetProduct, setWatchTargetProduct] = useState<MarketplaceProduct | null>(null);
  const [watchTargetPrice, setWatchTargetPrice] = useState('');
  const [watchMaxLimit, setWatchMaxLimit] = useState('');
  const [savingWatch, setSavingWatch] = useState(false);

  const loadTelemetry = useCallback(async () => {
    try {
      const data = await mandatesApi.active();
      setTelemetry(data);
    } catch {
      // Keep page browsing usable even if no mandate has been seeded yet
    }
  }, []);

  const loadCatalog = useCallback(async () => {
    setLoading(true);
    setError(null);
    const trimmed = committedQuery.trim();
    const activeBracket = PRICE_BRACKETS.find((b) => b.key === priceBracket) ?? PRICE_BRACKETS[0];

    try {
      if (trimmed && /^https?:\/\//i.test(trimmed)) {
        const lookupRes = await catalogApi.lookup(trimmed);
        setProducts(toMarketplaceProductsFromLookup(lookupRes));
        setCatalogSource('url-lookup');
      } else {
        const res = await catalogApi.marketplace({
          q: trimmed || undefined,
          category: selectedCategory !== ALL_CATEGORIES ? selectedCategory : undefined,
          minPrice: activeBracket.min,
          maxPrice: activeBracket.max,
          sort: sortBy,
          limit: 50,
        });
        setProducts(res.products);
        if (res.categories && res.categories.length > 0) {
          setCategories(res.categories);
        }
        setCatalogSource(res.source);
      }
    } catch (err) {
      setError(toApiError(err).message);
    } finally {
      setLoading(false);
    }
  }, [committedQuery, selectedCategory, priceBracket, sortBy]);

  useEffect(() => {
    void loadTelemetry();
    const onRefresh = () => void loadTelemetry();
    window.addEventListener('buybound:refresh', onRefresh);
    return () => window.removeEventListener('buybound:refresh', onRefresh);
  }, [loadTelemetry]);

  useEffect(() => {
    void loadCatalog();
  }, [loadCatalog]);

  useEffect(() => {
    const qParam = searchParams.get('q') ?? '';
    const catParam = searchParams.get('category') ?? ALL_CATEGORIES;
    setSearchInput(qParam);
    setCommittedQuery(qParam);
    if (catParam !== selectedCategory) {
      setSelectedCategory(catParam);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  useEffect(() => {
    const dialog = compareDialogRef.current;
    if (!dialog) return;
    if (compareProduct) {
      if (!dialog.open && typeof dialog.showModal === 'function') dialog.showModal();
    } else if (dialog.open && typeof dialog.close === 'function') {
      dialog.close();
    }
  }, [compareProduct]);

  const handleSearchSubmit = (e: FormEvent) => {
    e.preventDefault();
    const trimmed = searchInput.trim();
    setCommittedQuery(trimmed);
    const next = new URLSearchParams(searchParams);
    if (trimmed) next.set('q', trimmed);
    else next.delete('q');
    setSearchParams(next, { replace: true });
  };

  const handleClearSearch = () => {
    setSearchInput('');
    setCommittedQuery('');
    const next = new URLSearchParams(searchParams);
    next.delete('q');
    setSearchParams(next, { replace: true });
  };

  const handleCategorySelect = (cat: string) => {
    setSelectedCategory(cat);
    const next = new URLSearchParams(searchParams);
    if (cat !== ALL_CATEGORIES) next.set('category', cat);
    else next.delete('category');
    setSearchParams(next, { replace: true });
  };

  const maxLimitNum = useMemo(() => Number(telemetry?.maxUnitPrice ?? 260), [telemetry?.maxUnitPrice]);
  const allowedMerchantSet = useMemo(
    () => new Set((telemetry?.allowedMerchants ?? ['bestbuy.com', 'target.com', 'walmart.com', 'amazon.com', 'macys.com']).map((d) => d.toLowerCase())),
    [telemetry?.allowedMerchants],
  );
  const isBuyingPaused = telemetry?.status === 'REVOKED';

  const getSafetyBadge = (priceStr: string, domain: string): { label: string; tone: 'success' | 'warning' | 'danger' } => {
    if (isBuyingPaused) {
      return { label: 'Buying Paused (Safety Lock)', tone: 'warning' };
    }
    const price = Number(priceStr);
    if (Number.isFinite(price) && price > maxLimitNum) {
      return { label: `Above $${maxLimitNum.toFixed(0)} Per-Item Limit`, tone: 'danger' };
    }
    if (!allowedMerchantSet.has(domain.toLowerCase())) {
      return { label: `New Store Check: ${formatStoreName(domain)}`, tone: 'warning' };
    }
    return { label: `Within Your $${maxLimitNum.toFixed(0)} Limit`, tone: 'success' };
  };

  const handleBuyOffer = async (offer: Channel3ProductOffer, bypassAgent = false) => {
    const opKey = `${offer.channel3ProductId}-${offer.merchantDomain}-${bypassAgent ? 'direct' : 'agent'}`;
    setBuyingKey(opKey);
    setOutcomeBanner(null);

    try {
      const res = await paypalApi.createOrder({
        productTitle: offer.title,
        merchantId: offer.merchantDomain,
        price: Number(offer.currentPrice),
        baselinePrice: Number(offer.baselinePrice || offer.currentPrice),
        currency: offer.currency || 'USD',
        mandateId: telemetry?.mandateId ?? undefined,
        channel3ProductId: offer.channel3ProductId,
        imageUrl: offer.imageUrl,
        productUrl: offer.productUrl,
        bypassAgent,
      });

      setCompareProduct(null);
      window.dispatchEvent(new CustomEvent('buybound:refresh'));

      if (res.blocked) {
        const decision = res.guardianDecision;
        setProofTarget({
          title: offer.title,
          merchantId: offer.merchantDomain,
          price: offer.currentPrice,
          baselinePrice: offer.baselinePrice,
          decision: decision?.decision ?? 'BLOCK',
          reasonCodes: decision?.reasonCodes ?? [],
          ruleChecks: decision?.ruleChecks ?? [],
          aiSummary: res.forensics?.aiForensicsSummary,
          aiConfidence: res.forensics?.aiMatchConfidence,
          evidenceHash: res.intent.evidenceHash,
        });
        setOutcomeBanner({
          tone: 'danger',
          title: `Blocked by Your Spending Safety Rules (${formatUsd(offer.currentPrice)})`,
          detail: res.message,
          ctaTo: '/app/safety',
          ctaLabel: 'Adjust My Spending Limits →',
        });
        return;
      }

      const nextSession: CheckoutSession = {
        order: res,
        title: offer.title,
        merchantId: offer.merchantDomain,
        price: offer.currentPrice,
        baselinePrice: offer.baselinePrice,
      };

      if (res.guardianDecision?.decision === 'HOLD') {
        const primaryReason = res.guardianDecision.reasonCodes[0];
        const plainReason = primaryReason
          ? REASON_CODE_EXPLANATIONS[primaryReason] ?? res.message
          : res.message;
        setOutcomeBanner({
          tone: 'warning',
          title: `Order Held Safely Before Payment (${formatUsd(offer.currentPrice)} at ${offer.merchantName})`,
          detail: plainReason,
          holdSession: nextSession,
          approvalUrl: res.approveUrl ?? res.intent.approvalUrl,
          ctaTo: '/app/purchases',
          ctaLabel: 'Review Hold in Orders →',
        });
      } else {
        setCheckoutSession(nextSession);
      }
    } catch (err) {
      setOutcomeBanner({
        tone: 'danger',
        title: 'Checkout could not start',
        detail: toApiError(err).message,
      });
    } finally {
      setBuyingKey(null);
    }
  };

  const openWatchModal = (product: MarketplaceProduct) => {
    const best = Number(product.bestPrice);
    const suggestedTarget = Math.max(10, Math.round(best * 0.94));
    const suggestedLimit = Math.max(Math.ceil(maxLimitNum), Math.ceil(best * 1.1));
    setWatchTargetProduct(product);
    setWatchTargetPrice(String(suggestedTarget));
    setWatchMaxLimit(String(suggestedLimit));
  };

  const handleSaveWatch = async () => {
    if (!watchTargetProduct) return;
    const targetNum = Number(watchTargetPrice);
    const maxNum = Number(watchMaxLimit);
    if (!Number.isFinite(targetNum) || targetNum <= 0 || !Number.isFinite(maxNum) || maxNum < targetNum) {
      return;
    }

    setSavingWatch(true);
    try {
      const storeDomains = Array.from(
        new Set([
          ...Array.from(allowedMerchantSet),
          ...watchTargetProduct.offers.slice(0, 3).map((o) => o.merchantDomain),
        ]),
      );
      const weeklyCap = Math.max(Number(telemetry?.maxWeeklySpend ?? 500), maxNum * 2);
      const baselineNum = Number(watchTargetProduct.baselinePrice || watchTargetProduct.bestPrice);
      await watchesApi.create({
        productName: watchTargetProduct.title,
        productId: watchTargetProduct.productId,
        sku: watchTargetProduct.productId,
        variant: watchTargetProduct.brand || 'Standard',
        merchantId: watchTargetProduct.bestMerchantDomain,
        currency: 'USD',
        targetPrice: targetNum.toFixed(2),
        maxUnitPrice: maxNum.toFixed(2),
        maxTotal: maxNum.toFixed(2),
        maxQuantity: 1,
        maxWeeklySpend: weeklyCap.toFixed(2),
        allowedMerchantIds: storeDomains,
        baselineMarketPrice: baselineNum.toFixed(2),
        referenceProductUrl: watchTargetProduct.offers[0]?.productUrl,
        naturalLanguagePrompt: `Watch ${watchTargetProduct.title} under $${targetNum.toFixed(2)}`,
      });
      setWatchTargetProduct(null);
      window.dispatchEvent(new CustomEvent('buybound:refresh'));
      setOutcomeBanner({
        tone: 'success',
        title: `Price Alert Activated for ${watchTargetProduct.title}`,
        detail: `BuyBound will monitor competing stores and buy automatically once the price drops to $${targetNum.toFixed(2)} or less (up to $${maxNum.toFixed(2)} max).`,
        ctaTo: '/app/watches',
        ctaLabel: 'Open Price Alerts →',
      });
    } catch (err) {
      setOutcomeBanner({
        tone: 'danger',
        title: 'Could not save price watch',
        detail: toApiError(err).message,
      });
      setWatchTargetProduct(null);
    } finally {
      setSavingWatch(false);
    }
  };

  const categoryTabs = useMemo(() => [ALL_CATEGORIES, ...categories], [categories]);

  const sourceLabel =
    catalogSource === 'channel3' || catalogSource === 'channel3+marketplace-catalog'
      ? 'Live Multi-Store Feeds'
      : catalogSource === 'url-lookup'
        ? 'Direct Link Price Check'
        : catalogSource === 'ai-synthesizer' || catalogSource === 'smart-catalog-synthesizer'
          ? 'Dynamic Multi-Store Discovery'
          : 'Verified Multi-Store Catalog';

  return (
    <div className="space-y-6">
      {/* Header + Universal Product Search */}
      <section className="rounded-3xl border border-brand-border bg-white p-6 sm:p-8 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <span className="inline-flex items-center gap-2 rounded-full bg-brand-peach px-3 py-1 text-xs font-semibold text-brand-coral">
              Universal Deal Marketplace
            </span>
            <h1 className="mt-3 font-display text-3xl font-bold tracking-tight text-brand-charcoal">
              Shop Any Product Across Verified Stores
            </h1>
            <p className="mt-1.5 max-w-2xl text-sm text-brand-charcoal-muted">
              Browse 44+ top products across 8 categories or search for anything in the world — phones, laptops,
              sneakers, appliances, or a pasted product link. Every purchase is checked against your spending limits
              before PayPal pays.
            </p>
          </div>

          {telemetry && (
            <div className="flex flex-wrap items-center gap-2.5 rounded-2xl border border-brand-border bg-brand-ivory px-4 py-3 text-xs">
              <span className="text-brand-charcoal-muted">Active Per-Item Limit:</span>
              <strong className="mono text-sm font-bold text-brand-charcoal">
                {formatUsd(telemetry.maxUnitPrice ?? '260.00')}
              </strong>
              <Link to="/app/safety" className="font-semibold text-brand-coral hover:underline ml-1">
                Edit Limits →
              </Link>
            </div>
          )}
        </div>

        {/* Semantic Search Landmark */}
        <search className="mt-6">
          <form onSubmit={handleSearchSubmit} className="flex flex-col gap-3 sm:flex-row">
            <label htmlFor={searchInputId} className="sr-only">
              Search any product name, brand, or product URL
            </label>
            <div className="relative flex-1">
              <input
                id={searchInputId}
                type="search"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                placeholder="Search any product in the world (e.g. MacBook Air M3, PlayStation 5, Dyson V15, Casio G-Shock) or paste a URL…"
                className="w-full rounded-xl border border-brand-border bg-brand-ivory px-4 py-3 text-sm text-brand-charcoal placeholder:text-brand-charcoal-faint focus:border-brand-coral focus:bg-white focus:outline-none focus:ring-2 focus:ring-brand-coral/20"
              />
            </div>
            <div className="flex items-center gap-2">
              <Button type="submit" variant="primary" className="!px-6">
                Search All Stores
              </Button>
              {(committedQuery || selectedCategory !== ALL_CATEGORIES || priceBracket !== 'all') && (
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => {
                    handleClearSearch();
                    setSelectedCategory(ALL_CATEGORIES);
                    setPriceBracket('all');
                  }}
                >
                  Reset
                </Button>
              )}
            </div>
          </form>
        </search>

        {/* Quick-try search suggestions */}
        <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-brand-charcoal-muted">
          <span className="font-medium text-brand-charcoal-faint">Try searching:</span>
          {['MacBook Air M3', 'PlayStation 5', 'Logitech MX Master 3S', 'iPhone 15 Pro Max', 'Dyson V15'].map((sample) => (
            <button
              key={sample}
              type="button"
              onClick={() => {
                setSearchInput(sample);
                setCommittedQuery(sample);
                const next = new URLSearchParams(searchParams);
                next.set('q', sample);
                setSearchParams(next, { replace: true });
              }}
              className="rounded-full border border-brand-border bg-brand-ivory px-3 py-1 font-medium text-brand-charcoal hover:border-brand-coral hover:bg-brand-peach/40 transition-colors"
            >
              {sample}
            </button>
          ))}
        </div>

        {/* Category Pills + Price Bracket + Sort */}
        <div className="mt-6 space-y-3 border-t border-brand-border pt-5">
          <div className="flex flex-wrap gap-2" role="group" aria-label="Filter products by category">
            {categoryTabs.map((cat) => {
              const active = selectedCategory === cat;
              return (
                <button
                  key={cat}
                  type="button"
                  aria-pressed={active}
                  onClick={() => handleCategorySelect(cat)}
                  className={`rounded-full px-3.5 py-1.5 text-xs font-semibold transition-colors ${active
                    ? 'bg-brand-charcoal text-white shadow-sm'
                    : 'border border-brand-border bg-brand-ivory text-brand-charcoal-muted hover:border-brand-charcoal/40 hover:text-brand-charcoal'
                    }`}
                >
                  {cat}
                </button>
              );
            })}
          </div>

          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex flex-wrap items-center gap-2" role="group" aria-label="Filter by price range">
              {PRICE_BRACKETS.map((b) => {
                const active = priceBracket === b.key;
                return (
                  <button
                    key={b.key}
                    type="button"
                    aria-pressed={active}
                    onClick={() => setPriceBracket(b.key)}
                    className={`rounded-lg px-3 py-1.5 text-xs font-medium transition-colors ${active
                      ? 'bg-brand-peach text-brand-coral font-semibold border border-brand-coral/30'
                      : 'border border-brand-border bg-white text-brand-charcoal-muted hover:text-brand-charcoal'
                      }`}
                  >
                    {b.label}
                  </button>
                );
              })}
            </div>

            <div className="flex items-center gap-2">
              <label htmlFor={sortSelectId} className="text-xs font-medium text-brand-charcoal-muted shrink-0">
                Sort by
              </label>
              <div className="relative">
                <select
                  id={sortSelectId}
                  value={sortBy}
                  onChange={(e) => setSortBy(e.target.value as SortOption)}
                  className="min-h-[38px] appearance-none rounded-xl border border-brand-border bg-white pl-3 pr-8 py-1.5 text-xs font-medium text-brand-charcoal focus:border-brand-coral focus:outline-none focus:ring-2 focus:ring-brand-coral/20"
                >
                  <option value="savings">Biggest Savings</option>
                  <option value="price_asc">Lowest Price</option>
                  <option value="price_desc">Highest Price</option>
                  <option value="best_match">Most Stores Compared</option>
                </select>
                <svg
                  className="pointer-events-none absolute right-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-brand-charcoal-muted"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" d="m6 9 6 6 6-6" />
                </svg>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Checkout / Watch Outcome Feedback Banner */}
      {outcomeBanner && (
        <div
          role={outcomeBanner.tone === 'danger' ? 'alert' : 'status'}
          className={`flex flex-col justify-between gap-4 rounded-2xl border p-5 sm:flex-row sm:items-center ${outcomeBanner.tone === 'success'
            ? 'border-success/30 bg-success-bg text-ink'
            : outcomeBanner.tone === 'warning'
              ? 'border-warning/30 bg-warning-bg text-ink'
              : 'border-danger/30 bg-danger-bg text-ink'
            }`}
        >
          <div>
            <p className="font-display text-sm font-bold">{outcomeBanner.title}</p>
            <p className="mt-1 text-xs text-ink-muted">{outcomeBanner.detail}</p>
          </div>
          <div className="flex flex-wrap items-center gap-2 shrink-0">
            {outcomeBanner.holdSession && (
              <Button
                variant="paypal"
                onClick={() => setCheckoutSession(outcomeBanner.holdSession!)}
              >
                Pay with PayPal Sandbox (Live Popup)
              </Button>
            )}
            {outcomeBanner.approvalUrl && !outcomeBanner.holdSession && (
              <a
                href={outcomeBanner.approvalUrl}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center rounded-lg bg-[#0070BA] px-3.5 py-2 text-xs font-semibold text-white hover:bg-[#005EA6]"
              >
                Approve on PayPal ↗
              </a>
            )}
            {outcomeBanner.ctaTo && outcomeBanner.ctaLabel && (
              <Link
                to={outcomeBanner.ctaTo}
                className="inline-flex items-center rounded-lg border border-brand-border bg-white px-3.5 py-2 text-xs font-semibold text-brand-charcoal hover:bg-brand-ivory"
              >
                {outcomeBanner.ctaLabel}
              </Link>
            )}
          </div>
        </div>
      )}

      {/* Results Telemetry Bar */}
      {!loading && !error && (
        <div className="flex flex-wrap items-center justify-between gap-3 text-xs text-brand-charcoal-muted">
          <p>
            Showing <strong className="text-brand-charcoal">{products.length}</strong>{' '}
            {products.length === 1 ? 'product' : 'products'}
            {committedQuery ? ` matching "${committedQuery}"` : ''}
            {selectedCategory !== ALL_CATEGORIES ? ` in ${selectedCategory}` : ''}
          </p>
          <span className="inline-flex items-center gap-1.5 rounded-full border border-brand-border bg-white px-3 py-1 font-medium text-brand-charcoal">
            <span className="h-2 w-2 rounded-full bg-success-dot" aria-hidden="true" />
            {sourceLabel}
          </span>
        </div>
      )}

      {/* Grid States */}
      {loading && <Spinner label="Comparing live prices across competing stores…" />}
      {error && !loading && <ErrorBanner message={error} onRetry={() => void loadCatalog()} />}

      {!loading && !error && products.length === 0 && (
        <div className="rounded-2xl border border-brand-border bg-white p-10 text-center">
          <h2 className="font-display text-lg font-bold text-brand-charcoal">No products match this price range</h2>
          <p className="mt-1 text-sm text-brand-charcoal-muted">
            Widen the price range or search another product name — BuyBound can look up any product on demand.
          </p>
          <Button
            variant="primary"
            className="mt-4"
            onClick={() => {
              setPriceBracket('all');
              setSelectedCategory(ALL_CATEGORIES);
              handleClearSearch();
            }}
          >
            Show All Marketplace Products
          </Button>
        </div>
      )}

      {!loading && !error && products.length > 0 && (
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {products.map((product, index) => {
            const bestOffer = product.offers[0];
            const directKey = `${bestOffer.channel3ProductId}-${bestOffer.merchantDomain}-direct`;
            const safety = getSafetyBadge(product.bestPrice, product.bestMerchantDomain);
            const hasSavings = Number(product.bestSavings) > 0;
            const imageSrc = resolveProductImageSrc(product.imageUrl, product.category, product.title);

            return (
              <article
                key={product.productId}
                className="flex flex-col overflow-hidden rounded-2xl border border-brand-border bg-white shadow-sm transition-shadow hover:shadow-md"
              >
                <div className="relative h-48 w-full bg-white overflow-hidden border-b border-brand-border">
                  <img
                    src={imageSrc}
                    alt={product.title}
                    loading={index < 2 ? undefined : 'lazy'}
                    {...(index === 0 ? ({ fetchpriority: 'high' } as Record<string, string>) : {})}
                    referrerPolicy="no-referrer"
                    onError={(e) => handleProductImageError(e, product.category, product.title)}
                    className="h-full w-full object-contain p-3 bg-white transition-transform duration-300 hover:scale-[1.03]"
                  />

                  {product.category && (
                    <span className="absolute left-3 top-3 rounded-full bg-white/95 px-2.5 py-1 text-[11px] font-semibold text-brand-charcoal shadow-sm">
                      {product.category}
                    </span>
                  )}
                  {hasSavings && (
                    <span className="absolute right-3 top-3 rounded-full bg-brand-coral px-2.5 py-1 text-[11px] font-bold text-white shadow-sm">
                      Save {formatUsd(product.bestSavings)}
                    </span>
                  )}
                </div>

                <div className="flex flex-1 flex-col p-5">
                  <div className="flex items-center justify-between gap-2 text-[11px] font-semibold uppercase tracking-wide text-brand-charcoal-faint">
                    <span>{product.brand || 'Retail'}</span>
                    <span>{product.storeCount} stores compared</span>
                  </div>

                  <h2 className="mt-1.5 font-display text-base font-bold leading-snug text-brand-charcoal line-clamp-2 min-h-[2.6rem]">
                    {product.title}
                  </h2>

                  {product.description && (
                    <p className="mt-1.5 text-xs leading-relaxed text-brand-charcoal-muted line-clamp-2 min-h-[2.25rem]">
                      {product.description}
                    </p>
                  )}

                  <div className="mt-4 flex items-baseline justify-between gap-2 border-t border-brand-border/70 pt-3">
                    <div>
                      <div className="flex items-baseline gap-2">
                        <span className="mono font-display text-2xl font-bold text-brand-charcoal">
                          {formatUsd(product.bestPrice)}
                        </span>
                        {hasSavings && (
                          <span className="mono text-xs text-brand-charcoal-faint line-through">
                            {formatUsd(product.baselinePrice)}
                          </span>
                        )}
                      </div>
                      <p className="text-xs text-brand-charcoal-muted">
                        Best price at <strong className="text-brand-charcoal">{product.bestMerchantName}</strong>
                      </p>
                    </div>
                    {hasSavings && Number(product.savingsPercent) > 0 && (
                      <span className="rounded-lg bg-success-bg px-2 py-1 text-xs font-bold text-success">
                        -{Number(product.savingsPercent).toFixed(0)}%
                      </span>
                    )}
                  </div>

                  <div className="mt-3">
                    <Badge tone={safety.tone} dot>
                      {safety.label}
                    </Badge>
                  </div>

                  <div className="mt-4 flex flex-col gap-2 pt-1">
                    <Button
                      variant="paypal"
                      loading={buyingKey === directKey}
                      onClick={() => void handleBuyOffer(bestOffer, true)}
                    >
                      Direct Buy Now (PayPal · {formatUsd(product.bestPrice)})
                    </Button>
                    <div className="grid grid-cols-2 gap-2">
                      <Button
                        variant="secondary"
                        className="!min-h-[36px] !px-2.5 !text-xs"
                        onClick={() => openWatchModal(product)}
                      >
                        Send to AI Agent / Watch
                      </Button>
                      <Button
                        variant="ghost"
                        className="!min-h-[36px] !px-2.5 !text-xs"
                        onClick={() => setCompareProduct(product)}
                      >
                        Compare Offers
                      </Button>
                    </div>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      )}

      {/* Multi-Store Comparison Modal */}
      <dialog
        ref={compareDialogRef}
        aria-labelledby={compareTitleId}
        onCancel={(e) => {
          e.preventDefault();
          setCompareProduct(null);
        }}
        className="w-full max-w-2xl rounded-2xl border border-brand-border bg-white p-0 text-ink shadow-xl backdrop:bg-slate-900/45"
      >
        {compareProduct && (
          <div>
            <div className="flex items-start justify-between gap-4 border-b border-brand-border px-6 py-4">
              <div className="flex items-start gap-3.5 min-w-0">
                <img
                  src={resolveProductImageSrc(compareProduct.imageUrl, compareProduct.category, compareProduct.title)}
                  alt={compareProduct.title}
                  referrerPolicy="no-referrer"
                  onError={(e) => handleProductImageError(e, compareProduct.category, compareProduct.title)}
                  className="h-14 w-14 shrink-0 rounded-xl border border-brand-border bg-white object-cover"
                />
                <div className="min-w-0">
                  <p className="text-xs font-semibold uppercase tracking-wide text-brand-coral">
                    Multi-Store Price Comparison
                  </p>
                  <h2 id={compareTitleId} className="mt-0.5 font-display text-lg font-bold text-ink">
                    {compareProduct.title}
                  </h2>
                  <p className="mt-0.5 text-xs text-ink-muted">
                    Retail reference: <span className="mono">{formatUsd(compareProduct.baselinePrice)}</span> across{' '}
                    {compareProduct.storeCount} merchants
                  </p>
                </div>
              </div>
              <button
                type="button"
                aria-label="Close store comparison"
                onClick={() => setCompareProduct(null)}
                className="rounded-lg px-2.5 py-1 text-sm text-ink-muted hover:bg-brand-ivory hover:text-ink"
              >
                ✕
              </button>
            </div>

            <div className="divide-y divide-brand-border px-6 py-3">
              {compareProduct.offers.map((offer) => {
                const safety = getSafetyBadge(offer.currentPrice, offer.merchantDomain);
                const rowKey = `${offer.channel3ProductId}-${offer.merchantDomain}`;
                return (
                  <div
                    key={rowKey}
                    className="flex flex-col justify-between gap-3 py-3.5 sm:flex-row sm:items-center"
                  >
                    <div>
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-semibold text-sm text-ink">{offer.merchantName}</span>
                        <span className="mono text-xs text-ink-faint">{offer.merchantDomain}</span>
                        <Badge tone={safety.tone}>{safety.label}</Badge>
                      </div>
                      <p className="mt-1 text-xs text-ink-muted">
                        Condition: <strong className="capitalize">{offer.condition}</strong>
                        {Number(offer.verifiedSavings) > 0 && (
                          <> · Save {formatUsd(offer.verifiedSavings)} vs MSRP</>
                        )}
                      </p>
                    </div>

                    <div className="flex flex-wrap items-center gap-2 shrink-0">
                      <span className="mono font-display text-lg font-bold text-ink">
                        {formatUsd(offer.currentPrice)}
                      </span>
                      <Button
                        variant="paypal"
                        className="!min-h-[36px] !px-3 !text-xs"
                        loading={buyingKey === `${rowKey}-direct`}
                        onClick={() => void handleBuyOffer(offer, true)}
                      >
                        Direct Buy (PayPal)
                      </Button>
                      <Button
                        variant="secondary"
                        className="!min-h-[36px] !px-3 !text-xs"
                        loading={buyingKey === `${rowKey}-agent`}
                        onClick={() => void handleBuyOffer(offer, false)}
                      >
                        Agent Buy
                      </Button>
                    </div>
                  </div>
                );
              })}
            </div>

            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-brand-border bg-brand-ivory px-6 py-4 rounded-b-2xl">
              <Link
                to={`/app/offers?q=${encodeURIComponent(compareProduct.title)}`}
                onClick={() => setCompareProduct(null)}
                className="text-xs font-semibold text-brand-coral hover:underline"
              >
                Open in Compare Store Offers Grid →
              </Link>
              <Button variant="secondary" className="!min-h-[36px] !px-4 !text-xs" onClick={() => setCompareProduct(null)}>
                Close
              </Button>
            </div>
          </div>
        )}
      </dialog>

      {/* Set Auto-Buy Watch Dialog */}
      <Dialog
        open={Boolean(watchTargetProduct)}
        title="Set Automatic Price-Drop Watch"
        confirmLabel="Activate Price Watch"
        confirmVariant="primary"
        confirming={savingWatch}
        onConfirm={() => void handleSaveWatch()}
        onClose={() => setWatchTargetProduct(null)}
      >
        {watchTargetProduct && (
          <div className="space-y-4">
            <div className="rounded-xl border border-brand-border bg-brand-ivory p-3.5 text-xs">
              <p className="font-semibold text-ink">{watchTargetProduct.title}</p>
              <p className="mt-1 text-ink-muted">
                Current best price: <strong className="mono">{formatUsd(watchTargetProduct.bestPrice)}</strong> at{' '}
                {watchTargetProduct.bestMerchantName}
              </p>
            </div>

            <TextField
              id="shop-watch-target"
              label="Target Auto-Buy Price ($ USD)"
              type="number"
              min="1"
              step="0.01"
              value={watchTargetPrice}
              onChange={(e) => setWatchTargetPrice(e.target.value)}
              hint="BuyBound checks competing stores and buys only at or below this price."
            />

            <TextField
              id="shop-watch-limit"
              label="Hard Max Item Spending Cap ($ USD)"
              type="number"
              min="1"
              step="0.01"
              value={watchMaxLimit}
              onChange={(e) => setWatchMaxLimit(e.target.value)}
              hint="Even with taxes or fees, PayPal can never be charged above this hard cap."
            />
          </div>
        )}
      </Dialog>

      <PayPalCheckoutModal
        session={checkoutSession}
        vaultConnected={Boolean(telemetry?.vaultConnected)}
        onClose={() => setCheckoutSession(null)}
        onCaptured={({ intent, capture, message }) => {
          const paidTitle = checkoutSession?.title;
          setCheckoutSession(null);
          setReceiptTitle(paidTitle || undefined);
          setReceiptPayerEmail(capture?.payerEmail ?? telemetry?.paypalPayerEmail);
          setReceiptPayerName(capture?.payerName ?? telemetry?.paypalPayerName);
          setReceiptNewBalance(capture?.newSandboxBalance ?? undefined);
          setReceiptIntent(intent);
          setOutcomeBanner({
            tone: 'success',
            title: `Purchased ${paidTitle ?? 'Item'} via PayPal Sandbox`,
            detail: message,
            ctaTo: '/app/purchases',
            ctaLabel: 'View All Orders & Receipts →',
          });
        }}
      />

      <VerifiedReceiptModal
        intent={receiptIntent}
        productTitle={receiptTitle}
        payerEmail={receiptPayerEmail}
        payerName={receiptPayerName}
        newBalance={receiptNewBalance}
        onClose={() => setReceiptIntent(null)}
      />

      <GuardianProofModal target={proofTarget} onClose={() => setProofTarget(null)} />
    </div>
  );
}
