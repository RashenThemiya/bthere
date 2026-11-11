# Accepted booking payment: deadlines, retry and recovery (frontend guide)

Answers the frontend questions about the confirm/pay/retry flow after a provider
accepts. Updated for the 2-min + 1-hour retry window (11 November 2026).
It is implemented locally; **deployment to the environment you are using is not
verified** — confirm with backend before relying on it there.

Build the UI only from backend fields (`confirmationExpiresAt`, `status`,
`paymentStatus`, `canPay`/`canCancel`/`canExtend`). Do not compute deadlines in the app.

## 0. Short answer: which rule is real?

The backend now implements "2 minutes to confirm, then one fresh hour to retry after a failure":

- **Initial window:** `confirmationTimeoutSeconds` from the option policy, now **120 s (2 min)**
  by default (admin can still set 30–900 s). Starts when the provider accepts.
- **First failed attempt** (PayHere decline/cancel callback, or wallet `INSUFFICIENT_WALLET_FUNDS`)
  opens **one retry window**: `confirmationExpiresAt` becomes `failure time + 1 hour`
  (never earlier than the current deadline, never later than service start).
  `paymentRetryStartedAt` is set at that moment.
- **Later failures do not reset it.** The 1-hour deadline stays the same.
- **No attempt at all:** the request expires at the end of the initial 2 minutes.
- A PayHere checkout started inside the 2 minutes still gets the retry window if its failure
  callback arrives after the 2 minutes ran out.
- The assigned provider can still extend up to `paymentDeadlineLimit`.

The frontend does not compute any of this. Always count down to `confirmationExpiresAt`.

## 1. Time limits

| Question | Current behaviour |
|---|---|
| Initial window after acceptance | `now + confirmationTimeoutSeconds` (default 2 min) → `confirmationExpiresAt` |
| New 1 hour after a failure? | Yes, once: `confirmationExpiresAt` = failure time + 1 hour (capped at service start) |
| Reset on repeated failures? | No. The retry deadline is fixed after the first failure |
| No payment attempt | Expires at the initial deadline |
| Maximum possible time | `paymentDeadlineLimit` (provider extension limit; raised to the retry deadline if that is later) |
| Service start time | Pay/confirm is rejected (409) once start time has passed |

Rules per failure type:

| Failure | What the backend does | Deadline |
|---|---|---|
| Wallet insufficient balance | `409 INSUFFICIENT_WALLET_FUNDS`, nothing reserved, `paymentStatus: FAILED` | First time: +1 hour retry window and `PAYMENT_FAILED` event. Later: unchanged |
| PayHere card declined / customer cancelled on PayHere page (signed callback `-1`/`-2`) | Request → `AWAITING_CUSTOMER_CONFIRMATION`, `paymentStatus: FAILED`, job still reserved, `PAYMENT_FAILED` event to both | First time: +1 hour retry window. Later: unchanged; if it already passed → `EXPIRED` |
| Browser returns to cancel/return URL, app closed, network timeout | **Nothing changes.** Request stays `PAYMENT_PENDING` until PayHere sends a signed callback | Not applied while pending |
| No callback for 15 min after checkout started | Request → `PAYMENT_RECONCILIATION`, capacity kept, `PAYMENT_REVIEW_REQUIRED` event | Not applied; support resolves |
| MOCK card | Always succeeds (test only); no failure path | — |

## 2. Fields and action flags

### Countdown field

- **Use `confirmationExpiresAt`** for the countdown. It is the real deadline. It moves
  when the first payment failure opens the retry window, or when the provider extends.
- `paymentRetryStartedAt` (in `/payment-window`) is null until the first failure. When set,
  the retry window is in use; show e.g. "Payment failed — retry before <time>".
- `paymentDeadlineLimit` is the furthest the provider is allowed to extend to. It
  is **not** a deadline for the customer. Do not show it as a countdown.
- Both are UTC ISO-8601 instants. Re-read after any event; do not trust a stale value.

### `GET /api/v1/customers/me/booking-requests/{id}/payment-window`

Flags for the customer:

| Flag | True when |
|---|---|
| `canPay` | status `AWAITING_CUSTOMER_CONFIRMATION`, before `confirmationExpiresAt`, `paymentStatus` `NOT_STARTED` or `FAILED` |
| `canCancel` | status is `MATCHING`, `AWAITING_CUSTOMER_CONFIRMATION`, `UNMATCHED`, `EXPIRED` or `CANCELLED` |
| `canExtend` | always false for the customer |

Note: `canCancel` is also true for `EXPIRED`/`CANCELLED` (cancel is harmless there).
**Hide the Cancel button when status is `EXPIRED` or `CANCELLED`.**

Provider uses `GET /api/v1/providers/me/booking-requests/{id}/payment-window`:
`canPay` false; `canCancel` and `canExtend` true only during an unpaid,
unexpired window (`canExtend` also needs `confirmationExpiresAt < paymentDeadlineLimit`).

Reading this endpoint or `GET /booking-requests/{id}` also processes a due
expiry, so the response is always current.

### Customer UI actions

| `status` | `paymentStatus` | Show |
|---|---|---|
| `AWAITING_CUSTOMER_CONFIRMATION` | `NOT_STARTED` | Countdown + **Pay now** + Cancel |
| `AWAITING_CUSTOMER_CONFIRMATION` | `FAILED` | "Payment failed" + countdown + **Try again** + Cancel |
| `PAYMENT_PENDING` | `PENDING` | "Waiting for payment result" spinner. **No Pay, no Cancel** |
| `PAYMENT_RECONCILIATION` | `PENDING` | "We are checking your payment; contact support". No actions |
| `CONFIRMED` | `PAID` (card) / `RESERVED` (wallet) | Booking confirmed |
| `EXPIRED` | `NOT_STARTED`/`FAILED` | "Payment time expired". Offer new search |
| `CANCELLED` | any | Cancelled |
| `PAYMENT_REVIEW` / `PAYMENT_FAILED` | `REFUND_REVIEW` / `UNPAID_REVIEWED` | "Payment under review, contact support" |

### Examples (`payment-window`)

Accepted, not paid yet:
```json
{"requestId":501,"status":"AWAITING_CUSTOMER_CONFIRMATION","paymentStatus":"NOT_STARTED",
 "confirmationExpiresAt":"2026-11-08T04:02:00Z","paymentDeadlineLimit":"2026-11-08T04:17:00Z","paymentRetryStartedAt":null,
 "canPay":true,"canCancel":true,"canExtend":false}
```

Card failed at 04:01, retry window opened (1 hour from the failure):
```json
{"requestId":501,"status":"AWAITING_CUSTOMER_CONFIRMATION","paymentStatus":"FAILED",
 "confirmationExpiresAt":"2026-11-08T04:16:00Z","paymentDeadlineLimit":"2026-11-08T04:17:00Z","paymentRetryStartedAt":"2026-11-08T04:01:00Z",
 "canPay":true,"canCancel":true,"canExtend":false}
```

Checkout started, result unknown:
```json
{"requestId":501,"status":"PAYMENT_PENDING","paymentStatus":"PENDING",
 "confirmationExpiresAt":"2026-11-08T04:02:00Z","paymentDeadlineLimit":"2026-11-08T04:17:00Z","paymentRetryStartedAt":null,
 "canPay":false,"canCancel":false,"canExtend":false}
```

No callback for 15 min:
```json
{"requestId":501,"status":"PAYMENT_RECONCILIATION","paymentStatus":"PENDING",
 "confirmationExpiresAt":"2026-11-08T04:02:00Z","paymentDeadlineLimit":"2026-11-08T04:17:00Z","paymentRetryStartedAt":null,
 "canPay":false,"canCancel":false,"canExtend":false}
```

Deadline passed:
```json
{"requestId":501,"status":"EXPIRED","paymentStatus":"NOT_STARTED",
 "confirmationExpiresAt":"2026-11-08T04:02:00Z","paymentDeadlineLimit":"2026-11-08T04:17:00Z","paymentRetryStartedAt":null,
 "canPay":false,"canCancel":true,"canExtend":false}
```

### Full request View (`GET /api/v1/customers/me/booking-requests/{id}`)

```json
{
  "requestId": 501, "status": "AWAITING_CUSTOMER_CONFIRMATION", "selectionMode": "OPEN_REQUEST",
  "offerId": 601, "providerId": 15, "offerExpiresAt": "2026-11-08T04:01:00Z",
  "confirmationExpiresAt": "2026-11-08T04:15:00Z", "jobId": 701,
  "paymentStatus": "FAILED", "expectedAmount": 3000.00, "currencyCode": "LKR",
  "platformFeePercentage": 10.00, "platformFee": 300.00, "providerEarnings": 2700.00,
  "settlementStatus": null, "settlementOperationId": null,
  "paymentFundingMode": null, "paymentMethod": "CARD"
}
```

The View does **not** include `paymentDeadlineLimit` or the action flags; read
`/payment-window` for those.

### Wallet insufficient funds (`409`)

```json
{
  "type": "about:blank", "title": "Insufficient wallet balance", "status": 409,
  "detail": "Insufficient available wallet balance",
  "code": "INSUFFICIENT_WALLET_FUNDS", "currencyCode": "LKR",
  "requiredAmount": 3000.00, "availableAmount": 1200.00, "shortfall": 1800.00
}
```

The first time, this opens the 1-hour retry window (re-read `/payment-window` for the
new `confirmationExpiresAt`); later times it does not change. Show the shortfall and a top-up option, then
let the customer call `confirm-wallet` again before `confirmationExpiresAt`.

Other errors use ProblemDetail with only `detail` text (no `code`): 409 for an
expired window, wrong state or start time passed; 404 for a request the user doesn't own.
After any 409, re-read `/payment-window` and render from it.

## 3. Retry payment

The request, job and provider stay the same on retry. Call the same endpoint
again. None of these take an `Idempotency-Key` header.

| Method | Endpoint | Body | Retry behaviour |
|---|---|---|---|
| Wallet | `POST /api/v1/customers/me/booking-requests/{id}/confirm-wallet` | none | Safe to repeat; cannot reserve twice. After success, repeating returns the same View |
| MOCK card | `POST /api/v1/customers/me/booking-requests/{id}/confirm-mock-card` | `{"methodId": 1}` | Repeating with the same `methodId` returns the same View. A different `methodId` after payment → 409 |
| PayHere card | `POST /api/v1/customers/me/booking-requests/{id}/confirm` | see below | See next table |

PayHere body:
```json
{"cardMethod":"VISA","firstName":"Customer","lastName":"Name","email":"customer@example.com",
 "phone":"0771234567","address":"Billing address","city":"Colombo","country":"Sri Lanka"}
```
Response: `{"actionUrl":"https://sandbox.payhere.lk/pay/checkout","fields":{...},"status":"PAYMENT_PENDING"}`.
POST `fields` to `actionUrl` in a browser/web view.

PayHere retry rules:

| Current state | Calling `/confirm` again |
|---|---|
| `PAYMENT_PENDING`, same `cardMethod` | Returns the **same** checkout (same merchant order). Use this to reopen the page |
| `PAYMENT_PENDING`, different `cardMethod` | 409 |
| `AWAITING_CUSTOMER_CONFIRMATION` + `FAILED` | Creates a **new merchant order** (`BR-<uuid>`). `VISA` ↔ `MASTER` change is allowed here |
| `PAYMENT_RECONCILIATION` | 409; do not retry |

For an accepted request, CARD/WALLET can be changed without restarting provider
matching by calling `POST /api/v1/customers/me/booking-requests/{requestId}/payment-method`
with `{"paymentMethod":"CARD"}` or `{"paymentMethod":"WALLET"}`. This is allowed
before card checkout starts; a failed wallet reservation can also switch to CARD.
A started card checkout, reconciliation, funded booking, or expired confirmation
window cannot switch. The service option must allow the selected method. See
[booking request API](BOOKING_REQUESTS_API.md#change-payment-method-after-provider-acceptance).

### Avoiding a double charge

- Never start a new checkout while status is `PAYMENT_PENDING` or `PAYMENT_RECONCILIATION`.
- A return/cancel redirect, closed browser or timeout is **not** a failure.
  Re-read the request and wait for the status to change.
- Only show **Try again** when `canPay` is true.
- Disable the Pay button while a request is in flight.
- If a late success arrives for an old order, the backend moves it to review
  (`DUPLICATE_PAYMENT_REVIEW` / `PAYMENT_REVIEW`); it never charges into a second booking.

## 4. Recovery and expiry

### Recovering after Home / restart / another device

There is **no "list my booking requests" endpoint**. Current options:

1. Save `requestId` locally when the request is created, then call
   `GET /booking-requests/{id}` and `/payment-window`.
2. On a new device: `GET /api/v1/customers/me/booking-events?after=0`. Each event
   has `requestId`. Up to 100 events per page, oldest first. Page with
   `after=<last eventId>` and use the latest event per `requestId`.

The booking (job) responses **do not contain `requestId`**, so you cannot go
from a booking list item to its request today.

Backend can add `GET /api/v1/customers/me/booking-requests?active=true` and a
`requestId` on booking responses if you need them; ask if so.

### Events

Payload (same for every type):
```json
{"eventId":9001,"userId":42,"requestId":501,"offerId":601,"eventType":"PAYMENT_FAILED",
 "createdAt":"2026-11-08T04:05:00Z","expiresAt":"2026-11-08T04:15:00Z",
 "deliveryStatus":"SENT","deliveryAttempts":1,"nextDeliveryAt":"2026-11-08T04:05:00Z"}
```

| `eventType` | When | `expiresAt` |
|---|---|---|
| `PROVIDER_ACCEPTED` | Provider accepted | Payment deadline |
| `PAYMENT_FAILED` | Signed card failure, still within deadline | Payment deadline |
| `PAYMENT_DEADLINE_EXTENDED` | Provider extended | New deadline |
| `BOOKING_CONFIRMED` | Paid / wallet reserved | null |
| `PAYMENT_REVIEW_REQUIRED` | 15 min without callback, or chargeback | null |
| `BOOKING_EXPIRED` | Deadline passed unpaid | null |
| `BOOKING_CANCELLED` | Customer or provider cancelled | null |
| `BOOKING_PAYMENT_REVIEW` | Late/odd payment moved to review | null |
| `BOOKING_PAYMENT_FAILED` | Support resolved as unpaid | null |
| `DUPLICATE_PAYMENT_REVIEW` | Second payment detected | null |

Push may repeat. Deduplicate by `eventId`, then re-read the request. Do not take state from the event alone.

### When the deadline passes

- Request → `EXPIRED`, job → `CANCELLED`, provider capacity released immediately.
- Runs within about 5 s (background worker), or on the next read of the request.
- `PAYMENT_PENDING` / `PAYMENT_RECONCILIATION` do **not** expire automatically.
  Capacity stays reserved until a callback arrives or support resolves it.

## 5. Deployment and testing

- Implemented locally. **Not verified as deployed**; backend must confirm for your environment.
- Migration `docs/PAYMENT_RETRY_WINDOW_SCHEMA.sql` is needed for the retry window: adds
  `payment_retry_started_at` and sets all policies to 120 s.
- Migration `docs/UNPAID_BOOKING_WINDOW_SCHEMA.sql` is also needed if not applied yet; **run it before** the retry migration, because it sets all
  existing policies to 900 s, adds `payment_deadline_limit` and backfills it for
  old requests. Requests accepted before the migration that have no limit show
  `canExtend: false`.

### Test: card failure → retry → success (PayHere sandbox)
1. Create request with `paymentMethod: CARD`; provider accepts → `AWAITING_CUSTOMER_CONFIRMATION`, `canPay: true`.
2. `/confirm` → `PAYMENT_PENDING`. Pay with a sandbox card that declines.
3. After callback: `AWAITING_CUSTOMER_CONFIRMATION`, `paymentStatus: FAILED`, `confirmationExpiresAt` ≈ now + 1 hour, `paymentRetryStartedAt` set, event `PAYMENT_FAILED`.
   A second failure leaves `confirmationExpiresAt` unchanged.
4. `/confirm` again → new checkout. Pay with a success card → `CONFIRMED`, `paymentStatus: PAID`, event `BOOKING_CONFIRMED`.

### Test: wallet shortfall → top-up → success
1. WALLET request accepted. Wallet balance below amount.
2. `/confirm-wallet` → 409 `INSUFFICIENT_WALLET_FUNDS`; deadline becomes now + 1 hour (first time only).
3. Top up, `/confirm-wallet` again → `CONFIRMED`, `paymentStatus: RESERVED`.

### Test: pending → reconciliation
1. `/confirm`, open the PayHere page and close it without paying.
2. Status stays `PAYMENT_PENDING`; `canPay: false`.
3. After 15 min → `PAYMENT_RECONCILIATION`, event `PAYMENT_REVIEW_REQUIRED`.

### Test: expiry
1. Admin sets the option's `confirmationTimeoutSeconds` to 30 for testing.
2. Provider accepts; do not pay.
3. After 30 s (+ up to 5 s) → `EXPIRED`, event `BOOKING_EXPIRED`, `canPay: false`.
