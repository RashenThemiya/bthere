# Current Frontend API Contract

This document records the contract implemented by the backend as of 2026-10-05.
Where another document conflicts with this file, the Java request and response DTOs
remain the runtime source of truth. Alternate field names mentioned below are not
aliases and must not be used by clients.

## Authentication

### Email registration

`POST /api/v1/auth/register`

```json
{
  "email": "provider@example.com",
  "password": "StrongPassword123!",
  "accountType": "SERVICE_PROVIDER"
}
```

- `email`, `password`, and `accountType` are required.
- `accountType` is `CUSTOMER` or `SERVICE_PROVIDER`.
- The backend generates a unique username from the email address.

### Password login

`POST /api/v1/auth/login`

```json
{
  "identifier": "provider001",
  "password": "StrongPassword123!"
}
```

`identifier` may contain either a username or an email address.

### Phone OTP registration and login

`POST /api/v1/auth/otp/request`

```json
{
  "phoneNumber": "+94771234567",
  "accountType": "SERVICE_PROVIDER"
}
```

`POST /api/v1/auth/otp/verify`

```json
{
  "phoneNumber": "+94771234567",
  "otp": "123456",
  "accountType": "SERVICE_PROVIDER"
}
```

- `accountType` is required on both requests.
- A missing phone number is registered with the supplied account type.
- An existing active phone number with the same account type is logged in.
- `purpose` and `type` are not request fields.

### Logout

- `POST /api/v1/auth/logout` requires `{ "refreshToken": "..." }` and revokes that
  session.
- `POST /api/v1/auth/logout-all` requires bearer authentication and no request body;
  it revokes all sessions belonging to the authenticated user.

## Provider profile

Both endpoints require a bearer token with the `SERVICE_PROVIDER` role.

### Get profile

`GET /api/v1/providers/me/profile`

```json
{
  "serviceProviderId": 15,
  "userId": 42,
  "firstName": "Nimal",
  "lastName": "Perera",
  "nicNumber": "...",
  "passportNumber": null,
  "dateOfBirth": "1990-01-31",
  "gender": "MALE",
  "profilePhoto": "https://...",
  "bio": "...",
  "verificationStatus": "PENDING",
  "availabilityStatus": "AVAILABLE",
  "status": "ACTIVE",
  "averageRating": 0.00,
  "totalRatings": 0,
  "marketId": 1,
  "profileCompleted": true
}
```

### Create or replace profile

`PUT /api/v1/providers/me/profile`

```json
{
  "firstName": "Nimal",
  "lastName": "Perera",
  "nicNumber": "...",
  "passportNumber": null,
  "dateOfBirth": "1990-01-31",
  "gender": "MALE",
  "profilePhoto": "https://...",
  "bio": "...",
  "availabilityStatus": "AVAILABLE",
  "marketId": 1
}
```

Required fields are `firstName`, `lastName`, `dateOfBirth`, `gender`,
`availabilityStatus`, and `marketId`. `availabilityStatus` is `AVAILABLE`,
`UNAVAILABLE`, or `OFFLINE`. `nicNumber`, `passportNumber`, `profilePhoto`, and
`bio` are optional. The response is the same shape as the GET response.

The older `fullName`, `preferredName`, contact, and address fields are not part of
these provider-profile DTOs.

## Uploaded document references

`POST /api/v1/uploads/documents` returns both forms:

```json
{
  "key": "providers/42/documents/uuid.pdf",
  "url": "s3://bucket/providers/42/documents/uuid.pdf",
  "contentType": "application/pdf",
  "size": 12345
}
```

- Provider documents use required `documentKey` and should receive `key`.
- Professional certificates use required `documentKey` and should receive `key`.
- Education certificates use optional `documentKey` and should receive `key`.
- `documentUrl` and `s3ObjectKey` are not implemented request fields.

## Markets

Market create/update requests and responses use:

```json
{
  "name": "Sri Lanka",
  "countryCode": "LK",
  "currencyCode": "LKR",
  "timezone": "Asia/Colombo",
  "locale": "en-LK",
  "phoneCode": "+94"
}
```

Responses additionally contain `id`, `status`, `createdAt`, and `updatedAt`.
The persisted entity retains internal legacy column names, but the public API uses
`currencyCode` and `phoneCode` for both requests and responses.

## Admin user creation

`POST /api/v1/admin/users` uses `accountType`:

```json
{
  "username": "admin001",
  "email": "admin@example.com",
  "phoneNumber": "+94771234567",
  "password": "StrongAdminPassword!",
  "accountType": "ADMIN"
}
```

`username`, `email`, `password`, and `accountType` are required; `phoneNumber` is optional.
Supported role values are `CUSTOMER`, `SERVICE_PROVIDER`, `PREMIUM_ADMIN`, `ADMIN`,
`SUPPORT_ADMIN`, `FINANCE_ADMIN`, and `VERIFICATION_ADMIN`.
