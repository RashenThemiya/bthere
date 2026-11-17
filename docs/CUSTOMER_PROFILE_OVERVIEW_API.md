# Customer Profile page: overview API and action APIs

Implemented locally on 2026-11-17; deployment is not verified.

One read call fills the whole Profile page. Editing, uploading and verification keep their existing
endpoints (listed in [Actions](#actions-used-from-the-profile-page)); nothing was duplicated.

## 1. Profile overview

```http
GET /api/v1/customers/me/profile-overview?recentLimit=5
Authorization: Bearer <customer-access-token>
```

| Query | Default | Rules |
|---|---|---|
| `recentLimit` | `5` | 1–20. Maximum items in `pendingServices.items` and `serviceHistory.items`. |

Only the signed-in customer's own data is returned. Requires a `CUSTOMER` token.

### Response `200 OK`

```json
{
  "profile": {
    "customerId": 7,
    "firstName": "Madu",
    "lastName": "Perera",
    "displayName": "Madu Perera",
    "profilePhoto": "https://cdn.example.com/providers/25/images/photo.jpg",
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
      {
        "currencyCode": "LKR",
        "paidAmount": 84000.00,
        "refundedAmount": 5000.00,
        "totalSpent": 79000.00,
        "reservedAmount": 1500.00,
        "paidBookingCount": 13
      }
    ],
    "testSpending": [
      {
        "currencyCode": "LKR",
        "paidAmount": 9999.00,
        "refundedAmount": 0,
        "totalSpent": 9999.00,
        "reservedAmount": 0,
        "paidBookingCount": 1
      }
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
        "createdAt": "2026-11-17T10:30:00"
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
        "createdAt": "2026-11-16T09:00:00"
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

### No profile saved yet

Still `200 OK`. `profile` is `null` and `contacts` is filled. Everything else is empty, because bookings and
documents belong to a saved profile. Send the customer to profile completion (`PUT /api/v1/customers/me/profile`).

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

### Section rules

**Profile and contacts.** These are read straight from the saved profile and account. `emailVerified` and
`phoneVerified` are the stored flags, not guesses. Values never set are `null`.

**Counts.** Counts cover all of the customer's bookings, not only the recent items.

| Field | Counts |
|---|---|
| `awaitingProviderCount` | Booking requests in `MATCHING`, still looking for a provider (no job yet). |
| `activeCount` | Bookings not yet `COMPLETED` or `CANCELLED`: unpaid, confirmed, in progress, awaiting completion confirmation, or under payment review. |
| `pendingCount` | `awaitingProviderCount + activeCount`. |
| `completedCount` / `cancelledCount` | Bookings in `COMPLETED` / `CANCELLED`. |

**Spending.** Amounts are grouped by currency and never converted. Each booking counts at its booked amount,
which is the amount charged.

| Payment state | Where it is counted |
|---|---|
| `PAID`, `SETTLED` | `paidAmount` |
| `REFUNDED`, `PARTIALLY_REFUNDED`, `CANCELLATION_SETTLED` (cancelled after payment) | `paidAmount`, and the cancellation record's refund in `refundedAmount` |
| `RESERVED` (wallet money held for an unfinished booking) | `reservedAmount` only. Not spent yet; it may still be refunded. |
| `PAID_REVIEW`, `REFUND_REVIEW`, `CHARGEDBACK` (outcome still open) | Not in any total; counted in `excludedPaymentCount` |
| `NOT_STARTED`, `PENDING`, `FAILED`, `RELEASED`, `UNPAID_REVIEWED`, null (nothing taken) | Ignored |

- `totalSpent = paidAmount − refundedAmount`.
- **Real vs test money:** `spending` contains only real `PAYHERE` payments, including wallets funded by PayHere.
  `MOCK` and `PAYHERE_SANDBOX` payments go to `testSpending`. Production apps should show only `spending`.
- **Not guessed:** a booking with no known funding source (for example one made with the older direct-booking
  flow) is excluded and counted in `excludedPaymentCount`. So is a refunded booking without a cancellation record.
- Wallet top-ups and withdrawals are not "spending". See the wallet APIs for balances.

**Documents.** These use the same effective statuses as the requirements form (`APPROVED`, `PENDING`,
`REJECTED`, `EXPIRED`, …). `MISSING` means never submitted.

- **Which are listed:** active upload requirements (`IMAGE_UPLOAD`, `DOCUMENT_UPLOAD`, `FILE_UPLOAD`) that the
  customer has submitted, plus `GLOBAL` upload requirements not yet submitted.
- **Service-specific requirements** that were never submitted are not listed. They appear in the booking flow
  through `GET /requirements?serviceTypeId=&optionId=`.
- **`services`:** lists where the requirement applies. `GLOBAL` means every service.

**Pending services.** Newest first (`createdAt`).

- `kind: "REQUEST"` has a `requestId` and no `jobId`. Use `GET /booking-requests/{requestId}`.
- `kind: "BOOKING"` has a `jobId`. `requestId` is also set when the booking came from a booking request.
- `startAt` / `endAt` are market-local date-times, like other booking fields. For route bookings, `startAt` is the
  pickup time.

**Service history.** Only `COMPLETED` and `CANCELLED` bookings, newest first. `endAt` is the actual end time
when recorded, otherwise the expected end. `amount` is the booked amount; for refunds, read the booking's
cancellation.

### "View all"

| List | Endpoint | Note |
|---|---|---|
| Pending/active bookings and history | `GET /api/v1/customers/me/bookings` | Returns **all** bookings, newest first, **without pagination**. Filter by `jobStatus` in the app. |
| One booking request | `GET /api/v1/customers/me/booking-requests/{requestId}` | There is no list-of-requests endpoint yet. |
| Documents | `GET /api/v1/customers/me/requirements?serviceTypeId=&optionId=` | The full form per service/option. |

The backend has no paginated booking list or booking-request list yet. If "View all" needs paging, the backend
must add it; the overview's `hasMore`/`totalCount` already tell the app when to show the button.

### Errors

| Status | When |
|---|---|
| `400 Bad Request` | `recentLimit` outside 1–20 or not a number. |
| `401 Unauthorized` | Missing, invalid or expired token. |
| `403 Forbidden` | Token is not a `CUSTOMER` account. |
| `404 Not Found` | The account itself no longer exists (`"User not found"`). |

## Actions used from the Profile page

These already exist and are unchanged. Full request and response examples are in
[CUSTOMER_APP_FRONTEND_API.md](CUSTOMER_APP_FRONTEND_API.md), in the sections named below.

| Action | Endpoint | Reference |
|---|---|---|
| Edit profile (name, address, photo URL) | `PUT /api/v1/customers/me/profile` | §14 |
| Upload profile photo | `POST /api/v1/uploads/images` (multipart `file`), then send the returned `url` as `profilePhoto` in the PUT above | §22 |
| Upload a document file | `POST /api/v1/uploads/documents` (multipart `file`) returns a private `key` | §21 |
| Submit or resubmit a document | `PUT /api/v1/customers/me/requirement-submissions` with `value.files[].key` | §24 |
| View an uploaded private document | `GET /api/v1/uploads/view-url?key=…` (10-minute signed URL) | §23 |
| Verify email (new account only) | `POST /api/v1/auth/email/verify`, `POST /api/v1/auth/email/resend` | [AUTH_API.md](AUTH_API.md) §2–3 |

### Edit profile

```http
PUT /api/v1/customers/me/profile
Content-Type: application/json
```

```json
{
  "firstName": "Madu",
  "lastName": "Perera",
  "profilePhoto": "https://cdn.example.com/providers/25/images/photo.jpg",
  "addressLine1": "10 Main Street",
  "addressLine2": null,
  "city": "Colombo",
  "district": "Colombo",
  "province": "Western",
  "postalCode": "00100",
  "country": "Sri Lanka"
}
```

`200 OK` returns the saved profile. This is a full replacement, so send every field. Optional fields left out are
cleared. Required: `firstName`, `lastName`, `addressLine1`, `city`, `country`.

### Resubmit a rejected document

1. `POST /api/v1/uploads/documents` with a multipart part named `file`. The `200 OK` response has
   `{"key": "customers/25/documents/<uuid>.jpg", ...}`.
2. Submit the new file:

```http
PUT /api/v1/customers/me/requirement-submissions
Content-Type: application/json
```

```json
{
  "submissions": [
    {"requirementId": 7, "value": {"files": [{"key": "customers/25/documents/<uuid>.jpg"}]}, "expiresOn": "2030-01-01"}
  ]
}
```

`200 OK` returns the saved submissions; a document needing review returns to `PENDING`. Then refresh the
overview. `expiresOn` cannot be in the past. File count, type and size follow the requirement's rules
(`400` on violation).

### Not available yet

| Action | Status |
|---|---|
| Change email of a signed-in account (with OTP to the new address) | **No endpoint.** Email verify/resend only work for new registrations. |
| Add or change phone number of a signed-in account (with SMS OTP) | **No endpoint.** `POST /auth/otp/request` + `/auth/otp/verify` sign in or register by phone; they do not link a phone to an existing email account. |
| Paginated booking history / list of booking requests | **No endpoint** (see "View all" above). |

Hide or disable these buttons until the backend adds them.
