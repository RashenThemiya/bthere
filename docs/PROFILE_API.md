# JobHub Profile and Provider Setup API

Base URL: `https://jobhub.pentarixlabs.com`

Every endpoint below requires `Authorization: Bearer <access-token>`.

## S3 image upload

Upload a provider/profile image before saving its URL in a profile or document
request:

```http
POST /api/v1/uploads/images
Authorization: Bearer <access-token>
Content-Type: multipart/form-data

file: <JPG, PNG or WebP image>
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

Use the returned `url` as `profilePhoto` or `documentUrl` in the appropriate
profile/document API. Configure `AWS_S3_PUBLIC_BASE_URL` when images are served
through CloudFront or another custom public domain.

## Customer profile

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

Each market includes its country code, default currency, IANA timezone,
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
  "defaultCurrency": "GBP",
  "timezone": "Europe/London",
  "locale": "en-GB",
  "phoneCountryCode": "+44"
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
  "documentUrl": "https://secure-storage.example.com/document.pdf",
  "issuedDate": "2018-01-01",
  "expiryDate": null
}
```

The file must first be uploaded to secure object storage. This API stores its URL and metadata. New documents receive `PENDING` status. Approved documents cannot be deleted.

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
