# Service pricing API (V1)

All admin endpoints require a Super Admin bearer token.

## Pricing structure

Pricing is configured by `market -> service -> option`. A rate without geographical
coordinates is the required default. A rate with a center point and radius overrides
the default when the booking destination/meeting point is inside that area.

### Route-based default price

`POST /api/v1/admin/market-services/{offeringId}/rates`

```json
{
  "optionId": 10,
  "billingType": "ROUTE_BASED",
  "baseFare": 300,
  "pricePerKm": 100,
  "minimumFare": 600
}
```

Formula: `max(baseFare + estimatedDistanceKm * pricePerKm, minimumFare)`.

### Route-based geographical override

```json
{
  "optionId": 10,
  "billingType": "ROUTE_BASED",
  "baseFare": 500,
  "pricePerKm": 150,
  "minimumFare": 800,
  "geographicalAreaName": "Nuwara Eliya mountain area",
  "areaLatitude": 6.9497,
  "areaLongitude": 80.7891,
  "areaRadiusKm": 25
}
```

If multiple areas contain the destination, the smallest area is used.

### Time-based price

```json
{
  "optionId": 20,
  "billingType": "HOURLY",
  "durationMinutes": 60,
  "rate": 1200
}
```

Supported time billing types:

- `HOURLY`: `rate` is charged per `durationMinutes` increment (defaults to 60).
- `DAILY`: `rate` is charged for every started 24-hour period.
- `FIXED`: one fixed `rate` per booking.

The same geographical fields can be added to create a time-price override.

## Management endpoints

- `GET /api/v1/admin/market-services/{offeringId}/rates?page=0&size=20`
- `PUT /api/v1/admin/market-services/{offeringId}/rates/{rateId}`
- `PATCH /api/v1/admin/market-services/{offeringId}/rates/{rateId}/status`

Status request:

```json
{
  "status": "INACTIVE"
}
```

## Booking result

When a customer creates a booking, the backend selects the active effective rate and
stores `rateId`, `currencyCode`, and `expectedAmount` on the booking. Route distance is
currently a straight-line estimate; a road-routing provider can replace it later.

Legacy rate requests without `optionId` remain accepted, but they are not used by the
new automatic option-level booking calculation.
