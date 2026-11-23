# Service elapsed timer (customer Booking Details)

Three timestamps on the customer booking responses let the app show how long the service has been running.

| Field | Set when | Before that |
|---|---|---|
| `actualStartedAt` | The provider verifies the customer's START code (status → `IN_PROGRESS`) | `null` |
| `actualFinishedAt` | The provider finishes the service (status → `AWAITING_COMPLETION_CONFIRMATION`) | `null` |
| `completionConfirmedAt` | The customer confirms completion (status → `COMPLETED`) | `null` |

- All three are ISO-8601 instants in UTC, for example `"2026-11-19T04:45:02.123456Z"`.
- They are stored on the booking, so the same values come back after the app is closed and reopened.
- Repeated start, finish or confirm calls don't change a time that is already set.
- `completionConfirmedAt` is new (2026-11-19). Bookings completed before this change have `null` there, but
  their `actualStartedAt` and `actualFinishedAt` are filled.

For the booked start and end as UTC and the market time zone (countdowns, "provider is late"), see
[BOOKING_TIME_ZONE.md](BOOKING_TIME_ZONE.md).

## Where the fields appear

All three responses carry the same three fields with the same values.

| Endpoint | Response |
|---|---|
| `GET /api/v1/customers/me/booking-requests/{requestId}` | `View` (null until the request has a booking) |
| `GET /api/v1/customers/me/bookings` | `BookingResponse[]` |
| `GET /api/v1/customers/me/bookings/{jobId}/providers` | `BookingProviders` ([assigned providers](CUSTOMER_BOOKING_PROVIDERS_API.md)) |

Example (`GET /api/v1/customers/me/bookings/{jobId}/providers`, service running):

```json
{
  "jobId": 70,
  "jobStatus": "IN_PROGRESS",
  "fulfillmentModel": "ONE_TO_ONE",
  "contactAllowedStatuses": ["CONFIRMED", "IN_PROGRESS"],
  "providers": [ … ],
  "actualStartedAt": "2026-11-19T04:45:02.123456Z",
  "actualFinishedAt": null,
  "completionConfirmedAt": null
}
```

After the provider finishes and the customer confirms:

```json
{
  "jobStatus": "COMPLETED",
  "actualStartedAt": "2026-11-19T04:45:02.123456Z",
  "actualFinishedAt": "2026-11-19T07:15:30.004211Z",
  "completionConfirmedAt": "2026-11-19T07:20:11.918034Z"
}
```

## Showing the timer

| State | Show |
|---|---|
| `actualStartedAt == null` | No timer (service not started) |
| started, `actualFinishedAt == null` | Live timer: now − `actualStartedAt` |
| `actualFinishedAt != null` | Fixed duration: `actualFinishedAt` − `actualStartedAt` |

Phone clocks can be wrong. For an accurate live timer, read the HTTP `Date` header of the same response, work
out the difference from the phone clock once, and apply it to "now".

Refresh the booking when the `BOOKING_STARTED`, `COMPLETION_REQUESTED` or `BOOKING_COMPLETED` event or push
arrives.

## Already available elsewhere?

Not for customers. Before this change, only the admin booking monitor
(`GET /api/v1/admin/booking-monitor/bookings/{jobId}`) showed `actualStartDatetime` and `actualEndDatetime`. It is
admin-only and uses market-local times with no offset, so the customer app can't use it.

## Schema

Hibernate adds the column automatically (`ddl-auto=update`). For manual setups:

```sql
ALTER TABLE job ADD COLUMN completion_confirmed_at DATETIME(6) NULL;
```
