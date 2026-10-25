# Combination map marker rules

23 October update: rules are option-owned and identical across linked services.
See [FULLY_SHARED_OPTIONS_API.md](FULLY_SHARED_OPTIONS_API.md); prior service-local
scope descriptions below are superseded.

Markers can depend on one or multiple existing provider requirement choices, such as
Car or Car + Electric. Rules are owned by the shared service option and are independent
of pricing rules. All linked services use the same option marker rules.

## Vehicle markers independent of pricing variants

Marker conditions may use a subset of the option's searchable choice fields.
For example, pricing can use VEHICLE_TYPE + GENDER while markers use only
VEHICLE_TYPE. Create one CAR marker rule and one BIKE marker rule; do not duplicate
each rule for MALE and FEMALE. Extra search filters do not prevent a rule matching.
Gender can be omitted, single-selected or multi-selected without changing a
vehicle-only rule's marker.

Use the option-owned admin endpoint (the service-scoped endpoints below remain
available for linked services):

```http
POST /api/v1/admin/service-options/12/map-marker-rules
```

```json
{
  "conditions": [{"fieldId": 45, "choiceValue": "CAR"}],
  "mapMarkerIcon": {
    "key": "markers/car.png",
    "url": "https://cdn.example.com/markers/car.png",
    "altText": "Car"
  },
  "status": "ACTIVE"
}
```

Field IDs and asset URLs are illustrative. Repeat for each vehicle choice with its
own icon. GET lists rules; PUT `/{ruleId}` replaces a rule; DELETE `/{ruleId}` removes
it. To simplify an existing CAR + MALE rule, PUT the CAR-only conditions and icon,
and remove or deactivate redundant gender-specific rules. More specific active
rules still take precedence, so leaving them active may override the vehicle rule.

Customer `POST /api/v1/customers/me/provider-search` can send both vehicle and gender
filters. Each candidate's existing `mapMarker` contains a single resolved icon;
its `conditions` contain only the conditions of the matched marker rule. A
one-condition marker rule still returns `source: "COMBINATION"` for compatibility.
No new response field or database migration is required for this configuration.
Quote/booking `pricingSelections` remain independent and must still include all
applicable pricing fields, including gender when configured for pricing.

## Admin API

Requires SUPER_ADMIN. Base path:

`/api/v1/admin/provider-types/{serviceTypeId}/options/{optionId}/map-marker-rules`

| Method | Path | Response |
| --- | --- | --- |
| POST | Base path | 201, created rule |
| GET | Base path | 200, array of active and inactive rules |
| PUT | Base path + `/{ruleId}` | 200, full replacement |
| DELETE | Base path + `/{ruleId}` | 204, no body |

Create/update request:

```json
{
  "conditions": [
    {"fieldId": 45, "choiceValue": "CAR"},
    {"fieldId": 47, "choiceValue": "ELECTRIC"}
  ],
  "mapMarkerIcon": {
    "key": "markers/electric-car.png",
    "url": "https://cdn.example.com/markers/electric-car.png",
    "altText": "Electric car",
    "displayOrder": 0
  },
  "status": "ACTIVE"
}
```

Response:

```json
{
  "ruleId": 7,
  "serviceTypeId": 2,
  "optionId": 12,
  "conditions": [
    {"fieldId": 45, "choiceValue": "CAR"},
    {"fieldId": 47, "choiceValue": "ELECTRIC"}
  ],
  "mapMarkerIcon": {
    "key": "markers/electric-car.png",
    "url": "https://cdn.example.com/markers/electric-car.png",
    "altText": "Electric car",
    "displayOrder": 0
  },
  "status": "ACTIVE"
}
```

Conditions and mapMarkerIcon are required on POST and PUT. Status defaults to
ACTIVE when omitted; use INACTIVE in a full PUT to disable a rule. Conditions
are ANDed, must contain 1–10 distinct field IDs and use exactly one choice value
per field. Fields must be active, searchable, customer-visible, unconditional
SELECT/MULTI_SELECT provider requirements applicable to that service and option.
Values use the existing stable choice values, not labels or icon names.

Invalid fields/choices or duplicate field IDs return 400. Wrong service/option/rule
scope returns 404. Equally specific active rules that can overlap return 409,
including rules using different fields that could be selected together. More
specific rules are allowed. For example, Car and Car + Electric can coexist;
Car and Electric as separate one-condition rules conflict. Writes lock the shared
option row while validating and saving, preventing simultaneous conflicting edits.
Reactivating a rule performs the same validation. DELETE removes only that rule.

## Customer search and precedence

Continue using `POST /api/v1/customers/me/provider-search` and its existing filters:

```json
{
  "providerFilters": [
    {"fieldId": 45, "value": ["CAR"]},
    {"fieldId": 47, "value": ["ELECTRIC"]}
  ]
}
```

This is a request excerpt; send all existing required search fields as well.
Each condition needs exactly one selected value in its field's filter. Multiple
values such as `["ELECTRIC", "PETROL"]` do not select a particular fuel variant.
Only valid public choices participate in marker matching. Stale, private or
deactivated field choices cannot supply a combination marker.

Each candidate returns the resolved marker. Combination example:

```json
{
  "mapMarker": {
    "source": "COMBINATION",
    "icon": {
      "key": "markers/electric-car.png",
      "url": "https://cdn.example.com/markers/electric-car.png",
      "altText": "Electric car",
      "displayOrder": 0
    },
    "fieldId": null,
    "choiceValue": null,
    "ruleId": 7,
    "conditions": [
      {"fieldId": 45, "choiceValue": "CAR"},
      {"fieldId": 47, "choiceValue": "ELECTRIC"}
    ]
  }
}
```

The active matching rule with the most conditions wins. A runtime tie returns
409 rather than selecting an arbitrary marker. If no rule matches, the existing
fallback remains: selected single-choice marker → option marker → main-service
marker → default pin. Single-choice fallback still uses `mapMarkerFieldId` on the
shared service option; combination rules do not require that setting. Non-rule
markers return `ruleId: null` and `conditions: []` along with their existing fields.

These are separate map marker assets. Card icons, choice icons, filtering, provider
details, location precision, pricing and booking behavior retain their existing
roles. Rules use the customer's service/option/choice selection, not provider photos.

## Deployment

Apply [COMBINATION_MAP_MARKERS_SCHEMA.sql](COMBINATION_MAP_MARKERS_SCHEMA.sql)
before deployment. Existing marker configuration needs no backfill. The previous
shared-option and map-marker migrations remain prerequisites for those features.
