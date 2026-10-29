# Accepted booking: payment window, retry, cancellation and extension

Implemented locally; deployment is not verified. Applies to accepted single-provider
OPEN_REQUEST and DIRECT_REQUEST bookings. No general paid-booking refund/cancel
workflow is introduced.

## Fifteen-minute payment window

New policy default confirmationTimeoutSeconds is 900 (15 minutes). Existing
policies must be migrated/configured to 900; existing accepted requests retain
their saved deadlines. Admin policy still supports configured 30–900 seconds.
Provider acceptance returns AWAITING_CUSTOMER_CONFIRMATION, jobId and UTC
confirmationExpiresAt. Show a countdown and Pay now, using server time/deadline.

The customer uses the existing /confirm-wallet, /confirm-mock-card or hosted
PayHere /confirm endpoint, according to payment mode. Insufficient WALLET funds
return 409 with balance/shortfall; the unpaid booking stays retryable until its
deadline. No hold is posted on insufficient funds. A definite signed PayHere
failure/cancellation callback returns the request to AWAITING_CUSTOMER_CONFIRMATION
with paymentStatus FAILED, while keeping the job AWAITING_PAYMENT and capacity
reserved. PAYMENT_FAILED events notify both participants. It does not reset the
deadline. The customer can call /confirm again; a new merchant order is generated.

Old order callbacks remain traceable through receipt/order mapping. A stale
failure cannot fail the new checkout. A late success for an old order enters
payment/refund review or duplicate-payment review instead of funding a different
checkout. Do not infer failure from a timeout or browser redirect.

## Read available actions

```http
GET /api/v1/customers/me/booking-requests/{id}/payment-window
GET /api/v1/providers/me/booking-requests/{id}/payment-window
```

No body. CUSTOMER owns the request; SERVICE_PROVIDER must be the accepted assigned
provider. HTTP 200 example for the customer:

```json
{
  "requestId": 501,
  "status": "AWAITING_CUSTOMER_CONFIRMATION",
  "paymentStatus": "FAILED",
  "confirmationExpiresAt": "2026-11-01T04:15:00Z",
  "paymentDeadlineLimit": "2026-11-01T04:30:00Z",
  "canPay": true,
  "canCancel": true,
  "canExtend": false
}
```

Fields are authoritative for that response time, not a hold on future actions.
canPay is customer-only; canExtend is assigned-provider-only. The existing full
request View remains unchanged. On deadline expiry the server marks an unpaid
request EXPIRED, cancels the pending job and releases capacity; emit/show this as
payment time expired. Scheduler or lifecycle reads process due expiration.

## Cancel before payment

```http
POST /api/v1/customers/me/booking-requests/{id}/cancel
POST /api/v1/providers/me/booking-requests/{id}/cancel
```

No body; HTTP 200 full request View, status CANCELLED. Customer route retains
existing matching/unmatched/unpaid cancellation. Provider route is for their
accepted unexpired unpaid booking only. Repeating a successful cancel is
idempotent. Both cancel capacity and revoke outstanding offers; the provider
action is audited. No payment/refund occurs. Pending, paid, reserved or reconciled
funding cannot use this unpaid cancellation route and returns 409.

## Provider extends the deadline

```http
PUT /api/v1/providers/me/booking-requests/{id}/payment-deadline
Authorization: Bearer <SERVICE_PROVIDER_TOKEN>
Content-Type: application/json
```

```json
{"confirmationExpiresAt":"2026-11-01T04:25:00Z"}
```

HTTP 200 full request View with updated confirmationExpiresAt. Send an absolute
UTC timestamp so repeating the same PUT is idempotent. Only the assigned provider
can extend an unexpired unpaid confirmation window. It must increase the current
deadline, stay at/before paymentDeadlineLimit and strictly before service start.
The immutable limit is the original confirmation deadline plus 15 minutes. Thus
a default 15-minute window can extend to at most 30 minutes after acceptance,
not indefinitely. Customer cannot extend it. Both participants receive
PAYMENT_DEADLINE_EXTENDED; refresh countdown/action flags. The action is audited.

## Unknown/paid payment states

PAYMENT_PENDING and PAYMENT_RECONCILIATION mean a merchant payment may still
succeed. They cannot be cancelled/extended as unpaid, nor should another checkout
be started to guess the result. After 15 minutes of unknown checkout, retain
capacity in PAYMENT_RECONCILIATION for verified callback/admin investigation.
A signed definite failure then releases/returns to unpaid as appropriate to the
saved deadline. A paid CARD or RESERVED WALLET booking uses existing completion
and settlement, not this unpaid-cancel API. No automatic refund is claimed.

| Status | Meaning |
|---|---|
| 400 | Invalid/missing deadline, non-increasing time, past extension limit or service start |
| 401/403 | Missing token/wrong role |
| 404 | Unowned request or provider is not assigned |
| 409 | Window expired, not accepted, payment pending/paid, or invalid lifecycle |

Errors use ProblemDetail. Example 409 detail for provider cancellation:
`Only an accepted unpaid request can be cancelled; pending or paid payments require reconciliation/refund`.

Apply UNPAID_BOOKING_WINDOW_SCHEMA.sql after checking existing columns, then
deploy. It updates existing option policy timeouts to 900, adds immutable deadline
limits and maps historic receipts to merchant order IDs. Preserve live payment
records; skip ALTER/CREATE INDEX already performed by Hibernate/manual rollout.
