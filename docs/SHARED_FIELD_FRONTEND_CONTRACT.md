# Shared dynamic fields: frontend contract

The provider-type GET returns effective fields, including fields inherited through
linked options. Do not reject a response because a field's serviceTypeId differs
from the requested service. serviceTypeId remains the historical field owner.

This build adds ownerServiceTypeId (same owner ID) and configurationScope:
SHARED_OPTION for option-associated fields, SERVICE for common fields. These
properties require deployment. Older clients can derive them from serviceTypeId
and optionIds/legacy optionId. A shared field is inherited in the current service
when its owner differs from that service. An equal owner does not make it local.

For an option editor prefer:
`GET /api/v1/admin/service-options/4/custom-fields`.
The provider-type GET includes fields for all linked options. Illustrative
response projection before reconciliation (other normal properties omitted):

```json
[
  {"fieldId":5,"serviceTypeId":2,"ownerServiceTypeId":2,"configurationScope":"SHARED_OPTION","optionId":4,"optionIds":[4],"scope":"PROVIDER_REQUIREMENT","code":"RIDE_CARE_VEHICLE_TYPE","fieldType":"SELECT","status":"ACTIVE"},
  {"fieldId":7,"serviceTypeId":1,"ownerServiceTypeId":1,"configurationScope":"SHARED_OPTION","optionId":4,"optionIds":[4],"scope":"PROVIDER_REQUIREMENT","code":"CG_CARE_RIDE_VEHICLE_TYPE","fieldType":"SELECT","status":"ACTIVE"}
]
```

`scope` distinguishes PROVIDER_REQUIREMENT and BOOKING_FIELD; configurationScope
distinguishes ownership. Option GET also includes applicable common SERVICE fields.

## Mutations

Prefer `PUT /api/v1/admin/service-options/4/custom-fields/5` with a complete
definition; PUT is not partial:

```json
{
  "optionIds":[4],
  "scope":"PROVIDER_REQUIREMENT",
  "reuseScope":"ASSIGNMENT",
  "code":"RIDE_CARE_VEHICLE_TYPE",
  "label":"Vehicle Type",
  "fieldType":"SELECT",
  "required":true,
  "requiresApproval":false,
  "options":["CAR","BIKE","VAN","TUK_TUK"],
  "displayOrder":0,
  "customerVisible":true,
  "searchable":true,
  "filterOperator":"IN",
  "valueSource":"MANUAL"
}
```

HTTP 200 returns the complete field with the same ID and historical owner, updated
settings and incremented version. Preserve existing choice objects/icons and other
settings when constructing the full request.

`PATCH /api/v1/admin/service-options/4/custom-fields/5/status` with
`{"status":"INACTIVE"}` returns HTTP 200 and the complete field. Both operations
affect every linked service globally.

Legacy PUT/PATCH `/api/v1/admin/provider-types/{serviceId}/custom-fields/5`
(PATCH adds `/status`) accept the owner or a service linked to an associated
option. They update the same shared row. Unrelated contexts return 404; invalid
definitions return 400.

## Assign/unassign

optionIds is the complete global applicability set. Changing `[4,9]` to `[9]`
removes the field from option 4 across all linked services. Use the historical
owner endpoint and a full definition with an explicit optionIds list. Newly
assigned options must be linked to that context; existing historical associations
are allowed across namespaces. Omit legacy optionId when replacing optionIds:
sending both is invalid and returns 400.

The option-scoped URL requires its own option to remain in the list; removing it
returns 400. Omitting applicability there preserves existing associations.
An empty list at the owner endpoint makes a field common to the service; it does
not delete it. Use status to hide the field everywhere. A service-option link
status hides the entire option only in that main service.

## Reconcile fields 5 and 7

Their codes differ, so default same-code merging cannot infer equivalence. This
build supports an explicit equivalence setting before the first offline migration:

```powershell
$env:SHARED_OPTION_FIELD_ALIASES='5:7'
java -cp 'target/classes;target/dependency/*' com.jobhub.migration.SharedOptionAutoMerge --apply
```

See FULLY_SHARED_OPTIONS_MIGRATION.md for DB configuration, build and rollout.
This equivalence does not force ID 7 as winner. Existing priority applies: ACTIVE,
original option service, newest update, lowest ID. If option 4's original service
is 2 and both fields are active, field 5 wins. The effective option GET then has
one active Vehicle Type definition with ID 5 and unioned compatible choices.
Field 7 remains inactive, detached and archived; the option GET no longer includes
it. Owner administration can still list detached historical rows.

Provider answers, price/marker field-ID references and marker selectors are
remapped. Original definitions, answers and reviews are archived. Compatible
approvals are reused; missing/incompatible evidence stays pending. Historical
booking answers and pricing snapshots remain unchanged. Alias pairs must have
the same scope and overlapping option associations; labels alone are not enough.

The runner skips data processing once DATA_MERGED/COMPLETE exists. Adding aliases
after completion will not reconcile again. Do not delete migration state; an
already-migrated database requires a separate versioned follow-up migration.
Reload schemas after rollout and use returned canonical IDs in subsequent writes.

These changes have not been deployed or applied to the production database.
