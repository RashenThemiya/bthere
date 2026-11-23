# Booking time zone and UTC times

Booked start and end times are stored as market-local date-times without an offset (for example
`"startDatetime": "2026-11-25T10:00:00"` means 10:00 in Sri Lanka). To show countdowns correctly on a phone set to
any time zone, every customer and provider booking response now also returns the market and the same times as UTC
instants.

| Field | Type | Meaning |
|---|---|---|
| `marketId` | number | Market of the booking |
| `marketTimezone` | string | IANA zone of that market, e.g. `"Asia/Colombo"` |
| `startDatetimeUtc` / `startAtUtc` | ISO instant, `Z` | Booked start as UTC |
| `expectedEndDatetimeUtc` / `endAtUtc` | ISO instant, `Z` | Booked end as UTC |

- The existing local fields (`startDatetime`, `expectedEndDatetime`, `startAt`, `endAt`) are unchanged.
- Values are computed when the response is built, so old bookings get them too.
- A field is `null` when the underlying time is `null` (for example no end time on a ride request), or when the
  market has no valid time zone. `marketId` is still returned in that case.
- Use the `*Utc` fields for countdowns and "late" checks: `minutesLate = (now − startDatetimeUtc) / 60000`.

## Where they appear

| Endpoint | Response | Names |
|---|---|---|
| `GET /api/v1/customers/me/bookings` | `BookingResponse[]` | `marketId`, `marketTimezone`, `startDatetimeUtc`, `expectedEndDatetimeUtc` |
| `POST /api/v1/customers/me/bookings` | `BookingResponse` | same |
| `GET /api/v1/customers/me/bookings/{jobId}/providers` | `BookingProviders` | same, plus `startDatetime`, `expectedEndDatetime` (local) |
| `GET /api/v1/customers/me/booking-requests/{requestId}` (and accept/confirm/cancel responses) | `View` | same, plus `startDatetime`, `expectedEndDatetime` (local) |
| `GET /api/v1/customers/me/booking-requests` | `RequestItem` page | `marketId`, `marketTimezone`, `startAtUtc`, `endAtUtc` |
| `GET /api/v1/customers/me/profile-overview` | `pendingServices`, `serviceHistory` items | `marketId`, `marketTimezone`, `startAtUtc`, `endAtUtc` |
| `GET /api/v1/providers/me/bookings/{jobId}` | `BookingResponse` | `marketId`, `marketTimezone`, `startDatetimeUtc`, `expectedEndDatetimeUtc` |
| `GET /api/v1/providers/me/booking-offers` | `Offer[]` | same, plus `requestedPickupTimeUtc` |

In `View`, before a provider accepts, `startDatetime` is the requested start, or the requested pickup time for a
ride. After a booking exists it is the booking's start. `serviceHistory.endAtUtc` follows `endAt` (the actual end
when the service has finished).

## Example

`GET /api/v1/customers/me/bookings` (one item, trimmed):

```json
{
  "jobId": 38,
  "status": "CONFIRMED",
  "startDatetime": "2026-11-25T10:00:00",
  "expectedEndDatetime": "2026-11-25T12:00:00",
  "actualStartedAt": null,
  "marketId": 1,
  "marketTimezone": "Asia/Colombo",
  "startDatetimeUtc": "2026-11-25T04:30:00Z",
  "expectedEndDatetimeUtc": "2026-11-25T06:30:00Z"
}
```
