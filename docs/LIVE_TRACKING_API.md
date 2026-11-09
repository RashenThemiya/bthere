# Live tracking: API reference

This lists every new and changed API for live tracking. For the screen flow, see
[LIVE_TRACKING_FRONTEND_GUIDE.md](LIVE_TRACKING_FRONTEND_GUIDE.md).

- Base URL: `https://jobhub.pentarixlabs.com`
- Send `Authorization: Bearer <token>` on every call.
- Times are ISO-8601 UTC. Coordinates are decimal degrees.
- Errors come back as ProblemDetail: `{"title","status","detail"}`.

## Summary

| # | Method | Path | Role | Status |
|---|---|---|---|---|
| 1 | GET | `/api/v1/admin/service-options/{optionId}/live-tracking` | SUPER_ADMIN | New |
| 2 | PUT | `/api/v1/admin/service-options/{optionId}/live-tracking` | SUPER_ADMIN | New |
| 3 | GET | `/api/v1/admin/bookings/{jobId}/tracking` | SUPER_ADMIN | New |
| 4 | GET | `/api/v1/providers/me/bookings/{jobId}/tracking` | SERVICE_PROVIDER | New |
| 5 | POST | `/api/v1/providers/me/bookings/{jobId}/start-journey` | SERVICE_PROVIDER | New |
| 6 | POST | `/api/v1/providers/me/bookings/{jobId}/arrived` | SERVICE_PROVIDER | New |
| 7 | POST | `/api/v1/providers/me/location` | SERVICE_PROVIDER | New |
| 8 | GET | `/api/v1/customers/me/bookings/{jobId}/tracking` | CUSTOMER | New |
| 9 | GET | option context (`liveTrackingSupported`) | CUSTOMER | Changed |
| 10 | GET | `/booking-events` + FCM push | CUSTOMER | Changed: 2 new event types |
| 11 | — | booking confirmation (card / wallet / mock card / PayHere notify) | — | Changed: may auto-start tracking |

---

## 1. Get option tracking setting
`GET /api/v1/admin/service-options/{optionId}/live-tracking`

**200**
```json
{"mode": "NONE", "autoStartWithinMinutes": 30}
```
If the option has never been set, the response is `NONE` with `30`.

| Status | When |
|---|---|
| 404 | Option not found |

## 2. Update option tracking setting
`PUT /api/v1/admin/service-options/{optionId}/live-tracking`

**Request**
```json
{"mode": "FULL_TRIP", "autoStartWithinMinutes": 30}
```

| Field | Type | Rules |
|---|---|---|
| `mode` | string, required | `NONE` \| `EN_ROUTE` \| `FULL_TRIP` |
| `autoStartWithinMinutes` | int, required | 0–1440. A booking whose start or pickup time is within this many minutes when it is confirmed starts tracking automatically. Later bookings need Start journey. |

What each mode tracks:

| mode | Shared |
|---|---|
| `NONE` | never |
| `EN_ROUTE` | from journey start until Arrived or until the START code is entered |
| `FULL_TRIP` | from journey start until the provider requests completion |

**200**: the saved setting, same body as #1.

| Status | When |
|---|---|
| 400 | `mode` or `autoStartWithinMinutes` is invalid, or a mode other than `NONE` is set on an option without the `PROVIDER_TO_CUSTOMER` delivery mode |
| 404 | Option not found |

## 3. Admin: view tracking of any booking
`GET /api/v1/admin/bookings/{jobId}/tracking`

Returns the same body as #8, without the ownership check. **404** if the booking is not found.

---

## 4. Provider: tracking state of a booking
`GET /api/v1/providers/me/bookings/{jobId}/tracking`

**200**
```json
{
  "jobId": 29,
  "jobStatus": "CONFIRMED",
  "mode": "FULL_TRIP",
  "status": "NOT_STARTED",
  "startMode": null,
  "canStartJourney": true,
  "canMarkArrived": false,
  "sendLocation": false,
  "updateIntervalSeconds": 4,
  "journeyStartedAt": null,
  "arrivedAt": null,
  "target": {"label": "PICKUP", "latitude": 6.927100, "longitude": 79.845000,
             "address": "Galle Face Green, Colombo 03"}
}
```

| Field | Meaning |
|---|---|
| `status` | `NOT_SUPPORTED` \| `WAITING_FOR_CONFIRMATION` \| `NOT_STARTED` \| `EN_ROUTE` \| `ARRIVED` \| `IN_TRIP` \| `ENDED` |
| `startMode` | `AUTO` (started on confirmation) \| `MANUAL` (Start journey) \| `null` |
| `canStartJourney` | show the **Start journey** button |
| `canMarkArrived` | show the **Arrived** button |
| `sendLocation` | stream GPS to #7 every `updateIntervalSeconds` while true |
| `target` | where the provider is heading: `PICKUP` or `SERVICE_LOCATION`, then `DESTINATION` during the trip. `null` if the booking has no location |

| Status | When |
|---|---|
| 404 | Booking not found, or this provider is not on it |

## 5. Provider: Start journey
`POST /api/v1/providers/me/bookings/{jobId}/start-journey` (no body)

- Starts tracking with `startMode: MANUAL`.
- Sends `PROVIDER_EN_ROUTE` to the customer.
- Repeat calls are safe: once started, it returns the current state and does not notify again.

**200**: same body as #4.

| Status | When |
|---|---|
| 404 | Booking not found or not this provider's |
| 409 | Tracking is not enabled for this booking (`"Live tracking is not enabled for this booking"`) |
| 409 | The booking is not `CONFIRMED` (`"Start journey is available only for a confirmed booking that has not started"`) |

## 6. Provider: Arrived
`POST /api/v1/providers/me/bookings/{jobId}/arrived` (no body)

- Sets `arrivedAt` and sends `PROVIDER_ARRIVED` to the customer.
- `EN_ROUTE` mode stops sharing here. `FULL_TRIP` keeps sharing.
- Repeat calls are safe.

**200**: same body as #4.

| Status | When |
|---|---|
| 404 | Booking not found or not this provider's |
| 409 | Journey not started (`"Start the journey before marking arrival"`) |
| 409 | The booking has already started (`"Arrival can be marked only before the booking starts"`) |

## 7. Provider: send location
`POST /api/v1/providers/me/location`

This endpoint is per provider. One update reaches every booking that is currently sharing.

**Request**
```json
{
  "latitude": 6.914700,
  "longitude": 79.853000,
  "headingDegrees": 120,
  "speedKmh": 28,
  "accuracyMeters": 8,
  "recordedAt": "2026-11-09T12:30:05Z"
}
```

| Field | Type | Rules |
|---|---|---|
| `latitude` | decimal, required | -90 to 90 |
| `longitude` | decimal, required | -180 to 180 |
| `headingDegrees` | number, optional | 0–360 |
| `speedKmh` | number, optional | 0–400 |
| `accuracyMeters` | number, optional | 0–10000 |
| `recordedAt` | instant, required | device time of the GPS fix: within the last 2 minutes, at most 30 s in the future |

**200**
```json
{"receivedAt": "2026-11-09T12:30:05.412Z", "sharedWithJobIds": [29], "nextUpdateSeconds": 4}
```
An older fix than the one already stored is accepted but does not overwrite it.

| Status | When |
|---|---|
| 400 | Field validation fails, or `recordedAt` is outside the allowed window |
| 409 | No booking is sharing right now. Stop GPS and re-read #4 |
| 429 | More than one update every 2 seconds. Response has the `Retry-After: 2` header and `retryAfterSeconds: 2` |

---

## 8. Customer: track a booking
`GET /api/v1/customers/me/bookings/{jobId}/tracking`

Poll every `pollIntervalSeconds` while the screen is open.

**200**
```json
{
  "jobId": 29,
  "jobStatus": "CONFIRMED",
  "mode": "FULL_TRIP",
  "status": "EN_ROUTE",
  "pollIntervalSeconds": 4,
  "staleAfterSeconds": 30,
  "target": {"label": "PICKUP", "latitude": 6.927100, "longitude": 79.845000,
             "address": "Galle Face Green, Colombo 03"},
  "providerDisplayFields": [
    {"fieldId": 4, "code": "RIDE_NORMAL_VEHICLE_TYPE", "label": "Vehicle type", "fieldType": "SELECT",
     "options": ["…same as option context providerDisplayFields…"], "filterOperator": "…", "icon": {"url": "…"}}
  ],
  "providers": [
    {
      "providerId": 18,
      "displayName": "E2E Driver",
      "profilePhoto": null,
      "averageRating": null,
      "totalRatings": 0,
      "providerAttributes": {"4": "CAR"},
      "mapMarker": {"source": "COMBINATION", "icon": {"url": "https://jobhube.s3…/car-marker.png"},
                    "fieldId": 4, "choiceValue": "CAR", "ruleId": 7, "conditions": []},
      "status": "EN_ROUTE",
      "startMode": "AUTO",
      "journeyStartedAt": "2026-11-09T12:29:40Z",
      "arrivedAt": null,
      "location": {"latitude": 6.914700, "longitude": 79.853000, "headingDegrees": 120, "speedKmh": 28,
                   "accuracyMeters": 8, "recordedAt": "2026-11-09T12:30:05Z",
                   "receivedAt": "2026-11-09T12:30:05.412Z"},
      "stale": false,
      "distanceKm": 1.6,
      "etaMinutes": 4
    }
  ]
}
```

| Field | Meaning |
|---|---|
| `status` | Overall status. With several providers it is the most advanced of their statuses. Values are the same as #4 |
| `target.label` | `PICKUP` / `SERVICE_LOCATION` before the service starts, `DESTINATION` during a `FULL_TRIP` trip |
| `providers[].location` | `null` while not sharing, or before the first fix after the journey started |
| `providers[].stale` | No update for `staleAfterSeconds`. Keep the last pin and show "Updating…" |
| `providers[].distanceKm` / `etaMinutes` | Straight-line estimate to `target` using the option's average speed (30 km/h if unset). This is not a road ETA |
| `providers[].mapMarker` | Same marker as in search. Rotate it by `headingDegrees` |
| `providers[].providerAttributes` | Customer-visible provider fields keyed by fieldId. Label them with `providerDisplayFields` |

**404** if the booking is not found or belongs to another customer.

The screen to show for each status:

| status | Show |
|---|---|
| `NOT_SUPPORTED` | no map |
| `WAITING_FOR_CONFIRMATION` | waiting for payment or confirmation |
| `NOT_STARTED` | "Scheduled", provider not on the way yet |
| `EN_ROUTE` | live map to pickup or service location |
| `ARRIVED` | "Provider has arrived". The map stays live only in `FULL_TRIP` |
| `IN_TRIP` | live map to destination |
| `ENDED` | hide the map |

---

## 9. Changed: option context
In the customer option context (the response that has `bookingSchema`, `providerFilters`,
`pricingFieldIds`), `liveTrackingSupported` used to be always `false`. It is now `true` when the
option's tracking mode is not `NONE`. Use it to decide whether to show a tracking screen at all.
Bookings made with a delivery mode other than `PROVIDER_TO_CUSTOMER` still return
`NOT_SUPPORTED` from #8.

## 10. Changed: booking events and push
There are two new `eventType` values in `GET /api/v1/customers/me/booking-events` and in FCM data
messages. Both show a visible notification ("Booking update").

| eventType | When | App action |
|---|---|---|
| `PROVIDER_EN_ROUTE` | Tracking started, automatically on confirmation or by Start journey | open or refresh the tracking screen (#8) |
| `PROVIDER_ARRIVED` | Provider tapped Arrived | show "arrived" and refresh #8 |

The event carries `requestId`. Use `GET /booking-requests/{requestId}` to get the `jobId`.

## 11. Changed: booking confirmation
Nothing changes in these responses. When a booking becomes `CONFIRMED` through any payment path,
the backend now also:
- checks whether the booking starts within `autoStartWithinMinutes`;
- if it does, starts tracking for every provider on the job and sends `PROVIDER_EN_ROUTE`.

The provider app should re-read #4 after `BOOKING_CONFIRMED`. If `sendLocation` is true, start GPS
without waiting for a button.

---

## Database
Hibernate `ddl-auto=update` creates these automatically. A manual script is in
[LIVE_TRACKING_SCHEMA.sql](LIVE_TRACKING_SCHEMA.sql).
- `service_option.live_tracking_mode`, `service_option.live_tracking_auto_start_minutes`
- `job_provider_tracking` (one row per job and provider)
- `provider_live_location` (latest position per provider, purged after 1 day)
