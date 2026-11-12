import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { AgGridReact } from 'ag-grid-react';
import type { ColDef } from 'ag-grid-community';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { Card } from '../../../components/ui/Card';
import { EmptyState } from '../../../components/ui/EmptyState';
import { ErrorBanner } from '../../../components/ui/ErrorBanner';
import { SelectField } from '../../../components/ui/SelectField';
import { Spinner } from '../../../components/ui/Spinner';
import { useResource } from '../../../hooks/useResource';
import { auditApi, type AuditListParams } from '../../../services/api/endpoints';
import { formatDateTime } from '../../shared/meta';
import type { AuditEvent } from '../../../services/api/types';

const LIMIT_OPTIONS = [25, 50, 100];

export function AuditPage() {
  const [limitInput, setLimitInput] = useState(50);
  const [applied, setApplied] = useState<AuditListParams>({ limit: 50 });

  const { data: events, loading, error, reload } = useResource(
    () => auditApi.list(applied),
    [applied],
  );

  const columnDefs = useMemo<ColDef<AuditEvent>[]>(
    () => [
      {
        headerName: 'Time',
        field: 'occurredAt',
        width: 170,
        valueFormatter: (params: { value: string }) => formatDateTime(params.value),
      },
      {
        headerName: 'Activity',
        field: 'eventType',
        width: 220,
        cellRenderer: (params: { value: string }) => {
          const val = params.value ?? '';
          const isDanger = val.includes('blocked') || val.includes('kill_switch');
          const isSuccess = val.includes('captured') || val.includes('restored') || val.includes('connected');
          const isWarning = val.includes('hold') || val.includes('paused');
          const tone = isDanger ? 'danger' : isSuccess ? 'success' : isWarning ? 'warning' : 'info';
          return <Badge tone={tone}>{val.replace(/[._]/g, ' ')}</Badge>;
        },
      },
      {
        headerName: 'Summary',
        field: 'summary',
        flex: 1,
        minWidth: 280,
        cellRenderer: (params: { value?: string }) => (
          <span className="text-sm text-ink" title={params.value}>
            {params.value}
          </span>
        ),
      },
    ],
    [],
  );

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <Link to="/app/safety" className="text-xs font-medium text-brand-coral hover:underline">
            ← Back to Wallet &amp; Safety Settings
          </Link>
          <h1 className="mt-1 font-display text-2xl font-bold text-ink">Safety &amp; Purchase Activity Log</h1>
          <p className="mt-1 text-sm text-muted">
            Full record of price checks, blocked listings, PayPal authorizations, and kill-switch events.
          </p>
        </div>
        <div className="flex items-end gap-3">
          <SelectField
            id="audit-limit"
            label="Rows"
            value={String(limitInput)}
            onChange={(e) => {
              const next = Number(e.target.value);
              setLimitInput(next);
              setApplied({ limit: next });
            }}
          >
            {LIMIT_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </SelectField>
          <Button variant="secondary" onClick={reload} loading={loading}>
            Refresh
          </Button>
        </div>
      </div>

      {loading && events === null && <Spinner label="Loading activity log…" />}
      {error && <ErrorBanner message={error.message} onRetry={reload} />}
      {!loading && !error && events && events.length === 0 && (
        <EmptyState title="No activity yet" description="Safety checks and PayPal events will appear here." />
      )}
      {!error && events && events.length > 0 && (
        <Card>
          <div className="ag-studio-panel ag-theme-quartz" style={{ width: '100%' }}>
            <AgGridReact<AuditEvent>
              rowData={events}
              columnDefs={columnDefs}
              pagination
              paginationPageSize={15}
              rowHeight={50}
              domLayout="autoHeight"
            />
          </div>
        </Card>
      )}
    </div>
  );
}
