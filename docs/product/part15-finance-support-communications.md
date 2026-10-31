# Part 15: Finance, support and communications operations

This slice adds authenticated admin workflows below `/api/v1/admin/financeops`, `/supportops`, and `/communications`. Admin MFA and role permissions are enforced by `get_current_admin`; scoped roles receive the matching `admin.finance.*`, `admin.support.*`, or `admin.broadcasts.*` permission from migration `f15b351d7c20`.

## Finance controls

- Payout approval is gated on exactly one immutable `PAYOUT_HOLD` wallet transaction that matches the requested amount. New payout requests write the hold with the payout ID as its reference. Missing or mismatched historical holds appear as exceptions and cannot be approved or rejected automatically.
- Settlement reconciliation verifies the internal wallet hold and records any administrator-supplied settlement reference. No bank settlement provider is configured, so a reference is an operator attestation rather than a bank-confirmed event.
- Commission versions have monotonically increasing versions per vehicle category. New versions are drafts, require an approval reason, and may be scheduled; the API worker activates approved versions when their effective time arrives. Impact preview uses historical completed fares and does not recalculate settled driver payouts.
- Refunds require review. Approval records `APPROVED_AWAITING_PROVIDER`; it does not move money or claim a refund was sent. Configure and implement the payment provider's refund API before execution is enabled.
- Webhook replay is restricted to previously signature-verified `payment.captured` events. The inbox stores a digest and a reduced payload containing only event, order, and payment identifiers. Invalid signatures are never stored; replay attempts are capped at five.
- Wallet adjustments require two distinct approving administrators, neither of whom can be the requester, before an idempotent wallet ledger transaction is written.
- Ledger CSV exports are authenticated and capped at 50,000 transactions per request. Amounts are in minor currency units.

Invoice correction approval updates the supported source fields with an audit record and stale-value check. Applicable GST invoice replacement or credit-note rules depend on the tax jurisdiction and must be verified before issuing a replacement document.

## Support operations

Support queues include priority-based default SLA targets until a configured policy applies. Assignment stores a policy SLA due time. Active escalation policies are evaluated by the API worker and elevate aged open tickets to their configured squad. Internal notes remain staff-only; saved localized response templates are posted into the customer conversation, while email, SMS and push delivery are not configured here.

## Campaign delivery boundary

Campaigns are persisted as in-app notifications, support customer/driver and language segments, and exclude accounts without explicit marketing push opt-in for non-emergency messages. Emergency notices must target all active accounts and require `admin.broadcasts.emergency`; they may bypass marketing opt-out for urgent operational use. Scheduling and due commission activation run in each FastAPI worker while it is live, with database idempotency constraints protecting campaign recipient records. These campaigns do not send push, SMS, or email.

## Migration and runtime

Apply the normal Alembic upgrade through `f15b351d7c20`. Run the FastAPI application to process scheduled campaigns, commission activation, and configured support escalations. A dedicated durable worker is recommended for multi-instance deployments; the current API lifespan worker is intentionally local to the running process.
