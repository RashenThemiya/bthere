# Combined provider service registration

The mobile app can collect one service registration form and save it with one
authenticated JSON request. The provider must already have an account, profile,
and active market. This endpoint registers one service with multiple options.

```http
POST /api/v1/providers/me/service-registrations
Authorization: Bearer <service-provider-access-token>
Content-Type: application/json
```

## Request

IDs are examples: use real IDs from the service setup/onboarding schema.
All top-level properties except `availability` are required. Use empty arrays for
unused collections and empty nested arrays for unused selections. Only include
qualifications and locations supported by the selected service/options.

```json
{
  "service": { "providerTypeId": 3, "experienceYears": 4.5 },
  "options": { "optionIds": [12, 13] },
  "languages": { "languages": ["SINHALA", "ENGLISH"] },
  "skills": { "skillIds": [10, 11] },
  "documents": [
    {
      "documentTypeId": 1,
      "documentName": "National ID",
      "documentNumber": "200012345678",
      "documentKey": "providers/25/documents/nic.pdf",
      "issuedDate": "2020-01-01",
      "expiryDate": null
    }
  ],
  "certificates": [
    {
      "name": "Caregiving Certificate",
      "issuingOrganization": "Training Institute",
      "certificateNumber": "CERT-123",
      "issuedDate": "2024-01-01",
      "expiryDate": null,
      "documentKey": "providers/25/documents/certificate.pdf"
    }
  ],
  "education": [
    {
      "qualificationName": "Diploma in Caregiving",
      "instituteName": "Training Institute",
      "fieldOfStudy": "Caregiving",
      "startDate": "2022-01-01",
      "completionDate": "2023-01-01",
      "result": "PASS",
      "documentKey": "providers/25/documents/diploma.pdf"
    }
  ],
  "availability": {
    "availableAnyDay": true,
    "availableAnyTime": true,
    "weeklySlots": []
  },
  "unavailableDates": [],
  "serviceAreas": [
    {
      "optionId": 12,
      "deliveryMode": "PROVIDER_TO_CUSTOMER",
      "locations": [
        {
          "locationName": "Colombo base",
          "country": "Sri Lanka",
          "province": "Western",
          "district": "Colombo",
          "city": "Colombo",
          "latitude": 6.9271,
          "longitude": 79.8612,
          "radiusKm": 20,
          "capacity": 1
        }
      ]
    }
  ],
  "requirements": {
    "submissions": [
      { "fieldId": 20, "value": "Four years of experience" },
      {
        "fieldId": 24,
        "value": { "files": [{ "key": "providers/25/documents/support.pdf" }] }
      }
    ]
  }
}
```

## Response and behavior

- Success: `201 Created`, with `assignment` (the existing provider service
  assignment response) and `completion` (the existing combined onboarding response).
  `assignment.assignmentId` identifies the saved registration. Assignment fields
  include `providerTypeId`, `providerStatus`, `adminStatus`, effective `status`,
  `verificationStatus`, and required/missing document IDs.
- Saving is atomic: any validation/save failure rolls back all database changes
  from this call, including shared document updates. Previously uploaded files
  remain in storage.
- Existing validation, ownership, activation, and review rules apply. Saving does
  not grant approval. Missing onboarding requirements may still produce a saved
  registration with incomplete progress or pending verification: inspect `completion`.
- An already assigned service returns `409 Conflict`, including inactive assignments.
  This prevents ordinary repeat submissions from duplicating qualifications or
  resetting approval. After an uncertain response, query
  `GET /api/v1/providers/me/service-types` before retrying. Use individual update
  APIs to edit existing registrations.
- Service area groups require selected option IDs and unique option/delivery-mode
  pairs. Each group requires locations. Add separate groups for physical options
  that need locations. ONLINE has no service areas.
- Omit `availability` or send null if not configuring a schedule. Unavailable date
  entries use the existing `{ "date": "2026-12-25", "reason": "Holiday" }` shape.
- Documents are shared by provider/document type. Omit existing approved documents
  from `documents` to reuse them. Including an existing document type updates it
  and resets verification to PENDING, following the existing document API.
- Account/profile fields, admin service media, responsibilities, and payment
  configuration continue to use their existing APIs.

## Mobile flow

1. Log in and save profile/market if needed.
2. Read the service onboarding schema and collect the configured form.
3. Upload files through `/api/v1/uploads/images` or `/api/v1/uploads/documents`.
   Use actual returned keys. The `25` above is the authenticated user ID, not the
   service-provider entity ID. Do not send binary or base64 files to this endpoint.
4. Send the combined request once the provider finishes the form.
5. Display the returned assignment and completion status. Refresh the existing
   assignment/completion GET endpoints after admin review.

Errors follow the existing ProblemDetail format: `400` invalid payload/values,
`401` missing/invalid authentication, `403` wrong role, `404` missing/unavailable
resource, `409` existing registration. Existing individual APIs remain supported;
see [PROFILE_API.md](PROFILE_API.md).
