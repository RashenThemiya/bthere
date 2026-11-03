# Customer code display and provider start screen

Backend endpoints already exist. Mobile app source is not in this repository;
screen implementation/device testing needs the customer/provider app repositories.

## Customer

After BOOKING_CONFIRMED, read current booking/request state. Only a CONFIRMED
booking offers a START code. Fetch with CUSTOMER bearer authorization:

```http
GET /api/v1/customers/me/bookings/{jobId}/verification-codes
```

HTTP 200:

```json
{"purpose":"START","code":"0123","expiresAt":"2026-11-01T12:00:00Z"}
```

The code is a four-character string, including leading zeros. Show it only to the
customer with “Share this code with your provider when service begins.” Do not
put it in push payloads, public logs or provider screens. It becomes readable
30 minutes before scheduled start and remains bounded by expiresAt; fetching
earlier returns 409. Hide the code after BOOKING_STARTED/IN_PROGRESS. A refresh
must not create a replacement code.

## Provider

Show “Enter customer's start code” only after funded CONFIRMED service. CARD PAID
or WALLET RESERVED is funded; AWAITING_PAYMENT is not. Never auto-start on accept.
Use SERVICE_PROVIDER bearer authorization:

```http
POST /api/v1/providers/me/bookings/{jobId}/start
Content-Type: application/json
```

```json
{"code":"0123"}
```

HTTP 200 success: `{"successful":true,"status":"IN_PROGRESS"}`. Refresh the
booking and start the in-service UI only after this response/current server state.
Actual start time is stored on the server; successful retries are idempotent.

HTTP 200 incorrect code: `{"successful":false,"status":"INVALID_CODE"}`.
Inspect successful/status, not HTTP 200 alone. Five incorrect attempts lock the
code: `{"successful":false,"status":"CODE_LOCKED"}`. Stop automatic retries and
direct the user to support/admin's existing audited code reset flow.

400: missing/malformed four-digit code. 401/403: authentication/role. 404: missing
booking or wrong customer/assigned provider. 409: unpaid/not confirmed, outside
service window or code unavailable in current state. On network timeout, fetch
booking status before retrying; never assume the service started.

Provider cannot fetch the customer code through its APIs. Backend validates
ownership, payment, timing and failed attempts, consumes the code and atomically
sets request/job IN_PROGRESS, records actual start time and emits BOOKING_STARTED.
No new endpoint/schema change is required for this existing flow.

Four-digit rollout: existing legacy six-digit codes are refreshed to four digits
on the customer code GET, retaining expiry and failed-attempt count. After
deployment refresh the customer screen; cached old six-digit codes are invalid
for the new provider endpoint. Admin reset also generates four digits.
