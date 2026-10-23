# Fully shared service options — 23 October 2026

Implemented in the working tree. Not deployed; the production migration has not been run.
For shared-field owner metadata, cross-service edit examples and legacy field reconciliation,
see [SHARED_FIELD_FRONTEND_CONTRACT.md](SHARED_FIELD_FRONTEND_CONTRACT.md).
This supersedes the former service-local option fields, rates and marker behavior.
There are no per-service overrides for an option. Only membership `displayOrder`
and `status` vary between linked main services.

## Identity and ownership

Create `CARE_RIDE` once and link its `optionId` to Ride and Caregiver. Keep
`serviceTypeId` in customer requests as the selected navigation context. The
option response also returns `configurationServiceTypeId`: the stable original
configuration namespace used internally for shared definitions. This is not an
additional provider registration requirement for each navigation context.

Shared: booking/delivery/fulfillment settings, payment-method labels, media,
`mapMarkerFieldId`, provider requirements, booking fields, choice icons/markers,
option-specific document requirements, market/option prices and marker rules.
The shared catalog also returns `configurationServiceTypeId`. Its legacy
`services[].mapMarkerFieldId` is now a compatibility projection of the shared
value, not a service-specific override.

Global and main-service requirements remain separate administrative concepts.
Once an option is selected, its original configuration's common booking/provider
fields and customer service-eligibility policy are used consistently. Option
eligibility targets match the same option through every linked service. Do not
create copies of option-specific fields under each navigation service.

## Super Admin APIs

Every endpoint requires `SUPER_ADMIN`. JSON requests use `Content-Type: application/json`.

| Method | Endpoint | Response |
|---|---|---|
| GET | `/api/v1/admin/service-options?page=0&size=20` | 200 paginated shared catalog |
| GET | `/api/v1/admin/service-options/{optionId}` | 200 complete `ServiceOptionResponse` |
| PUT | `/api/v1/admin/service-options/{optionId}` | 200 complete option; existing full option request |
| GET | `/api/v1/admin/service-options/{optionId}/custom-fields` | 200 field array, including inherited common fields/inactive fields |
| POST | same `/custom-fields` | 201 complete field |
| PUT | `.../custom-fields/{fieldId}` | 200 complete field |
| PATCH | `.../custom-fields/{fieldId}/status` | 200 complete field |
| GET | `/api/v1/admin/service-options/{optionId}/document-requirements` | 200 document requirement array |
| PUT | same `/document-requirements` | 200 replacement requirement array |
| GET | `/api/v1/admin/service-options/{optionId}/map-marker-rules` | 200 rule array |
| POST | same `/map-marker-rules` | 201 complete rule |
| PUT | `.../map-marker-rules/{ruleId}` | 200 complete rule |
| DELETE | `.../map-marker-rules/{ruleId}` | 204 |

Existing service-scoped option/field/marker URLs remain supported. They validate
membership and resolve the same canonical option configuration. A field response
identifies its actual configuration namespace; it need not equal the navigation
service ID. `PUT` option still replaces settings/media; embedded `dynamicFields`
still upserts supplied definitions. Omitted media clears it. Updating the shared
option through any link changes it everywhere. Do not implement an override UI.

To change one link's status/order, continue using:

```http
POST /api/v1/admin/provider-types/{serviceTypeId}/options/{optionId}/link
```

```json
{"displayOrder":2}
```

The response is 200 and the membership becomes ACTIVE. `PATCH .../{optionId}/status`
with `{"status":"INACTIVE"}` or `DELETE .../{optionId}/link` affects only that
membership. The latter returns 204. The original configuration namespace remains
stable even when its original membership is inactive.

### Configure a field once

All IDs below are illustrative; use actual returned IDs.

```http
POST /api/v1/admin/service-options/120/custom-fields
```

```json
{
  "scope":"PROVIDER_REQUIREMENT",
  "reuseScope":"ASSIGNMENT",
  "code":"VEHICLE_TYPE",
  "label":"Vehicle type",
  "fieldType":"MULTI_SELECT",
  "required":true,
  "requiresApproval":true,
  "customerVisible":true,
  "searchable":true,
  "filterOperator":"IN",
  "options":[
    {"value":"CAR","label":"Car","icon":{"key":"service-icons/car.svg","url":"https://cdn.example.com/car.svg"},
     "mapMarkerIcon":{"key":"service-icons/car-pin.svg","url":"https://cdn.example.com/car-pin.svg"}},
    "BIKE","VAN","TUK_TUK"
  ],
  "condition":null
}
```

This defaults field applicability to option 120. A 201 response returns the stable
field ID, e.g. 145. Both main-service contexts return this same ID and choices.
Explicit option IDs must include the path's option and share a configuration
namespace. Updates preserve existing applicability if neither option ID property
is supplied. Field status body is `{"status":"ACTIVE"}` or INACTIVE.

Per-booking questions use the same field endpoint with `scope: BOOKING_FIELD`.
They are independent of provider filters. Manual provider answer approval follows
the one registration; a different navigation service does not require resubmission.
Profile-backed gender uses `valueSource: PROVIDER_PROFILE`, `profileField: gender`,
`requiresApproval: false`, and schema-advertised exact values.

### Option document requirements

```http
PUT /api/v1/admin/service-options/120/document-requirements
```

```json
{"documents":[{"documentTypeId":4,"required":true,"requiresApproval":true}]}
```

This replaces only option 120's standard document requirements. `documents: []`
explicitly clears them. Other options and service-common requirements are preserved.
Return shape is `ServiceDocumentRequirementResponse[]`:

```json
[{"documentTypeId":4,"documentTypeName":"Driving licence","description":null,
  "hasExpiry":true,"optionId":120,"required":true,"requiresApproval":true}]
```

Document records remain provider/document-type scoped. Existing valid approvals
are reused. Changes refresh affected provider approvals. The legacy service setup
API preserves omitted option requirements; to clear an option use this explicit
endpoint. Option-specific documents supplied through a different linked service's
setup are rejected with 400 and a pointer to this shared endpoint.

### Shared prices and markers

Use existing market-service APIs to enable each navigation main service in its
market. Create/list/update rates through an offering belonging to ANY linked
service, with the same option ID. Prices are stored in the original configuration
namespace, keyed by market + option; every linked offering lists the same rates.
Different markets can still have different currencies/prices. Editing or disabling
a shared rate affects all linked contexts in that market.

```http
POST /api/v1/admin/market-services/70/rates
```

```json
{"optionId":120,"billingType":"ROUTE_BASED","baseFare":200,"pricePerKm":100,
 "minimumFare":400,"conditions":[{"fieldId":145,"choiceValue":"CAR"}]}
```

201 `JobRateResponse` retains its canonical `serviceTypeId` and `optionId`;
the service ID may differ from the offering's navigation service. Rate writes lock
the shared option as well as the offering so linked paths cannot race conflicting
conditional prices. Existing rate validation/effective/geographic rules remain.

```http
POST /api/v1/admin/service-options/120/map-marker-rules
```

```json
{"conditions":[{"fieldId":145,"choiceValue":"CAR"}],
 "mapMarkerIcon":{"key":"service-icons/car-pin.svg","url":"https://cdn.example.com/car-pin.svg"},
 "status":"ACTIVE"}
```

201 returns the same rule when read through either link or the global option URL.
Prices and marker rules remain independent configurations. Single-choice fallback
uses the option's shared `mapMarkerFieldId`; service-icon fallback uses the original
configuration service so a different navigation path does not change the marker.

## Provider registration and reuse

There is one `provider_service_option` registration per provider + option, enforced
by a database unique constraint. The source assignment holds the existing schedule,
service areas, languages, skills, submissions and review history. Source registration
must be ACTIVE/approved; linking does not fabricate approvals or qualify unrelated
providers. For new registrations use the advertised configuration setup/field IDs;
the original configuration service is the natural registration namespace.

Before asking a provider to register again:

```http
GET /api/v1/providers/me/service-options/120/registration
Authorization: Bearer <provider-token>
```

```json
{"optionId":120,"providerOptionId":80,"assignmentId":140,
 "registrationServiceTypeId":6,"providerStatus":"ACTIVE","adminStatus":"ACTIVE",
 "verificationStatus":"APPROVED"}
```

200 identifies the existing assignment. Continue using its existing availability,
service-area and requirement update APIs. 404 means this provider has no shared
registration. A second registration/selection under a different assignment returns
409 instructing the caller to reuse the original assignment. A concurrent duplicate
is also prevented by the provider/option unique constraint. Registration lookup
does not expose another provider's data. Unselecting/deactivating the source option
affects every navigation context; unlinking a main service affects only that catalog.

## Customer flow

Customer URLs and body shapes do not change. Browse market → service → option →
context/eligibility → provider search → select provider(s) → quote → booking.

* Both linked contexts expose the same field IDs, choices and markers.
* Search scans registrations of the shared option across its linked services and
  deduplicates provider IDs. One provider cannot satisfy a two-person team twice.
* Search and booking use deterministic original-namespace-first registration
  selection for legacy duplicates; the migration requires duplicates be resolved.
* The selected registration supplies schedule, coverage, languages and attributes.
* Quote/booking resolves the same provider registration and canonical market/option
  price, while retaining the requested navigation `serviceTypeId` in the job.
* Provider capacity/overlap accounting continues to use provider + shared option ID.
  A booking through one link reduces availability through the other.
* Option-level customer requirements/eligibility are shared. Main-service-only
  browsing gates are still main-service concepts before an option is selected.

Example search filter is unchanged:

```json
{"fieldId":145,"value":["CAR"]}
```

The candidate's `providerAttributes["145"]` contains approved capabilities.
The quote's independent `pricingSelections` is `[{"fieldId":145,"choiceValue":"CAR"}]`.
No current-vehicle switch, road-routing integration, automatic open-request dispatch
or per-unit pricing is introduced by this change. Quote reserves no time/funds;
booking creation still revalidates availability/price. Existing payment limitations
and lack of booking-create idempotency remain.

## Errors and deployment

400: invalid applicability/choices, non-owner legacy setup writes, or invalid input.
403: role/eligibility restrictions. 404: missing registration/configuration/scope.
409: conflicting code/price/marker or duplicate provider option registration.
Existing ProblemDetail response structure is unchanged.

Run the automatic merge in the runbook before deployment. Duplicate fields, prices,
marker rules and registrations are merged by deterministic priorities, with original
rows/reviews archived. Choice sets are combined and condition IDs remapped; pending
or rejected evidence is not promoted. The SQL file prepares columns; the JDBC runner
merges the data. Hibernate schema update does not run it. No production migration
was executed as part of this implementation.

See [migration runbook](FULLY_SHARED_OPTIONS_MIGRATION.md) and
[migration SQL](FULLY_SHARED_OPTIONS_SCHEMA.sql).
