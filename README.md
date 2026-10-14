# Banking API

A Java and Spring Boot banking simulation built to demonstrate authenticated account management and reliable money movement. It supports simulated deposits, withdrawals, transfers, account statements, spending controls, and admin audit review.

This is a portfolio project. Balances are simulated funds. It does not connect to a payment network or provide a real banking service.

## Engineering highlights

- Layered Spring MVC, service, and JPA repository structure.
- JWT authentication, BCrypt password hashing, customer/admin roles, and account ownership checks.
- Atomic balance changes with database account locks and consistent lock ordering for transfers.
- Durable `Idempotency-Key` handling for deposits, withdrawals, and transfers. A retry with the same authenticated user, key, and payload returns the original transaction. Reusing a key with a different payload returns HTTP 409.
- Per-account single-transaction, daily, and monthly spending limits, enforced inside the balance transaction.
- Date-range statements with calculated opening/closing balances and explicit rejection above 10,000 entries.
- PostgreSQL schema migrations, database constraints, unit tests, PostgreSQL integration tests, and GitHub Actions CI.

## Stack

Java 17, Spring Boot 3.2, Spring Security, Spring Data JPA, PostgreSQL 15, Flyway, JUnit 5, Mockito, Testcontainers, Maven, and Docker Compose.

## Run locally

Requirements: Java 17, Maven 3.6 or later, and Docker Compose for the PostgreSQL setup.

Fast H2 development mode:

```bash
mvn spring-boot:run
```

The default `local` profile uses an in-memory H2 database and a local-only signing key. Data is reset when the application stops. Do not use this profile as a deployed environment.

PostgreSQL mode, recommended for the complete demo:

```bash
cp .env.example .env
```

Edit `.env` and replace both placeholder values. Make a signing secret with `openssl rand -base64 48`, then start the API and database:

```bash
docker compose up --build
```

The API is at `http://localhost:8080/api`; Swagger UI is at `http://localhost:8080/swagger-ui/index.html`. Stop containers with `docker compose down`. Keep the PostgreSQL data volume by default; use `docker compose down -v` only when intentionally resetting the local demo database.

The production profile requires `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, and `JWT_SECRET`. It applies Flyway migrations and validates the mapped schema at startup. For an existing database, review and back up the schema first: Flyway baselines a non-empty schema at version 1 and applies the safety/idempotency migration. If the current schema differs from the documented model, reconcile it before startup. Configure `APP_TIME_ZONE` for the timezone used by spending-limit periods and `CORS_ALLOWED_ORIGINS` as a comma-separated list for any browser client.

## Test

```bash
mvn verify
```

Unit tests run with Maven. PostgreSQL integration tests use Testcontainers and run when a Docker-compatible container runtime is available; CI runs them on GitHub Actions.

## Demo flow

1. Register two customers with `POST /api/auth/register` and log in with `POST /api/auth/login`.
2. Create an account for each customer using `POST /api/accounts`.
3. Deposit simulated funds. Include a fresh `Idempotency-Key` header, for example `demo-funding-001`.
4. Transfer funds between accounts with another unique key. Retry the exact request with the same key to see the original transaction returned without a second debit.
5. Set an account spending limit and show a withdrawal that exceeds it being rejected without changing the balance.
6. View the transaction history and a statement covering the transfer date.

Registering always creates a customer. The repository does not expose an HTTP action to promote a customer to an administrator; set up demo admin access through a trusted database or local-only seed process.

Example deposit request:

```bash
curl -X POST http://localhost:8080/api/transactions/deposit \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Idempotency-Key: demo-funding-001" \
  -H "Content-Type: application/json" \
  -d '{"accountId":1,"amount":1000.00,"description":"Demo funding"}'
```

Transfer requests, limits, authorization, and error behavior are documented in [API_DOCUMENTATION.md](API_DOCUMENTATION.md). Architecture and database details are in [TECHNICAL_DOCUMENTATION.md](TECHNICAL_DOCUMENTATION.md).

## Scope and next steps

The project is single-currency and uses simulated funding. It does not implement a double-entry ledger, payment provider, token refresh/revocation, or a production bank compliance program. See [PORTFOLIO_ROADMAP.md](PORTFOLIO_ROADMAP.md) for the wider portfolio plan.

## Portfolio summary

**Banking API:** Java backend with JWT security, PostgreSQL persistence, atomic transfer processing, retry-safe requests, spending controls, and database integration tests.

Author: Muganzi Benaiah
