# Fulfillment models and capacity (V1)

Fulfillment is configured when an admin creates or updates a service option:

`POST /api/v1/admin/provider-types/{serviceTypeId}/options`

`PUT /api/v1/admin/provider-types/{serviceTypeId}/options/{optionId}`

## Booking mode

Every option supports one of two simple booking modes:

- `ONE_AT_A_TIME`: one booking blocks that provider across all options of the same
  main service. Use this for human services such as caregivers.
- `MANY_AT_A_TIME`: bookings are counted independently by option and, for fixed
  provider locations, independently by location. Use this for parking and EV charging.

## One customer and one provider

```json
{
  "code": "HOME_CARE",
  "name": "Home care",
  "schedulingModel": "TIME_BASED",
  "locationEnabled": true,
  "deliveryModes": ["PROVIDER_TO_CUSTOMER"],
  "fulfillmentModel": "ONE_TO_ONE",
  "bookingMode": "ONE_AT_A_TIME"
}
```

The provider cannot have another active overlapping booking for this option.

## Many customers and one provider

```json
{
  "code": "CAR_PARKING",
  "name": "Car parking",
  "schedulingModel": "TIME_BASED",
  "locationEnabled": true,
  "deliveryModes": ["CUSTOMER_TO_PROVIDER"],
  "fulfillmentModel": "MANY_CUSTOMERS_ONE_PROVIDER",
  "defaultCapacity": 50,
  "bookingMode": "MANY_AT_A_TIME"
}
```

The provider can override capacity for each fixed location:

```json
{
  "locationName": "Colombo parking",
  "latitude": 6.9271,
  "longitude": 79.8612,
  "capacity": 50
}
```

An overlapping booking is rejected when the location capacity (or option default
capacity) is reached.

## One customer and many providers

```json
{
  "code": "PATIENT_TRANSFER_TEAM",
  "name": "Patient transfer team",
  "schedulingModel": "TIME_BASED",
  "locationEnabled": true,
  "deliveryModes": ["PROVIDER_TO_CUSTOMER"],
  "fulfillmentModel": "ONE_CUSTOMER_MANY_PROVIDERS",
  "requiredProviderCount": 3
}
```

The booking supplies exactly three distinct approved providers:

```json
{
  "serviceTypeId": 1,
  "optionId": 12,
  "providerIds": [21, 34, 55],
  "deliveryMode": "PROVIDER_TO_CUSTOMER",
  "startDatetime": "2026-10-10T08:00:00",
  "expectedEndDatetime": "2026-10-10T10:00:00",
  "meetingPoint": {
    "name": "Customer home",
    "addressLine1": "10 Main Street",
    "latitude": 6.9271,
    "longitude": 79.8612
  }
}
```

Every selected provider must be approved, active for the option, and free during the
requested time. `providerId` is still accepted for old one-provider frontend requests.

For V1, `ROUTE_BASED` options support `ONE_TO_ONE` only. The multi-customer and
multi-provider models are available for `TIME_BASED` options.
