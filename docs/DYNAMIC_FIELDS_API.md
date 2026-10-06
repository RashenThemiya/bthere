# Admin-managed service fields

This module lets Super Admin configure provider requirements and customer
booking schemas without backend code changes.

## Scopes

- `PROVIDER_REQUIREMENT`: completed by a provider and optionally reviewed.
- `BOOKING_FIELD`: completed by a customer for each job (job submission is a later module).

Leaving `optionId` null applies a field to the complete service. Supplying an
option ID applies it only to that service option.

## Admin APIs

- `POST /api/v1/admin/provider-types/{serviceTypeId}/custom-fields`
- `GET /api/v1/admin/provider-types/{serviceTypeId}/custom-fields`
- `PUT /api/v1/admin/provider-types/{serviceTypeId}/custom-fields/{fieldId}`
- `PATCH /api/v1/admin/provider-types/{serviceTypeId}/custom-fields/{fieldId}/status`

Supported types are `TEXT`, `TEXTAREA`, `NUMBER`, `BOOLEAN`, `DATE`, `TIME`,
`PHONE`, `EMAIL`, `SELECT`, `MULTI_SELECT`, `IMAGE_UPLOAD`, `DOCUMENT_UPLOAD`,
and `FILE_UPLOAD`.

Example provider image requirement:

```json
{
  "optionId": 2,
  "scope": "PROVIDER_REQUIREMENT",
  "code": "VEHICLE_PHOTOS",
  "label": "Vehicle photos",
  "description": "Upload front, rear and side photos",
  "fieldType": "IMAGE_UPLOAD",
  "required": true,
  "requiresApproval": true,
  "minimumFiles": 1,
  "maximumFiles": 3,
  "maximumFileSizeMb": 10,
  "allowedFileTypes": ["JPG", "PNG", "WEBP"],
  "options": null,
  "condition": null,
  "displayOrder": 4
}
```

Example Hospital Care booking field:

```json
{
  "optionId": 2,
  "scope": "BOOKING_FIELD",
  "code": "HOSPITAL_NAME",
  "label": "Hospital name",
  "fieldType": "TEXT",
  "required": true,
  "requiresApproval": false,
  "displayOrder": 1
}
```

Example conditional provider field:

```json
{
  "optionId": null,
  "scope": "PROVIDER_REQUIREMENT",
  "code": "VEHICLE_REGISTRATION",
  "label": "Vehicle registration",
  "fieldType": "TEXT",
  "required": true,
  "requiresApproval": true,
  "condition": {
    "fieldCode": "HAS_VEHICLE",
    "operator": "EQUALS",
    "value": true
  }
}
```

## Schemas used by frontends

- `GET /api/v1/provider-types/{serviceTypeId}/onboarding-schema`
- `GET /api/v1/provider-types/{serviceTypeId}/booking-schema`

Both return common fields and fields grouped by active service option.
The response also includes `serviceSetup`, containing standard document and
skill requirements, availability/location switches, certificate/education
rules, and option-selection limits. This lets the frontend build complete
onboarding from one response.

## Provider submissions

- `GET /api/v1/providers/me/services/{assignmentId}/submissions`
- `PUT /api/v1/providers/me/services/{assignmentId}/submissions`

```json
{
  "submissions": [
    {
      "fieldId": 25,
      "value": {
        "files": [
          { "key": "providers/15/images/front.jpg" },
          { "key": "providers/15/images/rear.jpg" }
        ]
      }
    }
  ]
}
```

Required provider fields are included in service completion. Fields requiring
approval remain incomplete until approved.

## Admin provider review

- `GET /api/v1/admin/provider-submissions?status=PENDING&page=0&size=20`
- `PATCH /api/v1/admin/provider-submissions/{submissionId}/review`

Review body uses `APPROVED` or `REJECTED` and an optional `rejectionReason`.
