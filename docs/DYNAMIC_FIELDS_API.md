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

## Default selections

Custom-field create/update requests and definition responses support optional
`defaultValue`. For `SELECT`, send a single configured choice value:

```json
{"fieldType":"SELECT","options":["CAR","BIKE"],"defaultValue":"CAR"}
```

For `MULTI_SELECT`, send an array of unique configured choice values:

```json
{"fieldType":"MULTI_SELECT","options":["CAR","BIKE"],"defaultValue":["CAR","BIKE"]}
```

These are partial examples; retain the other required definition properties.
Defaults use choice values, not labels or choice objects. Omitted/null defaults
mean no preselection; an empty MULTI_SELECT array also selects nothing. Updates
replace the default, so omission/null clears it. Removing a defaulted choice
requires updating/clearing its default in the same request. Other field types
and PROVIDER_PROFILE fields reject non-null defaults; profile fields use the
actual profile value.

Frontend: show a single-choice default picker for SELECT and a multiple-choice
picker for MULTI_SELECT in the admin editor. On input forms, use `defaultValue`
only for a fresh, unanswered field. Preserve saved answers, reused answers and
explicitly cleared selections. Submit the selected value through the normal
answer API. Defaults are suggestions: the backend does not create submissions,
grant approval, fill missing booking answers or satisfy requirements from them.
Do not apply these form defaults to customer search filters automatically.

No frontend source is included in this backend repository. Apply
`DYNAMIC_FIELD_DEFAULTS_SCHEMA.sql` once when schema updates are managed manually;
the new nullable column is also covered by the existing Hibernate update setting.

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


## Customer-visible provider image galleries

IMAGE_UPLOAD provider requirements now support `customerVisible: true` with
`searchable: false`, `filterOperator: null` and `condition: null`. Image fields
are display-only. DOCUMENT_UPLOAD/FILE_UPLOAD can also be explicitly published
as described below. Booking upload fields remain private; uploads cannot be pricing
fields or search filters.

Example admin definition (POST or full replacement PUT on existing field):

```json
{
  "optionIds": [1],
  "scope": "PROVIDER_REQUIREMENT",
  "reuseScope": "ASSIGNMENT",
  "code": "PORTFOLIO_IMAGES",
  "label": "Work photos",
  "fieldType": "IMAGE_UPLOAD",
  "required": false,
  "requiresApproval": true,
  "minimumFiles": 0,
  "maximumFiles": 5,
  "maximumFileSizeMb": 10,
  "allowedFileTypes": ["JPG", "PNG", "WEBP"],
  "customerVisible": true,
  "searchable": false,
  "filterOperator": null,
  "condition": null
}
```

Option IDs must be valid in the endpoint's service context. Upload each image
through existing multipart `POST /api/v1/uploads/images`, then submit returned
keys through the provider answer API. Both `["providers/25/images/photo.jpg"]`
and `{"files":[{"key":"providers/25/images/photo.jpg"}]}` answer shapes work.
Existing provider documents-path raster images are also supported. Responses
never trust a submitted URL. Customer publication checks provider ownership,
image key format and stored JPG/PNG/WebP content type. Missing/malformed files
are omitted. Pending/rejected answers and private fields are omitted.

Booking-context now returns `providerDisplayFields`, covering customer-visible
field labels and types, including display-only images. `providerFilters` remains
searchable fields only. Join display definitions by fieldId to the candidate's
`providerAttributes` from provider-search. Example partial responses:

```json
{
  "providerDisplayFields": [
    {"fieldId":22,"code":"PORTFOLIO_IMAGES","label":"Work photos",
     "fieldType":"IMAGE_UPLOAD","options":null,"filterOperator":null,"icon":null}
  ]
}
```

```json
{
  "providerAttributes": {
    "22": [
      {"key":"providers/25/images/photo.jpg",
       "url":"https://example.com/temporary-signed-image-url",
       "expiresAt":"2026-10-26T10:10:00Z"}
    ]
  }
}
```

Only APPROVED image answers are published, consistent with existing public
provider attributes (auto-approved fields also qualify). URLs expire after ten
minutes; reload provider-search to refresh. The general `/uploads/view-url`
endpoint still permits only file owners/admins; customers receive only the
approved image URLs in discovery results. Frontend must render these image
arrays under the matching provider/service option. This repository contains no
frontend gallery implementation. Existing field visibility is not changed
by deployment; explicitly enable customerVisible on intended portfolio fields.


## Customer visibility for all provider field types

All 13 dynamic field types now accept `customerVisible: true` for unconditional
`PROVIDER_REQUIREMENT` fields. Existing definitions stay private unless explicitly
enabled. This does not change provider-profile binding allowlists, booking-field
privacy or conditional-field restrictions.

| Type | Customer value | Searchable |
|---|---|---|
| TEXT, TEXTAREA | string | No |
| DATE, TIME | string, unchanged from provider answer | No |
| PHONE, EMAIL | string | No |
| NUMBER | number | Yes |
| BOOLEAN | boolean | Yes |
| SELECT | configured string value | Yes |
| MULTI_SELECT | array of configured string values | Yes |
| IMAGE_UPLOAD | array of `{key,url,expiresAt}` | No |
| DOCUMENT_UPLOAD, FILE_UPLOAD | array of `{key,url,expiresAt}` | No |

For display-only fields send `searchable: false`, `filterOperator: null` and
`condition: null`. Searchable fields still require customerVisible and their
existing operator. Example full definition for a public phone field:

```json
{
  "optionIds": [1],
  "scope": "PROVIDER_REQUIREMENT",
  "reuseScope": "ASSIGNMENT",
  "code": "CONTACT_PHONE",
  "label": "Contact phone",
  "fieldType": "PHONE",
  "required": false,
  "requiresApproval": false,
  "customerVisible": true,
  "searchable": false,
  "filterOperator": null,
  "condition": null
}
```

For a document field use `fieldType: DOCUMENT_UPLOAD` (or FILE_UPLOAD), a distinct
code/label, and upload limits such as `minimumFiles: 0`, `maximumFiles: 5`,
`maximumFileSizeMb: 10`, `allowedFileTypes: ["PDF","JPG","PNG","WEBP"]`.
Upload through `/api/v1/uploads/documents` and submit the returned keys through
the normal provider answer API. Both file-array and `{files:[...]}` shapes work.
Public files retain the upload service's existing PDF/JPG/PNG/WebP support; this
change does not add video or arbitrary file formats.

Only approved manual answers are published; existing permitted profile-backed
PROVIDED values retain their behavior. Public upload links check provider
ownership and stored content type and expire in ten minutes. Pending/rejected,
private, foreign, missing and unsupported files are omitted. Submitted URLs are
ignored and fresh URLs are signed by the backend. `/uploads/view-url` ownership
rules remain unchanged.

Customer frontend reads labels/types from booking-context `providerDisplayFields`
and values from provider-search candidates' `providerAttributes`. Example partial
candidate response with explicitly public fields:

```json
{
  "providerAttributes": {
    "31": "Experienced home-care provider",
    "32": "+94771234567",
    "33": "provider@example.com",
    "34": "2026-10-26",
    "35": "09:30",
    "36": [
      {
        "key": "providers/25/documents/certificate.pdf",
        "url": "https://example.com/temporary-signed-document-url",
        "expiresAt": "2026-10-26T10:10:00Z"
      }
    ]
  }
}
```

Render text as text, long descriptions as paragraphs, contact values as contact
links, date/time strings with appropriate formatting, images as a gallery and
PDF/files as view/download links. `providerFilters` includes only searchable
SELECT/MULTI_SELECT/NUMBER/BOOLEAN fields; pricing discovery remains independent.
There is no frontend source in this repository; it must render these returned
values. No database migration is needed for this extension.
