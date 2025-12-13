# Banking System REST API

A production-ready Spring Boot REST API for a banking system with JWT authentication, role-based access control, and clean layered architecture.

## Project Purpose

This project demonstrates a complete banking backend system. It showcases:

- Clean layered architecture (Controller → Service → Repository)
- JWT-based authentication & authorization
- Role-based access control (ADMIN/CUSTOMER)
- Atomic financial transactions with overdraft prevention
- RESTful API design with proper HTTP status codes
- Global exception handling with meaningful error responses
- Input validation using Jakarta Validation

## Architecture

```
src/main/java/com/banking/api/
├── config/          # Security and application configuration
├── controller/      # REST API endpoints
├── dto/             # Data Transfer Objects (request/response)
├── entity/          # JPA entities
├── enums/           # Enumeration types
├── exception/       # Custom exceptions & global handler
├── repository/      # Data access layer
├── security/        # JWT utilities & filters
└── service/         # Business logic layer
```

### Entity Relationships

```
User (1) ──────── (*) Account (1) ──────── (*) Transaction
```

## 🛠️ Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Java | 17 | Runtime |
| Spring Boot | 3.2.0 | Framework |
| Spring Security | 6.x | Authentication & Authorization |
| Spring Data JPA | 3.2.x | Data persistence |
| PostgreSQL | 15+ | Database |
| Maven | 3.6+ | Build tool |
| JWT (jjwt) | 0.12.3 | Token authentication |
| Lombok | Latest | Boilerplate reduction |
| Jakarta Validation | 3.0 | Input validation |

## 🚀 Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+
- PostgreSQL 15+ (or Docker)

### Database Setup

**Option 1: Local PostgreSQL**
```sql
CREATE DATABASE banking_db;
```

**Option 2: Docker**
```bash
docker run -d \
  --name banking-db \
  -e POSTGRES_DB=banking_db \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:15
```

### Environment Variables

Create a `.env` file or set environment variables:

```bash
DATABASE_URL=jdbc:postgresql://localhost:5432/banking_db
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres
JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
JWT_EXPIRATION=86400000
```

### Running the Application

```bash
# Clone the repository
git clone <repository-url>
cd spring-banking-api

# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

The API will be available at `http://localhost:8080/api`

## API Endpoints

### Authentication
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login & get JWT |

### Users
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| GET | `/api/users/me` | Authenticated | Get own profile |
| GET | `/api/admin/users` | Admin | List all users |

### Accounts
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| POST | `/api/accounts` | Customer | Create account |
| GET | `/api/accounts` | Customer | Get my accounts |
| GET | `/api/accounts/{id}` | Owner | Get account details |
| GET | `/api/accounts/{id}/balance` | Owner | Get balance |
| GET | `/api/admin/accounts` | Admin | List all accounts |

### Transactions
| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| POST | `/api/transactions/deposit` | Owner | Deposit money |
| POST | `/api/transactions/withdraw` | Owner | Withdraw money |
| POST | `/api/transactions/transfer` | Owner | Transfer money |
| GET | `/api/accounts/{id}/transactions` | Owner | Transaction history |
| GET | `/api/admin/transactions` | Admin | All transactions |

### Health
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/health` | Health check |

## Sample API Requests

### Register User
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "John Doe",
    "email": "john@example.com",
    "password": "password123"
  }'
```

### Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "password": "password123"
  }'
```

### Create Account
```bash
curl -X POST http://localhost:8080/api/accounts \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "accountType": "SAVINGS"
  }'
```

### Deposit Money
```bash
curl -X POST http://localhost:8080/api/transactions/deposit \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": 1,
    "amount": 1000.00,
    "description": "Initial deposit"
  }'
```

### Transfer Money
```bash
curl -X POST http://localhost:8080/api/transactions/transfer \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAccountId": 1,
    "destinationAccountId": 2,
    "amount": 100.00,
    "description": "Payment"
  }'
```

##  Database Schema

### Users Table
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT |
| full_name | VARCHAR(100) | NOT NULL |
| email | VARCHAR(255) | UNIQUE, NOT NULL |
| password | VARCHAR(255) | NOT NULL |
| role | VARCHAR(20) | NOT NULL |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |

### Accounts Table
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT |
| account_number | VARCHAR(20) | UNIQUE, NOT NULL |
| balance | DECIMAL(19,2) | NOT NULL, DEFAULT 0 |
| account_type | VARCHAR(20) | NOT NULL |
| status | VARCHAR(20) | NOT NULL |
| user_id | BIGINT | FK → users.id |
| created_at | TIMESTAMP | NOT NULL |

### Transactions Table
| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT | PK, AUTO_INCREMENT |
| amount | DECIMAL(19,2) | NOT NULL |
| type | VARCHAR(20) | NOT NULL |
| timestamp | TIMESTAMP | NOT NULL |
| reference_number | VARCHAR(36) | UNIQUE, NOT NULL |
| description | VARCHAR(255) | |
| source_account_id | BIGINT | FK → accounts.id |
| destination_account_id | BIGINT | FK → accounts.id, NULLABLE |

## 🔒 Security Features

- **JWT Authentication:** Stateless token-based authentication
- **Password Hashing:** BCrypt with default strength
- **Role-Based Access:** ADMIN and CUSTOMER roles
- **Account Ownership:** Users can only access their own accounts
- **Atomic Transactions:** Pessimistic locking for transfers

## Deployment (Render + Neon)

### Environment Variables for Render

```
DATABASE_URL=jdbc:postgresql://<neon-host>:5432/<database>?sslmode=require
DATABASE_USERNAME=<neon-username>
DATABASE_PASSWORD=<neon-password>
JWT_SECRET=<your-256-bit-secret>
JWT_EXPIRATION=86400000
PORT=8080
```

### Build Command
```bash
mvn clean package -DskipTests
```

### Start Command
```bash
java -jar target/banking-api-1.0.0.jar
```

## 👤 Author

Muganzi Benaiah
