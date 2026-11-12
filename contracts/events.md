# Events and State Transitions

## Internal events
- `watch.created`
- `offer.observed`
- `offer.validated`
- `purchase_intent.created`
- `guardian.evaluated`
- `spend.reserved`
- `purchase.hold`
- `purchase.blocked`
- `payment.order_created`
- `payment.capture_pending`
- `payment.capture_completed`
- `payment.failed_confirmed`
- `payment.reconciliation_required`
- `mandate.revoked`
- `webhook.received`
- `webhook.rejected`
- `savings.verified`

Each event should include `eventId`, `eventType`, `occurredAt`, `correlationId`, and relevant entity IDs. Do not include secrets or unnecessary personal data.

## Provider webhooks
Configure only event types supported by the exact PayPal product and account. Event names and signature verification requirements must be taken from current official documentation; do not assume that an event name from another PayPal product is supported.

Processing rules:
1. Verify authenticity before processing.
2. Persist event ID and minimal required payload metadata.
3. Deduplicate by provider event ID.
4. Enqueue state reconciliation.
5. Validate allowed state transition.
6. Fetch provider status if event order conflicts or state is ambiguous.
7. Record result in audit log.

## Transition invariants
- `CAPTURED` requires verified provider evidence.
- `BLOCKED` never transitions to a payment execution state.
- `RECONCILIATION_REQUIRED` cannot be blindly retried.
- Revocation blocks new work but does not falsely cancel a submitted payment.
- Duplicate events never duplicate purchases or savings entries.
