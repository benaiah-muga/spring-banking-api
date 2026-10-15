# ERP payment order API

This document defines the bank-side REST contract for ChasteBusiness OS to submit supplier payment orders. ChasteBusiness OS is the system of record for supplier invoices, invoice approval, tax, and invoice status. The banking API owns the payment order, the source account debit, and the resulting transaction record.

Payment execution is simulated. This API does not connect to a payment network or external payment provider. The simulated rail settles synchronously. The endpoints and behavior should be checked against the running implementation before treating this contract as a deployed integration.

The first workflow covers one full payment against an approved supplier invoice. Partial payment, refunds, chargebacks, reconciliation files, and multi-currency account balances are outside the current scope.

## Authentication

The integration uses the API's existing Bearer JWT authentication:

```http
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
Accept: application/json
```

The authenticated user must own the source account named in the request. Account ownership, account status, currency, and available balance are checked by the bank API. A caller must not reuse a customer token that does not have access to the paying account. Do not place JWTs in source control, API examples, or logs.

## Create a payment order

```http
POST /api/v1/erp/payment-orders
Authorization: Bearer <JWT_TOKEN>
Idempotency-Key: chaste-ap-2026-00481
```

```json
{
  "sourceAccountId": 42,
  "erpPaymentReference": "PAY-2026-00481",
  "invoiceReference": "AP-2026-00481",
  "beneficiaryName": "Northwind Supplies",
  "beneficiaryAccount": "SIM-001-9284",
  "amount": 1250.00,
  "currency": "USD"
}
```

The bank identifies this integration as ChasteBusiness OS on the server. `erpPaymentReference` is the ERP's stable unique identifier for this payment request. `invoiceReference` links the order to the approved invoice and may be omitted when the ERP has no invoice identifier. The beneficiary fields are simulated rail data, not a verified bank account. The bank does not validate beneficiary account ownership or route funds externally.

The amount must be positive and fit the bank's two-decimal amount scale. The current account model is single-currency USD and this payment API accepts USD only. Other currencies require account currency support and a later payment-rail implementation.

On a successful simulated payout, the API settles the order synchronously, debits the source account, creates a `WITHDRAWAL` transaction, and records the transaction reference. These database changes must commit together. The response is `201 Created`:

```json
{
  "id": 81,
  "userId": 7,
  "sourceAccountId": 42,
  "erpPaymentReference": "PAY-2026-00481",
  "invoiceReference": "AP-2026-00481",
  "beneficiaryName": "Northwind Supplies",
  "beneficiaryAccount": "SIM-001-9284",
  "amount": 1250.00,
  "currency": "USD",
  "status": "SETTLED",
  "transactionReference": "d90951c5-e288-47c2-bdc9-30af980d72d5",
  "createdAt": "2026-10-15T10:15:30",
  "updatedAt": "2026-10-15T10:15:30"
}
```

Treat beneficiary details and payment references as sensitive business data. Store only the data needed for execution and reconciliation, and avoid logging full account values.

## Idempotency and ERP references

`Idempotency-Key` is required and must contain 1 to 128 characters. It protects the create request from duplicate submission after a client timeout. The combination of authenticated user, the server-defined ChasteBusiness OS integration identity, and `erpPaymentReference` is also unique, so the ERP should use the same payment reference for retries of one logical payment.

- Repeating an order for the same authenticated user with the same ERP payment reference and identical payload returns the original payment order and transaction result. It does not debit the account again.
- Reusing the same ERP reference with a changed source account, beneficiary, amount, currency, or invoice reference returns `409 Conflict`.
- Use a new ERP payment reference and a new idempotency key for a genuinely new payment request. Do not create a new reference just because the previous HTTP response timed out. Query the existing order first.

ChasteBusiness OS should persist the returned bank order ID and transaction reference alongside its own invoice and payment records.

## Query a payment order

```http
GET /api/v1/erp/payment-orders/{id}
Authorization: Bearer <JWT_TOKEN>
```

The authenticated caller may read only payment orders within its authorized account and user scope. A successful response uses the same representation as create. An unknown order or an order outside the caller's authorization scope returns `404 Not Found`.

The initial simulated rail normally returns `SETTLED` immediately. The status endpoint supports later asynchronous rails and operational review. Do not mark the ERP invoice paid based on a browser redirect or an unverified client message. For the current synchronous simulator, mark it paid only after receiving the `SETTLED` response or confirming `SETTLED` with this query.

## Status values

| Status | Meaning |
| --- | --- |
| `PENDING` | Reserved for a later asynchronous rail. The current simulator does not return this state. |
| `PROCESSING` | Reserved for a later asynchronous rail. The current simulator does not return this state. |
| `SETTLED` | The simulated payout completed and the bank posted one withdrawal transaction. |
| `REJECTED` | Reserved for a later rail outcome. Current validation failures return an API error and create no order or withdrawal. |

The current simulator settles successful orders synchronously. `PENDING` and `PROCESSING` are included to keep the resource contract usable when asynchronous processing is added. Status must never move from `SETTLED` back to a non-settled state. A rejected order is not retried in place; submit a new payment reference after correcting the cause.

## Initial simulated rail

The `SIMULATED` rail records a local payment outcome and does not contact a real bank or payment provider. When bank validation passes, it posts a settled withdrawal and returns the transaction reference. Validation failures return an API error and leave the account unchanged. A settled response means only that the simulator posted the debit and transaction record. It is not proof that an external beneficiary received funds.

The current contract has no provider callback or ERP webhook. ChasteBusiness OS obtains status using the returned order ID. If asynchronous callbacks are added, they must use a durable outbox, event IDs for deduplication, signed delivery, bounded retries, and a query endpoint for recovery. Never credit or debit accounts solely because the ERP says an order succeeded.

## Errors

Errors use the API's standard response shape:

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "The request conflicts with an existing record",
  "path": "/api/v1/erp/payment-orders",
  "timestamp": "2026-10-15T10:15:30",
  "validationErrors": null
}
```

| HTTP status | Typical cause | ERP action |
| --- | --- | --- |
| `400 Bad Request` | Invalid amount, unsupported amount precision, invalid currency, inactive account, or insufficient funds. | Correct the request or account state before submitting a new payment. |
| `401 Unauthorized` | Missing, expired, or invalid JWT. | Obtain a valid token, then retry the same request with the same idempotency key. |
| `403 Forbidden` | Caller does not own or have access to the source account. | Correct the account-to-integration authorization. Do not retry unchanged. |
| `404 Not Found` | Source account or payment order does not exist. | Verify the account or bank order ID. |
| `409 Conflict` | ERP reference already exists with different request data, or another uniqueness constraint conflicts. | Query the existing order and reconcile against the invoice before deciding what to do. |
| `500 Internal Server Error` | Unexpected bank-side failure. | Query by order ID or repeat the same create request with the same key. Never create a new ERP reference solely due to an unknown outcome. |

Validation failures include field-level errors. Error messages must not reveal credentials, another user's payment data, or internal provider secrets.

## Ownership and reconciliation

ChasteBusiness OS remains authoritative for invoice content, approval, vendor master data, and invoice status. The bank API is authoritative for payment-order status, the account debit, and the bank transaction reference. The ERP should compare the order's invoice reference, amount, and currency with its approved invoice before submission, then store the bank order ID and returned transaction reference.

Reconciliation for the initial simulator consists of querying orders by returned bank ID and comparing ERP payment references, invoice references, amount, currency, final status, and transaction reference. The ERP must not silently submit a second payment if its request timed out. Provider settlement reports, automated callbacks, and automated bank-to-ERP reconciliation are later capabilities.

## Example

```bash
curl -X POST http://localhost:8080/api/v1/erp/payment-orders \
  -H "Authorization: Bearer $CHASTEBUSINESS_OS_JWT" \
  -H "Idempotency-Key: chaste-ap-2026-00481" \
  -H "Content-Type: application/json" \
  -d '{"sourceAccountId":42,"erpPaymentReference":"PAY-2026-00481","invoiceReference":"AP-2026-00481","beneficiaryName":"Northwind Supplies","beneficiaryAccount":"SIM-001-9284","amount":1250.00,"currency":"USD"}'
```

Persist the returned `id`, query it with `GET /api/v1/erp/payment-orders/{id}` when needed, and update the ERP invoice only after confirming `SETTLED`.
