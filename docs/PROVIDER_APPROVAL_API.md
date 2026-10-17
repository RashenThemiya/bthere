# Provider-grouped approval APIs

Implemented locally on 18 October 2026; deployment is separate.
All three routes require a bearer token with SUPER_ADMIN or VERIFICATION_ADMIN.
CUSTOMER, SERVICE_PROVIDER and FINANCE_ADMIN are denied. Use Content-Type:
application/json for bulk review. Read operations do not alter approval state.

## Provider list

```http
GET /api/v1/admin/provider-approvals?status=PENDING&page=0&size=20
```

No body. status is case-sensitive: PENDING (default), APPROVED, REJECTED or ALL.
The filter selects providers having at least one **assignment** with that stored
verificationStatus; ALL selects providers with any assignment. It does not filter
individual evidence rows. Every service for each matched provider is returned,
including services with another status. An approved service with optional pending
evidence may therefore require status=ALL to appear. Provider rows are paginated
in the database and sorted by providerId ascending. page >= 0; size 1–100,
default 20. totalElements counts providers, not assignments or uploads.

HTTP 200 returns Spring Page<Summary>. Stable paging fields include content,
number, size, totalElements, totalPages, first, last, numberOfElements and empty;
additional Spring pageable/sort metadata can be serialized.

Example item in content:

```json
{
  "providerId": 12,
  "providerName": "Rashen Themiy",
  "pendingCount": 3,
  "missingCount": 1,
  "services": [
    {
      "assignmentId": 14,
      "serviceName": "Home Nurse",
      "verificationStatus": "PENDING",
      "pendingCount": 3,
      "missingCount": 1
    }
  ]
}
```

pendingCount counts distinct eligible pending review records (type + id). Shared
provider-level DOCUMENT records are counted once at provider level, even when
several services require them. Do not sum service pendingCount to compute the
provider total. Missing/expired/non-reviewable records do not contribute.
missingCount counts required rows that have no submission; provider missingCount
sums service missingCount. Missing counts are not the total blocker count:
expired/rejected evidence, unmet minimum selections, availability and service area
checks can also block approval. Read details.checks for those conditions.

## Provider detail

```http
GET /api/v1/admin/provider-approvals/12
```

providerId must be positive. No body or query parameters. HTTP 200 returns:

```json
{
  "providerId": 12,
  "providerName": "Rashen Themiy",
  "pendingCount": 1,
  "missingCount": 1,
  "services": [
    {
      "assignmentId": 14,
      "serviceTypeId": 3,
      "serviceName": "Home Nurse",
      "verificationStatus": "PENDING",
      "providerStatus": "ACTIVE",
      "adminStatus": "ACTIVE",
      "emergencyOverride": false,
      "checks": {
        "documents": false,
        "skills": true,
        "certificates": false,
        "education": true,
        "availability": true,
        "serviceAreas": true,
        "serviceOptions": true,
        "dynamicRequirements": true
      },
      "pendingCount": 1,
      "missingCount": 1,
      "documents": [
        {
          "type": "DOCUMENT",
          "id": 70,
          "definitionId": 8,
          "name": "NIC",
          "required": true,
          "requiresApproval": true,
          "verificationStatus": "PENDING",
          "missing": false,
          "expired": false,
          "reviewable": true,
          "documentKey": "providers/25/documents/example-nic.pdf",
          "rejectionReason": null,
          "expiryDate": null,
          "sourceAssignmentId": null,
          "details": {
            "documentTypeId": 7,
            "optionId": null,
            "documentNumber": null
          }
        }
      ],
      "skills": [],
      "certificates": [
        {
          "type": "CERTIFICATE",
          "id": null,
          "definitionId": null,
          "name": "Professional certificate",
          "required": true,
          "requiresApproval": true,
          "verificationStatus": "MISSING",
          "missing": true,
          "expired": false,
          "reviewable": false,
          "documentKey": null,
          "rejectionReason": null,
          "expiryDate": null,
          "sourceAssignmentId": 14,
          "details": null
        }
      ],
      "education": [],
      "dynamicRequirements": [],
      "serviceOptions": []
    }
  ]
}
```

Examples illustrate fields; IDs/names/statuses depend on the actual configuration.
services is sorted by assignmentId ascending. Provider name joins first/last name.
No NIC number, birth date or other unrelated profile fields are exposed.

Every evidence array uses the same Requirement shape:

| Field | Contract |
| --- | --- |
| type | DOCUMENT, SKILL, CERTIFICATE, EDUCATION, DYNAMIC_REQUIREMENT, SERVICE_OPTION |
| id | Actual submitted record ID; null if absent. Use this ID for review. |
| definitionId | Configured requirement/skill/field/option ID; nullable; never substitute it for id. DOCUMENT uses requirementId, not documentTypeId. |
| name | Display label, string |
| required | Current configuration says this evidence category/item is required |
| requiresApproval | Current configuration requires manual review |
| verificationStatus | Actual record status; absent records use MISSING |
| missing | No submitted record exists for this row |
| expired | expiryDate is before the server's current date |
| reviewable | Has an ID, needs manual approval, is PENDING and is not expired |
| documentKey | Stored document locator, nullable; not a signed public URL |
| rejectionReason | Stored rejection/review note, nullable |
| expiryDate | Nullable YYYY-MM-DD |
| sourceAssignmentId | Source assignment for assignment evidence; null for provider-wide documents |
| details | Type-specific JSON object or null |

Documents are selected from configured common/service and selected-option rules,
including optional requirements. A shared document can appear in several service
requirements. Skills include configured skills with a missing placeholder when
not selected. Certificate/education arrays include submitted rows and a missing
placeholder when REQUIRED but absent; REQUIRED means at least one acceptable
record, not that every historical certificate must be approved. ServiceOptions
includes selected options and a missing placeholder if required with none selected;
checks.serviceOptions enforces the actual approved minimum.

Dynamic requirements use ACTIVE PROVIDER_REQUIREMENT fields, selected options,
the existing condition evaluator, and approved reusable evidence. Conditional
fields not applicable to the selection are excluded. Direct submissions take
precedence over reusable approved submissions, matching current approval logic.
For reused evidence details.reused=true and sourceAssignmentId identifies its
origin. It is already approved and not reviewable through another assignment.
BOOKING_FIELD definitions are excluded.

Type-specific details:

- DOCUMENT: documentTypeId, optionId, documentNumber.
- SKILL: null (definitionId identifies the skill).
- CERTIFICATE: issuingOrganization, certificateNumber, issuedDate.
- EDUCATION: instituteName, fieldOfStudy, startDate, completionDate, result.
- DYNAMIC_REQUIREMENT: code, fieldType, reuseScope, reused, value, optionIds.
  value is the submitted JSON, preserving file keys and multi-file structure.
- SERVICE_OPTION: providerStatus, adminStatus, configurationStatus; a missing
  placeholder instead supplies minimumSelections.

checks uses the existing service approval engine for documents, skills,
certificates, education, availability, serviceAreas, serviceOptions and
 dynamicRequirements. false means that category is incomplete; the app must show
these blockers even when all evidence arrays are empty. An emergency override
can leave a service approved while ordinary checks are incomplete. Stored
verificationStatus and checks are separate; GET does not refresh/mutate stored
status. Time-expired evidence may therefore make checks false before a write
refreshes the stored assignment. No GET enables/disables a service.

## Atomic bulk review

```http
POST /api/v1/admin/provider-approvals/bulk-review
```

```json
{
  "assignmentId": 14,
  "reviews": [
    {"type":"DOCUMENT","id":70,"status":"APPROVED"},
    {"type":"SKILL","id":60,"status":"APPROVED"},
    {"type":"CERTIFICATE","id":81,"status":"REJECTED","rejectionReason":"Upload a valid certificate"}
  ]
}
```

assignmentId: required positive assignment ID (not providerId or serviceTypeId).
reviews: required array, 1–100 non-null items. Each item has:

- type: one of the six uppercase Requirement types above.
- id: required positive submitted record ID shown in details.
- status: APPROVED or REJECTED only, case-sensitive.
- rejectionReason: optional string <=500 characters; nonblank required for REJECTED.

The backend locks the provider and target rows in stable type/ID order, validates
all records before writing, and calls existing review services. Provider-wide
documents must belong to that provider and be an applicable requirement of the
chosen service. Other items must belong to the assignment and still be applicable.
An item must currently need approval, be PENDING and not expired. Reused evidence
from another assignment, already reviewed evidence, missing placeholders,
foreign-service IDs and duplicate type/id pairs are rejected. Reviewers cannot
supply a verification/admin status for the assignment itself through this route.

**Atomic behavior:** any validation/review/refresh/audit failure aborts the entire
transaction. No per-item partial successes are returned. Successful HTTP 200:

```json
{
  "atomic": true,
  "reviewedCount": 3,
  "provider": {
    "providerId": 12,
    "providerName": "Rashen Themiy",
    "pendingCount": 0,
    "missingCount": 0,
    "services": []
  }
}
```

provider is the full Detail shape, with actual updated services/evidence/checks;
services:[] above only abbreviates that repeated shape. approved evidence does
not force assignment APPROVED: the engine re-evaluates every provider assignment
and all prerequisites (including reusable/shared requirements). providerStatus,
adminStatus and existing emergency override behavior remain separate. Audit
PROVIDER_APPROVAL_BULK_REVIEW records actor, assignment and batch. Existing review
services also record their reviewer IDs/times. No idempotency header is supported:
replaying a batch after success normally returns 409 because rows are no longer
PENDING. After timeout, GET detail before deciding what remains to review.

## Errors and frontend sequence

400 invalid filter/page/size/body/type/status/ID, empty/oversized batch, duplicate
items or rejection without a reason; 401/403 authentication/role; 404 absent
provider/assignment/item or item outside current provider/service applicability;
409 stale/already reviewed/non-reviewable item. Other transaction/database errors
roll back; do not assume they provide a per-item result.

1. Fetch the provider page. Render provider groups and service pending/missing counts.
2. Expand one provider: fetch its detail once; render every evidence category and checks.
3. Use id and reviewable for selection; show missing/expired/rejected rows distinctly.
4. Submit one assignment's selected reviews. Do not mix assignment-scoped records.
5. Replace expanded content using response.provider and refresh the current list page.
6. On error/timeout, retain choices, reload current detail, and reselect only eligible rows.

No schema migration is required. Provider paging is DB-backed; detail assembly
uses repository calls per selected provider/service and is not a bulk SQL
projection. Load-test and batch/cache requirement definitions before scaling to
large review queues. Tests cover missing/reused evidence, grouping/counts,
ownership prevalidation, state validation, delegated refresh/audit, transaction
interceptor rollback and role denial. Production MySQL contention/rollback and
storage-view authorization need deployment-level verification.
