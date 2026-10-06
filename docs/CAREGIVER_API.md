# Caregiver V1 API

Caregiver V1 uses reusable service options and admin-managed dynamic booking
fields. Provider verification and customer booking data remain separate.

## Admin service setup

Create or update Caregiver through the normal service setup endpoint and use:

```json
{
  "availabilityEnabled": true,
  "optionsRequired": true,
  "minimumOptionSelections": 1,
  "maximumOptionSelections": null
}
```

## Admin service options

- `POST /api/v1/admin/provider-types/{serviceTypeId}/options`
- `GET /api/v1/admin/provider-types/{serviceTypeId}/options`
- `PUT /api/v1/admin/provider-types/{serviceTypeId}/options/{optionId}`
- `PATCH /api/v1/admin/provider-types/{serviceTypeId}/options/{optionId}/status`

Create each option separately:

```json
{
  "code": "HOSPITAL_CARE",
  "name": "Hospital Care",
  "description": "Care delivered while the customer is hospitalized",
  "displayOrder": 2,
  "schedulingModel": "TIME_BASED",
  "routeAverageSpeedKmh": null,
  "locationEnabled": true,
  "deliveryModes": ["PROVIDER_TO_CUSTOMER"]
}
```

Locations are configured per option and delivery mode. They are not configured
on the main Caregiver service. `ONLINE` options cannot use physical locations.

Provider location endpoints:

- `GET /api/v1/providers/me/services/{assignmentId}/options/{optionId}/delivery-modes/{deliveryMode}/service-areas`
- `PUT /api/v1/providers/me/services/{assignmentId}/options/{optionId}/delivery-modes/{deliveryMode}/service-areas`

For `PROVIDER_TO_CUSTOMER`, every provider location requires `radiusKm`. For
`CUSTOMER_TO_PROVIDER`, locations are fixed points and `radiusKm` must be null.
`ONLINE` does not accept provider locations.

The option response includes `deliveryModeConfigurations`, which tells the
frontend whether to show a coverage radius, provider-location selector, customer
meeting-point form, or no location control.

## Customer booking

- `GET /api/v1/provider-types/{serviceTypeId}/booking-schema?optionId={optionId}`
- `POST /api/v1/customers/me/bookings`
- `GET /api/v1/customers/me/bookings`

For `PROVIDER_TO_CUSTOMER`, submit `meetingPoint`; the backend checks its
coordinates against all provider coverage circles. For `CUSTOMER_TO_PROVIDER`,
submit `providerLocationId`; it must belong to the selected provider, option,
and delivery mode. For `ONLINE`, neither location property is accepted.

The booking request contains an `answers` array. Each answer uses an
admin-managed booking `fieldId` and JSON `value`. The backend combines common
fields with the selected option's fields, validates required and conditional
fields, data types, select options and upload counts, and stores field metadata
with the job so later admin changes do not alter old bookings.

## Scheduling models

V1 supports `TIME_BASED` and `ROUTE_BASED` per service option.

- `TIME_BASED` requires `startDatetime` and `expectedEndDatetime`.
- `ROUTE_BASED` requires `requestedPickupTime`, `pickup`, and `destination`.

Route options must use `PROVIDER_TO_CUSTOMER` with locations enabled. The
pickup must fall inside a provider coverage radius. Until a road-routing
provider is integrated, distance is a straight-line geographical estimate and
duration uses `routeAverageSpeedKmh` (default 40 km/h).

Recommended option codes are `HOME_CARE`, `HOSPITAL_CARE`, `OVERNIGHT_CARE`,
and `LIVE_IN_CARE`.

## Provider option selection

- `GET /api/v1/provider-types/{serviceTypeId}/options`
- `GET /api/v1/providers/me/services/{assignmentId}/options`
- `PUT /api/v1/providers/me/services/{assignmentId}/options`

```json
{ "optionIds": [1, 2] }
```

New selections enter `PENDING` verification. Deselecting an option removes the
provider's ability to receive jobs for that option.

## Option verification

- `GET /api/v1/admin/provider-service-options?status=PENDING&page=0&size=20`
- `PATCH /api/v1/admin/provider-service-options/{providerOptionId}/review`

```json
{ "status": "APPROVED", "rejectionReason": null }
```

When options are required, the provider-service remains pending until the
configured minimum number of selected options has been approved.

## Optional provider languages

- `GET /api/v1/providers/me/services/{assignmentId}/languages`
- `PUT /api/v1/providers/me/services/{assignmentId}/languages`

```json
{ "languages": ["SINHALA", "ENGLISH", "TAMIL"] }
```

Languages are optional and do not block approval.

## Fixed customer booking contracts

Booking creation is the next job-module step. V1 will validate a fixed payload
according to the selected option code:

- `HOME_CARE`: service address, patient details, emergency contact, access and care notes.
- `HOSPITAL_CARE`: hospital name/address, ward, bed/room, patient details, hospital contact and care notes.
- `OVERNIGHT_CARE`: address, patient details, start/end time, emergency contact and overnight notes.
- `LIVE_IN_CARE`: address, patient details, start/end dates, accommodation details and care notes.

Admin may activate or deactivate each option, but does not dynamically build
these booking forms in V1.
