# Search longer after UNMATCHED

Implemented locally; deployment is not verified. Customer action, not an automatic
background retry. Requires an owned UNMATCHED OPEN_REQUEST with no jobId.

```http
POST /api/v1/customers/me/booking-requests/501/retry
Authorization: Bearer <CUSTOMER_TOKEN>
Idempotency-Key: search-longer-501-round-1
```

No body. Use a new key for a new retry action; retain that key across network
retries. HTTP 201 returns the full existing request View, with a **new requestId**.
Illustrative response excerpt:

```json
{
  "requestId": 502,
  "status": "MATCHING",
  "selectionMode": "OPEN_REQUEST",
  "offerId": 901,
  "providerId": 13,
  "jobId": null,
  "paymentStatus": "NOT_STARTED",
  "paymentMethod": "WALLET"
}
```

Keep polling the returned new ID. The old request stays UNMATCHED for history;
the new round's retryOfRequestId is stored internally. It is not added to View.
Same key/source retries return the same new request without duplicate offers.
Reusing a key for a different source/payload returns 409. There can be only one
active matching/unpaid request per customer/option.

The server reuses the original time/location/answers/payment/filter/radius inputs,
validates current policy, minimum notice, customer requirements and pricing, and
performs fresh provider discovery. Current option must still support OPEN_REQUEST.
Newly discovered providers come first; previously contacted providers follow in
discovery order, subject to these rules across the entire retry ancestry:

- Providers that DECLINED are excluded for this retry chain.
- Other contacted providers are excluded until 120 seconds after their previous
  offer deadline. This cooldown is not an extension of the old offer.
- Providers become eligible only if current approvals, coverage, availability,
  capacity and selected capabilities still match.

The new round receives the current policy's matchingTimeoutSeconds and grouped
offer settings. Offer timeout and total matching timeout remain distinct. Late
accepts against an old expired offer remain invalid. No job/payment/reservation
is created until the normal acceptance/confirmation steps.

If no eligible candidates remain, HTTP 201 can return another UNMATCHED request
immediately; Search longer cannot guarantee a match. Show this outcome clearly
and avoid automatically retrying in a loop. To change time/location/filters,
submit the normal Create request with edited inputs and a fresh key instead.

| Status | Meaning |
|---|---|
| 400 | Missing/invalid idempotency key, stale minimum-notice/time, invalid current booking answers/filter/radius/payment setup |
| 401 / 403 | Missing authentication / not CUSTOMER |
| 404 | Missing or another customer's source request; missing current catalog/market |
| 409 | Source is not UNMATCHED OPEN_REQUEST without a job, another active request exists, reused key conflicts, policy disabled or eligibility conflict |
| 503 | Required payment/code configuration unavailable |

Lifecycle error example:

```json
{
  "type": "about:blank",
  "title": "Resource conflict",
  "status": 409,
  "detail": "Only an unmatched OPEN_REQUEST without a booking can be retried",
  "instance": "/api/v1/customers/me/booking-requests/501/retry"
}
```

Before deployment apply BOOKING_RETRY_SCHEMA.sql if the column is absent; grouped
dispatch separately requires GROUPED_BOOKING_OFFERS_SCHEMA.sql. New installs use
BOOKING_REQUESTS_SCHEMA.sql. Existing request payloads and offers are preserved.
