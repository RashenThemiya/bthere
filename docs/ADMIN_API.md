# JobHub Administration API

Base URL: `https://jobhub.pentarixlabs.com`

All endpoints require `Authorization: Bearer <access-token>`. Paginated
endpoints accept `page`, `size`, and `sort`. Page numbering starts at zero,
the default size is 20, and the maximum size is 100.

## User administration

- `GET /api/v1/admin/users?search=&status=&role=&page=0&size=20`
- `PATCH /api/v1/admin/users/{userId}/status`
- `POST /api/v1/admin/users/{userId}/reset-password`

Status body:

```json
{ "status": "INACTIVE" }
```

Allowed values are `ACTIVE`, `INACTIVE`, and `SUSPENDED`. Deactivation and
password reset revoke all active sessions. Password reset body:

```json
{ "newPassword": "new-strong-password" }
```

## Service setup

- `POST /api/v1/admin/provider-types/setup`
- `PUT /api/v1/admin/provider-types/{providerTypeId}/setup`
- `PATCH /api/v1/admin/provider-types/{providerTypeId}/status`

Each setup request includes `availabilityEnabled`. Location configuration belongs
to each service option through `locationEnabled` and `deliveryModes`.
Enable only the onboarding steps required by that service. If an enabled step
is incomplete, the provider-service remains pending. When a feature is
disabled, its provider endpoints reject updates.

## Emergency provider-service approval

- `POST /api/v1/admin/provider-service-assignments/{assignmentId}/emergency-override`
- `DELETE /api/v1/admin/provider-service-assignments/{assignmentId}/emergency-override`

```json
{
  "reason": "Emergency staffing approval",
  "expiresAt": "2026-10-06T18:00:00"
}
```

The expiry is optional. Applying and revoking an override creates audit logs.

## Market-specific services and pricing

- `POST /api/v1/admin/market-services`
- `GET /api/v1/admin/market-services?marketId=1&page=0&size=20`
- `PATCH /api/v1/admin/market-services/{offeringId}/status`
- `POST /api/v1/admin/market-services/{offeringId}/rates`
- `GET /api/v1/admin/market-services/{offeringId}/rates?page=0&size=20`
- `PUT /api/v1/admin/market-services/{offeringId}/rates/{rateId}`
- `PATCH /api/v1/admin/market-services/{offeringId}/rates/{rateId}/status`

Create market availability:

```json
{
  "marketId": 1,
  "serviceTypeId": 3
}
```

Create or update a price:

```json
{
  "billingType": "HOURLY",
  "durationMinutes": 60,
  "rate": 2500.00,
  "effectiveFrom": "2026-10-05T00:00:00",
  "effectiveTo": null
}
```

Currency is automatically taken from the selected market.

## Dashboard statistics

- `GET /api/v1/admin/dashboard/statistics`

Returns user, provider, market, service, job, payment, and paid-value totals.

## Audit logs

- `GET /api/v1/admin/audit-logs?action=&entityType=&actorId=&page=0&size=20`

Audit-log access is restricted to Super Admin.

## Paginated verification queues

- `GET /api/v1/admin/provider-documents?status=PENDING&page=0&size=20`
- `GET /api/v1/admin/provider-qualifications/skills?status=PENDING&page=0&size=20`
- `GET /api/v1/admin/provider-qualifications/certificates?status=PENDING&page=0&size=20`
- `GET /api/v1/admin/provider-qualifications/education?status=PENDING&page=0&size=20`

Allowed statuses are `PENDING`, `APPROVED`, and `REJECTED`.
# Super Admin dashboard statistics

```http
GET /api/v1/admin/dashboard/statistics
Authorization: Bearer <superadmin-or-admin-token>
```

The response preserves the original user, provider, job, and payment totals and now
also contains:

```json
{
  "serviceConfiguration": {
    "totalServiceTypes": 6,
    "activeServiceTypes": 5,
    "inactiveServiceTypes": 1,
    "totalOptions": 14,
    "activeOptions": 12,
    "inactiveOptions": 2,
    "timeBasedOptions": 10,
    "routeBasedOptions": 4,
    "oneAtATimeOptions": 7,
    "manyAtATimeOptions": 7,
    "oneToOneOptions": 8,
    "manyCustomersOneProviderOptions": 4,
    "oneCustomerManyProvidersOptions": 2,
    "providerRequirementFields": 18,
    "bookingFields": 22,
    "totalMarketOfferings": 8,
    "activeMarketOfferings": 7,
    "totalRates": 20,
    "activeRates": 18
  },
  "approvalQueues": {
    "providerServices": 3,
    "serviceOptions": 4,
    "documents": 5,
    "skills": 2,
    "certificates": 1,
    "education": 2,
    "customRequirements": 3,
    "totalPending": 20
  },
  "bookingStatistics": {
    "timeBased": 40,
    "routeBased": 10,
    "oneToOne": 35,
    "manyCustomersOneProvider": 12,
    "oneCustomerManyProviders": 3
  }
}
```

Older rows that predate scheduling/fulfillment configuration can have null values and
are intentionally not counted in a subtype until they are updated or used in a new booking.
