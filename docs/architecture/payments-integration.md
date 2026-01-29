# Payments Integration

## Goals and principles
- Keep card data out of the backend to reduce PCI scope (use provider-hosted UI or SDKs).
- Support multiple providers via plugin architecture and country-based routing.
- Ensure idempotency and resilience for retries, webhooks, and partial failures.
- Treat provider webhooks as the source of truth for final payment state.

## Provider model
- `PaymentProvider` is the integration contract for all providers.
- `PaymentProviderRegistry` selects enabled providers by code and country.
- Provider credentials live in Vault/.env and are referenced via configuration records.
- The backend stores only provider references (payment id, status), not card data.

## Supported payment types (examples)
- Card payments via card SDK or hosted checkout.
- PayPal.
- Bank transfer (SEPA/IBAN for EU, local transfer for UA).
- Crypto payments (optional).

## Provider options (examples)
- Required: PayPal.
- Cards (EU/global): Adyen, Stripe, Checkout.com, Worldpay, Mollie.
- Bank transfer (EU, SEPA/IBAN): Adyen SEPA, Stripe SEPA, Mollie SEPA, Open Banking via Tink or TrueLayer.
- Ukraine local transfer: Fondy, WayForPay, Portmone, iPay.ua, MonoPay.
- Crypto (optional): Coinbase Commerce, BitPay.

## Core flows

### 1) Create payment (card or redirect)
1. Client creates an order: `POST /api/v1/orders`.
2. Client initiates payment: `POST /api/v1/payments` with `orderId`, `providerCode`, `returnUrl`.
3. Backend creates `Payment` in `PENDING` and calls provider with `idempotencyKey`.
4. Provider returns `providerPaymentId` and `nextAction` (redirect URL or client secret).
5. Backend returns `nextAction` to the client.
6. Client completes payment in provider UI/SDK.

### 2) Webhook confirmation (source of truth)
1. Provider sends webhook to `POST /api/webhooks/payments/{provider}`.
2. Backend verifies signature, event id, and idempotency.
3. Backend updates `Payment` status and publishes event.
4. Orders module updates `OrderStatus` based on payment outcome.

### 3) Capture or confirm (optional)
- If provider supports authorize/capture, confirm or capture after stock reservation.
- Keep capture idempotent and safe to retry.

### 4) Refund
- Admin or automation triggers refund.
- Backend calls provider API, then waits for webhook to finalize status.

## Payment status model
| Status | Meaning | Typical next |
|--------|---------|--------------|
| PENDING | Payment created, awaiting user action | REQUIRES_ACTION, PAID, FAILED |
| REQUIRES_ACTION | 3DS or redirect required | PAID, FAILED |
| AUTHORIZED | Funds authorized, not captured | PAID, FAILED, CANCELLED |
| PAID | Completed successfully | REFUNDED |
| FAILED | Provider rejected or error | PENDING (retry) |
| CANCELLED | User canceled or expired | PENDING (retry) |
| REFUNDED | Fully refunded | - |

Order status changes only after `Payment` reaches `PAID` or `FAILED`.

## Payment data model (minimal)
- `id`, `orderId`, `providerCode`, `providerPaymentId`
- `status`, `amount`, `currency`
- `idempotencyKey`, `failureReason`
- `createdAt`, `updatedAt`

## Webhooks and security
- Verify signatures for every webhook.
- Enforce replay protection using provider event id.
- Respond `2xx` quickly; do heavy work async.
- Store only non-sensitive payment metadata (no PAN/CVV).

## Idempotency and reliability
- Use an idempotency key per create, confirm, and refund request.
- Use an outbox to publish payment events to RabbitMQ.
- Keep webhook processing idempotent and safe to retry.

## Events
Publish domain events to RabbitMQ:
- `payments.created`
- `payments.authorized`
- `payments.completed`
- `payments.failed`
- `payments.refunded`

## Frontend integration
- Web (Next.js): card SDK or hosted checkout, PayPal.
- Mobile (React Native): native card SDK or in-app browser for hosted checkout or PayPal.
- Always redirect back to `returnUrl` and verify status via backend.

## Admin configuration
- Enable or disable providers per country.
- Manage credentials (API keys, webhook secrets).
- View payment attempts and failure reasons.

## Observability
- Metrics: success rate, latency, error rate, provider availability.
- Logs with `orderId`, `paymentId`, `providerCode`.
- Alerts on provider outage or elevated failure rate.

## Testing and sandbox
- Use provider sandbox accounts and test cards.
- Unit-test signature verification and idempotency.
- E2E: create payment -> webhook -> order status update.

## Failure handling and reconciliation
- Retry transient provider failures with backoff.
- Periodic reconciliation job compares local payments with provider API.
- Manual override flow for customer support when needed.
