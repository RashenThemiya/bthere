# API versioning

JobHub uses URL-based API versioning.

Current version:

```text
/api/v1
```

Examples:

```text
POST /api/v1/auth/login
GET  /api/v1/users/me
POST /api/v1/admin/provider-types/setup
GET  /api/v1/provider-types/{serviceTypeId}/booking-schema
POST /api/v1/customers/me/bookings
```

All controllers must expose routes below `/api/v1`. New backward-compatible
fields and endpoints remain in V1. A `/api/v2` route is introduced only for a
breaking contract change, and V1 remains available during the frontend
migration period.

V1 compatibility rules:

- New JSON request properties are optional or have backend defaults.
- Existing response properties are retained.
- Replaced endpoints remain available as deprecated aliases during migration.
- `schedulingModel` defaults to `TIME_BASED` for older option payloads.
- Missing booking `answers` defaults to an empty list.
- Legacy assignment-level `/service-areas` routes remain supported and map to
  the provider's first selected active option.

API versioning is separate from the Spring Boot application version and from
database schema management.
