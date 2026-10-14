# Banking API Documentation

## Overview

This document provides comprehensive API documentation for the Banking System REST API. The API provides endpoints for user management, account operations, and financial transactions with JWT-based authentication.

**Base URL:** `http://localhost:8080/api`

---

## Authentication

All protected endpoints require a JWT token in the Authorization header:

```
Authorization: Bearer <token>
```

---

## Endpoints

### Authentication

#### Register User

```http
POST /api/auth/register
```

**Request Body:**
```json
{
  "fullName": "John Doe",
  "email": "john@example.com",
  "password": "password123"
}
```

**Response (201 Created):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600000,
  "user": {
    "id": 1,
    "fullName": "John Doe",
    "email": "john@example.com",
    "role": "CUSTOMER",
    "createdAt": "2024-12-13T10:00:00",
    "updatedAt": "2024-12-13T10:00:00"
  }
}
```

#### Login User

```http
POST /api/auth/login
```

**Request Body:**
```json
{
  "email": "john@example.com",
  "password": "password123"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600000,
  "user": {
    "id": 1,
    "fullName": "John Doe",
    "email": "john@example.com",
    "role": "CUSTOMER"
  }
}
```

---

### User Endpoints

#### Get Current User Profile

```http
GET /api/users/me
Authorization: Bearer <token>
```

**Response (200 OK):**
```json
{
  "id": 1,
  "fullName": "John Doe",
  "email": "john@example.com",
  "role": "CUSTOMER",
  "createdAt": "2024-12-13T10:00:00",
  "updatedAt": "2024-12-13T10:00:00"
}
```

#### Get All Users (Admin Only)

```http
GET /api/admin/users
Authorization: Bearer <admin-token>
```

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": 1,
      "fullName": "John Doe",
      "email": "john@example.com",
      "role": "CUSTOMER"
    },
    {
      "id": 2,
      "fullName": "Admin User",
      "email": "admin@example.com",
      "role": "ADMIN"
    }
  ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 2,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

Page numbers start at zero. Page sizes must be positive and are capped at 100.

---

### Account Endpoints

#### Create Account

```http
POST /api/accounts
Authorization: Bearer <token>
```

**Request Body:**
```json
{
  "accountType": "SAVINGS"
}
```

**Account Types:** `SAVINGS`, `CURRENT`

**Response (201 Created):**
```json
{
  "id": 1,
  "accountNumber": "241213100015001234",
  "balance": 0.00,
  "accountType": "SAVINGS",
  "status": "ACTIVE",
  "userId": 1,
  "userFullName": "John Doe",
  "createdAt": "2024-12-13T10:00:15"
}
```

#### Get My Accounts

```http
GET /api/accounts
Authorization: Bearer <token>
```

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "accountNumber": "241213100015001234",
    "balance": 1000.00,
    "accountType": "SAVINGS",
    "status": "ACTIVE",
    "userId": 1,
    "userFullName": "John Doe"
  }
]
```

#### Get Account by ID

```http
GET /api/accounts/{id}
Authorization: Bearer <token>
```

#### Get Account Balance

```http
GET /api/accounts/{id}/balance
Authorization: Bearer <token>
```

**Response (200 OK):**
```json
{
  "accountId": 1,
  "accountNumber": "241213100015001234",
  "balance": 1000.00,
  "currency": "USD"
}
```

#### Get All Accounts (Admin Only)

```http
GET /api/admin/accounts?page=0&size=20
Authorization: Bearer <admin-token>
```

The response is paginated. Page numbers start at zero, page sizes must be positive, and the server caps page sizes at 100 across admin user, account, transaction, and audit-log collections. Account transaction history uses the same bounds. Negative page numbers and non-positive sizes return `400 Bad Request`.

---

### Transaction Endpoints

#### Deposit Money

```http
POST /api/transactions/deposit
Authorization: Bearer <token>
Idempotency-Key: demo-deposit-001
```

Use a new key for every operation. Retrying the same authenticated user's request with the same key and payload returns the original transaction. Reusing a key with a different payload returns `409 Conflict`. Include `Idempotency-Key` in the configured browser CORS allowed headers.

**Request Body:**
```json
{
  "accountId": 1,
  "amount": 500.00,
  "description": "Salary deposit"
}
```

**Response (201 Created):**
```json
{
  "id": 1,
  "amount": 500.00,
  "type": "DEPOSIT",
  "timestamp": "2024-12-13T10:30:00",
  "referenceNumber": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "description": "Salary deposit",
  "sourceAccountId": 1,
  "sourceAccountNumber": "241213100015001234"
}
```

#### Withdraw Money

```http
POST /api/transactions/withdraw
Authorization: Bearer <token>
Idempotency-Key: demo-withdrawal-001
```

**Request Body:**
```json
{
  "accountId": 1,
  "amount": 200.00,
  "description": "ATM withdrawal"
}
```

**Error Response (400 Bad Request - Insufficient Funds):**
```json
{
  "status": 400,
  "error": "Insufficient Funds",
  "message": "Insufficient funds. Requested: 200.00, Available: 100.00",
  "path": "/api/transactions/withdraw",
  "timestamp": "2024-12-13T10:35:00"
}
```

#### Transfer Money

```http
POST /api/transactions/transfer
Authorization: Bearer <token>
Idempotency-Key: demo-transfer-001
```

**Request Body:**
```json
{
  "sourceAccountId": 1,
  "destinationAccountId": 2,
  "amount": 100.00,
  "description": "Payment for services"
}
```

**Response (201 Created):**
```json
{
  "id": 3,
  "amount": 100.00,
  "type": "TRANSFER",
  "timestamp": "2024-12-13T10:40:00",
  "referenceNumber": "f1e2d3c4-b5a6-7890-1234-567890abcdef",
  "description": "Payment for services",
  "sourceAccountId": 1,
  "sourceAccountNumber": "241213100015001234",
  "destinationAccountId": 2,
  "destinationAccountNumber": "241213100020005678"
}
```

#### Get Transaction History

```http
GET /api/accounts/{accountId}/transactions?page=0&size=10
Authorization: Bearer <token>
```

**Query Parameters:**
- `page` (default: 0) - Page number
- `size` (default: 10) - Items per page

Page numbers start at zero. Page sizes must be positive and are capped at 100. Negative page numbers or non-positive sizes return `400 Bad Request`.

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": 3,
      "amount": 100.00,
      "type": "TRANSFER",
      "timestamp": "2024-12-13T10:40:00",
      "referenceNumber": "f1e2d3c4-b5a6-7890-1234-567890abcdef",
      "description": "Payment for services",
      "sourceAccountId": 1,
      "sourceAccountNumber": "241213100015001234",
      "destinationAccountId": 2,
      "destinationAccountNumber": "241213100020005678"
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

#### Get All Transactions (Admin Only)

```http
GET /api/admin/transactions?page=0&size=10
Authorization: Bearer <admin-token>
```

---

### Health Check

```http
GET /api/health
```

**Response (200 OK):**
```json
{
  "status": "UP",
  "service": "Banking API",
  "version": "1.0.0"
}
```

---

## Error Responses

### Validation Error (400)

```json
{
  "status": 400,
  "error": "Validation Failed",
  "message": "One or more fields have validation errors",
  "path": "/api/auth/register",
  "timestamp": "2024-12-13T10:00:00",
  "validationErrors": {
    "email": "Please provide a valid email address",
    "password": "Password must be between 6 and 100 characters"
  }
}
```

### Unauthorized (401)

```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid email or password",
  "path": "/api/auth/login",
  "timestamp": "2024-12-13T10:00:00"
}
```

### Forbidden (403)

```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "You do not have permission to access this resource",
  "path": "/api/admin/users",
  "timestamp": "2024-12-13T10:00:00"
}
```

### Not Found (404)

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Account not found with id: '999'",
  "path": "/api/accounts/999",
  "timestamp": "2024-12-13T10:00:00"
}
```

---

## Role-Based Access Control

| Role | Permissions |
|------|-------------|
| CUSTOMER | Own profile, own accounts, own transactions |
| ADMIN | Customer read access where permitted, view all users/accounts/transactions, change account status, and review audit logs. Money movement still requires owning the source account. |

---

## Rate Limiting

Currently not implemented. Consider adding for production deployments.

---

## Security Considerations

1. **Password Hashing:** All passwords are hashed using BCrypt
2. **JWT Tokens:** Tokens expire after one hour by default and can be configured with `JWT_EXPIRATION` (milliseconds)
3. **HTTPS:** Always use HTTPS in production
4. **CORS:** Configured for `http://localhost:3000` by default; production origins are set with `CORS_ALLOWED_ORIGINS`
