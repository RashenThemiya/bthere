# Customer details, documents, and eligibility

Implemented locally on 2026-10-16. Deployment is not verified. All endpoints use
`Authorization: Bearer <access-token>`. These requirements are separate from
`BOOKING_FIELD`: answers are saved per customer, rather than per booking.

## Model

Each requirement has one globally unique, immutable `code`, a dynamic field
definition, and one or more `targets`. A target is one of:

```json
{"scope": "GLOBAL", "serviceTypeId": null, "optionId": null}
```

```json
{"scope": "SERVICE", "serviceTypeId": 3, "optionId": null}
```

```json
{"scope": "OPTION", "serviceTypeId": 3, "optionId": 12}
```

Add multiple service/option targets to the same definition to reuse one answer
and approval across them. GLOBAL already covers every service/option and cannot
be combined with other targets. Option IDs must belong to the specified active
service. Applicable requirements accumulate; narrower scopes do not override or
relax broader requirements.

`required` tells the frontend whether the field needs completing. `blocksBooking`
independently controls booking access and requires `required: true`. A required
collect-only field (`blocksBooking: false`) does not stop booking. Services with
no applicable blocking requirements or age rules keep their existing behavior.

Verification modes:

| Mode | Result after submission | Approval |
| --- | --- | --- |
| NONE | PROVIDED | No review |
| ADMIN_REVIEW | PENDING | Admin reviews the submitted detail |
| DOCUMENT_REVIEW | PENDING | Admin reviews an upload or a detail supported by reviewed uploads |

## Admin requirement configuration

SUPER_ADMIN only:

```http
POST /api/v1/admin/customer-requirements
GET /api/v1/admin/customer-requirements
PUT /api/v1/admin/customer-requirements/{requirementId}
PATCH /api/v1/admin/customer-requirements/{requirementId}/status
```

Create returns `201`; reads, updates, and status changes return `200`.
POST/PUT use the same full definition. Array properties are required even when
empty. Omitted file limits default to one file, maximum 100 MB per file.

Create the evidence definition first:

```json
{
  "code": "NIC",
  "label": "National identity card — front and back",
  "description": "Upload clear images of both sides",
  "fieldType": "IMAGE_UPLOAD",
  "required": true,
  "verificationMode": "DOCUMENT_REVIEW",
  "blocksBooking": false,
  "targets": [{"scope": "GLOBAL", "serviceTypeId": null, "optionId": null}],
  "evidenceCodes": [],
  "choices": [],
  "minimumFiles": 2,
  "maximumFiles": 2,
  "maximumFileSizeMb": 10,
  "allowedFileTypes": ["JPG", "PNG", "WEBP"],
  "displayOrder": 1
}
```

Create a detail whose approval is tied to this evidence:

```json
{
  "code": "DATE_OF_BIRTH",
  "label": "Date of birth",
  "description": "Must match your NIC",
  "fieldType": "DATE",
  "required": true,
  "verificationMode": "DOCUMENT_REVIEW",
  "blocksBooking": false,
  "targets": [{"scope": "SERVICE", "serviceTypeId": 3, "optionId": null}],
  "evidenceCodes": ["NIC"],
  "choices": [],
  "minimumFiles": null,
  "maximumFiles": null,
  "maximumFileSizeMb": null,
  "allowedFileTypes": [],
  "displayOrder": 2
}
```

For a personal detail needing no review, use `verificationMode: "NONE"` and
`evidenceCodes: []`. For example, use `code: "GENDER"`, `fieldType: "SELECT"`,
and `choices: ["MALE", "FEMALE", "OTHER", "PREFER_NOT_TO_SAY"]`. Values are
configurable; the API does not impose a gender-based eligibility rule.

Supported field types: TEXT, TEXTAREA, NUMBER, BOOLEAN, DATE, TIME, PHONE, EMAIL,
SELECT, MULTI_SELECT, IMAGE_UPLOAD, DOCUMENT_UPLOAD, FILE_UPLOAD. DATE uses
`YYYY-MM-DD`, TIME uses ISO local time. MULTI_SELECT uses an array of configured
strings; upload values use the files object below. Text values are limited to
4,000 characters. File limits are 1–20 files and 1–100 MB per file; actual private
upload formats are PDF, JPG, PNG, WEBP. IMAGE_UPLOAD cannot accept PDF.

Evidence codes must reference existing active DOCUMENT_REVIEW upload definitions
without their own dependencies. Review of a detail requires approval of its
current evidence first. Evidence files alone do not automatically validate a DOB:
the reviewer must inspect the document and confirm the detail against it.

Responses contain `requirementId`, `version`, `status`, and `definition`.
PUT replaces the entire definition and increments its version. Customers must
resubmit after a definition update; old answers are not silently accepted against
new rules. Status body: `{"status":"ACTIVE"}` or `{"status":"INACTIVE"}`.
Inactive definitions are excluded from ordinary forms. If an enabled age rule or
reviewed detail still depends on inactive evidence, booking checks fail closed;
update dependent configuration when retiring requirements.

## Customer form and combined submission

CUSTOMER role; a saved customer profile is required:

```http
GET /api/v1/customers/me/requirements
GET /api/v1/customers/me/requirements?serviceTypeId=3&optionId=12
PUT /api/v1/customers/me/requirement-submissions
```

Without context, GET returns global requirements. Service context adds service
requirements; option context adds that option's requirements. The form also
includes evidence and DOB definitions needed by applicable rules. Each item has
`requirement` and `submission` (null if unanswered). Basic personal details can be
configured as dynamic requirements; existing profile fields are not copied or
automatically trusted as verified answers.

Upload evidence first:

```http
POST /api/v1/uploads/documents
Content-Type: multipart/form-data
file: <PDF/JPG/PNG/WEBP, maximum 100 MB>
```

For CUSTOMER tokens, upload returns a private key such as
`customers/25/documents/uuid.jpg` and an `s3://...` URL. Store the key; `25` is the
authenticated user ID. Provider uploads continue using the provider namespace.
Never use public profile-image uploads for identity documents.

```json
{
  "submissions": [
    {
      "requirementId": 1,
      "value": {
        "files": [
          {"key": "customers/25/documents/nic-front.jpg"},
          {"key": "customers/25/documents/nic-back.jpg"}
        ]
      },
      "expiresOn": null
    },
    {"requirementId": 2, "value": "2000-05-10", "expiresOn": null}
  ]
}
```

Use actual returned file keys and configured requirement IDs. Up to 100 supplied
answers save in one database transaction; omitted answers remain unchanged.
Submission validates file ownership, existence, actual S3 content type, size,
counts, selections, and value types. Expiry is submitted metadata subject to
review, not automatically extracted from a document. Replacing an answer restarts
review and increments `answerVersion`. Replacing/rejecting supporting evidence
also returns linked approved details to pending review.

PUT returns the submitted answers with `submissionId`, `customerId`,
`requirementId`, `code`, `value`, effective `status`, `storedStatus`, `expiresOn`,
review metadata, `answerVersion`, and `definitionVersion`. Effective status can be
MISSING (definition changed), PROVIDED, PENDING, APPROVED, REJECTED, or EXPIRED.
The form returns `submission: null` for a never-submitted requirement.

Private viewing uses:

```http
GET /api/v1/uploads/view-url?key=customers%2F25%2Fdocuments%2Fuuid.jpg
```

This returns a 10-minute URL for the owning customer or an authorized admin.

## Admin review

SUPER_ADMIN or VERIFICATION_ADMIN:

```http
GET /api/v1/admin/customer-submissions?status=PENDING&page=0&size=20
PATCH /api/v1/admin/customer-submissions/{submissionId}/review
```

Queue filters use stored status: PENDING, APPROVED, REJECTED, PROVIDED; inspect
effective `status` as well, since expiry can change it. Resubmission-required
answers may appear pending with effective MISSING. Ask the customer to resubmit
the current definition before approving them.

```json
{"status": "APPROVED", "rejectionReason": null, "answerVersion": 1}
```

```json
{"status": "REJECTED", "rejectionReason": "NIC image is unreadable", "answerVersion": 1}
```

Use `answerVersion` from the queue; a stale review returns 409. A rejection reason
is required. Approve evidence first, then the linked personal detail. Expired or
outdated evidence cannot be approved. Concurrent database edits are guarded by
optimistic locking.

## Optional age restrictions

SUPER_ADMIN configures each scope independently:

```http
PUT /api/v1/admin/customer-eligibility
PUT /api/v1/admin/provider-types/{serviceTypeId}/customer-eligibility
PUT /api/v1/admin/provider-types/{serviceTypeId}/options/{optionId}/customer-eligibility
GET /api/v1/admin/customer-eligibility
```

The first PUT is global. All PUTs accept:

```json
{
  "enabled": true,
  "minimumAge": 18,
  "dateOfBirthCode": "DATE_OF_BIRTH",
  "requiresVerifiedDateOfBirth": true,
  "requiredCodes": ["NIC"]
}
```

Use `enabled: false` to disable that scope's rule. `requiredCodes` can require
existing details/documents only for this scope, even when collected globally with
`blocksBooking: false`. Each listed code must be PROVIDED for NONE fields or
APPROVED for reviewed fields. For a document-only restriction, send `minimumAge:
null`, `dateOfBirthCode: null`, `requiresVerifiedDateOfBirth: false`, and
`requiredCodes: ["NIC"]`. An enabled rule needs minimumAge or a nonempty
requiredCodes list. Age restrictions require an active DATE definition; verified
DOB requires its verification mode to include review.
All applicable age rules must pass, so an option minimum of 21 tightens a global
minimum of 18. Age is calculated from DOB using the Asia/Colombo calendar date;
birthdays qualify on that date. No NIC decoding or external identity integration
is performed.

## Booking enforcement

```http
GET /api/v1/customers/me/eligibility?serviceTypeId=3&optionId=12
```

```json
{
  "eligible": false,
  "reasons": [
    {"code": "DATE_OF_BIRTH_UNVERIFIED", "requirementCode": "DATE_OF_BIRTH", "message": "Provide a current approved date of birth"}
  ]
}
```

Reasons include MISSING, PENDING, REJECTED, EXPIRED, DATE_OF_BIRTH_UNVERIFIED,
INVALID_DATE_OF_BIRTH, and MINIMUM_AGE. Read eligibility for the selected option
before showing the booking action; the backend independently enforces the same
check in `POST /api/v1/customers/me/bookings` before saving a job. Failure returns
403 ProblemDetail with an `eligibility` object containing the reasons.

Other errors: 400 invalid configuration/value/file/selection; 401 unauthenticated;
403 wrong role; 404 missing customer profile or active resource; 409 duplicate code,
stale answer review, or concurrent edit. Administrative configuration is global to
this application; no public endpoint exposes another customer's submissions.

## Persistence

New tables: `customer_requirement`, `customer_requirement_submission`, and
`customer_eligibility_rule`. The project currently uses Hibernate schema update;
deployments using schema validation/manual migrations must create these tables
and constraints before enabling the endpoints. File validation needs S3 HeadObject
permission in addition to existing private upload/view permissions.
