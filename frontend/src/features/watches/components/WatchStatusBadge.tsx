import { Badge } from '../../../components/ui/Badge';
import { WATCH_STATUS_META } from '../../shared/meta';
import type { WatchStatus } from '../../../services/api/types';

export function WatchStatusBadge({ status }: { status: WatchStatus }) {
  const meta = WATCH_STATUS_META[status];
  return <Badge tone={meta.tone}>{meta.label}</Badge>;
}
