# Admin-assisted registration - complete request and response examples - 26 October 2026

This reference expands every new endpoint and every existing endpoint listed in the admin-assisted registration handoff. Examples are illustrative fixtures checked against backend DTOs, not responses captured from a deployed server. Replace IDs, dates and keys with real returned values. Nulls/empty arrays are intentional; no success response below uses ellipses or omitted nested objects. Endpoints are independent examples, not a script that should resubmit the same evidence repeatedly.

Use an admin token for admin-assisted endpoints. Existing provider profile 15 maps internally to account 25; admin actor is 99. The example service is 3, option 12, assignment 14, field definition 22, submission 71, provider option selection 81 and document record 44. Never substitute a definition ID for a review record ID. Source accepts WHATSAPP, PHONE, IN_PERSON, EMAIL or ADMIN_ASSISTED; source is required in registration/document JSON, defaults to ADMIN_ASSISTED on uploads, and note is optional with maximum 1000 characters. Registration is available without any provider-side service request.

## New 1 - Provider registration context

```http
GET /api/v1/admin/providers/15/registration-context
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: SUPER_ADMIN or VERIFICATION_ADMIN. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Request body: none. Do not send a JSON body.

Success: HTTP 200. Full example response:

```json
{
  "providerId": 15,
  "assignments": [
    {
      "assignmentId": 14,
      "providerTypeId": 3,
      "providerTypeName": "Caregiver",
      "experienceYears": 5,
      "status": "ACTIVE",
      "providerStatus": "ACTIVE",
      "adminStatus": "ACTIVE",
      "verificationStatus": "PENDING",
      "emergencyOverride": false,
      "emergencyOverrideReason": null,
      "emergencyOverrideExpiresAt": null,
      "requiredDocumentTypeIds": [
        4
      ],
      "missingDocumentTypeIds": [
        4
      ]
    }
  ],
  "completion": {
    "profileCompleted": true,
    "marketSelected": true,
    "serviceSelected": true,
    "allActiveServicesApproved": false,
    "completionPercentage": 75,
    "nextStep": "COMPLETE_SERVICE_REQUIREMENTS",
    "services": [
      {
        "assignmentId": 14,
        "serviceTypeId": 3,
        "serviceName": "Caregiver",
        "documentsCompleted": false,
        "skillsCompleted": true,
        "certificatesCompleted": true,
        "educationCompleted": true,
        "availabilityCompleted": true,
        "serviceAreasCompleted": true,
        "optionsCompleted": false,
        "customRequirementsCompleted": false,
        "verificationStatus": "PENDING",
        "effectiveStatus": "ACTIVE"
      }
    ]
  },
  "documents": [
    {
      "documentId": 44,
      "serviceProviderId": 15,
      "documentTypeId": 4,
      "documentTypeName": "Qualification document",
      "documentName": "Qualification document",
      "documentNumber": "CERT-123",
      "documentKey": "providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf",
      "issuedDate": "2024-01-01",
      "expiryDate": "2028-01-01",
      "verificationStatus": "PENDING",
      "verifiedBy": null,
      "verifiedAt": null,
      "rejectionReason": null,
      "createdAt": "2026-10-26T10:00:00",
      "parts": [],
      "partDefinitions": []
    }
  ]
}
```

For a provider without services, assignments is []; completion reports serviceSelected false and SELECT_SERVICE when the market is already selected. This is the same full response structure, not a different response wrapper.

## New 2 - Upload document

```http
POST /api/v1/admin/providers/15/uploads/documents?source=WHATSAPP
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: SUPER_ADMIN or VERIFICATION_ADMIN. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Parameters: source query enum; providerId path. Actual response URLs depend on configured storage.

Request body: as follows.

```text
Content-Type: multipart/form-data
file: <binary file>
```

Success: HTTP 201. Full example response:

```json
{
  "key": "providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf",
  "url": "s3://example-bucket/providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf",
  "contentType": "application/pdf",
  "size": 125000
}
```

Documents: PDF/JPG/PNG/WebP up to 100 MB. This request is multipart, not JSON: use the form part named file. The form example above is not a JSON payload. Returned key belongs to target account 25, not admin 99. Use key in subsequent submissions. Preview private documents using the existing view-url endpoint. Uploads are separate from registration; a file is not a submitted/approved document until attached.

## New 3 - Upload image

```http
POST /api/v1/admin/providers/15/uploads/images?source=WHATSAPP
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: SUPER_ADMIN or VERIFICATION_ADMIN. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Parameters: source query enum; providerId path. Actual response URLs depend on configured storage.

Request body: as follows.

```text
Content-Type: multipart/form-data
file: <binary file>
```

Success: HTTP 201. Full example response:

```json
{
  "key": "providers/25/images/550e8400-e29b-41d4-a716-446655440001.jpg",
  "url": "https://cdn.example.com/providers/25/images/550e8400-e29b-41d4-a716-446655440001.jpg",
  "contentType": "image/jpeg",
  "size": 125000
}
```

Images: JPG/PNG/WebP up to 10 MB; no provider SVG. This request is multipart, not JSON: use the form part named file. The form example above is not a JSON payload. Returned key belongs to target account 25, not admin 99. Use key in subsequent submissions. Preview private documents using the existing view-url endpoint. Uploads are separate from registration; a file is not a submitted/approved document until attached.

## New 4 - Register service and options for provider

```http
POST /api/v1/admin/providers/15/service-registrations
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: SUPER_ADMIN or VERIFICATION_ADMIN. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Request body: as follows.

```json
{
  "source": "WHATSAPP",
  "note": "Admin completed registration from information and evidence received directly.",
  "registration": {
    "service": {
      "providerTypeId": 3,
      "experienceYears": 5
    },
    "options": {
      "optionIds": [
        12
      ]
    },
    "languages": {
      "languages": [
        "SINHALA",
        "ENGLISH"
      ]
    },
    "skills": {
      "skillIds": []
    },
    "documents": [
      {
        "documentTypeId": 4,
        "documentName": "Qualification document",
        "documentNumber": "CERT-123",
        "documentKey": "providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf",
        "issuedDate": "2024-01-01",
        "expiryDate": "2028-01-01",
        "parts": null
      }
    ],
    "certificates": [],
    "education": [],
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
            "locationName": "Colombo coverage",
            "country": "Sri Lanka",
            "province": "Western",
            "district": "Colombo",
            "city": "Colombo",
            "latitude": 6.9271,
            "longitude": 79.8612,
            "radiusKm": 10,
            "capacity": 1
          }
        ]
      }
    ],
    "requirements": {
      "submissions": [
        {
          "fieldId": 22,
          "value": {
            "files": [
              {
                "key": "providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf"
              }
            ]
          }
        }
      ]
    }
  }
}
```

Success: HTTP 201. Full example response:

```json
{
  "assignment": {
    "assignmentId": 14,
    "providerTypeId": 3,
    "providerTypeName": "Caregiver",
    "experienceYears": 5,
    "status": "ACTIVE",
    "providerStatus": "ACTIVE",
    "adminStatus": "ACTIVE",
    "verificationStatus": "PENDING",
    "emergencyOverride": false,
    "emergencyOverrideReason": null,
    "emergencyOverrideExpiresAt": null,
    "requiredDocumentTypeIds": [
      4
    ],
    "missingDocumentTypeIds": [
      4
    ]
  },
  "completion": {
    "profileCompleted": true,
    "marketSelected": true,
    "serviceSelected": true,
    "allActiveServicesApproved": false,
    "completionPercentage": 75,
    "nextStep": "COMPLETE_SERVICE_REQUIREMENTS",
    "services": [
      {
        "assignmentId": 14,
        "serviceTypeId": 3,
        "serviceName": "Caregiver",
        "documentsCompleted": false,
        "skillsCompleted": true,
        "certificatesCompleted": true,
        "educationCompleted": true,
        "availabilityCompleted": true,
        "serviceAreasCompleted": true,
        "optionsCompleted": false,
        "customRequirementsCompleted": false,
        "verificationStatus": "PENDING",
        "effectiveStatus": "ACTIVE"
      }
    ]
  }
}
```

All registration arrays and service/options/languages/skills/requirements wrappers are required. availability can be null when not applicable. Example includes a location group for the selected option/mode. Use actual configured requirements and document parts. Response returns fresh assignment and completion. Approval is separate. Duplicate service returns 409; update the existing assignment instead. Database registration is atomic; already uploaded files remain available for a corrected retry.

## New 5 - Add standard document to existing provider

```http
POST /api/v1/admin/providers/15/documents
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: SUPER_ADMIN or VERIFICATION_ADMIN. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Request body: as follows.

```json
{
  "source": "WHATSAPP",
  "note": "Evidence received by staff.",
  "document": {
    "documentTypeId": 4,
    "documentName": "Qualification document",
    "documentNumber": "CERT-123",
    "documentKey": "providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf",
    "issuedDate": "2024-01-01",
    "expiryDate": "2028-01-01",
    "parts": null
  }
}
```

Success: HTTP 201. Full example response:

```json
{
  "documentId": 44,
  "serviceProviderId": 15,
  "documentTypeId": 4,
  "documentTypeName": "Qualification document",
  "documentName": "Qualification document",
  "documentNumber": "CERT-123",
  "documentKey": "providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf",
  "issuedDate": "2024-01-01",
  "expiryDate": "2028-01-01",
  "verificationStatus": "PENDING",
  "verifiedBy": null,
  "verifiedAt": null,
  "rejectionReason": null,
  "createdAt": "2026-10-26T10:00:00",
  "parts": [],
  "partDefinitions": []
}
```

Adds a new provider-wide record, initially PENDING. Do not call this for a document already included in the registration request. If configured document parts exist, omit documentKey and submit parts [{code,documentKeys}]; use the configured part codes and limits. This is an add, not an upsert.

## New 6 - Replace provider option selections

```http
PUT /api/v1/admin/providers/15/service-registrations/14/options
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: SUPER_ADMIN or VERIFICATION_ADMIN. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Request body: as follows.

```json
{
  "optionIds": [
    12
  ]
}
```

Success: HTTP 200. Full example response:

```json
[
  {
    "providerOptionId": 81,
    "optionId": 12,
    "code": "HOSPITAL_CARE",
    "name": "Hospital Care",
    "providerStatus": "ACTIVE",
    "adminStatus": "PENDING",
    "verificationStatus": "PENDING",
    "reviewedBy": null,
    "reviewedAt": null,
    "reviewNote": null
  }
]
```

Full replacement of selected options. New selections are pending admin review. Existing approved selections can retain their status. Review providerOptionId (81), not optionId (12). On review approval this selection's adminStatus becomes APPROVED; service assignment adminStatus uses ACTIVE/INACTIVE instead.

## New 7 - Replace languages

```http
PUT /api/v1/admin/providers/15/service-registrations/14/languages
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: SUPER_ADMIN or VERIFICATION_ADMIN. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Request body: as follows.

```json
{
  "languages": [
    "SINHALA",
    "ENGLISH"
  ]
}
```

Success: HTTP 200. Full example response:

```json
[
  "ENGLISH",
  "SINHALA"
]
```

Returns a sorted bare string array. Allowed values: SINHALA, ENGLISH, TAMIL. Full replacement.

## New 8 - Replace skills

```http
PUT /api/v1/admin/providers/15/service-registrations/14/skills
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: SUPER_ADMIN or VERIFICATION_ADMIN. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Request body: as follows.

```json
{
  "skillIds": [
    10
  ]
}
```

Success: HTTP 200. Full example response:

```json
[
  {
    "providerSkillId": 91,
    "assignmentId": 14,
    "skillId": 10,
    "skillName": "Patient care",
    "required": false,
    "verificationStatus": "PENDING",
    "rejectionReason": null
  }
]
```

This example assumes active skill 10 has been configured for this service with requiresApproval true. Result is a bare array. Review providerSkillId, not skillId. Full replacement; auto-approved skills return APPROVED instead of PENDING.

## New 9 - Replace availability

```http
PUT /api/v1/admin/providers/15/service-registrations/14/availability
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: SUPER_ADMIN or VERIFICATION_ADMIN. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Request body: as follows.

```json
{
  "availableAnyDay": true,
  "availableAnyTime": true,
  "weeklySlots": []
}
```

Success: HTTP 200. Full example response:

```json
{
  "availableAnyDay": true,
  "availableAnyTime": true,
  "weeklySlots": []
}
```

Available-any-day/time example returns empty weeklySlots. For specific weekly slots, request elements are {dayOfWeek,startTime,endTime}; response elements additionally contain availabilityId. Configuration must enable availability.

## New 10 - Submit dynamic requirements

```http
PUT /api/v1/admin/providers/15/service-registrations/14/requirements
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: SUPER_ADMIN or VERIFICATION_ADMIN. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Request body: as follows.

```json
{
  "submissions": [
    {
      "fieldId": 22,
      "value": {
        "files": [
          {
            "key": "providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf"
          }
        ]
      }
    }
  ]
}
```

Success: HTTP 200. Full example response:

```json
[
  {
    "submissionId": 71,
    "assignmentId": 14,
    "fieldId": 22,
    "fieldCode": "QUALIFICATION_EVIDENCE",
    "fieldLabel": "Qualification evidence",
    "value": {
      "files": [
        {
          "key": "providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf"
        }
      ]
    },
    "verificationStatus": "PENDING",
    "reviewedBy": null,
    "reviewedAt": null,
    "reviewNote": null,
    "reuseScope": "ASSIGNMENT",
    "reused": false,
    "sourceAssignmentId": 14,
    "valueSource": "MANUAL",
    "profileField": null,
    "readOnly": false
  }
]
```

submissions is a collection of fieldId/value entries. This endpoint upserts submitted answers and returns resolved provider submissions, which can also include other existing/reused/profile-backed answers. It does not mean omitted answers were deleted. Profile-backed entries are read-only. Values must match fieldType. Review submissionId (71), not fieldId (22).

## New 11 - Add professional certificate

```http
POST /api/v1/admin/providers/15/service-registrations/14/certificates
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: SUPER_ADMIN or VERIFICATION_ADMIN. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Request body: as follows.

```json
{
  "name": "Caregiver certificate",
  "issuingOrganization": "Training Institute",
  "certificateNumber": "CG-123",
  "issuedDate": "2024-01-01",
  "expiryDate": "2028-01-01",
  "documentKey": "providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf"
}
```

Success: HTTP 201. Full example response:

```json
{
  "certificateId": 61,
  "assignmentId": 14,
  "name": "Caregiver certificate",
  "issuingOrganization": "Training Institute",
  "certificateNumber": "CG-123",
  "issuedDate": "2024-01-01",
  "expiryDate": "2028-01-01",
  "documentKey": "providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf",
  "verificationStatus": "PENDING",
  "rejectionReason": null
}
```

Assumes certificates are enabled and require review. Auto-approved configuration returns APPROVED. Dates are ISO yyyy-MM-dd; documentKey must belong to target provider. Subsequent submissions add certificates; they are not upserts.

## New 12 - Add education

```http
POST /api/v1/admin/providers/15/service-registrations/14/education
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: SUPER_ADMIN or VERIFICATION_ADMIN. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Request body: as follows.

```json
{
  "qualificationName": "Caregiving diploma",
  "instituteName": "Training Institute",
  "fieldOfStudy": "Caregiving",
  "startDate": "2023-01-01",
  "completionDate": "2024-01-01",
  "result": "PASS",
  "documentKey": "providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf"
}
```

Success: HTTP 201. Full example response:

```json
{
  "educationId": 62,
  "assignmentId": 14,
  "qualificationName": "Caregiving diploma",
  "instituteName": "Training Institute",
  "fieldOfStudy": "Caregiving",
  "startDate": "2023-01-01",
  "completionDate": "2024-01-01",
  "result": "PASS",
  "documentKey": "providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf",
  "verificationStatus": "PENDING",
  "rejectionReason": null
}
```

Assumes education is enabled and requires review. documentKey is optional for education, subject to the normal provider contract. Review educationId when pending.

## New 13 - Replace option/mode service areas

```http
PUT /api/v1/admin/providers/15/service-registrations/14/options/12/service-areas?deliveryMode=PROVIDER_TO_CUSTOMER
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: SUPER_ADMIN or VERIFICATION_ADMIN. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Parameters: providerId, assignmentId, optionId path; deliveryMode required query string.

Request body: as follows.

```json
[
  {
    "locationName": "Colombo coverage",
    "country": "Sri Lanka",
    "province": "Western",
    "district": "Colombo",
    "city": "Colombo",
    "latitude": 6.9271,
    "longitude": 79.8612,
    "radiusKm": 10,
    "capacity": 1
  }
]
```

Success: HTTP 200. Full example response:

```json
[
  {
    "serviceAreaId": 51,
    "optionId": 12,
    "deliveryMode": "PROVIDER_TO_CUSTOMER",
    "locationName": "Colombo coverage",
    "country": "Sri Lanka",
    "province": "Western",
    "district": "Colombo",
    "city": "Colombo",
    "latitude": 6.9271,
    "longitude": 79.8612,
    "radiusKm": 10,
    "capacity": 1,
    "status": "ACTIVE"
  }
]
```

Body is a bare array, not {locations:[]}. Replaces locations only for assignment 14, option 12 and specified mode. Requires that option to be selected and its location configuration to permit this mode. Configuration determines whether radius/capacity are required.

## Existing 1 - Service type catalog

```http
GET /api/v1/provider-types
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: Authenticated access under existing route policy. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Request body: none. Do not send a JSON body.

Success: HTTP 200. Full example response:

```json
[
  {
    "id": 3,
    "name": "Caregiver",
    "description": "Caregiver services",
    "iconUrl": null,
    "status": "ACTIVE",
    "icon": null,
    "coverImage": null,
    "gallery": [],
    "responsibilities": [],
    "mapMarkerIcon": null
  }
]
```

Response is a bare array. Catalog id is the service/provider type ID.

## Existing 2 - Service setup

```http
GET /api/v1/provider-types/3/setup
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: Authenticated access under existing route policy. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Request body: none. Do not send a JSON body.

Success: HTTP 200. Full example response:

```json
{
  "serviceTypeId": 3,
  "name": "Caregiver",
  "description": "Caregiver services",
  "iconUrl": null,
  "status": "ACTIVE",
  "availabilityEnabled": true,
  "serviceAreasEnabled": true,
  "optionsRequired": true,
  "minimumOptionSelections": 1,
  "maximumOptionSelections": 3,
  "documents": [
    {
      "documentTypeId": 4,
      "documentTypeName": "Qualification document",
      "optionId": 12,
      "required": true,
      "requiresApproval": true
    }
  ],
  "skills": [],
  "certificates": {
    "level": "OPTIONAL",
    "requiresApproval": true
  },
  "education": {
    "level": "OPTIONAL",
    "requiresApproval": true
  },
  "icon": null,
  "coverImage": null,
  "gallery": [],
  "responsibilities": [],
  "mapMarkerIcon": null
}
```

Describes provider setup requirements. Load actual active documents, skills, certificate/education rules and option-selection limits; do not hardcode the example.

## Existing 3 - Provider onboarding schema

```http
GET /api/v1/provider-types/3/onboarding-schema
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: Authenticated access under existing route policy. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Request body: none. Do not send a JSON body.

Success: HTTP 200. Full example response:

```json
{
  "serviceTypeId": 3,
  "serviceName": "Caregiver",
  "serviceSetup": {
    "serviceTypeId": 3,
    "name": "Caregiver",
    "description": "Caregiver services",
    "iconUrl": null,
    "status": "ACTIVE",
    "availabilityEnabled": true,
    "serviceAreasEnabled": true,
    "optionsRequired": true,
    "minimumOptionSelections": 1,
    "maximumOptionSelections": 3,
    "documents": [
      {
        "documentTypeId": 4,
        "documentTypeName": "Qualification document",
        "optionId": 12,
        "required": true,
        "requiresApproval": true
      }
    ],
    "skills": [],
    "certificates": {
      "level": "OPTIONAL",
      "requiresApproval": true
    },
    "education": {
      "level": "OPTIONAL",
      "requiresApproval": true
    },
    "icon": null,
    "coverImage": null,
    "gallery": [],
    "responsibilities": [],
    "mapMarkerIcon": null
  },
  "commonFields": [],
  "options": [
    {
      "optionId": 12,
      "code": "HOSPITAL_CARE",
      "name": "Hospital Care",
      "fields": [
        {
          "fieldId": 22,
          "serviceTypeId": 3,
          "optionId": 12,
          "optionIds": [
            12
          ],
          "scope": "PROVIDER_REQUIREMENT",
          "reuseScope": "ASSIGNMENT",
          "code": "QUALIFICATION_EVIDENCE",
          "label": "Qualification evidence",
          "description": "Upload supporting evidence",
          "fieldType": "FILE_UPLOAD",
          "required": true,
          "requiresApproval": true,
          "minimumFiles": 1,
          "maximumFiles": 3,
          "maximumFileSizeMb": 10,
          "allowedFileTypes": [
            "PDF",
            "JPG",
            "PNG",
            "WEBP"
          ],
          "options": null,
          "condition": null,
          "displayOrder": 1,
          "version": 1,
          "status": "ACTIVE",
          "customerVisible": false,
          "searchable": false,
          "filterOperator": null,
          "icon": null,
          "valueSource": "MANUAL",
          "profileField": null,
          "defaultValue": null,
          "ownerServiceTypeId": 3,
          "configurationScope": "SHARED_OPTION"
        }
      ]
    }
  ]
}
```

Uses provider requirements. commonFields apply at service level and options[].fields are option-specific. fieldId/ownerServiceTypeId/configurationScope are definition metadata, not submission IDs. Preserve shared option IDs; do not duplicate definitions in another service.

## Existing 4 - Admin service option catalog

```http
GET /api/v1/admin/provider-types/3/options
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: SUPER_ADMIN. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Request body: none. Do not send a JSON body.

Success: HTTP 200. Full example response:

```json
[
  {
    "optionId": 12,
    "serviceTypeId": 3,
    "code": "HOSPITAL_CARE",
    "name": "Hospital Care",
    "description": "Care at the customer hospital location",
    "displayOrder": 1,
    "status": "ACTIVE",
    "schedulingModel": "TIME_BASED",
    "routeAverageSpeedKmh": null,
    "locationEnabled": true,
    "deliveryModes": [
      "PROVIDER_TO_CUSTOMER"
    ],
    "fulfillmentModel": "ONE_TO_ONE",
    "defaultCapacity": 1,
    "requiredProviderCount": 1,
    "bookingMode": "ONE_AT_A_TIME",
    "providerSelectionMode": "DIRECT_REQUEST",
    "pricingOwner": "ADMIN",
    "allowedPaymentMethods": [
      "CASH"
    ],
    "deliveryModeConfigurations": [
      {
        "code": "PROVIDER_TO_CUSTOMER",
        "providerLocationType": "COVERAGE_AREA",
        "providerLocationRequired": true,
        "radiusRequired": true,
        "customerLocationInput": "MEETING_POINT"
      }
    ],
    "icon": null,
    "coverImage": null,
    "gallery": [],
    "responsibilities": [],
    "dynamicFields": [
      {
        "fieldId": 22,
        "serviceTypeId": 3,
        "optionId": 12,
        "optionIds": [
          12
        ],
        "scope": "PROVIDER_REQUIREMENT",
        "reuseScope": "ASSIGNMENT",
        "code": "QUALIFICATION_EVIDENCE",
        "label": "Qualification evidence",
        "description": "Upload supporting evidence",
        "fieldType": "FILE_UPLOAD",
        "required": true,
        "requiresApproval": true,
        "minimumFiles": 1,
        "maximumFiles": 3,
        "maximumFileSizeMb": 10,
        "allowedFileTypes": [
          "PDF",
          "JPG",
          "PNG",
          "WEBP"
        ],
        "options": null,
        "condition": null,
        "displayOrder": 1,
        "version": 1,
        "status": "ACTIVE",
        "customerVisible": false,
        "searchable": false,
        "filterOperator": null,
        "icon": null,
        "valueSource": "MANUAL",
        "profileField": null,
        "defaultValue": null,
        "ownerServiceTypeId": 3,
        "configurationScope": "SHARED_OPTION"
      }
    ],
    "mapMarkerIcon": null,
    "mapMarkerFieldId": null,
    "configurationServiceTypeId": 3
  }
]
```

Response is a bare option array with settings and complete dynamic definitions. VERIFICATION_ADMIN does not gain access to this SUPER_ADMIN route from the new registration role grant.

## Existing 5 - Shared option custom fields

```http
GET /api/v1/admin/service-options/12/custom-fields
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: SUPER_ADMIN. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Request body: none. Do not send a JSON body.

Success: HTTP 200. Full example response:

```json
[
  {
    "fieldId": 22,
    "serviceTypeId": 3,
    "optionId": 12,
    "optionIds": [
      12
    ],
    "scope": "PROVIDER_REQUIREMENT",
    "reuseScope": "ASSIGNMENT",
    "code": "QUALIFICATION_EVIDENCE",
    "label": "Qualification evidence",
    "description": "Upload supporting evidence",
    "fieldType": "FILE_UPLOAD",
    "required": true,
    "requiresApproval": true,
    "minimumFiles": 1,
    "maximumFiles": 3,
    "maximumFileSizeMb": 10,
    "allowedFileTypes": [
      "PDF",
      "JPG",
      "PNG",
      "WEBP"
    ],
    "options": null,
    "condition": null,
    "displayOrder": 1,
    "version": 1,
    "status": "ACTIVE",
    "customerVisible": false,
    "searchable": false,
    "filterOperator": null,
    "icon": null,
    "valueSource": "MANUAL",
    "profileField": null,
    "defaultValue": null,
    "ownerServiceTypeId": 3,
    "configurationScope": "SHARED_OPTION"
  }
]
```

Includes shared/inherited definitions and inactive fields according to the existing admin contract. Filter and render according to scope/status/applicability. This additional existing lookup was referenced in the workflow guide.

## Existing 6 - Private file preview

```http
GET /api/v1/uploads/view-url?key=providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: SUPER_ADMIN or VERIFICATION_ADMIN. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Parameters: key required query string, using the relative returned provider S3 key.

Request body: none. Do not send a JSON body.

Success: HTTP 200. Full example response:

```json
{
  "key": "providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf",
  "url": "https://example-bucket.s3.example.com/temporary-signed-preview",
  "expiresAt": "2026-10-26T04:40:00Z"
}
```

URL is illustrative and expires after ten minutes; use the actual returned URL. The s3:// upload result is not a browser preview URL. URL-encode the key query parameter.

## Existing 7 - Provider approval detail

```http
GET /api/v1/admin/provider-approvals/15
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: SUPER_ADMIN or VERIFICATION_ADMIN. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Request body: none. Do not send a JSON body.

Success: HTTP 200. Full example response:

```json
{
  "providerId": 15,
  "providerName": "Sample Provider",
  "pendingCount": 3,
  "missingCount": 0,
  "services": [
    {
      "assignmentId": 14,
      "serviceTypeId": 3,
      "serviceName": "Caregiver",
      "verificationStatus": "PENDING",
      "providerStatus": "ACTIVE",
      "adminStatus": "ACTIVE",
      "emergencyOverride": false,
      "checks": {
        "documents": false,
        "skills": true,
        "certificates": true,
        "education": true,
        "availability": true,
        "serviceAreas": true,
        "serviceOptions": false,
        "dynamicRequirements": false
      },
      "pendingCount": 3,
      "missingCount": 0,
      "documents": [
        {
          "type": "DOCUMENT",
          "id": 44,
          "definitionId": 8,
          "name": "Qualification document",
          "required": true,
          "requiresApproval": true,
          "verificationStatus": "PENDING",
          "missing": false,
          "expired": false,
          "reviewable": true,
          "documentKey": "providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf",
          "rejectionReason": null,
          "expiryDate": "2028-01-01",
          "sourceAssignmentId": null,
          "details": {
            "documentTypeId": 4,
            "optionId": 12,
            "documentNumber": "CERT-123",
            "parts": [],
            "partDefinitions": []
          }
        }
      ],
      "skills": [],
      "certificates": [],
      "education": [],
      "dynamicRequirements": [
        {
          "type": "DYNAMIC_REQUIREMENT",
          "id": 71,
          "definitionId": 22,
          "name": "Qualification evidence",
          "required": true,
          "requiresApproval": true,
          "verificationStatus": "PENDING",
          "missing": false,
          "expired": false,
          "reviewable": true,
          "documentKey": null,
          "rejectionReason": null,
          "expiryDate": null,
          "sourceAssignmentId": 14,
          "details": {
            "code": "QUALIFICATION_EVIDENCE",
            "fieldType": "FILE_UPLOAD",
            "reuseScope": "ASSIGNMENT",
            "reused": false,
            "value": {
              "files": [
                {
                  "key": "providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf"
                }
              ]
            },
            "optionIds": [
              12
            ]
          }
        }
      ],
      "serviceOptions": [
        {
          "type": "SERVICE_OPTION",
          "id": 81,
          "definitionId": 12,
          "name": "Hospital Care",
          "required": true,
          "requiresApproval": true,
          "verificationStatus": "PENDING",
          "missing": false,
          "expired": false,
          "reviewable": true,
          "documentKey": null,
          "rejectionReason": null,
          "expiryDate": null,
          "sourceAssignmentId": 14,
          "details": {
            "providerStatus": "ACTIVE",
            "adminStatus": "PENDING",
            "configurationStatus": "ACTIVE"
          }
        }
      ]
    }
  ]
}
```

This complete example has 3 pending reviews and 0 missing items. Each service has checks and separate evidence arrays; some optional arrays are empty. pendingCount is a distinct reviewable-item count, while missingCount counts missing required items. Use type/id and sourceAssignmentId. A standard document has no assignment-specific source ID; dynamic and option rows do.

## Existing 8 - Bulk review

```http
POST /api/v1/admin/provider-approvals/bulk-review
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: SUPER_ADMIN or VERIFICATION_ADMIN. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Request body: as follows.

```json
{
  "assignmentId": 14,
  "reviews": [
    {
      "type": "DOCUMENT",
      "id": 44,
      "status": "APPROVED",
      "rejectionReason": null
    },
    {
      "type": "DYNAMIC_REQUIREMENT",
      "id": 71,
      "status": "APPROVED",
      "rejectionReason": null
    },
    {
      "type": "SERVICE_OPTION",
      "id": 81,
      "status": "APPROVED",
      "rejectionReason": null
    }
  ]
}
```

Success: HTTP 200. Full example response:

```json
{
  "atomic": true,
  "reviewedCount": 3,
  "provider": {
    "providerId": 15,
    "providerName": "Sample Provider",
    "pendingCount": 0,
    "missingCount": 0,
    "services": [
      {
        "assignmentId": 14,
        "serviceTypeId": 3,
        "serviceName": "Caregiver",
        "verificationStatus": "APPROVED",
        "providerStatus": "ACTIVE",
        "adminStatus": "ACTIVE",
        "emergencyOverride": false,
        "checks": {
          "documents": true,
          "skills": true,
          "certificates": true,
          "education": true,
          "availability": true,
          "serviceAreas": true,
          "serviceOptions": true,
          "dynamicRequirements": true
        },
        "pendingCount": 0,
        "missingCount": 0,
        "documents": [
          {
            "type": "DOCUMENT",
            "id": 44,
            "definitionId": 8,
            "name": "Qualification document",
            "required": true,
            "requiresApproval": true,
            "verificationStatus": "APPROVED",
            "missing": false,
            "expired": false,
            "reviewable": false,
            "documentKey": "providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf",
            "rejectionReason": null,
            "expiryDate": "2028-01-01",
            "sourceAssignmentId": null,
            "details": {
              "documentTypeId": 4,
              "optionId": 12,
              "documentNumber": "CERT-123",
              "parts": [],
              "partDefinitions": []
            }
          }
        ],
        "skills": [],
        "certificates": [],
        "education": [],
        "dynamicRequirements": [
          {
            "type": "DYNAMIC_REQUIREMENT",
            "id": 71,
            "definitionId": 22,
            "name": "Qualification evidence",
            "required": true,
            "requiresApproval": true,
            "verificationStatus": "APPROVED",
            "missing": false,
            "expired": false,
            "reviewable": false,
            "documentKey": null,
            "rejectionReason": null,
            "expiryDate": null,
            "sourceAssignmentId": 14,
            "details": {
              "code": "QUALIFICATION_EVIDENCE",
              "fieldType": "FILE_UPLOAD",
              "reuseScope": "ASSIGNMENT",
              "reused": false,
              "value": {
                "files": [
                  {
                    "key": "providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf"
                  }
                ]
              },
              "optionIds": [
                12
              ]
            }
          }
        ],
        "serviceOptions": [
          {
            "type": "SERVICE_OPTION",
            "id": 81,
            "definitionId": 12,
            "name": "Hospital Care",
            "required": true,
            "requiresApproval": true,
            "verificationStatus": "APPROVED",
            "missing": false,
            "expired": false,
            "reviewable": false,
            "documentKey": null,
            "rejectionReason": null,
            "expiryDate": null,
            "sourceAssignmentId": 14,
            "details": {
              "providerStatus": "ACTIVE",
              "adminStatus": "APPROVED",
              "configurationStatus": "ACTIVE"
            }
          }
        ]
      }
    ]
  }
}
```

Full nested provider detail is returned, not only a success boolean. This example approves the 3 pending items above and all required checks are then true. Actual results remain PENDING if anything is incomplete. Supports DOCUMENT, SKILL, CERTIFICATE, EDUCATION, DYNAMIC_REQUIREMENT and SERVICE_OPTION; 1-100 unique pending eligible records. Rejection requires rejectionReason. The batch is atomic and rejects unrelated/non-pending/source-reused items.

## Existing 9 - Review one standard document

```http
PATCH /api/v1/admin/provider-documents/44/review
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: SUPER_ADMIN or VERIFICATION_ADMIN. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Request body: as follows.

```json
{
  "status": "APPROVED",
  "rejectionReason": null
}
```

Success: HTTP 200. Full example response:

```json
{
  "documentId": 44,
  "serviceProviderId": 15,
  "documentTypeId": 4,
  "documentTypeName": "Qualification document",
  "documentName": "Qualification document",
  "documentNumber": "CERT-123",
  "documentKey": "providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf",
  "issuedDate": "2024-01-01",
  "expiryDate": "2028-01-01",
  "verificationStatus": "APPROVED",
  "verifiedBy": 99,
  "verifiedAt": "2026-10-26T10:15:00",
  "rejectionReason": null,
  "createdAt": "2026-10-26T10:00:00",
  "parts": [],
  "partDefinitions": []
}
```

Independent alternative to bulk-review for this document. Do not assume the same document should be reviewed again after the bulk example. Response verifiedBy is the actual reviewer account ID. Rejection body is {status:"REJECTED",rejectionReason:"Readable explanation"}.

## Existing 10 - Admin service status

```http
PATCH /api/v1/admin/provider-service-assignments/14/status
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: SUPER_ADMIN. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Request body: as follows.

```json
{
  "status": "ACTIVE"
}
```

Success: HTTP 200. Full example response:

```json
{
  "assignmentId": 14,
  "providerTypeId": 3,
  "providerTypeName": "Caregiver",
  "experienceYears": 5,
  "status": "ACTIVE",
  "providerStatus": "ACTIVE",
  "adminStatus": "ACTIVE",
  "verificationStatus": "APPROVED",
  "emergencyOverride": false,
  "emergencyOverrideReason": null,
  "emergencyOverrideExpiresAt": null,
  "requiredDocumentTypeIds": [
    4
  ],
  "missingDocumentTypeIds": []
}
```

This example is after all checks and reviews are complete. Setting ACTIVE only changes admin activation and does not grant verification approval. If requirements remain incomplete, verificationStatus remains PENDING. Existing providerStatus/market/availability rules still apply.

## Existing 11 - Provider self-registration

```http
POST /api/v1/providers/me/service-registrations
```

Authorization: Bearer <ADMIN_TOKEN>. Roles: SERVICE_PROVIDER; use the provider token, not the admin token. Content-Type: application/json for JSON writes; application/json responses unless otherwise stated.

Request body: as follows.

```json
{
  "service": {
    "providerTypeId": 3,
    "experienceYears": 5
  },
  "options": {
    "optionIds": [
      12
    ]
  },
  "languages": {
    "languages": [
      "SINHALA",
      "ENGLISH"
    ]
  },
  "skills": {
    "skillIds": []
  },
  "documents": [
    {
      "documentTypeId": 4,
      "documentName": "Qualification document",
      "documentNumber": "CERT-123",
      "documentKey": "providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf",
      "issuedDate": "2024-01-01",
      "expiryDate": "2028-01-01",
      "parts": null
    }
  ],
  "certificates": [],
  "education": [],
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
          "locationName": "Colombo coverage",
          "country": "Sri Lanka",
          "province": "Western",
          "district": "Colombo",
          "city": "Colombo",
          "latitude": 6.9271,
          "longitude": 79.8612,
          "radiusKm": 10,
          "capacity": 1
        }
      ]
    }
  ],
  "requirements": {
    "submissions": [
      {
        "fieldId": 22,
        "value": {
          "files": [
            {
              "key": "providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf"
            }
          ]
        }
      }
    ]
  }
}
```

Success: HTTP 201. Full example response:

```json
{
  "assignment": {
    "assignmentId": 14,
    "providerTypeId": 3,
    "providerTypeName": "Caregiver",
    "experienceYears": 5,
    "status": "ACTIVE",
    "providerStatus": "ACTIVE",
    "adminStatus": "ACTIVE",
    "verificationStatus": "PENDING",
    "emergencyOverride": false,
    "emergencyOverrideReason": null,
    "emergencyOverrideExpiresAt": null,
    "requiredDocumentTypeIds": [
      4
    ],
    "missingDocumentTypeIds": [
      4
    ]
  },
  "completion": {
    "profileCompleted": true,
    "marketSelected": true,
    "serviceSelected": true,
    "allActiveServicesApproved": false,
    "completionPercentage": 75,
    "nextStep": "COMPLETE_SERVICE_REQUIREMENTS",
    "services": [
      {
        "assignmentId": 14,
        "serviceTypeId": 3,
        "serviceName": "Caregiver",
        "documentsCompleted": false,
        "skillsCompleted": true,
        "certificatesCompleted": true,
        "educationCompleted": true,
        "availabilityCompleted": true,
        "serviceAreasCompleted": true,
        "optionsCompleted": false,
        "customRequirementsCompleted": false,
        "verificationStatus": "PENDING",
        "effectiveStatus": "ACTIVE"
      }
    ]
  }
}
```

Same inner registration body and same response shape as the admin API. No source/note/registration outer wrapper on this old endpoint. Dashboard must use the new admin endpoint instead of impersonating the provider.

## Common errors, retries and UI behavior

No provider-side request is required. Missing existing provider or assignment belonging to another provider returns 404. Duplicate service registration returns 409. Invalid keys, missing configuration and invalid field values return 400. Invalid/missing JSON properties return validation errors. Missing/expired bearer token is rejected by authentication; a role without permission is forbidden. Security-layer 401/403 bodies can differ from application ProblemDetail, so branch on HTTP status rather than assuming one error wrapper.

```json
{
  "type": "about:blank",
  "title": "Resource conflict",
  "status": 409,
  "detail": "Service is already registered; use the individual update APIs",
  "instance": "/api/v1/admin/providers/15/service-registrations"
}
```

```json
{
  "type": "about:blank",
  "title": "Resource not found",
  "status": 404,
  "detail": "Provider service assignment not found",
  "instance": "/api/v1/admin/providers/15/service-registrations/14/requirements"
}
```

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "The request contains invalid fields",
  "instance": "/api/v1/admin/providers/15/service-registrations",
  "errors": {
    "source": "must not be null",
    "registration": "must not be null"
  }
}
```

Before retrying an uncertain successful write, reload context or approval detail to avoid duplicate registrations/documents/certificates. Reuse successful upload keys on validation retry. Source, note and successful admin operations are audited; uploads are not approval. No schema migration is required. Backend implementation is local, not deployed; admin dashboard screens still need integration.

