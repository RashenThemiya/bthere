# Auto-cancel when the provider does not start

Before this change, a paid booking stayed `CONFIRMED` forever when the provider never started it. Now the server
cancels it automatically, refunds the customer in full, and tells both sides. It runs on the server's scheduler
(every 60 seconds), so it works even when both apps are closed.

- **Status:** the existing `CANCELLED`. There is no new status.
- **Who:** `cancelledBy` is `SYSTEM`, and `cancellation.automatic` is `true`.
- **Money:** the customer gets back 100% of the paid amount, including the platform fee. There is no
  cancellation fee and nothing is kept. The refund goes to the customer's **wallet** (see [Refund](#refund)).
- **Provider:** no penalty, and the cancellation does not count towards suspension (product can change this later).

## When it happens

| Booking state at the check | Result |
|---|---|
| `CONFIRMED`, paid, `actualStartedAt: null`, provider has **not** arrived and has **not** tried a start code | Cancelled at **booked start + grace period**. Reason `PROVIDER_DID_NOT_START`. |
| Same, but the provider marked **ARRIVED** (live tracking) | **Held**. `autoCancelHold: "PROVIDER_ARRIVED"`. Cancelled only when the service window closes. Reason `NOT_STARTED_IN_SERVICE_WINDOW`. |
| Same, but the provider **tried a start code** (even a wrong one, or `CODE_LOCKED`) | **Held**. `autoCancelHold: "START_CODE_TRIED"`. Same as above. |
| `IN_PROGRESS`, `AWAITING_COMPLETION_CONFIRMATION`, `COMPLETED` | Never auto-cancelled. The start code was accepted. |
| `NO_SHOW_REVIEW` | Never auto-cancelled. An admin decides the dispute ([no-show complaints](CUSTOMER_NO_SHOW_API.md)). |
| Not paid (card not `PAID`, wallet not `RESERVED`), or already `CANCELLED` | Never auto-cancelled. |
| Booked start before **2026-11-24T00:00:00Z** (older bookings) | Never auto-cancelled. They stay `CONFIRMED` until the customer, the provider or an admin cancels them. |

"Paid" means `paymentMethod: CARD` with `paymentStatus: PAID`, or `WALLET` with `RESERVED`.

**Service window closes** = the later of these two times:
- The end of the start-code window (`codeExpiresAt`, normally the booked end + 24 h).
- The end of the no-show complaint window, if complaints are enabled for the option (booked end + `noShowClaimDeadlineHours`, default 24 h).

A held booking is never cancelled before start + grace either. The hold gives a provider who is at the address time
to start, or to file a no-show complaint if the customer is not there. Starting the journey (`EN_ROUTE`) does **not**
hold the booking; only arrival or a code attempt does.

## Grace period

The grace period is set per service option. Product decides the value.

- **Server default:** 60 minutes (`BOOKING_AUTO_CANCEL_GRACE_MINUTES`).
- **Per option override:** `autoCancelAfterMinutes`, from 5 to 1440. `null` uses the server default.
- A change applies to existing bookings too, because `autoCancelAt` is recalculated on every read.
- The whole feature can be switched off with `BOOKING_AUTO_CANCEL_ENABLED=false`.
- Only bookings that start from `BOOKING_AUTO_CANCEL_STARTS_FROM` (default `2026-11-24T00:00:00Z`) are covered, so
  bookings made before the feature existed are not cancelled when it is deployed. For those, `autoCancelAt` is `null`.

`GET /api/v1/admin/service-options/{optionId}/auto-cancel-settings` (SUPER_ADMIN)

```json
{ "optionId": 4, "enabled": true, "autoCancelAfterMinutes": null, "defaultMinutes": 60, "effectiveMinutes": 60 }
```

`PUT /api/v1/admin/service-options/{optionId}/auto-cancel-settings` (SUPER_ADMIN)

```json
{ "autoCancelAfterMinutes": 45 }
```

The response has the same shape as GET. A value outside 5–1440 returns 400. Send `null` to go back to the default.

## Start code and auto-cancel at the same time

`POST /api/v1/providers/me/bookings/{jobId}/start` and the auto-cancel both lock the same booking rows in the
same order. Only one of them can win:

- **The start code wins:** the booking is `IN_PROGRESS` and the auto-cancel skips it. A started booking is never cancelled.
- **The auto-cancel wins:** the start call returns 409 with code `BOOKING_AUTO_CANCELLED`:

```json
{
  "type": "about:blank",
  "title": "Resource conflict",
  "status": 409,
  "detail": "This booking was cancelled automatically because it was not started in time",
  "instance": "/api/v1/providers/me/bookings/70/start",
  "code": "BOOKING_AUTO_CANCELLED"
}
```

The start code is accepted from 30 minutes before the booked start until `codeExpiresAt`, as before. Any code entry
before `autoCancelAt`, even a wrong one, holds the booking (`START_CODE_TRIED`). So a provider who is at the door
entering the code is not cancelled under them. After the cancel, `GET /customers/me/bookings/{jobId}/verification-codes`
returns 409 and the customer app should hide the code.

## Fields for the apps

These endpoints now return `autoCancelAt`, `autoCancelHold` and `cancellation`:

| Endpoint | Response |
|---|---|
| `GET /api/v1/customers/me/bookings`, `POST /api/v1/customers/me/bookings` | `BookingResponse` |
| `GET /api/v1/customers/me/bookings/{jobId}/providers` (Booking Details) | `BookingProviders` |
| `GET /api/v1/customers/me/booking-requests/{requestId}` (and accept, confirm, cancel responses) | `View` |
| `GET /api/v1/providers/me/bookings/{jobId}` | `BookingResponse` |

| Field | Type | Meaning |
|---|---|---|
| `autoCancelAt` | ISO instant (UTC) or `null` | When this `CONFIRMED` booking will be cancelled if not started. `null` in every other state. |
| `autoCancelHold` | `null`, `PROVIDER_ARRIVED` or `START_CODE_TRIED` | Why `autoCancelAt` was moved to the end of the service window. |
| `cancellation` | object or `null` | Set once the booking is `CANCELLED`, by anyone. |

`cancellation` object:

| Field | Example | Meaning |
|---|---|---|
| `cancelledBy` | `"SYSTEM"` | `CUSTOMER`, `PROVIDER`, `SYSTEM` (auto-cancel), `CUSTOMER_NO_SHOW`, `NO_SHOW_REJECTED` |
| `reason` | `"PROVIDER_DID_NOT_START"` | Auto-cancel uses `PROVIDER_DID_NOT_START` or `NOT_STARTED_IN_SERVICE_WINDOW`. For other cancellations it is the text that was sent. |
| `automatic` | `true` | `true` only for `SYSTEM` |
| `currencyCode`, `paidAmount`, `refundAmount`, `cancellationFee` | `"LKR"`, `5000.00`, `5000.00`, `0.00` | Money outcome |
| `refundDestination` | `"WALLET"` | Always the wallet |
| `cancelledAt` | `"2026-11-25T05:30:12Z"` | UTC |

`paymentStatus` on the booking shows the payment result: `REFUNDED` after an auto-cancel. The full record is still
at `GET /customers/me/bookings/{jobId}/cancellation` and `GET /providers/me/bookings/{jobId}/cancellation`
([cancellations](CANCELLATION_REFUND_API.md)).

Countdown: `secondsLeft = (autoCancelAt − now) / 1000`. Show a warning such as "The provider has not started.
This booking will be cancelled at 11:30 with a full refund", and format the time with `marketTimezone`
([time zone](BOOKING_TIME_ZONE.md)).

### Example: waiting (start 10:00 Sri Lanka, grace 60)

```json
{
  "jobId": 70,
  "status": "CONFIRMED",
  "paymentMethod": "CARD",
  "paymentStatus": "PAID",
  "startDatetime": "2026-11-25T10:00:00",
  "startDatetimeUtc": "2026-11-25T04:30:00Z",
  "marketTimezone": "Asia/Colombo",
  "actualStartedAt": null,
  "autoCancelAt": "2026-11-25T05:30:00Z",
  "autoCancelHold": null,
  "cancellation": null
}
```

### Example: after the auto-cancel

```json
{
  "jobId": 70,
  "status": "CANCELLED",
  "paymentStatus": "REFUNDED",
  "actualStartedAt": null,
  "autoCancelAt": null,
  "autoCancelHold": null,
  "cancellation": {
    "cancelledBy": "SYSTEM",
    "reason": "PROVIDER_DID_NOT_START",
    "automatic": true,
    "currencyCode": "LKR",
    "paidAmount": 5000.00,
    "refundAmount": 5000.00,
    "cancellationFee": 0.00,
    "refundDestination": "WALLET",
    "cancelledAt": "2026-11-25T05:30:12Z"
  }
}
```

### Example: provider arrived (held)

```json
{ "status": "CONFIRMED", "autoCancelAt": "2026-11-26T06:30:00Z", "autoCancelHold": "PROVIDER_ARRIVED" }
```

## Events and push

Both events go to `GET /booking-events` and are sent as a visible push. Push is sent only when FCM is configured on
the server.

| To | `eventType` | Push title / body |
|---|---|---|
| Customer | `BOOKING_AUTO_CANCELLED` | "Booking cancelled" / "Your provider did not start the service in time. The full amount was refunded to your wallet." |
| Provider | `PROVIDER_BOOKING_AUTO_CANCELLED` | "Booking cancelled automatically" / "You did not start this booking in time, so it was cancelled and the customer was refunded." |

The provider also gets the usual silent `OFFER_CANCELLED` for the accepted offer. The customer does **not** get
`BOOKING_CANCELLED` for an auto-cancel. Apps that do not know `BOOKING_AUTO_CANCELLED` yet should refresh the
booking on any unknown event; the status will be `CANCELLED`.

Example event (trimmed):

```json
{ "eventId": 812, "userId": 5, "requestId": 106, "offerId": null, "eventType": "BOOKING_AUTO_CANCELLED",
  "createdAt": "2026-11-25T05:30:12Z" }
```

## Refund

| Payment | What happens |
|---|---|
| WALLET (`RESERVED`) | The reservation is released. The full amount moves from `RESERVED` to `AVAILABLE` in the customer's wallet. |
| CARD (`PAID`) | The full amount is credited to the customer's wallet `AVAILABLE` balance. It is **not** sent back to the card. The customer can spend or withdraw it. |

There is one ledger operation per booking (`BOOKING:{jobId}:CANCEL`), and it is never repeated. If a card refund
cannot be posted (for example the card payment was never verified), the booking stays `CONFIRMED`. The server logs
it, retries every 15 minutes, and an admin should reconcile it.

## Not covered yet

- If the **customer** cancels after the booked start while the provider is late, the normal refund policy still
  applies (the lowest tier). The customer can wait for `autoCancelAt` for a full refund.
- There is no "provider is late" reminder push before `autoCancelAt`.
