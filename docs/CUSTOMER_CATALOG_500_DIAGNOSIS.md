# Customer service catalog 500 investigation — 2026-10-21

Endpoint: `GET /api/v1/customers/me/services?marketId=<selected market>`.

## Confirmed local reproduction

A real Spring transaction-boundary regression test reproduces a server-side
failure when an active service has no active offering in the selected market.
This is synthetic test data, not a reproduction using the affected production
customer or their actual selected market.

The previous implementation caught ResourceNotFoundException from the proxied,
transactional CustomerAvailabilityService.requireOffering method and continued.
Spring had already marked the shared transaction rollback-only. When the outer
CustomerDiscoveryService.services method returned, its transaction failed:

```text
org.springframework.transaction.UnexpectedRollbackException:
Transaction rolled back because it has been marked as rollback-only
  at AbstractPlatformTransactionManager.processRollback(...)
  at AbstractPlatformTransactionManager.commit(...)
  at TransactionAspectSupport.commitTransactionAfterReturning(...)
  at TransactionInterceptor.invoke(...)
  at CustomerDiscoveryService$$SpringCGLIB$$0.services(...)
```

Before the fix, both mixed offered/unoffered catalogs and catalogs with no offered
services failed in CustomerCatalogTransactionTest. The offered restricted-service
case and invalid-market case did not exhibit that rollback defect.

## Fix

Catalog filtering now calls hasActiveOffering and skips absent/inactive offerings
using a boolean result. No expected filtering exception crosses a transactional
boundary. Strict requireOffering checks remain on option, context and search paths.

The catalog still validates the customer profile and active selected market first.
Offered services retain customer requirement/access evaluation. Restricted services
remain visible with their access action; option-only restrictions produce CHECK_OPTION.
No offered services returns an empty JSON array, rather than an error.

Regression coverage uses Spring proxies and DataSourceTransactionManager with a
mock JDBC connection (no database changes): mixed catalog, empty/unoffered catalog,
inactive offering, restricted service, option restriction, invalid market and missing
customer profile. Existing availability/eligibility/discovery tests also apply.

## Production correlation still required

No affected user ID, actual selected market ID, failure time, production stack trace
or production server session was supplied. The local Docker engine was unavailable;
no relevant running local server/log was found. Do not label this reproduced defect
as the confirmed production cause without correlating the server exception.

On the application host, capture the failure timestamp/request ID and stack trace:

```bash
docker logs --since 30m --timestamps jobhub
```

Keep credentials, Authorization headers, tokens and customer personal data out of
shared logs. Reproduce with the affected user's existing customer session and actual
market; record HTTP status/body and correlate to the server log. If the exception
differs, inspect its root cause (including SQL/schema/JSON failures) rather than
assuming all catalog 500s have this transaction cause. Verify deployed schema/version
before changing data or customer eligibility configuration.

After deployment, verify: selected market returns only its active offerings; missing
profile/market produce the documented 404; blocked service remains visible with
requirements action; option-only restriction returns CHECK_OPTION; no offerings returns
200 with []; eligible services remain eligible. This code change has not been deployed.
