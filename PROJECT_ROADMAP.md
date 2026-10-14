# Banking API: project roadmap

## Current state

The core API implementation is complete and verified locally. It provides customer authentication, account management, simulated deposits, withdrawals and transfers, spending controls, date-range statements, audit records, and retry-safe transaction requests. PostgreSQL migrations, Docker Compose configuration, CI, and automated unit and integration tests are included.

A live HTTP workflow also passed against a dedicated local PostgreSQL database, covering registration, account creation, a deposit, an idempotent transfer retry, spending-limit enforcement, and balance reads.

The service handles fictional balances only. It is not connected to banks or payment networks and is not ready to process real funds.

## Before a hosted environment

1. Rotate any database credential that may still be active from earlier configuration.
2. Inspect and back up the target database. Confirm its schema matches the legacy schema expected by Flyway baseline version 1 before applying migration V2.
3. Provision production secrets through the hosting provider. Use a restricted database account, a strong JWT signing key, explicit CORS origins, and a deliberate time zone.
4. Create administrator access through a trusted operational path. Public registration must continue to assign the customer role.
5. Run the complete workflow against the Compose PostgreSQL stack, including registration, account creation, money movement, duplicate retries, limits, statements, and admin audit review.
6. Select a hosting provider only after checking its current cost, database durability, backup support, and runtime requirements. Verify health checks, logs, backups, and recovery before sharing an endpoint.

The repository's migration test verifies the expected legacy schema shape in a fresh test database. It does not inspect or migrate an external database on the owner's behalf.

## Follow-up engineering

- Add refresh and revocation behavior for authentication tokens.
- Add an explicit currency model before supporting multiple currencies.
- Consider a double-entry ledger and reconciliation workflow if the application scope expands beyond a single-currency simulation.
- Add monitoring for failed authentication, transaction conflicts, migration status, and database health before operating a hosted environment.
- Add load tests only when expected traffic and a target environment are defined.

## Current boundaries

The API uses one application and one PostgreSQL database. It does not include payment-provider integration, real deposits, cards, loans, interest, foreign exchange, or banking compliance workflows. These remain out of scope until there is a verified product requirement and an appropriate operational and regulatory review.
