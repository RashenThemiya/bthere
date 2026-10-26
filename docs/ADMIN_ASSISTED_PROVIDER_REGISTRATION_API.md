# Admin-assisted provider service registration

Implemented in the backend working tree; deploy before using these endpoints.
All new routes require SUPER_ADMIN or VERIFICATION_ADMIN. Use the admin's bearer
token throughout. The provider does not need to log in, request the service or
upload the evidence themselves. This flow targets an existing provider profile
with a user account; it does not create accounts or bypass account setup.

## Dashboard workflow

1. Select the existing provider and load registration-context. Use providerId
   (service_provider ID), not userId. The server resolves the account internally.
2. Select a service/option and load its setup/onboarding schema. Collect configured
   requirements, skills, availability and service locations from the provider.
3. Upload files received via WhatsApp/phone/in person using the admin provider
   upload endpoint. Preview them with the existing admin `/uploads/view-url`.
4. Submit the combined registration. This creates the service registration on
   behalf of the provider, using the same validation as self-registration.
5. Load the existing admin approval detail, inspect pending evidence and explicitly
   approve selected review items with bulk-review. No provider-side request is
   needed. Re-read completion; only show Approved when verificationStatus says so.

The dashboard can present these as one wizard with Upload, Review and Approve
steps. Uploading alone does not create a document submission or approve it. Admin
status ACTIVE alone is not verification approval. Missing requirements remain
PENDING; emergency override is a separate existing process, not part of this flow.
No admin dashboard source is included in this backend repository.

## New endpoints

Base: `/api/v1/admin/providers/{providerId}`

| Method | Suffix | Response |
|---|---|---|
| GET | `/registration-context` | 200 context |
| POST | `/uploads/documents?source=WHATSAPP` | 201 FileUploadResponse |
| POST | `/uploads/images?source=WHATSAPP` | 201 FileUploadResponse |
| POST | `/service-registrations` | 201 ProviderServiceRegistrationResponse |
| POST | `/documents` | 201 ProviderDocumentResponse |
| PUT | `/service-registrations/{assignmentId}/requirements` | 200 ProviderCustomSubmissionResponse[] |
| POST | `/service-registrations/{assignmentId}/certificates` | 201 ProfessionalCertificateResponse |
| POST | `/service-registrations/{assignmentId}/education` | 201 EducationQualificationResponse |
| PUT | `/service-registrations/{assignmentId}/options` | 200 ProviderServiceOptionResponse[] |
| PUT | `/service-registrations/{assignmentId}/languages` | 200 string[] |
| PUT | `/service-registrations/{assignmentId}/skills` | 200 ProviderSkillResponse[] |
| PUT | `/service-registrations/{assignmentId}/availability` | 200 ProviderScheduleResponse |
| PUT | `/service-registrations/{assignmentId}/options/{optionId}/service-areas?deliveryMode=PROVIDER_TO_CUSTOMER` | 200 ProviderServiceAreaResponse[] |

IDs must be positive. Missing provider or assignment belonging to another provider
returns 404. Invalid definitions/owned keys return 400. Duplicate registration of
the same service returns 409; use the individual update endpoints instead.
Existing shared-option rules still apply. Do not create duplicate registrations
of the same shared option through different navigation services.

Sources: WHATSAPP, PHONE, IN_PERSON, EMAIL, ADMIN_ASSISTED. Registration/document
JSON requires source. Multipart uploads default to ADMIN_ASSISTED when omitted.
Source and optional note (maximum 1000 characters) are saved in the admin audit
trail with actor ID and target provider/assignment/document. No WhatsApp connector
is involved; the admin downloads the received evidence and uploads the file.

## Load context and definitions

```http
GET /api/v1/admin/providers/15/registration-context
```

Response properties: `providerId`, `assignments` (ProviderTypeAssignmentResponse[]),
`completion` (ProviderOnboardingResponse), `documents` (ProviderDocumentResponse[]).
An unregistered existing provider can have empty assignments/documents. The context
uses existing completion logic; it does not create or activate anything.

Use existing service definitions:

```http
GET /api/v1/provider-types/3/setup
GET /api/v1/provider-types/3/onboarding-schema
GET /api/v1/admin/service-options/12/custom-fields
```

Use the returned configuration/owner IDs and selected-option applicability. Do not
copy dynamic fields for a linked service. Profile-backed fields remain read-only;
correct the underlying profile rather than submitting duplicate dynamic answers.

## Upload WhatsApp evidence

```http
POST /api/v1/admin/providers/15/uploads/documents?source=WHATSAPP
Content-Type: multipart/form-data
```

Multipart part: `file` (binary). PDF/JPG/PNG/WebP, maximum 100 MB, using the existing
private-document upload validation. Images endpoint accepts JPG/PNG/WebP, maximum
10 MB, using the existing provider-image validation; it does not allow admin SVG
assets in a provider's gallery. Files are stored under the TARGET provider account,
not the admin account. Example: providerId 15 resolves internally to userId 25.

```json
{
  "key": "providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf",
  "url": "s3://example-bucket/providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf",
  "contentType": "application/pdf",
  "size": 125000
}
```

Keep `key` for registration/submission. The s3:// URL is not a browser preview.
Existing admin `GET /api/v1/uploads/view-url?key=...` returns a short-lived preview
URL. Uploads are separate from the database registration transaction; validation
failure can leave an uploaded file unreferenced. Reuse the successful upload key
when correcting/retrying the registration rather than uploading again.

## Register on behalf of the provider

```http
POST /api/v1/admin/providers/15/service-registrations
Content-Type: application/json
```

Illustrative full request; replace all IDs and configured requirements. Empty
serviceAreas is appropriate only when the selected option does not require them.
Include the existing registration serviceAreas groups for location-based services.
Document types with configured parts require `parts` instead of `documentKey`.

```json
{
  "source": "WHATSAPP",
  "note": "Provider asked staff to register; evidence received via WhatsApp.",
  "registration": {
    "service": {"providerTypeId": 3, "experienceYears": 5},
    "options": {"optionIds": [12]},
    "languages": {"languages": ["SINHALA", "ENGLISH"]},
    "skills": {"skillIds": []},
    "documents": [
      {
        "documentTypeId": 4,
        "documentName": "Qualification document",
        "documentNumber": "CERT-123",
        "documentKey": "providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf",
        "issuedDate": null,
        "expiryDate": null
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
    "serviceAreas": [],
    "requirements": {"submissions": []}
  }
}
```

Returns the SAME response contract as provider self-registration: `assignment`
(ProviderTypeAssignmentResponse, including assignmentId and verificationStatus)
and `completion` (ProviderOnboardingResponse, including missing/completion checks).
Registration is atomic for database writes; a validation failure rolls it back.
Files are not auto-approved just because an admin submitted them. Normal configured
auto-approval behavior still applies to non-reviewable fields/qualifications.

## Add documents or complete an existing registration

Do not POST another registration for an already registered service. Submit new
provider-wide documents with:

```http
POST /api/v1/admin/providers/15/documents
```

```json
{
  "source": "WHATSAPP",
  "note": "Replacement document received and checked by staff.",
  "document": {
    "documentTypeId": 4,
    "documentName": "Qualification document",
    "documentNumber": "CERT-123",
    "documentKey": "providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf",
    "issuedDate": null,
    "expiryDate": null
  }
}
```

Response is the normal document record, initially PENDING. Standard documents are
provider-wide and can affect multiple registered services. Reuse existing document
IDs/approvals where applicable. Do not submit the same document twice during the
same workflow. This endpoint adds a record, not an upsert.

Requirements PUT body is `{"submissions":[{"fieldId":22,"value":{"files":[
{"key":"providers/25/documents/550e8400-e29b-41d4-a716-446655440000.pdf"}]}}]}`.
Use actual field types and values. Existing approval/reuse/ownership rules apply.

Other completion PUT bodies use their existing provider contracts:

- options: `{"optionIds":[12]}` (full replacement)
- languages: `{"languages":["SINHALA","ENGLISH"]}` (full replacement)
- skills: `{"skillIds":[10]}` (full replacement)
- availability: `{"availableAnyDay":true,"availableAnyTime":true,"weeklySlots":[]}`
- service-areas: ProviderServiceAreaRequest[] (replaces only the specified option/mode)

Certificates and education use the existing ProfessionalCertificateRequest and
EducationQualificationRequest bodies, with target-provider upload keys. Every
assignment-scoped edit verifies that assignmentId belongs to providerId. Each
successful edit records the admin actor in the audit trail.

## Review and approve directly from the dashboard

Use the EXISTING APIs with the same admin token:

```http
GET /api/v1/admin/provider-approvals/15
POST /api/v1/admin/provider-approvals/bulk-review
```

The detail response has `services[].assignmentId`, checks, and reviewable items.
Use the returned submission/option/document record IDs, not field definition IDs.
Only select items the admin actually reviewed. Example with illustrative IDs:

```json
{
  "assignmentId": 14,
  "reviews": [
    {"type": "DOCUMENT", "id": 44, "status": "APPROVED", "rejectionReason": null},
    {"type": "DYNAMIC_REQUIREMENT", "id": 71, "status": "APPROVED", "rejectionReason": null},
    {"type": "SERVICE_OPTION", "id": 81, "status": "APPROVED", "rejectionReason": null}
  ]
}
```

Bulk review is atomic for 1-100 eligible pending items belonging to the selected
assignment. Reused evidence cannot be bulk-reviewed as a new target submission;
review the source where appropriate. For an uploaded standard document not listed
as applicable in that service detail, the existing individual endpoint remains:

```http
PATCH /api/v1/admin/provider-documents/44/review
```

```json
{"status":"APPROVED","rejectionReason":null}
```

Both paths retain reviewer attribution and refresh provider service approval.
Re-fetch registration-context/approval detail after review. If mandatory documents,
options, skills, custom requirements, availability or locations remain incomplete,
the service stays PENDING. The UI must show the remaining checks instead of
claiming that review alone made the provider bookable. Existing provider/admin
ACTIVE status, market and service availability rules still apply.

No schema migration is required. Existing provider self-registration endpoints,
file access checks, review permissions and approval rules remain available.


## Complete request and response examples

[Complete request/response reference](ADMIN_ASSISTED_PROVIDER_API_EXAMPLES.md) covers all 13 new APIs and 11 existing APIs, including full nested response examples, multipart parameters, roles and error handling.
