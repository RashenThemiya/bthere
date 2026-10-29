# MOCK card confirmation for booking requests

Implemented locally on 29 October 2026; deployment is not verified. This supports
ADMIN-priced single-provider OPEN_REQUEST and DIRECT_REQUEST bookings in MOCK
mode. PayHere checkout remains the real/sandbox merchant path.

Read `GET /api/v1/me/payment-settings` with a CUSTOMER bearer token:

```json
{
  "provider": "MOCK",
  "testMode": true,
  "capabilities": {
    "walletLedger": true,
    "bookingReservations": true,
    "cardCheckout": true,
    "topups": true,
    "withdrawals": true,
    "cardLinking": true,
    "bankLinking": true
  }
}
```

`provider` and `testMode` are additive fields; existing capabilities remain.
The option must allow CARD, have an enabled booking-request policy, and the
server needs BOOKING_VERIFICATION_KEY. Policy enabling and CARD request creation
accept the MOCK gateway without requiring PayHere credentials. Readiness is
still validated at each action; global capabilities do not replace option policy.

1. Create/reuse a test card with POST `/api/v1/me/payment-methods/setup?type=CARD`
   (no body), or select one from GET `/api/v1/me/payment-methods`.
2. POST `/api/v1/customers/me/booking-requests` using the existing request shape,
   `booking.paymentMethod: "CARD"` and Idempotency-Key. OPEN_REQUEST omits providerId;
   DIRECT_REQUEST supplies it. Required booking answers/pricing choices still apply.
3. Wait until the provider accepts and request status is
   AWAITING_CUSTOMER_CONFIRMATION. Show the accepted quote and confirmation deadline.
4. In MOCK mode, show an explicit **Confirm test payment** action. Call:

```http
POST /api/v1/customers/me/booking-requests/501/confirm-mock-card
Authorization: Bearer <CUSTOMER_TOKEN>
Content-Type: application/json
```

```json
{"methodId": 1}
```

HTTP 200 returns the existing full booking-request View. Relevant fields:

```json
{
  "requestId": 501,
  "status": "CONFIRMED",
  "paymentStatus": "PAID",
  "paymentMethod": "CARD",
  "paymentFundingMode": "MOCK",
  "jobId": 701,
  "expectedAmount": 3000.00,
  "currencyCode": "LKR"
}
```

These are illustrative IDs/amounts and an excerpt, not the entire View. No hosted
actionUrl is returned. This endpoint immediately simulates successful payment;
it does not charge a real card, contact a merchant or debit the customer wallet.
No PAN/CVV/contact fields, client price or client success status are accepted.
BOOKING_CONFIRMED events and START code follow the existing lifecycle. Earnings
are credited only after customer-confirmed completion. Synthetic earnings use
MOCK_CLEARING/MOCK_PLATFORM_FEE accounts and a MOCK-funded provider wallet;
they cannot mix with a nonempty live/sandbox wallet or settle outside MOCK mode.

Retries with the same request and method return the saved result, including
after that card is removed. A different method after payment returns 409.
Request/job/user locks prevent double confirmation and serialize card removal.
Audit/code/receipt failures roll back the entire confirmation transaction.

| Status | Meaning |
|---|---|
| 400 | Missing/nonpositive methodId |
| 401 / 403 | Missing authentication / not CUSTOMER |
| 404 | Request not owned, or card not owned/verified CARD under MOCK |
| 409 | Provider has not accepted, confirmation expired, start passed, wrong payment method, invalid saved amount/currency, or already paid with another method |
| 503 | MOCK is not selected/enabled, or booking verification key is invalid |

Errors use ProblemDetail. For non-MOCK environments the detail is
`Mock booking card payments are disabled`. The existing `/confirm` endpoint
remains the PayHere hosted path; in MOCK it returns 409 with
`Use confirm-mock-card with a saved mock methodId`. WALLET continues to use
`/confirm-wallet` with no body. PaymentMethod values remain CARD/WALLET.

Deploy the changed backend before frontend testing. No schema migration or
PayHere credentials are needed for this mock flow. Preserve the existing
BOOKING_VERIFICATION_KEY and configured option policy across redeployment.
