# Market banners API

Banners are promotional images that the admin dashboard manages for each market and the mobile apps show,
for example as a carousel on the home screen. A market can have up to 20 banners. Each banner has:

- an image;
- a title, plus an optional subtitle;
- an audience: `CUSTOMER`, `PROVIDER` or `ALL`;
- an optional tap action: `NONE`, `SERVICE`, `OPTION` or `URL`;
- a display order;
- an `ACTIVE` / `INACTIVE` switch;
- an optional schedule (`startsAt` / `endsAt`, UTC ISO-8601).

Base URL: `https://jobhub.pentarixlabs.com/api/v1`. Errors use the usual ProblemDetail body. Validation errors
also carry a machine-readable `code` (listed below).

## Mobile app

### `GET /markets/{marketId}/banners?audience=CUSTOMER`

This endpoint is public, so no `Authorization` header is needed and it works on splash or login screens too.

- `audience` is `CUSTOMER` (the default) or `PROVIDER`.
- The response holds only the banners to show now, already in display order. Each one is `ACTIVE`, inside its
  schedule, meant for this audience or for `ALL`, and its linked service or option is still offered in the
  market.
- The response is cached for 1 minute (`Cache-Control: public, max-age=60`).

`200 OK`

```json
{
  "marketId": 1,
  "audience": "CUSTOMER",
  "banners": [
    {
      "bannerId": 4,
      "title": "Home care from LKR 2,500",
      "subtitle": "Verified caregivers near you",
      "imageUrl": "https://jobhube.s3.eu-north-1.amazonaws.com/service-icons/3f6c1c0e-9a51-4c1b-8d55-0f1c2a9b7e10.jpg",
      "linkType": "OPTION",
      "serviceTypeId": 3,
      "optionId": 12,
      "linkUrl": null,
      "sortOrder": 0,
      "endsAt": "2026-12-31T18:30:00Z"
    },
    {
      "bannerId": 7,
      "title": "How Bthere keeps you safe",
      "subtitle": null,
      "imageUrl": "https://jobhube.s3.eu-north-1.amazonaws.com/service-icons/9b2d7a41-0c3e-4f5a-b6d8-1e2f3a4b5c6d.webp",
      "linkType": "URL",
      "serviceTypeId": null,
      "optionId": null,
      "linkUrl": "https://example.com/safety",
      "sortOrder": 1,
      "endsAt": null
    }
  ]
}
```

| `linkType` | What a tap does |
|---|---|
| `NONE` | Nothing. The banner is image only. |
| `SERVICE` | Open the service: `GET /customers/me/services/{serviceTypeId}/options?marketId=` |
| `OPTION` | Open that option's booking: `GET /customers/me/booking-context?marketId=&serviceTypeId=&optionId=` |
| `URL` | Open `linkUrl` (always `https://`) in the browser. |

Notes for the app:

- Load `imageUrl` directly. It is a public image and needs no token.
- An empty `banners` list means "hide the carousel".
- `endsAt` is only a hint: the server already drops banners whose schedule has ended.
- Errors:

| Status | When | Body |
|---|---|---|
| `400` | `audience` is not `CUSTOMER` or `PROVIDER` | `code: BANNER_AUDIENCE_INVALID` |
| `404` | The market does not exist or is not `ACTIVE` | `detail: "Market not found"` |

## Admin dashboard (SUPER_ADMIN token)

### 1. Upload the image

`POST /uploads/images`, sent as `multipart/form-data` with the image in the `file` field (the existing endpoint).
As a SUPER_ADMIN, the file is stored under the public `service-icons/` prefix.

- Allowed types: JPG, PNG or WebP, up to 10 MB. SVG is not accepted for banners.
- Suggested size: 1200×600 px (2:1) and under 500 KB, so the carousel loads quickly.

`201 Created`

```json
{
  "key": "service-icons/3f6c1c0e-9a51-4c1b-8d55-0f1c2a9b7e10.jpg",
  "url": "https://jobhube.s3.eu-north-1.amazonaws.com/service-icons/3f6c1c0e-9a51-4c1b-8d55-0f1c2a9b7e10.jpg",
  "contentType": "image/jpeg",
  "size": 182344
}
```

### 2. Create the banner

`POST /admin/markets/{marketId}/banners`

```json
{
  "title": "Home care from LKR 2,500",
  "subtitle": "Verified caregivers near you",
  "imageKey": "service-icons/3f6c1c0e-9a51-4c1b-8d55-0f1c2a9b7e10.jpg",
  "audience": "CUSTOMER",
  "linkType": "OPTION",
  "serviceTypeId": 3,
  "optionId": 12,
  "status": "ACTIVE",
  "startsAt": "2026-11-26T00:00:00Z",
  "endsAt": "2026-12-31T18:30:00Z"
}
```

| Field | Rule |
|---|---|
| `title` | Required, up to 120 characters. Also use it as the image's accessibility label. |
| `subtitle` | Optional, up to 250 characters. |
| `imageKey` or `imageUrl` | One is required: the `key` or the `url` from step 1. The image must exist. |
| `audience` | `CUSTOMER` (default), `PROVIDER` or `ALL`. |
| `linkType` | `NONE` (default), `SERVICE`, `OPTION` or `URL`. |
| `serviceTypeId` | Required for `SERVICE` and `OPTION`. The service must be active and offered in this market. |
| `optionId` | Required for `OPTION`. The option must be active for that service. |
| `linkUrl` | Required for `URL`, and must start with `https://`. |
| `status` | `ACTIVE` (default) or `INACTIVE`. |
| `startsAt`, `endsAt` | Optional, UTC. When both are given, `endsAt` must be after `startsAt`. |
| `sortOrder` | Optional. A new banner goes last. |

Fields that do not belong to the chosen `linkType` are cleared. `201 Created` returns the admin view:

```json
{
  "bannerId": 4,
  "marketId": 1,
  "title": "Home care from LKR 2,500",
  "subtitle": "Verified caregivers near you",
  "imageKey": "service-icons/3f6c1c0e-9a51-4c1b-8d55-0f1c2a9b7e10.jpg",
  "imageUrl": "https://jobhube.s3.eu-north-1.amazonaws.com/service-icons/3f6c1c0e-9a51-4c1b-8d55-0f1c2a9b7e10.jpg",
  "audience": "CUSTOMER",
  "linkType": "OPTION",
  "serviceTypeId": 3,
  "optionId": 12,
  "linkUrl": null,
  "sortOrder": 0,
  "status": "ACTIVE",
  "startsAt": "2026-11-26T00:00:00Z",
  "endsAt": "2026-12-31T18:30:00Z",
  "visibility": "SCHEDULED",
  "createdAt": "2026-11-25T11:40:12",
  "updatedAt": "2026-11-25T11:40:12"
}
```

`visibility` tells the admin whether the app shows the banner right now:

| `visibility` | Meaning |
|---|---|
| `LIVE` | The app shows it now. |
| `SCHEDULED` | `startsAt` is in the future. |
| `ENDED` | `endsAt` has passed. |
| `INACTIVE` | It was switched off. |
| `TARGET_UNAVAILABLE` | The linked service or option is no longer active or offered in the market. |

### Other admin endpoints

| Method and path | Body | Returns |
|---|---|---|
| `GET /admin/markets/{marketId}/banners` | none | Every banner of the market, in display order. |
| `GET /admin/banners/{bannerId}` | none | One banner. |
| `PUT /admin/banners/{bannerId}` | The same body as create, replacing the whole banner | The banner. `status: null` and `sortOrder: null` keep their current values. |
| `PATCH /admin/banners/{bannerId}/status` | `{"status": "INACTIVE"}` | The banner. |
| `PUT /admin/markets/{marketId}/banners/order` | `{"bannerIds": [7, 4, 9]}`, listing every banner of the market once | The list in the new order. |
| `DELETE /admin/banners/{bannerId}` | none | `204 No Content`. |

Every change is written to the audit log (`MARKET_BANNER_CREATED`, `_UPDATED`, `_STATUS`, `_REORDERED`,
`_DELETED`).

### Errors (`400` unless noted)

| `code` | When |
|---|---|
| `BANNER_IMAGE_REQUIRED` | Neither `imageKey` nor `imageUrl` was sent. |
| `BANNER_IMAGE_INVALID` | The image is not an admin upload from `POST /uploads/images`, or is not a JPG, PNG or WebP. |
| `BANNER_IMAGE_MISSING` | No uploaded file exists with that key. |
| `BANNER_LINK_INVALID` | An id required by the `linkType` is missing, or `linkUrl` is not a valid `https://` URL. |
| `BANNER_LINK_UNAVAILABLE` | The linked service or option is not active in this market. |
| `BANNER_SCHEDULE_INVALID` | `endsAt` is not after `startsAt`. |
| `BANNER_LIMIT_REACHED` | The market already has 20 banners. |
| `BANNER_ORDER_MISMATCH` | `bannerIds` does not list every banner of the market exactly once. |
| (no code) `404` | The market or banner was not found. |
| (no code) `403` | The token is not a SUPER_ADMIN token. |

Example error:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "The linked service or option is not active in this market",
  "code": "BANNER_LINK_UNAVAILABLE"
}
```

## Storage

The banners are stored in the `market_banner` table, which is created automatically on deploy
(`ddl-auto=update`). Images are kept in S3 under `service-icons/`. Deleting a banner leaves its image file in
place.
