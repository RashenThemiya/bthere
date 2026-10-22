# Automatic merge of existing shared options — 23 October 2026

Duplicate configuration no longer stops migration for manual selection.
The offline JDBC runner com.jobhub.migration.SharedOptionAutoMerge applies
deterministic priorities, archives original rows, and records completion.
Existing option IDs and service links remain. Production has not been migrated.

## Automatic policy

| Data | Merge behavior |
|---|---|
| Fields | Same scope/code with overlapping option applicability merge into one group. Prefer an ACTIVE definition from an option's original service, then newest update and lowest ID. Preserve that field ID and the union of applicable option IDs. |
| Choice values/icons | Union compatible SELECT/MULTI_SELECT choices. Winning metadata takes priority; fill missing labels/icons from duplicate records. |
| Types/settings | Winning field type/settings take priority. Required/review flags use OR among definitions of that type. Incompatible old definitions and answers are archived rather than accepted as evidence for another type. |
| References | Remap provider answers, price conditions, marker conditions and selectors automatically. Superseded field rows remain inactive with a migration suffix. Historical booking snapshots and per-booking answers are untouched. |
| Provider registration | Keep one existing reviewed row per provider/option: reviewed rows first, newest actual review, original service, newest update, lowest ID. Preserve its real status, including a newer rejection. |
| Provider answers | Canonical answer wins within an assignment. Union valid MULTI_SELECT answers across assignments only when all contributors are APPROVED. Composite provenance goes into the migration note/archive without attributing all approval to one reviewer. |
| Operation | The winning registration's source assignment supplies schedule, coverage, languages and skills. Other assignments/areas/schedules remain for their other options and history. Contradictory schedules are not combined into extra availability. |
| Prices | Retain distinct markets, incompatible choice combinations, specificity tiers, non-overlapping windows/areas. For equally specific compatible overlaps prefer original service, newest update, lowest ID. Retire losing rates; retain historical IDs. |
| Marker rules | Retain distinct combinations/specificity; resolve equal-priority compatible rules by original service, newest update, lowest ID. Retire losers. |
| Marker field | Existing shared value, otherwise original-service selector, otherwise lowest service ID with a selector; remap the field ID. |
| Option documents | Same option/document type merges; required/review flags use OR. Actual provider documents/reviews are preserved. |
| Option eligibility | Union enabled required codes, highest minimum age, and verified-DOB requirement if any enabled rule requires it. |

Single-choice answers and schedules cannot be unioned meaningfully. Their existing
source is selected by these priorities automatically. No duplicate-conflict approval
screen is required. Pending/rejected evidence is never promoted to APPROVED.
Missing or incompatible proof for the merged policy remains pending while the
migration still completes; valid existing evidence is reused.

Fields spanning several original configuration namespaces retain their association
union. Shared field lookup now uses option applicability instead of demanding the
same historical field namespace for every option. Unscoped main-service fields
remain separate from option-specific configuration.

## Archive, transactions and retries

The shared_option_merge_archive table stores complete original configuration,
answers and review rows as JSON, with IDs, assets, values, reviewer and timestamps.
Its migration key is fully_shared_options_v2_auto. Source assignments and operational
rows are retained; booking rows are not modified. The runner prints counts only.

Consolidation and the DATA_MERGED state commit in one transaction. Database failures
or orphan references roll back data and archive inserts. Setup DDL commits separately
in MySQL. Provider ID becomes NOT NULL and UNIQUE(provider_id, option_id) is added
only after merging duplicates.

If final DDL fails, rerun: DATA_MERGED skips the data phase and retries final schema
work. COMPLETE reruns also do not remerge later application writes or duplicate
archives. The SQL file now optionally prepares columns only; do not run the previous
conflict-aborting procedure.

## Run

Back up the database and pause application writers. Rehearse against a restored
MySQL 8 database using one maintenance process. Verify earlier shared-option,
combination-pricing, marker-field and marker-rule tables first. Hibernate schema
update does not run this merger and must not create uniqueness over old duplicates.

Configure DB_URL, DB_USERNAME and DB_PASSWORD in the maintenance process environment.
The runner does not read the workspace .env automatically.

~~~text
mvn -DskipTests compile dependency:copy-dependencies -DincludeScope=runtime
~~~

Windows PowerShell:

~~~powershell
java -cp 'target/classes;target/dependency/*' com.jobhub.migration.SharedOptionAutoMerge --apply
~~~

Linux:

~~~sh
java -cp 'target/classes:target/dependency/*' com.jobhub.migration.SharedOptionAutoMerge --apply
~~~

This starts no Spring application or HTTP service. It is an explicit offline data
write. Automated tests exercise actual JDBC merge behavior using H2 in MySQL mode;
production MySQL execution remains unverified until rehearsal/deployment.

## Verify

Confirm archive rows and COMPLETE state, one provider registration per option,
mapped field IDs, retained distinct prices and retired duplicate rules. Compare
historical bookings/reviews with the backup. Refresh normal eligibility as required.
Test the same customer market/time/location via both linked main services: identical
fields, source registration, availability, fare and markers. Booking via one link
must reduce availability via the other. Restore database and previous application
together if rolling back; old service-local code cannot interpret consolidated writes.
