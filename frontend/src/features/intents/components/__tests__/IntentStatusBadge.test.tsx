import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { IntentStatusBadge } from '../IntentStatusBadge';
import type { IntentStatus } from '../../../../services/api/types';

const cases: Array<[IntentStatus, string]> = [
  ['CAPTURED', 'Captured'],
  ['BLOCKED', 'Blocked'],
  ['AWAITING_APPROVAL', 'Awaiting approval'],
  ['RECONCILIATION_REQUIRED', 'Needs reconciliation'],
  ['SPEND_RESERVED', 'Spend reserved'],
  ['CREATED', 'Created'],
];

describe('IntentStatusBadge', () => {
  it.each(cases)('renders the human-readable label for %s', (status, label) => {
    render(<IntentStatusBadge status={status} />);
    expect(screen.getByText(label)).toBeInTheDocument();
  });

  it('renders exactly one badge element', () => {
    const { container } = render(<IntentStatusBadge status="BLOCKED" />);
    expect(container.querySelectorAll('span').length).toBe(1);
    expect(screen.getByText('Blocked')).toBeInTheDocument();
  });
});
