# Cancelling paid bookings and refunds

Covers booking-request bookings (CARD or WALLET) that are paid but not started (status `CONFIRMED`).
Implemented locally; not deployed.

- **Refunds always go to the customer's wallet**, immediately, for card and wallet payments alike.
- **Customer cancels:** the refund policy decides the refund. The amount kept is split between the
  provider and the platform using the policy's `retainedProviderSharePercentage`.
- **Provider cancels:** the customer gets 100% back; the provider and the platform keep nothing.
- Unpaid requests keep using the existing `POST /booking-requests/{id}/cancel`.
- After the start code (`IN_PROGRESS`) a booking cannot be cancelled.

## Endpoints

| # | Method | Path | Role |
|---|---|---|---|
| 1 | GET | `/api/v1/customers/me/bookings/{jobId}/cancellation-preview` | CUSTOMER |
| 2 | POST | `/api/v1/customers/me/bookings/{jobId}/cancel` | CUSTOMER |
| 3 | GET | `/api/v1/customers/me/bookings/{jobId}/cancellation` | CUSTOMER |
| 4 | POST | `/api/v1/providers/me/bookings/{jobId}/cancel` | SERVICE_PROVIDER |
| 5 | GET | `/api/v1/providers/me/bookings/{jobId}/cancellation` | SERVICE_PROVIDER |
| 6 | GET | `/api/v1/admin/bookings/{jobId}/cancellation` | SUPER_ADMIN |
| 7 | — | refund policy gains `retainedProviderSharePercentage` | Changed |

## 1. Preview (customer)

Shows what cancelling now would do; nothing is saved.

```json
{
  "jobId": 70, "cancelledBy": "CUSTOMER", "currencyCode": "LKR",
  "paidAmount": 10000.00, "refundAmount": 4800.00, "refundPercentage": 50.00, "cancellationFee": 200.00,
  "retainedAmount": 5200.00, "providerAmount": 3640.00, "platformAmount": 1560.00,
  "platformFeeRefunded": true, "refundDestination": "WALLET", "minutesBeforeStart": 300,
  "refundPolicyId": 3, "refundPolicyVersion": 2, "refundTierName": "Two hours"
}
```

Show the customer: "You will get LKR 4,800 back to your wallet. LKR 5,200 is kept (Two hours rule)."

## 2. Cancel (customer)

```json
{"reason": "Plans changed", "note": "optional, up to 1000", "expectedRefundAmount": 4800.00}
```

`reason` is required (up to 100). Send the preview's `refundAmount` as `expectedRefundAmount`: if
the amount changed meanwhile (a tier boundary passed), the call returns 409 and nothing changes.
Repeating the call returns the saved cancellation.

**200**
```json
{
  "cancellationId": 12, "jobId": 70, "requestId": 9, "cancelledBy": "CUSTOMER", "cancelledByUserId": 5,
  "reason": "Plans changed", "note": null, "currencyCode": "LKR",
  "paidAmount": 10000.00, "refundAmount": 4800.00, "refundPercentage": 50.00, "cancellationFee": 200.00,
  "providerAmount": 3640.00, "platformAmount": 1560.00, "refundDestination": "WALLET",
  "minutesBeforeStart": 300, "refundPolicyId": 3, "refundPolicyVersion": 2, "refundTierName": "Two hours",
  "walletOperationId": 88, "paymentStatus": "PARTIALLY_REFUNDED", "createdAt": "2026-11-10T10:15:00"
}
```

`paymentStatus`: `REFUNDED` (all back), `PARTIALLY_REFUNDED`, or `CANCELLATION_SETTLED` (nothing back).

What happens: booking-request and job become `CANCELLED`, the provider's slot is freed, live
tracking ends, the start code stops working, the provider gets `BOOKING_CANCELLED_BY_CUSTOMER`
and the customer gets `BOOKING_CANCELLED` in the booking events feed.

## 4. Cancel (provider)

Same body (`expectedRefundAmount` is ignored). The customer is refunded 100% to their wallet and
receives `BOOKING_CANCELLED`. Works even when no refund policy exists.

## 3, 5, 6. Read a cancellation

Return the body from #2. 404 when the booking has no cancellation.

## Errors

| Status | When |
|---|---|
| 400 | missing `reason`, note too long |
| 404 | booking not found or not yours; provider not assigned to it |
| 409 | `"Only a paid booking that has not started can be cancelled"` |
| 409 | `"No refund policy is configured for this service; contact support to cancel"` (customer only) |
| 409 | `"The refund amount changed to {amount}; review it and try again"` |
| 409 | wallet/ledger checks (e.g. card payment not verified, mixed funding modes) |

## How the refund is calculated (customer cancels)

1. **Policy:** the one in force when the booking was confirmed (`refundPolicyId` saved on the
   booking). Bookings confirmed before this change use today's policy for the market and service.
2. **Tier:** minutes from now to the booking start (pickup for rides). The tier with the largest
   `minimumMinutesBeforeStart` that is still reached applies. After the start time, the `0` tier applies.
3. **Refund** = (paid amount, or paid minus platform fee when `platformFeeRefundable` is false)
   × tier `refundPercentage` − tier `cancellationFee`, never below 0.
4. **Kept** = paid − refund. Provider gets kept × `retainedProviderSharePercentage`; the platform gets
   the rest. When the percentage is not set, the kept amount is split like a normal booking
   (provider earnings ÷ paid amount).

Example: paid 10,000, platform fee 1,500, tier 50% with fee 200, platform fee refundable,
provider share 70% → refund 4,800; kept 5,200 → provider 3,640, platform 1,560.

## Money movement (wallet ledger)

| Payment | Debit | Credits |
|---|---|---|
| WALLET | customer `RESERVED` (paid amount) | customer `AVAILABLE` (refund), provider `AVAILABLE` (share), `PLATFORM_FEE` |
| CARD | `PAYHERE_CLEARING` (paid amount) | same as above |

One wallet operation per booking (`BOOKING:{jobId}:CANCEL`, type `WALLET_BOOKING_CANCEL` /
`CARD_BOOKING_CANCEL`); repeats are no-ops. Card refunds are not sent back to the card; the customer
can spend the wallet balance or withdraw it.

## Super Admin: refund policy field

`POST/PUT /api/v1/admin/markets/{marketId}/refund-policies[/{policyId}]` accept and return:

```json
{"retainedProviderSharePercentage": 70.00}
```

0–100, optional. Only DRAFT policies can be edited; create a new version to change it.

## Schema

Hibernate `ddl-auto=update` applies these. See [CANCELLATION_REFUND_SCHEMA.sql](CANCELLATION_REFUND_SCHEMA.sql).
