# Banking API: portfolio roadmap

Target role: Java backend developer. This is a plan based on source inspection, not a runtime audit. Application behavior and deployment have not been verified.

## Project positioning

Build a banking simulation that demonstrates reliable money movement, clear access rules, and a repeatable delivery process.

Suggested portfolio description after completion:

> A Spring Boot banking simulation with PostgreSQL, JWT authentication, atomic transfers, retry-safe transaction requests, spending controls, and automated integration tests.

The strongest interview story is a specific failure you prevented: two simultaneous withdrawals cannot overdraw an account, and retrying a transfer cannot debit it twice. Support those claims with tests and a short demo.

## Implementation status

Core backend scope is now implemented and locally verified. `mvn verify` passes with unit tests and PostgreSQL Testcontainers tests for concurrent duplicate deposits, concurrent withdrawals, ledger boundaries, PostgreSQL migrations, and preserving an existing database when Flyway history is absent. The local H2 profile also starts and responds `UP` at `/api/health`.

Still to do before calling the portfolio release complete: rotate the old database credential if it is active, inspect and back up the real database before its first migration, create a restricted demo admin, run the full demo against the Compose stack, deploy only if a suitable hosting option is chosen, and record the walkthrough. The migration baseline was tested using the original schema shape; it does not verify an external database that this repository cannot access.

## Existing foundation and gaps

| Area | What exists | Work needed |
| --- | --- | --- |
| Structure | Layered controllers, services, repositories, DTOs, validation, and global errors | Keep routine API and administration changes reviewable |
| Authentication | JWT, BCrypt, customer/admin roles, ownership checks, configurable CORS | Rotate any old remote credential before publishing; provision demo admin access through a trusted path |
| Money movement | BigDecimal, atomic transactions, consistent account locks, durable idempotency | Inspect real production data before applying constraints |
| Spending controls | Single, daily, and monthly limits are enforced in the locked balance transaction | Keep zero as a limit that blocks all positive spending; document the chosen business rule |
| Statements | Date-range opening/closing balances and ordered activity | Requests above 10,000 entries return an error and need a narrower date range |
| Audit trail | Financial posting, status change, limit update, and statement actions | Keep audit detail free of credentials and unnecessary personal data |
| Database | H2 local profile, PostgreSQL profile, Flyway baseline and migrations, relational checks | Compare the real database with the tested legacy schema before first startup |
| Delivery | Maven tests, Testcontainers, GitHub Actions, Docker Compose, Swagger, refreshed docs | Run the demo on the Compose stack and decide whether hosting is worth the cost |

Production secret fallbacks have been removed from `application.yml`. Rotate the old database credential if it is still active, and check repository history before publishing. Do not copy credential values into documentation.

The README describes the project as a banking simulation. Keep portfolio claims tied to behavior that has been implemented and verified.

## Release scope and architecture

Keep one Spring Boot application and one PostgreSQL database, organized by the existing layers. Use JPA and ordinary database transactions. Add only Flyway, PostgreSQL-backed integration testing, container configuration, and CI as required infrastructure.

The first portfolio release includes:

- Registration/login, customer profiles, and account creation.
- Simulated deposits, withdrawals, and transfers with transaction history.
- Single, daily, and monthly spending limits that actually reject excess spending.
- Accurate date-range statements and an admin audit view.
- Account suspension/reactivation with explicit permissions and an audit record.
- Idempotency for all balance-changing requests.
- Verified local startup, CI, API documentation, and a synthetic-data demo.

Use one documented currency across the application. Choose its amount scale before tightening validation; a two-decimal demo currency matches the current schema. Store timestamps consistently in UTC and document the timezone used for daily/monthly spending periods. All balances represent simulated funds.

Admin access should permit inspection, status management, and audit review. Money movement should require ownership of the source account even for an admin; public registration must always create a customer. Provision demo admins through an explicit local/demo setup.

Defer microservices, Kafka, Kubernetes, real payment providers, loans, cards, interest, foreign exchange, and a full accounting ledger. A polished frontend is optional after the backend release is complete.

## Milestone 1: establish a reproducible baseline, implemented

Tasks:

- Run the existing build and exercise register → login → create account → deposit → transfer → history. Capture any failures as small issues.
- Remove production secret defaults; add an example environment file with placeholders. Keep local-only defaults confined to local configuration.
- Make H2-console access local-only and make allowed browser origins configurable.
- Add Docker Compose for the application and PostgreSQL. A Maven Wrapper remains an optional convenience.
- Introduce Flyway migrations and change persistent database profiles from Hibernate schema updates to schema validation. If preserving an existing database, inspect and baseline it deliberately; do not reset its data.
- Review the pinned Spring Boot/dependency versions and choose a supported, compatible baseline. Handle upgrades in a focused change with build verification.
- Fix the README startup instructions: distinguish the H2 default from PostgreSQL, explain profile selection, and show how environment variables are supplied.

Completion evidence: a fresh checkout starts with the documented commands, migrations create the database, records survive restart, and the production profile refuses to start without required secrets. No real credentials remain in tracked configuration.

## Milestone 2: make the current rules correct, implemented

Tasks:

- Validate money amounts against database precision/scale instead of allowing silent rounding; bound descriptions and pagination inputs.
- Normalize email consistently before both duplicate checks and persistence; keep database uniqueness as the final safeguard.
- Define permitted account status transitions. Coordinate status changes and limit updates with the same account locking strategy used for money movement.
- Enforce single/daily/monthly spending limits inside the withdrawal/transfer transaction. Deposits do not consume spending limits; incoming transfers do not consume the recipient's limits. Define how a limit is removed and how zero behaves.
- Calculate usage with database date-range aggregates, including the exact start boundary of each spending period.
- Correct statements: opening balance comes from activity before the start, closing balance from activity through the end, and running balances follow stable timestamp/ID order. Transactions after the selected period must not alter its balances.
- Query statements by date range in the database. Use pagination or a documented bounded export with an explicit error for an oversized request; never silently omit transactions.
- Put status mutation and its audit entry in one service transaction. Add successful deposit, withdrawal, and transfer audit entries within their balance-changing transactions. Keep denied attempts in separate application/security logs if needed.
- Add database checks for positive transaction amounts and nonnegative balances, consistent with a no-overdraft simulation.

Completion evidence: tests prove ownership, role boundaries, suspended-account rejection, amount precision, limit boundaries, historical statement accuracy, and complete calculations beyond 1,000 transactions. Failed operations change neither balances nor successful-operation records.

## Milestone 3: add the signature feature, retry-safe money movement, implemented

Tasks:

- Accept an `Idempotency-Key` header on deposits, withdrawals, and transfers.
- Store a durable request record scoped to authenticated user + operation + key, with a fingerprint of the validated request and its successful result.
- Save that record, balance changes, transaction, and audit event atomically. Enforce scope uniqueness in PostgreSQL so simultaneous duplicates cannot both post.
- Replay the original result for the same key and payload; reject the same key with a different payload using a documented conflict response.
- Specify concurrent duplicate handling and failed-request behavior. For the first release, retain successful keys without expiry; a rolled-back request may be retried because nothing posted.
- Test the existing lock ordering and serializable isolation against PostgreSQL before changing them. Handle transient database conflicts with a documented response; any automatic retry must be bounded and rerun the entire transaction safely.

Completion evidence: sequential and simultaneous duplicate requests produce one posting, a retry after application restart returns the same transaction reference, conflicting payloads return a conflict, and an injected persistence failure rolls back the entire operation.

Concurrency demonstration: with a balance of 100, simultaneous withdrawals of 80 must never both succeed. After terminal responses, the balance is 20 if one succeeds or 100 if neither commits. Opposing transfers preserve the combined balance, and tests detect partial posting or indefinite waits.

## Milestone 4: prove the backend and automate delivery, implemented

Tasks:

- Use focused unit tests for calculations and validation, HTTP/security tests for API permissions, and PostgreSQL integration tests for persistence, rollback, locking, and idempotency.
- Use Testcontainers for isolated PostgreSQL test databases. Concurrency tests must use separate transactions/connections and deterministic coordination.
- Configure Maven so `./mvnw verify` runs both unit and integration tests; CI must use that same command and fail if required tests fail.
- Add a GitHub Actions build/test workflow and publish test results. Keep test evidence more prominent than an arbitrary coverage percentage.
- Add minimal health/readiness checks and request correlation IDs; avoid logging tokens, passwords, or full sensitive payloads. Restrict any detailed management endpoints.
- Bound collection endpoints and use database aggregates for admin summaries instead of loading every row.

Completion evidence: CI passes from a clean checkout; the suite exercises real PostgreSQL; a reviewer can find named tests for duplicate transfers, concurrent withdrawals, rollback, historical statements, and authorization.

## Milestone 5: package the portfolio release, in progress

Tasks:

- Deploy the API and PostgreSQL to one suitable hosting setup. Choose hosting after checking current cost/runtime requirements; retain a complete local demo regardless of hosting availability.
- Seed synthetic customers and accounts in a dedicated demo profile. Document reset behavior and keep public demo admin access restricted.
- Update Swagger examples and existing API/technical documents to match tested behavior, including idempotency, limits, currency, and error responses.
- Rewrite the README with the problem, key capabilities, startup, architecture diagram, test command, demo link, and explicit scope.
- Write three short decision notes: why one application, how concurrency is controlled, and how idempotency survives retries/restarts.
- Record a 2–3 minute demo and tag the completed portfolio release. Add measured performance results only if you run a reproducible benchmark and report its environment.

Completion evidence: both customers can authenticate, a transfer changes both balances and histories, a repeated request causes no extra debit, a limit violation changes nothing, and an admin can inspect the audit trail. The same walkthrough works locally and on the deployed demo.

## Work order and effort

Create one small issue for each task and finish milestones in order. Prefer reviewable changes with a behavior example and relevant test evidence. Add regression tests alongside each correctness fix, rather than waiting for the CI milestone.

The initial planning estimate was 40–60 focused hours. The main engineering uncertainties, PostgreSQL migrations and concurrent idempotency, have been tested. Remaining time depends on hosting and credential access; the local demo does not depend on deployment.

If time is tight, prioritize secret cleanup, PostgreSQL setup, correctness fixes, idempotency, critical integration tests, CI, and documentation. A hosted demo and frontend can follow. Do not advertise an unfinished feature as part of the release.

## Portfolio inventory entry

Use this after the corresponding features are implemented and verified:

- **Title:** Banking API: reliable transaction processing with Java and Spring Boot.
- **Role:** Backend developer; API design, persistence, security, testing, and delivery.
- **Stack:** Java, Spring Boot, Spring Security, PostgreSQL, JPA, Flyway, JUnit, Testcontainers, Docker, GitHub Actions.
- **Engineering highlight:** Atomic transfers, retry-safe requests, and concurrency tests that prevent overdrafts and duplicate debits.
- **Evidence:** Repository, API docs/demo, CI results, architecture/decision notes, and short walkthrough.
- **Scope:** Single-currency banking simulation using synthetic data and simulated funding.

Interview example: “I used database locking and transactions to keep transfers atomic, then added durable idempotency so a repeated client request cannot post twice. I verified those behaviors against PostgreSQL with simultaneous requests and rollback tests.”

## Technical references

- [Testcontainers PostgreSQL module](https://java.testcontainers.org/modules/databases/postgres/) supports running PostgreSQL containers for integration testing.
- [Flyway migrations](https://documentation.red-gate.com/flyway/flyway-concepts/migrations) provide version-controlled schema changes applied consistently across environments.
- [Spring Boot project and support information](https://spring.io/projects/spring-boot/) should be checked when selecting the dependency baseline.
