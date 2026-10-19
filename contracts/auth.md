# Authentication & authorization contract

## Login
`POST /api/v1/auth/login` (core-api) with `{ "email", "password" }` → `{ "accessToken", "tokenType": "Bearer", "expiresIn": 3600, "user": {...} }`.
Passwords are stored as BCrypt hashes (`$2a$`/`$2b$`, cost 10). Dev users: `resources/seed/users.json`.

## JWT (HS256 locally and in AWS; secret = env `JWT_SECRET`, ≥ 32 bytes)
```json
{
  "iss": "ekm-core-api",
  "sub": "3f6c1a52-8a0e-4b8e-9d1c-1c2b3a4d5e6f",
  "email": "hannah.hr@helios.example.com",
  "name": "Hannah Reyes",
  "role": "EMPLOYEE",
  "department": "HR",
  "iat": 1792000000,
  "exp": 1792003600
}
```
- Only **core-api** issues and validates user JWTs. The frontend stores the token in memory (+ `sessionStorage` fallback),
  sends it as `Authorization: Bearer`, and may decode it **only for display** (name, role) — never for security decisions.
- On 401 the frontend clears the token and redirects to `/login`.

## Roles
| Role | Can |
|---|---|
| `EMPLOYEE` | chat/search over allowed departments, view/download allowed docs, see own chat history |
| `MANAGER` | everything EMPLOYEE can + upload / new version / delete documents **in own department only** (GENERAL documents are managed by ADMIN) |
| `ADMIN` | everything, all departments, user management, analytics dashboard, knowledge-gap management |

## Department access rule (the core security rule of the project)
```
allowedDepartments(user) =
    ALL departments                        if user.role == ADMIN
    { user.department, "GENERAL" }         otherwise
```
- core-api computes `allowedDepartments` from the **validated JWT** and passes it to ai-service on every
  search/answer call. ai-service **must** apply it as a SQL `WHERE department_code = ANY(:allowed)` filter
  **before** ranking (pre-filtering, not post-filtering).
- Every document endpoint checks the same rule and returns **404** (not 403) for documents outside it.
- Every denied access emits an `ACCESS_DENIED` audit event.

## Service-to-service
core-api → ai-service calls include `X-Internal-Token: ${INTERNAL_SERVICE_TOKEN}`. ai-service rejects any
request without the exact token with 401. ai-service never receives the user JWT.
