# Gates: banking API implementation

OWNS: src/**, pom.xml, .github/**, Dockerfile, compose.yaml, .env.example, README.md, API_DOCUMENTATION.md, TECHNICAL_DOCUMENTATION.md, GATES.md

Scope: make the existing banking simulation safer to configure, correct the core money workflows, and establish a presentable backend baseline.

- [x] G1: production configuration requires secrets and database schema uses versioned migrations
  EVIDENCE: application.yml requires the production database and JWT variables, uses validate plus Flyway, and FlywayBaselineIT passed against a populated PostgreSQL schema.

- [x] G2: spending limits, historical statements, and admin financial permissions have explicit correct behavior
  EVIDENCE: StatementServiceTest passed 3 cases including the repeatable-read statement contract and spending limits; TransactionRepositoryIT passed the HTTP flow that rejects an over-limit withdrawal; transaction ownership checks apply to admin users too.

- [x] G3: critical money workflows have PostgreSQL integration coverage and repeatable local/CI instructions
  EVIDENCE: mvn -B verify passed 6 unit tests and 5 PostgreSQL integration tests; a live HTTP workflow passed against local PostgreSQL; docker compose config --quiet passed with placeholder environment values; CI runs verify.

- [x] G4: project documentation describes a tested banking simulation without claiming production readiness
  EVIDENCE: README, API_DOCUMENTATION, TECHNICAL_DOCUMENTATION, and OpenAPI metadata describe fictional balances; the Maven and OpenAPI descriptions make no production-readiness claim.
