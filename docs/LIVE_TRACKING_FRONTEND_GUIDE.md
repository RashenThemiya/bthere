# Live tracking: frontend guide

Uber-style "provider is on the way" tracking. It works for any service option. Admins switch it
on per option. The provider app sends GPS and the customer app polls for it. Nothing here is
specific to rides.

## 1. Admin: switch tracking on for an option (SUPER_ADMIN)

```http
GET /api/v1/admin/service-options/{optionId}/live-tracking
PUT /api/v1/admin/service-options/{optionId}/live-tracking
{"mode": "FULL_TRIP", "autoStartWithinMinutes": 30}
```

| mode | Location is shared | Use for |
|---|---|---|
| `NONE` (default) | never | online, customer goes to provider |
| `EN_ROUTE` | from journey start until the provider taps Arrived or the service starts | caregiver, technician, home visits |
| `FULL_TRIP` | from journey start until the provider requests completion | rides, delivery |

- The option must allow the `PROVIDER_TO_CUSTOMER` delivery mode, otherwise the request returns 400.
- Bookings made with another delivery mode are never tracked, even on a tracked option.
- `autoStartWithinMinutes` is a number from 0 to 1440.
- The customer option context field `liveTrackingSupported` is now `true` when the mode is not `NONE`.

## 2. When tracking starts

Tracking starts when the booking becomes `CONFIRMED`, after payment.

- **"Now" booking:** the pickup or start time is within `autoStartWithinMinutes` of confirmation.
  - Tracking starts **automatically** with `startMode: AUTO`.
  - The customer gets the `PROVIDER_EN_ROUTE` event.
  - The provider has no button to press.
- **Scheduled booking:** tracking does not start on its own.
  - The provider app shows a **Start journey** button.
  - When the provider taps it, tracking starts with `startMode: MANUAL` and the customer gets `PROVIDER_EN_ROUTE`.

The pickup time is used for route-based options and the start time for time-based options.

```
now:        accept → pay → CONFIRMED (tracking AUTO) → Arrived → START code → IN_PROGRESS → complete
scheduled:  accept → pay → CONFIRMED → [Start journey] → Arrived → START code → IN_PROGRESS → complete
            EN_ROUTE stops at Arrived / START code · FULL_TRIP continues until the provider taps complete
```

## 3. Provider app (SERVICE_PROVIDER token)

### Read the state of a job
`GET /api/v1/providers/me/bookings/{jobId}/tracking`

```json
{
  "jobId": 29, "jobStatus": "CONFIRMED", "mode": "FULL_TRIP", "status": "NOT_STARTED",
  "startMode": null, "canStartJourney": true, "canMarkArrived": false,
  "sendLocation": false, "updateIntervalSeconds": 4,
  "journeyStartedAt": null, "arrivedAt": null,
  "target": {"label": "PICKUP", "latitude": 6.9271, "longitude": 79.845, "address": "Galle Face Green"}
}
```

- Show the **Start journey** button only when `canStartJourney` is true.
- Show the **Arrived** button only when `canMarkArrived` is true.
- While `sendLocation` is true, stream GPS every `updateIntervalSeconds`.
- Re-read this endpoint when you receive these events: `BOOKING_CONFIRMED`, `BOOKING_STARTED`, `COMPLETION_REQUESTED`, `BOOKING_CANCELLED`.

### Actions
| Call | Effect | Errors |
|---|---|---|
| `POST /api/v1/providers/me/bookings/{jobId}/start-journey` | Starts tracking, `startMode: MANUAL`. Repeat calls are safe. | 409 if the job is not `CONFIRMED` or tracking is off |
| `POST /api/v1/providers/me/bookings/{jobId}/arrived` | Sets `arrivedAt` and notifies the customer. Repeat calls are safe. | 409 before Start journey, or after the service has started |

Both return the same state body as GET.

### Send location
`POST /api/v1/providers/me/location`

This endpoint is per provider, not per job: one update reaches every job that is currently sharing.

```json
{"latitude": 6.9147, "longitude": 79.853, "headingDegrees": 120, "speedKmh": 28,
 "accuracyMeters": 8, "recordedAt": "2026-11-09T12:30:05Z"}
```

Response:

```json
{"receivedAt": "2026-11-09T12:30:05.4Z", "sharedWithJobIds": [29], "nextUpdateSeconds": 4}
```

- `headingDegrees`, `speedKmh` and `accuracyMeters` are optional.
- `recordedAt` is the device time of the GPS fix. It must be within the last 2 minutes, with up to 30 seconds of clock skew allowed. Otherwise the request returns 400.
- Updates are limited to one every 2 seconds. A faster update returns 429.
- An older buffered fix never overwrites a newer one.
- **409** means no booking is sharing right now. Stop the GPS stream and re-read the job state.

## 4. Customer app (CUSTOMER token)

`GET /api/v1/customers/me/bookings/{jobId}/tracking` (poll every `pollIntervalSeconds`, which is 4)

```json
{
  "jobId": 29, "jobStatus": "CONFIRMED", "mode": "FULL_TRIP", "status": "EN_ROUTE",
  "pollIntervalSeconds": 4, "staleAfterSeconds": 30,
  "target": {"label": "PICKUP", "latitude": 6.9271, "longitude": 79.845, "address": "Galle Face Green"},
  "providerDisplayFields": [ /* same shape as option context providerDisplayFields */ ],
  "providers": [{
    "providerId": 18, "displayName": "E2E Driver", "profilePhoto": "…",
    "averageRating": 4.8, "totalRatings": 12,
    "providerAttributes": {"4": "CAR"},
    "mapMarker": {"source": "COMBINATION", "icon": {"url": "…car marker…"}, "...": "..."},
    "status": "EN_ROUTE", "startMode": "AUTO",
    "journeyStartedAt": "…", "arrivedAt": null,
    "location": {"latitude": 6.9147, "longitude": 79.853, "headingDegrees": 120, "speedKmh": 28,
                 "accuracyMeters": 8, "recordedAt": "…", "receivedAt": "…"},
    "stale": false, "distanceKm": 1.6, "etaMinutes": 4
  }]
}
```

### `status` values

| status | Show |
|---|---|
| `NOT_SUPPORTED` | no tracking map |
| `WAITING_FOR_CONFIRMATION` | payment or confirmation still pending |
| `NOT_STARTED` | "Scheduled for …"; the provider has not started the journey |
| `EN_ROUTE` | live map: provider moving to `target` (`PICKUP` or `SERVICE_LOCATION`) |
| `ARRIVED` | "Your provider has arrived". The map stays live only in `FULL_TRIP` |
| `IN_TRIP` | live map to `target` `DESTINATION` (`FULL_TRIP` only) |
| `ENDED` | hide the map |

### Notes for the customer screen
- **Location:** `location` is `null` until the first GPS fix after the journey started, and whenever sharing is off. A position recorded before this journey is never returned.
- **Stale:** `stale: true` means no update for 30 seconds. Keep the last pin and show "Updating location…".
- **ETA:** `etaMinutes` is a straight-line distance estimate using the option's average speed. It is not a road-route ETA.
- **Marker:** draw the provider with `mapMarker.icon.url`, rotated by `headingDegrees`.
- **Vehicle details:** label `providerAttributes` with `providerDisplayFields`. These are the vehicle type and other customer-visible fields.
- **Multiple providers:** jobs with more than one provider list each one in `providers[]`. The top-level `status` is the most advanced status among them.
- **Access:** another customer's job returns 404.

### Events (existing `/booking-events` feed and FCM push)
- `PROVIDER_EN_ROUTE`: tracking started. Open or refresh the tracking screen.
- `PROVIDER_ARRIVED`: the provider tapped Arrived.

## 5. Admin support

`GET /api/v1/admin/bookings/{jobId}/tracking` returns the same body as the customer view, for any job.

## 6. Privacy and retention
- Only the latest position per provider is stored. No route history is kept.
- Positions older than one day are deleted every hour.
- A provider's position is visible only to the customer of a job whose tracking window is open, and to SUPER_ADMIN.

Schema: `docs/LIVE_TRACKING_SCHEMA.sql`. You only need it when Hibernate `ddl-auto` is off.
