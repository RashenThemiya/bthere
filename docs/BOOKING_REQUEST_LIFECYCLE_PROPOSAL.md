# Scheduled booking requests, card authorization and provider offers

Status: original design proposal, 25 October 2026, superseded by
[the implemented MVP contract](BOOKING_REQUESTS_API.md). The user selected debit
card support: match first, then collect payment through hosted PayHere checkout.
The authorization-before-dispatch sequence below records the alternative design;
it is not the implemented payment sequence.
Completion was subsequently simplified to provider request plus customer
confirmation without an end code; only the start code is retained. Use the
implemented MVP contract for the current endpoints and states.
Existing quote/create endpoints, admin settings and mock card intents do not
implement this lifecycle. The PAYHERE adapter currently remains unconfigured.

## Product flow

1. Customer selects service/option, delivery/location/time, provider filters and
   all required booking fields. Validate option-specific schema and eligibility
   on the server before starting payment or dispatch.
2. Use the option's configured providerSelectionMode. DIRECT_REQUEST targets the
   chosen eligible provider only. OPEN_REQUEST lets the backend select eligible
   candidates using the configured dispatch policy. Do not silently switch modes.
3. Calculate and persist a server-owned quote snapshot. MVP payment method is CARD;
   enforce it on these new routes, independently of legacy wallet/cash APIs.
4. If credit-card hold mode is chosen, start PayHere authorization for the exact
   displayed LKR amount. Begin dispatch only after a verified authorization result.
   A redirect or app callback is not proof of authorization.
5. Send time-limited offers. For DIRECT_REQUEST, decline/expiry returns a clear
   unmatched result, without automatically contacting other providers. For
   OPEN_REQUEST, use a configurable ordered selection such as nearest configured
   service area, then send sequential offers for the MVP. Broaden only within
   configured coverage/search limits; random selection is not required.
6. Provider acceptance atomically claims the offer and temporarily reserves the
   required capacity. Customer gets a configurable short confirmation period
   (proposed default 120 seconds). Stop further offers for that slot.
7. Customer confirms the accepted provider and displayed amount. Backend captures
   the authorized payment. Confirm the booking only after verified capture success.
   Capture failure must not produce a confirmed booking. Uncertain outcomes remain
   PAYMENT_PROCESSING until reconciled, retaining the reservation.
8. At the scheduled meeting/pickup, customer gives a start code to the assigned
   provider. Valid verification changes CONFIRMED to IN_PROGRESS.
9. After the service, customer gives a different completion code. Valid verification
   changes IN_PROGRESS to COMPLETED. Customer can access this code only after start.

Use separate booking, offer and payment states. Suggested request states:
DRAFT, AWAITING_AUTHORIZATION, MATCHING, AWAITING_CUSTOMER_CONFIRMATION,
PAYMENT_PROCESSING, CONFIRMED, IN_PROGRESS, COMPLETED, UNMATCHED, EXPIRED,
CANCELLED and PAYMENT_FAILED. A separate payment record tracks authorization,
capture, uncertainty, expiry and refund state. Cancellation is not evidence that
funds have been released or refunded.

## Proposed API surface

These are proposed routes, not existing endpoints. Existing bookings/quote can
continue serving direct-provider previews. An OPEN_REQUEST preview needs a
provider-independent option-rate calculation; it must not require a fabricated
providerId or claim availability before matching.

| Actor | Method and proposed path | Purpose |
| --- | --- | --- |
| Customer | POST /api/v1/customers/me/booking-requests/preview | Validate configured fields and calculate a server price for either mode, without dispatch/payment |
| Customer | POST /api/v1/customers/me/booking-requests | Create request from validated inputs with a stable idempotency key |
| Customer | POST /api/v1/customers/me/booking-requests/{id}/card-authorization | Return hosted PayHere authorization parameters; never accept raw card data |
| Customer | GET /api/v1/customers/me/booking-requests/{id} | Read current state, selected offer, server deadlines, payment status and resulting bookingId |
| Customer | POST /api/v1/customers/me/booking-requests/{id}/confirm | Validate deadline and capture once for the accepted offer |
| Customer | POST /api/v1/customers/me/booking-requests/{id}/cancel | Cancel an eligible request and reconcile any active payment operation |
| Provider | GET /api/v1/providers/me/booking-offers | Read owned outstanding offers and their expiry |
| Provider | POST /api/v1/providers/me/booking-offers/{offerId}/accept | Atomically accept an unexpired eligible offer |
| Provider | POST /api/v1/providers/me/booking-offers/{offerId}/decline | Decline an owned offer |
| Provider | POST /api/v1/providers/me/bookings/{jobId}/arrived | Record arrival without starting service |
| Customer | GET /api/v1/customers/me/bookings/{jobId}/verification-codes | Read only currently permitted own start/completion code |
| Provider | POST /api/v1/providers/me/bookings/{jobId}/start | Verify customer start code and transition once |
| Provider | POST /api/v1/providers/me/bookings/{jobId}/complete | Verify separate completion code and transition once |
| PayHere | POST /api/v1/payments/payhere/notify | Verify gateway callback signature and merchant/order/amount/currency before recording state |

All writes require authentication/ownership except authenticated-by-signature
PayHere callbacks. Gateway notification has no customer bearer-token dependency.
Apply idempotency to create, authorization initiation, confirmation/capture and
lifecycle actions. A retry with a changed body and the same key returns a conflict.
Never retry an uncertain capture as a new logical payment.

Request inputs include selected serviceTypeId/optionId, future scheduling and
delivery fields, providerFilters, required answers, pricingSelections and an
optional target provider for DIRECT_REQUEST. Derive selection mode, rate, amount,
currency, timeouts and eligibility rules from server configuration. OPEN_REQUEST
must omit a target provider. Quote/matching must reject unsupported mode or
fulfillment combinations with a precise error.

## Configuration and availability

Add validated option-owned request policy settings for dispatch enabled/mode,
minimum advance notice, dispatch order/search limits, provider offer timeout,
customer confirmation timeout and verification-code policy. Defaults are proposed,
not existing behavior. Respect existing allowed payment methods and shared-option
configuration. Reject conflicting CARD-only rollout settings rather than quietly
ignoring an option's payment restrictions.

For the scheduled MVP retain provider option qualification, reusable approvals,
market, coverage, working hours, configured capability filters and capacity checks.
Validate again at offer acceptance and hold capacity until payment is resolved.
Persist these temporary reservations so other searches/bookings count them too.
This is not live GPS matching and must not be labelled Ride Now. Existing trip
duration estimates do not account for travel to pickup or the previous job's end
location; that remains a separate availability improvement.

Fulfillment matters: ONE_CUSTOMER_MANY_PROVIDERS requires the configured number
of distinct accepted providers and capacity reservations before customer
confirmation/capture. One acceptance cannot confirm a team booking. Either
implement this fully or explicitly disable request dispatch for that model in the
first rollout; do not silently treat it as one provider. Existing fixed-location
options also need an unambiguous venue compatible with the selected providers.

## Notifications, codes and expiration

Use a persistent outbox so committed offers produce retryable push delivery and
expiry/acceptance revocations. Provider apps need registered push tokens and
platform-specific notification sound/channel handling. A backend event alone
cannot guarantee audible ringing on a locked phone or override notification
permissions. Polling the offer API remains a recovery path; every accept checks
the server deadline, regardless of what an old notification displays.

Offer timeouts, overall dispatch timeout and customer-confirmation deadlines need
durable processing that resumes after restart. Atomic state transitions prevent
late acceptance, double assignment and duplicate captures. No provider match,
customer decline or timeout must stop dispatch and release local reservations.
Do not promise immediate bank-side hold release unless PayHere provides a verified
supported mechanism; reconcile separately from request cancellation.

Use independently generated, one-time start and completion codes; store protected
verifiers with expiration and bounded attempt counters. Never send codes in
provider responses, push messages or logs. Only the owning customer can retrieve
the applicable code. Only assigned providers can verify it, in the correct state.
Add audited support resolution for unavailable customer/code disputes; completion
must not become permanently impossible because a phone is unavailable. Payment
capture at confirmation and service start/completion are separate events.

## PayHere constraints and pending product decision

Official references:

- https://support.payhere.lk/hold-on-card
- https://support.payhere.lk/api-%26-mobile-sdk/authorize-api
- https://support.payhere.lk/api-%26-mobile-sdk/capture-api
- https://support.payhere.lk/automated-charging

PayHere's published hold flow supports Visa/Mastercard credit cards only. It
reserves the authorized amount, reducing available credit; funds are not yet
captured into merchant payment settlement. Capture must be for the authorized
amount or less, within seven days. Uncaptured holds expire automatically; do not
claim cancellation immediately unblocks funds. Tokenized card preapproval is
consent to charge later, not a sufficient-funds guarantee for this booking.

Pending choice: credit-card-only authorization before dispatch, or debit-card
support with a different sequence such as matching first and collecting payment
after customer confirmation. Do not substitute tokenization for a booking hold.
Document cancellations/refunds after successful capture before enabling live
charges. Bookings confirmed promptly can be charged at confirmation even when
the service is scheduled later; an authorization left uncaptured for more than
seven days cannot support a later capture.

Gateway credentials, live merchant API enablement, allowed server IP/domain,
verified callbacks, reconciliation and provider-app push integration require
configuration and end-to-end testing before production rollout.

## Required verification before rollout

Exercise both selection modes, required-field validation, unavailable providers,
repeated callback/action delivery, competing accepts, overlapping capacity,
expiry/restart recovery, payment success/failure/unknown outcomes, capture versus
cancel races, callback authenticity, cross-account access and code misuse.
Sandbox authorization/capture tests must show no double charge or confirmed
booking without verified payment. Verify actual Android/iOS notification behavior
on devices; backend tests alone cannot establish ringing behavior.
