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

The Banking System API is a RESTful web service built with Spring Boot 3.2, designed to handle core banking operations including user authentication, account management, and financial transactions.

### Key Capabilities

| Feature | Description |
|---------|-------------|
| User Management | Registration, authentication, profile management |
| Account Management | Create/view accounts, balance inquiries |
| Transactions | Deposits, withdrawals, transfers with atomicity |
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
├── controller/       # REST API endpoints (5 controllers)
├── dto/
│   ├── request/      # Incoming data validation (6 DTOs)
│   └── response/     # Outgoing data (7 DTOs)
├── entity/           # JPA entities (3 entities)
├── enums/            # Role, AccountType, AccountStatus, TransactionType
├── exception/        # Custom exceptions + GlobalExceptionHandler
├── repository/       # Spring Data JPA interfaces
├── security/         # JWT service, filter, UserDetailsService
└── service/          # Business logic layer
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

```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public TransactionResponse transfer(TransferRequest request) {
    // 1. Acquire pessimistic locks in consistent order (prevent deadlock)
    Account source = accountRepository.findByIdWithLock(sourceId);
    Account dest = accountRepository.findByIdWithLock(destId);
    
    // 2. Validate sufficient funds
    if (source.getBalance().compareTo(amount) < 0) {
        throw new InsufficientFundsException(amount, source.getBalance());
    }
    
    // 3. Execute atomic update
    source.setBalance(source.getBalance().subtract(amount));
    dest.setBalance(dest.getBalance().add(amount));
    
    // 4. Create audit record
    Transaction tx = Transaction.builder()
            .type(TransactionType.TRANSFER)
            .amount(amount)
            .sourceAccount(source)
            .destinationAccount(dest)
            .referenceNumber(UUID.randomUUID().toString())
            .build();
    
    return TransactionResponse.fromEntity(transactionRepository.save(tx));
}
```

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
| `DATABASE_URL` | PostgreSQL connection string | localhost:5432/banking_db |
| `DATABASE_USERNAME` | DB username | postgres |
| `DATABASE_PASSWORD` | DB password | postgres |
| `JWT_SECRET` | HMAC signing key (256-bit) | [dev key] |
| `JWT_EXPIRATION` | Token TTL in milliseconds | 86400000 (24h) |
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
      ddl-auto: update  # Use 'validate' in production
    show-sql: false

jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION:86400000}
```

---

## Deployment Guide

### Render Deployment

1. **Create Web Service** on Render
2. **Connect GitHub Repository**
3. **Configure Environment Variables** (see table above)
4. **Build Command:** `mvn clean package -DskipTests`
5. **Start Command:** `java -jar target/banking-api-1.0.0.jar`

### Neon PostgreSQL Setup

1. Create a Neon project
2. Copy connection string
3. Set `DATABASE_URL` with `?sslmode=require` suffix

### Docker Deployment

```dockerfile
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY target/banking-api-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## Performance Considerations

### Optimizations Implemented

| Optimization | Implementation |
|--------------|----------------|
| Pagination | PagedResponse for transaction history |
| Lazy Loading | FetchType.LAZY on entity relationships |
| Pessimistic Locking | Prevents race conditions in transfers |
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
