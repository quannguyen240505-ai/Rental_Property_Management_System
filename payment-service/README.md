# Payment Service

## Scope
Spring Boot service for deposit receipt collection, owner verification and Rental eligibility.
Online invoice payment, payment-provider callbacks, Billing settlement, cash handover and deposit allocation are not implemented by this PR.

## Configuration
- Java 25; MySQL 8.0.16+; default port `8089` (`PAYMENT_PORT`).
- `MYSQL_USER`, `MYSQL_PASSWORD`; optional `MYSQL_HOST`, `MYSQL_PORT`, `SPRING_DATASOURCE_URL`.
- Default database: `rental_payment_db`. Create an empty schema before startup.
- `AUTH_BASE_URL` (default `http://localhost:8081`), `AUTH_INTERNAL_SERVICE_KEY` (same value as Auth).
- `PROPERTY_BASE_URL` (default `http://localhost:8082`); `EUREKA_ENABLED`, `EUREKA_URL` for discovery.
- Do not commit credentials. For direct local testing without Eureka set `EUREKA_ENABLED=false`.

## Database
```sql
CREATE DATABASE rental_payment_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```
Flyway applies V1 and V2 on a new schema. V2 enforces one receipt per hold and adds hold locks.
Do not pre-import the standalone schema or edit applied migrations. Keep `baseline-on-migrate=false` and `clean-disabled=true`.
Back up legacy schemas and validate them before planning a baseline; legacy baseline is not tested.

## Authentication and APIs
Send `Authorization: Bearer <access_token>` from Auth. Payment introspects the token and forwards it to Property for current access scope. Do not put passwords or internal service keys in client requests; do not log bearer tokens or secrets.

| Method / endpoint | Role | Success |
|---|---|---|
| POST `/api/deposit-receipts` | OWNER / STAFF | 201 receipt (including identical replay) |
| POST `/api/deposit-receipts/{receiptId}/verify` | OWNER | 200 receipt with OWNER_VERIFIED status |
| GET `/api/deposit-receipts/{receiptId}/rental-eligibility` | OWNER / STAFF | 200 eligibility response |
| GET `/actuator/health` | Public | 200 health |

Create body:
```json
{"holdId":"<hold UUID>","amount":500000,"paymentMethod":"CASH","evidenceUrl":null}
```
`paymentMethod`: CASH or BANK_TRANSFER; amount is positive integer VND.
Create requires an eligible, unexpired PENDING_VERIFICATION / RESERVED_PENDING hold. Same payload and collector can replay the existing receipt; conflicting payloads return 409. Receipt response includes `id`, `receiptCode`, `receiptReference`, `propertyId`, `roomId`, `holdId`, `customerId`, `collectedBy`, `amount`, `paymentMethod`, `status`, `evidenceUrl`, `createdAt`, `updatedAt`.

Owner verification accepts REPORTED or CUSTOMER_CONFIRMED receipts for an eligible, unexpired pending hold. It records the owner and an audit entry in the same transaction; repeated verification returns the existing receipt without duplicate audit. No request body is needed.

Rental eligibility returns `receiptId`, `receiptReference`, `holdId`, `propertyId`, `roomId`, `customerId`, `amount`, `status`, `verified`, `eligible`, `reason`. A verified receipt is eligible only for APPROVED / RESERVED or an unexpired PENDING_VERIFICATION / RESERVED_PENDING hold. A negative business eligibility is HTTP 200 with `eligible=false`, e.g. `RECEIPT_NOT_OWNER_VERIFIED` or `HOLD_NOT_ELIGIBLE`.

### Errors
Business errors use `{"code":"..."}`; framework-level validation/security errors may have a different response shape.
- 400 `INVALID_RECEIPT_REQUEST` or validation failure.
- 401 invalid/missing token, `UNAUTHORIZED`, `PROPERTY_REJECTED_TOKEN`.
- 403 `FORBIDDEN`, `PROPERTY_FORBIDDEN`.
- 404 `RECEIPT_NOT_FOUND`, `HOLD_NOT_FOUND`.
- 409 `RECEIPT_ALREADY_EXISTS`, `INVALID_RECEIPT_STATUS`, `RECEIPT_HOLD_MISMATCH`, `HOLD_EXPIRED`, `INVALID_HOLD_STATUS`, `INVALID_ROOM_STATUS`, `HOLD_NOT_ELIGIBLE`.
- 503 Auth/Property unavailable or invalid upstream response (`PROPERTY_SERVICE_UNAVAILABLE` for Property).

## Tests
```sh
mvn -f payment-service/pom.xml clean test
```
The explicit MySQL integration suite uses a separate local schema named `rental_payment_it_*`. Create it before the run; set `PAYMENT_IT_DB_URL`, `PAYMENT_IT_DB_USER`, `PAYMENT_IT_DB_PASSWORD`.
```sh
mvn -f payment-service/pom.xml -Dtest=DepositReceiptMySqlIT test
```
CI includes a separate Payment MySQL job with an isolated schema and uploaded Surefire reports. It tests receipt concurrency/replay, expiry rollback, and exactly-once owner-verification audit. Test results must be checked on the latest commit; this document does not assert a new run passed.

## Integration checklist
Start Auth, Property and Payment (and Eureka/Gateway when testing the full architecture). Use real OWNER/STAFF tokens and a real Property hold from the correct scope. Payment calls `GET /api/room-holds/{holdId}/deposit-eligibility`. Verify collection, owner verification, approval and Rental eligibility with actual service data. Confirm Rental consumes this response and matches receipt, hold, room, customer and property identifiers before creating a contract. End-to-end Rental/Gateway integration remains to be verified with the team.
