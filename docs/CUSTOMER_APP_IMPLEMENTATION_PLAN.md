# Customer app flow and backend implementation plan

Prepared from the local backend on 2026-10-16. This is a proposed implementation
plan, not a claim that the proposed endpoints exist or are deployed.

Implementation update: steps 1–6 now have a local API path documented in
[Customer discovery API](CUSTOMER_DISCOVERY_API.md). New catalog/options/context
and provider-search routes are implemented with shared schedule/capacity checks.
The assessment below records the original planning baseline; dispatch, live GPS,
holds, quotes, and booking lifecycle routes remain proposed. See the API guide for
current limits and implementation details. Deployment is not verified.

## Product flow

1. Register/login as CUSTOMER; complete the basic profile.
2. Choose the operating market and location, or an online service context.
3. Browse active market services, with images, responsibilities, options, and
   customer access badges. Restricted services stay visible with an action to
   complete requirements; do not silently hide them as if unavailable.
4. Open a service/option. Check global + service + option customer requirements
   before provider discovery. If blocked, show the missing form, upload/review
   flow, or age restriction; preserve the customer's selected service as a return
   destination. Approval changes the derived eligibility; a customer does not
   manually set themselves active.
5. Choose delivery mode, time/duration or pickup/destination, and any booking
   answers that affect matching. No provider is described as available without a
   specific time interval and location/delivery context.
6. Search available providers/locations, displaying a list or map appropriate to
   the option. Filter booked/full/unqualified/out-of-area providers on the server.
7. DIRECT_REQUEST: select the required number of providers. OPEN_REQUEST: show
   eligible supply/coverage and request automatic matching instead of requiring
   customer-supplied provider IDs.
8. Complete remaining per-booking fields, preview price and responsibilities,
   select an allowed payment method, and confirm the request.
9. Backend rechecks eligibility, provider availability, current configuration,
   price, and capacity atomically. Show pending provider acceptance/matching as
   pending, not a confirmed booking.
10. Show booking status, provider acceptance, payment status, and cancellation
    options. For dispatched services, show tracking only after the appropriate
    assignment/permission stage.

Existing authentication: /api/v1/auth/*; profile: /api/v1/customers/me/profile.
Existing catalog: /api/v1/provider-types and /{serviceTypeId}/options/setup/schema
surfaces. These generic catalog responses are not a complete customer marketplace
with market filtering, access badges, matching, prices, or availability.

## Service access and the application form

Keep three independent concepts in the API and UI:

| Concept | Meaning | Customer action |
| --- | --- | --- |
| Catalog availability | Service is active and offered in the selected market | Browse or change market |
| Customer eligibility | Customer has met the applicable personal/document rules | Complete details or wait for review |
| Supply availability | A qualified provider/resource has capacity at the requested place/time | Search, change time, or change location |

Customer requirements APIs already exist locally; see CUSTOMER_REQUIREMENTS_API.md.
Use existing GET requirements and PUT requirement-submissions for "Complete
requirements"/"Apply". No additional application endpoint is needed for the
current derived eligibility design. If explicit manual per-customer activation
independent of requirements is later wanted, it needs a separate policy/state.

Service-wide eligibility badges must not imply eligibility for every option. If
service-level checks pass but option rules vary, show CHECK_OPTION; evaluate again
when an option is selected. Proposed access states are UI summaries, not existing
stored status enums: ELIGIBLE, NEEDS_DETAILS, PENDING_REVIEW, REJECTED,
RESTRICTED, CHECK_OPTION. Return structured reasons from the existing evaluator.

Browse restricted service descriptions, but gate provider search/detail access
on eligibility by default. Direct API calls, quote creation, matching, holds,
and booking must independently enforce this; hiding a mobile button is insufficient.

## Shared matching and availability engine

Extract reusable policy services rather than duplicating private booking checks
in a new search controller. Search, slots, preview, acceptance, and final booking
must call the same logic with the same normalized booking context.

Candidate filters:

- Active service and active market offering; active option belongs to service.
- Customer eligible for that exact service/option.
- Provider account active; service assignment active and approved; selected
  provider option approved and active for both provider and administrator.
- Provider market equals the requested market; enabled delivery mode matches.
- Weekly schedule covers the entire requested interval when enabled, respecting
  provider unavailable dates and the market timezone. Split multi-day requests
  by local calendar date; handle timezone transitions and boundary times.
- Physical locations/service areas are active. PROVIDER_TO_CUSTOMER requires
  coverage of the customer meeting point or route pickup. CUSTOMER_TO_PROVIDER
  requires a valid fixed provider location. ONLINE has no physical radius.
- Provider attributes satisfy matching requirements explicitly configured for
  that option. Do not guess that an arbitrary dynamic answer is a search filter.
- Correct overlap/capacity policy, including active holds and accepted work.
- A valid price/quote mechanism exists for the configured pricing owner.

Current weekly availability exists as provider configuration, but booking creation
does not consult it. Provider account availabilityStatus also is not currently
checked by booking creation. For proposed immediate dispatch, require fresh online
presence and AVAILABLE. For future appointments, use the future schedule rather
than assuming a provider who is offline now cannot accept future work.

## Capacity and time rules

Use half-open intervals [start, end), so a booking ending at 10:00 does not
conflict with one starting at 10:00. Add explicit configurable preparation/travel
buffers when needed; do not silently invent buffer durations.

| Configuration | Proposed discovery/booking behavior |
| --- | --- |
| ONE_AT_A_TIME | Exclude provider when overlapping work/holds exist across options of the same main service, matching the current policy |
| MANY_AT_A_TIME + MANY_CUSTOMERS_ONE_PROVIDER | Count occupied units by option and fixed location when relevant; return only locations with capacity throughout the full interval |
| MANY_AT_A_TIME + ONE_TO_ONE | Current behavior still blocks overlapping work for the same option; this mode alone does not create unlimited capacity |
| ONE_CUSTOMER_MANY_PROVIDERS | Require the configured number of distinct eligible providers available simultaneously; reserve the whole team atomically |

Current ONE_AT_A_TIME does not block across unrelated main services. Preserve
that contract initially; add an explicit exclusive-person/shared-resource policy
before claiming a human cannot be double-booked across different services.

Capacity is the peak simultaneous occupied units, not the total count of jobs
that overlap some portion of a long requested interval. Use capacityUsed and
deduplicate each job across legacy primary-provider and provider-assignment rows.
Current count/max queries are a starting point, not sufficient for mixed legacy
records or precise resource occupancy. Version 1 requests consume one unit; expose
quantity only with corresponding pricing and capacity validation.

Define the reserving status set explicitly. Accepted/confirmed/in-progress work
and unexpired holds consume capacity. Cancelled/rejected/expired work does not.
Pending requests need bounded reservation/offer expiry so abandoned requests do
not block capacity forever. Provider rejection, cancellation, payment failure,
timeout, and hold expiry release their reservations exactly once.

Use database locking or atomic inventory allocation at commit. For teams, lock
provider/resource rows in stable ID order. A transaction wrapping count-then-save
alone does not prevent two customers from reserving the last slot concurrently.
Search results are a snapshot, never a guaranteed reservation.

## Map behavior and immediate dispatch

- CUSTOMER_TO_PROVIDER: pins represent public business/service locations.
- PROVIDER_TO_CUSTOMER scheduled work: show coverage/provider service bases with
  an honest location label; an uploaded service area is not live GPS.
- ONLINE: list results; no physical map is necessary.
- Uber-style immediate dispatch requires new provider location/heartbeat data,
  freshness checks, consent, and routing/ETA integration. Keep private home
  addresses hidden and use approximate public pins before assignment.
- Route-based options currently use straight-line distance and average speed.
  Do not label those results as road travel time. Road-routing integration should
  supply distance/ETA before production route matching or time reservation.

The current option DTO supports TIME_BASED/ROUTE_BASED, not an ASAP scheduling
mode. If immediate dispatch is needed, add explicit immediate/scheduled support,
lead time, booking horizon, and cancellation/offer-expiry configuration.

## Dynamic forms at the correct stage

1. Customer requirements: personal details and reusable verified evidence, checked
   before restricted provider search and again before committing a booking.
2. Matching inputs: option, delivery mode, time/location, plus explicit configured
   matching attributes such as language or required equipment. Collect these
   before search. Proposed schema metadata can identify stage and supported
   operator/source; the server must validate it against provider data.
3. BOOKING_FIELD: answers attached to this booking, including conditional questions
   and files. Non-matching fields can be collected after provider selection.

Load forms from current definitions, not hardcoded service names in mobile code.
Carry schema/configuration versions in previews and booking snapshots. If admin
changes a required question or rule, refresh the form instead of submitting stale
answers silently. Fix booking-upload validation: it currently accepts only
providers/{userId}/ keys, while new customer private uploads return customers/ keys.
Reuse private ownership/file metadata validation for both customer upload paths.

## Proposed customer APIs (not implemented)

| API | Purpose |
| --- | --- |
| GET /api/v1/customers/me/services?marketId=1 | Market catalog with media, options, access summary and actions; no per-card N+1 eligibility calls |
| GET /api/v1/customers/me/services/{serviceTypeId}/options | Customer-ready options with configuration and exact access state |
| GET /api/v1/customers/me/booking-context?serviceTypeId=3&optionId=12 | Form/schema, delivery, scheduling, matching, payment and fulfillment settings with versions |
| POST /api/v1/customers/me/provider-search | Paginated/map-bounded available providers or locations for validated context |
| GET /api/v1/customers/me/providers/{providerId} | Public provider details, scoped to authorized service/option context |
| POST /api/v1/customers/me/availability-slots | Alternative available times for a selected provider/location and duration |
| POST /api/v1/customers/me/booking-previews | Validate answers and context; return estimate/quote, current availability and responsibilities |
| POST /api/v1/customers/me/booking-holds | Optional bounded capacity hold before payment/confirmation |
| POST /api/v1/customers/me/booking-requests | Proposed OPEN_REQUEST matching/offer lifecycle without preselected provider IDs |
| GET /api/v1/customers/me/bookings/{bookingId} | Own booking details/status; list already exists |
| POST /api/v1/customers/me/bookings/{bookingId}/cancel | Policy-based cancellation and capacity release |

Extend the existing POST /api/v1/customers/me/bookings for direct booking,
preview/hold references and Idempotency-Key. Do not create two inconsistent booking
engines. Proposed supporting provider APIs: private presence/location heartbeat,
offers list, accept/reject, and attendance/status transitions. Add SSE/WebSocket
events or bounded polling for matches, approval changes, capacity changes, and
booking status; reconnect must reconcile through a current GET response.

Example proposed search request:

```json
{
  "marketId": 1,
  "serviceTypeId": 3,
  "optionId": 12,
  "deliveryMode": "PROVIDER_TO_CUSTOMER",
  "startAt": "2026-11-01T08:00:00+05:30",
  "endAt": "2026-11-01T10:00:00+05:30",
  "location": {"latitude": 6.9271, "longitude": 79.8612},
  "matchingAnswers": [],
  "pageSize": 20
}
```

New discovery contracts should use offset-aware timestamps normalized to UTC;
market timezone controls schedule interpretation. Adapt existing LocalDateTime
booking fields explicitly rather than silently changing their meaning.

Search response should include query time/expiry, cursor, normalized context,
eligibility, requiredProviderCount, and candidates with providerId, safe display
profile, option/location IDs, approximate/public coordinates, distance or sourced
ETA, remaining capacity, and price availability. Return empty supply with clear
NO_AVAILABLE_PROVIDERS and alternate actions; return CUSTOMER_INELIGIBLE with
requirement actions when access is blocked. Never expose other customers' booking
details to explain a provider's unavailability.

## Booking confirmation and lifecycle

Preview is not a booking and should not charge a customer. Lock/revalidate current
inventory and configuration at final submission; accept an idempotency key scoped
to customer and request hash. A replay returns the same booking; reuse with a
different payload returns a conflict. Use specific conflict codes for changed
availability, expired hold, changed quote, and changed requirements.

DIRECT_REQUEST and OPEN_REQUEST exist as option configuration, but current booking
creation requires selected provider IDs for both and does not implement offer
dispatch/acceptance. Add that lifecycle rather than presenting OPEN_REQUEST as
already operational. Existing JobOffer entity alone is not a working matching API.

ADMIN and PROVIDER pricing owners are configurable, but current creation always
calls admin-rate pricing. Provider pricing needs an actual provider quote/rate
contract. Keep it unavailable for unsupported flows until implemented, rather
than silently charging the admin rate.

Display only allowedPaymentMethods. Selecting CARD/BANK_TRANSFER/etc. currently
records a payment choice and PENDING status; automatic collection/refunds need a
payment integration. Never mark payment successful from a client flag or receipt
alone. Apply existing refund-policy configuration through a defined cancellation
lifecycle and preserve the accepted policy/price snapshot on the booking.

## Implementation order and acceptance criteria

1. Shared correctness foundation: extract availability/capacity logic, enforce
   schedules/market/account/location status, fix customer booking upload keys,
   define status/expiry semantics, add atomic allocation and idempotency.
2. Customer catalog and access flow: market-aware services/options, eligibility
   badges/actions, forms, review return flow, aggregate queries.
3. Provider search/list/map and slot alternatives: reuse the shared checks and
   pagination/spatial indexes; distinguish fixed locations from live positions.
4. Booking context/preview/direct confirmation: dynamic answers, quote snapshots,
   allowed payment selection, capacity holds where needed, owned booking detail.
5. OPEN_REQUEST matching and provider accept/reject; team matching and expiry.
6. Immediate live location/routing, notifications, payment settlement, cancellation
   and refunds as production lifecycle modules.

Acceptance tests must include: eligibility before direct search calls; service vs
option restrictions; schedule gaps and unavailable dates; cross-midnight/timezone
boundaries; out-of-area/inactive locations; same-service exclusive overlaps;
multi-capacity partially occupied intervals and location overrides; all members
of a required team; simultaneous last-slot reservations; expired/released holds;
idempotent retries; stale configuration; customer private upload ownership;
direct vs open request behavior; provider rejection/rematching; payment-method
restrictions; no exact private GPS or other-customer booking leakage.

This plan does not change runtime behavior. Existing local implementation and
gaps above are grounded in CustomerBookingService, customer requirement services,
option configuration, availability entities, pricing services, and controller
routes inspected for this plan.
