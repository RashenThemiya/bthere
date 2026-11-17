# Customer no-show complaints

Built 2026-11-17, **not deployed yet**. Schema: [CUSTOMER_NO_SHOW_SCHEMA.sql](CUSTOMER_NO_SHOW_SCHEMA.sql)
(applied automatically by `ddl-auto=update`).

When a customer does not give the start code, or cannot be reached, the provider can file a complaint after a
waiting period. The booking is then held for admin review. An admin approves it (the customer is charged and the
provider gets a share in their wallet) or rejects it (the customer is refunded in full; the admin may penalise
the provider).

```
CONFIRMED (paid, not started)
   │  start time + waiting period passed, provider files: photo + GPS + time
   ▼
NO_SHOW_REVIEW   money held · start code disabled · no cancellations · no provider penalty
   │  customer may add their side (optional)
   ├── admin APPROVE ──► CANCELLED: customer charged per policy, provider share → provider wallet, rest → platform
   └── admin REJECT  ──► CANCELLED: customer refunded 100% to wallet, optional penalty on the provider
```

| Who | Endpoint |
|---|---|
| Provider | `GET /api/v1/providers/me/bookings/{jobId}/no-show-claim/eligibility` |
| Provider | `POST /api/v1/providers/me/bookings/{jobId}/no-show-claim` |
| Provider | `GET /api/v1/providers/me/bookings/{jobId}/no-show-claim` |
| Customer | `GET /api/v1/customers/me/bookings/{jobId}/no-show-claim` |
| Customer | `POST /api/v1/customers/me/bookings/{jobId}/no-show-claim/response` |
| Admin | `GET /api/v1/admin/no-show-claims?status=PENDING&providerId=&page=0&size=20` |
| Admin | `GET /api/v1/admin/no-show-claims/{claimId}` |
| Admin | `POST /api/v1/admin/no-show-claims/{claimId}/approve` |
| Admin | `POST /api/v1/admin/no-show-claims/{claimId}/reject` |
| Admin | `GET` / `PUT /api/v1/admin/service-options/{optionId}/no-show-settings` |
| Admin | Refund policy fields: `POST /api/v1/admin/markets/{marketId}/refund-policies` ([§7](#7-admin-money-settings-refund-policy)) |

Times without a zone (`opensAt`, `closesAt`) are market-local like other booking times. Times ending in `Z` are UTC.

---

## 1. Admin: turn it on per service option

Complaints are **off** until an admin sets `noShowClaimAfterMinutes` on a service option. Only `TIME_BASED`
options can be enabled.

```http
GET /api/v1/admin/service-options/5/no-show-settings
```

```json
{
  "optionId": 5,
  "schedulingModel": "TIME_BASED",
  "enabled": false,
  "noShowClaimAfterMinutes": null,
  "noShowClaimDeadlineHours": null,
  "noShowMaxDistanceMeters": null,
  "noShowPhotoMaxAgeMinutes": null
}
```

```http
PUT /api/v1/admin/service-options/5/no-show-settings
Content-Type: application/json
```

```json
{
  "noShowClaimAfterMinutes": 60,
  "noShowClaimDeadlineHours": 24,
  "noShowMaxDistanceMeters": 500,
  "noShowPhotoMaxAgeMinutes": 15
}
```

`200 OK` returns the saved settings (same shape as GET, `enabled: true`). This is a full replacement: send all
four. `optionId`, `schedulingModel` and `enabled` in the body are ignored.

| Field | Allowed | `null` means | Meaning |
|---|---|---|---|
| `noShowClaimAfterMinutes` | 1–1440 | **off** | Minutes after the booked start before the provider can complain (for example 60 or 120). |
| `noShowClaimDeadlineHours` | 1–720 | 24 | Hours after the booked end (or after the window opens, if later) during which a complaint can still be filed. |
| `noShowMaxDistanceMeters` | 10–100000 | 500 | Complaints filed farther than this from the booking location are **flagged** for admin (not blocked). |
| `noShowPhotoMaxAgeMinutes` | 1–240 | 15 | The photo must have been uploaded within this many minutes before submitting. |

Errors: `400` for values outside the ranges, or `"No-show complaints are only available for TIME_BASED service
options"`; `404` unknown option.

## 2. Provider: check and file

### Check whether the button can be enabled

```http
GET /api/v1/providers/me/bookings/70/no-show-claim/eligibility
```

```json
{
  "jobId": 70,
  "eligible": false,
  "reason": "TOO_EARLY",
  "opensAt": "2026-11-20T10:00:00",
  "closesAt": "2026-11-21T11:00:00",
  "maxDistanceMeters": 500,
  "photoMaxAgeMinutes": 15
}
```

| `reason` | Meaning | App |
|---|---|---|
| `null` | Can file now (`eligible: true`). | Show "Report customer not available". |
| `TOO_EARLY` | Waiting period not over. | Show "Available from `opensAt`". |
| `TOO_LATE` | After `closesAt`. | Hide; contact support. |
| `NOT_ENABLED` | Admin has not enabled it for this service option. | Hide. |
| `NOT_TIME_BASED` | Route/trip booking. | Hide. |
| `NOT_WAITING_TO_START` | Booking already started, finished, cancelled or under review. | Hide. |
| `NOT_PAID` | Booking is not paid. | Hide. |
| `ALREADY_SUBMITTED` | A complaint exists. | Show the complaint (`GET …/no-show-claim`). |

`opensAt`/`closesAt` are `null` when the option has no waiting period.

### File the complaint

1. Take the photo **in the app camera** (do not allow gallery picks) and upload it as a private file:

   ```http
   POST /api/v1/uploads/documents
   Content-Type: multipart/form-data   (part name: file; JPG, PNG or WebP)
   ```

   `200 OK` → `{"key": "providers/180/documents/<uuid>.jpg", ...}`

2. Read the GPS position at the same time, then submit:

   ```http
   POST /api/v1/providers/me/bookings/70/no-show-claim
   Content-Type: application/json
   ```

   ```json
   {
     "photoKey": "providers/180/documents/3f2a9c.jpg",
     "latitude": 6.9272,
     "longitude": 79.8613,
     "accuracyMeters": 12,
     "deviceCapturedAt": "2026-11-20T04:45:10Z",
     "callAttempts": 3,
     "note": "Called 3 times, no answer. Gate locked."
   }
   ```

| Field | Required | Rules |
|---|---|---|
| `photoKey` | yes | Your own upload from step 1. Must be an image uploaded **after the booked start** and within `photoMaxAgeMinutes`. |
| `latitude`, `longitude` | yes | Device GPS, −90…90 / −180…180. |
| `accuracyMeters` | no | GPS accuracy, 0–100000. |
| `deviceCapturedAt` | yes | Phone time of the photo/GPS (UTC ISO). Stored for comparison; the server uses its own times. |
| `callAttempts` | no | 0–100. |
| `note` | no | Up to 2000 characters. |

`201 Created` returns the complaint ([§5](#5-complaint-object)) with `status: "PENDING"`. The booking becomes
`NO_SHOW_REVIEW`, the customer gets a push (`NO_SHOW_REPORTED`), and **no penalty** is charged to the provider.
Sending the same request again returns the existing complaint.

What the server records itself: the photo's upload time (from storage, not the phone), the submit time, minutes
after the booked start, and the distance to the booking location (`distanceFlagged` when above the limit).

| Status | `detail` |
|---|---|
| `400` | `Take a new photo now; it must be uploaded within the last 15 minutes` (old photo, or taken before the booked start) |
| `400` | `Photo must be a JPG, PNG or WebP you uploaded with POST /api/v1/uploads/documents`, `Uploaded photo does not exist` |
| `400` | field validation |
| `404` | Not your booking. |
| `409` | Not eligible; the message matches the `reason` (for example `A complaint can be filed from 2026-11-20T10:00`). |

## 3. Customer: see it and respond

```http
GET /api/v1/customers/me/bookings/70/no-show-claim
```

Returns the complaint without the provider's exact location and photo (`photoKey`, `latitude`, `longitude`,
`accuracyMeters`, `distanceMeters`, `distanceFlagged`, `deviceCapturedAt`, `penalty` are `null`). `404` when
there is no complaint.

The customer can add their side **once**, while `status` is `PENDING`:

```http
POST /api/v1/customers/me/bookings/70/no-show-claim/response
Content-Type: application/json
```

```json
{"note": "I was at home; nobody called or rang the bell."}
```

`200 OK` returns the complaint. `409` if already answered or already decided. The provider gets a silent
`NO_SHOW_CUSTOMER_RESPONDED` event.

While the booking is `NO_SHOW_REVIEW`, the customer cannot cancel it and the provider's phone is hidden
(`GET /bookings/{jobId}/providers` returns `unavailableReason: "BOOKING_ENDED"`).

## 4. Admin: review

```http
GET /api/v1/admin/no-show-claims?status=PENDING
```

Spring page of complaints, newest first (`content`, `totalElements`, …). `status`: `PENDING`, `APPROVED` or
`REJECTED` (optional). `providerId` optional.

```http
GET /api/v1/admin/no-show-claims/1
```

The full complaint, plus `photoUrl` (a 10-minute signed link to the photo). Show the photo, a map with
`latitude`/`longitude` and the booking location, `distanceMeters` (red when `distanceFlagged`),
`photoUploadedAt` vs `submittedAt`, `minutesAfterStart`, `callAttempts`, both notes.

### Approve (customer was at fault)

```http
POST /api/v1/admin/no-show-claims/1/approve
Content-Type: application/json
```

```json
{"reason": "Photo shows locked gate at the booked time; customer did not answer", "refundPercentage": null, "providerSharePercentage": null}
```

- `refundPercentage` (0–100, optional): how much the customer gets back. Default: the booking's refund policy
  `customerNoShowRefundPercentage`. If both are missing → `409`.
- `providerSharePercentage` (0–100, optional): provider's share of the amount kept. Default: the policy's
  `retainedProviderSharePercentage`; if that is also null, the booking's normal provider/platform split.

Calculation (same as a customer cancellation): refund = `refundPercentage` × (paid − platform fee, unless the
policy refunds the platform fee). Kept = paid − refund. Provider gets kept × `providerSharePercentage` **in
their wallet**; the platform gets the rest. Example: paid 10,000, platform fee 1,000, refund 0 %, provider share
70 % → customer 0, provider 7,000, platform 3,000.

### Reject (complaint not accepted)

```http
POST /api/v1/admin/no-show-claims/1/reject
Content-Type: application/json
```

```json
{"reason": "Customer was home; provider went to the wrong address", "penaltyAmount": 1500.00}
```

- The customer is refunded **100 %** to their wallet.
- `penaltyAmount` (optional, 0 to the booking amount): charged to the provider like a cancellation penalty
  (taken from their wallet now or later; the policy's `penaltyCustomerSharePercentage` of it goes to the
  customer). With a penalty, the booking counts as a **provider cancellation** towards the suspension limit;
  waiving the penalty (`POST /admin/provider-penalties/{penaltyId}/waive`) removes it from the count.
  Without a penalty nothing is charged and nothing is counted.

Both decisions cancel the booking (`CANCELLED`), create the normal cancellation record
(`GET /api/v1/admin/bookings/{jobId}/cancellation` and the customer/provider equivalents), and send pushes:
provider `NO_SHOW_CLAIM_APPROVED` / `NO_SHOW_CLAIM_REJECTED`; customer `BOOKING_CANCELLED` plus silent
`NO_SHOW_RESOLVED`. The cancellation's `cancelledBy` is `CUSTOMER_NO_SHOW`, `NO_SHOW_REJECTED`, or `PROVIDER`
(rejected with a penalty).

| Status | When |
|---|---|
| `400` | Missing `reason`, percentage outside 0–100, `penaltyAmount cannot be more than the booking amount …`. |
| `404` | Unknown complaint. |
| `409` | Already approved/rejected; booking no longer in `NO_SHOW_REVIEW`; no refund percentage available; payment cannot be refunded (for example unverified card payment). |

## 5. Complaint object

```json
{
  "claimId": 1,
  "jobId": 70,
  "requestId": 9,
  "serviceProviderId": 18,
  "status": "APPROVED",
  "submittedAt": "2026-11-20T04:46:02Z",
  "minutesAfterStart": 76,
  "callAttempts": 3,
  "providerNote": "Called 3 times, no answer. Gate locked.",
  "photoKey": "providers/180/documents/3f2a9c.jpg",
  "photoUrl": "https://…signed…",
  "photoUploadedAt": "2026-11-20T04:45:31Z",
  "latitude": 6.9272000,
  "longitude": 79.8613000,
  "accuracyMeters": 12,
  "distanceMeters": 15,
  "distanceFlagged": false,
  "deviceCapturedAt": "2026-11-20T04:45:10Z",
  "customerNote": "I was at home.",
  "customerRespondedAt": "2026-11-20T05:10:00Z",
  "decidedAt": "2026-11-20T08:00:00Z",
  "decisionReason": "Photo shows locked gate at the booked time",
  "currencyCode": "LKR",
  "refundPercentage": 0.00,
  "refundAmount": 0.00,
  "providerAmount": 7000.00,
  "platformAmount": 3000.00,
  "penaltyAmount": null,
  "penalty": null
}
```

- `photoUrl`: admin detail only; `null` everywhere else.
- `distanceMeters`: `null` when the booking has no location.
- Decision fields (`decidedAt` … `penalty`) are `null` while `PENDING`. `penaltyAmount`/`penalty` are set only
  for a rejection with a penalty (`penalty` uses the provider-penalty shape from
  [CANCELLATION_REFUND_API.md](CANCELLATION_REFUND_API.md)).
- Customer view: evidence location/photo fields and `penalty` are `null`.

## 6. Booking status `NO_SHOW_REVIEW`

New value for both the booking (job) and the booking request. While in it: the start code cannot be used,
neither side can cancel, live tracking is closed, and the money stays held. It counts as **active** in the
customer profile overview, and `NO_SHOW_REVIEW` is accepted by `GET /customers/me/booking-requests?status=`.

## 7. Admin money settings (refund policy)

Approval uses two existing refund policy fields, set per market (and optionally per offering) when creating a
policy version (`POST /api/v1/admin/markets/{marketId}/refund-policies`, then `/activate`):

| Field | Used for | Example |
|---|---|---|
| `customerNoShowRefundPercentage` | Refund to the customer when a complaint is approved. `0` = customer pays everything. | `0.00` |
| `retainedProviderSharePercentage` | Provider's share of the amount kept (also used for late customer cancellations). | `70.00` |
| `penaltyCustomerSharePercentage` | Customer's share of a provider penalty after a rejected complaint. | `50.00` |
| `platformFeeRefundable` | Whether the platform fee is part of the refundable base. | `false` |

The admin can override the first two per decision (`refundPercentage`, `providerSharePercentage`).
