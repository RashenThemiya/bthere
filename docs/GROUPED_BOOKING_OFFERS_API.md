# Grouped OPEN_REQUEST offers

Implemented locally; not deployed. OPEN_REQUEST now broadcasts to up to 20
eligible distinct providers at a time, in the existing discovery order. The
stored candidate queue remains bounded to the first 100 search rows before
provider deduplication. DIRECT_REQUEST still targets only its selected provider.

## Policy configuration

SUPER_ADMIN GET/PUT `/api/v1/admin/service-options/{optionId}/booking-request-policy`.
Customer GET `/api/v1/customers/me/booking-requests/policy?optionId={optionId}`
returns the same policy shape. Additive `offerGroupSize` accepts 1–100, default 20.
Omitting/null on PUT preserves the current size (20 for a new policy), so older
clients remain compatible. Setting 1 restores sequential dispatch.

Example PUT body (30-second groups; existing timeout values are preserved unless
changed explicitly):

```json
{
  "enabled": true,
  "minimumNoticeMinutes": 120,
  "offerTimeoutSeconds": 30,
  "offerGroupSize": 20,
  "confirmationTimeoutSeconds": 120,
  "matchingTimeoutSeconds": 600,
  "maximumRadiusKm": 50
}
```

Group size validation errors return 400. Existing setup/key/payment validation
continues to apply when enabling a policy. Group size does not mean 20 providers
are assigned: this is single-provider fulfillment with competing offers.

## Dispatch and acceptance

Every offered provider receives BOOKING_OFFER with its own offerId. All offers
in a group share one expiresAt, capped by the overall request matching deadline.
Unpriceable/unavailable candidates are skipped while filling the group. Each
offer stores its own amount/currency/platform-fee/net-earnings snapshot; providers
can have different area prices. Provider offer responses use that snapshot.

An individual decline does not advance while another offer in that group remains
pending. All declined means immediate next group; timeout expires all remaining
offers and then sends the next group. Exhausted candidates/deadline end matching
as UNMATCHED. Customer cancellation revokes all pending offers.

Existing provider APIs are unchanged:

```http
GET /api/v1/providers/me/booking-offers
POST /api/v1/providers/me/booking-offers/{offerId}/accept
POST /api/v1/providers/me/booking-offers/{offerId}/decline
GET /api/v1/providers/me/booking-events?after={lastEventId}
```

Accept/decline take no body. The first valid acceptance locks the request,
rechecks provider eligibility/capacity and the winning offer's saved price/split,
and creates exactly one reserved job. Other pending offers become CANCELLED and
each provider receives OFFER_CANCELLED. A concurrent losing accept returns 409:

```json
{
  "type": "about:blank",
  "title": "Resource conflict",
  "status": 409,
  "detail": "Already accepted by another provider"
}
```

Winning-provider retries return the saved result without another reservation.
Invalid/expired acceptance cannot win. Failed reservation or price validation
rolls back and does not revoke other offers. Acceptance still waits for customer
payment confirmation; no automatic charge or wallet debit occurs at dispatch.

While MATCHING, request View's providerId/offerId identify the first pending
offer for backward compatibility, not a chosen winner or complete group. Its
amount is a preview; the accepted provider's final quote is authoritative. Render
“Finding a provider” until AWAITING_CUSTOMER_CONFIRMATION rather than showing the
first pending provider as assigned. No new full-group customer endpoint is added.

## Provider app and rollout

On OFFER_CANCELLED/OFFER_EXPIRED, stop sound and dismiss the incoming screen.
Revalidate GET /booking-offers before displaying/accepting; a late notification
must not restart ringing. FCM delivery, registered device tokens, OS permissions
and app sound handling remain necessary. Broadcasting creates backend offers
and durable events; it does not itself guarantee a ringing phone.

Apply [existing-installation migration](GROUPED_BOOKING_OFFERS_SCHEMA.sql) before
deploying, skipping columns already created by Hibernate. New installations use
BOOKING_REQUESTS_SCHEMA.sql. Historical single offers with null snapshots use the
saved request-level price fallback; no open-request records are recreated.
Deployment and a hosted multi-device test are required after these local changes.
