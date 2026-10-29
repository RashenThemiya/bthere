# Customer App — full frontend API reference

The new booking-request lifecycle now supports WALLET as well as CARD. See
[wallet request payments](WALLET_BOOKING_REQUESTS_API.md) for the confirmation endpoint,
reservation states, funding rules and WALLET_BOOKING_SETTLE accounting.

PayHere customer-confirmed bookings now support automatic provider earnings credit.
See [card booking settlement](CARD_BOOKING_SETTLEMENT_API.md) for the completion hook,
payment references, earnings history and environment separation.

## MOCK booking card checkout (2026-10-29)

OPEN_REQUEST and DIRECT_REQUEST now support an explicit synthetic card confirmation
in MOCK mode after provider acceptance. See [MOCK booking card API](MOCK_BOOKING_CARD_API.md)
for the full flow, request/response examples and errors. Payment settings add
`provider` and `testMode`; existing capabilities remain. In MOCK, call
`POST /api/v1/customers/me/booking-requests/{id}/confirm-mock-card` with
`{"methodId":1}` instead of the PayHere `/confirm` endpoint. Implemented locally;
deployment is not verified.

## Saved card brand and removal (2026-10-28)

Authenticated CUSTOMER bearer token; all paths below include `/api/v1`.
Implemented locally; hosted deployment is not verified.

`GET /api/v1/me/payment-methods` returns HTTP 200:

```json
[
  {
    "methodId": 1,
    "type": "CARD",
    "displayLabel": "TEST CARD •••• 4242",
    "brand": "VISA",
    "status": "VERIFIED_MOCK"
  }
]
```

Use `brand` (`VISA` or `MASTERCARD`) for the card logo. The current synthetic
mock card is VISA, including older mock rows without a saved brand.
Bank methods have `brand: null`. Live card linking remains unconfigured;
this change does not enable live card storage or payments.

`DELETE /api/v1/me/payment-methods/{methodId}` takes no body and returns
HTTP **204 No Content**. Refresh the list after success: the removed card
is absent. A repeated delete returns 404. Removing a card does not refund
payments, cancel bookings, or change the wallet balance.

| Status | Meaning |
|---|---|
| 401 | Missing/expired token |
| 403 | Role is neither CUSTOMER nor SERVICE_PROVIDER |
| 404 | Method is absent, owned by another user, or outside the current payment provider |
| 409 | A payment intent referencing this method is PENDING; card remains saved |

```json
{
  "type": "about:blank",
  "title": "Resource conflict",
  "status": 409,
  "detail": "Payment method is used by a pending payment",
  "instance": "/api/v1/me/payment-methods/1"
}
```

For 404, title is `Resource not found` and detail is `Payment method not found`.
Wait for a pending intent to finish as SUCCEEDED or FAILED, then retry removal.
Removal does not cancel or resolve that intent automatically. Completed payment
history retains its original methodId. Only intents that reference this specific
method block removal; a hosted checkout without a saved methodId is independent.
In MOCK mode, setup can add a new synthetic card after removal with a new ID.
See [mock payment APIs](MOCK_PAYMENTS_API.md) for setup and test-payment flows.
Existing schemas need the nullable brand column; see
[brand migration](PAYMENT_METHOD_BRAND_MIGRATION.sql).

## Customer tab update: provider filters and field/choice icons (2026-10-20)

Profile-photo 403 troubleshooting: the upload endpoint authenticates writes, while
the returned image URL requires separate S3/CDN anonymous read configuration.
Customer images currently use providers/{userId}/images, not customer document
prefixes. Do not add a Bearer token to S3 image requests or replace stable profile
URLs with expiring document view URLs. See [public image access checks](PUBLIC_IMAGE_ACCESS.md).

Implemented locally; deployment is not verified. These additions supplement the
numbered reference. Customers consume admin-configured metadata; the customer app
does not expose admin configuration or icon-upload controls.

### Discover filters for the selected option

```http
GET /api/v1/customers/me/booking-context?marketId=1&serviceTypeId=3&optionId=12
```

Success 200: existing context plus providerFilters. Response fragment:

```json
{
  "matchingFilters":["languages","requiredSkillIds","providerFilters"],
  "providerFilters":[
    {
      "fieldId":25,"code":"VEHICLE_TYPE","label":"Vehicle type",
      "fieldType":"SELECT","filterOperator":"IN",
      "icon":{"key":"service-icons/vehicle.png","url":"https://your-bucket.example/service-icons/vehicle.png"},
      "options":[
        {"value":"CAR","label":"Car","icon":{"key":"service-icons/car.png","url":"https://your-bucket.example/service-icons/car.png"}},
        {"value":"VAN","label":"Van","icon":null},
        "BIKE"
      ]
    },
    {"fieldId":26,"code":"EXPERIENCE","label":"Experience years","fieldType":"NUMBER","options":null,"filterOperator":"RANGE","icon":null},
    {"fieldId":27,"code":"OWN_VEHICLE","label":"Own vehicle","fieldType":"BOOLEAN","options":null,"filterOperator":"EQUALS","icon":null}
  ]
}
```

Only active applicable fields explicitly enabled by admin appear. SELECT/MULTI_SELECT
render choices, NUMBER renders inclusive min/max inputs, BOOLEAN renders Yes/No.
Empty providerFilters means no dynamic filters. Re-fetch on service/option change
and clear obsolete selections. Support string choices and {value,label,icon} objects;
fallback to value for missing label, text-only for missing icon. Field and choice icons
are independently optional.

### Search using stable values

Platform fee update (25 October 2026): booking quotes/responses and booking-request
Views add platformFeePercentage, platformFee and providerEarnings. expectedAmount
remains the full fare; commission is deducted from it for provider earnings.
Use server values and preserve booking snapshots. See [platform fee API](PLATFORM_FEES_API.md).

Scheduled request booking update (25 October 2026): opt-in options now use the
[booking request/card checkout lifecycle](BOOKING_REQUESTS_API.md). Fill configured
booking fields first, create a direct/open request, wait for provider acceptance,
confirm and pay by Visa/Mastercard credit/debit card, then use separate customer
a start code and customer completion confirmation (no end code). This is implemented locally, not deployed. Legacy booking-create
is blocked for enabled options. Read that contract for policy, new endpoints,
notification integration and payment-review limitations.

Map marker rules may use fewer fields than pricing. For example, CAR + MALE and
CAR + FEMALE can share a CAR-only marker rule while having different prices.
Render each search candidate's returned `mapMarker.icon`; do not create one icon
per pricing combination. Extra gender filters do not change a vehicle-only marker.
Search has no price field: request `POST /api/v1/customers/me/bookings/quote`
separately with all applicable `pricingSelections`.

### Provider-less estimate (OPEN_REQUEST booking form)

`/quote` needs a `providerId`, so it cannot price an OPEN_REQUEST form before a
provider accepts. Use the estimate endpoint while the form is being filled.
`/quote` is unchanged and remains the DIRECT_REQUEST flow.

```http
POST /api/v1/customers/me/bookings/estimate
Authorization: Bearer <CUSTOMER token>
```

The body is the same shape as `POST /booking-requests` (`marketId`, `booking`,
optional `searchLocation`), so the form draft can be sent as-is. Do not send
`booking.providerId`/`providerIds`. `booking.answers` are optional and ignored:
name/contact and other booking answers are not required for an estimate.
Customer profile and requirement eligibility are not checked here; they are
checked when the booking request is created.

```json
{
  "marketId": 1,
  "booking": {
    "serviceTypeId": 3,
    "optionId": 12,
    "deliveryMode": "PROVIDER_TO_CUSTOMER",
    "startDatetime": "2030-01-07T08:00:00",
    "expectedEndDatetime": "2030-01-07T10:00:00",
    "meetingPoint": {
      "name": "Home", "addressLine1": "12 Galle Road",
      "latitude": 6.9271, "longitude": 79.8612
    },
    "paymentMethod": "CARD",
    "pricingSelections": [{ "fieldId": 25, "choiceValue": "CAR" }]
  }
}
```

Location used for area prices:

| Booking | Point used |
|---|---|
| ROUTE_BASED | `booking.pickup` + `booking.destination` (distance), destination for area price. Send `requestedPickupTime`, not start/end. |
| PROVIDER_TO_CUSTOMER | `booking.meetingPoint` (required) |
| CUSTOMER_TO_PROVIDER | `searchLocation` if sent; otherwise only non-area prices apply |
| ONLINE | none |

200 response (same shape as `/quote`):

```json
{
  "rateId": 7,
  "currencyCode": "LKR",
  "expectedAmount": 3000.00,
  "startDatetime": "2030-01-07T08:00:00",
  "expectedEndDatetime": "2030-01-07T10:00:00",
  "estimatedDistanceKm": null,
  "pricingSelections": [{ "fieldId": 25, "choiceValue": "CAR" }],
  "pricingSnapshot": { "billingType": "HOURLY", "rate": 1500.00 },
  "platformFeePercentage": 10.00,
  "platformFee": 300.00,
  "providerEarnings": 2700.00
}
```

Estimate only: no booking, provider reservation, capacity hold or payment is
created. The amount comes from the admin rate for the market, option, time and
pricing choices. When a provider accepts, the request is re-quoted with that
provider's location. That quote can differ from the estimate when an area price
depends on the provider location (CUSTOMER_TO_PROVIDER), and it is the final
amount shown for confirmation.

Validation errors (`ProblemDetail`, `detail` text shown below):

| Status | detail |
|---|---|
| 400 | Field errors for missing `marketId`, `booking`, `booking.serviceTypeId`, `booking.optionId`, `booking.deliveryMode`, or an invalid `meetingPoint`/`pickup`/`destination`/`searchLocation` |
| 400 | `Estimate does not take a provider; use quote for a selected provider` |
| 400 | `Delivery mode is not enabled for this option` |
| 400 | `Physical locations are disabled for this option` |
| 400 | `TIME_BASED booking requires startDatetime before expectedEndDatetime` |
| 400 | `ROUTE_BASED booking requires requestedPickupTime, pickup and destination` |
| 400 | `Choose a future interval of at most 31 days` |
| 400 | `Customer meeting point is required` |
| 400 | `Select exactly one choice for every applicable pricing field: [25, 26]` |
| 404 | `Active service option not found` / `Market not found` / `Service is not offered in this market` |
| 404 | `No active price is configured for the selected choices, market and service option` |
| 409 | `Multiple equally specific prices match the selected choices` |
| 401 / 403 | Missing token / not a CUSTOMER |

```http
POST /api/v1/customers/me/provider-search
```

```json
{
  "marketId":1,"serviceTypeId":3,"optionId":12,"deliveryMode":"ONLINE",
  "startAt":"2030-01-07T08:00:00+05:30","endAt":"2030-01-07T10:00:00+05:30",
  "languages":["ENGLISH"],"requiredSkillIds":[],
  "providerFilters":[
    {"fieldId":25,"value":["CAR","VAN"]},
    {"fieldId":26,"minimum":3},
    {"fieldId":27,"value":true}
  ],
  "page":0,"pageSize":20
}
```

Use actual IDs, future times and an enabled delivery mode. ONLINE omits location;
physical searches require it. Submit values only, never labels/icons/choice objects.
Filters AND-combine with each other and languages/skills. IN accepts any requested
choice (any overlap for MULTI_SELECT). RANGE permits either or both bounds. EQUALS
takes a boolean. Omit providerFilters or send [] to clear dynamic filtering.

Success 200: existing Results wrapper/pagination; candidates additionally include
providerAttributes keyed by field ID, e.g. {"25":"CAR","26":5,"27":true}.
Only approved manual answers or valid linked profile values from customer-visible fields are included. Reuse is
resolved by backend. Display-only fields may appear but cannot be queried. No-review
fields are auto-approved; do not label all attributes independently verified.
Private uploads and pending/rejected/invalid answers are excluded. Filtering occurs
before pagination and does not reserve capacity or create a booking.

400: unavailable/private/inactive/other-option field, invalid choice/type/range,
duplicate IDs/choices, over 50 filters or over 100 choices per filter.
403: customer eligibility failed. Do not retry invalid filters unchanged.

### Booking fields remain separate

Profile-backed provider filters: admin can publish gender from provider profile and
age derived from provider DOB. These are live self-declared PROVIDED values; they
can match filters without a duplicate provider submission. Do not label them verified.
Raw DOB, NIC/passport and other private profile details cannot be published through
this mapping. Customer profile/patient fields are not auto-filled from provider data.

bookingSchema BOOKING_FIELD definitions also carry optional field and choice icons.
Render common plus selected-option fields, respecting required/conditions. Final booking
answers remain {fieldId,value}: SELECT sends "CAR", MULTI_SELECT sends ["CAR","VAN"].
Do not submit provider filter field IDs as booking answers or automatically copy filters
to the booking form. Reusable customer NIC/DOB requirements retain their own requirementId,
definition wrapper and submission APIs; provider document date modes do not govern them.

Full contracts: [provider filters](CUSTOMER_DISCOVERY_API.md#dynamic-provider-filters)
and [choice icons](DYNAMIC_FIELDS_API.md#icons-for-individual-select-choices).

Wallet update (2026-10-17): WALLET bookings now reserve funds atomically and return
paymentStatus RESERVED. Initial wallet read/history/reconciliation and finance
settlement APIs are documented in [Wallet API](WALLET_API.md). Top-ups, withdrawals,
card/bank linking and direct-card sessions remain disabled pending integration.
The older payment-state descriptions below must be read with this update.

Contract checked against the local Java controllers, DTOs, and services on 2026-10-16. New APIs are implemented locally; deployment has not been verified. This reference covers customer registration through discovery, plus the existing final booking handoff. IDs, URLs, tokens, times and results in examples are illustrative, not live data. Use actual configured IDs and future times.

## 1. Connection, authentication and conventions

Base URL: https://jobhub.pentarixlabs.com

All paths below include /api/v1. JSON writes use Content-Type: application/json. Protected calls use Authorization: Bearer <access-token>. Customer-specific routes require role CUSTOMER. Registration/login/OTP/refresh/logout-current are public credential endpoints; logout-all, users/me, markets and uploads require bearer authentication. Never send an admin token from the customer application.

GET endpoints have no request body. An empty JSON array is a valid empty list response. Lists returned by catalog/options/markets/requirements/bookings are raw arrays, not {data: ...} wrappers. Search is a results object with its own pagination. Missing data can be null; do not replace null with guessed IDs or URLs. JSON property names and enum values are case-sensitive unless stated otherwise. Treat IDs as opaque Java Long values; use a lossless JSON strategy if IDs exceed JavaScript safe integers. Monetary values/coordinates are JSON numbers; use decimal-safe arithmetic for displayed prices.

Search uses offset-aware ISO timestamps. Booking uses market-local timestamps without an offset. Date fields use YYYY-MM-DD. Local time fields use HH:mm:ss (HH:mm is also parseable). Instant fields, including upload URL expiry and checkedAt, use an offset such as Z. Do not hardcode timezone/currency from the phone locale; read the selected market.

## 2. Endpoint inventory and screen sequence

| Screen/action | Method and endpoint | Success |
| --- | --- | --- |
| Email registration | POST /api/v1/auth/register | 201 RegisterResponse |
| Email verification | POST /api/v1/auth/email/verify | 200 LoginResponse |
| Resend verification | POST /api/v1/auth/email/resend | 202 OtpRequestResponse |
| Password login | POST /api/v1/auth/login | 200 LoginResponse |
| Phone OTP request | POST /api/v1/auth/otp/request | 202 OtpRequestResponse |
| Phone OTP verification | POST /api/v1/auth/otp/verify | 200 LoginResponse |
| Google login | POST /api/v1/auth/google | 200 LoginResponse |
| Refresh session | POST /api/v1/auth/refresh | 200 LoginResponse |
| Logout current session | POST /api/v1/auth/logout | 204, no body |
| Logout all sessions | POST /api/v1/auth/logout-all | 204, no body |
| Restore current user | GET /api/v1/users/me | 200 CurrentUserResponse |
| Read/save customer profile | GET / PUT /api/v1/customers/me/profile | 200 CustomerProfileResponse |
| Choose market | GET /api/v1/markets | 200 MarketResponse[] |
| Browse services | GET /api/v1/customers/me/services?marketId=1 | 200 ServiceCard[] |
| Choose service option | GET /api/v1/customers/me/services/3/options?marketId=1 | 200 OptionCard[] |
| Load settings/form | GET /api/v1/customers/me/booking-context?marketId=1&serviceTypeId=3&optionId=12 | 200 Context |
| Load customer requirements | GET /api/v1/customers/me/requirements?serviceTypeId=3&optionId=12 | 200 FormItem[] |
| Private evidence upload | POST /api/v1/uploads/documents | 200 FileUploadResponse |
| Public profile image upload | POST /api/v1/uploads/images | 201 FileUploadResponse |
| View private evidence | GET /api/v1/uploads/view-url?key=<encoded-key> | 200 PresignedUrlResponse |
| Save reusable customer answers | PUT /api/v1/customers/me/requirement-submissions | 200 Submission[] |
| Recheck eligibility | GET /api/v1/customers/me/eligibility?serviceTypeId=3&optionId=12 | 200 Eligibility |
| Search available providers | POST /api/v1/customers/me/provider-search | 200 Results |
| Final booking handoff | POST /api/v1/customers/me/bookings | 201 BookingResponse |
| Own booking history | GET /api/v1/customers/me/bookings | 200 BookingResponse[] |

Register/login → GET profile (404 means complete it) → choose market/location → services → options → context/access check → complete requirements/review if needed → select delivery/time/matching preferences → provider search → booking answers → final booking. Keep selected service/option in navigation when opening requirements, so the customer can return to the same flow.

## 3. Email registration

```http
POST /api/v1/auth/register
```

Request body:

```json
{
  "email": "customer@example.com",
  "password": "StrongPassword123!",
  "accountType": "CUSTOMER"
}
```

Success: 201 Created.

```json
{
  "id": 25,
  "username": "customer01",
  "email": "customer@example.com",
  "phoneNumber": null,
  "status": "PENDING",
  "emailVerified": false,
  "phoneVerified": false,
  "roles": [
    "CUSTOMER"
  ]
}
```

Required: email (valid, max 254), password (10–72 characters), accountType. The public accountType enum also supports SERVICE_PROVIDER, but this app sends CUSTOMER. Username is generated; do not send username, role, profile or address here. Registration sends email OTP and does not return tokens. Email is normalized to lowercase. Errors: 400 invalid input; 409 duplicate account; 429 OTP cooldown; delivery/configuration failure can return 500.

## 4. Verify email

```http
POST /api/v1/auth/email/verify
```

Request body:

```json
{
  "email": "customer@example.com",
  "otp": "123456"
}
```

Success: 200 OK.

```json
{
  "accessToken": "<access-token>",
  "refreshToken": "<refresh-token>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": 25,
    "username": "customer01",
    "email": "customer@example.com",
    "status": "ACTIVE",
    "marketId": null,
    "roles": [
      "CUSTOMER"
    ]
  }
}
```

Both fields required. OTP is a six-digit string (preserve leading zeroes). Default OTP expiry is 300 seconds and maximum failed attempts is 5; server configuration may differ. Success activates the account and authenticates it. Errors: 400 invalid format; 401 invalid/expired/used OTP or attempt limit.

## 5. Resend email OTP

```http
POST /api/v1/auth/email/resend
```

Request body:

```json
{
  "email": "customer@example.com"
}
```

Success: 202 Accepted.

```json
{
  "message": "If the email is awaiting verification, a code has been sent",
  "expiresIn": 300
}
```

email is required and must be valid. Response is intentionally generic, not proof that an account exists. Use retryAfterSeconds / Retry-After on 429; default resend cooldown is 60 seconds.

## 6. Password login

```http
POST /api/v1/auth/login
```

Request body:

```json
{
  "identifier": "customer@example.com",
  "password": "StrongPassword123!"
}
```

Success: 200 OK.

```json
{
  "accessToken": "<access-token>",
  "refreshToken": "<refresh-token>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": 25,
    "username": "customer01",
    "email": "customer@example.com",
    "status": "ACTIVE",
    "marketId": null,
    "roles": [
      "CUSTOMER"
    ]
  }
}
```

identifier and password must be nonblank. identifier accepts email or generated username. Do not send accountType to this endpoint. Email-registered users must verify before password login. Wrong credentials/inactive account returns 401. Confirm returned roles includes CUSTOMER before entering the customer interface.

## 7. Phone registration/login — request OTP

```http
POST /api/v1/auth/otp/request
```

Request body:

```json
{
  "phoneNumber": "+94771234567",
  "accountType": "CUSTOMER"
}
```

Success: 202 Accepted.

```json
{
  "message": "If the phone number is eligible, an OTP has been sent",
  "expiresIn": 300
}
```

Both properties required. phoneNumber must match E.164: ^\+[1-9][0-9]{7,14}$. No purpose/type field. Response is generic. Text.lk must be configured. A new number becomes an account on successful verification; an existing matching account signs in. Handle 400/429 without revealing whether a number exists.

## 8. Phone registration/login — verify OTP

```http
POST /api/v1/auth/otp/verify
```

Request body:

```json
{
  "phoneNumber": "+94771234567",
  "otp": "123456",
  "accountType": "CUSTOMER"
}
```

Success: 200 OK.

```json
{
  "accessToken": "<access-token>",
  "refreshToken": "<refresh-token>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": 25,
    "username": "customer01",
    "email": "customer@example.com",
    "status": "ACTIVE",
    "marketId": null,
    "roles": [
      "CUSTOMER"
    ]
  }
}
```

All three properties required; phone is E.164, OTP exactly six digits. LoginResponse.user.email may be null for phone-only accounts; username is generated. Do not require an email on the next screen just because the illustrative login sample has one. Errors: 400 invalid input; 401 invalid/expired OTP or account mismatch.

## 9. Google authentication

```http
POST /api/v1/auth/google
```

Request body:

```json
{
  "idToken": "<google-id-token>",
  "accountType": "CUSTOMER"
}
```

Success: 200 OK.

```json
{
  "accessToken": "<access-token>",
  "refreshToken": "<refresh-token>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": 25,
    "username": "customer01",
    "email": "customer@example.com",
    "status": "ACTIVE",
    "marketId": null,
    "roles": [
      "CUSTOMER"
    ]
  }
}
```

Both fields required. Obtain a Google ID token, not a Google access token. Google client configuration must match the backend. Existing linked accounts retain their stored role; inspect returned roles. Errors: 400 missing data; 401 invalid token/inactive or unsupported account. This document does not imply that a production Google client is configured.

## 10. Refresh authentication

```http
POST /api/v1/auth/refresh
```

Request body:

```json
{
  "refreshToken": "<current-refresh-token>"
}
```

Success: 200 OK.

```json
{
  "accessToken": "<access-token>",
  "refreshToken": "<refresh-token>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": 25,
    "username": "customer01",
    "email": "customer@example.com",
    "status": "ACTIVE",
    "marketId": null,
    "roles": [
      "CUSTOMER"
    ]
  }
}
```

refreshToken is required/nonblank. Replace BOTH stored tokens atomically on success: refresh tokens rotate. Serialize refresh operations so concurrent 401s do not replay the same refresh token. On refresh 401, clear the session and return to login. Retry only safe reads automatically; do not blindly retry a non-idempotent booking.

## 11. Logout current session

```http
POST /api/v1/auth/logout
```

Request body:

```json
{
  "refreshToken": "<current-refresh-token>"
}
```

Success: 204 No Content.

No response body.

No JSON response body; do not attempt JSON parsing. Revoke the refresh session and clear local credentials.

## 12. Logout every session

```http
POST /api/v1/auth/logout-all
```

Request body:

None.

Success: 204 No Content.

No response body.

Requires bearer authentication. Revokes all refresh sessions. Existing short-lived access tokens can remain usable until their expiry.

## 13. Current authenticated user

```http
GET /api/v1/users/me
```

Request body:

None.

Success: 200 OK.

```json
{
  "id": 25,
  "username": "customer01",
  "email": "customer@example.com",
  "status": "ACTIVE",
  "marketId": null,
  "roles": [
    "CUSTOMER"
  ]
}
```

Exact CurrentUserResponse fields: id (integer), username (string), email (string|null), status (string), marketId (integer|null), roles (string[]). For CUSTOMER marketId is null: it is not the selected discovery market. Store selected marketId in app state and send it on discovery requests. LoginResponse always has accessToken, refreshToken, tokenType, expiresIn (seconds), user. expiresIn is server-provided; 900 is the default, not a client constant.

## 14. Create/replace customer profile

```http
PUT /api/v1/customers/me/profile
```

Request body:

```json
{
  "firstName": "Jane",
  "lastName": "Doe",
  "profilePhoto": null,
  "addressLine1": "10 Main Street",
  "addressLine2": null,
  "city": "Colombo",
  "district": "Colombo",
  "province": "Western",
  "postalCode": "00100",
  "country": "Sri Lanka"
}
```

Success: 200 OK.

```json
{
  "customerId": 7,
  "userId": 25,
  "firstName": "Jane",
  "lastName": "Doe",
  "profilePhoto": null,
  "addressLine1": "10 Main Street",
  "addressLine2": null,
  "city": "Colombo",
  "district": "Colombo",
  "province": "Western",
  "postalCode": "00100",
  "country": "Sri Lanka",
  "status": "ACTIVE",
  "profileCompleted": true
}
```

Full replacement/upsert for these fields. Required nonblank: firstName (max100), lastName (max100), addressLine1 (max255), city (max100), country (max100). Optional nullable: profilePhoto (max2048), addressLine2 (max255), district/province (max100 each), postalCode (max20). Omitting optional values clears them through the existing replacement behavior. Do not send userId, customerId, status or profileCompleted. DOB, gender and NIC are configured reusable requirements, not profile DTO properties. Authentication must be CUSTOMER.

## 15. Read customer profile

```http
GET /api/v1/customers/me/profile
```

Request body:

None.

Success: 200 OK.

```json
{
  "customerId": 7,
  "userId": 25,
  "firstName": "Jane",
  "lastName": "Doe",
  "profilePhoto": null,
  "addressLine1": "10 Main Street",
  "addressLine2": null,
  "city": "Colombo",
  "district": "Colombo",
  "province": "Western",
  "postalCode": "00100",
  "country": "Sri Lanka",
  "status": "ACTIVE",
  "profileCompleted": true
}
```

404 means no saved profile: route to profile completion. The current implementation returns profileCompleted:true for a persisted profile, not a dynamic document-eligibility percentage. customerId (entity ID) and userId (authentication ID) are different; private file keys use userId.

## 16. Active markets

```http
GET /api/v1/markets
```

Request body:

None.

Success: 200 OK.

```json
[
  {
    "id": 1,
    "name": "Sri Lanka",
    "countryCode": "LK",
    "currencyCode": "LKR",
    "timezone": "Asia/Colombo",
    "locale": "en-LK",
    "phoneCode": "+94",
    "status": "ACTIVE",
    "createdAt": "2026-01-01T10:00:00",
    "updatedAt": "2026-01-01T10:00:00"
  }
]
```

No query/body required; authenticated read. id is selected marketId. countryCode is the configured country code; currencyCode is currency (not currency); phoneCode is the dial prefix (not phoneCountryCode). timezone is an IANA zone used for schedules. createdAt/updatedAt are local datetime strings. Choose a returned active market and retain its ID in app state; there is no customer PUT market-selection endpoint. Location permission/GPS is handled by the mobile platform; the backend takes coordinates in search, not a device permission flag.

## 17. Browse services for market

```http
GET /api/v1/customers/me/services?marketId=1
```

Request body:

None.

Success: 200 OK.

```json
[
  {
    "service": {
      "id": 3,
      "name": "Nursing",
      "description": "Home nursing services",
      "iconUrl": "https://cdn.example.com/nursing.png",
      "status": "ACTIVE",
      "icon": {
        "key": "service-icons/nursing.png",
        "url": "https://cdn.example.com/nursing.png",
        "altText": "Nursing service",
        "displayOrder": 0
      },
      "coverImage": null,
      "gallery": [],
      "responsibilities": [
        {
          "responsibleParty": "CUSTOMER",
          "title": "Provide access",
          "description": "Ensure access to the agreed service location.",
          "required": true,
          "displayOrder": 1
        }
      ]
    },
    "access": {
      "status": "ELIGIBLE",
      "action": "CONTINUE",
      "eligibility": {
        "eligible": true,
        "reasons": []
      }
    }
  }
]
```

Required query: marketId (integer). Requires saved customer profile and active market. Returns active services with active market offerings only; empty results is []. Restricted services are returned with access badges, not hidden. Service fields shown above are the complete ProviderTypeResponse shape: id, name, description, iconUrl, status, icon, coverImage, gallery, responsibilities. Optional media/description can be null; collections can be empty. This endpoint returns a raw unpaginated array.

## 18. Choose option for service

```http
GET /api/v1/customers/me/services/3/options?marketId=1
```

Request body:

None.

Success: 200 OK.

```json
[
  {
    "option": {
      "optionId": 12,
      "serviceTypeId": 3,
      "code": "HOME_CARE",
      "name": "Home Care",
      "description": "Care at the customer location",
      "displayOrder": 1,
      "status": "ACTIVE",
      "schedulingModel": "TIME_BASED",
      "routeAverageSpeedKmh": null,
      "locationEnabled": true,
      "deliveryModes": [
        "PROVIDER_TO_CUSTOMER"
      ],
      "fulfillmentModel": "ONE_TO_ONE",
      "defaultCapacity": 1,
      "requiredProviderCount": 1,
      "bookingMode": "ONE_AT_A_TIME",
      "providerSelectionMode": "DIRECT_REQUEST",
      "pricingOwner": "ADMIN",
      "allowedPaymentMethods": [
        "CASH",
        "CARD"
      ],
      "deliveryModeConfigurations": [
        {
          "code": "PROVIDER_TO_CUSTOMER",
          "providerLocationType": "COVERAGE_AREA",
          "providerLocationRequired": true,
          "radiusRequired": true,
          "customerLocationInput": "MEETING_POINT"
        }
      ],
      "icon": {
        "key": "service-icons/nursing.png",
        "url": "https://cdn.example.com/nursing.png",
        "altText": "Nursing service",
        "displayOrder": 0
      },
      "coverImage": null,
      "gallery": [],
      "responsibilities": [
        {
          "responsibleParty": "CUSTOMER",
          "title": "Provide access",
          "description": "Ensure access to the agreed service location.",
          "required": true,
          "displayOrder": 1
        }
      ],
      "dynamicFields": [
        {
          "fieldId": 20,
          "serviceTypeId": 3,
          "optionId": 12,
          "optionIds": [
            12
          ],
          "scope": "BOOKING_FIELD",
          "reuseScope": "ASSIGNMENT",
          "code": "CARE_NOTES",
          "label": "Care instructions",
          "description": "Tell the provider what care is needed",
          "fieldType": "TEXTAREA",
          "required": false,
          "requiresApproval": false,
          "minimumFiles": null,
          "maximumFiles": null,
          "maximumFileSizeMb": null,
          "allowedFileTypes": [],
          "options": null,
          "condition": null,
          "displayOrder": 1,
          "version": 1,
          "status": "ACTIVE"
        }
      ]
    },
    "access": {
      "status": "ELIGIBLE",
      "action": "CONTINUE",
      "eligibility": {
        "eligible": true,
        "reasons": []
      }
    }
  }
]
```

Path serviceTypeId and query marketId required. Returns active options for this service/market, raw array. Each option has its exact eligibility, not just the service badge. Do not use providerTypeId as a different service ID: service.id is the serviceTypeId for this flow. Options are admin configured; render returned modes/limits rather than hardcoding Nursing behavior. Every option response property is shown in the example.

## 19. Full booking context and form

```http
GET /api/v1/customers/me/booking-context?marketId=1&serviceTypeId=3&optionId=12
```

Request body:

None.

Success: 200 OK.

```json
{
  "marketId": 1,
  "timezone": "Asia/Colombo",
  "selection": {
    "option": {
      "optionId": 12,
      "serviceTypeId": 3,
      "code": "HOME_CARE",
      "name": "Home Care",
      "description": "Care at the customer location",
      "displayOrder": 1,
      "status": "ACTIVE",
      "schedulingModel": "TIME_BASED",
      "routeAverageSpeedKmh": null,
      "locationEnabled": true,
      "deliveryModes": [
        "PROVIDER_TO_CUSTOMER"
      ],
      "fulfillmentModel": "ONE_TO_ONE",
      "defaultCapacity": 1,
      "requiredProviderCount": 1,
      "bookingMode": "ONE_AT_A_TIME",
      "providerSelectionMode": "DIRECT_REQUEST",
      "pricingOwner": "ADMIN",
      "allowedPaymentMethods": [
        "CASH",
        "CARD"
      ],
      "deliveryModeConfigurations": [
        {
          "code": "PROVIDER_TO_CUSTOMER",
          "providerLocationType": "COVERAGE_AREA",
          "providerLocationRequired": true,
          "radiusRequired": true,
          "customerLocationInput": "MEETING_POINT"
        }
      ],
      "icon": {
        "key": "service-icons/nursing.png",
        "url": "https://cdn.example.com/nursing.png",
        "altText": "Nursing service",
        "displayOrder": 0
      },
      "coverImage": null,
      "gallery": [],
      "responsibilities": [
        {
          "responsibleParty": "CUSTOMER",
          "title": "Provide access",
          "description": "Ensure access to the agreed service location.",
          "required": true,
          "displayOrder": 1
        }
      ],
      "dynamicFields": [
        {
          "fieldId": 20,
          "serviceTypeId": 3,
          "optionId": 12,
          "optionIds": [
            12
          ],
          "scope": "BOOKING_FIELD",
          "reuseScope": "ASSIGNMENT",
          "code": "CARE_NOTES",
          "label": "Care instructions",
          "description": "Tell the provider what care is needed",
          "fieldType": "TEXTAREA",
          "required": false,
          "requiresApproval": false,
          "minimumFiles": null,
          "maximumFiles": null,
          "maximumFileSizeMb": null,
          "allowedFileTypes": [],
          "options": null,
          "condition": null,
          "displayOrder": 1,
          "version": 1,
          "status": "ACTIVE"
        }
      ]
    },
    "access": {
      "status": "ELIGIBLE",
      "action": "CONTINUE",
      "eligibility": {
        "eligible": true,
        "reasons": []
      }
    }
  },
  "bookingSchema": {
    "serviceTypeId": 3,
    "serviceName": "Nursing",
    "serviceSetup": {
      "serviceTypeId": 3,
      "name": "Nursing",
      "description": "Home nursing services",
      "iconUrl": "https://cdn.example.com/nursing.png",
      "status": "ACTIVE",
      "availabilityEnabled": true,
      "serviceAreasEnabled": true,
      "optionsRequired": true,
      "minimumOptionSelections": 1,
      "maximumOptionSelections": 5,
      "documents": [],
      "skills": [
        {
          "skillId": 10,
          "name": "Patient care",
          "required": false,
          "requiresApproval": false
        }
      ],
      "certificates": {
        "level": "OPTIONAL",
        "requiresApproval": true
      },
      "education": {
        "level": "DISABLED",
        "requiresApproval": false
      },
      "icon": {
        "key": "service-icons/nursing.png",
        "url": "https://cdn.example.com/nursing.png",
        "altText": "Nursing service",
        "displayOrder": 0
      },
      "coverImage": null,
      "gallery": [],
      "responsibilities": [
        {
          "responsibleParty": "CUSTOMER",
          "title": "Provide access",
          "description": "Ensure access to the agreed service location.",
          "required": true,
          "displayOrder": 1
        }
      ]
    },
    "commonFields": [],
    "options": [
      {
        "optionId": 12,
        "code": "HOME_CARE",
        "name": "Home Care",
        "fields": [
          {
            "fieldId": 20,
            "serviceTypeId": 3,
            "optionId": 12,
            "optionIds": [
              12
            ],
            "scope": "BOOKING_FIELD",
            "reuseScope": "ASSIGNMENT",
            "code": "CARE_NOTES",
            "label": "Care instructions",
            "description": "Tell the provider what care is needed",
            "fieldType": "TEXTAREA",
            "required": false,
            "requiresApproval": false,
            "minimumFiles": null,
            "maximumFiles": null,
            "maximumFileSizeMb": null,
            "allowedFileTypes": [],
            "options": null,
            "condition": null,
            "displayOrder": 1,
            "version": 1,
            "status": "ACTIVE"
          }
        ]
      }
    ]
  },
  "matchingFilters": [
    "languages",
    "requiredSkillIds",
    "providerFilters"
  ],
  "liveTrackingSupported": false,
  "providerFilters": []
}
```

All three integer query parameters are required. The endpoint remains readable when selection.access is blocked, so the app can explain the service and offer requirements. It does not bypass search eligibility. selection is the complete OptionCard. bookingSchema.options is filtered to the selected option. commonFields always applies; selected option fields apply in addition; deduplicate shared fieldId values. serviceSetup documents/skills/certificate/education sections describe provider setup and may supply configured skill IDs; do not ask the customer to complete provider qualification sections. matchingFilters is [languages, requiredSkillIds, providerFilters]. Context providerFilters describes admin-enabled dynamic provider filters; see [dynamic provider filter contract](CUSTOMER_DISCOVERY_API.md#dynamic-provider-filters). liveTrackingSupported is currently false.

## 20. Customer reusable requirements form

```http
GET /api/v1/customers/me/requirements?serviceTypeId=3&optionId=12
```

Request body:

None.

Success: 200 OK.

```json
[
  {
    "requirement": {
      "requirementId": 1,
      "version": 1,
      "status": "ACTIVE",
      "definition": {
        "code": "NIC",
        "label": "National identity card",
        "description": "Upload front and back images",
        "fieldType": "IMAGE_UPLOAD",
        "required": true,
        "verificationMode": "DOCUMENT_REVIEW",
        "blocksBooking": false,
        "targets": [
          {
            "scope": "GLOBAL",
            "serviceTypeId": null,
            "optionId": null
          }
        ],
        "evidenceCodes": [],
        "choices": [],
        "minimumFiles": 2,
        "maximumFiles": 2,
        "maximumFileSizeMb": 10,
        "allowedFileTypes": [
          "JPG",
          "PNG",
          "WEBP"
        ],
        "displayOrder": 1
      }
    },
    "submission": null
  },
  {
    "requirement": {
      "requirementId": 2,
      "version": 1,
      "status": "ACTIVE",
      "definition": {
        "code": "DATE_OF_BIRTH",
        "label": "Date of birth",
        "description": "Must match your NIC",
        "fieldType": "DATE",
        "required": true,
        "verificationMode": "DOCUMENT_REVIEW",
        "blocksBooking": false,
        "targets": [
          {
            "scope": "SERVICE",
            "serviceTypeId": 3,
            "optionId": null
          }
        ],
        "evidenceCodes": [
          "NIC"
        ],
        "choices": [],
        "minimumFiles": null,
        "maximumFiles": null,
        "maximumFileSizeMb": null,
        "allowedFileTypes": [],
        "displayOrder": 2
      }
    },
    "submission": null
  }
]
```

serviceTypeId and optionId are optional, but optionId requires serviceTypeId. No context returns global requirements; service context adds service rules; option context adds its rules. Related evidence and DOB definitions can be included even when their own targets are broader/different, because they support applicable rules. A FormItem always has requirement and submission; unanswered submission is null. requirementId is for requirement-submissions; fieldId is for BOOKING_FIELD answers. They are NOT interchangeable. Read full customer requirement definition/answer schemas below.

## 21. Private customer document upload

```http
POST /api/v1/uploads/documents
```

Request format: multipart/form-data. One binary file part named file; no JSON request body.

Success: 200 OK.

```json
{
  "key": "customers/25/documents/550e8400-e29b-41d4-a716-446655440000.jpg",
  "url": "s3://private-bucket/customers/25/documents/550e8400-e29b-41d4-a716-446655440000.jpg",
  "contentType": "image/jpeg",
  "size": 245120
}
```

Actual request is multipart/form-data with one part named file containing file bytes, not the illustrative string "multipart" above. Supported MIME types: application/pdf, image/jpeg, image/png, image/webp. Maximum100MB, and field limits can be stricter. Let the HTTP library generate the multipart boundary; do not send JSON or set a boundaryless multipart header. Use returned key in files[].key; s3:// URL is not a browser view URL. Never infer bucket/prefix/UUID. CUSTOMER upload keys use authenticated userId. The backend checks ownership, object existence, actual S3 metadata, field count/type/size rules on submission. Already uploaded files are not rolled back if a later form save fails.

## 22. Public profile photo upload

```http
POST /api/v1/uploads/images
```

Request format: multipart/form-data. One binary file part named file; no JSON request body.

Success: 201 Created.

```json
{
  "key": "providers/25/images/550e8400-e29b-41d4-a716-446655440000.jpg",
  "url": "https://cdn.example.com/providers/25/images/550e8400-e29b-41d4-a716-446655440000.jpg",
  "contentType": "image/jpeg",
  "size": 245120
}
```

Actual request is multipart/form-data with part file, not a JSON string. Maximum10MB; JPG/PNG/WebP for customers. Current generic image uploader uses a providers/{userId}/images prefix even for CUSTOMER; this existing prefix does not mean the customer is a provider. Use its returned public url as profilePhoto. Never upload NIC/evidence using this public endpoint. SVG is not accepted for customer photos; service-asset SVG support is Super Admin only.

## 23. View private file

```http
GET /api/v1/uploads/view-url?key=customers%2F25%2Fdocuments%2F550e8400-e29b-41d4-a716-446655440000.jpg
```

Request body:

None.

Success: 200 OK.

```json
{
  "key": "customers/25/documents/550e8400-e29b-41d4-a716-446655440000.jpg",
  "url": "https://private-bucket.example.com/object?temporary-signature=example",
  "expiresAt": "2030-01-01T00:10:00Z"
}
```

Required query key is the returned object key, URL-encoded once. Only owning customer/authorized admin can view the private file. Signed url lasts10minutes; use expiresAt to refresh when needed. Do not store signed urls as permanent submission values, expose them in logs, or use their host as a fixed configuration.

## 24. Save all supplied reusable answers

```http
PUT /api/v1/customers/me/requirement-submissions
```

Request body:

```json
{
  "submissions": [
    {
      "requirementId": 1,
      "value": {
        "files": [
          {
            "key": "customers/25/documents/nic-front.jpg"
          },
          {
            "key": "customers/25/documents/nic-back.jpg"
          }
        ]
      },
      "expiresOn": null
    },
    {
      "requirementId": 2,
      "value": "2000-05-10",
      "expiresOn": null
    }
  ]
}
```

Success: 200 OK.

```json
[
  {
    "submissionId": 101,
    "customerId": 7,
    "requirementId": 1,
    "code": "NIC",
    "value": {
      "files": [
        {
          "key": "customers/25/documents/nic-front.jpg"
        },
        {
          "key": "customers/25/documents/nic-back.jpg"
        }
      ]
    },
    "status": "PENDING",
    "storedStatus": "PENDING",
    "expiresOn": null,
    "reviewedBy": null,
    "reviewedAt": null,
    "rejectionReason": null,
    "answerVersion": 1,
    "definitionVersion": 1
  },
  {
    "submissionId": 102,
    "customerId": 7,
    "requirementId": 2,
    "code": "DATE_OF_BIRTH",
    "value": "2000-05-10",
    "status": "PENDING",
    "storedStatus": "PENDING",
    "expiresOn": null,
    "reviewedBy": null,
    "reviewedAt": null,
    "rejectionReason": null,
    "answerVersion": 1,
    "definitionVersion": 1
  }
]
```

submissions array is required, maximum100 entries; [] is permitted. Each entry requires nonnull requirementId and value; expiresOn is optional/null or YYYY-MM-DD and cannot be in the past when supplied. Duplicate requirement IDs are invalid. All supplied answers save in one transaction; omitted answers are preserved. NONE fields become PROVIDED; reviewed fields become PENDING. Resubmitting increments answerVersion and resets review metadata. Replacing/rejecting supporting evidence returns linked approved details to pending review. A saved answer is not automatically approval or booking eligibility. Customer does not submit customerId, userId, status, reviewer or version flags. Use the server-returned effective status.

## 25. Check customer access

```http
GET /api/v1/customers/me/eligibility?serviceTypeId=3&optionId=12
```

Request body:

None.

Success: 200 OK.

```json
{
  "eligible": false,
  "reasons": [
    {
      "code": "DATE_OF_BIRTH_UNVERIFIED",
      "requirementCode": "DATE_OF_BIRTH",
      "message": "Provide a current approved date of birth"
    }
  ]
}
```

Query context rules match GET requirements. An ineligible result still returns200 for this endpoint; search/booking enforcement returns403 instead. eligible is computed from required blocking fields, review/expiry/current definition state and all applicable global/service/option rules. All age rules apply, so stricter option age limits win. Age uses current date in Asia/Colombo. Keep display reasons separate from availability: an eligible customer can still have zero available providers.

## 26. Time-based provider search

```http
POST /api/v1/customers/me/provider-search
```

Request body:

```json
{
  "marketId": 1,
  "serviceTypeId": 3,
  "optionId": 12,
  "deliveryMode": "PROVIDER_TO_CUSTOMER",
  "startAt": "2030-01-07T08:00:00+05:30",
  "endAt": "2030-01-07T10:00:00+05:30",
  "location": {
    "latitude": 6.9271,
    "longitude": 79.8612
  },
  "radiusKm": 30,
  "languages": [
    "ENGLISH"
  ],
  "requiredSkillIds": [
    10
  ],
  "page": 0,
  "pageSize": 20
}
```

Success: 200 OK.

```json
{
  "checkedAt": "2030-01-01T00:00:00Z",
  "marketId": 1,
  "timezone": "Asia/Colombo",
  "startAt": "2030-01-07T08:00:00+05:30",
  "endAt": "2030-01-07T10:00:00+05:30",
  "providerSelectionMode": "DIRECT_REQUEST",
  "requiredProviderCount": 1,
  "status": "AVAILABLE",
  "totalCandidates": 1,
  "page": 0,
  "pageSize": 20,
  "hasNext": false,
  "candidates": [
    {
      "providerId": 15,
      "assignmentId": 14,
      "optionId": 12,
      "providerLocationId": null,
      "displayName": "Jane Doe",
      "profilePhoto": null,
      "averageRating": 4.8,
      "totalRatings": 25,
      "locationLabel": "APPROXIMATE_SERVICE_AREA",
      "mapPoint": {
        "latitude": 6.93,
        "longitude": 79.86
      },
      "distanceKm": 0.5,
      "remainingCapacity": 1,
      "languages": [
        "ENGLISH",
        "SINHALA"
      ]
    }
  ]
}
```

All top-level required fields: marketId, serviceTypeId, optionId, deliveryMode, startAt. For TIME_BASED, endAt is additionally required. Physical modes require location; ONLINE must omit/null it. TIME_BASED must omit/null pickup and destination. Matching filters are optional and AND-combined: provider must speak every requested language and have every requested approved skill. Required skill IDs must be configured for this service. Do not send BOOKING_FIELD answers to this endpoint: it has no answers/matchingAnswers property.

## 27. Route-based provider search

```http
POST /api/v1/customers/me/provider-search
```

Request body:

```json
{
  "marketId": 1,
  "serviceTypeId": 4,
  "optionId": 20,
  "deliveryMode": "PROVIDER_TO_CUSTOMER",
  "startAt": "2030-01-07T08:00:00+05:30",
  "pickup": {
    "latitude": 6.9271,
    "longitude": 79.8612
  },
  "destination": {
    "latitude": 7,
    "longitude": 79.9
  },
  "radiusKm": 30,
  "languages": [],
  "requiredSkillIds": [],
  "page": 0,
  "pageSize": 20
}
```

Success: 200 OK.

```json
{
  "checkedAt": "2030-01-01T00:00:00Z",
  "marketId": 1,
  "timezone": "Asia/Colombo",
  "startAt": "2030-01-07T08:00:00+05:30",
  "endAt": "2030-01-07T08:30:00+05:30",
  "providerSelectionMode": "DIRECT_REQUEST",
  "requiredProviderCount": 1,
  "status": "NO_AVAILABLE_PROVIDERS",
  "totalCandidates": 0,
  "page": 0,
  "pageSize": 20,
  "hasNext": false,
  "candidates": []
}
```

The response above demonstrates the complete Results shape; actual endAt is derived and candidates/status must come from the real search (do not synthesize them). ROUTE_BASED requires pickup and destination and omits/nulls location and endAt. startAt is pickup time. End time is estimated using straight-line distance and configured average speed (default40km/h), rounded up to at least1minute. This is not a road ETA. Route options support PROVIDER_TO_CUSTOMER and ONE_TO_ONE in current admin validation.

## 28. Complete enum and value catalog

Use only values allowed by the selected option/definition. Arrays containing enums are unordered sets for UI purposes; do not assume the first value is the default.

### Authentication, access and requirement states

| Field | Type / constraints / frontend meaning |
| --- | --- |
| accountType | CUSTOMER, SERVICE_PROVIDER. Customer app always sends CUSTOMER on register/OTP/Google. |
| roles | String array. This app requires CUSTOMER. Other known backend roles include SERVICE_PROVIDER, SUPER_ADMIN, PREMIUM_ADMIN, ADMIN, SUPPORT_ADMIN, FINANCE_ADMIN, VERIFICATION_ADMIN; customer screens must not grant access based on a local role edit. |
| account/profile status | Registration writes PENDING; successful customer authentication/profile writes ACTIVE. Inactive account handling is server controlled. This is a persisted string, not a closed DTO enum; do not treat an unknown status as active. |
| catalog/definition status | ACTIVE, INACTIVE. Customer catalog returns active resources only. |
| access.status | ELIGIBLE, CHECK_OPTION, NEEDS_DETAILS, PENDING_REVIEW, REJECTED, RESTRICTED. |
| access.action | CONTINUE, SELECT_OPTION, COMPLETE_REQUIREMENTS, WAIT_FOR_REVIEW, VIEW_REQUIREMENTS. |
| Eligibility reason.code | MISSING, PENDING, REJECTED, EXPIRED, DATE_OF_BIRTH_UNVERIFIED, INVALID_DATE_OF_BIRTH, MINIMUM_AGE. Unknown future codes should show the server message and offer refresh. |
| Submission effective status | MISSING, PROVIDED, PENDING, APPROVED, REJECTED, EXPIRED. A never-submitted FormItem has submission:null, not an invented submissionId. |
| Submission storedStatus | PROVIDED, PENDING, APPROVED, REJECTED. Can differ from effective status due to expiry, definition changes or evidence changes. |
| Requirement target.scope | GLOBAL (no service/option IDs), SERVICE (serviceTypeId only), OPTION (serviceTypeId+optionId). A definition can have multiple service/option targets. GLOBAL cannot be mixed with other targets. |
| Requirement verificationMode | NONE → PROVIDED; ADMIN_REVIEW → PENDING; DOCUMENT_REVIEW → PENDING and needs review of uploaded or linked evidence. |
| Requirement fieldType / booking fieldType | TEXT, TEXTAREA, NUMBER, BOOLEAN, DATE, TIME, PHONE, EMAIL, SELECT, MULTI_SELECT, IMAGE_UPLOAD, DOCUMENT_UPLOAD, FILE_UPLOAD. |
| Booking field scope | BOOKING_FIELD for customer per-booking questions. PROVIDER_REQUIREMENT can occur in generic option.dynamicFields; do not render it as a customer requirement. Prefer bookingSchema for customer forms. |
| Booking field reuseScope | ASSIGNMENT, SERVICE, PROVIDER exist in generic dynamic definitions. BOOKING_FIELD must use ASSIGNMENT; reuse is not customer eligibility reuse. |
| Requirement level (provider serviceSetup only) | DISABLED, OPTIONAL, REQUIRED. Customer app does not submit provider education/certificates. |
| File type configuration | PDF, JPG, PNG, WEBP for private customer requirements. IMAGE_UPLOAD cannot accept PDF. |
| Condition operator for BOOKING_FIELD | EQUALS (default), NOT_EQUALS. Missing parent answer means condition does not apply. Other operators are not implemented. |
| Languages | SINHALA, ENGLISH, TAMIL. Case-sensitive search values. Null list entries invalid. |
| Responsibility responsibleParty | PROVIDER, CUSTOMER, PLATFORM. Display the selected service/option responsibilities; no customer checkbox/acknowledgment property is currently accepted by booking DTO. |

### Service option and discovery enums

| Field | Type / constraints / frontend meaning |
| --- | --- |
| schedulingModel | TIME_BASED, ROUTE_BASED. No ASAP enum or live dispatch promise. |
| deliveryMode / deliveryModes | PROVIDER_TO_CUSTOMER, CUSTOMER_TO_PROVIDER, ONLINE. Send one mode from returned deliveryModes. |
| fulfillmentModel | ONE_TO_ONE; MANY_CUSTOMERS_ONE_PROVIDER; ONE_CUSTOMER_MANY_PROVIDERS. |
| bookingMode | ONE_AT_A_TIME; MANY_AT_A_TIME. MANY_AT_A_TIME alone does not imply unlimited simultaneous capacity. |
| providerSelectionMode | DIRECT_REQUEST; OPEN_REQUEST. OPEN_REQUEST is configured but automatic dispatch/offer acceptance is not implemented; current final booking still requires provider IDs. |
| pricingOwner | ADMIN; PROVIDER. Current booking prices from admin rates; provider-owned quote workflow is not implemented. |
| allowedPaymentMethods / paymentMethod | CASH, CARD, BANK_TRANSFER, WALLET, EZ_CASH, KOKO. Render only the selected option's allowed subset. Final booking normalizes case/spaces; use uppercase canonical values. |
| deliveryModeConfigurations.providerLocationType | COVERAGE_AREA, FIXED_POINT, NONE. |
| deliveryModeConfigurations.customerLocationInput | MEETING_POINT, PROVIDER_LOCATION_SELECTION, NONE. ROUTE_BASED additionally needs pickup/destination, even though generic delivery configuration says MEETING_POINT. |
| Results.status | AVAILABLE, NO_AVAILABLE_PROVIDERS. |
| Candidate.locationLabel | PUBLIC_SERVICE_LOCATION, APPROXIMATE_SERVICE_AREA, ONLINE. |
| matchingFilters | languages, requiredSkillIds, providerFilters. Dynamic provider filtering requires admin-enabled customerVisible/searchable fields; see CUSTOMER_DISCOVERY_API.md. |
| liveTrackingSupported | Boolean; currently false. No live GPS/heartbeat stream is implemented by these endpoints. |
| Booking status/paymentStatus | Creation writes status:PENDING and paymentStatus:PENDING. Status fields are strings. Overlap rules exclude CANCELLED, COMPLETED, REJECTED, EXPIRED. These overlap values are not a claim that customer lifecycle transition endpoints exist. |

## 29. Field-by-field response and request schemas

Examples above show every property of service, option, context, search, profile and auth responses. The tables below specify nested structures and nullable/default behavior needed to implement a typed client.

### Access and Eligibility

| Field | Type / constraints / frontend meaning |
| --- | --- |
| Access.status / action | Strings from the catalog above. UI summary only; do not store as approval authority. |
| Access.eligibility | Eligibility object, always present. |
| Eligibility.eligible | Boolean; true only when all applicable enforced rules pass. |
| Eligibility.reasons | Reason[]; [] when eligible. |
| Reason.code / requirementCode / message | String / string / string. requirementCode identifies the reusable detail such as NIC or DATE_OF_BIRTH; do not parse IDs out of human messages. |
| CHECK_OPTION | Service-level eligible can be true while access.status is CHECK_OPTION. Always read the selected option. |
| PENDING_REVIEW | Current badge is used when every reason is exactly PENDING. DOB pending review can instead produce DATE_OF_BIRTH_UNVERIFIED and NEEDS_DETAILS; inspect GET requirements submission statuses before asking the customer to re-upload. |

### Media and responsibility objects

| Field | Type / constraints / frontend meaning |
| --- | --- |
| icon / coverImage | ServiceMediaPayload|null; null means no structured image. |
| gallery | ServiceMediaPayload[]; may be []. |
| ServiceMediaPayload.key | String object key, max1024 in admin contract; opaque to customer UI. |
| ServiceMediaPayload.url | String image URL, max2048 in admin contract; use this for display. |
| ServiceMediaPayload.altText | String|null, max300. |
| ServiceMediaPayload.displayOrder | Integer|null. Display ordering, not media identity. |
| responsibilities | ServiceResponsibilityPayload[]; may be []. |
| responsibleParty / title / description | Enum string / string max150 / string|null max1000. |
| required / displayOrder | Boolean / nonnegative integer|null. Required responsibility is configuration; no separate acceptance write endpoint exists. |

### ServiceSetupResponse (nested inside bookingSchema)

| Field | Type / constraints / frontend meaning |
| --- | --- |
| serviceTypeId, name, description, iconUrl, status | Integer; string; nullable string; nullable string; status string. |
| availabilityEnabled / serviceAreasEnabled / optionsRequired | Booleans. Provider setup configuration exposed for rendering context. |
| minimumOptionSelections / maximumOptionSelections | Integer|null; provider service-selection limits, not number of providers needed by a customer booking. |
| documents | DocumentRequirement[]: {documentTypeId:integer, documentTypeName:string, optionId:integer|null, required:boolean, requiresApproval:boolean}. These are PROVIDER documents. |
| skills | SkillRequirement[]: {skillId:integer, name:string, required:boolean, requiresApproval:boolean}. Use IDs as optional requiredSkillIds preferences; server requires approved selected provider skills. |
| certificates / education | SectionRequirement: {level:DISABLED|OPTIONAL|REQUIRED, requiresApproval:boolean}. Provider configuration only. |
| icon / coverImage / gallery / responsibilities | Media and responsibility structures above. |

### ServiceOptionResponse

| Field | Type / constraints / frontend meaning |
| --- | --- |
| optionId / serviceTypeId | Integer IDs identifying option and owning service. |
| code / name / description | String / string / nullable string. code is stable configured identifier, not an ID. |
| displayOrder / status | Integer|null / ACTIVE in customer reads. |
| schedulingModel / routeAverageSpeedKmh | Enum / positive integer|null; null speed defaults40 for route estimation. |
| locationEnabled / deliveryModes | Boolean / enum string[]. Physical search returns no candidates if locationEnabled is false. |
| fulfillmentModel / defaultCapacity / requiredProviderCount | Enum / integer (default1) / integer (default1). Team booking requires that many DISTINCT providers. |
| bookingMode / providerSelectionMode / pricingOwner | Enums as listed above. |
| allowedPaymentMethods | Payment method string[]. Do not offer absent methods. |
| deliveryModeConfigurations | Array of {code:string, providerLocationType:string, providerLocationRequired:boolean, radiusRequired:boolean, customerLocationInput:string}. |
| icon / coverImage / gallery / responsibilities | Complete media/responsibility shapes above. |
| dynamicFields | ServiceCustomFieldResponse[]. May include provider and booking scopes; prefer filtered bookingSchema for customer questions. |

### ServiceDynamicSchemaResponse and ServiceCustomFieldResponse

| Field | Type / constraints / frontend meaning |
| --- | --- |
| bookingSchema.serviceTypeId / serviceName / serviceSetup | Integer / string / complete ServiceSetupResponse. |
| bookingSchema.commonFields | ServiceCustomFieldResponse[] applying to whole service. |
| bookingSchema.options | OptionSchema[]: {optionId:integer, code:string, name:string, fields:ServiceCustomFieldResponse[]}. Context filters to selected option. |
| fieldId / serviceTypeId | Integer; integer. fieldId goes in booking answers. |
| optionId / optionIds | Legacy integer|null / integer[]. Empty optionIds means common; current arrays support multiple options. |
| scope / reuseScope | Strings described in enum catalog; customer booking schema has scope BOOKING_FIELD. |
| code / label / description | String stable code; string display label; string|null help text. |
| icon | Optional media object: {key, url, altText, displayOrder}, or null. Display alongside the provider/booking field label; customer providerFilters carries the same icon. See [field icon configuration](DYNAMIC_FIELDS_API.md#optional-field-icons). |
| fieldType / required / requiresApproval | Type enum; boolean; boolean. BOOKING_FIELD does not use the reusable customer admin approval workflow. |
| minimumFiles / maximumFiles / maximumFileSizeMb | Integer|null. Booking count default minimum1 if required else0; maximum enforced only when configured. Private upload hard maximum100MB remains. |
| allowedFileTypes | String[]; [] means no extra configured restriction beyond supported private formats. |
| options | JSON|null. SELECT/MULTI_SELECT choices can be strings or objects with value, optional label and optional icon media. Display choice icon/label (fallback to value), submit only string value(s). Customer providerFilters preserves these icons too. See [choice icons](DYNAMIC_FIELDS_API.md#icons-for-individual-select-choices). |
| condition | JSON|null. Supported shape: {fieldCode:string, operator:EQUALS|NOT_EQUALS, value:any JSON}. Compare JSON values using their types. |
| displayOrder / version / status | Integer|null; integer schema revision; ACTIVE/INACTIVE (customer booking-schema read includes active definitions). Keep fieldId/code/version for rendering/cache invalidation; final booking only accepts fieldId/value. |

### Customer requirement definition and submission

| Field | Type / constraints / frontend meaning |
| --- | --- |
| FormItem.requirement / submission | Requirement object; Submission|null. |
| Requirement.requirementId / version / status / definition | Integer / integer / status string / Definition. |
| Definition.code / label / description | Stable uppercase code [A-Z][A-Z0-9_]{0,99}; string max200; nullable help text max1000. |
| Definition.fieldType / required / verificationMode / blocksBooking | Enums/booleans. required controls form completion; blocksBooking controls booking enforcement and requires required:true. A service rule may require a globally collected field even if blocksBooking:false. |
| Definition.targets | Target[]: {scope:GLOBAL|SERVICE|OPTION, serviceTypeId:integer|null, optionId:integer|null}. One answer/approval can serve all listed targets. |
| Definition.evidenceCodes | String[] referencing configured reviewed upload requirements. Approve evidence first, then the dependent detail. |
| Definition.choices | String[] used by SELECT/MULTI_SELECT; these are NOT booking field options objects. |
| Definition.minimumFiles / maximumFiles / maximumFileSizeMb | Integer|null. Null defaults:1,1,100 respectively. Configured min/max files1–20 and size1–100MB. |
| Definition.allowedFileTypes / displayOrder | String[]; nonnegative integer. IMAGE_UPLOAD cannot include PDF. |
| Submission.submissionId / customerId / requirementId / code | Integer IDs and code string. customerId is entity ID, not authenticated userId. |
| Submission.value | Typed JSON matching the definition; no status/approval inside value is trusted. |
| Submission.status / storedStatus | Effective and stored states described above. Display effective status. |
| Submission.expiresOn | YYYY-MM-DD|null, submitted metadata subject to review; not a date extracted automatically from NIC. |
| Submission.reviewedBy / reviewedAt / rejectionReason | Reviewer integer|null; local datetime string|null (current review writes UTC clock without offset); string|null. |
| Submission.answerVersion / definitionVersion | Integers. A definition update makes old answers effectively MISSING; resubmission restarts review. |
| Customer PUT entry | {requirementId:integer, value:nonnull JSON, expiresOn:date|null}; no customerId/status/reviewer flags. At most100 entries; omitted answers unchanged. |

### Provider-search request

| Field | Type / constraints / frontend meaning |
| --- | --- |
| marketId / serviceTypeId / optionId | Required nonnull integer IDs; all must form an active market/service/option combination. |
| deliveryMode | Required nonblank string; must be one of option.deliveryModes. |
| startAt | Required ISO OffsetDateTime; must be in the future relative to server Instant.now. |
| endAt | Required for TIME_BASED; must be after startAt. Omit/null for ROUTE_BASED; server derives it. |
| location | Point|null. Required physical TIME_BASED search centre/customer address; forbidden ONLINE and ROUTE_BASED. |
| pickup / destination | Point|null. Both required ROUTE_BASED, both forbidden TIME_BASED. |
| Point.latitude / longitude | Required numeric coordinates; latitude -90..90, longitude -180..180. |
| radiusKm | Number|null; default50; 0.1..500. Distance to centre for fixed locations; travelling provider must also cover pickup/customer point with its own radius. |
| languages | String[]|null; null/[] means no language filter; values from enum; every selected language must be spoken. |
| requiredSkillIds | Integer[]|null; null/[] means no skill preference; each must belong to service; every requested skill must be approved for candidate. |
| page / pageSize | Integer|null; defaults0/20; page>=0; size1..100. Page numbers are offset-based, not cursors. |
| Interval rules | Future interval, maximum31 elapsed days. Converted to market timezone. Nonexistent/ambiguous local times and intervals crossing DST transitions rejected due to existing local-time booking storage. |
| Unsupported request properties | No matchingAnswers, booking answers, providerId, eligibility override, paymentMethod, GPS permission flag or client availability assertion belongs in provider-search. |

### Results and Candidate

| Field | Type / constraints / frontend meaning |
| --- | --- |
| checkedAt / marketId / timezone | Instant timestamp; selected market integer; IANA zone string. |
| startAt / endAt | Offset timestamps for normalized requested interval; route end is estimated. |
| providerSelectionMode / requiredProviderCount | Enum string and integer; team needs that many distinct providers. |
| status | AVAILABLE or NO_AVAILABLE_PROVIDERS. For insufficient team size, candidates is[] even if individual candidates exist. |
| totalCandidates / page / pageSize / hasNext | Integers and boolean. Counts provider-location results (not necessarily distinct people). Can be positive for insufficient team size; hasNext is computed on candidate results. |
| candidates | Candidate[]; results ordered by distance then provider ID then location ID. |
| Candidate.providerId / assignmentId / optionId | Integer provider entity ID / provider-service assignment ID / option ID. Final booking providerId uses Candidate.providerId, NOT assignmentId or userId. |
| Candidate.providerLocationId | Integer|null; fixed public location ID for CUSTOMER_TO_PROVIDER only. |
| displayName / profilePhoto | String display name; nullable public photo URL. No NIC/contact data. |
| averageRating / totalRatings | Numeric|null and integer|null; do not invent ratings for null/unrated providers. |
| locationLabel / mapPoint / distanceKm | Enum; Point|null; number|null (km). ONLINE returns null coordinates/distance. Travelling pins are rounded approximate coverage centres and distance is calculated from that public point. Fixed pins are public business locations. |
| remainingCapacity / languages | Positive integer spare units during entire interval; string[]. It is a search snapshot, not a reservation or guarantee. |
| Pagination UI | Retain request context, increment page until hasNext:false, deduplicate provider/location keys. On time/location/filter change reset page0 and discard stale results. Re-fetch after app resume or booking failure. |

## 30. Dynamic field value examples and rendering rules

Reusable customer requirements and per-booking fields are distinct. Never substitute requirementId for fieldId. Use booleans/numbers as JSON booleans/numbers, not strings. false and0 are valid answers, not missing values.

```json
{
  "TEXT": "Example",
  "TEXTAREA": "Longer instructions",
  "NUMBER": 4.5,
  "BOOLEAN": false,
  "DATE": "2000-05-10",
  "TIME": "08:30:00",
  "PHONE": "+94771234567",
  "EMAIL": "customer@example.com",
  "SELECT": "HOME",
  "MULTI_SELECT": [
    "A",
    "B"
  ],
  "IMAGE_UPLOAD": {
    "files": [
      {
        "key": "customers/25/documents/photo.jpg"
      }
    ]
  },
  "DOCUMENT_UPLOAD": {
    "files": [
      {
        "key": "customers/25/documents/nic.pdf"
      }
    ]
  },
  "FILE_UPLOAD": {
    "files": [
      {
        "key": "customers/25/documents/support.pdf"
      }
    ]
  }
}
```

For customer requirement TEXT/TEXTAREA/DATE/TIME/PHONE/EMAIL/SELECT, nonblank text is required when submitting, max4000characters. DATE/TIME must parse as ISO local values. PHONE accepts an optional + and 7–30 characters consisting of digits/spaces/parentheses/hyphens (different from auth E.164). EMAIL must match the basic address pattern. MULTI_SELECT must be nonempty and have unique configured string values. Upload value must be an object containing files[], each item object with key. Counts/types/sizes and unique keys are checked; ownership is customer-private.

For BOOKING_FIELD, the existing backend validates required fields, condition applicability, JSON type, choices and upload rules at final booking. DATE/TIME/PHONE/EMAIL booking answers currently receive text-type validation rather than the stronger reusable customer validation; the client should still render/validate the correct input. Required fields only apply when their condition applies. Parent fieldCode must refer to another available booking definition. Implement only EQUALS/NOT_EQUALS; compare typed JSON. When parent changes, clear hidden children. Do not invent new operators or map arbitrary booking fields to provider skills.

Example booking condition and choices:

```json
{
  "options": [
    {
      "value": "HOME",
      "label": "Home"
    },
    {
      "value": "HOSPITAL",
      "label": "Hospital"
    }
  ],
  "condition": {
    "fieldCode": "CARE_LOCATION",
    "operator": "EQUALS",
    "value": "HOSPITAL"
  }
}
```

## 31. Exact availability and map behavior

The backend filters active provider accounts, service assignments and provider/admin option statuses/approval, market, supported delivery, weekly schedule coverage, unavailable dates, active service areas and remaining capacity. Provider UNAVAILABLE excludes candidates. OFFLINE alone does not exclude a future scheduled appointment; this is not an immediate-dispatch online-presence system.

ONE_AT_A_TIME blocks overlapping work across options of the same main service and advertises at most1slot. An existing exclusive option also blocks a new nonexclusive option in that service. It does not impose exclusivity across unrelated services. MANY_AT_A_TIME + MANY_CUSTOMERS_ONE_PROVIDER uses option defaultCapacity or fixed location capacity override, subtracting peak simultaneous occupied units. MANY_AT_A_TIME + ONE_TO_ONE still blocks overlapping work for the same option. Team models need the configured distinct count; search gives individuals, not a grouped/reserved team or a guarantee of a shared venue.

Intervals are [start,end): one booking ending10:00 does not conflict with one starting10:00. Weekly slots must cover the whole interval; adjacent slots merge, gaps do not. Unavailable dates override weekly hours. If availability configuration is disabled, those schedule/date checks are skipped. Active areas must match market and delivery mode. PROVIDER_TO_CUSTOMER requires actual coverage and search radius; CUSTOMER_TO_PROVIDER returns fixed public locations in radius; ONLINE returns no physical pins.

Cancelled/completed/rejected/expired jobs do not consume capacity. Pending jobs currently do, until their status changes. Final booking locks providers in stable order and rechecks current capacity; search has no hold. Do not show a busy/full provider as available based on cached list items. Do not explain hidden providers with another customer's private booking data.

Do not call approximate service-area pins live positions. liveTrackingSupported:false means no Uber-style moving positions, heartbeat, road ETA, acceptance stream or automatic dispatch exists here. Road routing, live presence, matching/acceptance, capacity holds, idempotent booking retries, provider quote collection and payment settlement are later modules.

## 32. Existing final booking handoff — full request and response

```http
POST /api/v1/customers/me/bookings
Authorization: Bearer <customer-access-token>
Content-Type: application/json
```

TIME_BASED / PROVIDER_TO_CUSTOMER request:

```json
{
  "serviceTypeId": 3,
  "optionId": 12,
  "providerId": 15,
  "providerIds": null,
  "deliveryMode": "PROVIDER_TO_CUSTOMER",
  "startDatetime": "2030-01-07T08:00:00",
  "expectedEndDatetime": "2030-01-07T10:00:00",
  "requestedPickupTime": null,
  "pickup": null,
  "destination": null,
  "providerLocationId": null,
  "meetingPoint": {
    "name": "Customer home",
    "addressLine1": "10 Main Street",
    "addressLine2": null,
    "city": "Colombo",
    "district": "Colombo",
    "province": "Western",
    "postalCode": "00100",
    "country": "Sri Lanka",
    "latitude": 6.9271,
    "longitude": 79.8612,
    "instructions": "Use the front entrance"
  },
  "customerNote": "Please call on arrival",
  "answers": [
    {
      "fieldId": 20,
      "value": "Routine care instructions"
    }
  ],
  "paymentMethod": "CASH"
}
```

Success:201 Created. Complete BookingResponse:

```json
{
  "jobId": 1001,
  "serviceTypeId": 3,
  "optionId": 12,
  "providerId": 15,
  "providerIds": [
    15
  ],
  "deliveryMode": "PROVIDER_TO_CUSTOMER",
  "schedulingModel": "TIME_BASED",
  "fulfillmentModel": "ONE_TO_ONE",
  "capacityUsed": 1,
  "providerLocationId": null,
  "meetingPointName": "Customer home",
  "latitude": 6.9271,
  "longitude": 79.8612,
  "startDatetime": "2030-01-07T08:00:00",
  "expectedEndDatetime": "2030-01-07T10:00:00",
  "destinationLatitude": null,
  "destinationLongitude": null,
  "destinationAddress": null,
  "estimatedDistanceKm": null,
  "estimatedDurationMinutes": null,
  "rateId": 40,
  "currencyCode": "LKR",
  "expectedAmount": 2400,
  "paymentMethod": "CASH",
  "paymentStatus": "PENDING",
  "status": "PENDING",
  "answers": {
    "CARE_NOTES": "Routine care instructions"
  }
}
```

Required: serviceTypeId, optionId, deliveryMode, valid provider selection, correct time/location shape. For a single provider send providerId; alternatively use providerIds. Team requests must contain exactly requiredProviderCount distinct providers; the backend unions providerId/providerIds and rejects null IDs. For fixed venue teams, member locations must be compatible with the one booking venue; search is not a grouped team selection API.

TIME_BASED requires startDatetime before expectedEndDatetime, with no route fields. These are market-local LocalDateTime strings WITHOUT offset; convert search timestamps using market timezone, not phone timezone. Future-time and maximum31day checks are performed in the market zone. PROVIDER_TO_CUSTOMER needs meetingPoint and no providerLocationId. CUSTOMER_TO_PROVIDER needs the selected candidate.providerLocationId and no meetingPoint. ONLINE needs neither. Do not reuse a providerLocationId from a different provider/option/market.

ROUTE_BASED booking request (actual times, address strings and IDs must come from customer input/configuration):

```json
{
  "serviceTypeId": 4,
  "optionId": 20,
  "providerId": 15,
  "deliveryMode": "PROVIDER_TO_CUSTOMER",
  "requestedPickupTime": "2030-01-07T08:00:00",
  "pickup": {
    "address": "10 Main Street, Colombo",
    "latitude": 6.9271,
    "longitude": 79.8612
  },
  "destination": {
    "address": "Destination address",
    "latitude": 7,
    "longitude": 79.9
  },
  "customerNote": null,
  "answers": [],
  "paymentMethod": "CASH"
}
```

ROUTE_BASED forbids startDatetime, expectedEndDatetime, meetingPoint and providerLocationId; the backend derives the time interval/distance using the current straight-line estimate. RoutePoint.address is required nonblank max500; coordinates required within latitude/longitude bounds. Selection and capacity are checked again for every selected provider.

MeetingPoint fields: name required max150; addressLine1 required max255; addressLine2 optional max255; city/district/province/country optional max100 each; postalCode optional max30; latitude/longitude required within bounds; instructions optional max1000. customerNote optional max4000.

answers is optional/null or [{fieldId:integer,value:nonnull JSON}]. Submit common + selected-option BOOKING_FIELD answers only. The response answers is an object keyed by field code, not the request array or customer requirement code. Follow the field type/condition/choice/file rules above. Missing required active booking fields, invalid IDs/choices/types or private file ownership fail400.

paymentMethod is optional; omitted/blank defaults CASH ONLY when CASH is allowed. Otherwise the customer must choose an allowed method. A valid configured rate is required; backend currently uses admin pricing even if option.pricingOwner is PROVIDER, so do not advertise provider quotes as operational. Actual payment collection is not implemented by recording this field. Expected price is computed at booking, not returned by provider search; no customer preview endpoint is implemented. PENDING is not payment success or provider acceptance.

BookingResponse field dictionary: jobId/serviceTypeId/optionId/providerId are integer IDs; providerIds is integer[]; deliveryMode/schedulingModel/fulfillmentModel are the enums above; capacityUsed is integer (currently1); providerLocationId nullable integer; meetingPointName nullable string; latitude/longitude nullable numbers; startDatetime/expectedEndDatetime market-local strings; destinationLatitude/destinationLongitude/destinationAddress nullable number/number/string; estimatedDistanceKm nullable number; estimatedDurationMinutes nullable integer; rateId integer; currencyCode string; expectedAmount decimal number; paymentMethod string; paymentStatus/status string; answers object with code→typed JSON. No price/tax/route values may be asserted by the client to override server calculation.

GET /api/v1/customers/me/bookings has no body/query and returns200 with BookingResponse[] in newest-created-first order, or[]. It returns only the authenticated customer's records. It is currently unpaginated. Single-booking GET, customer cancellation, booking holds, booking previews, dispatch and payment callback routes described in the planning document are NOT implemented customer contracts.

## 33. Error contracts and frontend handling

Business/DTO validation errors use application/problem+json. Security-filter401/403, malformed JSON/parameter binding, servlet upload failures and unexpected exceptions may use Spring/container error bodies instead. Inspect HTTP status/content type before decoding; do not assume every error contains errors or eligibility.

Validation400 example:

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "The request contains invalid fields",
  "instance": "/api/v1/customers/me/provider-search",
  "errors": {
    "location.latitude": "must be greater than or equal to -90"
  }
}
```

Invalid business value400 example:

```json
{
  "type": "about:blank",
  "title": "Invalid request value",
  "status": 400,
  "detail": "Choose a future interval of at most 31 days",
  "instance": "/api/v1/customers/me/provider-search"
}
```

Customer eligibility403 example (search/booking only):

```json
{
  "type": "about:blank",
  "title": "Customer eligibility requirements not satisfied",
  "status": 403,
  "detail": "Customer requirements must be completed before booking",
  "instance": "/api/v1/customers/me/provider-search",
  "eligibility": {
    "eligible": false,
    "reasons": [
      {
        "code": "MISSING",
        "requirementCode": "NIC",
        "message": "National identity card: MISSING"
      }
    ]
  }
}
```

Missing profile404 example:

```json
{
  "type": "about:blank",
  "title": "Resource not found",
  "status": 404,
  "detail": "Customer profile has not been completed",
  "instance": "/api/v1/customers/me/profile"
}
```

OTP cooldown429 example:

```json
{
  "type": "about:blank",
  "title": "Too many requests",
  "status": 429,
  "detail": "Please wait before requesting another OTP",
  "retryAfterSeconds": 43
}
```

On429, read Retry-After header and/or retryAfterSeconds; disable resend until allowed. On401, refresh once using serialized token rotation, then sign out if refresh fails. On403 with eligibility, show requirements/reasons and preserve selected service/option. On403 without eligibility, check role/access; do not keep retrying. On404 profile, open profile; on404 service/option/market, refresh catalog and clear invalid selection. On400 show errors keyed to field paths or business detail; if availability changed, search again. On409 reread changed state before retrying. On5xx show a retryable server failure without exposing tokens/private evidence. A network timeout after a booking POST has uncertain outcome; check own booking history before manual retry because no booking idempotency key is implemented.

Application status vocabulary and HTTP errors are different: NO_AVAILABLE_PROVIDERS is200 with empty candidates; ineligible GET eligibility is200 with eligible:false; attempted provider-search when ineligible is403. Final booking currently reports changed capacity as400 Invalid request value, not a dedicated409 availability enum. Do not implement a nonexistent conflict-code parser.

File failures can be400 business errors or413 for multipart size. Current global multipart exception detail says Image must not exceed10MB even for a document request; this generic message is an implementation inconsistency. Enforce documented per-endpoint/field limits in the app and handle413 by HTTP status instead of treating that text as document configuration.

## 34. Frontend state, caching and acceptance checklist

- Keep auth tokens secure; never log them. user.marketId is null for customers. Keep selected market, service, option, delivery mode, time/location and search page in app state.
- After login, load profile. After selecting a market, load market services. After selecting service, load options. Read selected option/context before building the form. Restricted services remain visible.
- Separate reusable requirements from booking-specific questions. Display per-item submission.status and rejectionReason; approved evidence can be reused. Replacing evidence restarts linked verification. No customer button can set APPROVED.
- Store a return destination during requirements/review. There is no eligibility push stream; recheck on returning to the app, after a submission, or on explicit refresh. Wait for real admin approval; do not locally advance eligibility after upload.
- Use commonFields + selected option.fields from bookingSchema; render returned definitions. Clear conditional children when their parent changes. Do not ask customers to fill provider setup requirements from serviceSetup.
- Request actual device location permission in the app; offer manual location input if denied. For ONLINE, no coordinate is necessary. Geocoding/map rendering is frontend infrastructure, not a backend route in this contract.
- Before searching, collect valid future time context, physical location or route points, and supported language/skill preferences. Reset search pages when any context changes; discard stale in-flight responses using a request key.
- Only display returned available candidates for the exact context. Fixed business locations can be map pins with their location IDs; travelling providers use approximate centres. No live position/ETA claim. Show no-results with change-time/location actions.
- Retain provider entity IDs, not assignment/user IDs, for final booking. For teams, need distinct compatible people; multiple location pins for one provider do not form a team.
- Render only allowed payment methods. Search contains no price quote and makes no reservation. Final booking repeats eligibility, schedule, coverage, provider approval and capacity checks.
- Treat response strings as extensible where DTOs use persisted strings. Handle unknown enums conservatively with a refresh/fallback instead of silently permitting booking.
- Test empty catalogs/options, missing profile, pending/rejected/expired evidence, age restriction, null photo/rating, invalid route/time combinations, out-of-radius/no capacity, multi-location duplicates, pagination reset, stale availability, token refresh failure, upload413 and uncertain booking timeout.

## 35. Implementation boundaries and source of truth

Current APIs support customer authentication/profile, market catalog, dynamic reusable forms and review states, exact option context, scheduled provider discovery, language/approved-skill matching and final booking validation. They do not yet supply automatic OPEN_REQUEST dispatch, provider quote collection, live GPS/road ETA, holds, automatic payment settlement/refunds, or a full customer cancellation/status-transition API. Do not implement those screens against guessed URLs. The initial candidate scan/catalog access evaluation uses repeated repository reads and in-memory filtered pagination; large deployment needs batching/spatial indexes/load tests. Unit tests do not substitute for production MySQL contention testing.

Source of truth for this reference: AuthController and auth DTOs; CustomerProfileController/Service and profile DTOs; MarketController/MarketResponse; CustomerDiscoveryController/Service and CustomerDiscoveryApi; CustomerRequirementsController/Service and CustomerRequirementsApi; FileUploadController and S3 private/image services; ServiceOptionResponse, ServiceDynamicSchemaResponse, ServiceCustomFieldResponse, ServiceSetupResponse; CustomerBookingController/Service, CreateBookingRequest and BookingResponse; GlobalExceptionHandler and security configuration. This document changes documentation only, not runtime APIs. Dates and deployment configuration must be verified separately before release.



## Pricing field discovery metadata

`GET /api/v1/customers/me/booking-context` returns top-level `pricingFieldIds`,
for example `[45, 46]`. Join to `providerFilters[].fieldId` for field definitions;
collect exactly one choice per ID in quote/booking `pricingSelections`.
Optional paired `pricingLatitude` and `pricingLongitude` query parameters include
applicable area rates. Without coordinates only general rates participate.
Refresh after pricing-location changes; marker fields are independent.
See [full contract and JSON sample](COMBINATION_PRICING_API.md#customer-pricing-field-discovery).


### Public provider images

Booking context includes `providerDisplayFields` for public field labels/types,
including display-only IMAGE_UPLOAD fields. Join by fieldId to each provider-search
candidate's `providerAttributes`; image values are arrays of `{key,url,expiresAt}`.
Render as a gallery for that provider/service option. Only approved public images
are returned; signed URLs expire after ten minutes. Images are not search filters.
See [configuration, upload and response examples](DYNAMIC_FIELDS_API.md#customer-visible-provider-image-galleries).


### Customer display support for every provider field type

All unconditional PROVIDER_REQUIREMENT types now support explicit customerVisible.
Booking-context `providerDisplayFields` supplies the labels and fieldTypes; join
by fieldId to provider-search candidate `providerAttributes`. TEXT/TEXTAREA/DATE/
TIME/PHONE/EMAIL return strings. Documents/files return arrays of
`{key,url,expiresAt}`, like images; render as view/download links. Signed URLs last
ten minutes. Only approved public manual answers are included. Searchable remains
restricted to SELECT/MULTI_SELECT/NUMBER/BOOLEAN. Booking questions remain separate.
See [configuration and examples](DYNAMIC_FIELDS_API.md#customer-visibility-for-all-provider-field-types).
