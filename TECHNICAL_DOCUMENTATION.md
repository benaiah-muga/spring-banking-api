# Technical Documentation - Banking System API

## Table of Contents

1. [System Overview](#system-overview)
2. [Architecture](#architecture)
3. [Security Implementation](#security-implementation)
4. [Database Design](#database-design)
5. [API Design Patterns](#api-design-patterns)
6. [Transaction Handling](#transaction-handling)
7. [Error Handling](#error-handling)
8. [Configuration Management](#configuration-management)
9. [Deployment Guide](#deployment-guide)
10. [Performance Considerations](#performance-considerations)

---

## System Overview

The Banking System API is a banking simulation built with Spring Boot 3.2. Balances use synthetic funds; the service does not connect to banks or payment networks.

### Key Capabilities

| Feature | Description |
|---------|-------------|
| User Management | Registration, authentication, profile management |
| Account Management | Create/view accounts, balance inquiries |
| Transactions | Deposits, withdrawals, transfers with atomicity |
| Retry safety | Durable per-user idempotency records return the original transaction on request retry |
| Spending controls | Single-transaction, daily, and monthly limits enforced during withdrawals and transfers |
| Statements | Date-range opening and closing balances are calculated from the transaction ledger |
| Security | JWT-based auth, role-based access control (RBAC) |
| Admin Functions | View all users, accounts, and transactions |

---

## Architecture

### Layered Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Client Layer                            │
│              (Mobile Apps, Web Apps, Third Party)            │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Controller Layer                          │
│   AuthController │ UserController │ AccountController │ ...  │
│                  (REST Endpoints)                            │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     Service Layer                            │
│     UserService  │  AccountService  │  TransactionService    │
│                  (Business Logic)                            │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Repository Layer                           │
│   UserRepository │ AccountRepository │ TransactionRepository │
│                  (JPA Data Access)                           │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   PostgreSQL Database                        │
│             users │ accounts │ transactions                  │
└─────────────────────────────────────────────────────────────┘
```

### Package Structure

```
com.banking.api
├── config/           # Security, OpenAPI configuration
├── controller/       # REST API endpoints (6 controllers)
├── dto/
│   ├── request/      # Incoming data validation
│   └── response/     # Outgoing data
├── entity/           # JPA entities, including durable idempotency records
├── enums/            # Role, AccountType, AccountStatus, TransactionType
├── exception/        # Custom exceptions + GlobalExceptionHandler
├── repository/       # Spring Data JPA interfaces
├── security/         # JWT service, filter, UserDetailsService
├── service/          # Business logic layer
└── util/             # Shared request helpers, including pagination bounds
```

---

## Security Implementation

### Authentication Flow

```
┌─────────┐     ┌─────────────────┐     ┌────────────────┐
│ Client  │────▶│ /api/auth/login │────▶│ UserService    │
└─────────┘     └─────────────────┘     └────────────────┘
                                               │
                                               ▼
                                        ┌────────────────┐
                                        │ AuthManager    │
                                        │ (BCrypt Check) │
                                        └────────────────┘
                                               │
                                               ▼
                                        ┌────────────────┐
                                        │ JwtService     │
                                        │ (Generate JWT) │
                                        └────────────────┘
                                               │
                                               ▼
                                        ┌────────────────┐
                                        │ AuthResponse   │
                                        │ {token, user}  │
                                        └────────────────┘
```

### JWT Token Structure

```json
{
  "header": {
    "alg": "HS256",
    "typ": "JWT"
  },
  "payload": {
    "sub": "user@example.com",
    "iat": 1702468800,
    "exp": 1702555200
  },
  "signature": "..."
}
```

### Role-Based Access Control Matrix

| Endpoint Pattern | CUSTOMER | ADMIN |
|-----------------|:--------:|:-----:|
| `/api/auth/**` | ✅ Public | ✅ Public |
| `/api/users/me` | ✅ | ✅ |
| `/api/accounts/**` | ✅ (own) | ✅ (all) |
| `/api/transactions/**` | ✅ (own) | ✅ |
| `/api/admin/**` | ❌ | ✅ |

### Security Configuration Highlights

```java
// Key security settings
.csrf(AbstractHttpConfigurer::disable)  // Stateless API
.sessionManagement(SessionCreationPolicy.STATELESS)
.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
```

---

## Database Design

### Entity Relationship Diagram

```
┌──────────────────────┐       ┌──────────────────────┐
│        users         │       │      accounts        │
├──────────────────────┤       ├──────────────────────┤
│ id (PK)              │       │ id (PK)              │
│ full_name            │       │ account_number (UQ)  │
│ email (UQ)           │◀──────│ user_id (FK)         │
│ password (hash)      │  1:N  │ balance              │
│ role                 │       │ account_type         │
│ created_at           │       │ status               │
│ updated_at           │       │ created_at           │
└──────────────────────┘       └──────────────────────┘
                                         │
                         ┌───────────────┼───────────────┐
                         │               │               │
                         ▼               ▼               │
               ┌──────────────────────────────────┐     │
               │         transactions             │     │
               ├──────────────────────────────────┤     │
               │ id (PK)                          │     │
               │ amount                           │     │
               │ type                             │     │
               │ timestamp                        │     │
               │ reference_number (UQ)            │     │
               │ description                      │     │
               │ source_account_id (FK) ──────────┘     │
               │ destination_account_id (FK, nullable)──┘
               └──────────────────────────────────┘
```

### Data Types and Constraints

| Column | PostgreSQL Type | Java Type | Constraint |
|--------|-----------------|-----------|------------|
| id | BIGSERIAL | Long | PRIMARY KEY |
| balance | NUMERIC(19,2) | BigDecimal | NOT NULL, DEFAULT 0 |
| email | VARCHAR(255) | String | UNIQUE, NOT NULL |
| password | VARCHAR(255) | String | NOT NULL (BCrypt) |
| role | VARCHAR(20) | Enum | CHECK (ADMIN, CUSTOMER) |
| timestamp | TIMESTAMP | LocalDateTime | NOT NULL |
| amount | NUMERIC(19,2) | BigDecimal | NOT NULL, greater than zero |
| idempotency_key | VARCHAR(128) | String | UNIQUE with user_id |

---

## API Design Patterns

### Request/Response Flow

```
Request → Validation → Controller → Service → Repository → Database
                                                    ↓
Response ← DTO Mapping ← Response Builder ← Entity ←────────────────
```

### DTO Pattern Examples

**Request DTO with Validation:**
```java
public class TransferRequest {
    @NotNull(message = "Source account ID is required")
    private Long sourceAccountId;
    
    @NotNull(message = "Destination account ID is required")
    private Long destinationAccountId;
    
    @NotNull @DecimalMin("0.01")
    private BigDecimal amount;
}
```

**Response DTO with Entity Mapping:**
```java
public class AccountResponse {
    // Fields omitted
    
    public static AccountResponse fromEntity(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                // ...
                .build();
    }
}
```

---

## Transaction Handling

### Atomic Transfer Implementation

Money-changing service methods use Spring's default `READ_COMMITTED` isolation and a database transaction. They first lock the idempotency stripe for the authenticated user and request key, then lock the source and destination accounts in ascending account ID order. The account locks serialize competing balance changes, and the shared ordering prevents opposing transfers from acquiring account locks in different orders.

Within that transaction, the service checks ownership, account status, spending limits, and available funds. It updates both balances and writes the transaction, audit event, and idempotency record together. If any step fails, the database rolls back the entire operation. Repeating a successful request with the same user, key, and payload returns the original transaction; reusing that key for a different operation or payload returns `409 Conflict`.

The implementation uses `READ_COMMITTED`, not `SERIALIZABLE`: account row locks provide the required balance serialization, while the idempotency stripe serializes duplicate keys before the transaction reads a prior result.

### Pessimistic Locking Query

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM Account a WHERE a.id = :id")
Optional<Account> findByIdWithLock(@Param("id") Long id);
```

---

## Error Handling

### Exception Hierarchy

```
RuntimeException
├── ResourceNotFoundException (404)
├── BadRequestException (400)
├── UnauthorizedException (401)
├── AccessDeniedException (403)
└── InsufficientFundsException (400)
```

### Global Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getAllErrors()
                .stream()
                .collect(Collectors.toMap(
                    e -> ((FieldError) e).getField(),
                    ObjectError::getDefaultMessage
                ));
        return ResponseEntity.badRequest()
                .body(ErrorResponse.withValidationErrors(400, "Validation Failed", errors));
    }
}
```

### Standard Error Response Format

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Insufficient funds. Requested: 500.00, Available: 100.00",
  "path": "/api/transactions/withdraw",
  "timestamp": "2024-12-13T10:30:00",
  "validationErrors": null
}
```

---

## Configuration Management

### Environment Variables

| Variable | Purpose | Default |
|----------|---------|---------|
| `DATABASE_URL` | PostgreSQL connection string | Required for `prod` |
| `DATABASE_USERNAME` | DB username | Required for `prod` |
| `DATABASE_PASSWORD` | DB password | Required for `prod` |
| `JWT_SECRET` | Base64 encoded HMAC signing key | Required for `prod` |
| `JWT_EXPIRATION` | Token lifetime in milliseconds | 3600000 (1h) |
| `APP_TIME_ZONE` | Timezone used for daily and monthly limits | UTC |
| `CORS_ALLOWED_ORIGINS` | Comma-separated browser origins | http://localhost:3000 |
| `PORT` | Server port | 8080 |

### application.yml Structure

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 1

jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION:3600000}
```

On a non-empty database without Flyway history, Flyway marks the existing schema at version 1 and runs the second migration. Back up and inspect existing data first; constraints in that migration intentionally fail if current data contains negative balances or non-positive transactions.

---

## Deployment Guide

### Local PostgreSQL with Docker Compose

```dockerfile
cp .env.example .env
docker compose up --build
```

Replace the environment file placeholders before starting. CI runs `mvn verify`; PostgreSQL integration tests use Testcontainers.

---

## Performance Considerations

### Optimizations Implemented

| Optimization | Implementation |
|--------------|----------------|
| Pagination | PagedResponse for transaction history |
| Lazy Loading | FetchType.LAZY on entity relationships |
| Pessimistic Locking | Prevents race conditions in transfers |
| Idempotency lock stripes | Serialize matching retry keys across application instances |
| Index on account_number | Unique constraint creates index |
| Connection Pooling | HikariCP (Spring Boot default) |

### Recommended Production Enhancements

1. **Rate Limiting** - Add bucket4j or Spring Cloud Gateway
2. **Caching** - Redis for user sessions/frequently accessed data
3. **Monitoring** - Spring Actuator + Prometheus/Grafana
4. **Logging** - Structured logging with correlation IDs
5. **API Versioning** - Path-based versioning (/api/v1/)

---

## API Documentation

- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:8080/v3/api-docs`
- **Markdown Docs:** [API_DOCUMENTATION.md](./API_DOCUMENTATION.md)
