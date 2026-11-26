# Payment deposit collection — Property integration

Implemented against the team proposal supplied on 2026-11-24. This document does not certify that the Property endpoints are deployed.

## Request and permission

`POST /api/deposit-receipts`, authenticated OWNER or STAFF, body:

```json
{"holdId":"11111111-1111-4111-8111-111111111111","amount":1000000,"paymentMethod":"CASH","evidenceUrl":null}
```

Payment introspects the user token through Auth on every request. It forwards that same Bearer token to:

`GET {PROPERTY_BASE_URL}/api/room-holds/{holdId}/deposit-eligibility`

No body, Auth service key, customer-supplied propertyId, or user impersonation header is sent. Property must authenticate again and enforce current STAFF assignments; OWNER may act across the system. CUSTOMER/TENANT are rejected at Payment before any database operation.

Expected Property response: holdId, propertyId, roomId, customerId (UUID), holdStatus, roomStatus, expiresAt (ISO offset date-time), eligible (boolean), reason (nullable). IDs must be complete and holdId must match. eligible=false requires a reason. Bare local date-time expiresAt is rejected with 503.

A new receipt requires eligible=true, PENDING_VERIFICATION, RESERVED_PENDING and an expiry later than the write-time clock. Payment does not manufacture a default Hold lifetime or override Property's expiry. Property owns the 5-minute/72-hour limits and any 24-hour FE default.

## Result and receipt reference

201 returns the existing response fields plus `receiptReference`, an alias of the stable `receiptCode`. Current references remain `DC-<UUID>`; the `RC-20261124-001` reference in the proposal is an example, not a new numbering algorithm. New receipts are REPORTED, not OWNER_VERIFIED.

## Duplicate prevention and retry

V2 adds a unique hold_id constraint and a per-Hold InnoDB lock table. The service uses a Spring-managed READ_COMMITTED transaction, an exclusive per-Hold row lock before validation, then checks for an existing receipt under lock. This works across multiple application instances; a Java-only lock is not used.

- The same authorized collector, holdId, amount, paymentMethod and evidenceUrl returns the original receipt with 201, without a second insert. Current Property permissions must still pass. Property may now report eligible=false because the original receipt exists, the Hold expired, or the Hold changed status; returning an old receipt does not collect more money or reopen the Hold.
- Changed payload/collector or a CANCELLED existing receipt returns 409 RECEIPT_ALREADY_EXISTS.
- All receipt states retain the one-receipt-per-Hold uniqueness. Cancelling a receipt does not silently authorize another collection on that Hold. A different re-collection policy needs a separate agreed accounting workflow.
- A duplicate insert from a different writer returns 409 and aborts the local transaction.
- Expiry is rechecked immediately before insert using Instant, respecting the offset in Property's response.
- Failed validation rolls back the newly created mutex row as well as any business write.

## Error mapping

| Condition | Payment response |
| --- | --- |
| Missing or inactive user token | 401 UNAUTHORIZED |
| Customer/Tenant role | 403 FORBIDDEN |
| Auth timeout/network/5xx/wrong service key | 503 AUTH_SERVICE_UNAVAILABLE |
| Property rejects JWT | 401 PROPERTY_REJECTED_TOKEN |
| Property rejects scope | 403 PROPERTY_FORBIDDEN |
| Property returns 404 | 404 HOLD_NOT_FOUND |
| Property timeout/network/5xx/invalid response | 503 PROPERTY_SERVICE_UNAVAILABLE |
| Hold expired / invalid Hold or room status | 409 with the corresponding code |
| Duplicate or changed retry | 409 RECEIPT_ALREADY_EXISTS |

A Property route that is not yet deployed may also return 404. Confirm that endpoint availability separately before diagnosing a missing Hold. Error logs omit JWTs, keys and upstream response bodies.

## Distributed consistency and remaining dependencies

A read-only eligibility GET and a later Payment commit are not one atomic cross-service operation. Payment's mutex prevents duplicate Payment writes and its expiry check blocks local expiry, but it cannot lock Property's cancellation transaction. Property must coordinate cancellation/expiry with Payment's recorded/in-flight collection outcome before releasing the room. If strict atomic eligibility is required, agree an authorization/lease or reservation-confirmation protocol; do not claim the GET closes this race.

Property's "no prior valid receipt" check needs a trusted Payment status source or synchronization; Property must not read payment_db. Payment's unique constraint is authoritative for duplicate creation and must remain even when Property supplies this check.

This change does not implement CUSTOMER evidence confirmation, OWNER monetary verification, refunds, receipt read/verification endpoints, or Property approve. Property approve must eventually query an authenticated Payment verification endpoint and require OWNER_VERIFIED with matching Hold/customer. REPORTED must not be accepted as verified. No production bypass/mock verifier is shipped.

## Local setup

Keep the working MYSQL_USER, MYSQL_PASSWORD, AUTH_INTERNAL_SERVICE_KEY, AUTH_BASE_URL, PAYMENT_PORT and SPRING_DATASOURCE_URL from the current Payment - Run configuration. Add:

```
PROPERTY_BASE_URL=http://localhost:8082
```

This is the origin only (no endpoint path, quotes, whitespace or POST). localhost applies only when Property runs on this machine.

Keep the current application schema `rental_payment_deposit_test_20261124`. Do not recreate it or edit V1. Before startup, inspect:

```sql
SELECT hold_id, COUNT(*) AS receipt_count
FROM rental_payment_deposit_test_20261124.deposit_receipts
GROUP BY hold_id HAVING COUNT(*) > 1;
```

Expected: no rows. If duplicates exist, stop and reconcile the financial records; V2 intentionally fails instead of deleting them. Restarting after V2 should say No migration necessary.

## Verification

Default: `mvn -f payment-service/pom.xml clean test` (50 test methods including existing tests). These use fake HTTP servers and mocked repositories; they do not prove MySQL persistence or Property deployment. Run on the target Java 25 setup.

Four explicit MySQL tests live in DepositReceiptMySqlIT. Set PAYMENT_IT_DB_URL, PAYMENT_IT_DB_USER and PAYMENT_IT_DB_PASSWORD in a separate STS Maven configuration, with goals:

```
-Dtest=DepositReceiptMySqlIT test
```

Create an empty schema first:

```sql
CREATE DATABASE rental_payment_it_20261124 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

PAYMENT_IT_DB_URL example:
`jdbc:mysql://localhost:3306/rental_payment_it_20261124?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`

The test guards the host/schema before invoking Flyway and calls the service through an actual Spring transaction proxy. It checks concurrent identical retry, concurrent changed amounts, expiry rollback, and retry after Property reports an existing receipt. Cleanup targets only UUID fixtures created by that test. No global truncate or Flyway clean is used. These *IT tests do not run in default Surefire discovery; run explicitly. A normal BUILD SUCCESS alone is not proof that the MySQL cases ran.
