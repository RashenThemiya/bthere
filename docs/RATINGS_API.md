# Provider ratings: API reference

Customers rate each provider once per completed booking. Every rating keeps the booking's
service and service option, so a provider has a rating per option (Home care, Hospital care…)
as well as an overall rating. Implemented locally; not deployed.

- Base URL: `https://jobhub.pentarixlabs.com`
- Send `Authorization: Bearer <token>` on every call.
- Errors are ProblemDetail: `{"title","status","detail"}`.
- Schema: [RATINGS_SCHEMA.sql](RATINGS_SCHEMA.sql) (Hibernate `ddl-auto=update` applies it).

## Summary

| # | Method | Path | Role |
|---|---|---|---|
| 1 | POST | `/api/v1/customers/me/bookings/{jobId}/ratings` | CUSTOMER |
| 2 | GET | `/api/v1/customers/me/bookings/{jobId}/ratings` | CUSTOMER |
| 3 | GET | `/api/v1/customers/me/providers/{providerId}/ratings?optionId=` | CUSTOMER |
| 4 | GET | `/api/v1/customers/me/providers/{providerId}/reviews?optionId=&page=&size=` | CUSTOMER |
| 5 | GET | `/api/v1/providers/me/ratings?optionId=` | SERVICE_PROVIDER |
| 6 | GET | `/api/v1/providers/me/reviews?optionId=&page=&size=` | SERVICE_PROVIDER |
| 7 | GET | `/api/v1/admin/ratings?providerId=&optionId=&status=&page=&size=` | SUPER_ADMIN |
| 8 | GET | `/api/v1/admin/providers/{providerId}/ratings?optionId=` | SUPER_ADMIN |
| 9 | PATCH | `/api/v1/admin/ratings/{ratingId}/status` | SUPER_ADMIN |
| 10 | — | provider search candidates gain `optionAverageRating`, `optionTotalRatings` | Changed |

## Rules

- Only the booking's customer can rate, and only when the job is `COMPLETED` (after
  `confirm-completion`).
- One rating per provider per booking. A team booking can rate each provider separately.
- `rating` is a whole number 1–5. `comment` is optional, up to 1000 characters.
- Ratings cannot be edited or deleted by the customer. Super Admin can hide one.
- Only `VISIBLE` ratings count in averages, totals and review lists.
- The provider's overall `averageRating` / `totalRatings` (already in search, tracking and the
  provider profile) are recalculated on every new rating and every moderation change.

## 1. Rate a provider
`POST /api/v1/customers/me/bookings/{jobId}/ratings`

```json
{"providerId": 18, "rating": 5, "comment": "Very caring and on time."}
```

`providerId` can be omitted when the booking has one provider.

**201**
```json
{
  "ratingId": 41, "jobId": 70, "providerId": 18, "serviceTypeId": 21, "optionId": 12,
  "rating": 5, "comment": "Very caring and on time.", "status": "VISIBLE",
  "createdAt": "2026-11-10T09:30:00"
}
```

| Status | When |
|---|---|
| 400 | rating not 1–5, comment over 1000, `providerId` missing on a multi-provider booking (`"providerId is required when a booking has more than one provider"`), or the provider was not on the booking (`"This provider did not work on this booking"`) |
| 404 | Booking not found or not yours (`"Booking not found"`) |
| 409 | Booking not completed (`"Only a completed booking can be rated"`), or already rated (`"You have already rated this provider for this booking"`) |

## 2. Rating state of a booking
`GET /api/v1/customers/me/bookings/{jobId}/ratings`

Use it after `BOOKING_COMPLETED` to show the "Rate your provider" prompt.

```json
{
  "jobId": 70, "optionId": 12, "jobStatus": "COMPLETED", "canRate": true,
  "providers": [
    {"providerId": 18, "displayName": "Kumari Perera", "profilePhoto": null, "rated": true,
     "rating": {"ratingId": 41, "jobId": 70, "providerId": 18, "serviceTypeId": 21, "optionId": 12,
                "rating": 5, "comment": "Very caring and on time.", "status": "VISIBLE",
                "createdAt": "2026-11-10T09:30:00"}},
    {"providerId": 19, "displayName": "Shirani Fernando", "profilePhoto": null, "rated": false, "rating": null}
  ]
}
```

`canRate` is true when the job is `COMPLETED` and at least one provider is still unrated.
404 if the booking is not yours.

## 3. Provider rating summary
`GET /api/v1/customers/me/providers/{providerId}/ratings?optionId=12`

`optionId` is optional. With it, `options` holds only that option; `overall` always covers
every option.

```json
{
  "providerId": 18, "displayName": "Kumari Perera",
  "overall": {"averageRating": 4.20, "totalRatings": 5,
              "distribution": {"1": 0, "2": 1, "3": 0, "4": 1, "5": 3}},
  "options": [
    {"optionId": 12, "optionName": "Home care", "averageRating": 4.75, "totalRatings": 4,
     "distribution": {"1": 0, "2": 0, "3": 0, "4": 1, "5": 3}},
    {"optionId": 13, "optionName": "Hospital bedside care", "averageRating": 2.00, "totalRatings": 1,
     "distribution": {"1": 0, "2": 1, "3": 0, "4": 0, "5": 0}}
  ]
}
```

`averageRating` is `null` when there are no ratings. Options are ordered by number of ratings.
404 if the provider does not exist.

## 4. Provider reviews
`GET /api/v1/customers/me/providers/{providerId}/reviews?optionId=12&page=0&size=20`

Newest first, visible ratings only. `size` 1–50. Customer names are shortened.

```json
{
  "content": [
    {"ratingId": 41, "optionId": 12, "optionName": "Home care", "rating": 5,
     "comment": "Very caring and on time.", "customerName": "Nimali S.",
     "createdAt": "2026-11-10T09:30:00"}
  ],
  "totalElements": 4, "totalPages": 1, "number": 0, "size": 20
}
```

400 for a bad `page` / `size`; 404 if the provider does not exist.

## 5–6. Provider's own ratings
`GET /api/v1/providers/me/ratings?optionId=` returns #3 for the signed-in provider.
`GET /api/v1/providers/me/reviews?optionId=&page=&size=` returns #4 for the signed-in provider.

## 7. Admin: search ratings
`GET /api/v1/admin/ratings?providerId=18&optionId=12&status=VISIBLE&page=0&size=20`

All filters are optional; `status` is `VISIBLE` or `HIDDEN`. Spring page of:

```json
{
  "ratingId": 41, "jobId": 70, "providerId": 18, "providerName": "Kumari Perera",
  "customerId": 4, "customerName": "Nimali Silva", "serviceTypeId": 21, "optionId": 12,
  "rating": 5, "comment": "Very caring and on time.", "status": "VISIBLE",
  "moderationReason": null, "moderatedBy": null,
  "createdAt": "2026-11-10T09:30:00", "updatedAt": "2026-11-10T09:30:00"
}
```

## 8. Admin: provider summary
`GET /api/v1/admin/providers/{providerId}/ratings?optionId=` returns the same body as #3.

## 9. Admin: hide or restore a rating
`PATCH /api/v1/admin/ratings/{ratingId}/status`

```json
{"status": "HIDDEN", "reason": "Abusive language"}
```

`status` is `VISIBLE` or `HIDDEN`; `reason` up to 500 characters. Returns the #7 item. The
provider's averages are recalculated and the change is audited as `RATING_MODERATED`.
404 if the rating does not exist.

## 10. Changed: provider search
Each candidate in `POST /api/v1/customers/me/provider-search` now also has:

| Field | Meaning |
|---|---|
| `optionAverageRating` | Average for the searched option, `null` if none |
| `optionTotalRatings` | Number of visible ratings for the searched option |

`averageRating` / `totalRatings` stay the provider's overall figures (now kept up to date).
Show the option figures on search cards: "★ 4.8 (25) for Home care".
