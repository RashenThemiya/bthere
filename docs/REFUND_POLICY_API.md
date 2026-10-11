# Refund Policy API

Base URL: `https://jobhub.pentarixlabs.com`

Refund policies belong to a market. A policy with `offeringId: null` is the
market default. A policy with an `offeringId` is an override for that market
service. All effective timestamps are ISO-8601 UTC instants.

## Resolution order

For a booking or UI request, the backend resolves the policy in this order:

1. Effective active policy for the requested market-service offering.
2. Effective active default policy for the market.
3. `404 Not Found` when neither exists.

The resolved policy should be snapshotted when a booking is created so future
policy versions do not change an existing booking's rules.

## Policy lifecycle

- `DRAFT`: editable but never used for bookings.
- `ACTIVE`: eligible for resolution inside its effective UTC interval.
- `INACTIVE`: retained for history and no longer eligible for resolution.

Creating a policy assigns the next version for its market and status `DRAFT`.
Only drafts can be edited or activated. Active policies cannot be modified;
create a new version instead.

## Shared request payload

```json
{
  "offeringId": null,
  "name": "Sri Lanka Default Refund Policy",
  "description": "Default cancellation rules for Sri Lanka",
  "noProviderRefundPercentage": 100,
  "providerCancellationRefundPercentage": 100,
  "providerNoShowRefundPercentage": 100,
  "serviceFailureRefundPercentage": 100,
  "customerNoShowRefundPercentage": 0,
  "platformFeeRefundable": true,
  "processingFeeRefundable": false,
  "effectiveFrom": "2026-11-01T00:00:00Z",
  "effectiveTo": null,
  "tiers": [
    {
      "name": "Early cancellation",
      "minimumMinutesBeforeStart": 1440,
      "refundPercentage": 100,
      "cancellationFee": 0,
      "priority": 1
    },
    {
      "name": "Medium cancellation",
      "minimumMinutesBeforeStart": 240,
      "refundPercentage": 50,
      "cancellationFee": 0,
      "priority": 2
    },
    {
      "name": "Late cancellation",
      "minimumMinutesBeforeStart": 0,
      "refundPercentage": 0,
      "cancellationFee": 0,
      "priority": 3
    }
  ]
}
```

### Payload meaning

| Property | Meaning |
|---|---|
| `offeringId` | `null` for the market default; set it for a service override. |
| `noProviderRefundPercentage` | Refund when matching cannot find a provider. |
| `providerCancellationRefundPercentage` | Refund after an accepted provider cancels. |
| `providerNoShowRefundPercentage` | Refund when the provider does not attend. |
| `serviceFailureRefundPercentage` | Refund when the booked service cannot be delivered. |
| `customerNoShowRefundPercentage` | Refund when the customer does not attend. |
| `platformFeeRefundable` | Whether the platform fee is included in the refund. |
| `processingFeeRefundable` | Whether the payment-processing fee is included. |
| `effectiveFrom` / `effectiveTo` | UTC interval in which an active policy can resolve. |
| `minimumMinutesBeforeStart` | Minimum cancellation lead time for the tier. |
| `refundPercentage` | Refund percentage for a cancellation matching the tier. |
| `cancellationFee` | Fixed fee in the market currency, applied with this tier. |
| `priority` | Display/evaluation order; must be unique inside a policy. |

Percentages accept values from `0` through `100`. Amounts and time thresholds
cannot be negative. Every policy requires a catch-all tier where
`minimumMinutesBeforeStart` is `0`. Thresholds and priorities must be unique.
`effectiveTo`, when supplied, must be after `effectiveFrom`.

Currency is not accepted from the client. The backend copies it from the
market, preventing a policy from using a currency different from its market.

## Admin endpoints

All admin endpoints require a `SUPER_ADMIN` bearer token.

### Create a draft

```http
POST /api/v1/admin/markets/{marketId}/refund-policies
```

Body: shared request payload.

Success: `201 Created`.

### List all market policy versions

```http
GET /api/v1/admin/markets/{marketId}/refund-policies
```

Success: `200 OK` with an array ordered by descending version.

### Get one policy

```http
GET /api/v1/admin/markets/{marketId}/refund-policies/{policyId}
```

### Replace a draft

```http
PUT /api/v1/admin/markets/{marketId}/refund-policies/{policyId}
```

Body: the complete shared request payload. This is full replacement, not a
partial update. It returns `409 Conflict` unless the policy is `DRAFT`.

### Activate a draft

```http
POST /api/v1/admin/markets/{marketId}/refund-policies/{policyId}/activate
```

No request body. The previous active policy for the same scope is ended at the
new policy's `effectiveFrom`. A scheduled future policy can therefore coexist
with the current policy without overlapping effective intervals.

### Deactivate an active policy

```http
POST /api/v1/admin/markets/{marketId}/refund-policies/{policyId}/deactivate
```

No request body. The backend sets `effectiveTo` to the current UTC instant when
it is absent or still in the future, and changes status to `INACTIVE`.

## Resolved policy endpoint

Requires an authenticated user.

Market default:

```http
GET /api/v1/markets/{marketId}/refund-policy
```

Service override with fallback to market default:

```http
GET /api/v1/markets/{marketId}/refund-policy?offeringId={offeringId}
```

## Response example

```json
{
  "policyId": 12,
  "marketId": 1,
  "offeringId": null,
  "version": 1,
  "name": "Sri Lanka Default Refund Policy",
  "description": "Default cancellation rules for Sri Lanka",
  "currencyCode": "LKR",
  "noProviderRefundPercentage": 100.00,
  "providerCancellationRefundPercentage": 100.00,
  "providerNoShowRefundPercentage": 100.00,
  "serviceFailureRefundPercentage": 100.00,
  "customerNoShowRefundPercentage": 0.00,
  "platformFeeRefundable": true,
  "processingFeeRefundable": false,
  "status": "ACTIVE",
  "effectiveFrom": "2026-11-01T00:00:00Z",
  "effectiveTo": null,
  "tiers": [
    {
      "tierId": 31,
      "name": "Early cancellation",
      "minimumMinutesBeforeStart": 1440,
      "refundPercentage": 100.00,
      "cancellationFee": 0.00,
      "priority": 1
    }
  ],
  "createdAt": "2026-10-12T10:00:00",
  "updatedAt": "2026-10-12T10:05:00"
}
```

## Error behavior

- `400 Bad Request`: bean validation, invalid time interval, duplicate tier
  threshold/priority, missing catch-all tier.
- `404 Not Found`: market, market offering, policy, or resolved active policy
  does not exist.
- `409 Conflict`: edit/activate/deactivate is invalid for the current lifecycle
  status, or activation would produce an invalid schedule.
