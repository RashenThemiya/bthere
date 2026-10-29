# Scheduled booking requests: customer, provider, card and wallet payment APIs

WALLET is now supported alongside CARD. See [WALLET_BOOKING_REQUESTS_API.md](WALLET_BOOKING_REQUESTS_API.md)
for confirm-wallet, structured shortfalls, reserved payment states and automatic settlement.

Superadmin read-only request/booking/offer/payment/settlement monitoring is documented
in [ADMIN_BOOKING_MONITOR_API.md](ADMIN_BOOKING_MONITOR_API.md).

Implemented locally, 25 October 2026. Not deployed or tested against a live
PayHere merchant account or mobile device. Apply BOOKING_REQUESTS_SCHEMA.sql
before rollout. This opt-in flow supports a single provider, scheduled bookings,
LKR, Visa/Mastercard credit or debit card checkout, and existing wallet funds. No card balance check or hold is
made before matching. Existing wallet/mock capability APIs do not describe this
new hosted checkout integration.

## Sequence

Platform commission is configured before settlement through the
[service/option/rate percentage APIs](PLATFORM_FEES_API.md). Quotes, request Views,
offers and bookings expose platformFeePercentage, platformFee and providerEarnings.
Commission is included in the fare. Accepted bookings retain the saved split;
fee changes before acceptance require a fresh offer even if the fare is unchanged.

Service/option → booking-context and eligibility → required booking fields and
filters/pricing choices → create request → provider offer → provider accepts →
customer confirms before deadline → hosted PayHere checkout → verified payment
callback → CONFIRMED → start code → IN_PROGRESS → provider requests completion →
AWAITING_COMPLETION_CONFIRMATION → customer confirms → COMPLETED.

The option's existing providerSelectionMode controls DIRECT_REQUEST versus
OPEN_REQUEST. DIRECT_REQUEST contacts only the selected provider. OPEN_REQUEST
offers sequentially to distinct eligible providers ordered by the existing
search distance. This is scheduled coverage matching, not live GPS/ETA matching.
The first 100 search candidate rows form the bounded open-request queue; fixed
venue rows are deduplicated to one offer per provider. Direct targets are filtered
before pagination. No automatic switch from direct to open mode occurs.

Fields, eligibility, delivery/scheduling settings, capability filters, coverage,
working hours and booking capacity use the existing configuration. Price choices
also constrain provider filters. Each offer is re-quoted; acceptance revalidates
and reserves capacity in an AWAITING_PAYMENT job. A changed offered price returns
409; cancel/recreate the request rather than silently accepting a changed amount.

## MOCK card checkout update (29 October 2026)

MOCK now supports CARD request creation and explicit synthetic confirmation after
provider acceptance without PayHere credentials. Use payment-settings.provider
to select `confirm-mock-card` versus the existing hosted `/confirm` route.
See [MOCK booking card contract](MOCK_BOOKING_CARD_API.md). WALLET is unchanged.
Implemented locally; deployment is not verified.

## Accepted unpaid booking window

New policies default to a 15-minute confirmation window. Definite card failure
keeps an unpaid booking retryable until its deadline. Assigned providers can
cancel unpaid requests or extend their deadline by up to another 15 minutes;
customers retain unpaid cancellation. Read action flags via payment-window.
See [payment window APIs](UNPAID_BOOKING_WINDOW_API.md) for full examples,
errors and the unknown-payment reconciliation exception. Local implementation;
deployment is not verified.

## Search longer after UNMATCHED

Customer POST `/api/v1/customers/me/booking-requests/{id}/retry`, no body,
Idempotency-Key required, creates a new matching round from current discovery.
New providers are prioritized; declined providers are excluded and other contacted
providers have a 120-second post-deadline cooldown. See
[Search longer contract](BOOKING_SEARCH_LONGER_API.md) for responses/errors.
Implemented locally; deployment is not verified.

## Grouped OPEN_REQUEST dispatch

OPEN_REQUEST now broadcasts in configurable groups, default 20; the first valid
acceptance wins and cancels remaining offers. DIRECT_REQUEST remains single-target.
See [grouped offers contract](GROUPED_BOOKING_OFFERS_API.md) and its additive
schema migration. This update supersedes older sequential-offer descriptions
below. Implemented locally; deployment is not verified.

## Admin configuration

SUPER_ADMIN:

GET /api/v1/admin/service-options/{optionId}/booking-request-policy

PUT /api/v1/admin/service-options/{optionId}/booking-request-policy

```json
{
  "enabled": true,
  "minimumNoticeMinutes": 120,
  "offerTimeoutSeconds": 60,
  "confirmationTimeoutSeconds": 120,
  "matchingTimeoutSeconds": 600,
  "maximumRadiusKm": 50
}
```

Missing policy means disabled with the displayed defaults. Enabling requires CARD or WALLET
in the option's allowedPaymentMethods, ADMIN pricing, supported single-provider
fulfillment and an encryption key for codes. CARD supports MOCK test confirmation
or configured PayHere checkout; WALLET-only does not require PayHere. Team
options are rejected explicitly. Policies are shared across linked services.
For an enabled option, the legacy POST /customers/me/bookings endpoint returns
409 directing the customer to booking-requests; it cannot bypass this lifecycle.
Disabling a policy stops future offers; existing accepted/payment-in-progress
requests still resolve through their lifecycle.

Validation ranges: minimumNoticeMinutes 1–43200; offerTimeoutSeconds 15–600;
confirmationTimeoutSeconds 30–900; matchingTimeoutSeconds 60–3600;
maximumRadiusKm 0.1–500. The request radius cannot exceed the option policy.

Server configuration (never ship secrets in either app):

```text
BOOKING_PAYHERE_ENABLED=true
BOOKING_PAYHERE_SANDBOX=true
BOOKING_PAYHERE_MERCHANT_ID=<merchant ID>
BOOKING_PAYHERE_MERCHANT_SECRET=<domain/app merchant secret>
BOOKING_PAYHERE_NOTIFY_URL=https://<backend>/api/v1/payments/payhere/booking-notify
BOOKING_PAYHERE_RETURN_URL=https://<frontend>/payment-return
BOOKING_PAYHERE_CANCEL_URL=https://<frontend>/payment-cancel
BOOKING_VERIFICATION_KEY=<base64-encoded 32 random bytes>
BOOKING_PUSH_SERVICE_ACCOUNT_FILE=<server-local Firebase service-account JSON path>
```

Select PAYHERE in existing admin gateway settings as well. Sandbox is the default;
production keys and domain/app approval are separate setup. The new checkout does
not enable the older generic gateway/wallet endpoints. Preserve the verification
key across restarts; key replacement without migration invalidates stored codes.

## Customer endpoints

Require CUSTOMER bearer authorization. JSON writes use Content-Type:
application/json. Paths below begin /api/v1/customers/me.

| Method and suffix | Success |
| --- | --- |
| GET /booking-requests/policy?optionId=12 | 200 policy |
| POST /booking-requests | 201 request View; requires Idempotency-Key header |
| GET /booking-requests/{requestId} | 200 current View |
| POST /booking-requests/{requestId}/confirm | 200 hosted Checkout for CARD |
| POST /booking-requests/{requestId}/confirm-wallet | 200 View for WALLET; no body |
| POST /booking-requests/{requestId}/cancel | 200 cancelled View where allowed |
| GET /bookings/{jobId}/verification-codes | 200 currently available own code |
| POST /bookings/{jobId}/confirm-completion | 200 confirms provider-requested completion; no body or code |
| GET /booking-events?after=0 | 200 up to 100 own durable events after event ID |

Illustrative route request. Replace IDs/coordinates and use a future pickup
meeting the option's minimum notice. All configured required answers must be
included; an empty answers array only works when none are required.

```json
{
  "marketId": 1,
  "booking": {
    "serviceTypeId": 3,
    "optionId": 12,
    "deliveryMode": "PROVIDER_TO_CUSTOMER",
    "requestedPickupTime": "2030-01-07T08:00:00",
    "pickup": {"address":"Pickup address","latitude":6.9271,"longitude":79.8612},
    "destination": {"address":"Destination address","latitude":6.9371,"longitude":79.8712},
    "paymentMethod": "CARD",
    "answers": [],
    "pricingSelections": [{"fieldId":45,"choiceValue":"CAR"}]
  },
  "radiusKm": 20,
  "languages": ["ENGLISH"],
  "requiredSkillIds": [],
  "providerFilters": [{"fieldId":45,"value":["CAR"]}]
}
```

For DIRECT_REQUEST add booking.providerId; OPEN_REQUEST must omit it. Do not send
providerIds (team selection). TIME_BASED uses startDatetime/expectedEndDatetime
instead of route fields. PROVIDER_TO_CUSTOMER time bookings supply meetingPoint;
CUSTOMER_TO_PROVIDER searches supply top-level searchLocation and can optionally
constrain booking.providerLocationId. The chosen candidate's location ID is used
when reserving. ONLINE omits physical coordinates. Dates in booking bodies are
market-local; server-generated searches convert them to offset-bearing times.

The same customer/key/body returns the same request without another offer.
Changing a body while reusing its key returns 409.
Only one matching/unpaid request per customer and option may remain active;
another request returns 409 until the existing request is resolved or cancelled.
Confirmed bookings do not count as outstanding requests. A no-match request is stored
as UNMATCHED without taking payment. Provider matching/pricing is bounded; a new
search/request is required to discover providers outside the original queue.

View fields: requestId, status, selectionMode, offerId, providerId,
offerExpiresAt, confirmationExpiresAt, jobId, paymentStatus, expectedAmount and
currencyCode, platformFeePercentage, platformFee, providerEarnings, settlementStatus,
settlementOperationId, paymentFundingMode and paymentMethod. An active
offer identifies a candidate, not a confirmed booking.
jobId appears after provider acceptance; AWAITING_PAYMENT is a capacity
reservation, not paid/confirmed service. Amount/currency can be null when no
eligible priced candidate was found. Deadlines are UTC Instant strings.

After provider acceptance, confirm before confirmationExpiresAt:

```json
{
  "cardMethod":"VISA",
  "firstName":"Customer",
  "lastName":"Name",
  "email":"customer@example.com",
  "phone":"0771234567",
  "address":"Billing address",
  "city":"Colombo",
  "country":"Sri Lanka"
}
```

cardMethod is VISA or MASTER (either credit/debit); it is a card brand, not a
client assertion that funds exist. Confirm returns actionUrl, fields and
status: PAYMENT_PENDING. POST the returned fields to the exact actionUrl in a
hosted checkout/browser. Card number/CVV go to PayHere, never this backend.
Repeated confirm returns the same checkout payload; changing card brand after
checkout starts returns 409. No client price/rate/distance override is accepted.

A return/cancel browser redirect cannot confirm/cancel payment. Read the request
until a verified callback resolves it. PayHere signatures, merchant, order,
amount, currency and card method are checked; replayed notification status does
not repeat booking transitions. Multiple successful payments for one order are
flagged for review. Reopening/resubmitting a hosted payment page is not a new
booking and may need merchant review if the gateway processes an extra charge.

## Provider endpoints

Require SERVICE_PROVIDER bearer authorization. Paths begin /api/v1/providers/me.

| Method and suffix | Purpose |
| --- | --- |
| GET /booking-offers | Current pending own offers |
| POST /booking-offers/{offerId}/accept | Reserve capacity; wait for customer payment |
| POST /booking-offers/{offerId}/decline | Decline and advance an open request |
| GET /bookings/{jobId} | Assigned request booking details, including submitted answers |
| POST /bookings/{jobId}/start | Verify customer START code |
| POST /bookings/{jobId}/complete | Request customer completion confirmation; no body or code |
| GET /booking-events?after=0 | Own durable event stream for polling/recovery |

Offer includes offerId/requestId/status/expiresAt, serviceTypeId/optionId,
deliveryMode, scheduling fields, providerLocationId, expectedAmount/currencyCode,
pickup/destination/meetingPoint. Full patient/booking answers are not in an offer;
they are available only to the assigned provider after acceptance. Repeated
accept returns existing state without another capacity reservation. Expired or
already claimed offers return 409. Declined/expired direct offers end UNMATCHED.

## Start code and customer completion confirmation

Customer code response: {purpose:"START",code:"six digits",expiresAt}.
The START code is available only for CONFIRMED, is encrypted at rest, and is
never in provider responses or push events. It becomes available 30 minutes before
scheduled start and expires 24 hours after expected end unless support issues a
replacement. No completion code is generated, exposed or accepted.

Provider start body:

```json
{"code":"123456"}
```

Response: {successful:true,status:"IN_PROGRESS"}, or
{successful:false,status:"INVALID_CODE"|"CODE_LOCKED"}. Invalid attempts persist;
five incorrect attempts lock the start code. Wrong ownership is 404; wrong lifecycle
state/time window is 409. Repeating an already successful transition is idempotent.
After service, the assigned provider calls POST /api/v1/providers/me/bookings/{jobId}/complete
with no body. Only an in-progress paid booking can enter
AWAITING_COMPLETION_CONFIRMATION. This records actualEndDatetime and sends a
COMPLETION_REQUESTED notification to the customer. This state releases provider
capacity in both search and locked booking checks, so waiting for a customer does
not prevent subsequent work. It is not yet the final COMPLETED/settlement state.

The owning customer then calls POST /api/v1/customers/me/bookings/{jobId}/confirm-completion
with no body. Response: {successful:true,status:"COMPLETED"}. Confirmation is only
allowed after the provider has requested it; other customers/providers cannot
confirm. Both actions are idempotent and do not repeat notifications or overwrite
the provider's actual end timestamp on retries. Confirmation has no code deadline. Successful confirmation also atomically credits
the provider wallet using the saved commission split; see
[CARD_BOOKING_SETTLEMENT_API.md](CARD_BOOKING_SETTLEMENT_API.md).
If the customer does not respond, the state remains AWAITING_COMPLETION_CONFIRMATION;
this MVP adds no automatic confirmation or dispute workflow.

Start/end record actual service timestamps; the MVP does not automatically
recalculate a new actual-duration fare or charge an additional amount at completion.

SUPER_ADMIN can POST /api/v1/admin/bookings/{jobId}/verification-codes/reset with
{reason:"support evidence and reason"} (10–1000 characters). Only a paid booking
still waiting to start can receive a replacement start code. The old code is
replaced, attempts reset and the action audited without logging the code. Support still needs a way to contact
the owning customer; this is not permission for providers to bypass verification.

## Mobile notifications

Any authenticated app can register a device with:

POST /api/v1/me/booking-devices

```json
{"token":"<FCM registration token>","platform":"ANDROID","deviceName":"Phone"}
```

Response: {deviceId}. Platform is ANDROID or IOS. DELETE
/api/v1/me/booking-devices/{deviceId} unregisters an owned device. Unregister on
logout and register fresh on login/token refresh. Tokens are not returned in APIs.
At most ten active devices per user are retained.

Offers and lifecycle changes generate durable events with eventId, userId,
requestId, offerId, eventType, createdAt, expiresAt and delivery metadata. Poll
using the last seen eventId. Events include BOOKING_OFFER, OFFER_EXPIRED,
OFFER_DECLINED, OFFER_ACCEPTED, OFFER_CANCELLED, PROVIDER_ACCEPTED,
BOOKING_CONFIRMED, BOOKING_STARTED, COMPLETION_REQUESTED, BOOKING_COMPLETED and review/terminal events.

When Firebase credentials are configured, a worker sends FCM HTTP v1 messages
with high Android priority, offer expiry TTL and an Android booking_calls channel
(booking_updates for ordinary booking notifications). Register those channels in
the Android app. Data includes IDs/type/expiry only; apps fetch authorized details.
The app must stop ringing on accept/decline/revocation/expiry and must deduplicate
eventId. Push delivery can retry and therefore repeat. iOS needs APNs credentials
configured in Firebase, notification permissions and its native handlers.

Backend push is not a phone call and cannot guarantee full-screen or continuous
ringing. Foreground/background/locked-screen handling and audible notification
behavior require provider-app implementation and device testing. Polling remains
a recovery path. A stale notification can never authorize a late acceptance.

## Expiration, payments and support

The durable expiry worker resumes after restart. Unaccepted offers advance the
queue; expiry/decline/cancel before checkout releases local capacity. Customer
cancellation is allowed before checkout begins, not while a payment may succeed
or after confirmation. No customer funds are collected before checkout.

After checkout begins, missing notification for 15 minutes changes the request
to PAYMENT_RECONCILIATION and retains capacity. It does not assume payment failed.
A late valid success before scheduled start can still confirm; success after
capacity was released or start time passed is marked REFUND_REVIEW/PAYMENT_REVIEW
without confirming unavailable service. Chargebacks are recorded separately and
not overwritten by stale success/failure notifications.

Finance/SUPER_ADMIN can POST
/api/v1/admin/booking-requests/{requestId}/resolve-unpaid with
{reason:"merchant order verification/evidence reference"}. This is a manual,
audited operation after checking the order in the merchant system, not automated
gateway reconciliation. It rejects orders with a recorded successful payment.
It releases capacity for verified-unpaid outstanding requests. A later success
is flagged for refund review rather than reclaiming capacity.

Automatic PayHere retrieval/refunds, customer cancellation after successful
payment, provider cancellation after acceptance, automatic provider payouts,
team dispatch and live Ride Now matching remain outside this MVP. Resolve paid
cancellations, duplicate payments and refund/chargeback review through merchant
operations; do not advertise automatic refunds or bank payouts. Customer-confirmed
CARD and WALLET request bookings do automatically distribute saved provider earnings to the wallet.

References: PayHere [Checkout API](https://support.payhere.lk/api-%26-mobile-sdk/checkout-api),
[hold limitations](https://support.payhere.lk/hold-on-card), Firebase
[HTTP v1](https://firebase.google.com/docs/cloud-messaging/send/v1-api) and
[message lifetime](https://firebase.google.com/docs/cloud-messaging/customize-messages/setting-message-lifespan).
