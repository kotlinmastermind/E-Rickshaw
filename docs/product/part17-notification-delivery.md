# Part 17 · Notification delivery and preferences

## Scope

Notification preferences, consent audit, inbox state, delivery attempts, retry scheduling, deduplication, templates, and admin operations are persisted in the API database. Customer and driver clients use `/api/v1/notifications`; elevated delivery operations live under `/api/v1/admin/notification-ops` and require MFA plus the matching fine-grained admin permission.

## Provider boundary

- In-app inbox records are the durable fallback and are never removed when an external channel is unavailable.
- Push is sent through Firebase Cloud Messaging only when the Firebase Admin SDK can initialize. `CONFIGURED_UNVERIFIED` means credentials initialized locally; it is not an external delivery probe or proof of downstream device receipt.
- Email and SMS delivery are not configured. Their attempts are recorded as unavailable instead of simulated as sent.
- Push receipts represent provider acceptance (FCM message id), not device display/read confirmation.

Configure `FCM_CREDENTIALS_PATH` or the supported Firebase Application Default Credentials in the backend runtime before expecting push delivery. Keep provider secrets outside source control. The backend must be running for its 30-second persisted retry worker to process due notifications.

## Preference and safety policy

Users can control push/email/SMS preferences, language (`en`, `hi`, `mr`, `gu`), and quiet hours in an IANA time zone. Preference changes create an append-only consent audit row. Quiet hours defer ordinary pushes. Explicit emergency/safety event types may bypass quiet hours and push opt-out; this allowlist is intentionally narrow and is not configurable from the mobile app. Marketing delivery continues to use the separate broadcast opt-out policy.

The normal push retry limit is five attempts and a per-user rolling cap limits pushes to twenty attempts per hour. Failed deliveries remain in the admin queue; an administrator with replay permission can queue only failed records that have not exhausted that retry limit. Replay is audited. Duplicate keys are unique at the database layer. Notification actions are limited to approved relative app paths and clients only navigate known in-app routes.

## Operations

Run the migration with `alembic upgrade head`. Start the backend using the repository's normal ASGI command to activate due-notification processing. Admins can inspect the 24-hour status summary, provider configuration state, consent audit, localized template variants and previews, and eligible failed event replays in **Notification Delivery**. Template previews never send a message and require exact values for all placeholders.
