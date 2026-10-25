# Superadmin booking and transaction monitoring

The new booking-request lifecycle now supports WALLET as well as CARD. See
[wallet request payments](WALLET_BOOKING_REQUESTS_API.md) for the confirmation endpoint,
reservation states, funding rules and WALLET_BOOKING_SETTLE accounting.

Implemented locally on 25 October 2026; not deployed. All routes are read-only and
require `Authorization: Bearer <SUPER_ADMIN token>`. CUSTOMER, SERVICE_PROVIDER,
ADMIN and FINANCE_ADMIN cannot use these monitoring endpoints. Existing audited
support actions and finance approval endpoints retain their own permissions.
No request body is used by any endpoint below.

Base: `/api/v1/admin/booking-monitor`

## Endpoint reference

| GET suffix | Result |
| --- | --- |
| `/summary` | All-time counts grouped by stored status |
| `/requests` | Paginated booking requests, including those without a job |
| `/requests/{requestId}` | Request and linked booking summary, if accepted |
| `/bookings` | Paginated jobs, including legacy and new bookings |
| `/bookings/{jobId}` | Booking summary, linked request and full BookingResponse |
| `/offers` | Paginated provider offer attempts |
| `/payment-notices` | Paginated verified PayHere booking callback receipts |
| `/transactions` | Paginated wallet operations, including earnings settlements |
| `/transactions/{operationId}` | One wallet operation |
| `/transactions/{operationId}/entries` | Paginated immutable wallet/system ledger postings |
| `/events` | Paginated booking notification events and delivery state |

All collection endpoints support `page=0&size=20`, with page >= 0 and size 1..100.
They return Spring Page objects (`content`, `number`, `size`, `totalElements`,
`totalPages`, `first`, `last`, `numberOfElements`, `empty`, plus framework metadata).
Ordering is fixed by descending record ID; custom sort is not supported. Read all
pages to reconstruct a timeline; do not assume the first page contains every offer,
receipt or ledger posting. Blank text filters are ignored except statusCode, which
must be one of the exact gateway codes below. Status/type/mode filters ignore case;
IDs, gateway payment IDs and payment references match exactly. Unknown status
strings return an empty collection, not an inferred status group.

## Dashboard counts

`GET /summary`

```json
{
  "requestsByStatus": {"MATCHING": 3, "IN_PROGRESS": 2, "COMPLETED": 10},
  "bookingsByStatus": {"AWAITING_PAYMENT": 1, "IN_PROGRESS": 2, "COMPLETED": 12},
  "requestPaymentsByStatus": {"NOT_STARTED": 3, "PAID": 12},
  "requestSettlementsByStatus": {"POSTED": 10, "UNRECORDED": 5}
}
```

Illustrative counts. Only stored groups appear; missing keys mean zero. Null
statuses map to UNRECORDED, which does not imply a failed settlement. Requests
count only the new booking-request lifecycle; bookings count all jobs, including
legacy records. These are all-time counts with no filters and no revenue sums;
they can include sandbox/test bookings. Do not add request and job counts together.
Use filtered collection `totalElements` for counts by market/customer/date/etc.
Summary is an administrative snapshot, not a transactionally locked live feed.
The older `/api/v1/admin/dashboard/statistics` endpoint is unchanged; its legacy
Payment-table totals are not this PayHere receipt/settlement report.

## Booking requests

`GET /requests?status=MATCHING&marketId=1&page=0&size=20`

Supported filters: requestId, jobId, customerUserId, marketId, optionId, providerId,
status, paymentStatus, settlementStatus, fundingMode, createdFrom, createdTo.
providerId matches any provider offered that request (including declined/expired
attempts); it is not restricted to the final assigned provider.

An example `content` item (all RequestRow fields):

```json
{
  "requestId": 501, "customerUserId": 5, "marketId": 1, "optionId": 12,
  "selectionMode": "OPEN_REQUEST", "status": "COMPLETED", "jobId": 70,
  "paymentStatus": "PAID", "expectedAmount": 1000.00, "currencyCode": "LKR",
  "platformFeePercentage": 15.00, "platformFee": 150.00, "providerEarnings": 850.00,
  "fundingMode": "PAYHERE_SANDBOX", "paymentOrderId": "BR-example",
  "gatewayPaymentId": "gateway-payment-id", "settlementStatus": "POSTED",
  "settlementOperationId": 66, "expiresAt": "2030-01-07T00:10:00Z",
  "confirmationExpiresAt": "2030-01-07T00:03:00Z",
  "paymentStartedAt": "2030-01-07T00:02:00Z",
  "createdAt": "2030-01-07T00:00:00", "updatedAt": "2030-01-07T04:00:00"
}
```

The item illustrates a completed request; use status=COMPLETED to retrieve that
status rather than the MATCHING example query. GET `/requests/501` returns
`{"request": <RequestRow>, "booking": <BookingRow or null>}`. Unmatched/matching
requests can have no job, no amount and no payment source. No start code,
checkoutJson, idempotency key, checkout signature or internal candidate queue is
returned. Customer identity here is a userId, distinct from a job's customerId.

## Ongoing and completed bookings

`GET /bookings?status=IN_PROGRESS`

`GET /bookings?status=AWAITING_COMPLETION_CONFIRMATION`

`GET /bookings?status=COMPLETED&paymentStatus=SETTLED`

Supported filters: jobId, customerId, providerId, marketId, serviceTypeId, optionId,
status, paymentStatus, paymentMethod, schedulingModel, createdFrom, createdTo.
providerId matches either the primary assigned provider or any recorded team
assignment (including historical statuses). Each job appears once. An ongoing
booking means status=IN_PROGRESS; CONFIRMED is paid and waiting to start. Use
separate queries for each dashboard status category.

Example `content` item (all BookingRow fields):

```json
{
  "jobId": 70, "requestId": 501, "customerId": 7, "providerId": 15,
  "marketId": 1, "serviceTypeId": 3, "optionId": 12, "status": "COMPLETED",
  "paymentMethod": "CARD", "paymentStatus": "SETTLED", "schedulingModel": "ROUTE_BASED",
  "deliveryMode": "PROVIDER_TO_CUSTOMER", "fulfillmentModel": "ONE_TO_ONE",
  "currencyCode": "LKR", "expectedAmount": 1000.00, "totalAmount": 1000.00,
  "platformFeePercentage": 15.00, "platformFee": 150.00, "providerEarnings": 850.00,
  "startDatetime": "2030-01-07T08:00:00", "expectedEndDatetime": "2030-01-07T08:02:00",
  "actualStartDatetime": "2030-01-07T08:01:00", "actualEndDatetime": "2030-01-07T08:05:00",
  "createdAt": "2030-01-07T00:01:00", "updatedAt": "2030-01-07T04:00:00"
}
```

GET `/bookings/70` returns `booking` (BookingRow), `request` (RequestRow or null)
and `details` (the existing full BookingResponse: authorized locations, submitted
answers, providerIds and pricingSelections/pricingSnapshot). Legacy jobs have
requestId:null and request:null. This is an explicit superadmin detail read;
collection rows do not include private answers. IDs are returned rather than
customer/provider names or phone numbers. This change adds no location tracking.

## Provider offer attempts and notification delivery

`GET /offers?requestId=501` (use this to display who was offered the booking).

Filters: requestId, providerId, status, createdFrom, createdTo.

```json
{
  "offerId": 601, "requestId": 501, "providerId": 15, "providerLocationId": null,
  "status": "ACCEPTED", "expiresAt": "2030-01-07T00:01:00Z",
  "createdAt": "2030-01-07T00:00:00"
}
```

Offer states include PENDING, ACCEPTED, DECLINED, EXPIRED and CANCELLED. These
are server offer states, not a telephony call log. Existing data stores creation
and expiry but no distinct acceptedAt/declinedAt timestamps; do not invent them.

`GET /events?requestId=501&eventType=BOOKING_OFFER`

Filters: requestId, userId, eventType, deliveryStatus, createdFrom, createdTo.

```json
{
  "eventId": 801, "requestId": 501, "offerId": 601, "userId": 42,
  "eventType": "BOOKING_OFFER", "createdAt": "2030-01-07T00:00:00Z",
  "expiresAt": "2030-01-07T00:01:00Z", "deliveryStatus": "DELIVERED",
  "deliveryAttempts": 1, "nextDeliveryAt": "2030-01-07T00:00:00Z"
}
```

Delivery statuses are PENDING, DELIVERED, FAILED and EXPIRED. DELIVERED is backend notification delivery state, not proof that a person saw the
screen, that the device rang or that the provider accepted. userId is the event
recipient's user account; providerId on an offer is the provider profile ID.
Monitoring reads do not call tick/dispatch, expire offers or trigger notifications.
The existing background expiry/delivery workers own those actions.

## Verified PayHere payment receipts

`GET /payment-notices?requestId=501&statusCode=2`

Filters: requestId, paymentId, statusCode, fundingMode, receivedFrom, receivedTo.

```json
{
  "noticeId": 901, "requestId": 501, "paymentId": "gateway-payment-id",
  "statusCode": "2", "status": "SUCCESS", "receivedAt": "2030-01-07T00:02:30Z",
  "primaryPayment": true, "paymentOrderId": "BR-example", "requestPaymentStatus": "PAID",
  "currencyCode": "LKR", "bookingAmount": 1000.00, "fundingMode": "PAYHERE_SANDBOX",
  "jobId": 70, "settlementStatus": "POSTED", "settlementOperationId": 66
}
```

Codes: 2 SUCCESS, 0 PENDING, -1 CANCELLED, -2 FAILED, -3 CHARGEDBACK. Each distinct
verified payment ID/status is retained once. A payment can have multiple status
receipts, and an order can have extra payments. primaryPayment means the notice's
payment ID matches the request's saved gatewayPaymentId; it does not by itself
mean the booking is confirmed or the payment is currently safe/settled. Review
requestPaymentStatus and settlementStatus as well. A successful receipt can be
followed by a chargeback or require refund review.

bookingAmount/currencyCode/fundingMode come from the linked request (current saved
booking context), not a new gateway report. Do not sum receipt rows as revenue:
status updates and duplicate payments can refer to the same fare. This endpoint
covers persisted verified booking callbacks only; it does not query PayHere or
list legacy mock payment intents. No raw callback signatures/contact data appear.

## Wallet transactions and settlement ledger

`GET /transactions?jobId=70&type=CARD_BOOKING_SETTLE&status=POSTED`

Filters: jobId, ownerUserId, type, status, fundingMode, paymentReference,
createdFrom, createdTo. Without filters this includes all wallet operations,
including legacy wallet reservations/releases/settlements and mock movements.
ownerUserId is the operation owner (the customer for booking settlement), not the
receiving provider. Provider walletId appears on the actual ledger posting.

```json
{
  "operationId": 66, "type": "CARD_BOOKING_SETTLE", "ownerUserId": 5,
  "actorUserId": 5, "jobId": 70, "currencyCode": "LKR", "amount": 1000.00,
  "status": "POSTED", "platformFeePercentage": 15.00, "platformFee": 150.00,
  "providerEarnings": 850.00, "fundingMode": "PAYHERE_SANDBOX",
  "paymentReference": "gateway-payment-id", "createdAt": "2030-01-07T04:00:00"
}
```

GET `/transactions/66` returns this TransactionRow. For CARD_BOOKING_SETTLE,
amount is gross fare; providerEarnings is the provider credit. Current/live admin
fee edits do not change this saved split. Settlement states may include
PROCESSING, POSTED and PENDING_APPROVAL depending on operation type; legacy fee
and source metadata can be null.

GET `/transactions/66/entries?page=0&size=20` returns paginated LedgerRow objects:

```json
{
  "entryId": 101, "operationId": 66, "walletId": 42,
  "accountKey": "WALLET:42", "bucket": "AVAILABLE", "currencyCode": "LKR",
  "delta": 850.00, "balanceBefore": 0.00, "balanceAfter": 850.00,
  "createdAt": "2030-01-07T04:00:00"
}
```

System postings have walletId:null and a SYSTEM accountKey. All postings of a
completed settlement reconcile to zero (clearing debit, provider credit, fee
credit); fetch every page before summing. This API never credits/debits a wallet
or grants manual settlement approval. A wallet credit is not a bank transfer.
PAYHERE_SANDBOX/MOCK are test funding; explicitly filter fundingMode=PAYHERE for
live card-settlement reports. Unknown historical source remains null, not live.

## Time filters, errors and operational limits

createdFrom/createdTo on requests, bookings, offers and transactions use ISO local
datetimes, e.g. 2026-10-25T00:00:00, in the database creation timestamp convention;
there is no implicit market/phone timezone conversion. These filter row creation,
not scheduled pickup/start time. Booking scheduled/actual times are market-local.
Receipts receivedFrom/receivedTo and event createdFrom/createdTo are ISO instants
with offset, e.g. 2026-10-25T08:00:00Z. All ranges are lower-inclusive and
upper-exclusive; from >= to is 400. Omitted bounds are unrestricted.

400 invalid date binding, reversed range, nonpositive ID or invalid pagination /
statusCode; 401 unauthenticated; 403 wrong role; 404 missing detail/transaction.
A collection filter with no matches returns 200 with content:[], including an
unknown requestId filter. No raw entity serialization, checkout payloads, start
codes or candidate queues are exposed. Existing support endpoints are separate:
POST /api/v1/admin/booking-requests/{requestId}/resolve-unpaid and
POST /api/v1/admin/bookings/{jobId}/verification-codes/reset.

These endpoints use the existing tables and add no required schema migration.
They provide paginated polling, not WebSocket/live device tracking. Summary groups
and unbounded-time queries can still scan large tables; production MySQL plans,
indexes and load/contended lifecycle behavior must be verified before broad rollout.
