# Customer app: login through provider discovery

The complete endpoint-by-endpoint frontend contract, including full request and
response examples, nested schemas, enums, errors, and booking handoff is in
[Customer App frontend reference](CUSTOMER_APP_FRONTEND_API.md). The Customer App
Google Docs tab follows that full reference.

Implemented locally on 2026-10-16; deployment is not verified. All new routes
require a CUSTOMER bearer token and a saved customer profile. Existing account,
profile, and customer requirement APIs remain the entry points.

## Mobile sequence

| Step | Request |
| --- | --- |
| Register or login | Existing `/api/v1/auth/*` with CUSTOMER account type |
| Complete profile | `PUT /api/v1/customers/me/profile` |
| Choose market | `GET /api/v1/markets`; retain selected marketId in the app |
| Browse market services | `GET /api/v1/customers/me/services?marketId=1` |
| Choose option | `GET /api/v1/customers/me/services/3/options?marketId=1` |
| Read form/settings | `GET /api/v1/customers/me/booking-context?marketId=1&serviceTypeId=3&optionId=12` |
| Complete access requirements | Existing requirements/submission APIs below |
| Search providers | `POST /api/v1/customers/me/provider-search` |

Market/location selection is request context, not an update to a new customer
market entity. A physical search needs location coordinates; ONLINE does not.

## Service catalog and eligibility

Services are filtered to active offerings in the selected active market. Each
service card includes the existing service response (name, description, icon,
coverImage, gallery, responsibilities) and an `access` object:

```json
{
  "status": "NEEDS_DETAILS",
  "action": "COMPLETE_REQUIREMENTS",
  "eligibility": {
    "eligible": false,
    "reasons": [{"code": "MISSING", "requirementCode": "NIC", "message": "NIC: MISSING"}]
  }
}
```

Restricted services stay visible. Status/action pairs:

- ELIGIBLE / CONTINUE: current service-level checks pass.
- CHECK_OPTION / SELECT_OPTION: service checks pass but at least one option has
  additional restrictions. Read the exact selected option's access state.
- NEEDS_DETAILS / COMPLETE_REQUIREMENTS: collect information or resolve DOB verification.
- PENDING_REVIEW / WAIT_FOR_REVIEW: applicable reasons are pending reviews.
- REJECTED / COMPLETE_REQUIREMENTS: correct and resubmit rejected evidence.
- RESTRICTED / VIEW_REQUIREMENTS: an age restriction prevents access.

The nested reasons and form submission statuses are authoritative. For example,
a DOB reason can require a look at the form to distinguish missing data from
pending evidence review; the access badge is a summary, not a persisted status.

Option cards contain `option` (the existing complete ServiceOptionResponse,
including media, responsibilities, delivery configuration, scheduling, selection,
fulfillment, capacity, pricing owner, payment methods and dynamic definitions)
and the exact option's `access`.

For COMPLETE_REQUIREMENTS, retain the selected service/option in navigation and use:

```http
GET /api/v1/customers/me/requirements?serviceTypeId=3&optionId=12
PUT /api/v1/customers/me/requirement-submissions
GET /api/v1/customers/me/eligibility?serviceTypeId=3&optionId=12
```

See [Customer requirements API](CUSTOMER_REQUIREMENTS_API.md). "Apply" uses this
form/submission flow; it does not let the customer set an approval flag. Refresh
eligibility after review. Provider search independently checks eligibility and
returns 403 ProblemDetail with structured reasons if access is blocked.

## Booking context and dynamic forms

Map markers are independent of pricing variants. A search with vehicle CAR and
gender FEMALE can match a marker rule containing only CAR. Render the single
`mapMarker.icon` returned on each candidate; do not generate markers for every
combination of filter values. See [marker configuration](COMBINATION_MAP_MARKERS_API.md#vehicle-markers-independent-of-pricing-variants).
Search does not return prices; use `POST /api/v1/customers/me/bookings/quote` with
the applicable `pricingSelections` for pricing.

Context returns `marketId`, market `timezone`, an option card in `selection`,
`bookingSchema`, `matchingFilters`, and `liveTrackingSupported: false`.
The booking schema contains common BOOKING_FIELD definitions and the selected
option's fields, plus service setup. Use field types, required flags, choices,
upload rules, and conditions to render the form. Customer eligibility details are
reusable; BOOKING_FIELD answers belong to each booking.

Supported matching filters are `languages`, `requiredSkillIds`, and admin-enabled
`providerFilters`. Context also returns `providerFilters`, an array of dynamic
filter definitions for this exact service/option; see below.
Use language codes SINHALA, ENGLISH, TAMIL and skill IDs configured for the service
in the schema's service setup. All requested languages and approved skills must
match the provider. An arbitrary dynamic booking answer is not automatically a
provider attribute filter: no such mapping exists in current admin configuration.
Collect normal dynamic booking answers before the existing final booking call;
their existing server validation still applies there.

### Dynamic provider filters

Super Admin enables fields using the existing custom-field POST/PUT endpoints:

```json
{
  "optionIds": [12],
  "scope": "PROVIDER_REQUIREMENT",
  "reuseScope": "PROVIDER",
  "code": "GENDER",
  "label": "Gender",
  "fieldType": "SELECT",
  "required": false,
  "requiresApproval": false,
  "options": ["MALE", "FEMALE", "OTHER"],
  "customerVisible": true,
  "searchable": true,
  "filterOperator": "IN"
}
```

Both flags default to false, including existing definitions. Searchable requires
customerVisible. Only unconditional PROVIDER_REQUIREMENT fields of types SELECT,
MULTI_SELECT, NUMBER and BOOLEAN can be published. Uploads, text/contact fields and
BOOKING_FIELD definitions cannot be enabled. Select choices must be unique nonempty
string values. Choices may be strings or objects with value, label and optional icon;
see [choice icon contract](DYNAMIC_FIELDS_API.md#icons-for-individual-select-choices).
Filters submit values only, never label/icon objects. PUT replaces these settings
too, so preserve flags when editing a field.
The operator is derived if omitted: IN for choices, RANGE for NUMBER, EQUALS for BOOLEAN.
An explicitly incompatible operator is rejected.

Booking context `providerFilters` example (IDs are illustrative):

Each definition also includes optional `icon` media (`key`, `url`, `altText`,
`displayOrder`), or null for fields without an icon. Render it alongside the label.
See [field icon upload/configuration](DYNAMIC_FIELDS_API.md#optional-field-icons).

```json
[
  {"fieldId": 25, "code": "GENDER", "label": "Gender", "fieldType": "SELECT",
   "options": ["MALE", "FEMALE", "OTHER"], "filterOperator": "IN"},
  {"fieldId": 26, "code": "EXPERIENCE", "label": "Experience years", "fieldType": "NUMBER",
   "options": null, "filterOperator": "RANGE"},
  {"fieldId": 27, "code": "OWN_VEHICLE", "label": "Own vehicle", "fieldType": "BOOLEAN",
   "options": null, "filterOperator": "EQUALS"}
]
```

Add to the existing provider-search body:

```json
{
  "providerFilters": [
    {"fieldId": 25, "value": ["FEMALE"]},
    {"fieldId": 26, "minimum": 3, "maximum": 20},
    {"fieldId": 27, "value": true}
  ]
}
```

This fragment supplements required market/option/time/location fields. Filters are
AND-combined with each other and languages/skills. IN means any selected choice;
MULTI_SELECT matches if any requested choice is present. Number bounds are inclusive;
one bound may be omitted. BOOLEAN takes a JSON boolean. Maximum 50 filters and 100
choices per filter; duplicate field IDs/choices, unknown or inactive fields, other-option
fields, nonsearchable fields, invalid types/choices and inverted/empty ranges return 400.
Omit providerFilters or send [] for no dynamic filtering.

Each candidate has `providerAttributes`, an object keyed by field ID, e.g.
`{"25":"FEMALE","26":5,"27":true}`. Only opted-in customer-visible fields
and APPROVED manual answers or valid PROVIDER_PROFILE/PROVIDED values are included.
Profile bindings may publicly expose gender and derived age only, never raw DOB/identifiers.
See [profile reuse](DYNAMIC_FIELDS_API.md#reuse-provider-profile-values-without-duplicate-inputs).
Fields not requiring
review are automatically APPROVED on submission; this is not an independent verification.
Existing provider/service answer reuse applies. An exact pending/rejected answer overrides
an older reusable approval and is excluded; missing/unapproved/invalid answers never match.
Matching happens before pagination and does not change booking eligibility or reserve capacity.
Display-only customerVisible fields may appear in providerAttributes but cannot be queried.

Persistence: `service_custom_field` gains `customer_visible` and `searchable` booleans
defaulting false, and nullable `filter_operator` varchar. Hibernate schema update handles
these in the current setup. Manually managed deployments must add columns before rollout:

```sql
ALTER TABLE service_custom_field
  ADD COLUMN customer_visible BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN searchable BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN filter_operator VARCHAR(255) NULL;
```

Discovery still scans candidates in memory; dynamic answer resolution adds reads per
candidate with public fields. Batch/index and load-test before large-scale deployment.

## Time-based provider search

```http
POST /api/v1/customers/me/provider-search
Authorization: Bearer <customer-token>
Content-Type: application/json
```

```json
{
  "marketId": 1,
  "serviceTypeId": 3,
  "optionId": 12,
  "deliveryMode": "PROVIDER_TO_CUSTOMER",
  "startAt": "2030-01-07T08:00:00+05:30",
  "endAt": "2030-01-07T10:00:00+05:30",
  "location": {"latitude": 6.9271, "longitude": 79.8612},
  "radiusKm": 30,
  "languages": ["ENGLISH"],
  "requiredSkillIds": [],
  "page": 0,
  "pageSize": 20
}
```

Use real active IDs and future times. `startAt` is required; TIME_BASED also needs
endAt. Requests must be future intervals of at most 31 days. Timestamps include
offsets and are interpreted in the market timezone for schedules. The current
booking database stores market-local LocalDateTime, so requests involving an
ambiguous/nonexistent local time or crossing a DST transition are rejected rather
than interpreted incorrectly. Existing booking datetime fields remain market-local.

Defaults: radiusKm 50 (allowed 0.1–500), page 0, pageSize 20 (maximum 100).
Changing time, option, delivery mode, location, or filters requires another search.
For CUSTOMER_TO_PROVIDER, location is the customer's search centre. For ONLINE,
omit location, pickup, and destination and show a list rather than a physical map.

## Route-based search

For ROUTE_BASED options, use startAt as pickup time, pickup/destination points,
and omit location/endAt:

```json
{
  "marketId": 1,
  "serviceTypeId": 4,
  "optionId": 20,
  "deliveryMode": "PROVIDER_TO_CUSTOMER",
  "startAt": "2030-01-07T08:00:00+05:30",
  "pickup": {"latitude": 6.9271, "longitude": 79.8612},
  "destination": {"latitude": 7.0, "longitude": 79.9},
  "page": 0,
  "pageSize": 20
}
```

The end time uses the same straight-line distance / configured average speed
estimate as current booking creation. This is not road routing or a live ETA.

## Search result

```json
{
  "checkedAt": "2030-01-01T00:00:00Z",
  "marketId": 1,
  "timezone": "Asia/Colombo",
  "startAt": "2030-01-07T08:00:00+05:30",
  "endAt": "2030-01-07T10:00:00+05:30",
  "providerSelectionMode": "DIRECT_REQUEST",
  "requiredProviderCount": 1,
  "status": "AVAILABLE",
  "totalCandidates": 1,
  "page": 0,
  "pageSize": 20,
  "hasNext": false,
  "candidates": [
    {
      "providerId": 15,
      "assignmentId": 14,
      "optionId": 12,
      "providerLocationId": null,
      "displayName": "Jane Doe",
      "profilePhoto": "https://example.com/profile.jpg",
      "averageRating": 4.8,
      "totalRatings": 25,
      "locationLabel": "APPROXIMATE_SERVICE_AREA",
      "mapPoint": {"latitude": 6.93, "longitude": 79.86},
      "distanceKm": 0.5,
      "remainingCapacity": 1,
      "languages": ["ENGLISH", "SINHALA"]
    }
  ]
}
```

Location labels: PUBLIC_SERVICE_LOCATION (fixed business location and selectable
providerLocationId), APPROXIMATE_SERVICE_AREA (rounded coverage centre, no fixed
location ID), ONLINE (null map point/distance). Never present service-area centres
as moving providers or precise home locations. Distance is straight-line distance
to the service-area/business centre, not travel ETA. Other customers' booking
details, provider identity documents, and personal contact details are not returned.

Results are sorted by distance then provider/location IDs. Multiple public locations
can produce multiple candidates for a provider; team counts use distinct provider
IDs. NO_AVAILABLE_PROVIDERS has an empty candidate list, also when too few distinct
providers are available for a team. Change time/location or requirements and retry.
AVAILABLE is a snapshot of individual candidates, not a reserved team or slot.

## Availability and booking consistency

Search checks active provider account, service assignment/approval and both
provider/admin option activation and approval, market, delivery mode, schedules,
unavailable dates, active service areas, and remaining capacity. UNAVAILABLE
providers are excluded. OFFLINE alone does not hide future scheduled appointments;
live immediate dispatch/presence is not implemented.

Weekly slots must cover the entire interval; adjoining slots can combine, gaps
cannot. An unavailable date overrides weekly hours. Schedule-disabled services
skip weekly/date checks. Capacity uses half-open [start, end) intervals, peak
simultaneous capacityUsed, location-specific capacities, and deduplicated jobs
across primary and team assignments. ONE_AT_A_TIME blocks overlapping work across
options of the same main service, including when the already-booked option is
exclusive. It does not impose exclusivity across unrelated main services.

Final booking now uses the same qualification/schedule/capacity policy. It locks
provider rows in stable ID order and uses locked current inventory reads before
saving; multi-provider requests check every member. Search does not reserve a slot.
Future-time validation runs in the market timezone instead of bean-validation
against the server clock. Customer BOOKING_FIELD uploads now validate actual private
customer S3 keys, metadata, and configured file types/sizes.

Pending jobs currently consume capacity until their status changes. Reservation
holds, idempotent booking retries, automatic offer expiry, and OPEN_REQUEST dispatch
are subsequent lifecycle modules, not added by these six discovery steps. Current
booking creation still requires provider IDs even if option configuration is
OPEN_REQUEST; context exposes that configuration but does not promise a dispatcher.
Team selection must use providers compatible with the actual single booking venue;
the search result is not a grouped team offer. Provider-owned quote collection,
live GPS, actual map rendering, and payment collection also remain separate modules.

## Errors, performance and validation

400: invalid location/time/mode/skill/filters; 401: unauthenticated; 403: wrong role
or customer ineligible; 404: incomplete profile or unavailable market/service/option.
Availability can change after search; refresh it when final booking validation
fails. The API never accepts client assertions that a provider is available.

The initial implementation evaluates service candidates and pages filtered results
in memory. Before large-scale deployment, replace the candidate scan and repeated
lookups with batched/spatial queries and load-test it. Repository locks/current-read
query contracts and ordering are unit tested; production MySQL contention tests
are still needed to validate deployed isolation/indexing behavior.
