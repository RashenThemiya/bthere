# Service media and responsibilities

Media and responsibilities are managed through the existing service setup and
service-option create/update APIs. Image bytes are uploaded first with the
existing upload API; its returned `key` and `url` are then stored in the JSON
payload.

## Upload an image

```http
POST /api/v1/uploads/images
Authorization: Bearer <SUPER_ADMIN token>
Content-Type: multipart/form-data

file: <JPG, PNG or WebP, maximum 10 MB>
```

Success: `201 Created`.

```json
{
  "key": "service-icons/550e8400-e29b-41d4-a716-446655440000.jpg",
  "url": "https://jobhube.s3.eu-north-1.amazonaws.com/service-icons/550e8400-e29b-41d4-a716-446655440000.jpg",
  "contentType": "image/jpeg",
  "size": 245120
}
```

## Service setup endpoints

```http
POST /api/v1/admin/provider-types/setup
PUT  /api/v1/admin/provider-types/{serviceTypeId}/setup
```

The existing setup payload now includes these additive content properties:

```json
{
  "name": "Home Nursing",
  "description": "Professional nursing services at home",
  "iconUrl": "https://jobhube.s3.eu-north-1.amazonaws.com/service-icons/nursing.png",
  "availabilityEnabled": true,
  "serviceAreasEnabled": true,
  "optionsRequired": true,
  "minimumOptionSelections": 1,
  "maximumOptionSelections": 5,
  "documents": [
    {
      "documentTypeId": 1,
      "optionId": null,
      "required": true,
      "requiresApproval": true
    }
  ],
  "skills": [],
  "certificates": { "level": "OPTIONAL", "requiresApproval": false },
  "education": { "level": "OPTIONAL", "requiresApproval": false },
  "icon": {
    "key": "service-icons/nursing.png",
    "url": "https://jobhube.s3.eu-north-1.amazonaws.com/service-icons/nursing.png",
    "altText": "Home nursing icon",
    "displayOrder": 0
  },
  "coverImage": {
    "key": "service-icons/nursing-cover.jpg",
    "url": "https://jobhube.s3.eu-north-1.amazonaws.com/service-icons/nursing-cover.jpg",
    "altText": "Home nursing",
    "displayOrder": 0
  },
  "gallery": [
    {
      "key": "service-icons/nursing-gallery.jpg",
      "url": "https://jobhube.s3.eu-north-1.amazonaws.com/service-icons/nursing-gallery.jpg",
      "altText": "Nurse caring for a patient",
      "displayOrder": 1
    }
  ],
  "responsibilities": [
    {
      "responsibleParty": "PROVIDER",
      "title": "Arrive on time",
      "description": "Arrive at the confirmed service time.",
      "required": true,
      "displayOrder": 1
    }
  ]
}
```

`iconUrl` remains for backward-compatible clients. When `icon` is supplied,
the backend synchronizes `iconUrl` to `icon.url`.

## Service-option endpoints

```http
POST /api/v1/admin/provider-types/{serviceTypeId}/options
PUT  /api/v1/admin/provider-types/{serviceTypeId}/options/{optionId}
```

The existing option payload now includes `icon`, `coverImage`, `gallery`, and
`responsibilities` with the same structures used by service setup.

```json
{
  "code": "HOME_CARE",
  "name": "Home Care",
  "description": "Care provided at the customer's home",
  "displayOrder": 1,
  "schedulingModel": "TIME_BASED",
  "routeAverageSpeedKmh": null,
  "locationEnabled": true,
  "deliveryModes": ["PROVIDER_TO_CUSTOMER"],
  "fulfillmentModel": "ONE_TO_ONE",
  "defaultCapacity": 1,
  "requiredProviderCount": 1,
  "bookingMode": "ONE_AT_A_TIME",
  "providerSelectionMode": "OPEN_REQUEST",
  "pricingOwner": "ADMIN",
  "allowedPaymentMethods": ["CARD", "WALLET"],
  "icon": {
    "key": "service-icons/home-care.png",
    "url": "https://jobhube.s3.eu-north-1.amazonaws.com/service-icons/home-care.png",
    "altText": "Home care icon",
    "displayOrder": 0
  },
  "coverImage": null,
  "gallery": [],
  "responsibilities": [
    {
      "responsibleParty": "CUSTOMER",
      "title": "Provide access",
      "description": "Ensure the provider can access the service location.",
      "required": true,
      "displayOrder": 1
    }
  ]
}
```

## Replacement semantics

The PUT endpoints are complete replacements:

- `icon: null` removes structured icon metadata.
- `coverImage: null` removes the cover image.
- `gallery: []` removes all gallery images.
- `responsibilities: []` removes all responsibilities.
- `gallery` and `responsibilities` are required arrays on create and PUT.
- Media keys must be unique inside the parent service or option.
- Gallery display orders must be unique.
- Responsibility display orders must be unique.

## Responsibility enum

`responsibleParty` accepts exactly:

```text
PROVIDER
CUSTOMER
PLATFORM
```

## Responses

Existing service setup, provider-type, and service-option responses now include:

```json
{
  "icon": { "key": "...", "url": "...", "altText": "...", "displayOrder": 0 },
  "coverImage": null,
  "gallery": [],
  "responsibilities": []
}
```

The database stores these collections in `service_type_media`,
`service_type_responsibility`, `service_option_media`, and
`service_option_responsibility`. Hibernate creates the tables on deployment
under the current schema-update configuration.
