# Pricing based on provider requirement choices

Configure one rate for a combination such as Caregiver → Ride → Car + Male driver.
This uses existing SELECT/MULTI_SELECT provider requirement choice values; no
duplicate vehicle catalog or separate variant entity is required. Icons and labels
remain presentation metadata. Conditions reference stable values such as `CAR`.

## Configure rates

Requires SUPER_ADMIN. Existing market-service rate endpoints now accept `conditions`:

| Method | Path | Result |
| --- | --- | --- |
| POST | `/api/v1/admin/market-services/{offeringId}/rates` | 201, full rate including conditions |
| PUT | `/api/v1/admin/market-services/{offeringId}/rates/{rateId}` | 200, full replacement including conditions |
| GET | `/api/v1/admin/market-services/{offeringId}/rates` | 200, paginated rates including conditions |
| PATCH | `/api/v1/admin/market-services/{offeringId}/rates/{rateId}/status` | 200; reactivation checks conflicts again |

The offering determines the market, main service and currency. Example POST body:

```json
{
  "optionId": 12,
  "billingType": "ROUTE_BASED",
  "baseFare": 250,
  "pricePerKm": 100,
  "minimumFare": 500,
  "conditions": [
    {"fieldId": 45, "choiceValue": "CAR"},
    {"fieldId": 46, "choiceValue": "MALE"}
  ]
}
```

For PUT, also send `effectiveFrom` and all editable pricing fields to retain.
Omitted or empty `conditions` on PUT clears the previous conditions. The body can
also contain existing effective-window and geographical-area fields. Time-based
options use the existing `FIXED`, `HOURLY` or `DAILY` billing type and `rate`.

Example response (illustrative IDs):

```json
{
  "rateId": 71,
  "marketId": 1,
  "serviceTypeId": 2,
  "optionId": 12,
  "currencyCode": "LKR",
  "billingType": "ROUTE_BASED",
  "durationMinutes": null,
  "rate": null,
  "baseFare": 250,
  "pricePerKm": 100,
  "minimumFare": 500,
  "geographicalAreaName": null,
  "areaLatitude": null,
  "areaLongitude": null,
  "areaRadiusKm": null,
  "effectiveFrom": "2026-10-21T12:00:00",
  "effectiveTo": null,
  "status": "ACTIVE",
  "conditions": [
    {"fieldId": 45, "choiceValue": "CAR"},
    {"fieldId": 46, "choiceValue": "MALE"}
  ]
}
```

Each condition is ANDed. Fields must belong to the main service and apply to the
option, be ACTIVE, PROVIDER_REQUIREMENT, SELECT or MULTI_SELECT, customer-visible,
searchable and unconditional. Values must exist in the field's current choices.
Only one choice per field and at most ten pricing fields are accepted. Conditional
rates require `optionId`; service-level legacy rates cannot have conditions.

## Customer selection, quote and booking

Discovery continues to use existing `providerFilters`. Booking does not reuse a
previous search request: send the final single-choice selections separately in
`pricingSelections`. The provider requirement can be MULTI_SELECT (a provider may
offer Car and Van), while a booking selects exactly one vehicle.

Requires CUSTOMER. Send the same complete body to:

- `POST /api/v1/customers/me/bookings/quote` → 200, preview without saving or reserving capacity/wallet funds.
- `POST /api/v1/customers/me/bookings` → 201, booking with price and selection snapshots.

Example route request (choose a future market-local pickup time):

```json
{
  "serviceTypeId": 2,
  "optionId": 12,
  "providerId": 15,
  "deliveryMode": "PROVIDER_TO_CUSTOMER",
  "requestedPickupTime": "2027-01-10T10:00:00",
  "pickup": {"address": "Pickup", "latitude": 6.9271, "longitude": 79.8612},
  "destination": {"address": "Destination", "latitude": 6.9510, "longitude": 79.8720},
  "paymentMethod": "CASH",
  "answers": [],
  "pricingSelections": [
    {"fieldId": 45, "choiceValue": "CAR"},
    {"fieldId": 46, "choiceValue": "MALE"}
  ]
}
```

Provider ID(s), eligibility, availability, location, payment method and required
booking answers must be valid for quotes as well as bookings. Every selected
provider must offer all selected choices using approved provider answers (or
allowlisted profile values with PROVIDED status). A missing, pending or mismatching
provider value rejects the request. Team bookings validate every team member.

Quote response example (illustrative computed distance):

```json
{
  "rateId": 71,
  "currencyCode": "LKR",
  "expectedAmount": 750.00,
  "startDatetime": "2027-01-10T10:00:00",
  "expectedEndDatetime": "2027-01-10T10:08:00",
  "estimatedDistanceKm": 5.00,
  "pricingSelections": [
    {"fieldId": 45, "choiceValue": "CAR"},
    {"fieldId": 46, "choiceValue": "MALE"}
  ],
  "pricingSnapshot": {
    "billingType": "ROUTE_BASED",
    "durationMinutes": null,
    "rate": null,
    "baseFare": 250,
    "pricePerKm": 100,
    "minimumFare": 500,
    "conditions": [
      {"fieldId": 45, "choiceValue": "CAR"},
      {"fieldId": 46, "choiceValue": "MALE"}
    ]
  }
}
```

Route distance is calculated by the existing backend straight-line estimator;
the example amount above shows the calculation for 5 km, rather than promising
that distance for the example coordinates. Quotes do not guarantee availability
or lock a fare. Creating a booking recalculates and revalidates it. Booking and
booking-list responses include `pricingSelections` and `pricingSnapshot` alongside
the existing `rateId` and `expectedAmount`. Old bookings return null snapshots.
Later changes to a rate do not change the recorded selections, parameters or fare.

## Matching and conflicts

1. Keep ACTIVE rates effective at the time of quoting/booking and applicable to the location.
2. Require a single selected value for every distinct pricing field used by those rates.
3. Match all conditions; prefer the rule with the greatest number of conditions.
4. For equal condition counts, prefer the smallest matching geographical radius;
   an area rate precedes a general rate.
5. Equally specific matching conditional rates return 409. Legacy unconditional
   rates retain their latest-effective-from precedence.

An unconditional rate can serve as an explicit fallback, but customers cannot
omit required pricing selections to bypass conditional rates. Missing selections,
extra fields, unknown choices and unsupported provider combinations return 400;
no matching price returns 404. Admin writes reject overlapping equally specific
conditional rules with overlapping effective windows and equal geographical
priority. Writes for the same market-service offering are serialized with a row
lock. More specific rules and narrower geographical overrides are allowed.

## Deployment

Apply [COMBINATION_PRICING_SCHEMA.sql](COMBINATION_PRICING_SCHEMA.sql) once before
deploying against an existing MySQL database. Existing rates need no condition
backfill. Hibernate schema update can create the additions in development. The
shared-service-option migration remains a separate prerequisite for that feature.
