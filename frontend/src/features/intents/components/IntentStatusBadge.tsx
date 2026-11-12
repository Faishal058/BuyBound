import { Badge } from '../../../components/ui/Badge';
import { INTENT_STATUS_META } from '../../shared/meta';
import type { IntentStatus } from '../../../services/api/types';

export function IntentStatusBadge({ status }: { status: IntentStatus }) {
  const meta = INTENT_STATUS_META[status];
  return <Badge tone={meta.tone}>{meta.label}</Badge>;
}
