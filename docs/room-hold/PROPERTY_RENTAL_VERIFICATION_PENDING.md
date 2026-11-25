# Property ↔ Rental verification dependency — PENDING

Reviewed alongside Property PR #12 commit e2a9975 (2026-11-26).

Status: **NOT IMPLEMENTED**. This note is for coordination with Sáng and Quân. Do not call a draft URL in production.

Rental needs a trusted way to check room existence, its owning property and the acting user's management rights before creating a DRAFT lease. The method, route and response must be agreed by both services.

Suggested proposal for discussion only:

- Method: `GET`
- Path: `/api/rooms/{roomId}/contract-eligibility` (PROPOSAL, no controller in Property yet)
- Auth: Rental forwards the end-user `Authorization: Bearer <JWT>`; Property introspects with Auth Service and checks OWNER/STAFF property-scoped authorization. Do not impersonate OWNER or replace the user JWT with `X-Service-Key`.
- Response (PROPOSAL): `roomId`, `propertyId`, `roomStatus`, `eligible`, `reason`. Client must not supply a trusted `propertyId`; Property derives it from the room.
- HTTP 401: missing/invalid JWT; 403: disallowed role/STAFF outside assigned property; 404: room not found; 409: conflict if endpoint chooses to signal business ineligibility as an error (decision pending); 503: Auth/Property upstream unavailable (fail closed). Whether ineligible state returns `200 eligible=false` or `409` must be agreed before implementation.

Unresolved with Sáng: contract DRAFT prerequisites, whether a held room may proceed to DRAFT, required property/room snapshot details, retries and gateway route. Keep this work in a separate integration PR unless the team explicitly re-scopes PR #12.
