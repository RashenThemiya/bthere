# Initial wallet and booking-payment foundation
First release: wallets and payment initiation support **LKR only** for customers and providers. Other currency requests return HTTP 400 even if configured in an active market. Wallet lists return only LKR; legacy foreign-currency data is preserved without conversion. LKR amounts allow at most two decimal places. The underlying user/currency schema is retained for future expansion.

Update: [Switchable mock payments](MOCK_PAYMENTS_API.md) now provides an explicitly
enabled development-only MOCK SPI. DISABLED remains the default and PAYHERE remains
an unconfigured slot. The disabled-external-payment descriptions below apply to
DISABLED/PAYHERE; consult the mock guide for test-mode request/response behavior.

Implemented locally on 2026-10-17. Not deployed. This is an internal accounting
foundation, not an operational bank/card integration or approval to handle live funds.
No top-up credit endpoint, manual balance adjustment endpoint, unverified webhook,
raw card storage, or bank-credential storage is exposed.

## What works and what is disabled

CUSTOMER and SERVICE_PROVIDER accounts have their own per-currency wallets.
The current account model has one role-specific user identity; wallet ownership
is keyed by authenticated userId + ISO currency, never by a client-supplied owner.

- Available balance: can be reserved for a WALLET booking.
- Reserved balance: held against a specific server-priced booking.
- Pending balance: provider earnings awaiting a future release/dispute/payout policy.
- Transactions: append-only signed entries grouped by a balanced operation.
- A repeated booking payment/release/settlement operation does not post twice.
- Finance settlement requires a completed reserved booking and two different
  SUPER_ADMIN/FINANCE_ADMIN actors. It cannot be used to create arbitrary credits.

Gateway-dependent capabilities are false. Card/bank linking, card checkout,
top-ups and withdrawals return 503 and do not mutate balances. There is currently
no supported way to fund a new zero-balance wallet through this application.
Do not insert test balances into a live database. Pending provider earnings have
no automatic release-to-available endpoint yet. Chargeback/refund/reconciliation
against a real processor is not implemented. Completed-job status transitions
remain part of the separate booking lifecycle.

Direct CARD and WALLET are separate payment choices. CARD does not top up or
debit a wallet. Existing CARD booking creation records PENDING; a card session is
not yet available, and the customer must not be told the booking is paid.

## Customer/provider API

Every endpoint below requires Authorization: Bearer <access-token>, with CUSTOMER
or SERVICE_PROVIDER role. JSON request bodies use application/json.

```http
GET /api/v1/me/wallet-capabilities
```

200:

```json
{
  "walletLedger": true,
  "bookingReservations": true,
  "cardCheckout": false,
  "topups": false,
  "withdrawals": false,
  "cardLinking": false,
  "bankLinking": false
}
```

Disable unavailable UI actions based on these flags. No configuration boolean
can safely enable external money movement without implementing its integration.

```http
PUT /api/v1/me/wallets/LKR
```

No body. Idempotently opens a zero wallet for an uppercase ISO currency enabled
in an active market; owner is taken from the token. 200:

```json
{
  "walletId": 1,
  "currencyCode": "LKR",
  "available": 0,
  "reserved": 0,
  "pending": 0,
  "status": "ACTIVE"
}
```

```http
GET /api/v1/me/wallets
GET /api/v1/me/wallets/LKR
GET /api/v1/me/wallets/LKR/transactions?page=0&size=20
GET /api/v1/me/wallets/LKR/reconciliation
```

No request bodies. Wallet list returns Balance[] sorted by currency; a single
read returns the same Balance object as PUT. A missing wallet is 404. No walletId
or userId parameter lets the caller access a different owner's wallet.

Transactions return a Spring Page with content Entry[], page metadata, totals,
and navigation flags. page >= 0; size 1–100, default20. Entries newest first.
The stable entry shape is:

```json
{
  "entryId": 10,
  "operationId": 5,
  "currencyCode": "LKR",
  "bucket": "AVAILABLE",
  "delta": -100,
  "balanceBefore": 500,
  "balanceAfter": 400,
  "createdAt": "2030-01-01T10:00:00"
}
```

Positive delta credits a bucket; negative delta debits it. A reserve has two
entries: AVAILABLE -100 and RESERVED +100. Do not count every entry as a separate
payment; group by operationId. No update/delete API exists for entries.

Reconciliation returns ledger totals compared with stored snapshots:

```json
{"walletId": 1, "balanced": true, "available": 400, "reserved": 100, "pending": 0}
```

It does not compare with bank/processor records. A mismatch blocks future money
postings; it does not automatically reset a balance to zero or repair the ledger.

## Booking reservation

When POST /api/v1/customers/me/bookings selects WALLET, the same booking transaction
now reserves the full server-calculated expectedAmount from available balance.
Insufficient or unreconciled funds rolls back the whole booking and its wallet
operation. The response paymentStatus becomes RESERVED, not PAID. The existing
booking POST itself is still not idempotent; do not retry it blindly after timeout.

An existing own WALLET booking can be reserved/retried through:

```http
POST /api/v1/customers/me/bookings/100/wallet-payment
```

CUSTOMER only, no request body. Ignores client monetary assertions; amount,
currency, customer, payment method and state come from the locked booking.
200:

```json
{
  "operationId": 5,
  "type": "BOOKING_RESERVE",
  "jobId": 100,
  "currencyCode": "LKR",
  "amount": 100,
  "status": "POSTED"
}
```

Booking reserves are naturally idempotent by bookingId + operation type, with a
database unique operation key. An already-released reservation cannot be reused
to pay a new/reopened booking. Current reserve retries return the original record;
clients must also inspect the booking's current paymentStatus.

## Gateway-disabled request contracts

```http
POST /api/v1/me/wallets/LKR/topups
POST /api/v1/me/wallets/LKR/withdrawals
Idempotency-Key: <nonblank-key-max-100-characters>
Content-Type: application/json
```

Body (the amount is validated but no external transfer is initiated):

```json
{"amount": 1000.00}
```

An existing own wallet is required. Both endpoints always return 503 with no
balance changes in this initial implementation. Idempotency-Key is reserved for
the future integration contract; no successful top-up/withdrawal record is created.
The current withdrawal request intentionally accepts no bank account information:
verified payout destinations and transaction-bound reauthentication are not built.

```http
POST /api/v1/me/payment-methods/setup?type=CARD
POST /api/v1/me/payment-methods/setup?type=BANK_ACCOUNT
POST /api/v1/customers/me/bookings/100/card-payment-session
```

No bodies. Setup type enum CARD, BANK_ACCOUNT; always 503. Card session is
CUSTOMER-only, first verifies an owned CARD booking, then returns 503. Never send
PAN, CVV, banking passwords, banking OTPs or raw bank details to these endpoints.
No linked-method list or saved-card charge API is implemented.

Example unavailable response:

```json
{
  "type": "about:blank",
  "status": 503,
  "detail": "Wallet top-ups require an approved payment provider integration"
}
```

## Finance operations (not customer/provider controls)

SUPER_ADMIN or FINANCE_ADMIN only; no request bodies:

```http
POST /api/v1/admin/wallet-bookings/100/release
POST /api/v1/admin/wallet-bookings/100/settlement
POST /api/v1/admin/wallet-bookings/100/settlement/approve
```

- release requires jobStatus CANCELLED, REJECTED or EXPIRED and a reservation.
  It returns reserved funds to available, records BOOKING_RELEASE, and sets
  paymentStatus RELEASED. It does not cancel a job or refund a captured card.
- settlement requests BOOKING_SETTLE with status PENDING_APPROVAL; job must be
  COMPLETED and wallet payment RESERVED.
- approve must be a different finance actor from the requester. Revalidate
  completed/reserved state, amount and currency; debit customer RESERVED, credit
  provider PENDING and post platform fee to a separate system ledger account.
  Record POSTED and paymentStatus SETTLED. No client fee/recipient/amount is accepted.
- For teams, net amount is split equally across distinct assigned provider user
  accounts. Any currency minor-unit remainder goes to the last sorted recipient.
  This initial allocation policy must be agreed before live use; no custom split
  request is supported. Null platformFee currently means zero fee.
- Provider cannot be the same wallet owner as the customer. Inactive owners,
  frozen wallets and reconciliation mismatches block postings. All movements and
  finance approvals have audit records.
- Provider earnings remain pending. There is no finance shortcut to make them
  withdrawable until dispute/holding-period/payout rules are implemented.

## Money, states and accounting rules

Currency codes must be uppercase valid Java/ISO currency codes with 0–4 fractional
digits. Amounts are exact BigDecimal values with the currency's actual minor-unit
scale; fractional digits are rejected, never silently rounded. LKR has 2, JPY0,
KWD3. Maximum accepted amount/bucket balance is 99999999999999.9999, subject to
minor-unit scale. No foreign-exchange conversion or cross-currency transfer exists.

Wallet bucket enums: AVAILABLE, RESERVED, PENDING. A system PLATFORM_FEE posting
has no user wallet. Every operation's signed postings sum to zero in one currency.
Operation types: BOOKING_RESERVE, BOOKING_RELEASE, BOOKING_SETTLE. Operation status:
POSTED or PENDING_APPROVAL. Wallet status must be ACTIVE for posting; any other
status is blocked (FROZEN is a useful administrative/database state, not a new
public status-write endpoint). Booking payment status additions: RESERVED,
RELEASED, SETTLED. These are not equivalent to external provider settlement.

Money mutation locks the booking, then owner user rows in deterministic order,
then wallet/current ledger rows. User locks also serialize first wallet creation;
wallet/user-currency and operation keys have database unique constraints.
Wallet has an optimistic version as an additional concurrent-write guard. All
ledger entries and snapshots are saved atomically; database constraints and
runtime error rollbacks protect against partial posting.

The ledger is append-only at application/ORM level (@Immutable) and no public
edit/delete endpoint exists. Database administrators can still modify data;
production must restrict journal tables to SELECT/INSERT for the application
account, use separate migration credentials, and retain audited backups. This is
not a cryptographic tamper-proof ledger or external reconciliation engine.

Legacy WalletTransaction records are not automatically copied into this new
double-entry ledger. Existing positive snapshot balances without matching ledger
entries are blocked from spending; investigate/back up/reconcile and migrate them
explicitly with reviewed opening entries. Never use an unverified client top-up,
receipt screenshot or direct database balance edit as an opening credit.

Deployment schema reference: [WALLET_SCHEMA_MIGRATION.sql](WALLET_SCHEMA_MIGRATION.sql).
It is not auto-executed and requires an inspected schema diff/backups. Do not run
it blindly after Hibernate has already added the same columns/tables.

## Error handling

400 invalid currency/amount/precision/request; 401 missing/invalid token; 403 wrong
role; 404 own wallet/booking not found; 409 insufficient/frozen/unreconciled funds,
changed settlement state, self-approval/self-settlement or stale concurrent write;
503 external integration not available. Business errors use ProblemDetail; security
and request-binding errors may use standard Spring error responses.

## Before enabling real funding and payouts

Choose the merchant/payment and payout providers; confirm their support for this
wallet/marketplace model, saved cards, top-ups, refunds and bank payouts. Implement
provider-hosted tokenization, encrypted provider references, signed/timestamped
webhook validation, server-side amount/currency/order/merchant checks, durable
event deduplication and charge-reference uniqueness, and outbound idempotency.
Use a durable payment-intent/outbox state machine for uncertain provider outcomes;
do not wrap an external network payment in a database-only transaction and assume
it can roll back. Add verified destinations, amount-bound step-up authentication,
limits, fraud/dispute handling, pending-earnings release policy and reconciliation
against real processor/bank statements before allowing withdrawal.

Security design references:
[OWASP transaction authorization](https://cheatsheetseries.owasp.org/cheatsheets/Transaction_Authorization_Cheat_Sheet.html),
[OWASP authorization](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html),
[Stripe idempotency reference](https://docs.stripe.com/api/idempotent_requests).
These are design references, not a claim that Stripe is integrated or available
for this merchant's Sri Lankan business.

Testing covers accounting/idempotency/ownership/precision/insufficient funds,
dual-control approval, role permissions and transaction rollback. Production
MySQL concurrent integration/chaos tests, penetration review and live gateway
verification remain required before treating this foundation as live-money ready.
