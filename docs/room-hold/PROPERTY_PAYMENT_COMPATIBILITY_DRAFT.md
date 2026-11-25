# Property–Payment deposit-eligibility contract (confirmed by Quoc, 2026-11-26)

Scope: Property provides a read-only **point-in-time** eligibility check. It does not collect, own, or reconcile payment receipts. Payment is responsible for issuing receipts and enforcing idempotency, including a UNIQUE hold_id constraint on Payment's side.

## 1. Create Hold first

`POST /api/room-holds` accepts `viewingId` and `expiresAt` with the existing Property API authentication rules. `receiptReference` is optional for legacy clients and **not required** for new Hold creation. Property creates PENDING_VERIFICATION Hold, marks the room RESERVED_PENDING, and preserves concurrency locks.

Flyway migration `V6__make_hold_receipt_reference_optional.sql` changes `room_holds.receipt_reference` to nullable. Do not modify already-applied V4.

## 2. Deposit eligibility

`GET /api/room-holds/{holdId}/deposit-eligibility`

Headers: `Authorization: Bearer <original user JWT>`. Payment forwards the exact user JWT. Property independently calls Auth Introspection; **no X-Service-Key may replace the end-user Bearer identity**. No request body.

Example HTTP 200 (all fields required, `expiresAt` with ISO 8601 offset):

```json
{
  "holdId": "11111111-1111-4111-8111-111111111111",
  "propertyId": "22222222-2222-4222-8222-222222222222",
  "roomId": "33333333-3333-4333-8333-333333333333",
  "customerId": "44444444-4444-4444-8444-444444444444",
  "holdStatus": "PENDING_VERIFICATION",
  "roomStatus": "RESERVED_PENDING",
  "expiresAt": "2026-11-27T18:00:00+07:00",
  "eligible": true,
  "reason": null
}
```

`expiresAt` is stored in existing Property MySQL `DATETIME(6)` using the Asia/Ho_Chi_Minh local time convention, then serialized as `OffsetDateTime` with +07:00.

Eligibility conditions, all required: Hold PENDING_VERIFICATION, Room RESERVED_PENDING, current Property time strictly before expiresAt.

- Business ineligible: HTTP 200 with `eligible=false` and one reason code: `INVALID_HOLD_STATUS`, `INVALID_ROOM_STATUS`, `HOLD_EXPIRED`.
- Authorization: OWNER all properties; STAFF only its assigned property; CUSTOMER/TENANT 403 even for own Hold.
- Invalid/expired JWT: 401; missing authorization: 401; Auth service unavailable or integration failure: 503; no permission: 403; unknown Hold: 404.

**Important:** `RECEIPT_ALREADY_EXISTS` is a *reserved contract reason*. Property cannot reliably determine whether Payment issued a receipt; do not infer it from a legacy `receiptReference`, which might be a manual string. No false proof of payment must be returned. Payment must enforce its own receipt uniqueness/idempotency. This reason becomes usable only after a separately agreed, authenticated Payment-to-Property notification or lookup exists.

## 3. Limits and next decisions

Eligibility is only a snapshot; it does **not** atomically reserve the right to issue a receipt. Payment must handle retry and duplicate issuance. The OWNER_VERIFIED approval flow and trusted Payment verification API are still not complete; do not claim end-to-end deposit approval has been validated.

Local Maven and MySQL tests on this revision must be run before pushing. No direct DB access across services.
