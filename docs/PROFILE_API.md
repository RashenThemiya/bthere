# JobHub Profile and Provider Setup API

Base URL: `https://jobhub.pentarixlabs.com`

For one mobile request registering a service, options, and supporting details,
see [Combined service registration](PROVIDER_SERVICE_REGISTRATION_API.md).

Every endpoint below requires `Authorization: Bearer <access-token>`.

## S3 image upload

Upload a provider/profile image before saving its URL in a profile or document
request:

```http
POST /api/v1/uploads/images
Authorization: Bearer <access-token>
Content-Type: multipart/form-data

file: <JPG, PNG or WebP image; safe SVG is also accepted for Super Admin service assets>
```

Maximum size is 10 MB. Example response:

```json
{
  "key": "providers/15/images/550e8400-e29b-41d4-a716-446655440000.jpg",
  "url": "https://jobhub-images.s3.ap-south-1.amazonaws.com/providers/15/images/550e8400-e29b-41d4-a716-446655440000.jpg",
  "contentType": "image/jpeg",
  "size": 245120
}
```

Use the returned image `url` as `profilePhoto`. Configure
`AWS_S3_PUBLIC_BASE_URL` when images are served
through CloudFront or another custom public domain.

Private PDFs and document images:

- `POST /api/v1/uploads/documents` accepts PDF, JPG, PNG, or WebP up to 100 MB.
- `GET /api/v1/uploads/view-url?key={objectKey}` returns a 10-minute presigned URL.

Private uploads return both a relative `key` and an `s3://bucket/key` URL. Store
the relative `key` in every document or certificate `documentKey` field. Providers can create viewing
URLs only for their own files; authorized admins can view provider files.

## Customer profile

For configurable personal details, documents, verification, and global/service/option
booking requirements, see [Customer requirements API](CUSTOMER_REQUIREMENTS_API.md).

- `GET /api/v1/customers/me/profile`
- `PUT /api/v1/customers/me/profile`

```json
{
  "firstName": "Jane",
  "lastName": "Doe",
  "profilePhoto": "https://cdn.example.com/jane.jpg",
  "addressLine1": "10 Main Street",
  "addressLine2": null,
  "city": "Colombo",
  "district": "Colombo",
  "province": "Western",
  "postalCode": "00100",
  "country": "Sri Lanka"
}
```

Only accounts with the `CUSTOMER` role can use these endpoints.

## Service-provider profile

- `GET /api/v1/providers/me/profile`
- `PUT /api/v1/providers/me/profile`

```json
{
  "firstName": "John",
  "lastName": "Smith",
  "nicNumber": "901234567V",
  "passportNumber": null,
  "dateOfBirth": "1990-01-01",
  "gender": "MALE",
  "profilePhoto": "https://cdn.example.com/john.jpg",
  "bio": "Experienced electrician",
  "availabilityStatus": "AVAILABLE",
  "marketId": 1
}
```

NIC and passport are optional at the general-profile level. Super Admin can
require identity documents for specific services. Availability is `AVAILABLE`,
`UNAVAILABLE`, or `OFFLINE`. One active market must be selected.

## Markets

Providers list active markets with:

```http
GET /api/v1/markets
```

Each market includes its country code, currency code, IANA timezone,
locale, and international phone code.

Providers can change their operating market later:

```http
PUT /api/v1/providers/me/markets

{
  "marketId": 2
}
```

Super Admin manages markets with:

- `POST /api/v1/markets` — create a market
- `GET /api/v1/markets/all` — list active and inactive markets
- `PUT /api/v1/markets/{marketId}` — update market configuration
- `PATCH /api/v1/markets/{marketId}/status` — activate or deactivate

```json
{
  "name": "United Kingdom",
  "countryCode": "GB",
  "currencyCode": "GBP",
  "timezone": "Europe/London",
  "locale": "en-GB",
  "phoneCode": "+44"
}
```

## Provider-type catalog and selection

List active types:

```http
GET /api/v1/provider-types
```

Super Admin creates a type:

```http
POST /api/v1/admin/provider-types

{
  "name": "Electrician",
  "description": "Electrical installation and repair",
  "iconUrl": "https://cdn.example.com/electrician.svg"
}
```

Recommended: Super Admin creates the service and its complete profile setup
in one request:

```http
POST /api/v1/admin/provider-types/setup

{
  "name": "Caregiver",
  "description": "Home and elderly care",
  "iconUrl": "https://cdn.example.com/caregiver.svg",
  "availabilityEnabled": true,
  "optionsRequired": true,
  "minimumOptionSelections": 1,
  "maximumOptionSelections": 5,
  "icon": null,
  "coverImage": null,
  "gallery": [],
  "responsibilities": [],
  "documents": [
    {
      "documentTypeId": 1,
      "required": true,
      "requiresApproval": true
    },
    {
      "documentTypeId": 2,
      "required": false,
      "requiresApproval": true
    }
  ],
  "skills": [
    {
      "name": "Elderly care",
      "required": true,
      "requiresApproval": false
    },
    {
      "name": "First aid",
      "required": false,
      "requiresApproval": false
    }
  ],
  "certificates": {
    "level": "OPTIONAL",
    "requiresApproval": true
  },
  "education": {
    "level": "OPTIONAL",
    "requiresApproval": true
  }
}
```

Requirement levels are `DISABLED`, `OPTIONAL`, and `REQUIRED`.

`gallery` and `responsibilities` are required arrays, even when empty. For
structured icons, cover images, galleries, responsibilities, and replacement
rules, see [SERVICE_MEDIA_RESPONSIBILITIES_API.md](SERVICE_MEDIA_RESPONSIBILITIES_API.md).

Super Admin updates the full setup with:

```http
PUT /api/v1/admin/provider-types/{providerTypeId}/setup
```

The provider/frontend reads the configured completion form with:

```http
GET /api/v1/provider-types/{providerTypeId}/setup
```

Super Admin activates or deactivates a service with:

```http
PATCH /api/v1/admin/provider-types/{providerTypeId}/status

{
  "status": "INACTIVE"
}
```

Allowed values are `ACTIVE` and `INACTIVE`. Inactive services are hidden from
the active catalog and cannot be newly selected. Existing provider information
is preserved so the service can be activated again later.

Provider reads or replaces their selections:

- `GET /api/v1/providers/me/service-types`
- `PUT /api/v1/providers/me/service-types`

```json
{
  "assignments": [
    {
      "providerTypeId": 1,
      "experienceYears": 5.5
    }
  ]
}
```

PUT replaces the provider's entire existing selection.

A provider can add another service at any time without replacing existing
selections:

```http
POST /api/v1/providers/me/service-types

{
  "providerTypeId": 3,
  "experienceYears": 2.5
}
```

A provider activates or deactivates one of their own services:

```http
PATCH /api/v1/providers/me/service-types/{assignmentId}/status

{
  "status": "INACTIVE"
}
```

Super Admin can independently activate or deactivate a provider's assigned
service:

```http
PATCH /api/v1/admin/provider-service-assignments/{assignmentId}/status

{
  "status": "INACTIVE"
}
```

The returned assignment contains `providerStatus`, `adminStatus`, and the
effective `status`. The effective status is `ACTIVE` only when both the
provider and Super Admin statuses are active. A provider cannot override an
administrative deactivation.

Each selected service has its own `verificationStatus`:

- `PENDING`: one or more required documents are missing, awaiting approval, rejected, or expired.
- `APPROVED`: every required document is approved and valid.

The response also contains `requiredDocumentTypeIds` and
`missingDocumentTypeIds`, allowing the frontend to show exactly what the
provider must upload.

Reusable document requirements can be service-wide or option-specific. In the
service setup `documents` collection, omit `optionId` (or send `null`) for a
service-wide requirement, or provide an active option ID for a requirement
that applies only when the provider selects that option:

```json
{
  "documents": [
    {
      "documentTypeId": 1,
      "optionId": null,
      "required": true,
      "requiresApproval": true
    },
    {
      "documentTypeId": 5,
      "optionId": 12,
      "required": true,
      "requiresApproval": true
    }
  ]
}
```

The provider uploads a document once against its `documentTypeId`. Any
service-wide or selected-option requirement using that type reuses the same
valid document and its existing approval. Selecting an option recalculates
`requiredDocumentTypeIds`, `missingDocumentTypeIds`, and service approval.

Service skills:

- `GET /api/v1/providers/me/services/{assignmentId}/skills`
- `PUT /api/v1/providers/me/services/{assignmentId}/skills`

```json
{
  "skillIds": [1, 3, 4]
}
```

Professional certificates:

- `GET /api/v1/providers/me/services/{assignmentId}/certificates`
- `POST /api/v1/providers/me/services/{assignmentId}/certificates`

```json
{
  "name": "Caregiver NVQ Level 4",
  "issuingOrganization": "Training Institute",
  "certificateNumber": "CERT-1001",
  "issuedDate": "2024-01-01",
  "expiryDate": null,
  "documentKey": "providers/15/documents/certificate.pdf"
}
```

Education qualifications:

- `GET /api/v1/providers/me/services/{assignmentId}/education`
- `POST /api/v1/providers/me/services/{assignmentId}/education`

```json
{
  "qualificationName": "Diploma in Caregiving",
  "instituteName": "Training Institute",
  "fieldOfStudy": "Caregiving",
  "startDate": "2022-01-01",
  "completionDate": "2023-01-01",
  "result": "Distinction",
  "documentKey": "providers/15/documents/diploma.pdf"
}
```

Combined onboarding progress:

```http
GET /api/v1/providers/me/completion
```

The response contains the overall percentage, next step, and per-service
document, skill, certificate, education, verification, and activation state.

### Document upload metadata controls

Super Admin can configure dates in `POST /api/v1/admin/document-types` and
`PUT /api/v1/admin/document-types/{documentTypeId}`:

```json
{
  "name": "NIC",
  "description": "National Identity Card",
  "hasExpiry": false,
  "issuedDateMode": "OPTIONAL",
  "expiryDateMode": "HIDDEN"
}
```

Both modes accept `HIDDEN`, `OPTIONAL`, or `REQUIRED` and are returned by document
type list/create/update APIs. Frontends must hide HIDDEN dates, allow blank OPTIONAL
dates, and require REQUIRED dates. Backend rejects supplied HIDDEN dates and missing
REQUIRED dates. If modes are omitted, issued date defaults to OPTIONAL; expiry date
defaults to REQUIRED when legacy `hasExpiry` is true, otherwise OPTIONAL. Explicit
expiryDateMode takes precedence; response hasExpiry reflects whether expiry is REQUIRED.
Existing rows without modes retain these legacy defaults.

Provider upload requires `documentTypeId` and `documentKey`. `documentName` is
optional and defaults to the document type name; `documentNumber` is optional.
Dates follow admin configuration. This does not change service document requirements
or approval rules. New nullable columns `issued_date_mode` and `expiry_date_mode`
are added to `document_type` by Hibernate schema update; manually managed schemas
must add them before deploying. Existing uploads are not rewritten.

Read the document requirements for a service:

```http
GET /api/v1/provider-types/{providerTypeId}/document-requirements
```

Super Admin assigns required document types to a service:

```http
PUT /api/v1/admin/provider-types/{providerTypeId}/document-requirements

{
  "documentTypeIds": [1, 2, 5]
}
```

This request replaces that service's requirement list. An empty list means
the service requires no documents.

## Dynamic provider requirements for multiple options

### Provider option and language selections

These endpoints require the `SERVICE_PROVIDER` role and an assignment owned
by the authenticated provider.

- `GET /api/v1/providers/me/services/{assignmentId}/options`
- `PUT /api/v1/providers/me/services/{assignmentId}/options`

PUT replaces the selected options using active option IDs from that service:

```json
{
  "optionIds": [12, 13]
}
```

- `GET /api/v1/providers/me/services/{assignmentId}/languages`
- `PUT /api/v1/providers/me/services/{assignmentId}/languages`

```json
{
  "languages": ["SINHALA", "ENGLISH", "TAMIL"]
}
```

PUT replaces the language selection. Allowed values are `SINHALA`, `ENGLISH`,
and `TAMIL`.

### Admin field configuration

Super Admin can create one dynamic requirement for several service options:

```http
POST /api/v1/admin/provider-types/{providerTypeId}/custom-fields
```

```json
{
  "optionIds": [12, 13],
  "scope": "PROVIDER_REQUIREMENT",
  "reuseScope": "ASSIGNMENT",
  "code": "DRIVING_EXPERIENCE",
  "label": "Driving experience in years",
  "fieldType": "NUMBER",
  "required": true,
  "requiresApproval": false,
  "displayOrder": 1
}
```

The provider answers this field once when any listed option is selected.
Use `optionIds: []` for a service-wide requirement. PUT to
`/api/v1/admin/provider-types/{providerTypeId}/custom-fields/{fieldId}`
replaces the option list. Read the form from
`GET /api/v1/provider-types/{providerTypeId}/onboarding-schema`.
See [DYNAMIC_FIELDS_API.md](DYNAMIC_FIELDS_API.md) for submission and reuse rules.

Option GET responses now include `dynamicFields` with the full definitions
applicable to each option, including common service-wide fields. Super Admin
can also send `dynamicFields` in the option POST or PUT API to create or edit
fields in the same transaction. Each entry contains an optional `fieldId`
(omit to create) and a `definition` containing the custom-field request.
See [option API examples](DYNAMIC_FIELDS_API.md#dynamic-fields-in-option-apis).

### Create an option with dynamic fields

Super Admin creates the option and its fields together in one request:

```http
POST /api/v1/admin/provider-types/{providerTypeId}/options
Authorization: Bearer <super-admin-token>
Content-Type: application/json
```

```json
{
  "code": "HOME_CARE",
  "name": "Home Care",
  "description": "Care provided at the customer's home",
  "displayOrder": 1,
  "schedulingModel": "TIME_BASED",
  "locationEnabled": true,
  "deliveryModes": ["PROVIDER_TO_CUSTOMER"],
  "fulfillmentModel": "ONE_TO_ONE",
  "defaultCapacity": 1,
  "requiredProviderCount": 1,
  "bookingMode": "MANY_AT_A_TIME",
  "providerSelectionMode": "OPEN_REQUEST",
  "pricingOwner": "ADMIN",
  "allowedPaymentMethods": ["CASH", "CARD"],
  "icon": null,
  "coverImage": null,
  "gallery": [],
  "responsibilities": [],
  "dynamicFields": [
    {
      "definition": {
        "scope": "PROVIDER_REQUIREMENT",
        "reuseScope": "ASSIGNMENT",
        "code": "CARE_EXPERIENCE",
        "label": "Care experience in years",
        "fieldType": "NUMBER",
        "required": true,
        "requiresApproval": false,
        "displayOrder": 1
      }
    },
    {
      "definition": {
        "scope": "BOOKING_FIELD",
        "code": "CARE_NOTES",
        "label": "Care instructions",
        "fieldType": "TEXTAREA",
        "required": false,
        "requiresApproval": false,
        "displayOrder": 2
      }
    }
  ]
}
```

Omit `fieldId` to create each new field. Omit `optionId` and `optionIds`
inside its definition: the backend assigns it to the newly created option.
The response includes the new `optionId` and `dynamicFields` containing their
generated `fieldId` and `optionIds`. Providers complete `PROVIDER_REQUIREMENT`
fields; customers complete `BOOKING_FIELD` fields when booking.

To edit the option and a field together, send the option properties to
`PUT /api/v1/admin/provider-types/{providerTypeId}/options/{optionId}` and
include that field's returned `fieldId` alongside its full `definition`.
Omitted dynamic fields remain unchanged.

## Document types and provider documents

List active document types:

```http
GET /api/v1/document-types
```

Super Admin creates a document type:

```http
POST /api/v1/admin/document-types

{
  "name": "National ID",
  "description": "Government-issued identity document",
  "required": true,
  "hasExpiry": false
}
```

Provider document endpoints:

- `GET /api/v1/providers/me/documents`
- `POST /api/v1/providers/me/documents`
- `DELETE /api/v1/providers/me/documents/{documentId}`

```json
{
  "documentTypeId": 1,
  "documentName": "NIC front",
  "documentNumber": "901234567V",
  "documentKey": "providers/15/documents/nic-front.pdf",
  "issuedDate": "2018-01-01",
  "expiryDate": null
}
```

The file must first be uploaded to secure object storage. This API stores its
relative S3 object key and metadata. New documents receive `PENDING` status.
Approved documents cannot be deleted.

A provider stores only one current document for each document type. Posting
the same document type again updates it and returns it to `PENDING` review.
One approved document is reusable across every selected service that requires
that document type. For example, one approved National ID can satisfy the
National ID requirement for Electrician, Plumber, and Cleaner services.

## Admin document review

Super Admin and Verification Admin can list documents:

```http
GET /api/v1/admin/provider-documents?status=PENDING
```

Allowed filters: `PENDING`, `APPROVED`, and `REJECTED`.

Approve:

```http
PATCH /api/v1/admin/provider-documents/{documentId}/review

{
  "status": "APPROVED",
  "rejectionReason": null
}
```

Reject:

```json
{
  "status": "REJECTED",
  "rejectionReason": "The uploaded image is unreadable"
}
```

A rejection reason is mandatory when rejecting a document.

After an administrator reviews a document, the backend automatically
recalculates the approval status of all services selected by that provider.

Admin qualification-review endpoints:

- `GET /api/v1/admin/provider-qualifications/skills?status=PENDING`
- `PATCH /api/v1/admin/provider-qualifications/skills/{id}/review`
- `GET /api/v1/admin/provider-qualifications/certificates?status=PENDING`
- `PATCH /api/v1/admin/provider-qualifications/certificates/{id}/review`
- `GET /api/v1/admin/provider-qualifications/education?status=PENDING`
- `PATCH /api/v1/admin/provider-qualifications/education/{id}/review`

Review requests use the same `APPROVED` or `REJECTED` body as document review.

## Provider service availability

Availability belongs to a provider's selected service assignment. This allows a
provider to offer different hours and locations for different services.
The administrator controls these features independently for every service with
`availabilityEnabled` in the service setup and each option's `locationEnabled`
and `deliveryModes` configuration.
The frontend should read `GET /api/v1/provider-types/{providerTypeId}/setup`
and only show the enabled steps.

Get the weekly schedule:

```http
GET /api/v1/providers/me/services/{assignmentId}/availability
```

Replace it with selected days and hours:

```http
PUT /api/v1/providers/me/services/{assignmentId}/availability

{
  "availableAnyDay": false,
  "availableAnyTime": false,
  "weeklySlots": [
    { "dayOfWeek": "MONDAY", "startTime": "08:00", "endTime": "17:00" },
    { "dayOfWeek": "SUNDAY", "startTime": "08:00", "endTime": "17:00" }
  ]
}
```

For every day and every time, use both flags as `true` and send an empty
`weeklySlots` array. If only `availableAnyTime` is true, provide the selected
days as slots and omit their start/end times.

## Specific unavailable dates

```http
GET /api/v1/providers/me/services/{assignmentId}/unavailable-dates
POST /api/v1/providers/me/services/{assignmentId}/unavailable-dates
DELETE /api/v1/providers/me/services/{assignmentId}/unavailable-dates/{unavailableDateId}
```

```json
{
  "date": "2026-10-20",
  "reason": "Medical appointment"
}
```

## Service locations and travel ranges

Get locations:

```http
GET /api/v1/providers/me/services/{assignmentId}/options/{optionId}/delivery-modes/{deliveryMode}/service-areas
```

Replace all locations:

```http
PUT /api/v1/providers/me/services/{assignmentId}/options/{optionId}/delivery-modes/{deliveryMode}/service-areas

[
  {
    "locationName": "Colombo base",
    "country": "Sri Lanka",
    "province": "Western",
    "district": "Colombo",
    "city": "Colombo",
    "latitude": 6.9271,
    "longitude": 79.8612,
    "radiusKm": 20
  }
]
```

The provider can add multiple locations. Each radius is validated from 0.1 km
to 500 km, and locations use the provider's currently selected market.
