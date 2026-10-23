# Configurable provider document parts

Implemented locally; not deployed or applied to production. Admins can configure
any number of named parts per document type. Each part permits 1–100 files, each
up to 100 MB, with a configured subset of PDF/JPEG/PNG/WebP MIME types. A required
part needs at least one file; an optional part can be omitted. Repeated part codes
and reuse of the same object key for different parts are rejected.

## Configure a type

SUPER_ADMIN uses POST `/api/v1/admin/document-types` (201), or PUT
`/api/v1/admin/document-types/{documentTypeId}` (200) with:

```json
{
  "name":"Driving Licence",
  "description":"Upload both sides of your licence",
  "hasExpiry":true,
  "issuedDateMode":"OPTIONAL",
  "expiryDateMode":"REQUIRED",
  "parts":[
    {"code":"FRONT","label":"Front image","required":true,"maximumFiles":1,"maximumFileSizeMb":5,"allowedFileTypes":["image/jpeg","image/png"]},
    {"code":"BACK","label":"Back image","required":true,"maximumFiles":1,"maximumFileSizeMb":5,"allowedFileTypes":["image/jpeg","image/png"]},
    {"code":"EXTRA","label":"Additional pages","required":false,"maximumFiles":5,"maximumFileSizeMb":10,"allowedFileTypes":["application/pdf","image/jpeg"]}
  ]
}
```

The response contains id, name, description, hasExpiry, status, issuedDateMode,
expiryDateMode and the complete parts array. Codes are unique uppercase identifiers
matching `[A-Z][A-Z0-9_]{0,49}`. Labels are required, at most 150 characters.
Parts are ordered by their array position. Existing date-mode behavior is unchanged.

GET `/api/v1/admin/document-types` includes inactive types. Provider GET
`/api/v1/document-types` returns active types. Both include parts. Shared-option
and service document requirement responses also include parts and date modes.

On PUT, omitted/null parts preserves existing configuration; explicit `[]` restores
legacy single-file mode. This edits configuration only. Existing provider documents
and reviews are never deleted or reset by an admin configuration edit.

## Provider submission

Upload each file through the existing private provider document upload endpoint.
Then SERVICE_PROVIDER uses POST `/api/v1/providers/me/documents`:

```json
{
  "documentTypeId":12,
  "documentNumber":"B1234567",
  "issuedDate":"2025-01-01",
  "expiryDate":"2030-01-01",
  "parts":[
    {"code":"FRONT","documentKeys":["providers/25/documents/front.jpg"]},
    {"code":"BACK","documentKeys":["providers/25/documents/back.jpg"]}
  ]
}
```

Illustrative response projection (HTTP 201; other normal metadata remains):

```json
{
  "documentId":45,
  "documentTypeId":12,
  "documentTypeName":"Driving Licence",
  "documentKey":"providers/25/documents/front.jpg",
  "verificationStatus":"PENDING",
  "parts":[
    {"code":"FRONT","documentKeys":["providers/25/documents/front.jpg"]},
    {"code":"BACK","documentKeys":["providers/25/documents/back.jpg"]}
  ],
  "partDefinitions":[
    {"code":"FRONT","label":"Front image","required":true,"maximumFiles":1,"maximumFileSizeMb":5,"allowedFileTypes":["image/jpeg","image/png"]},
    {"code":"BACK","label":"Back image","required":true,"maximumFiles":1,"maximumFileSizeMb":5,"allowedFileTypes":["image/jpeg","image/png"]},
    {"code":"EXTRA","label":"Additional pages","required":false,"maximumFiles":5,"maximumFileSizeMb":10,"allowedFileTypes":["application/pdf","image/jpeg"]}
  ]
}
```

One provider/type still has one document row and one bundle-level approval. POST
replaces the complete bundle; send all required parts every time. Optional files
omitted on replacement are removed from the current bundle. A replacement resets
review to PENDING and refreshes provider approvals. There is no partial-save mode.
The legacy documentKey in the response is the first submitted file for compatibility;
new clients must render every part using parts and partDefinitions.

The server checks key ownership and actual S3 object content type/size; it does not
trust client file metadata. View URLs still use the existing authorized private-file
view endpoint for each key. The API never returns permanent public document URLs.

## Review and compatibility

GET `/api/v1/providers/me/documents`, admin GET
`/api/v1/admin/provider-documents?status=PENDING`, and review responses include
parts and the submission's partDefinitions snapshot. The assignment approval view
also includes these in each DOCUMENT row's metadata. Review the whole bundle using
PATCH `/api/v1/admin/provider-documents/{documentId}/review` as before; one approval
covers all submitted parts. Rejection/replacement applies to the bundle.

Existing documents retain their original schema/evidence and approval, including
when an admin later changes part requirements. Configuration changes apply to the
next submission, not retroactively. Existing legacy uploads return empty parts and
partDefinitions; render their documentKey. Existing legacy types with no parts still
accept the original single documentKey body. Multipart types reject that legacy
body rather than silently accepting one image in place of required sides.

## Errors

400: unknown/duplicate part, missing required part, zero files, too many files,
foreign or malformed key, repeated object key, disallowed MIME type, oversized or
missing S3 object, conflicting documentKey with parts, invalid date/configuration.
404: unknown/inactive document type. Existing 409 approved-document deletion rule
is unchanged. Failed submission validation performs no document or review writes.

## Rollout

Apply the additive columns in DOCUMENT_PARTS_SCHEMA.sql before deploying this build
(skip columns already present). No data backfill is needed for existing single-file
types/documents. New uploads persist a configuration snapshot with their parts.
Tests use mocked S3 metadata; production S3 permissions must allow HeadObject.

The existing separately configured “Driving licence - Front” / “Back” and NIC types
remain intact with all uploads/reviews. They are not guessed or automatically
combined by name. A new combined type can be configured immediately. Consolidating
old separate types and their service requirements needs an explicit ID/part mapping
and a separate data migration; this change does not claim to have performed it.
