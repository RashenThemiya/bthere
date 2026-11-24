# Customer "My documents" (no service selection)

The Profile page can list and fill every document a customer may need, without choosing a service or option first.
All endpoints need `Authorization: Bearer <access-token>` with the CUSTOMER role.

> Built on 24 November 2026; not deployed yet. Existing endpoints and fields are unchanged; new fields are added at
> the end of `documents[]` items.

## Answers in short

| Question | Answer |
|---|---|
| Does `profile-overview` `documents[]` list every requirement? | **It does after this change.** Before, it listed only `GLOBAL` requirements plus ones already submitted, so an option-only requirement (production's `CUSTOMER_HOME_CARE_GN`) did not show until uploaded. Now every active upload requirement that applies to at least one active service or option is listed, as `MISSING` until submitted. `GET /customers/me/documents` returns the same list on its own. |
| How do I get one form by `requirementId`? | `GET /api/v1/customers/me/documents/{requirementId}`. It returns the definition, upload rules and the current submission. |
| How is a document reused? | One requirement = one record = one submission per customer. A requirement can target many services/options (`targets`), and the one upload and its approval count for all of them. |
| What if admins made two different requirements for the same document? | They are two records and need two uploads; the backend does not merge them. An admin should keep one requirement, add the other's targets to it, and deactivate the other. Production has only one requirement today, so there are no duplicates. |
| Scope: all markets or the customer's market? | **All markets.** Requirements have no market, and the customer profile has no selected market. The list is the same everywhere. |
| Do `/uploads/documents`, `/requirement-submissions`, `/uploads/view-url` change? | No. They work as before. |

## Endpoints

| Method | Path | Use |
|---|---|---|
| GET | `/api/v1/customers/me/documents` | **New.** Profile list (same items as `profile-overview` `documents[]`) |
| GET | `/api/v1/customers/me/documents/{requirementId}` | **New.** One requirement with upload rules and current submission |
| GET | `/api/v1/customers/me/profile-overview` | Unchanged URL; `documents[]` now lists every active document and has the new fields |
| POST | `/api/v1/uploads/documents` | Upload one file (multipart part `file`). Returns a private `key`. Use it for **every** upload type, `IMAGE_UPLOAD` included. |
| PUT | `/api/v1/customers/me/requirement-submissions` | Submit or resubmit by `requirementId` |
| GET | `/api/v1/uploads/view-url?key=` | 10-minute signed URL to show an uploaded file |
| GET | `/api/v1/customers/me/requirements?serviceTypeId=&optionId=` | Booking-flow form for one service/option. Items now also have `status`, `statusReason`, `actionRequired`. |
| GET | `/api/v1/customers/me/eligibility?serviceTypeId=&optionId=` | Booking check (unchanged) |

## Which documents are listed

An active requirement with an upload field type (`IMAGE_UPLOAD`, `DOCUMENT_UPLOAD` or `FILE_UPLOAD`) is listed
when either of these is true:
- At least one of its targets is `GLOBAL`, or an active service, or an active option of an active service.
- The customer has already submitted it.

Each requirement is listed once, ordered by `displayOrder`. Inactive requirements are never listed.
`services[]` shows only the targets that are active now. Non-upload details (text, date, and so on) are not listed
here; the booking flow form still collects them.

## Document item

Production's one requirement today, for a customer who has not uploaded it (service and option names are examples):

```json
{
  "requirementId": 1,
  "code": "CUSTOMER_HOME_CARE_GN",
  "name": "Home Care customer Grama Niladhari certificate",
  "fieldType": "DOCUMENT_UPLOAD",
  "required": true,
  "blocksBooking": true,
  "services": [
    { "scope": "OPTION", "serviceTypeId": 1, "serviceName": "Caregiver", "optionId": 2, "optionName": "Home Care" }
  ],
  "status": "MISSING",
  "submissionId": null,
  "reviewedAt": null,
  "expiresOn": null,
  "rejectionReason": null,
  "description": null,
  "verificationMode": "DOCUMENT_REVIEW",
  "statusReason": "NOT_SUBMITTED",
  "actionRequired": true,
  "upload": {
    "minimumFiles": 1,
    "maximumFiles": 2,
    "maximumFileSizeMb": 10,
    "allowedFileTypes": ["PDF", "JPG", "PNG", "WEBP"],
    "allowedContentTypes": ["application/pdf", "image/jpeg", "image/png", "image/webp"],
    "uploadEndpoint": "POST /api/v1/uploads/documents"
  },
  "fileKeys": [],
  "definitionVersion": 1,
  "submittedDefinitionVersion": null,
  "answerVersion": null
}
```

| Field | Null when | Meaning |
|---|---|---|
| `status` | never | Effective status: `MISSING`, `PENDING`, `APPROVED`, `PROVIDED`, `REJECTED`, `EXPIRED` |
| `statusReason` | `APPROVED` / `PROVIDED` | See the table below |
| `actionRequired` | never | `true` for `MISSING`, `REJECTED`, `EXPIRED`: show an upload button |
| `upload` | not an upload field | File rules for the form |
| `upload.maximumFileSizeMb` | — | Smaller of the requirement's limit and the server upload limit (10 MB) |
| `fileKeys` | never (empty list) | Keys of the files in the current submission; open with `GET /uploads/view-url?key=` |
| `submissionId`, `answerVersion`, `submittedDefinitionVersion` | not submitted | Current submission |
| `reviewedAt` | not reviewed, or reset after a resubmit | Admin review time |
| `rejectionReason` | not rejected | Admin's reason |
| `expiresOn` | customer gave no expiry | Expiry date the customer entered |
| `description` | admin left it empty | Help text |
| `services[].serviceTypeId/serviceName/optionId/optionName` | `GLOBAL` (all null) or `SERVICE` (option fields null) | Where this document is used |

### `status` and `statusReason`

| `status` | `statusReason` | When | Show | `actionRequired` |
|---|---|---|---|---|
| `MISSING` | `NOT_SUBMITTED` | Never uploaded | "Upload" | true |
| `MISSING` | `DEFINITION_CHANGED` | Admin changed the requirement after the upload (`definitionVersion` ≠ `submittedDefinitionVersion`) | "Requirements changed. Upload again." | true |
| `PENDING` | `IN_REVIEW` | Waiting for admin | "Under review" | false |
| `PENDING` | `SUPPORTING_EVIDENCE_CHANGED` | Was approved, but a document it depends on changed | "Under review again" | false |
| `APPROVED` | `null` | Accepted | "Approved" | false |
| `PROVIDED` | `null` | No review needed (`verificationMode: NONE`) | "Saved" | false |
| `REJECTED` | `REJECTED` | See `rejectionReason` | "Rejected: {reason}. Upload again." | true |
| `EXPIRED` | `EXPIRED` | `expiresOn` is before today (Sri Lanka date) | "Expired. Upload a new one." | true |

Existing apps that read only `status` keep working: the statuses are the same as before.

## Reuse rules

1. A requirement has one unique, never-changing `code` and one or more `targets` (`GLOBAL`, `SERVICE`, `OPTION`).
2. A customer has at most one submission per requirement. It is reused for every target, so the customer uploads
   once. An approval is reused too: booking any of those services/options sees the same `APPROVED` status.
3. The admin reuses a document by **adding targets** to the existing requirement. Creating a new requirement with
   the same `code` is refused (409 "Requirement code already exists; add targets to reuse it").
4. Two requirements with **different codes** are separate, even if they describe the same paper. Each needs its own
   upload and review. To fix this, an admin keeps one requirement, adds the other's targets to it, and sets the other
   to `INACTIVE`. Uploads made against the inactive one are not moved; those customers upload again.
5. When an admin edits a requirement, `definitionVersion` goes up. Every submission made under the old version
   becomes `MISSING` / `DEFINITION_CHANGED` and must be uploaded again.
6. Resubmitting resets review: the new upload is `PENDING` (or `PROVIDED` when no review is needed). This also
   applies to an `APPROVED` document. If the requirement has `blocksBooking: true`, booking those services is blocked
   again until the admin approves. Ask the customer to confirm before replacing an approved document.

## Files

| Rule | Value |
|---|---|
| Types | PDF, JPG, PNG, WebP (`allowedFileTypes`, may be fewer). `IMAGE_UPLOAD` never accepts PDF. |
| Count | `minimumFiles`–`maximumFiles` (default 1–1) in one submission |
| Size per file | `upload.maximumFileSizeMb` (requirement limit, and at most the server's 10 MB) |
| Storage | Private. Key form: `customers/{userId}/documents/{uuid}.{ext}`. Only the owner and admins can open it. |
| Expiry | Optional `expiresOn` (`YYYY-MM-DD`) sent with the submission; it cannot be in the past. Today is the Sri Lanka date. |

## Flow

1. **List:** `GET /customers/me/documents` (or `profile-overview`). Show items with `actionRequired: true` first.
2. **Open a form:** `GET /customers/me/documents/{requirementId}`. Use `upload` to set the file picker (types,
   count, size) and `fileKeys` to show the current files.
3. **Upload each file:** `POST /api/v1/uploads/documents`, multipart part `file`.

   ```json
   { "key": "customers/40/documents/3f6c1c0e-9a51-4c1b-8d55-0f1c2a9b7e10.pdf",
     "url": "s3://<bucket>/customers/40/documents/3f6c1c0e-9a51-4c1b-8d55-0f1c2a9b7e10.pdf",
     "contentType": "application/pdf", "size": 482113 }
   ```

4. **Submit:** `PUT /customers/me/requirement-submissions`. Send all files of the document together. Several
   documents can go in one call.

   ```json
   { "submissions": [
       { "requirementId": 1,
         "value": { "files": [ { "key": "customers/40/documents/3f6c1c0e-9a51-4c1b-8d55-0f1c2a9b7e10.pdf" } ] },
         "expiresOn": "2027-11-30" } ] }
   ```

   `200 OK`:

   ```json
   [ { "submissionId": 12, "customerId": 4, "requirementId": 1, "code": "CUSTOMER_HOME_CARE_GN",
       "value": { "files": [ { "key": "customers/40/documents/3f6c1c0e-9a51-4c1b-8d55-0f1c2a9b7e10.pdf" } ] },
       "status": "PENDING", "storedStatus": "PENDING", "expiresOn": "2027-11-30",
       "reviewedBy": null, "reviewedAt": null, "rejectionReason": null,
       "answerVersion": 1, "definitionVersion": 1 } ]
   ```

5. **Review:** an admin approves or rejects it (`PATCH /api/v1/admin/customer-submissions/{id}/review`). There is
   no push for this yet; refresh the list when the Profile page opens.
6. **Booking:** `GET /customers/me/eligibility?serviceTypeId=1&optionId=2` uses the same submission. While the
   document is not `APPROVED`, a `blocksBooking` requirement makes the booking call fail with the eligibility reasons.

Show a file: `GET /api/v1/uploads/view-url?key=customers%2F40%2Fdocuments%2F3f6c….pdf` →
`{"key": "...", "url": "https://…signed…", "expiresAt": "2026-11-24T10:20:00Z"}` (valid 10 minutes).

## Errors

All errors use the standard body `{type, title, status, detail, instance}`; validation errors add `errors`.

| Call | Status | `detail` |
|---|---|---|
| `GET /documents`, `GET /documents/{id}`, `PUT /requirement-submissions` without a customer profile | 404 | "Complete the customer profile first" |
| `GET /documents/{id}` for an unknown or inactive requirement | 404 | "Active customer requirement not found" |
| `POST /uploads/documents` with no file | 400 | "Document file is required" |
| `POST /uploads/documents` with another type | 400 | "Only PDF, JPG, PNG and WebP files are allowed" |
| `POST /uploads/documents` over the server limit | 413 | "File must not exceed 10 MB", `code: "FILE_TOO_LARGE"` |
| `PUT /requirement-submissions`: wrong number of files | 400 | "Invalid files array: {code}" |
| same: a file without `key`, or the same key twice | 400 | "Each file requires a key" / "Duplicate file key" |
| same: key not from this customer's `/uploads/documents` | 400 | "File must be a private document owned by this customer" |
| same: key that was never uploaded | 400 | "Uploaded file does not exist" |
| same: type not allowed or file over the requirement's size | 400 | "File type or size is not allowed: {code}" |
| same: `expiresOn` in the past | 400 | "Evidence expiry date cannot be in the past" |
| same: requirement inactive or unknown | 404 | "Active customer requirement not found" |
| same: same `requirementId` twice in one call | 400 | "Duplicate requirement ID" |
| same: `submissions` missing or more than 100 | 400 | validation error with `errors.submissions` |
| `GET /uploads/view-url` for someone else's file | 400 | "You cannot access this file" |

A failed `PUT` saves nothing from that call: the whole batch is checked first.

## Backward compatibility

- No endpoint was removed or renamed. Request bodies are unchanged.
- `documents[]` items keep all their old fields and meanings. They gain `description`, `verificationMode`,
  `statusReason`, `actionRequired`, `upload`, `fileKeys`, `definitionVersion`, `submittedDefinitionVersion` and
  `answerVersion`.
- `documents[]` can now contain more items (option/service documents not yet submitted). An app that shows the list
  as-is just shows them as `MISSING`.
- `GET /requirements` items gain `status`, `statusReason` and `actionRequired`; `requirement` and `submission` are
  unchanged.
- Correction to the older Profile doc: `IMAGE_UPLOAD` files must also be uploaded with `POST /uploads/documents`.
  A key from `POST /uploads/images` is refused by `PUT /requirement-submissions`.
