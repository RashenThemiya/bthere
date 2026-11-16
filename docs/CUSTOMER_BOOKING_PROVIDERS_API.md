# Customer Booking Details: assigned providers

Implemented locally on 2026-11-17; deployment is not verified.

Returns the provider(s) assigned to one of the signed-in customer's bookings: name, profile photo,
rating, and whether the customer may call them right now. It works for single-provider and team
bookings, and for history (cancelled/completed bookings still return name and photo).

## Request

```http
GET /api/v1/customers/me/bookings/{jobId}/providers
Authorization: Bearer <customer-access-token>
```

- `jobId`: the booking's job ID (the `jobId` from booking lists, booking-request Views, etc.).
- No body or query parameters.
- Requires a `CUSTOMER` token. The backend checks the booking belongs to this customer.

## Response `200 OK`

### Confirmed single-provider booking (phone allowed)

```json
{
  "jobId": 125,
  "jobStatus": "CONFIRMED",
  "fulfillmentModel": "ONE_TO_ONE",
  "contactAllowedStatuses": ["CONFIRMED", "IN_PROGRESS"],
  "providers": [
    {
      "providerId": 14,
      "role": "PRIMARY",
      "displayName": "Tharushi Silva",
      "profilePhoto": "https://jobhube.s3.eu-north-1.amazonaws.com/profile/14.jpg",
      "averageRating": 4.80,
      "totalRatings": 25,
      "ratedByYou": false,
      "contact": {
        "callAllowed": true,
        "phoneNumber": "+94771234567",
        "unavailableReason": null
      }
    }
  ]
}
```

### Completed booking (history: name and photo still shown, phone hidden)

```json
{
  "jobId": 125,
  "jobStatus": "COMPLETED",
  "fulfillmentModel": "ONE_TO_ONE",
  "contactAllowedStatuses": ["CONFIRMED", "IN_PROGRESS"],
  "providers": [
    {
      "providerId": 14,
      "role": "PRIMARY",
      "displayName": "Tharushi Silva",
      "profilePhoto": "https://jobhube.s3.eu-north-1.amazonaws.com/profile/14.jpg",
      "averageRating": 4.80,
      "totalRatings": 25,
      "ratedByYou": true,
      "contact": {
        "callAllowed": false,
        "phoneNumber": null,
        "unavailableReason": "BOOKING_ENDED"
      }
    }
  ]
}
```

### Team booking (in progress)

```json
{
  "jobId": 140,
  "jobStatus": "IN_PROGRESS",
  "fulfillmentModel": "TEAM",
  "contactAllowedStatuses": ["CONFIRMED", "IN_PROGRESS"],
  "providers": [
    {
      "providerId": 14, "role": "PRIMARY", "displayName": "Tharushi Silva",
      "profilePhoto": null, "averageRating": 4.80, "totalRatings": 25, "ratedByYou": false,
      "contact": {"callAllowed": true, "phoneNumber": "+94771234567", "unavailableReason": null}
    },
    {
      "providerId": 16, "role": "TEAM_MEMBER", "displayName": "Nethmi Jayasinghe",
      "profilePhoto": null, "averageRating": 0.00, "totalRatings": 0, "ratedByYou": false,
      "contact": {"callAllowed": false, "phoneNumber": null, "unavailableReason": "PHONE_NOT_AVAILABLE"}
    }
  ]
}
```

## Fields

| Field | Meaning |
|---|---|
| `jobStatus` | Current booking status. |
| `fulfillmentModel` | `ONE_TO_ONE` for single-provider bookings; other values (e.g. team models) mean several providers may be listed. |
| `contactAllowedStatuses` | The statuses in which a phone can be shown at all. Currently `CONFIRMED`, `IN_PROGRESS`. |
| `providers` | The assigned provider first (`PRIMARY`), then other active team members (`TEAM_MEMBER`). Empty when no provider is assigned yet. |
| `displayName` | First and last name. |
| `profilePhoto` | Photo URL, or `null` when the provider has none. Show a placeholder. |
| `averageRating` / `totalRatings` | The provider's overall public rating. |
| `ratedByYou` | This customer already rated this provider for this booking. |
| `contact.callAllowed` | Show the phone number and Call button only when `true`. |
| `contact.phoneNumber` | Provider phone (E.164). Always `null` when `callAllowed` is `false`. |
| `contact.unavailableReason` | Why the phone is hidden. `null` when `callAllowed` is `true`. |

Providers whose team assignment is `REMOVED`, `DECLINED` or `CANCELLED` are not listed, the same as
ratings and live tracking.

## When the phone is shown

| `jobStatus` | `callAllowed` | `unavailableReason` |
|---|---|---|
| `CONFIRMED` (paid, not started) | `true`* | — |
| `IN_PROGRESS` | `true`* | — |
| `PENDING`, `AWAITING_PAYMENT`, `AWAITING_CUSTOMER_CONFIRMATION`, `PAYMENT_PENDING`, `PAYMENT_FAILED`, `PAYMENT_RECONCILIATION` | `false` | `BOOKING_NOT_CONFIRMED` |
| `AWAITING_COMPLETION_CONFIRMATION`, `COMPLETED`, `CANCELLED`, any other status | `false` | `BOOKING_ENDED` |

\* `false` with `PHONE_NOT_AVAILABLE` when the provider has no phone on their account, or their
account is not active.

The phone is hidden before payment so a booking cannot be moved off the platform, and after the
service ends to protect the provider's privacy. Do not cache the phone number after the booking
leaves `CONFIRMED`/`IN_PROGRESS`; call this endpoint again when the Booking Details page opens or
the booking status changes.

Suggested UI text:

| Reason | Text |
|---|---|
| `BOOKING_NOT_CONFIRMED` | "You can call your provider after the booking is confirmed." |
| `BOOKING_ENDED` | "This booking has ended." |
| `PHONE_NOT_AVAILABLE` | "Phone not available for this provider." |

## Errors

| Status | When |
|---|---|
| `400 Bad Request` | `jobId` is not a number. |
| `401 Unauthorized` | Missing, invalid or expired token. |
| `403 Forbidden` | Token is not a `CUSTOMER` account. |
| `404 Not Found` | The booking does not exist **or** belongs to another customer (same response, so booking IDs cannot be probed). |

`404` body:

```json
{
  "type": "about:blank",
  "title": "Resource not found",
  "status": 404,
  "detail": "Booking not found",
  "instance": "/api/v1/customers/me/bookings/999/providers"
}
```
