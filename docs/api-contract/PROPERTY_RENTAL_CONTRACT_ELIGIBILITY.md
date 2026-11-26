# Property -> Rental: Contract Eligibility

Status: **implementation proposed in `feature/thien/contract-eligibility`**; not deployed until a PR is merged and the service is restarted. This document describes the proposed endpoint contract.

`GET /api/rooms/{roomId}/contract-eligibility`

- Rental forwards the end-user's original `Authorization: Bearer <JWT>`.
- Property introspects with Auth Service and verifies OWNER or STAFF with assignment to the property's scope. Do not impersonate OWNER or use a client-supplied `propertyId` for authorization.
- Returns `200` and **exactly these top-level data fields**: `roomId`, `propertyId`, `roomStatus`, `eligible`, `reason`, `requiresHoldId`, `holdId`, `holdStatus`, `customerId`.
- `reason` is null if eligible=true. `holdId`, `holdStatus`, `customerId` are null when no valid approved hold is returned.
- `customerId` is the Auth user UUID captured by the Viewing -> Hold workflow; it is NOT the Rental tenantProfileId. Rental must verify tenant-profile ownership against Auth user identity before creating a DRAFT tied to a Hold.

## Decision table

| Room status | Rule | Result |
| --- | --- | --- |
| `AVAILABLE` | No inconsistent APPROVED Hold exists | `eligible=true`, `requiresHoldId=false`, Hold/customer fields null |
| `AVAILABLE` | An APPROVED Hold exists despite room AVAILABLE | `eligible=false`, `reason=ROOM_HOLD_INCONSISTENT` |
| `RESERVED_PENDING` | Hold still awaiting verification | `eligible=false`, `reason=ROOM_PENDING_VERIFICATION` |
| `RESERVED` | Exactly one APPROVED Hold exists with customer and approval metadata | `eligible=true`, `requiresHoldId=true`, `holdId`, `holdStatus=APPROVED`, `customerId` |
| `RESERVED` | No approved Hold exists | `eligible=false`, `reason=APPROVED_HOLD_NOT_FOUND`, `requiresHoldId=true` |
| `RESERVED` | Multiple approved Holds exist | `eligible=false`, `reason=MULTIPLE_APPROVED_HOLDS` |
| `RESERVED` | Approved Hold has missing identity/approval data | `eligible=false`, `reason=APPROVED_HOLD_INCOMPLETE` |
| `OCCUPIED` | Already leased | `eligible=false`, `reason=ROOM_OCCUPIED` |

Already APPROVED holds are not invalidated merely because their original `expiresAt` has passed: approval occurred before the deadline. A subsequent cancellation/expiry changes the Hold/Room states and makes them ineligible.

## Example: RESERVED with approved Hold

```json
{
  "roomId": "11111111-1111-4111-8111-111111111111",
  "propertyId": "22222222-2222-4222-8222-222222222222",
  "roomStatus": "RESERVED",
  "eligible": true,
  "reason": null,
  "requiresHoldId": true,
  "holdId": "33333333-3333-4333-8333-333333333333",
  "holdStatus": "APPROVED",
  "customerId": "44444444-4444-4444-8444-444444444444"
}
```

## Response status and ownership

- `200`: state inquiry successful, including `eligible=false` (business-level rejection is NOT a 409).
- `401`: missing/invalid user JWT.
- `403`: wrong role or STAFF without property assignment.
- `404`: room ID does not exist.
- `503`: Auth introspection unavailable; Rental should fail closed on Property outage.

**Consistency limitation:** This GET is a point-in-time read. Between eligibility and Rental write/activation, the Hold can change or the room can become occupied. Rental must recheck current eligibility at a suitable write boundary and coordinate with Property's locked status transition before activation. This endpoint alone does not reserve a room or guarantee no concurrent DRAFT creation.

Payment proof remains Payment's responsibility. An APPROVED Hold is a Property status, not in itself proof that Payment validation E2E is complete.
