# Customer Profile page: overview, lists and action APIs

| Endpoint | State |
|---|---|
| `GET /api/v1/customers/me/profile-overview` | **Live** on `jobhub.pentarixlabs.com` since 2026-11-17 (checked against a test customer). |
| `GET /api/v1/customers/me/booking-requests` (list) | Built 2026-11-17, **not deployed yet**. |
| `POST /api/v1/customers/me/contacts/{email,phone}/{otp,verify}` | Built 2026-11-17, **not deployed yet**. |

All endpoints need a `CUSTOMER` access token (`Authorization: Bearer …`) and only ever return or change the
signed-in customer's own data. Editing the profile and uploading documents use the existing endpoints in
[§6](#6-actions-used-from-the-profile-page).

**Contents:**

1. [Profile overview](#1-profile-overview)
2. [Status values and counts](#2-status-values-and-counts)
3. [Spending](#3-spending)
4. [Opening an item's details](#4-opening-an-items-details)
5. ["View all": booking-request list](#5-view-all-booking-request-list)
6. [Actions used from the Profile page](#6-actions-used-from-the-profile-page) (edit profile, photo, documents)
7. [Change or verify email and phone](#7-change-or-verify-email-and-phone)
8. [Errors](#8-errors)

Date-times without a zone (`2026-11-20T09:00:00`) are market-local, like every other booking field. Amounts are
JSON numbers in the currency named next to them.

---

## 1. Profile overview

```http
GET /api/v1/customers/me/profile-overview?recentLimit=5
```

| Query | Default | Allowed | Meaning |
|---|---|---|---|
| `recentLimit` | `5` | integer **1–20** | Maximum number of items in `pendingServices.items` and in `serviceHistory.items`. Anything else returns `400`. |

### Full response `200 OK`

```json
{
  "profile": {
    "customerId": 7,
    "firstName": "Madu",
    "lastName": "Perera",
    "displayName": "Madu Perera",
    "profilePhoto": "https://cdn.example.com/customers/40/images/photo.jpg",
    "status": "ACTIVE",
    "profileCompleted": true
  },
  "contacts": {
    "email": "madu@example.com",
    "emailVerified": true,
    "phoneNumber": "+94771234567",
    "phoneVerified": false
  },
  "statistics": {
    "pendingCount": 3,
    "awaitingProviderCount": 1,
    "activeCount": 2,
    "completedCount": 12,
    "cancelledCount": 2,
    "spending": [
      {"currencyCode": "LKR", "paidAmount": 84000.00, "refundedAmount": 5000.00, "totalSpent": 79000.00,
       "reservedAmount": 1500.00, "paidBookingCount": 13}
    ],
    "testSpending": [
      {"currencyCode": "LKR", "paidAmount": 9999.00, "refundedAmount": 0, "totalSpent": 9999.00,
       "reservedAmount": 0, "paidBookingCount": 1}
    ],
    "excludedPaymentCount": 1
  },
  "documents": [
    {
      "requirementId": 7,
      "code": "NIC",
      "name": "National identity card",
      "fieldType": "DOCUMENT_UPLOAD",
      "required": true,
      "blocksBooking": true,
      "services": [
        {"scope": "GLOBAL", "serviceTypeId": null, "serviceName": null, "optionId": null, "optionName": null},
        {"scope": "OPTION", "serviceTypeId": 1, "serviceName": "Caregiver", "optionId": 2, "optionName": "Home Care"}
      ],
      "status": "REJECTED",
      "submissionId": 55,
      "reviewedAt": "2026-11-10T12:00:00",
      "expiresOn": "2027-01-01",
      "rejectionReason": "Photo is blurry"
    },
    {
      "requirementId": 8,
      "code": "POLICE_REPORT",
      "name": "Police report",
      "fieldType": "FILE_UPLOAD",
      "required": false,
      "blocksBooking": false,
      "services": [{"scope": "GLOBAL", "serviceTypeId": null, "serviceName": null, "optionId": null, "optionName": null}],
      "status": "MISSING",
      "submissionId": null,
      "reviewedAt": null,
      "expiresOn": null,
      "rejectionReason": null
    }
  ],
  "pendingServices": {
    "items": [
      {
        "kind": "REQUEST",
        "requestId": 99,
        "jobId": null,
        "serviceTypeId": 1,
        "serviceName": "Caregiver",
        "optionId": 1,
        "optionName": "Hospital Care",
        "startAt": "2026-11-20T09:00:00",
        "endAt": "2026-11-20T11:00:00",
        "status": "MATCHING",
        "paymentStatus": "NOT_STARTED",
        "createdAt": "2026-11-17T10:30:00.123456"
      },
      {
        "kind": "BOOKING",
        "requestId": 91,
        "jobId": 125,
        "serviceTypeId": 1,
        "serviceName": "Caregiver",
        "optionId": 1,
        "optionName": "Hospital Care",
        "startAt": "2026-11-19T08:00:00",
        "endAt": "2026-11-19T10:00:00",
        "status": "CONFIRMED",
        "paymentStatus": "PAID",
        "createdAt": "2026-11-16T09:00:00.5"
      }
    ],
    "limit": 5,
    "hasMore": false,
    "totalCount": 2
  },
  "serviceHistory": {
    "items": [
      {
        "jobId": 110,
        "requestId": 80,
        "serviceTypeId": 1,
        "serviceName": "Caregiver",
        "optionId": 1,
        "optionName": "Hospital Care",
        "startAt": "2026-11-05T08:00:00",
        "endAt": "2026-11-05T10:12:00",
        "status": "COMPLETED",
        "amount": 7000.00,
        "currencyCode": "LKR",
        "paymentStatus": "SETTLED"
      }
    ],
    "limit": 5,
    "hasMore": true,
    "totalCount": 14
  }
}
```

`createdAt` can carry fractional seconds (`…:00.123456`); parse it as an ISO local date-time.

### Customer without a saved profile

Still `200 OK`. Send them to profile completion (`PUT /api/v1/customers/me/profile`).

```json
{
  "profile": null,
  "contacts": {"email": "madu@example.com", "emailVerified": true, "phoneNumber": null, "phoneVerified": false},
  "statistics": {"pendingCount": 0, "awaitingProviderCount": 0, "activeCount": 0, "completedCount": 0,
                 "cancelledCount": 0, "spending": [], "testSpending": [], "excludedPaymentCount": 0},
  "documents": [],
  "pendingServices": {"items": [], "limit": 5, "hasMore": false, "totalCount": 0},
  "serviceHistory": {"items": [], "limit": 5, "hasMore": false, "totalCount": 0}
}
```

### Nullable fields

Fields not listed here are never `null`. Lists are never `null` (empty `[]` instead). Numbers in counts are
never `null` (`0` instead).

| Field | `null` when |
|---|---|
| `profile` | The customer has not saved a profile yet. |
| `profile.lastName`, `profile.profilePhoto` | Not set. `displayName` is then the parts that exist, trimmed. |
| `contacts.email` | Phone-only account. |
| `contacts.phoneNumber` | Email/Google account with no phone. |
| `documents[].submissionId`, `reviewedAt`, `rejectionReason`, `expiresOn` | Not submitted (`status: "MISSING"`), not reviewed yet, not rejected, or no expiry. |
| `documents[].services[].serviceTypeId/serviceName/optionId/optionName` | `scope: "GLOBAL"` (all ids/names null), `scope: "SERVICE"` (option fields null). |
| `pendingServices.items[].requestId` | Old direct booking that did not come from a booking request. |
| `pendingServices.items[].jobId` | `kind: "REQUEST"`: no provider has accepted yet. |
| `…serviceName`, `…optionName` | The service/option was deleted. Show a generic label. |
| `…startAt`, `…endAt` | Not stored on the request. Route bookings have `startAt` (pickup) but usually no `endAt`. |
| `…paymentStatus` | Old record without a payment state. |
| `serviceHistory.items[].amount`, `currencyCode`, `requestId` | Not recorded on an old booking. |

`emailVerified` and `phoneVerified` are the stored flags. They are `false` until the address was verified
with a code; they are never guessed.

### Documents

Listed: every active upload requirement (`IMAGE_UPLOAD`, `DOCUMENT_UPLOAD`, `FILE_UPLOAD`) that applies to at
least one active service or option (`GLOBAL` included), plus any the customer has already submitted. One item per
requirement, `MISSING` until uploaded. Items also carry `statusReason`, `actionRequired`, `upload` rules and
`fileKeys`; see [CUSTOMER_DOCUMENTS_API.md](CUSTOMER_DOCUMENTS_API.md), which also has
`GET /customers/me/documents` and `GET /customers/me/documents/{requirementId}`.

| `status` | Meaning | Suggested action |
|---|---|---|
| `MISSING` | Never submitted. | Upload |
| `PENDING` | Waiting for admin review. | None (show "Under review") |
| `APPROVED` | Accepted. | None, or replace |
| `REJECTED` | See `rejectionReason`. | Resubmit |
| `EXPIRED` | `expiresOn` has passed. | Resubmit |

The ID you need for upload/resubmit is **`requirementId`**. `submissionId` is only for display/support. See
[§6](#6-actions-used-from-the-profile-page) for the calls.

---

## 2. Status values and counts

### Status values

`pendingServices.items[].status` is the **booking request** status when `kind` is `REQUEST`, and the
**booking (job)** status when `kind` is `BOOKING`. `serviceHistory.items[].status` is always the booking status.

Booking (job) statuses:

| Status | Meaning | Where it appears |
|---|---|---|
| `PENDING` | Created, payment not finished yet. | pending (active) |
| `AWAITING_PAYMENT` | Old direct-booking flow: waiting for payment. | pending (active) |
| `CONFIRMED` | Paid, provider assigned, not started. | pending (active) |
| `NO_SHOW_REVIEW` | Provider reported the customer did not show; admin is reviewing ([no-show complaints](CUSTOMER_NO_SHOW_API.md)). | pending (active) |
| `IN_PROGRESS` | Provider started the service. | pending (active) |
| `AWAITING_COMPLETION_CONFIRMATION` | Provider finished; waiting for the customer to confirm. | pending (active) |
| `COMPLETED` | Finished. | history |
| `CANCELLED` | Cancelled by anyone, or the request behind it expired/failed. | history |

Booking request statuses (also used by the [list](#5-view-all-booking-request-list)):

| Status | Meaning | Active? |
|---|---|---|
| `MATCHING` | Offering the request to providers. | yes |
| `AWAITING_CUSTOMER_CONFIRMATION` | A provider accepted; customer must confirm and pay. | yes |
| `PAYMENT_PENDING` | Checkout started. | yes |
| `PAYMENT_RECONCILIATION` | Payment result being checked with the gateway. | yes |
| `PAYMENT_REVIEW` | Payment needs manual review by operations. | yes |
| `CONFIRMED`, `NO_SHOW_REVIEW`, `IN_PROGRESS`, `AWAITING_COMPLETION_CONFIRMATION` | Same as the booking statuses above. | yes |
| `COMPLETED` | Finished. | no |
| `CANCELLED` | Cancelled. | no |
| `UNMATCHED` | No provider accepted. | no |
| `EXPIRED` | Customer did not confirm or pay in time. | no |
| `PAYMENT_FAILED` | Payment failed and was closed. | no |

Treat any status you do not recognise as active and show it as-is; new values may be added.

### How the counts are calculated

Counts cover **all** of the customer's records, not only the recent items shown.

| Field | Calculation |
|---|---|
| `awaitingProviderCount` | Booking requests in `MATCHING` that have no booking (`jobId`) yet. |
| `activeCount` | Bookings whose status is **not** `COMPLETED` or `CANCELLED`. |
| `pendingCount` | `awaitingProviderCount + activeCount`. Same as `pendingServices.totalCount`. |
| `completedCount` | Bookings in `COMPLETED`. |
| `cancelledCount` | Bookings in `CANCELLED`. |

`completedCount + cancelledCount` equals `serviceHistory.totalCount`. Requests that ended without a booking
(`UNMATCHED`, `EXPIRED` before a provider accepted) are in no count.

---

## 3. Spending

`statistics.spending` and `statistics.testSpending` are **arrays with one object per currency**. Amounts are
never converted between currencies. An empty array means nothing was paid.

| Field | Type | Meaning |
|---|---|---|
| `currencyCode` | string | ISO currency, for example `"LKR"`. |
| `paidAmount` | number | Sum of booked amounts that were charged (including ones later refunded). |
| `refundedAmount` | number | Sum of refunds from cancellation records. |
| `totalSpent` | number | `paidAmount − refundedAmount`. **Show this as "Total spent".** |
| `reservedAmount` | number | Wallet money held for unfinished bookings. Not spent yet; may still be refunded. |
| `paidBookingCount` | integer | Bookings counted in `paidAmount`. |

- **`spending`**: real money only (`PAYHERE` payments, including wallets funded through PayHere). Production
  apps show only this.
- **`testSpending`**: `MOCK` and `PAYHERE_SANDBOX` payments. Show it only in test builds, or not at all.
- **`excludedPaymentCount`**: payments left out of every total because the result is not final (`PAID_REVIEW`,
  `REFUND_REVIEW`, `CHARGEDBACK`), the payment source is unknown (old direct bookings), or a refund has no
  cancellation record. If it is above 0 you may show "Some payments are being processed".

| Payment state | Counted in |
|---|---|
| `PAID`, `SETTLED` | `paidAmount` |
| `REFUNDED`, `PARTIALLY_REFUNDED`, `CANCELLATION_SETTLED` | `paidAmount`, and the recorded refund in `refundedAmount` |
| `RESERVED` | `reservedAmount` only |
| `PAID_REVIEW`, `REFUND_REVIEW`, `CHARGEDBACK` | none; `excludedPaymentCount` |
| `NOT_STARTED`, `PENDING`, `FAILED`, `RELEASED`, `UNPAID_REVIEWED`, `null` | ignored (nothing was taken) |

Wallet top-ups and withdrawals are not spending; use the wallet APIs for balances.

---

## 4. Opening an item's details

| Item | Has | Open with |
|---|---|---|
| Pending, `kind: "REQUEST"` | `requestId` | `GET /api/v1/customers/me/booking-requests/{requestId}`: status, current offer, amount, payment. |
| Pending, `kind: "BOOKING"` | `jobId` (+ `requestId` if it came from a request) | `GET /api/v1/customers/me/booking-requests/{requestId}` for status and payment, plus the booking APIs below. |
| History item | `jobId` (+ `requestId`) | Same as a booking. |

Booking APIs keyed by `jobId` (all under `/api/v1/customers/me`):

| Need | Endpoint |
|---|---|
| Assigned provider (name, photo, rating, phone when allowed) | `GET /bookings/{jobId}/providers` ([contract](CUSTOMER_BOOKING_PROVIDERS_API.md)) |
| Start/end codes | `GET /bookings/{jobId}/verification-codes` |
| Live tracking | `GET /bookings/{jobId}/tracking` |
| Cancellation preview / cancel / result | `GET /bookings/{jobId}/cancellation-preview`, `POST /bookings/{jobId}/cancel`, `GET /bookings/{jobId}/cancellation` |
| Rating | `GET`/`POST /bookings/{jobId}/ratings` |
| Confirm completion | `POST /bookings/{jobId}/confirm-completion` |

There is no single `GET /bookings/{jobId}` endpoint. When `requestId` is `null` (old direct booking), take the
booking fields from `GET /api/v1/customers/me/bookings` (all bookings, unpaginated).

---

## 5. "View all": booking-request list

> Not deployed yet.

```http
GET /api/v1/customers/me/booking-requests?status=MATCHING,CONFIRMED&page=0&size=20
```

| Query | Default | Allowed |
|---|---|---|
| `status` | all | One or more [request statuses](#status-values), comma-separated or repeated (`status=A&status=B`). Case-insensitive. Unknown value → `400`. |
| `page` | `0` | 0 or more (zero-based). |
| `size` | `20` | 1–50. |

Newest first. Every booking made through the booking-request flow is in this list (with `jobId` once a provider
accepted), so it serves both "View all pending" and "View all history":

- Pending: `status=MATCHING,AWAITING_CUSTOMER_CONFIRMATION,PAYMENT_PENDING,PAYMENT_RECONCILIATION,PAYMENT_REVIEW,CONFIRMED,NO_SHOW_REVIEW,IN_PROGRESS,AWAITING_COMPLETION_CONFIRMATION`
- History: `status=COMPLETED,CANCELLED`
- Also show requests that never became bookings: add `UNMATCHED,EXPIRED,PAYMENT_FAILED`.

`200 OK`:

```json
{
  "items": [
    {
      "requestId": 91,
      "jobId": 125,
      "status": "CONFIRMED",
      "paymentStatus": "PAID",
      "paymentFundingMode": "PAYHERE",
      "serviceTypeId": 1,
      "serviceName": "Caregiver",
      "optionId": 1,
      "optionName": "Hospital Care",
      "startAt": "2026-11-19T08:00:00",
      "endAt": "2026-11-19T10:00:00",
      "amount": 7000.00,
      "currencyCode": "LKR",
      "createdAt": "2026-11-16T09:00:00.5"
    }
  ],
  "page": 0,
  "size": 20,
  "totalCount": 1,
  "hasMore": false
}
```

Load the next page with `page + 1` while `hasMore` is `true`. Nullable: `jobId` (no provider yet),
`paymentStatus`, `paymentFundingMode`, `serviceName`/`optionName` (deleted), `startAt`/`endAt`, `amount`/`currencyCode`.
`paymentFundingMode` is `PAYHERE` for real money, `MOCK`/`PAYHERE_SANDBOX` for test payments.

Old direct bookings (no `requestId`) are not in this list; they are only in `GET /customers/me/bookings`.

---

## 6. Actions used from the Profile page

These already exist and are unchanged. Full details are in [CUSTOMER_APP_FRONTEND_API.md](CUSTOMER_APP_FRONTEND_API.md)
in the sections named below.

| Action | Endpoint | Reference |
|---|---|---|
| Edit profile (name, address, photo URL) | `PUT /api/v1/customers/me/profile` | §14 |
| Upload profile photo | `POST /api/v1/uploads/images` (multipart `file`), then send the returned `url` as `profilePhoto` in the PUT above | §22 |
| Upload a document file | `POST /api/v1/uploads/documents` (multipart `file`) returns a private `key` | §21 |
| Submit or resubmit a document | `PUT /api/v1/customers/me/requirement-submissions` with `requirementId` and `value.files[].key` | §24 |
| View an uploaded private document | `GET /api/v1/uploads/view-url?key=…` (10-minute signed URL) | §23 |
| Change or verify email / phone | `POST /api/v1/customers/me/contacts/…` | [§7](#7-change-or-verify-email-and-phone) |

### Edit profile

```http
PUT /api/v1/customers/me/profile
Content-Type: application/json
```

```json
{
  "firstName": "Madu",
  "lastName": "Perera",
  "profilePhoto": "https://cdn.example.com/customers/40/images/photo.jpg",
  "addressLine1": "10 Main Street",
  "addressLine2": null,
  "city": "Colombo",
  "district": "Colombo",
  "province": "Western",
  "postalCode": "00100",
  "country": "Sri Lanka"
}
```

`200 OK` returns the saved profile. This is a full replacement, so send every field; optional fields left out
are cleared. Required: `firstName`, `lastName`, `addressLine1`, `city`, `country`.

### Upload or resubmit a document

Use the document's `requirementId` from `documents[]` (works for `MISSING`, `REJECTED` and `EXPIRED`).

1. Upload the file:

   ```http
   POST /api/v1/uploads/documents
   Content-Type: multipart/form-data   (part name: file)
   ```

   `200 OK` → `{"key": "customers/40/documents/<uuid>.jpg", ...}`. Use this endpoint for `IMAGE_UPLOAD`
   requirements too: a key from `POST /api/v1/uploads/images` is refused when submitted.

2. Submit it:

   ```http
   PUT /api/v1/customers/me/requirement-submissions
   Content-Type: application/json
   ```

   ```json
   {
     "submissions": [
       {"requirementId": 7, "value": {"files": [{"key": "customers/40/documents/<uuid>.jpg"}]}, "expiresOn": "2030-01-01"}
     ]
   }
   ```

`200 OK` returns the saved submissions; a document that needs review goes back to `PENDING`. Refresh the
overview afterwards. `expiresOn` is optional and cannot be in the past. File count, type and size follow the
requirement's rules (`400` on violation).

---

## 7. Change or verify email and phone

> Not deployed yet.

Two steps: request a 6-digit code to the **new** address, then verify it. Nothing on the account changes until
step 2 succeeds, so an unverified address is never saved. The same calls verify the **current** address when
`emailVerified` / `phoneVerified` is `false` (send the current value).

| Step | Email | Phone (SMS) |
|---|---|---|
| 1. Send code | `POST /api/v1/customers/me/contacts/email/otp` | `POST /api/v1/customers/me/contacts/phone/otp` |
| 2. Verify and save | `POST /api/v1/customers/me/contacts/email/verify` | `POST /api/v1/customers/me/contacts/phone/verify` |

### Step 1: send code

```json
{"email": "new@example.com"}
```

```json
{"phoneNumber": "+94771234567"}
```

Phone numbers use E.164 (`+` and country code). Emails are saved in lower case.

`200 OK`:

```json
{"destination": "new@example.com", "expiresIn": 300, "resendAfterSeconds": 60}
```

`expiresIn`: seconds the code stays valid. `resendAfterSeconds`: wait at least this long before "Resend".

### Step 2: verify

```json
{"email": "new@example.com", "otp": "123456", "currentPassword": "••••••••"}
```

```json
{"phoneNumber": "+94771234567", "otp": "123456", "currentPassword": "••••••••"}
```

`currentPassword` is **required for accounts that sign in with a password**, and ignored for phone-OTP and
Google accounts (send `null` or leave it out). If you do not know the account type, show the password field
and handle `"Current password is required"`.

`200 OK` returns the updated contacts (same shape as `contacts` in the overview):

```json
{"email": "new@example.com", "emailVerified": true, "phoneNumber": "+94771234567", "phoneVerified": true}
```

After an email change, the customer signs in with the new email. After a phone change, phone-OTP sign-in uses
the new number. The current session stays valid.

### Rules and errors for this flow

| Status | `detail` | When / what to do |
|---|---|---|
| `400` | `This email is already verified` / `This phone number is already verified` | Step 1 with the current, already verified address. |
| `400` | `Verification code is invalid or expired` | Wrong, expired or used code, or too many attempts. Let the user retry or request a new code. |
| `400` | `Current password is required` / `Current password is incorrect` | Password accounts only. A wrong password uses up one code attempt. |
| `400` | field validation (`Email must be valid`, `Phone number must use international E.164 format`, `OTP must contain six digits`) | Fix the input. |
| `409` | `Email is already registered` / `Phone number is already registered` | The address belongs to another account (checked at both steps). |
| `429` | `Please wait before requesting another verification code` | Resend too soon. Body has `retryAfterSeconds`; the `Retry-After` header has the same value. |

A wrong code returns `400`, **not** `401`, so do not treat it as a sign-out. Each code allows a limited number
of attempts and expires after `expiresIn` seconds.

---

## 8. Errors

All errors use this body (RFC 9457 problem details):

```json
{
  "type": "about:blank",
  "title": "Invalid request value",
  "status": 400,
  "detail": "recentLimit must be between 1 and 20",
  "instance": "/api/v1/customers/me/profile-overview"
}
```

Show `detail` only for the cases in the tables; use your own text for the rest.

| Status | Endpoint | When |
|---|---|---|
| `400` | overview | `recentLimit` outside 1–20 (`recentLimit must be between 1 and 20`) or not a number. |
| `400` | request list | `page` < 0, `size` outside 1–50, or `Unknown status: …`. |
| `400` / `409` / `429` | contacts | See [§7](#rules-and-errors-for-this-flow). |
| `401` | all | Missing, invalid or expired access token. Refresh the token, then retry. |
| `403` | all | The token is not a `CUSTOMER` account. |
| `404` | overview, contacts | The account no longer exists (`User not found`). |
