# Property PR #12 — Fix Quân review (2026-11-26)

Baseline: `feature/thien/property-service` @ `e2a9975`. This ZIP is an overlay containing **only changed/new files**. No changes to database migrations V1–V6 or other services.

## Fixes

1. `RoomService.updateRoom()` and `.setRoomPublished()` now both use the existing transactional `RoomRepository.findForUpdateById()` mutex shared with Room Hold and Viewing. No `@Version` migration needed for this fix.
2. Unit tests stub and verify pessimistic locks; 2 new cases.
3. `RoomHoldConcurrencyMySqlIT` adds 3 tests: metadata update+hold, unpublish+hold, publish-on-held 409. MySQL tests expected 14 (11 previous + 3 new).
4. `property-service/pom.xml` adds opt-in Maven Failsafe `mysql-it` profile; regular `clean verify` runs unit tests only, whereas `-Pmysql-it clean verify` runs unit + MySQL IT.
5. `.github/workflows/backend-ci.yml` retains the existing all-modules Unit Test job and adds **separate MySQL test DB** `rental_property_test_db` and a dedicated Property Failsafe job.
6. `PROPERTY_RENTAL_VERIFICATION_PENDING.md` explicitly documents the unfinished Rental verification API without inventing an implemented endpoint.

## Apply

From `F:\Rental_Property_Management_System` on the feature branch:

```powershell
git status -sb
git branch backup/thien-before-room-lock-20261126
Expand-Archive -LiteralPath "$HOME\Downloads\Property_PR12_Review_Fix_20261126.zip" -DestinationPath "F:\Rental_Property_Management_System" -Force
git diff --check
git status --short
```

**Check your branch first**. This package was made against commit `e2a9975`; do not apply blindly if upstream files have been edited since then.

## Verify

Normal Unit Tests (expected **95 tests**):

```powershell
& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" -pl property-service -am clean verify
```

MySQL + unit tests in the Maven `verify` lifecycle (expected **14 MySQL ITs** plus unit tests):

```powershell
& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" -pl property-service -am -Pmysql-it clean verify
```

MySQL test DB must be `rental_property_test_db`, with `PROPERTY_TEST_DB_USERNAME` and `PROPERTY_TEST_DB_PASSWORD` environment variables. Never point this profile at real rental_property_db.

**Do not push until both runs pass.** After push, verify **both jobs** of Backend CI PASS. Previous #14 ran only unit tests. The Payment end-to-end, OWNER_VERIFIED, Rental verification API remain pending.

## Review feedback mapping

- Quân §1 concurrency: shared pessimistic lock and added MySQL race tests.
- Quân §2 integration verification: opt-in Failsafe + isolated CI MySQL job.
- Quân §3 Rental service: tracked documented dependency, no fictitious endpoint.
- Quân §4 PR scope: update Description **after actual tests and CI**, not before.
