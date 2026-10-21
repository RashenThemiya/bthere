# Separate service icons and map marker icons

`icon` remains the asset for cards, lists and selection controls. `mapMarkerIcon`
is a separate asset used on maps. Provider photos do not determine marker icons.

## Admin configuration

Requires SUPER_ADMIN. The existing endpoints accept the new optional fields:

| Endpoint | New properties |
| --- | --- |
| `POST /api/v1/admin/provider-types` | `mapMarkerIcon` |
| `POST /api/v1/admin/provider-types/setup` | `mapMarkerIcon` |
| `PUT /api/v1/admin/provider-types/{serviceTypeId}/setup` | `mapMarkerIcon` |
| `POST /api/v1/admin/provider-types/{serviceTypeId}/options` | `mapMarkerIcon`, `mapMarkerFieldId` |
| `PUT /api/v1/admin/provider-types/{serviceTypeId}/options/{optionId}` | `mapMarkerIcon`, `mapMarkerFieldId` |
| `POST /api/v1/admin/provider-types/{serviceTypeId}/options/{optionId}/link` | `mapMarkerFieldId` alongside `displayOrder` |

Send the existing required properties in create/setup/update requests. Example
additional properties for Ride:

```json
{
  "mapMarkerIcon": {
    "key": "markers/ride.png",
    "url": "https://cdn.example.com/markers/ride.png",
    "altText": "Ride",
    "displayOrder": 0
  },
  "mapMarkerFieldId": 45
}
```

`mapMarkerFieldId` identifies the existing Vehicle type provider requirement.
It must be active, customer-visible, searchable, unconditional, SELECT or
MULTI_SELECT, and apply to that main service and option. Invalid IDs return 400.
Configure that field first, then set the marker field ID on the option. No marker
field ID is required for the option or main-service fallback icons to work.

Main-service and option read responses now return `mapMarkerIcon`. Option read
responses additionally return the requested service link's `mapMarkerFieldId`.
The shared catalog's `services` entries also include each link's marker field ID.

Because requirement fields belong to a main service, `mapMarkerFieldId` is stored
on the service–option link. A shared Ride option may use field 45 in Caregiver and
field 55 in another main service. Its option-level marker asset remains shared.

On full setup/option updates, omitted or null `mapMarkerIcon` clears that marker;
omitted or null `mapMarkerFieldId` on option PUT clears the selector. On link POST,
omitting the selector preserves an existing selector; a new link defaults to none.
The normal full-update behavior for other service content remains in effect.

## Choice marker assets

Use the existing custom-field create/update API's `options` array. A choice can
have both a card icon and an independently configured map marker:

```json
{
  "options": [
    {
      "value": "CAR",
      "label": "Car",
      "icon": {
        "key": "icons/car.png",
        "url": "https://cdn.example.com/icons/car.png"
      },
      "mapMarkerIcon": {
        "key": "markers/car.png",
        "url": "https://cdn.example.com/markers/car.png",
        "altText": "Car marker",
        "displayOrder": 0
      }
    }
  ]
}
```

Both assets require nonblank `key` and `url`. `altText` and `displayOrder` are
optional. String choices such as `"CAR"` remain supported without a custom marker.
Choice objects are returned in the existing field definitions and customer filter
schema, including `mapMarkerIcon`.

## Customer search

Use the existing `POST /api/v1/customers/me/provider-search` request (CUSTOMER role).
To select Car, include this filter alongside the existing service, option, time
and location properties:

```json
{
  "providerFilters": [
    {"fieldId": 45, "value": ["CAR"]}
  ]
}
```

Every result in `candidates` now includes `mapMarker`. Example response excerpt:

```json
{
  "providerId": 15,
  "optionId": 12,
  "locationLabel": "APPROXIMATE_SERVICE_AREA",
  "mapPoint": {"latitude": 6.93, "longitude": 79.86},
  "mapMarker": {
    "source": "CHOICE",
    "icon": {
      "key": "markers/car.png",
      "url": "https://cdn.example.com/markers/car.png",
      "altText": "Car marker",
      "displayOrder": 0
    },
    "fieldId": 45,
    "choiceValue": "CAR"
  }
}
```

Marker fallback order:

[Combination marker rules](COMBINATION_MAP_MARKERS_API.md) take priority when
configured. If no combination rule matches, use the following existing fallback:

1. `CHOICE`: marker of the single selected value in the configured marker field.
2. `OPTION`: service option's `mapMarkerIcon`.
3. `SERVICE`: main service's `mapMarkerIcon`.
4. `DEFAULT`: `icon`, `fieldId` and `choiceValue` are null; render a built-in map pin.

No selection, multiple selected values, a plain string choice, or a choice without
a marker uses the next fallback. Another field's marker is never chosen implicitly.
Card icons are never substituted for marker icons. All candidates for the same
search use the same resolved service/option/choice marker; provider attributes
still determine which providers match the search. Existing provider identifiers,
details, approximate service-area coordinates, exact public business locations
and null ONLINE map points retain their existing behavior. This does not add live
tracking or change availability, filtering, pricing or booking selections.

## Deployment

Apply [MAP_MARKER_ICONS_SCHEMA.sql](MAP_MARKER_ICONS_SCHEMA.sql) once before
deploying to an existing database. Existing service and option media tables store
the new marker role without additional icon columns. Existing choice JSON needs
no migration. Configure markers explicitly; old card icons are not copied.
