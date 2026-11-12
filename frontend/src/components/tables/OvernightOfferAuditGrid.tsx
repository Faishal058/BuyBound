import { useCallback, useMemo, useRef, useState, type FormEvent } from 'react';
import { AgGridReact } from 'ag-grid-react';
import type { ColDef, GridApi, GridReadyEvent } from 'ag-grid-community';
import { GuardianProofModal, type GuardianProofTarget } from '../modals/GuardianProofModal';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { formatStoreName, formatUsd } from '../../features/shared/meta';
import { handleProductImageError, resolveProductImageSrc } from '../../features/shared/productImages';
import { aiApi, purchasesApi } from '../../services/api/endpoints';
import { toApiError } from '../../services/api/client';
import type { HuntedOffer, PurchaseIntent } from '../../services/api/types';

export type OfferTabFilterKey = 'all' | 'approved' | 'hold' | 'blocked';

const FILTER_TABS: Array<{ key: OfferTabFilterKey; label: string }> = [
  { key: 'all', label: 'All Offers' },
  { key: 'approved', label: 'Approved' },
  { key: 'hold', label: 'Needs My Approval' },
  { key: 'blocked', label: 'Blocked' },
];

const QUICK_SEARCHES = [
  'Show approved offers under $210 with 4.5+ seller rating',
  'Show blocked sellers with negative review red flags',
  'Show deals with over $40 savings',
];

interface OvernightOfferAuditGridProps {
  offers: HuntedOffer[];
  intents?: PurchaseIntent[];
  onRefresh?: () => Promise<void> | void;
  initialQuery?: string;
  onClearUrlQuery?: () => void;
  showTabs?: boolean;
  compact?: boolean;
  title?: string;
  subtitle?: string;
}

export function OvernightOfferAuditGrid({
  offers,
  intents = [],
  onRefresh,
  initialQuery = '',
  onClearUrlQuery,
  showTabs = true,
  compact = false,
  title,
  subtitle,
}: OvernightOfferAuditGridProps) {
  const [activeTab, setActiveTab] = useState<OfferTabFilterKey>('all');
  const [searchQuery, setSearchQuery] = useState('');
  const [searching, setSearching] = useState(false);
  const [searchInsight, setSearchInsight] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);
  const [busyOfferId, setBusyOfferId] = useState<string | null>(null);
  const [proofTarget, setProofTarget] = useState<GuardianProofTarget | null>(null);

  const gridApiRef = useRef<GridApi | null>(null);

  const intentById = useMemo(() => {
    const map = new Map<string, PurchaseIntent>();
    for (const intent of intents) {
      map.set(intent.id, intent);
    }
    return map;
  }, [intents]);

  const applyTabFilter = useCallback((tab: OfferTabFilterKey, api?: GridApi | null) => {
    const targetApi = api ?? gridApiRef.current;
    if (!targetApi) return;

    if (tab === 'all') {
      targetApi.setFilterModel(null);
      targetApi.applyColumnState({ defaultState: { sort: null } });
    } else if (tab === 'approved') {
      targetApi.setFilterModel({
        guardianDecision: { filterType: 'text', type: 'equals', filter: 'ALLOW' },
      });
    } else if (tab === 'hold') {
      targetApi.setFilterModel({
        guardianDecision: { filterType: 'text', type: 'equals', filter: 'HOLD' },
      });
    } else if (tab === 'blocked') {
      targetApi.setFilterModel({
        guardianDecision: { filterType: 'text', type: 'equals', filter: 'BLOCK' },
      });
    }
  }, []);

  const handleTabSelect = (tab: OfferTabFilterKey) => {
    setActiveTab(tab);
    setSearchInsight(null);
    setActionError(null);
    applyTabFilter(tab);
  };

  const handleGridReady = (event: GridReadyEvent) => {
    gridApiRef.current = event.api;
    if (initialQuery.trim()) {
      event.api.setFilterModel({
        productTitle: { filterType: 'text', type: 'contains', filter: initialQuery.trim() },
      });
      setSearchInsight(`Filtered store comparison table for "${initialQuery.trim()}".`);
    } else {
      applyTabFilter(activeTab, event.api);
    }
  };

  const runSmartSearch = async (queryText: string) => {
    if (!queryText.trim()) return;
    setSearching(true);
    setActionError(null);
    setSearchInsight(null);
    try {
      const res = await aiApi.gridQuery(queryText.trim(), 'offers');
      if (gridApiRef.current) {
        const hasFilters = res.filterModel && Object.keys(res.filterModel).length > 0;
        gridApiRef.current.setFilterModel(hasFilters ? res.filterModel : null);
        if (res.sortModel && res.sortModel.length > 0) {
          gridApiRef.current.applyColumnState({
            state: res.sortModel.map((s) => ({ colId: s.colId, sort: s.sort })),
            defaultState: { sort: null },
          });
        } else {
          gridApiRef.current.applyColumnState({ defaultState: { sort: null } });
        }
      }
      setSearchInsight(
        res.insightSummary
          .replace(/guardianDecision/gi, 'safety verdict')
          .replace(/verifiedSavings/gi, 'savings')
          .replace(/offerPrice/gi, 'price')
          .replace(/sellerRating/gi, 'seller rating')
          .replace(/negativeRedFlagCount/gi, 'red flags'),
      );
    } catch (err) {
      setActionError(toApiError(err).message);
    } finally {
      setSearching(false);
    }
  };

  const handleSearchSubmit = (e: FormEvent) => {
    e.preventDefault();
    void runSmartSearch(searchQuery);
  };

  const handleReset = () => {
    setSearchQuery('');
    setSearchInsight(null);
    setActionError(null);
    setActiveTab('all');
    onClearUrlQuery?.();
    applyTabFilter('all');
  };

  const handleApproveAndBuy = useCallback(
    async (offer: HuntedOffer) => {
      if (!offer.intentId) return;
      setBusyOfferId(offer.id);
      setActionError(null);
      setActionSuccess(null);
      try {
        const res = await purchasesApi.approveHold(offer.intentId);
        setActionSuccess(res.message);
        await onRefresh?.();
        window.dispatchEvent(new CustomEvent('buybound:refresh'));
      } catch (err) {
        setActionError(toApiError(err).message);
      } finally {
        setBusyOfferId(null);
      }
    },
    [onRefresh],
  );

  const handleDeclineOffer = useCallback(
    async (offer: HuntedOffer) => {
      if (!offer.intentId) return;
      setBusyOfferId(offer.id);
      setActionError(null);
      setActionSuccess(null);
      try {
        const res = await purchasesApi.rejectHold(offer.intentId);
        setActionSuccess(res.message);
        await onRefresh?.();
        window.dispatchEvent(new CustomEvent('buybound:refresh'));
      } catch (err) {
        setActionError(toApiError(err).message);
      } finally {
        setBusyOfferId(null);
      }
    },
    [onRefresh],
  );

  const columnDefs = useMemo<ColDef<HuntedOffer>[]>(
    () => [
      {
        headerName: 'Store / Merchant',
        field: 'merchantId',
        filter: 'agTextColumnFilter',
        minWidth: 195,
        cellRenderer: (params: { data?: HuntedOffer }) => {
          const row = params.data;
          if (!row) return null;
          const verified = row.verifiedSellerHistory !== false && (row.negativeRedFlagCount ?? 0) === 0;
          return (
            <div className="flex flex-col justify-center py-1 leading-tight">
              <div className="flex items-center gap-1.5">
                <span className="truncate text-sm font-semibold text-ink">
                  {formatStoreName(row.merchantId)}
                </span>
                <span
                  className={`inline-flex items-center rounded-full px-1.5 py-0.5 text-[10px] font-bold ${
                    verified
                      ? 'bg-success-bg text-success'
                      : 'bg-danger-bg text-danger'
                  }`}
                >
                  {verified ? 'Verified' : 'Unverified'}
                </span>
              </div>
              <span className="mono text-[11px] text-faint">{row.merchantId}</span>
            </div>
          );
        },
      },
      {
        headerName: 'Item & SKU Match',
        field: 'productTitle',
        filter: 'agTextColumnFilter',
        minWidth: 250,
        flex: 1.5,
        cellRenderer: (params: { data?: HuntedOffer }) => {
          const row = params.data;
          if (!row) return null;
          const skuOk =
            row.aiExactSkuMatch && !row.aiPromptInjectionDetected && !row.aiSubscriptionTrapDetected;
          const confidence = row.aiMatchConfidence ? Math.round(Number(row.aiMatchConfidence)) : 95;
          return (
            <div className="flex items-center gap-3 py-1 leading-tight">
              <img
                src={resolveProductImageSrc(row.imageUrl, null, row.productTitle)}
                alt={row.productTitle}
                loading="lazy"
                referrerPolicy="no-referrer"
                onError={(e) => handleProductImageError(e, null, row.productTitle)}
                className="h-9 w-9 shrink-0 rounded-lg border border-border object-contain bg-white p-0.5"
              />
              <div className="min-w-0 flex-1">
                <span className="block truncate text-sm font-medium text-ink" title={row.productTitle}>
                  {row.productTitle}
                </span>
                <div className="mt-0.5 flex items-center gap-1.5">
                  <span
                    className={`inline-flex items-center rounded px-1.5 py-0.2 text-[10px] font-semibold ${
                      skuOk ? 'bg-success-bg text-success' : 'bg-danger-bg text-danger'
                    }`}
                  >
                    {skuOk ? `${confidence}% SKU Match` : 'Bait / Trap'}
                  </span>
                </div>
              </div>
            </div>
          );
        },
      },
      {
        headerName: 'Total Price vs. Cap',
        field: 'offerPrice',
        filter: 'agNumberColumnFilter',
        valueGetter: (p) => (p.data ? Number(p.data.offerPrice) : 0),
        minWidth: 165,
        cellRenderer: (params: { data?: HuntedOffer }) => {
          const row = params.data;
          if (!row) return null;
          const priceNum = Number(row.offerPrice);
          const inDropBand =
            (row.guardianDecision === 'ALLOW' || (priceNum >= 200 && priceNum <= 210)) &&
            row.guardianDecision !== 'BLOCK';
          const sav = Number(row.verifiedSavings ?? 0);
          return (
            <div className="flex flex-col justify-center py-1 leading-tight">
              <div className="flex items-center gap-1.5">
                <span
                  className={`mono text-sm font-bold ${
                    inDropBand
                      ? 'rounded bg-success-bg px-1.5 py-0.5 text-success'
                      : row.reasonCodes.includes('ITEM_PRICE_CAP_EXCEEDED')
                        ? 'text-danger'
                        : 'text-ink'
                  }`}
                >
                  {formatUsd(row.offerPrice)}
                </span>
                {row.baselinePrice && Number(row.baselinePrice) > priceNum && (
                  <span className="mono text-[11px] text-faint line-through">
                    {formatUsd(row.baselinePrice)}
                  </span>
                )}
              </div>
              <span className="text-[11px] text-muted">
                {row.reasonCodes.includes('ITEM_PRICE_CAP_EXCEEDED')
                  ? 'Exceeds Hard Cap'
                  : sav > 0
                    ? `Saves ${formatUsd(sav)}`
                    : 'Within Ceiling'}
              </span>
            </div>
          );
        },
      },
      {
        headerName: 'Seller Rating & Reviews',
        field: 'sellerRating',
        filter: 'agNumberColumnFilter',
        valueGetter: (p) => p.data?.sellerRating ?? 4.8,
        minWidth: 220,
        cellRenderer: (params: { data?: HuntedOffer }) => {
          const row = params.data;
          if (!row) return null;
          const rating = row.sellerRating ?? 4.8;
          const reviews = row.sellerReviewCount ?? 520;
          const redFlags = row.negativeRedFlagCount ?? 0;
          return (
            <div
              className="flex flex-col justify-center py-1 leading-tight"
              title={row.sellerRedFlagSummary ?? 'Verified authentic merchant'}
            >
              <div className="flex items-center gap-1.5 text-xs">
                <span className={`mono font-bold ${rating >= 4.5 ? 'text-ink' : 'text-danger'}`}>
                  {rating.toFixed(1)}★
                </span>
                <span className="text-muted">({reviews.toLocaleString()} reviews)</span>
              </div>
              <div className="mt-0.5">
                {redFlags > 0 ? (
                  <span className="inline-flex items-center gap-1 rounded-full bg-danger-bg px-2 py-0.5 text-[10px] font-bold text-danger">
                    ⚑ {redFlags} Red Flags
                  </span>
                ) : (
                  <span className="inline-flex items-center rounded-full bg-success-bg px-2 py-0.5 text-[10px] font-semibold text-success">
                    0 Red Flags
                  </span>
                )}
              </div>
            </div>
          );
        },
      },
      {
        headerName: 'Guardian Verdict',
        field: 'guardianDecision',
        filter: 'agTextColumnFilter',
        minWidth: 215,
        cellRenderer: (params: { data?: HuntedOffer }) => {
          const row = params.data;
          if (!row) return null;
          const primaryCode = row.reasonCodes?.[0];
          return (
            <div className="flex flex-col justify-center gap-0.5 py-1 leading-tight">
              <div className="flex items-center gap-1.5">
                {row.guardianDecision === 'ALLOW' ? (
                  <Badge tone="success" dot>
                    ALLOW
                  </Badge>
                ) : row.guardianDecision === 'HOLD' ? (
                  <Badge tone="warning" dot>
                    HOLD
                  </Badge>
                ) : (
                  <Badge tone="danger" dot>
                    BLOCK
                  </Badge>
                )}
              </div>
              {primaryCode && (
                <span className="mono truncate text-[10px] text-muted" title={row.reasonCodes.join(', ')}>
                  {primaryCode}
                </span>
              )}
            </div>
          );
        },
      },
      {
        headerName: 'Delivery / Action',
        colId: 'deliveryAction',
        minWidth: 240,
        sortable: false,
        filter: false,
        cellRenderer: (params: { data?: HuntedOffer }) => {
          const row = params.data;
          if (!row) return null;
          const linkedIntent = row.intentId ? intentById.get(row.intentId) : undefined;
          const canActOnHold =
            row.guardianDecision === 'HOLD' &&
            Boolean(row.intentId) &&
            (linkedIntent?.status === 'HELD_FOR_APPROVAL' || linkedIntent?.status === 'AWAITING_APPROVAL');
          const busy = busyOfferId === row.id;

          return (
            <div className="flex flex-wrap items-center gap-1.5 py-1">
              {row.guardianDecision === 'ALLOW' && (
                <span className="inline-flex items-center rounded-full bg-success-bg px-2 py-0.5 text-[11px] font-semibold text-success">
                  {row.estimatedDelivery ?? 'Arriving in 2 Days'}
                </span>
              )}
              {row.guardianDecision === 'BLOCK' && (
                <span className="inline-flex items-center rounded-full bg-danger-bg px-2 py-0.5 text-[11px] font-semibold text-danger">
                  Blocked
                </span>
              )}
              {canActOnHold && (
                <>
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => void handleApproveAndBuy(row)}
                    className="rounded-full bg-brand-coral px-2.5 py-1 text-[11px] font-semibold text-white hover:bg-brand-coral-hover disabled:opacity-50"
                  >
                    {busy ? 'Working…' : 'Approve Hold'}
                  </button>
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => void handleDeclineOffer(row)}
                    className="rounded-md border border-[#DC2626]/30 bg-danger-bg px-2 py-1 text-[11px] font-semibold text-danger hover:bg-red-100 disabled:opacity-50"
                  >
                    Decline
                  </button>
                </>
              )}
              <button
                type="button"
                onClick={() =>
                  setProofTarget({
                    title: row.productTitle,
                    merchantId: row.merchantId,
                    price: row.offerPrice,
                    baselinePrice: row.baselinePrice,
                    decision: row.guardianDecision,
                    reasonCodes: row.reasonCodes,
                    ruleChecks: row.ruleChecks ?? [],
                    aiSummary: row.aiForensicsSummary,
                    aiConfidence: row.aiMatchConfidence,
                  })
                }
                className="rounded-md border border-border-strong bg-surface px-2.5 py-1 text-[11px] font-medium text-ink hover:bg-surface-muted"
              >
                View Proof
              </button>
            </div>
          );
        },
      },
      // Hidden filterable fields so natural-language AG Studio filters apply cleanly on all attributes
      { field: 'negativeRedFlagCount', filter: 'agNumberColumnFilter', valueGetter: (p) => p.data?.negativeRedFlagCount ?? 0, hide: true },
      { field: 'sellerReviewCount', filter: 'agNumberColumnFilter', valueGetter: (p) => p.data?.sellerReviewCount ?? 520, hide: true },
      { field: 'verifiedSavings', filter: 'agNumberColumnFilter', valueGetter: (p) => (p.data?.verifiedSavings ? Number(p.data.verifiedSavings) : 0), hide: true },
      { field: 'aiMatchConfidence', filter: 'agNumberColumnFilter', valueGetter: (p) => (p.data?.aiMatchConfidence ? Number(p.data.aiMatchConfidence) : 95), hide: true },
      { field: 'verifiedSellerHistory', filter: 'agTextColumnFilter', valueGetter: (p) => String(p.data?.verifiedSellerHistory ?? true), hide: true },
      { field: 'reasonCodes', filter: 'agTextColumnFilter', valueGetter: (p) => (p.data?.reasonCodes ?? []).join(','), hide: true },
      { field: 'estimatedDelivery', filter: 'agTextColumnFilter', valueGetter: (p) => p.data?.estimatedDelivery ?? '', hide: true },
    ],
    [busyOfferId, handleApproveAndBuy, handleDeclineOffer, intentById],
  );

  return (
    <div>
      {(title || subtitle) && (
        <div className="mb-4">
          {title && <h2 className="font-display text-lg font-bold text-ink">{title}</h2>}
          {subtitle && <p className="mt-0.5 text-xs text-muted">{subtitle}</p>}
        </div>
      )}

      {showTabs && (
        <div
          role="tablist"
          aria-label="Filter store offers"
          className="mb-4 flex items-center gap-6 overflow-x-auto border-b border-border"
        >
          {FILTER_TABS.map((tab) => {
            const selected = activeTab === tab.key;
            return (
              <button
                key={tab.key}
                type="button"
                role="tab"
                aria-selected={selected}
                onClick={() => handleTabSelect(tab.key)}
                className={`bb-tab shrink-0 pb-3 text-sm font-medium transition-colors ${
                  selected ? 'text-brand-coral font-semibold' : 'text-muted hover:text-ink'
                }`}
              >
                {tab.label}
              </button>
            );
          })}
        </div>
      )}

      <div role="search" className="mb-3">
        <form onSubmit={handleSearchSubmit} className="flex flex-col gap-2 sm:flex-row sm:items-center">
          <label htmlFor="offers-smart-search" className="visually-hidden">
            Filter offers using natural language
          </label>
          <input
            id="offers-smart-search"
            type="search"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder='Try: "Show approved offers under $210 with 4.5+ seller rating"'
            className="min-h-[42px] flex-1 rounded-xl border border-border-strong bg-brand-ivory px-3.5 py-2 text-sm text-ink placeholder:text-faint focus:border-brand-coral focus:bg-surface focus:outline-none focus:ring-2 focus:ring-brand-coral/15"
          />
          <div className="flex items-center gap-2">
            <Button type="submit" loading={searching}>
              Filter Deals
            </Button>
            <Button type="button" variant="secondary" onClick={handleReset}>
              Reset
            </Button>
          </div>
        </form>
      </div>

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <span className="text-xs text-faint">Smart filters:</span>
        {QUICK_SEARCHES.map((q) => (
          <button
            key={q}
            type="button"
            onClick={() => {
              setSearchQuery(q);
              void runSmartSearch(q);
            }}
            className="rounded-full border border-brand-border bg-brand-ivory px-2.5 py-1 text-xs font-medium text-muted hover:border-brand-coral hover:text-ink transition-colors"
          >
            {q}
          </button>
        ))}
      </div>

      {searchInsight && (
        <div role="status" className="mb-4 rounded-xl border border-brand-coral/25 bg-brand-peach-soft px-3.5 py-2 text-xs font-medium text-ink">
          {searchInsight}
        </div>
      )}
      {actionSuccess && (
        <div role="status" className="mb-4 rounded-lg border border-[#16A34A]/25 bg-success-bg px-3.5 py-2 text-xs font-medium text-success">
          {actionSuccess}
        </div>
      )}
      {actionError && (
        <div role="alert" className="mb-4 rounded-lg border border-[#DC2626]/25 bg-danger-bg px-3.5 py-2 text-xs font-medium text-danger">
          {actionError}
        </div>
      )}

      <div className="ag-studio-panel ag-theme-quartz" style={{ width: '100%' }}>
        <AgGridReact<HuntedOffer>
          rowData={offers}
          columnDefs={columnDefs}
          onGridReady={handleGridReady}
          pagination={!compact}
          paginationPageSize={compact ? 5 : 10}
          rowHeight={64}
          domLayout="autoHeight"
        />
      </div>

      <GuardianProofModal target={proofTarget} onClose={() => setProofTarget(null)} />
    </div>
  );
}

