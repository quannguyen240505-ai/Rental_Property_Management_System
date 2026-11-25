# Google Login — API Contract (MVP)

## Flow
1. Angular displays Google Identity Services (GIS) popup button for the authorized web origin.
2. GIS callback returns a **Google ID token** (`credential`), NOT our JWT.
3. Angular POSTs `{ "idToken": "..." }` to `/api/auth/google` through Gateway.
4. Auth verifies Google's signature/JWK, `iss`, `aud`, `exp`, `email_verified`, and Google `sub`.
5. Auth looks up `users.google_sub`. If new, creates a `CUSTOMER` account with no phone number; if found, reuses the existing account/role/status.
6. Auth returns the **same LoginResponse** schema as normal username/password login, containing our HS256 application JWT.

## Endpoints
- `POST /api/auth/google` — public, JSON `{"idToken":"<google-id-token>"}`. Success 200 = `LoginResponse`.
- `POST /api/auth/google/link` — authenticated using our application JWT; same request JSON. Success 200 = `MessageResponse`.

Error examples: 401 `INVALID_GOOGLE_TOKEN`; 409 `ACCOUNT_LINK_REQUIRED` (same email, not yet linked); 409 `GOOGLE_EMAIL_MISMATCH`; 409 `GOOGLE_ALREADY_LINKED`; 503 `GOOGLE_LOGIN_NOT_CONFIGURED`.

## Security and data model
- Never grant roles based on Google email. Newly created Google accounts get `CUSTOMER`; `OWNER/STAFF/TENANT` are only preserved for previously linked internal accounts.
- Use Google `sub` as stable external identity; **do not** auto-link users just by matching email. For existing password users, sign in normally first and call the authenticated link endpoint with a matching Google email.
- Keep app JWT verification/introspection unchanged. Google ID token must not be used as the app JWT.
- `users.google_sub` is nullable and UNIQUE. Auth currently uses JPA `ddl-auto: update`; review database migration strategy before deployment to production.
- `GOOGLE_CLIENT_ID` is the **web OAuth client ID**, not a client secret. Do not commit secrets or Google ID tokens. HTTPS outside localhost.
- Use an unpredictable placeholder password for Google-only accounts because existing `password_hash` DB column is NOT NULL. They may later establish a password through the existing secure password-reset flow.

## Setup
1. Create a Web application OAuth client in Google Cloud > Google Auth Platform > Clients.
2. Authorized JavaScript origin: `http://localhost:4200` (or actual Angular origin).
3. Set `GOOGLE_CLIENT_ID=<client-id>.apps.googleusercontent.com` in the Auth Service environment (and the same public Client ID in Angular).
4. Load `https://accounts.google.com/gsi/client` in Angular, initialize `google.accounts.id.initialize({client_id, callback})`, and render the button. In callback, POST `response.credential` as `idToken` to Gateway `http://localhost:8080/api/auth/google`.
5. To link an existing account: normal login -> authenticated `/api/auth/google/link` -> subsequent Google logins work.

## Unit checks (no Google network)
`GoogleIdTokenVerifierTest`: accepted token, wrong audience/issuer, unverified email, expired token, signature failure, missing configuration.
`GoogleLoginServiceTest`: new customer, existing linked account, conflicting email, matching-email link, mismatched-email link.

## Not tested in this generated patch
No Maven/JDK 25 build or Google Cloud end-to-end run was possible in this environment. Run the repository CI on a feature PR and test a real Google login before merge.
