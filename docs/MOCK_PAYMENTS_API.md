# Switchable payment SPI and mock testing

Implemented locally on 2026-10-17. Not deployed. All mock money, card methods and
bank methods are synthetic test data. No provider or bank network calls are made.

## Provider selection

The Spring-selected `PaymentGateway` SPI isolates provider naming, capabilities,
payment initiation and simulation from controllers. It currently supports:

| Admin provider | Behavior |
| --- | --- |
| DISABLED | Default; external payment functions return 503 |
| MOCK | Synthetic top-ups, direct-card payments, withdrawals, failure and refund tests |
| PAYHERE | Selectable fail-closed integration slot; returns 503 until official implementation is supplied |

PayHere API calls, credentials, hosted checkout, tokenization and bank payout APIs
are NOT implemented. Selecting PAYHERE never silently falls back to MOCK. Future
providers can implement the SPI and register in PaymentGatewayConfiguration;
official money-movement verification/outbox policies still need implementing.

Selection is persisted in the database and changed only by SUPER_ADMIN. No payment
provider environment variables or restart are needed. Missing settings default to DISABLED.

GET /api/v1/admin/settings/payments reads the current selection.
PUT /api/v1/admin/settings/payments accepts:

```json
{"provider":"MOCK"}
```

Allowed provider values: DISABLED, MOCK, PAYHERE. Response:

```json
{"provider":"MOCK","configured":true,"testMode":true}
```

Selecting PAYHERE currently returns configured=false and testMode=false, and payment
operations return 503 until its official adapter is implemented. Updates are audited.
Customers/providers cannot change or read this admin setting. Their APIs return
capabilities and payment methods without provider or mock selection fields.
Use a separate test database when enabling MOCK; synthetic balances remain marked
MOCK internally and cannot be spent after switching away from MOCK. Existing intents
and instruments stay associated with their original gateway; switching does not
convert or migrate funds. In-flight transactions finish with their transaction's
settings snapshot; switching affects subsequent transactions.


First release: wallets and payment initiation support **LKR only** for customers and providers. Other currency requests return HTTP 400 even if configured in an active market. Wallet lists return only LKR; legacy foreign-currency data is preserved without conversion. LKR amounts allow at most two decimal places. The underlying user/currency schema is retained for future expansion.
## Settings and capabilities

CUSTOMER or SERVICE_PROVIDER bearer authentication:

```http
GET /api/v1/me/payment-settings
```

MOCK response:

```json
{
  "capabilities": {
    "walletLedger": true,
    "bookingReservations": true,
    "cardCheckout": true,
    "topups": true,
    "withdrawals": true,
    "cardLinking": true,
    "bankLinking": true
  }
}
```

GET /api/v1/me/wallet-capabilities returns the capabilities object alone. In
DISABLED/PAYHERE, the last five flags are false. Always show a visible TEST/MOCK
banner in your test environment. These capabilities describe a simulation, not
live-bank capability.

## Test payment methods

```http
POST /api/v1/me/payment-methods/setup?type=CARD
POST /api/v1/me/payment-methods/setup?type=BANK_ACCOUNT
GET /api/v1/me/payment-methods
```

No request body. Setup returns 200 with one synthetic method per owner/type/provider:

```json
{
  "methodId": 5,
  "type": "BANK_ACCOUNT",
  "displayLabel": "TEST BANK •••• 0001",
  "brand": null,
  "status": "VERIFIED_MOCK"
}
```

CARD label is TEST CARD •••• 4242. CARD methods return `brand`: `"VISA"` or
`"MASTERCARD"` (the mock card is always the synthetic `"VISA"`); use it to pick the
card logo. BANK_ACCOUNT methods return `brand: null`. The label is invented by the server; it is not
a card PAN or verified bank account. List returns Method[]. Methods are scoped
to owner and selected provider. No bank/card numbers, CVV, banking passwords,
bank OTPs or real payment tokens are accepted or stored. The mock verification
state must never be reused as real KYC/account verification.

### Remove a saved method

```http
DELETE /api/v1/me/payment-methods/{methodId}
```

No body. CUSTOMER or SERVICE_PROVIDER token. Works for CARD and BANK_ACCOUNT.

| Status | When |
|---|---|
| 204 No Content | Removed. It no longer appears in `GET /api/v1/me/payment-methods`. |
| 404 | `methodId` does not exist, belongs to another user, or belongs to another provider. |
| 409 | A payment (top-up/withdrawal intent) using this method is still `PENDING`. |
| 401 | Missing or expired token. |

Pending-payment behaviour: the method cannot be removed while any intent that
references it is `PENDING`. Wait until that payment ends as `SUCCEEDED` or
`FAILED`, then retry. Completed payments keep their `methodId` for history even
after the method is removed. After removal, `POST /payment-methods/setup` can add
the method again (it gets a new `methodId`).

```json
{
  "type": "about:blank",
  "title": "Resource conflict",
  "status": 409,
  "detail": "Payment method is used by a pending payment"
}
```

```json
{
  "type": "about:blank",
  "title": "Resource not found",
  "status": 404,
  "detail": "Payment method not found"
}
```

## Top-up test

1. Open a zero wallet: PUT /api/v1/me/wallets/LKR, no body.
2. Start a synthetic top-up:

```http
POST /api/v1/me/wallets/LKR/topups
Idempotency-Key: topup-test-001
Content-Type: application/json
```

```json
{"amount": 1000.00, "methodId": null}
```

methodId is optional for top-up; if provided, it must be an owned VERIFIED_MOCK
CARD method. 200 creates a PENDING intent; money is NOT credited at creation:

```json
{
  "intentId": 1,
  "purpose": "TOPUP",
  "currencyCode": "LKR",
  "amount": 1000.00,
  "jobId": null,
  "methodId": null,
  "status": "PENDING"
}
```

3. Simulate its result as the same authenticated owner:

```http
POST /api/v1/me/mock-payments/1/events
Content-Type: application/json
```

```json
{"outcome": "SUCCESS"}
```

200 returns the same complete Intent shape with status SUCCEEDED. The wallet
gets AVAILABLE +1000 and an external-clearing ledger counterentry -1000. Replaying
SUCCESS returns the same result without another credit. Repeating creation with
the same owner/provider/idempotency key and matching request returns the same
intent; changed purpose/currency/amount/method returns 409. Keys are required,
nonblank, maximum100characters and persisted as hashes.

## Withdrawal test

First create an owned synthetic BANK_ACCOUNT method. Use its actual returned ID:

```http
POST /api/v1/me/wallets/LKR/withdrawals
Idempotency-Key: withdrawal-test-001
Content-Type: application/json
```

```json
{"amount": 100.00, "methodId": 5}
```

200 returns Intent with purpose WITHDRAWAL, status PENDING. Creation reserves
AVAILABLE -100 / RESERVED +100. Missing/wrong-owner/wrong-type/unverified method,
insufficient funds or reconciliation failure rejects the request.

POST /api/v1/me/mock-payments/{intentId}/events with SUCCESS consumes reserved
funds into the synthetic external-clearing account. FAILURE releases the hold
back to available. PENDING leaves it held. No real bank payout is made. A
successful withdrawal cannot be refunded through this test event endpoint;
simulate a separate verified return flow only after a future adapter supports it.

## Direct card test

Create an owned booking with paymentMethod CARD; it does not need wallet funds.

```http
POST /api/v1/customers/me/bookings/100/card-payment-session
Idempotency-Key: card-booking-100
```

CUSTOMER only; no body. Server uses the locked booking's currency/expectedAmount.
Returns Intent with purpose CARD and jobId. One intent is retained per booking
even if callers retry with different keys, preventing two mock card charges for
the same booking. For a terminal failed/refunded intent, this initial mock API
does not create an automatic replacement; use a new test booking.

Simulate SUCCESS: booking paymentStatus becomes PAID. FAILURE: FAILED. REFUND
after success: REFUNDED. No wallet debit or credit occurs for direct card payment.
Current provider settlement is still the wallet-reservation workflow; direct-card
earnings distribution is not implemented by this mock session. Do not infer it
from a PAID flag. There is no real hosted checkout URL in the mock response.

## Intent reads, outcomes and refunds

```http
GET /api/v1/me/payment-intents/{intentId}
POST /api/v1/me/mock-payments/{intentId}/events
```

Only the owning user can read/simulate an intent for the selected provider.
Simulation body outcome enum: SUCCESS, FAILURE, PENDING, REFUND. State enum:
PENDING, SUCCEEDED, FAILED, REFUNDED. Purpose enum: TOPUP, WITHDRAWAL, CARD.

- PENDING → SUCCESS/FAILURE is allowed.
- Repeated same outcome is a no-op.
- Terminal success/failure cannot be overwritten by a conflicting outcome.
- SUCCEEDED → REFUND is allowed for TOPUP/CARD only.
- Top-up refund debits available funds exactly once; if money has been spent or
  reserved and cannot cover the refund, it returns 409 with no partial refund.
- Duplicate events reuse natural intent/effect ledger operation keys. Transactions,
  current-state locks, unique keys and wallet reconciliation remain in force.
- The endpoint is a local test tool, not a public processor webhook. Live adapters
  cannot use the simulate SPI operation by default; DISABLED/PAYHERE returns 503.

## Mock/real separation

Wallet responses add fundingMode (UNFUNDED, MOCK, or null for legacy rows). Any
mock-funded wallet is marked MOCK. Mock earnings carry that marker to providers.
Mock and existing non-mock funds cannot be mixed; MOCK wallets cannot post money
after switching the provider off/out of mock mode. There is no public balance
reset or mock-to-real conversion endpoint. Retain a separate DB for tests; this
marker is a safeguard, not a substitute for isolating mock bookings/payment data.

## SPI extension and schema

PaymentGateway.Request carries intentId, userId, purpose, currencyCode, amount,
jobId and methodId. Add an official adapter only after merchant/API details are
known. PAYHERE is explicitly UnconfiguredPaymentGateway, not guessed PayHere HTTP
calls. A future bank payout provider may be a separate port/composed adapter;
do not assume a card gateway supports marketplace bank payouts.

Real gateways require durable intents/outbox, server-authenticated callbacks,
merchant/order/amount/currency checks, charge/event deduplication, token protection,
transaction-bound withdrawal reauthentication and approved payout destinations.
Do not put a real remote payment into the mock's database-only transaction and
assume a rollback reverses the remote payment.

New schema: gateway_intent, payment_instrument, and wallet.funding_mode. Existing
Hibernate schema-update configuration can create them in a fresh local DB. For
manual migrations, use an inspected schema diff and retain prior wallet migration
requirements. Do not apply new DDL blindly to an existing live database.
An unexecuted MySQL schema reference is in [MOCK_PAYMENT_SCHEMA.sql](MOCK_PAYMENT_SCHEMA.sql).

Errors: 400 invalid amount/key/type/outcome; 401/403 authentication/role; 404
unknown or other-owner intent/method/booking; 409 mismatched retry, illegal state,
insufficient funds or mixed funds; 503 inactive/official-unconfigured provider.
No live payment integrations are enabled by this work.

