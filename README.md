# InvoiceHub — Backend

The REST API for **InvoiceHub**, a multi-tenant B2B SaaS platform for invoicing and
payments. This Spring Boot service owns authentication, tenant isolation, and all the
business domain — clients, invoices, line items, payments, teams, and platform
administration. It is consumed by the `invoice-hub-frontend` React client.

> Built for Malaysian B2B billing — MYR currency, 8% SST tax, and geo reference data
> (states / cities / postcodes).

## Tech stack

| Concern         | Choice                                          |
| --------------- | ----------------------------------------------- |
| Language        | Java 25                                          |
| Framework       | Spring Boot 4 (Web MVC, Data JPA, Security, Validation) |
| Database        | PostgreSQL                                       |
| Migrations      | Flyway                                           |
| Auth            | JWT (JJWT) — access token + refresh-token cookie |
| Boilerplate     | Lombok                                           |
| Build           | Maven (wrapper included)                         |
| Local infra     | spring-boot-docker-compose (auto-starts Postgres in dev) |

## Getting started

### Prerequisites

- JDK 25
- PostgreSQL running locally (or via Docker Compose), database named `invoice-hub`

### Run

```bash
# start the API (http://localhost:8100/api)
./mvnw spring-boot:run
```

```bash
# compile / run tests / package
./mvnw compile
./mvnw test
./mvnw package
```

The server listens on port **8100** with a context path of **`/api`**, so every route
below is served under `http://localhost:8100/api/...`.

### Configuration

Key settings live in `src/main/resources/application.properties`:

| Property                       | Default                          | Notes                                              |
| ------------------------------ | -------------------------------- | -------------------------------------------------- |
| `spring.datasource.url`        | `jdbc:postgresql://localhost:5432/invoice-hub` | Point at your Postgres instance      |
| `server.port`                  | `8100`                           |                                                    |
| `server.servlet.context-path`  | `/api`                           |                                                    |
| `app.cors.allowed-origins`     | `http://localhost:5173`          | Vite dev server origin                             |
| `jwt.secret`                   | `${JWT_SECRET:…dev key…}`        | **Set `JWT_SECRET` in production** — never ship the dev fallback |
| `jwt.access-token-minutes`     | `30`                             | Access-token lifetime                              |
| `app.cookie.secure`            | `false`                          | `false` for local http; **`true` in prod (HTTPS)** |

> ⚠️ **Security:** the committed `jwt.secret` fallback and the dev Basic-auth user are for
> local development only. Provide a real `JWT_SECRET` via environment variable and set
> `app.cookie.secure=true` before deploying.

## Architecture

```
com.adsyahir.invoice_hub_backend
├── config       # security, CORS, JWT filter, bean wiring
├── controller   # REST endpoints
├── service      # business logic
├── dao          # Spring Data JPA repositories
├── model        # JPA entities
├── dto          # request / response payloads (never expose entities directly)
├── enums        # InvoiceStatus, PaymentMethod/Status, TenantPlan/Status, RoleName
├── validation   # custom bean-validation constraints
├── exception    # GlobalExceptionHandler (centralized error → HTTP mapping)
└── seed         # reference / initial data seeding
```

### Key design decisions

- **Multi-tenancy.** Every domain row is scoped to a `tenant`. A user's tenant is derived
  from the authenticated principal — never trusted from a request path — to prevent
  cross-tenant access (IDOR). `SUPER_ADMIN`s are not scoped to a single tenant.
- **Dual-key pattern.** Entities carry both an internal `id` (BIGINT, used for FKs/joins)
  and a public `uuid` (used in URLs and API payloads). Hibernate `@UuidGenerator` fills
  the uuid on insert; the DB also has a `gen_random_uuid()` default as a backstop.
- **Enums stored as strings.** `@Enumerated(EnumType.STRING)` everywhere (never ORDINAL)
  so values stay readable and stable in the DB.
- **DTOs at the edges.** Controllers accept/return DTOs, not entities — avoids leaking
  password hashes and dodges lazy-loading serialization issues.
- **Soft deletes.** Clients, invoices, and users carry a `deleted_at` timestamp
  (null = live row) rather than being hard-deleted.
- **Centralized errors.** A `GlobalExceptionHandler` maps exceptions to HTTP responses;
  services throw `ResponseStatusException` (e.g. 404) instead of wrapping and rethrowing.

## Authentication

- `POST /api/auth/register` — create an organization + its admin user
- `POST /api/auth/login` — email + password → JWT access token + refresh cookie
- `GET  /api/auth/me` — current user + tenant
- `POST /api/auth/refresh` — rotate the access token using the refresh cookie
- `POST /api/auth/logout` — invalidate the refresh token

Send the access token as `Authorization: Bearer <token>` on protected routes.

## API overview

All routes are under the `/api` context path.

| Area        | Endpoints                                                                    |
| ----------- | ---------------------------------------------------------------------------- |
| Auth        | `/auth/register`, `/auth/login`, `/auth/me`, `/auth/refresh`, `/auth/logout` |
| Clients     | `POST /clients`, `GET /clients`, `GET /clients/{uuid}`, `PUT /clients/{uuid}`, `DELETE /clients/{id}` |
| Invoices    | `POST /invoices/create`, `GET /invoices`, `GET /invoices/{id}`               |
| Payments    | `POST /payments`                                                             |
| Settings    | `PATCH /settings/{uuid}` (org profile), `GET /settings/teams`                |
| Teams       | `GET /teams`, `DELETE /teams/{uuid}`                                          |
| Roles       | `GET /roles`, `PUT /roles/{id}/permissions`                                  |
| Permissions | `GET /permissions`                                                           |
| Geo         | `GET /geo/states`, `GET /geo/{stateId}/cities`, `GET /geo/{cityId}/postcodes` |
| Admin       | `GET /users`, `PUT /users/{id}/role`                                         |

## Database & migrations

Schema is managed by **Flyway** — versioned SQL files in
`src/main/resources/db/migration` (`V1__…`, `V2__…`, …). New numbered migrations apply
automatically on the next boot; no manual reset is needed.

Conventions:

- **One table per migration file.**
- **No `INSERT`s in migrations** — seed data lives in the `seed` package instead.

Core tables: `tenants`, `users`, `roles`, `permissions`, `role_permissions`,
`refresh_tokens`, `clients`, `invoices`, `invoice_line_items`, `payments`, and geo
reference tables (`states`, `cities`, `postcodes`).

## Related

This is the API half of a two-part project. The client is a **React 19 + Vite** SPA
(`invoice-hub-frontend`) that talks to this service via `VITE_API_URL`.
