# Admin-managed service fields

23 October update: option-scoped definitions/IDs are shared across linked services.
See [FULLY_SHARED_OPTIONS_API.md](FULLY_SHARED_OPTIONS_API.md) for shared field APIs
and provider answer/approval reuse. Service-common configuration remains distinct.

This module lets Super Admin configure provider requirements and customer
booking schemas without backend code changes.

## Scopes

- `PROVIDER_REQUIREMENT`: completed by a provider and optionally reviewed.
- `BOOKING_FIELD`: completed by a customer for each job (job submission is a later module).

Use `optionIds` to apply one field to multiple options of the same service:

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

POST creates one field ID; PUT replaces the field's complete option list.
An empty or omitted `optionIds` applies the field to the complete service.
Every ID must be unique and identify an active option of that service.
The legacy `optionId` is still supported, but do not send both properties.
Responses include `optionIds`, including for existing single-option fields.

The same field appears under each configured option in the schema. Selecting
any configured option makes it applicable; selecting several still requires
only one answer per provider-service registration. `reuseScope` controls reuse
between separate field definitions or registrations, not which options apply.

## Reusing provider answers

### Reuse provider profile values without duplicate inputs

Provider requirements may link directly to existing profile data. Admin sets
valueSource to PROVIDER_PROFILE and profileField to an allowlisted field. Omitted
valueSource defaults to MANUAL; existing definitions are unchanged and must be
explicitly linked if they should use the profile. Do not infer links from code/label.

```json
{
  "scope":"PROVIDER_REQUIREMENT",
  "code":"GENDER",
  "label":"Gender",
  "fieldType":"SELECT",
  "required":true,
  "requiresApproval":false,
  "valueSource":"PROVIDER_PROFILE",
  "profileField":"gender",
  "options":["MALE","FEMALE","OTHER"],
  "customerVisible":true,
  "searchable":true
}
```

| profileField | Compatible fieldType |
| --- | --- |
| firstName, lastName, nicNumber, passportNumber, profilePhoto | TEXT |
| bio | TEXT or TEXTAREA |
| gender | TEXT or SELECT |
| dateOfBirth | DATE |
| age | NUMBER; calculated from profile DOB on the Asia/Colombo calendar date |
| marketId | NUMBER; current active profile market |

Only gender (SELECT) and derived age (NUMBER) may be enabled for customer-visible
dynamic attributes/search. Other profile details remain private in this mechanism.
BOOKING_FIELD cannot bind to provider profiles. MANUAL fields cannot set profileField.
Unsupported names/types return 400. SELECT profile values must exactly match configured
choice values, including case; otherwise resolution reports INVALID. Missing/blank data
reports MISSING. A future DOB cannot supply DOB/age. No normalization or guessed value
mapping occurs.

GET /api/v1/providers/me/services/{assignmentId}/submissions resolves applicable linked
values live. Each includes valueSource, profileField and readOnly. Example item:

```json
{
  "submissionId":null,"assignmentId":14,"fieldId":25,"fieldCode":"GENDER",
  "fieldLabel":"Gender","value":"FEMALE","verificationStatus":"PROVIDED",
  "reviewedBy":null,"reviewedAt":null,"reviewNote":null,"reuseScope":"ASSIGNMENT",
  "reused":false,"sourceAssignmentId":null,"valueSource":"PROVIDER_PROFILE",
  "profileField":"gender","readOnly":true
}
```

Render read-only with an Edit profile action; on MISSING/INVALID direct the provider
to correct the profile/admin choices. Do not submit these fields in standalone or combined
registration requirements. Duplicate submissions return 400. No new answer row is stored.
Legacy manual rows are retained, but cannot override a linked field's live profile value.
They are excluded from manual review queues/counts and cross-field answer reuse while
their definition is profile-backed.
Profile changes are reflected on the next read/search and refresh service completeness.
Creating, changing or activating/deactivating profile-linked definitions also refreshes
existing assignments for that service.
Required linked fields must resolve PROVIDED; conditions can read linked parent values.
Admin grouped approval details show these as nonreviewable rows, with no submission ID.

PROVIDED is self-declared profile data, not APPROVED identity evidence. requiresApproval
must be false for linked fields; use separate reviewed documents/requirements for evidence.
This implementation intentionally rejects approval on linked fields rather than silently
trusting profile values. Customer filtering accepts explicitly published PROVIDED gender/age
as well as approved manual answers. Existing manual answer reuse continues independently;
reuseScope does not copy or override profile-linked data.

Schema rollout adds nullable value_source and profile_field columns to service_custom_field
(Hibernate schema update handles these locally). Manually managed databases must apply:

```sql
ALTER TABLE service_custom_field
  ADD COLUMN value_source VARCHAR(255) NULL,
  ADD COLUMN profile_field VARCHAR(255) NULL;
```

There is no migration of existing values, no customer-profile linking and no patient-data
autofill. Keep existing profile and requirement data until mappings have been reviewed.

Provider requirements accept `reuseScope`:

- `ASSIGNMENT`: answer separately for each provider-service registration. This
  is the default and preserves existing behavior.
- `SERVICE`: reuse an approved answer with the same field `code` and
  `fieldType` across options belonging to the same service.
- `PROVIDER`: reuse an approved answer with the same field `code` and
  `fieldType` across all services belonging to the provider.

`BOOKING_FIELD` definitions must use `ASSIGNMENT`. Reuse is only available for
`PROVIDER_REQUIREMENT` fields. Reusable definitions must use the same stable
`code` and compatible `fieldType`, for example `DRIVING_EXPERIENCE` and
`NUMBER`. Changing the label does not prevent reuse.

When the provider submissions endpoint returns an inherited answer, it uses
the target field ID and contains `"reused": true`, the configured
`reuseScope`, and `sourceAssignmentId`. The provider does not submit or verify
that value again. Submitting a new value for the target field creates an
assignment-specific replacement and restarts approval when approval is
required.

## Admin APIs

### Optional field icons

Both PROVIDER_REQUIREMENT and BOOKING_FIELD definitions accept an optional `icon`.
Super Admin first uploads the image through `POST /api/v1/uploads/images`
(`multipart/form-data`, part `file`; JPG, PNG, WebP or sanitized SVG, max 10 MB).
Use the returned key and url in the existing custom-field create/update request:

```json
{
  "scope": "PROVIDER_REQUIREMENT",
  "code": "EXPERIENCE",
  "label": "Experience years",
  "fieldType": "NUMBER",
  "customerVisible": true,
  "searchable": true,
  "icon": {
    "key": "service-icons/experience.png",
    "url": "https://your-bucket.example/service-icons/experience.png",
    "altText": "Experience",
    "displayOrder": 0
  }
}
```

The same `icon` object works for BOOKING_FIELD and nested option
`dynamicFields[].definition`. Key and url are required when icon is present;
altText and displayOrder are optional. The icon is decorative field metadata,
not a provider/customer answer. Individual select choices can also have their
own icons as described below.
It is returned on custom-field responses, option dynamic fields, onboarding and
booking schemas, and customer booking-context providerFilters. Frontends render
the icon beside the label, falling back to the label when null.

Create without icon stores no icon. Custom-field PUT replaces the full definition:
send the existing icon to keep it; omitted/null icon removes the attachment.
Removal does not delete the uploaded S3 object. Existing definitions need no icon.

Persistence adds nullable TEXT `service_custom_field.icon_json`. Current Hibernate
schema update creates it; manually managed schemas must apply before rollout:

```sql
ALTER TABLE service_custom_field ADD COLUMN icon_json TEXT NULL;
```

### Icons for individual select choices

SELECT and MULTI_SELECT `options` accept legacy strings and objects with a stable
string `value`, optional display `label`, and optional `icon` media. Each choice
can have its own icon, separately from the field's overall icon:

```json
{
  "scope": "PROVIDER_REQUIREMENT",
  "code": "VEHICLE_TYPE",
  "label": "Vehicle type",
  "fieldType": "SELECT",
  "customerVisible": true,
  "searchable": true,
  "options": [
    {
      "value": "CAR",
      "label": "Car",
      "icon": {
        "key": "service-icons/car.png",
        "url": "https://your-bucket.example/service-icons/car.png",
        "altText": "Car"
      }
    },
    {"value": "VAN", "label": "Van", "icon": null},
    "BIKE"
  ]
}
```

Upload each icon first through the existing image-upload endpoint and use its
returned key/url. Without a label, display the value; without an icon, display
text only. Response schemas and customer providerFilters preserve the complete
options metadata, including choice icons. Existing string choices still work.
This also works for BOOKING_FIELD, with customerVisible/searchable omitted or false.

Provider/booking SELECT answers remain `"CAR"`; MULTI_SELECT answers remain
`["CAR","BIKE"]`. Customer providerFilters use `{"fieldId":25,"value":["CAR"]}`.
Do not submit labels, icons or choice objects as answers/filters. Labels and icons
can change without changing saved values. Choice values must be unique nonempty
strings (including across string/object entries); invalid choices are rejected
on provider submissions. Present icons require nonblank key/url with the same
length limits as field icons; altText/displayOrder are optional. Malformed icons,
invalid labels and duplicate/missing values return 400 on admin create/update.
No additional database columns are required; metadata uses existing options_json.

Provider fields can opt into customer display/search with `customerVisible`,
`searchable`, and `filterOperator`. Both flags default false. See
[dynamic provider filter contract](CUSTOMER_DISCOVERY_API.md#dynamic-provider-filters)
for supported types, operators, request/response examples and schema rollout.

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
  "reuseScope": "SERVICE",
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
  "reuseScope": "ASSIGNMENT",
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
  "reuseScope": "PROVIDER",
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

## Dynamic fields in option APIs

Both option list endpoints include `dynamicFields` on each option:

- `GET /api/v1/provider-types/{serviceTypeId}/options` returns active fields.
- `GET /api/v1/admin/provider-types/{serviceTypeId}/options` also includes inactive fields.

The list includes fields assigned to the option and common service-wide fields,
with their `fieldId`, `optionIds`, `scope`, `reuseScope`, and complete definition.

Super Admin can create or edit dynamic fields in the same option request:

- `POST /api/v1/admin/provider-types/{serviceTypeId}/options`
- `PUT /api/v1/admin/provider-types/{serviceTypeId}/options/{optionId}`

Add this property alongside the usual option properties:

```json
{
  "dynamicFields": [
    {
      "fieldId": 25,
      "definition": {
        "scope": "PROVIDER_REQUIREMENT",
        "reuseScope": "ASSIGNMENT",
        "code": "DRIVING_EXPERIENCE",
        "label": "Driving experience in years",
        "fieldType": "NUMBER",
        "required": true,
        "requiresApproval": false,
        "displayOrder": 1
      }
    }
  ]
}
```

Omit `fieldId` to create a new field. Include it to update an existing field
that applies to this option. Each `definition` is a full custom-field request.
Omitting option IDs assigns new fields to the current option and preserves
existing fields' option assignments. Explicit `optionIds` replaces those
assignments and must include the current option; `[]` makes the field common.
Editing a shared or common definition changes it for every option using it.

Omitted, null, or empty `dynamicFields` leaves existing fields unchanged;
fields omitted from the array are also preserved. Use the custom-field status
API to deactivate a field. Option and field changes are saved in one transaction.

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
