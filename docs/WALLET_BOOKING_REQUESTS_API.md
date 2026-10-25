# Wallet payments for scheduled booking requests

Implemented locally; not deployed. This extends the existing single-provider
request lifecycle to CARD or WALLET for both supported TIME_BASED and ROUTE_BASED
services. It does not add team dispatch, new funding/top-up adapters, cancellation
refunds or bank payouts. No additional schema migration is needed beyond the
existing booking-request, platform-fee and card-settlement schemas.

## Configuration and creation

The option must allow WALLET and use ADMIN pricing, with an enabled booking-request
policy. Code encryption must be configured. A WALLET-only policy/request does not
require PayHere checkout configuration. CARD requests continue to require PayHere;
if an option allows both, an unavailable card adapter does not block WALLET requests.
The same minimum notice, capacity, coverage, eligibility and required field checks
apply. DIRECT_REQUEST includes booking.providerId; OPEN_REQUEST omits it.

CUSTOMER authorization and Content-Type: application/json:

`POST /api/v1/customers/me/booking-requests`

Header: `Idempotency-Key: <unique-logical-request-key>`

Example TIME_BASED ONLINE request (illustrative future dates/IDs; required fields
must come from the returned booking schema):

```json
{
  "marketId": 1,
  "booking": {
    "serviceTypeId": 3,
    "optionId": 12,
    "deliveryMode": "ONLINE",
    "startDatetime": "2030-01-07T08:00:00",
    "expectedEndDatetime": "2030-01-07T09:00:00",
    "paymentMethod": "WALLET",
    "answers": [],
    "pricingSelections": []
  },
  "radiusKm": 20,
  "languages": ["ENGLISH"],
  "requiredSkillIds": [],
  "providerFilters": []
}
```

For ROUTE_BASED use the existing requestedPickupTime/pickup/destination fields
instead of time interval fields, and set paymentMethod:WALLET. No money is reserved
at creation or during provider ringing. Provider acceptance creates an
AWAITING_PAYMENT job and an AWAITING_CUSTOMER_CONFIRMATION request. The customer
must explicitly confirm before confirmationExpiresAt; available balance can change
before then. The selected method is retained through quote and acceptance.

Request View adds `paymentMethod` (CARD or WALLET) to its existing fields, including
paymentStatus, paymentFundingMode and settlement metadata. The payment method is
fixed for the request. To change it before funding, cancel the still-unfunded
request and create a fresh request with a new idempotency key.

## Confirm using wallet

`POST /api/v1/customers/me/booking-requests/{requestId}/confirm-wallet`

CUSTOMER token. No body, no card brand and no billing-contact fields.

The server locks the owned request/job and payer wallet, checks the deadline,
reconciles the wallet and reserves expectedAmount from AVAILABLE into RESERVED.
The booking becomes CONFIRMED, paymentStatus becomes RESERVED, a customer START
code is generated and BOOKING_CONFIRMED events are queued. This is one transaction;
a failure generating the code or events rolls the financial reservation back.
No card gateway is called. Repeated/concurrent confirmation cannot reserve twice.

HTTP 200 View example:

```json
{
  "requestId": 501,
  "status": "CONFIRMED",
  "selectionMode": "OPEN_REQUEST",
  "offerId": 601,
  "providerId": 15,
  "offerExpiresAt": "2030-01-07T00:01:00Z",
  "confirmationExpiresAt": "2030-01-07T00:03:00Z",
  "jobId": 70,
  "paymentStatus": "RESERVED",
  "expectedAmount": 1000.00,
  "currencyCode": "LKR",
  "platformFeePercentage": 15.00,
  "platformFee": 150.00,
  "providerEarnings": 850.00,
  "settlementStatus": null,
  "settlementOperationId": null,
  "paymentFundingMode": "PAYHERE",
  "paymentMethod": "WALLET"
}
```

paymentFundingMode describes the wallet money's original source, not the chosen
booking payment method. PAYHERE means existing live card-sourced wallet funds;
it does not mean this wallet confirmation charges a card.

Example customer balances with LKR 1,500 available before confirmation:

```text
Before: available 1,500; reserved 0
After confirmation: available 500; reserved 1,000
After completion settlement: available 500; reserved 0
```

Read balances with GET /api/v1/me/wallets/LKR and history with
GET /api/v1/me/wallets/LKR/transactions?page=0&size=20.

## Insufficient balance

HTTP 409, application/problem+json example:

```json
{
  "type": "about:blank",
  "title": "Insufficient wallet balance",
  "status": 409,
  "detail": "Insufficient available wallet balance",
  "instance": "/api/v1/customers/me/booking-requests/501/confirm-wallet",
  "code": "INSUFFICIENT_WALLET_FUNDS",
  "currencyCode": "LKR",
  "requiredAmount": 1000.00,
  "availableAmount": 200.00,
  "shortfall": 800.00
}
```

The booking remains AWAITING_CUSTOMER_CONFIRMATION, no money is reserved and no
start code is issued. Keep the same deadline; no automatic extension is made.
The app can display the shortfall and retry the same confirmation if sufficient
eligible funds become available before the deadline. If it expires, normal expiry
cancels the unpaid job and releases service capacity. This update does not implement
live wallet top-ups: do not present the generic mock top-up API as a live funding
route. A request without an existing wallet sees available zero and a shortfall.

## Start and completion

Existing endpoints are unchanged:

GET /api/v1/customers/me/bookings/{jobId}/verification-codes
POST /api/v1/providers/me/bookings/{jobId}/start with {"code":"123456"}
POST /api/v1/providers/me/bookings/{jobId}/complete with no body
POST /api/v1/customers/me/bookings/{jobId}/confirm-completion with no body

WALLET/RESERVED is valid for starting and requesting completion, just as CARD/PAID
is valid for card bookings. There is no end code. Customer completion confirmation
atomically consumes the customer's RESERVED gross amount, credits the provider's
AVAILABLE net earnings, records the saved fee and completes the booking.
Both request and job paymentStatus become SETTLED; request settlementStatus is
POSTED with settlementOperationId. Card request paymentStatus still remains PAID
as its gateway receipt while its job becomes SETTLED.

Example: gross 1,000; saved fee 15% = 150; provider receives 850. The operation type
is WALLET_BOOKING_SETTLE, amount is gross, providerEarnings is net, and
paymentReference is WALLET_RESERVATION:<reservation-operation-id>. No PayHere
payment notice is created for wallet payment. Every settlement is balanced and
idempotent, including concurrent retries. A failure rolls back completion and
all financial postings, preserving the reservation. No fee recalculation from
current admin settings or actual duration/distance occurs.

The provider app still shows only providerEarnings/currencyCode on incoming and
booking screens, then refreshes wallet/history after BOOKING_COMPLETED. Provider
accept/start/complete routes remain SERVICE_PROVIDER-only; providers cannot reserve
a customer's wallet or confirm completion for them.

## Funding separation and older endpoints

Live request-wallet payments require a reconciled wallet tagged PAYHERE. A wallet
tagged MOCK is allowed only while the generic server gateway is in MOCK mode;
provider earnings and fee postings remain synthetic (MOCK_PLATFORM_FEE). Unknown,
legacy or unclassified nonempty balances require reconciliation; their source is
not guessed. PAYHERE_SANDBOX funds cannot pay for wallet bookings. Live/mock funds
cannot mix in a nonempty provider wallet. Frozen/inactive/unreconciled wallets block
posting. These rules prevent converting test credits into live provider earnings.

The existing POST /customers/me/bookings/{jobId}/wallet-payment route and admin
wallet-booking release/settlement/approval routes reject jobs linked to the new
request lifecycle. Use confirm-wallet and customer confirm-completion instead.
Legacy wallet-funded bookings without a booking request retain their original
finance-approved pending-earnings settlement behavior.

The CARD confirm endpoint rejects WALLET requests. confirm-wallet rejects CARD
requests. Verified gateway callbacks cannot fund wallet jobs. Confirmation retries
after success return the existing View without re-reserving or resetting codes.
The paid/reserved request cannot use the pre-payment cancellation endpoint; paid
cancellation/refund policies remain separate future work.

## Superadmin monitoring

Existing GET /api/v1/admin/booking-monitor endpoints expose WALLET jobs, RESERVED /
SETTLED payment states, BOOKING_RESERVE and WALLET_BOOKING_SETTLE operations and
linked ledger entries. Examples:

GET /api/v1/admin/booking-monitor/bookings?paymentMethod=WALLET&paymentStatus=RESERVED
GET /api/v1/admin/booking-monitor/transactions?jobId=70&type=WALLET_BOOKING_SETTLE
GET /api/v1/admin/booking-monitor/transactions/{operationId}/entries

Errors: 400 invalid create method/fields/configuration; 401/403 authentication/role;
404 ownership/missing request; 409 insufficient funds, wrong selected method,
missing acceptance, expired confirmation, unsupported funding or wallet/settlement
conflict. For the structured shortfall use code=INSUFFICIENT_WALLET_FUNDS rather
than matching the detail text. No new hold-and-capture or bank-payout API is added.
