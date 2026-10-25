# Automatic PayHere booking settlement

The new booking-request lifecycle now supports WALLET as well as CARD. See
[wallet request payments](WALLET_BOOKING_REQUESTS_API.md) for the confirmation endpoint,
reservation states, funding rules and WALLET_BOOKING_SETTLE accounting.

Superadmin read-only request/booking/offer/payment/settlement monitoring is documented
in [ADMIN_BOOKING_MONITOR_API.md](ADMIN_BOOKING_MONITOR_API.md).

Implemented locally; not deployed. Apply `BOOKING_REQUESTS_SCHEMA.sql`,
`PLATFORM_FEES_SCHEMA.sql`, then `CARD_BOOKING_SETTLEMENT_SCHEMA.sql` in that order
when schema updates are managed manually. Existing wallet tables must exist.

## Completion

Customer authentication is required:

`POST /api/v1/customers/me/bookings/{jobId}/confirm-completion`

Request body: none. Response:

```json
{"successful":true,"status":"COMPLETED"}
```

The provider must first request completion. This transaction verifies the captured
PayHere payment and saved gross/commission/net snapshot, credits the assigned
provider's AVAILABLE LKR wallet balance, writes balanced immutable ledger entries,
and completes the booking. Any failure rolls the transaction back. No second card
charge occurs. A unique per-booking operation and request/job locks prevent retries
from crediting twice. Existing finance approval for wallet-funded jobs is unchanged.
Only single-provider card bookings are supported by automatic settlement.

Example: gross LKR 1,000, commission 15%, platform fee LKR 150, provider credit LKR
850. The saved booking split is used even if an administrator changes fees later.
100% commission records zero provider earnings. This credits an internal wallet;
a bank transfer/withdrawal is a separate workflow.

Booking request views add `settlementStatus` (`POSTED` after success),
`settlementOperationId`, and `paymentFundingMode`. Before settlement these can be
null. The job payment status becomes `SETTLED`; the request gateway payment status
remains `PAID`, preserving the capture receipt and callback idempotency.

## Provider balance and earnings history

Use the provider's bearer token. All GET requests have no body.

`GET /api/v1/me/wallets/LKR`

```json
{"walletId":42,"currencyCode":"LKR","available":850.00,"reserved":0.00,"pending":0.00,"status":"ACTIVE","fundingMode":"PAYHERE"}
```

`GET /api/v1/me/wallets/LKR/transactions?page=0&size=20`

Response is a Spring paginated object. An example item in `content`:

```json
{"entryId":101,"operationId":66,"currencyCode":"LKR","bucket":"AVAILABLE","delta":850.00,"balanceBefore":0.00,"balanceAfter":850.00,"createdAt":"2026-10-25T10:30:00","type":"CARD_BOOKING_SETTLE","jobId":70,"grossAmount":1000.00,"platformFeePercentage":15.00,"platformFee":150.00,"providerEarnings":850.00,"fundingMode":"PAYHERE","paymentReference":"gateway-payment-id"}
```

History adds payment reference, gross fare, saved commission and net earnings to
existing ledger entries. Historical operations without metadata return null for
those fields. Only the authenticated user's wallet entries are returned. The
verified gateway receipt is retained in `booking_payment_notice`; the settlement
operation links that receipt to the earnings ledger. There is no public credit API.
Refresh balance/history after `BOOKING_COMPLETED` or when opening the wallet.

`GET /api/v1/me/wallets/LKR/reconciliation` returns `walletId`, `balanced`,
`available`, `reserved`, and `pending` from the ledger.

## Payment and safety rules

The server freezes `PAYHERE` or `PAYHERE_SANDBOX` on checkout creation. Sandbox
credits use separate clearing/fee accounts, are tagged in history and cannot fund
wallet bookings. A nonempty wallet cannot mix sandbox, live card and other funds.
An empty wallet can adopt the new funding source. Older orders with no known
payment environment require merchant reconciliation; never guess or backfill live.

Settlement requires an owned completed booking, a verified successful receipt,
no verified chargeback, a consistent saved split, and an active reconciled wallet.
Failures return the existing API error format (404 ownership/missing data, 409
invalid state/payment/split/wallet). The booking remains awaiting confirmation if
posting fails. Investigate the error before retrying confirmation.

A verified post-settlement chargeback freezes the credited provider wallet for
finance review; it does not silently subtract spent earnings. Cancellation refunds,
automatic chargeback recovery, customer disputes and live bank payouts are not
implemented by this change.
