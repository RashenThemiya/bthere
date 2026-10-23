# JobHub Administration API

Document types support configurable named upload parts; see
[DOCUMENT_PARTS_API.md](DOCUMENT_PARTS_API.md) for updated bodies, responses,
compatibility rules and additive schema preparation. Deployment is pending.

## Super Admin tab update: fields, icons, search and document dates (2026-10-20)

Implemented locally; deployment is not verified. Configuration requires SUPER_ADMIN.
Providers/customers consume their relevant definitions and submit answers. IDs/URLs
below are illustrative.

### Configure provider requirements and booking questions

```http
POST /api/v1/admin/provider-types/3/custom-fields
GET /api/v1/admin/provider-types/3/custom-fields
PUT /api/v1/admin/provider-types/3/custom-fields/25
PATCH /api/v1/admin/provider-types/3/custom-fields/25/status
```

POST returns 201; GET/PUT/PATCH return 200. GET is a plain array. Status body:
{"status":"ACTIVE"} or {"status":"INACTIVE"}. Upload field/choice icons first
using POST /api/v1/uploads/images, multipart part file; use returned key/url.
Admin images support JPG, PNG, WebP and sanitized SVG, maximum 10 MB.

Create/update definition:

```json
{
  "optionIds":[12],"scope":"PROVIDER_REQUIREMENT","reuseScope":"PROVIDER",
  "code":"VEHICLE_TYPE","label":"Vehicle type","description":"Your available vehicle",
  "fieldType":"SELECT","required":false,"requiresApproval":false,
  "minimumFiles":null,"maximumFiles":null,"maximumFileSizeMb":null,
  "allowedFileTypes":[],"condition":null,"displayOrder":1,
  "customerVisible":true,"searchable":true,"filterOperator":"IN",
  "icon":{"key":"service-icons/vehicle.png","url":"https://your-bucket.example/service-icons/vehicle.png","altText":"Vehicle"},
  "options":[
    {"value":"CAR","label":"Car","icon":{"key":"service-icons/car.png","url":"https://your-bucket.example/service-icons/car.png"}},
    {"value":"VAN","label":"Van","icon":null},
    "BIKE"
  ]
}
```

Response contains the definition plus fieldId, serviceTypeId, effective optionId/optionIds,
version and status. Icons/choices flow to admin lists, option dynamicFields, onboarding/
booking schemas and customer filter definitions.

| Setting | Admin behavior |
| --- | --- |
| scope | PROVIDER_REQUIREMENT: provider onboarding. BOOKING_FIELD: customer per booking. |
| required | Mandatory completion; separate from approval/customer visibility. |
| requiresApproval | Provider answer becomes PENDING when true, otherwise auto-APPROVED. |
| optionIds | []/omitted: service-wide. Otherwise active options of this service. Do not also send legacy optionId. |
| reuseScope | ASSIGNMENT, SERVICE, PROVIDER; booking fields must use ASSIGNMENT. |
| customerVisible | Publish approved manual answers or valid linked profile values; default false. |
| searchable | Enable customer filters; default false; requires customerVisible. |
| filterOperator | Derived if omitted: IN for choices, RANGE for NUMBER, EQUALS for BOOLEAN. |
| icon | Optional field icon; each select choice may independently have an icon. |

Only unconditional SELECT/MULTI_SELECT/NUMBER/BOOLEAN provider requirements can be
customer-visible/searchable. Uploads, text/contact fields and BOOKING_FIELD cannot
be enabled as provider filters. Booking fields can still have field/choice icons;
keep visibility/search flags false. All 13 existing types remain available for forms.
Choices must have unique nonempty string values; objects may add label/icon metadata.
Answers and filters submit values only. Missing icon key/url, malformed choices,
duplicate choice values or incompatible operators return 400.

Field PUT replaces the complete definition, including option assignments, icons,
choices and flags. Preserve settings to keep them. Omitted/null icon removes attachment
without deleting the S3 object. Label/icon changes preserve values; changing values
can make saved answers invalid for search. Option POST/PUT also accepts
dynamicFields:[{fieldId,definition}]: omit fieldId to create, supply to edit. Icons
belong inside definition, separately from option.icon. Omitted dynamic fields remain unchanged.

### Reuse provider profile data

Profile-backed provider fields are now available to avoid duplicate onboarding inputs.
Set valueSource:PROVIDER_PROFILE and profileField:gender (SELECT/TEXT), dateOfBirth
(DATE), age (NUMBER), or another compatible allowlisted profile property. Default
valueSource is MANUAL. Existing fields are not automatically linked by code/name.
Keep requiresApproval:false; reviewed evidence is configured separately. Only profile
gender/age can be publicly enabled as dynamic attributes; identifiers/DOB remain private.
Profile-linked values are read-only in provider forms and report PROVIDED, not verified
APPROVED. Required fields use live validity for completeness; old manual answers cannot
override the profile. See [mapping, examples and SQL rollout](DYNAMIC_FIELDS_API.md#reuse-provider-profile-values-without-duplicate-inputs).

### Document date controls

```http
POST /api/v1/admin/document-types
GET /api/v1/admin/document-types
PUT /api/v1/admin/document-types/1
```

```json
{"name":"NIC","description":"National Identity Card","hasExpiry":false,
 "issuedDateMode":"OPTIONAL","expiryDateMode":"HIDDEN"}
```

POST 201 / PUT 200 returns {id,name,description,hasExpiry,status,issuedDateMode,expiryDateMode}.
GET returns a plain array. HIDDEN rejects supplied date, OPTIONAL permits blank,
REQUIRED requires a date. Omitted modes default issued to OPTIONAL, expiry to REQUIRED
if legacy hasExpiry true, otherwise OPTIONAL. Explicit expiryDateMode takes precedence;
response hasExpiry reflects REQUIRED. Existing rows retain legacy defaults.
Provider documentName/number are optional; name defaults to type name. Required document
types belong to service/option setup, not a required flag on document-type creation.
Existing uploads are not retroactively rewritten.

### Customer requirements and review response wrappers

GET /api/v1/admin/customer-requirements returns Requirement[], not Page:

```json
[{"requirementId":2,"version":1,"status":"ACTIVE","definition":{
  "code":"DATE_OF_BIRTH","label":"Date of birth","description":null,"fieldType":"DATE",
  "required":true,"verificationMode":"DOCUMENT_REVIEW","blocksBooking":false,
  "targets":[{"scope":"SERVICE","serviceTypeId":3,"optionId":null}],
  "evidenceCodes":["NIC"],"choices":[],"minimumFiles":null,"maximumFiles":null,
  "maximumFileSizeMb":null,"allowedFileTypes":[],"displayOrder":2
}}]
```

GET /api/v1/admin/customer-submissions?status=PENDING&page=0&size=20 returns
Page<Submission>: content items with totalElements, totalPages, number and size.
Items contain submissionId/customerId/requirementId/code/value, effective status,
storedStatus, expiresOn, review metadata, answerVersion and definitionVersion;
no embedded requirement definition. Join by requirementId. Filter uses storedStatus;
effective status may be MISSING/EXPIRED. Review sends current answerVersion.
VERIFICATION_ADMIN can also read/review these submissions.

Successful submission queue response fragment (Spring Page may include additional
pageable/sort/first/last metadata):

```json
{
  "content":[{
    "submissionId":101,"customerId":25,"requirementId":2,"code":"DATE_OF_BIRTH",
    "value":"2000-05-10","status":"PENDING","storedStatus":"PENDING",
    "expiresOn":null,"reviewedBy":null,"reviewedAt":null,"rejectionReason":null,
    "answerVersion":1,"definitionVersion":1
  }],
  "totalElements":1,"totalPages":1,"number":0,"size":20
}
```

GET /api/v1/admin/customer-eligibility returns Rule[]:

```json
[{"ruleId":3,"target":{"scope":"OPTION","serviceTypeId":3,"optionId":12},
  "configuration":{"enabled":true,"minimumAge":21,"dateOfBirthCode":"DATE_OF_BIRTH",
  "requiresVerifiedDateOfBirth":true,"requiredCodes":["NIC"]}}]
```

Hydrate by target.scope/serviceTypeId/optionId using configuration. GLOBAL has null
IDs; SERVICE has optionId null; OPTION has both IDs. No expanded names/objects or
default entries for unconfigured targets are returned.

Full validation and database rollout: [dynamic fields](DYNAMIC_FIELDS_API.md),
[provider filtering](CUSTOMER_DISCOVERY_API.md#dynamic-provider-filters),
[document modes](PROFILE_API.md#document-upload-metadata-controls),
[customer requirements](CUSTOMER_REQUIREMENTS_API.md).

Base URL: `https://jobhub.pentarixlabs.com`

All endpoints require `Authorization: Bearer <access-token>`. Paginated
endpoints accept `page`, `size`, and `sort`. Page numbering starts at zero,
the default size is 20, and the maximum size is 100.

## User administration

- `GET /api/v1/admin/users?search=&status=&role=&page=0&size=20`
- `PATCH /api/v1/admin/users/{userId}/status`
- `POST /api/v1/admin/users/{userId}/reset-password`

Status body:

```json
{ "status": "INACTIVE" }
```

Allowed values are `ACTIVE`, `INACTIVE`, and `SUSPENDED`. Deactivation and
password reset revoke all active sessions. Password reset body:

```json
{ "newPassword": "new-strong-password" }
```

## Service setup

- `POST /api/v1/admin/provider-types/setup`
- `PUT /api/v1/admin/provider-types/{providerTypeId}/setup`
- `PATCH /api/v1/admin/provider-types/{providerTypeId}/status`

Each setup request includes `availabilityEnabled`. Location configuration belongs
to each service option through `locationEnabled` and `deliveryModes`.
Enable only the onboarding steps required by that service. If an enabled step
is incomplete, the provider-service remains pending. When a feature is
disabled, its provider endpoints reject updates.

## Emergency provider-service approval

- `POST /api/v1/admin/provider-service-assignments/{assignmentId}/emergency-override`
- `DELETE /api/v1/admin/provider-service-assignments/{assignmentId}/emergency-override`

```json
{
  "reason": "Emergency staffing approval",
  "expiresAt": "2026-10-06T18:00:00"
}
```

The expiry is optional. Applying and revoking an override creates audit logs.

## Market-specific services and pricing

- `POST /api/v1/admin/market-services`
- `GET /api/v1/admin/market-services?marketId=1&page=0&size=20`
- `PATCH /api/v1/admin/market-services/{offeringId}/status`
- `POST /api/v1/admin/market-services/{offeringId}/rates`
- `GET /api/v1/admin/market-services/{offeringId}/rates?page=0&size=20`
- `PUT /api/v1/admin/market-services/{offeringId}/rates/{rateId}`
- `PATCH /api/v1/admin/market-services/{offeringId}/rates/{rateId}/status`

Create market availability:

```json
{
  "marketId": 1,
  "serviceTypeId": 3
}
```

Create or update a price:

```json
{
  "billingType": "HOURLY",
  "durationMinutes": 60,
  "rate": 2500.00,
  "effectiveFrom": "2026-10-05T00:00:00",
  "effectiveTo": null
}
```

Currency is automatically taken from the selected market.

## Dashboard statistics

- `GET /api/v1/admin/dashboard/statistics`

Returns user, provider, market, service, job, payment, and paid-value totals.

## Audit logs

- `GET /api/v1/admin/audit-logs?action=&entityType=&actorId=&page=0&size=20`

Audit-log access is restricted to Super Admin.

## Paginated verification queues

- `GET /api/v1/admin/provider-documents?status=PENDING&page=0&size=20`
- `GET /api/v1/admin/provider-qualifications/skills?status=PENDING&page=0&size=20`
- `GET /api/v1/admin/provider-qualifications/certificates?status=PENDING&page=0&size=20`
- `GET /api/v1/admin/provider-qualifications/education?status=PENDING&page=0&size=20`

Allowed statuses are `PENDING`, `APPROVED`, and `REJECTED`.
# Super Admin dashboard statistics

```http
GET /api/v1/admin/dashboard/statistics
Authorization: Bearer <superadmin-or-admin-token>
```

The response preserves the original user, provider, job, and payment totals and now
also contains:

```json
{
  "serviceConfiguration": {
    "totalServiceTypes": 6,
    "activeServiceTypes": 5,
    "inactiveServiceTypes": 1,
    "totalOptions": 14,
    "activeOptions": 12,
    "inactiveOptions": 2,
    "timeBasedOptions": 10,
    "routeBasedOptions": 4,
    "oneAtATimeOptions": 7,
    "manyAtATimeOptions": 7,
    "oneToOneOptions": 8,
    "manyCustomersOneProviderOptions": 4,
    "oneCustomerManyProvidersOptions": 2,
    "providerRequirementFields": 18,
    "bookingFields": 22,
    "totalMarketOfferings": 8,
    "activeMarketOfferings": 7,
    "totalRates": 20,
    "activeRates": 18
  },
  "approvalQueues": {
    "providerServices": 3,
    "serviceOptions": 4,
    "documents": 5,
    "skills": 2,
    "certificates": 1,
    "education": 2,
    "customRequirements": 3,
    "totalPending": 20
  },
  "bookingStatistics": {
    "timeBased": 40,
    "routeBased": 10,
    "oneToOne": 35,
    "manyCustomersOneProvider": 12,
    "oneCustomerManyProviders": 3
  }
}
```

Older rows that predate scheduling/fulfillment configuration can have null values and
are intentionally not counted in a subtype until they are updated or used in a new booking.

## Replace a market service rate

```http
PUT /api/v1/admin/market-services/{offeringId}/rates/{rateId}
Authorization: Bearer <superadmin-token>
Content-Type: application/json
```

This is a complete replacement of the rate's editable pricing fields. Omitted fields
and fields explicitly set to `null` have the same meaning. `billingType` and
`effectiveFrom` are always required. `effectiveTo: null` makes the rate open-ended,
and `optionId: null` selects legacy service-level pricing.

Time-based and legacy service-level pricing require `billingType` to be `HOURLY`,
`DAILY`, or `FIXED`, and require `rate`. They reject `baseFare`, `pricePerKm`, and
`minimumFare`. Route-based options require `billingType: ROUTE_BASED`, `baseFare`,
`pricePerKm`, and `minimumFare`; they reject `rate` and `durationMinutes`.

The four geographical override fields are optional, but must either all be null or
all be supplied. `effectiveTo`, when supplied, cannot be before `effectiveFrom`.

```json
{
  "billingType": "HOURLY",
  "durationMinutes": 60,
  "rate": 2500.00,
  "effectiveFrom": "2026-10-09T08:00:00",
  "effectiveTo": null,
  "optionId": 12,
  "baseFare": null,
  "pricePerKm": null,
  "minimumFare": null,
  "geographicalAreaName": null,
  "areaLatitude": null,
  "areaLongitude": null,
  "areaRadiusKm": null
}
```

The update does not change the rate ID, market, service type, currency, or status.
