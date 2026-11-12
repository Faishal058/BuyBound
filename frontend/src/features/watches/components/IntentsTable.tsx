import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { AgGridReact } from 'ag-grid-react';
import type { ColDef } from 'ag-grid-community';
import { Badge } from '../../../components/ui/Badge';
import { INTENT_STATUS_META, formatDateTime, formatMoney, shortId } from '../../shared/meta';
import type { PurchaseIntent } from '../../../services/api/types';

/** Purchase intents telemetry grid rendered with AG Grid. */
export function IntentsTable({ intents }: { intents: PurchaseIntent[] }) {
  const columnDefs = useMemo<ColDef<PurchaseIntent>[]>(
    () => [
      {
        headerName: 'Intent',
        field: 'id',
        width: 120,
        cellRenderer: (params: { value: string; data: PurchaseIntent }) => (
          <Link
            to={`/intents/${params.data.id}`}
            className="mono text-primary hover:underline font-semibold"
            title={params.data.id}
          >
            {shortId(params.value)}
          </Link>
        ),
      },
      {
        headerName: 'State Machine Status',
        field: 'status',
        width: 200,
        cellRenderer: (params: { value: PurchaseIntent['status'] }) => {
          const meta = INTENT_STATUS_META[params.value];
          return <Badge tone={meta.tone}>{meta.label}</Badge>;
        },
      },
      { headerName: 'Merchant', field: 'merchantId', width: 140 },
      {
        headerName: 'SKU / Variant',
        width: 150,
        valueGetter: (params) => {
          const sku = params.data?.canonicalSku || '—';
          const variant = params.data?.variant ? ` (${params.data.variant})` : '';
          return `${sku}${variant}`;
        },
      },
      {
        headerName: 'Current Quote',
        width: 140,
        cellRenderer: (params: { data: PurchaseIntent }) => {
          if (!params.data) return null;
          const snap = params.data.offerSnapshot;
          if (snap) {
            return (
              <span className="font-medium text-ink">
                {formatMoney(snap.unitPrice, snap.currency)}
                <span className="text-xs text-muted"> / unit</span>
              </span>
            );
          }
          return <span>{formatMoney(params.data.maxTotal, params.data.currency)}</span>;
        },
      },
      {
        headerName: 'Max Total',
        width: 130,
        valueGetter: (params) =>
          params.data ? formatMoney(params.data.maxTotal, params.data.currency) : '',
      },
      {
        headerName: 'Source',
        width: 120,
        cellRenderer: (params: { data: PurchaseIntent }) => {
          const src = params.data?.offerSnapshot?.source || 'direct';
          const tone = src === 'channel3' ? 'info' : src === 'fixture' ? 'warning' : 'neutral';
          return <Badge tone={tone}>{src}</Badge>;
        },
      },
      {
        headerName: 'PayPal Order ID',
        field: 'providerOrderId',
        width: 160,
        cellRenderer: (params: { value?: string }) =>
          params.value ? (
            <span className="mono text-xs text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200">
              {params.value}
            </span>
          ) : (
            <span className="text-muted text-xs">—</span>
          ),
      },
      {
        headerName: 'Evidence Hash',
        field: 'evidenceHash',
        width: 130,
        cellRenderer: (params: { value?: string }) =>
          params.value ? (
            <span className="mono text-xs text-muted" title={params.value}>
              {shortId(params.value)}
            </span>
          ) : (
            <span className="text-muted text-xs">—</span>
          ),
      },
      {
        headerName: 'Updated',
        field: 'updatedAt',
        width: 170,
        valueFormatter: (params: { value: string }) => formatDateTime(params.value),
      },
    ],
    [],
  );

  return (
    <div className="ag-theme-quartz shadow-sm rounded-lg border border-border" style={{ height: 380, width: '100%' }}>
      <AgGridReact<PurchaseIntent>
        rowData={intents}
        columnDefs={columnDefs}
        pagination
        paginationPageSize={10}
        domLayout="autoHeight"
      />
    </div>
  );
}
