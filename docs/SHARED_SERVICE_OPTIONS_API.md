# Shared service options

An option can belong to multiple main services using the same option ID. Its name,
description, media, responsibilities, delivery, booking and payment configuration are
shared. Status and display order belong to each service–option link.

## Deployment

Apply [SHARED_SERVICE_OPTIONS_SCHEMA.sql](SHARED_SERVICE_OPTIONS_SCHEMA.sql) before
deploying against an existing MySQL database. Hibernate schema update alone cannot
backfill existing memberships. The script retains existing option IDs and copies
each original service membership, order and status into the new table. The script
can be rerun without duplicating links or reactivating inactive links.

## Admin API

All endpoints require `SUPER_ADMIN`; paths start with `/api/v1/admin`.

| Method | Path | Behavior |
| --- | --- | --- |
| GET | `/service-options?page=0&size=20` | Shared catalog with option IDs and all service links |
| POST | `/provider-types/{serviceTypeId}/options` | Create an option and its first link; existing request format |
| POST | `/provider-types/{serviceTypeId}/options/{optionId}/link` | Attach an existing option, or reactivate its existing link |
| DELETE | `/provider-types/{serviceTypeId}/options/{optionId}/link` | Deactivate only this service's link |
| GET | `/provider-types/{serviceTypeId}/options` | List linked options, including inactive links |
| PUT | `/provider-types/{serviceTypeId}/options/{optionId}` | Edit shared details and this link's display order |
| PATCH | `/provider-types/{serviceTypeId}/options/{optionId}/status` | Set this link's status |

Link request: `{"displayOrder": 2}` or `{}`. Omitted order defaults to zero on new
links and retains the current order on existing links. Linking never creates a
duplicate option. Conflicting option codes within a service return HTTP 409.
Renaming a shared option's code checks every linked service, including inactive links.

Example: create “Meal preparation” under Elder care, then link its returned option ID
under Home help. Both services return that same ID. Editing its name changes both
services; deactivating its Elder care link leaves Home help active.

Responses from service-specific endpoints report the requested `serviceTypeId`,
that link's `displayOrder` and `status`, and that service's dynamic fields.
Dynamic fields remain service-specific and are configured separately after linking.
Provider selections and approvals remain keyed by assignment and option, so linking
does not grant provider approval in another service. Market prices remain scoped to
market, service and option.

Inactive links are hidden from public lists and rejected for new bookings, discovery,
provider option selection and availability configuration. Historical bookings,
provider approvals and dynamic-field references remain stored. Relinking reactivates
the same membership. Shared options retain their existing ID when linked elsewhere;
separate preexisting options are not automatically merged.
