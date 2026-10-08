# JobHub Authentication API

Frontend integration reference for registration, verification, login, and authenticated-user requests.

## Base URL

Production:

```text
https://jobhub.pentarixlabs.com
```

All request and response bodies use JSON.

```http
Content-Type: application/json
```

## Account types

Public registration supports:

```text
CUSTOMER
SERVICE_PROVIDER
```

Administrative accounts are created by a Super Admin and sign in with username/password. They cannot use Google or phone OTP authentication.

## Recommended email registration flow

```text
Select CUSTOMER or SERVICE_PROVIDER
    -> POST /api/v1/auth/register
    -> show email OTP screen
    -> POST /api/v1/auth/email/verify
    -> store returned tokens
    -> call GET /api/v1/users/me
    -> continue to profile completion
```

## 1. Register with email and password

```http
POST /api/v1/auth/register
```

No authentication is required.

### Request

```json
{
  "email": "customer@example.com",
  "password": "StrongPassword123",
  "accountType": "CUSTOMER"
}
```

The backend generates a unique username from the email address.

### Validation

| Field | Rules |
|---|---|
| `email` | Required; valid email; maximum 254 characters |
| `password` | Required; 10-72 characters |
| `accountType` | Required; `CUSTOMER` or `SERVICE_PROVIDER` |

Emails are normalized to lowercase and must be unique.

### Success: `201 Created`

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

The backend sends a six-digit OTP to the email address. The user cannot log in until email verification changes the account status to `ACTIVE`.

### Common errors

- `400 Bad Request`: invalid fields.
- `409 Conflict`: email is already registered.
- `429 Too Many Requests`: an OTP was requested again before the resend cooldown ended.
- `500 Internal Server Error`: email delivery/configuration failed.

## 2. Verify registration email OTP

```http
POST /api/v1/auth/email/verify
```

No authentication is required.

### Request

```json
{
  "email": "customer@example.com",
  "otp": "123456"
}
```

The OTP must contain exactly six digits. By default, it expires after five minutes and permits a maximum of five failed attempts.

### Success: `200 OK`

Successful verification activates the account and signs the user in.

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "random-refresh-token",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": 25,
    "username": "customer01",
    "email": "customer@example.com",
    "status": "ACTIVE",
    "roles": [
      "CUSTOMER"
    ]
  }
}
```

`expiresIn` is the access-token lifetime in seconds.

### Errors

- `400 Bad Request`: invalid email or OTP format.
- `401 Unauthorized`: OTP is invalid, expired, already used, or exceeded its attempt limit.

## 3. Resend email verification OTP

```http
POST /api/v1/auth/email/resend
```

No authentication is required.

### Request

```json
{
  "email": "customer@example.com"
}
```

### Success: `202 Accepted`

```json
{
  "message": "If the email is awaiting verification, a code has been sent",
  "expiresIn": 300
}
```

The response is intentionally generic, including when the email does not exist or is already verified. This prevents account discovery.

### Errors

- `400 Bad Request`: invalid email.
- `429 Too Many Requests`: resend requested before the cooldown ended. The default cooldown is 60 seconds.

## 4. Login with username/email and password

```http
POST /api/v1/auth/login
```

No authentication is required. OTP is not required for normal login after email verification.

### Request using username or email

```json
{
  "identifier": "customer01",
  "password": "StrongPassword123"
}
```

The `identifier` field accepts an email too:

```json
{
  "identifier": "customer@example.com",
  "password": "StrongPassword123"
}
```

### Success: `200 OK`

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "random-refresh-token",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": 25,
    "username": "customer01",
    "email": "customer@example.com",
    "status": "ACTIVE",
    "roles": [
      "CUSTOMER"
    ]
  }
}
```

### Errors

- `400 Bad Request`: identifier or password is blank.
- `401 Unauthorized`: credentials are incorrect or the account is not `ACTIVE`.

For security, the backend returns the same message for an unknown account and an incorrect password:

```json
{
  "title": "Authentication failed",
  "status": 401,
  "detail": "Invalid username or password"
}
```

## 5. Use the access token

Send the access token on protected requests:

```http
Authorization: Bearer <access-token>
```

Example:

```http
GET /api/v1/users/me
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### Success: `200 OK`

```json
{
  "id": 25,
  "username": "customer01",
  "email": "customer@example.com",
  "status": "ACTIVE",
  "roles": [
    "CUSTOMER"
  ]
}
```

Missing, expired, or invalid access tokens return `401 Unauthorized`.

## 6. Google login/registration

Google authentication is intended only for `CUSTOMER` and `SERVICE_PROVIDER` accounts.

```http
POST /api/v1/auth/google
```

The frontend obtains a Google ID token using Google Identity Services and sends it to JobHub.

### Request

```json
{
  "idToken": "google-id-token",
  "accountType": "CUSTOMER"
}
```

`accountType` is required on every request. It is used when creating a new account;
an existing linked account retains its saved role.

### Success: `200 OK`

Returns the same `LoginResponse` used by email/password login.

The backend verifies the Google signature, issuer, expiry, audience, and verified-email claim. Google login is unavailable until `GOOGLE_CLIENT_ID` is configured correctly.

### Errors

- `400 Bad Request`: ID token or account type is missing.
- `401 Unauthorized`: invalid Google token, inactive account, broken link, or an administrative account attempted Google login.

## 7. Phone OTP registration/login

Phone OTP authentication is intended only for `CUSTOMER` and `SERVICE_PROVIDER` accounts.

### Request an OTP

```http
POST /api/v1/auth/otp/request
```

The same request is used for registration and login:

```json
{
  "phoneNumber": "+94771234567",
  "accountType": "SERVICE_PROVIDER"
}
```

### Success: `202 Accepted`

```json
{
  "message": "If the phone number is eligible, an OTP has been sent",
  "expiresIn": 300
}
```

The response is intentionally generic to prevent phone-number discovery.

### Verify an OTP

```http
POST /api/v1/auth/otp/verify
```

```json
{
  "phoneNumber": "+94771234567",
  "otp": "123456",
  "accountType": "SERVICE_PROVIDER"
}
```

`accountType` is always required. If the phone number does not exist, the backend
registers it with that account type. If it exists and its account type matches,
the backend logs it in. The generic OTP-request response prevents account discovery.

### Success: `200 OK`

Returns the standard `LoginResponse`.

Phone OTP is unavailable until Text.lk is enabled with a valid API token and sender ID. Initial values can be supplied through `TEXTLK_API_TOKEN` and `TEXTLK_SENDER_ID`. A `SUPER_ADMIN` can update them through `PUT /api/v1/admin/settings/sms`:

```json
{
  "apiToken": "new-token-or-omit-to-keep-current",
  "senderId": "CareHub",
  "enabled": true
}
```

`GET /api/v1/admin/settings/sms` returns the current sender and enabled state, but only a masked version of the API token.

## 8. Super Admin creates an account without OTP

Only an authenticated `SUPER_ADMIN` can call this endpoint.

```http
POST /api/v1/admin/users
Authorization: Bearer <super-admin-access-token>
```

### Create a customer

```json
{
  "username": "customer02",
  "email": "customer02@example.com",
  "phoneNumber": "+94771234568",
  "password": "StrongPassword123",
  "accountType": "CUSTOMER"
}
```

### Create a service provider

```json
{
  "username": "provider02",
  "email": "provider02@example.com",
  "phoneNumber": "+94771234569",
  "password": "StrongPassword123",
  "accountType": "SERVICE_PROVIDER"
}
```

Admin-created accounts are immediately `ACTIVE`, their email is marked verified, and no OTP is sent. The password for this endpoint must be 12-72 characters.

## Token handling

- Send `accessToken` as a Bearer token on protected API calls.
- The default access-token lifetime is 900 seconds (15 minutes).
- Do not log either token.
- Prefer keeping the access token in application memory rather than persistent browser storage.
- Treat the refresh token as a credential with the same sensitivity as a password.
- Refresh tokens are rotated: replace the stored refresh token after every successful refresh.

## Refresh and logout

Refresh an expired or nearly expired access token:

```http
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "current-refresh-token"
}
```

Success returns the standard login response with a new access token and a new refresh token. The previous refresh token immediately becomes invalid.

Revoke one refresh session:

```http
POST /api/v1/auth/logout
Content-Type: application/json

{
  "refreshToken": "current-refresh-token"
}
```

Success returns `204 No Content`.

Revoke every refresh session for the current user:

```http
POST /api/v1/auth/logout-all
Authorization: Bearer <access-token>
```

Success returns `204 No Content`. Existing access tokens remain valid until their short expiry; all refresh sessions are revoked.

## Standard error format

Business and validation errors use `application/problem+json` and generally follow this structure:

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "The request contains invalid fields",
  "instance": "/api/v1/auth/register",
  "errors": {
    "email": "Email must be valid",
    "password": "Password must contain between 10 and 72 characters"
  }
}
```

Possible status codes:

| Status | Meaning |
|---|---|
| `200` | Successful login, verification, Google authentication, OTP verification, or authenticated read |
| `201` | Account created; email verification still required |
| `202` | OTP request/resend accepted |
| `400` | Validation or invalid request value |
| `401` | Authentication failed, token invalid/expired, or OTP invalid/expired |
| `403` | Authenticated account does not have the required role |
| `409` | Username, email, or phone already exists |
| `429` | OTP cooldown or request limit reached |
| `500` | Unexpected server or provider configuration failure |

## Frontend state recommendations

Recommended authentication states:

```text
SIGNED_OUT
REGISTERING
AWAITING_EMAIL_VERIFICATION
AUTHENTICATED
TOKEN_EXPIRED
```

After every successful login or verification:

1. Save the returned authentication state securely.
2. Set the Bearer access token on protected requests.
3. Use the returned `user.roles` to choose the customer, provider, or admin interface.
4. Call `GET /api/v1/users/me` when restoring an existing session.

Do not grant frontend access based only on hidden buttons or local role state. The backend remains responsible for authorization.
