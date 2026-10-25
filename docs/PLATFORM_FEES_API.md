# Platform commission percentages

Implemented locally, 25 October 2026. Not deployed. Apply
[PLATFORM_FEES_SCHEMA.sql](PLATFORM_FEES_SCHEMA.sql) once before starting the updated
application when automatic schema updates are disabled. The booking-request
tables must already exist. Existing booking amounts and fee records are preserved.

The platform percentage is commission deducted from the configured fare:

```text
Customer fare = 1,000.00
Platform fee percentage = 15.00
Platform fee = 150.00
Provider earnings = 850.00
```

The customer still pays 1,000.00. Customer-confirmed PayHere bookings now credit
the saved net earnings automatically; see [card settlement](CARD_BOOKING_SETTLEMENT_API.md).
Bank payouts and cancellation refunds remain separate workflows.

## Resolution order

| Priority | Configuration |
| --- | --- |
| 1 | The selected job rate's platformFeePercentage |
| 2 | Shared option's platformFeePercentage |
| 3 | The option's canonical main service's platformFeePercentage |
| 4 | 0.00% when no scope defines a percentage |

A rate can already be specific to a market, geographic area, option and choice
combination. Its percentage applies only when that rate wins normal pricing
selection. For example, CAR + MALE and CAR + FEMALE can have different rate-level
percentages. There is no separate variant entity or percentage-only matching
rule, and unmatched rates are not used as commission fallbacks.

Shared options use their original configurationServiceTypeId for the service
default, irrespective of the customer's linked navigation service. A legacy
service-level rate without an option inherits that rate's service default.

Null/omitted configuration means inherit; explicit 0 means zero commission at
that scope. Values must be between 0 and 100 inclusive with at most two decimal
places. The fare is rounded to two decimals first; the fee is rounded HALF_UP to
two decimals; provider earnings are fare minus that fee so the split balances.

## Admin: service and option defaults

Requires SUPER_ADMIN, bearer authentication and JSON writes.

| Method | Endpoint |
| --- | --- |
| GET / PUT | /api/v1/admin/provider-types/{serviceTypeId}/platform-fee |
| GET / PUT | /api/v1/admin/service-options/{optionId}/platform-fee |

PUT body:

```json
{"platformFeePercentage": 15.00}
```

Clear the override and resume inheritance:

```json
{"platformFeePercentage": null}
```

GET/PUT response example for an option inheriting the service's 15%:

```json
{
  "scope": "OPTION",
  "scopeId": 12,
  "configurationServiceTypeId": 3,
  "platformFeePercentage": null,
  "effectivePlatformFeePercentage": 15.00,
  "source": "SERVICE"
}
```

IDs are illustrative. source is OPTION, SERVICE or DEFAULT for these configuration
reads; RATE is returned in applied pricing snapshots. Effective configuration
reads do not incorporate any particular rate override. Service/option writes are
audited with old/new configuration. These dedicated endpoints preserve fee settings
when unrelated service/option forms are updated.

## Admin: rate, option variant and geographic overrides

The existing rate endpoints accept/return the new nullable property
platformFeePercentage:

| Method | Endpoint |
| --- | --- |
| POST | /api/v1/admin/market-services/{offeringId}/rates |
| PUT | /api/v1/admin/market-services/{offeringId}/rates/{rateId} |
| GET | /api/v1/admin/market-services/{offeringId}/rates |

Example creation for a configured TIME_BASED option and choice field:

```json
{
  "optionId": 12,
  "billingType": "FIXED",
  "rate": 1000.00,
  "conditions": [{"fieldId":45,"choiceValue":"CAR"}],
  "platformFeePercentage": 10.00
}
```

Use the existing billing/geographic/condition requirements. Rate PUT remains a
full replacement, including effectiveFrom and every editable rate field. Omitting
platformFeePercentage or setting null on PUT clears its old override. Preserve
the current value when editing unrelated rate fields if the override should stay.
Rate GET returns the stored override, not an inferred effective percentage.
Existing rate create/update audits include the percentage.

## Customer and provider contracts

Server quote, booking response and request/offer responses add:

```json
{
  "expectedAmount": 1000.00,
  "platformFeePercentage": 15.00,
  "platformFee": 150.00,
  "providerEarnings": 850.00
}
```

Applies to POST /api/v1/customers/me/bookings/quote, booking creation/history,
customer booking-request Views, provider offers and assigned booking details.
No client-supplied percentage or fee amount is accepted in customer booking
requests. Display the provider's net from providerEarnings rather than applying
another client-side deduction. Customer quote expectedAmount is still the full
fare charged by checkout. Amount fields may be null on unmatched requests or
historical records created before this feature.

The pricingSnapshot also records platformFeePercentage and platformFeeSource
(RATE, OPTION, SERVICE or DEFAULT). The job stores its percentage, platformFee
and providerEarnings at creation/reservation. Later admin edits do not reprice
an accepted booking or change its saved split. Existing historical rows are not
backfilled or recomputed from today's configuration.

Offers snapshot the current split. If the percentage/fee/net changes before
provider acceptance, acceptance returns 409 even if the customer fare stayed the
same; cancel/recreate for a fresh offer. Once accepted, checkout uses the reserved
booking and its saved split. Retries do not apply commission twice.

Start/completion confirmation does not trigger another charge or recalculate
commission. Existing wallet-funded settlement already uses job.platformFee to
deduct the platform share; customer-confirmed PayHere bookings automatically credit
the saved net earnings to the provider wallet. Refund, cancellation and provider-payout policies must use
the saved amounts when those workflows are implemented.

Errors: 400 invalid percentage/precision or existing rate validation errors;
401/403 unauthenticated/wrong role; 404 missing scope; 409 stale offer or existing
rate/configuration conflicts. Never invent a client-side fallback commission.
