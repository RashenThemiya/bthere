# Booking notice ("now" and advance bookings)

Each service option has a **minimum notice**: how many minutes ahead of the current time a
customer's start (or pickup, for route-based options) must be. Implemented locally; not deployed.

| `minimumNoticeMinutes` | Meaning | Typical options |
|---|---|---|
| `0` | "Now" bookings allowed (`instantBooking: true`) | Ride, online consult |
| `120` (default) | Start at least 2 hours from now | Caregiver, spa, consultant |
| `1440` | Start at least a day from now | Tour guide, rooms, event helpers |

Range 0–43200 (30 days). Options never configured use 120.

## Super Admin

`GET /api/v1/admin/service-options/{optionId}/booking-notice`

`PUT /api/v1/admin/service-options/{optionId}/booking-notice`
```json
{"minimumNoticeMinutes": 0}
```
**200**
```json
{"minimumNoticeMinutes": 0, "instantBooking": true}
```
`instantBooking` is read-only (true when the notice is 0). 400 outside 0–43200; 404 option not found.

The booking-request policy (`PUT /api/v1/admin/service-options/{optionId}/booking-request-policy`)
has the same `minimumNoticeMinutes` field. Saving either endpoint updates both, so they stay equal.
If an older policy still holds a larger value, the larger one applies to booking requests.

## Customer app

Every service option response now includes:

```json
{"minimumNoticeMinutes": 120, "instantBooking": false}
```

- `instantBooking: true` → show **Book now**; send `startAt` / `startDatetime` /
  `requestedPickupTime` as the current time. A start up to 5 minutes old is accepted, to cover
  the delay between the app and the server.
- Otherwise set the date-time picker minimum to `now + minimumNoticeMinutes`, rounded up to the
  next 5 minutes.

The rule is checked on provider search, estimate, quote, direct booking and booking-request
create. It is not checked again when a provider accepts a request or on payment retry.

| Status | Message |
|---|---|
| 400 | `"Bookings for this service must start at least {n} minutes from now"` |
| 400 | `"Start time cannot be in the past"` (notice 0, start more than 5 minutes old) |

## "Now" request lifecycle (rides)

1. Customer taps **Book now** → `POST /customers/me/booking-requests` with the start / pickup time = now.
2. Offers go to nearby providers. When one accepts, the job's start becomes the **acceptance time**
   (the booked duration is kept). A start that is still in the future is never moved.
3. Customer confirms and pays (2-minute window). On confirmation, live tracking starts
   automatically because the start is inside the option's `autoStartWithinMinutes`.
4. Provider app: `GET /providers/me/bookings/{jobId}/tracking` → open `target.navigationUrl`
   (pickup), stream GPS, tap **Arrived**.
5. Provider enters the customer's start code → job `IN_PROGRESS`. With live-tracking mode
   `FULL_TRIP`, tracking continues and `target` / `navigationUrl` switch to the destination.
6. Provider requests completion → tracking ends.

Set a ride option up with booking notice `0` and live-tracking mode `FULL_TRIP`.

## Schema

`service_option.minimum_notice_minutes INT NULL DEFAULT 120` (Hibernate `ddl-auto=update` adds it;
null is treated as 120):

```sql
ALTER TABLE service_option ADD COLUMN minimum_notice_minutes INT NULL DEFAULT 120;
```
