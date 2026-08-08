# Vidyut Spring Boot API

The backend uses one PostgreSQL-backed authentication system. Passwords are stored as
BCrypt hashes in `accounts`; authorization is stored in `account_roles`.

## Account partition

- `INDIVIDUAL` accounts have `ROLE_EV_USER`, `ROLE_HOST`, or both.
- `COMPANY` accounts have only `ROLE_COMPANY`.
- `ADMIN` accounts have only `ROLE_ADMIN`.
- EV, host, and company data lives in `ev_user_profiles`, `host_profiles`, and
  `companies` respectively.

JPA validates this model and a deferred PostgreSQL constraint trigger enforces it at
transaction commit, including profile participation. A direct SQL attempt to mix a
company role with an individual account is rejected by PostgreSQL.

## Mode-scoped login

`POST /api/auth/login` returns `allowedModes`, `activeMode`, and an access token scoped
to one mode. A dual EV/host user switches with `POST /api/auth/switch-mode`; the backend
issues a new token containing only the selected authority.

Protected API groups are `/api/ev/**`, `/api/host/**`, `/api/company/**`, and
`/api/admin/**`. Ownership-sensitive endpoints derive the account ID from the verified
JWT rather than accepting a client-provided user ID.

## Local PostgreSQL

```powershell
$env:SPRING_DATASOURCE_PASSWORD = '<your PostgreSQL password>'
mvn spring-boot:run
```

Optional variables are `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and
`JWT_SECRET`. Production requires `JWT_SECRET`.

Run the isolated H2-backed test suite with:

```powershell
mvn clean test
```
