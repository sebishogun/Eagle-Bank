# API Search Capabilities

## Overview

The Eagle Bank API provides comprehensive search functionality for transactions and accounts using the Specification Pattern with Spring Data JPA. This enables complex, composable queries with clean, maintainable code.

## Transaction Search

### 1. Query Parameter Based Search

**Endpoint**: `GET /v1/accounts/{accountId}/transactions`

**Query Parameters**:
- `startDate` - Filter by start date (inclusive)
- `endDate` - Filter by end date (inclusive)
- `minAmount` - Minimum transaction amount
- `maxAmount` - Maximum transaction amount
- `type` - Transaction type (DEPOSIT, WITHDRAWAL, TRANSFER)
- `description` - Search by description keyword
- `page` - Page number (0-based)
- `size` - Page size (default: 20)
- `sort` - Sort field and direction (e.g., `createdAt,desc`)

**Example Request**:
```bash
curl -X GET "http://localhost:8080/api/v1/accounts/{accountId}/transactions?\
startDate=2024-01-01T00:00:00&\
endDate=2024-12-31T23:59:59&\
minAmount=100&\
maxAmount=1000&\
type=DEPOSIT&\
description=salary&\
page=0&\
size=20&\
sort=createdAt,desc" \
-H "Authorization: Bearer {token}"
```

**Response**:
```json
{
  "content": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "transactionReference": "TXN20240815143022001",
      "transactionType": "DEPOSIT",
      "amount": 500.00,
      "balanceBefore": 1000.00,
      "balanceAfter": 1500.00,
      "description": "Monthly salary",
      "status": "COMPLETED",
      "createdAt": "2024-08-15T14:30:22"
    }
  ],
  "totalElements": 25,
  "totalPages": 2,
  "number": 0,
  "size": 20
}
```

### 2. Request Body Based Advanced Search

**Endpoint**: `POST /v1/accounts/{accountId}/transactions/search`

**Request Body** (`TransactionSearchRequest`):
```json
{
  "startDate": "2024-01-01T00:00:00",
  "endDate": "2024-12-31T23:59:59",
  "minAmount": 100.00,
  "maxAmount": 5000.00,
  "transactionType": "DEPOSIT",
  "descriptionKeyword": "salary",
  "referenceNumber": "TXN2024",
  "completedOnly": true,
  "largeTransactionThreshold": 1000.00
}
```

**Additional Fields**:
- `referenceNumber` - Exact or partial transaction reference
- `completedOnly` - Filter only completed transactions
- `largeTransactionThreshold` - Find transactions above threshold

**Example Request**:
```bash
curl -X POST "http://localhost:8080/api/v1/accounts/{accountId}/transactions/search" \
-H "Authorization: Bearer {token}" \
-H "Content-Type: application/json" \
-d '{
  "startDate": "2024-01-01T00:00:00",
  "endDate": "2024-12-31T23:59:59",
  "largeTransactionThreshold": 5000.00,
  "completedOnly": true
}'
```

## Account Search

### 1. User Account Search

**Endpoint**: `POST /v1/accounts/search`

**Request Body** (`AccountSearchRequest`):
```json
{
  "minTransactionAmount": 1000.00,
  "transactionType": "DEPOSIT",
  "since": "2024-01-01T00:00:00"
}
```

**Use Cases**:
- Find accounts with specific transaction patterns
- Identify accounts with recent activity
- Search by transaction criteria

### 2. Admin Account Search

#### High-Value Accounts
**Endpoint**: `GET /admin/accounts/high-value`

**Query Parameters**:
- `threshold` - Minimum transaction amount

**Example**:
```bash
curl -X GET "http://localhost:8080/api/admin/accounts/high-value?threshold=10000" \
-H "Authorization: Bearer {admin_token}"
```

**Response**:
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440002",
    "accountNumber": "ACC1234567890",
    "accountName": "Premium Business Account",
    "accountType": "CHECKING",
    "status": "ACTIVE",
    "balance": 50000.00,
    "userId": "550e8400-e29b-41d4-a716-446655440003"
  }
]
```

#### Dormant Accounts
**Endpoint**: `GET /admin/accounts/dormant`

**Query Parameters**:
- `inactiveSince` - Date to check for last activity

**Example**:
```bash
curl -X GET "http://localhost:8080/api/admin/accounts/dormant?inactiveSince=2023-01-01T00:00:00" \
-H "Authorization: Bearer {admin_token}"
```

## Specification Pattern Implementation

### How It Works

The search functionality is powered by Spring Data JPA Specifications:

```java
public class TransactionSpecifications {
    
    public static Specification<Transaction> forAccount(UUID accountId) {
        return (root, query, cb) -> 
            cb.equal(root.get("account").get("id"), accountId);
    }
    
    public static Specification<Transaction> transactedBetween(
            LocalDateTime start, LocalDateTime end) {
        return (root, query, cb) -> 
            cb.between(root.get("transactionDate"), start, end);
    }
    
    public static Specification<Transaction> amountGreaterThan(BigDecimal amount) {
        return (root, query, cb) -> 
            cb.greaterThan(root.get("amount"), amount);
    }
    
    public static Specification<Transaction> ofType(TransactionType type) {
        return (root, query, cb) -> 
            cb.equal(root.get("type"), type);
    }
}
```

### Composable Queries

Specifications can be combined using `and()`, `or()`, and `not()`:

```java
Specification<Transaction> spec = TransactionSpecifications
    .forAccount(accountId)
    .and(TransactionSpecifications.transactedBetween(startDate, endDate))
    .and(TransactionSpecifications.amountGreaterThan(minAmount))
    .and(TransactionSpecifications.ofType(TransactionType.DEPOSIT));

Page<Transaction> results = transactionRepository.findAll(spec, pageable);
```

## Search Features

### 1. Pagination
All search endpoints support pagination:
- `page` - Page number (0-based)
- `size` - Items per page
- `sort` - Sort field and direction

### 2. Sorting
Multiple sort parameters supported:
```
?sort=amount,desc&sort=createdAt,asc
```

### 3. Date Range Filtering
- Inclusive date ranges
- Support for open-ended ranges (only start or end date)

### 4. Amount Range Filtering
- Minimum and maximum amount boundaries
- Support for open-ended ranges

### 5. Text Search
- Case-insensitive description search
- Partial matching supported

### 6. Type Filtering
- Filter by transaction or account type
- Multiple types can be combined with custom logic

## Performance Optimization

### 1. Indexed Fields
Key search fields are indexed for performance:
```sql
CREATE INDEX idx_transaction_account_date ON transactions(account_id, transaction_date);
CREATE INDEX idx_transaction_amount ON transactions(amount);
CREATE INDEX idx_transaction_type ON transactions(type);
CREATE INDEX idx_account_user_status ON accounts(user_id, status);
```

### 2. Query Optimization
- Specifications generate optimized SQL queries
- Lazy loading prevented with proper fetch strategies
- Result limiting with pagination

### 3. Caching
Frequently searched data is cached:
- Recent transactions cached for quick access
- Account summaries cached with TTL

## Use Cases

### 1. Monthly Statement Generation
```bash
# Get all transactions for a month
GET /v1/accounts/{id}/transactions?startDate=2024-08-01&endDate=2024-08-31
```

### 2. Large Transaction Monitoring
```bash
# Find transactions above $10,000
POST /v1/accounts/{id}/transactions/search
{
  "largeTransactionThreshold": 10000.00
}
```

### 3. Expense Tracking
```bash
# Find all withdrawals in a date range
GET /v1/accounts/{id}/transactions?type=WITHDRAWAL&startDate=2024-01-01
```

### 4. Income Analysis
```bash
# Find all deposits with "salary" in description
GET /v1/accounts/{id}/transactions?type=DEPOSIT&description=salary
```

### 5. Fraud Detection
```bash
# Admin: Find high-value transactions across all accounts
GET /admin/accounts/high-value?threshold=50000
```

### 6. Account Maintenance
```bash
# Admin: Find dormant accounts for review
GET /admin/accounts/dormant?inactiveSince=2023-01-01
```

## Error Handling

### Common Error Responses

#### 400 Bad Request
Invalid search parameters:
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "End date must be after start date"
}
```

#### 403 Forbidden
Accessing another user's data:
```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "User is not authorized to access this account"
}
```

#### 404 Not Found
Account doesn't exist:
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Account not found with id: 550e8400-e29b-41d4-a716-446655440001"
}
```

## Best Practices

1. **Use Appropriate Endpoint**: 
   - Simple searches: Use GET with query parameters
   - Complex searches: Use POST with request body

2. **Optimize Date Ranges**: 
   - Keep date ranges reasonable to avoid large result sets
   - Use pagination for large date ranges

3. **Combine Filters**: 
   - Multiple filters improve query precision
   - Reduces result set size and improves performance

4. **Use Pagination**: 
   - Always paginate results
   - Default page size is 20, maximum is 100

5. **Cache Frequent Searches**: 
   - Common searches are cached automatically
   - Consider client-side caching for static data

## Examples in Postman Collection

The Postman collection includes pre-configured search examples:

1. **Transaction Search Examples**
   - Search by date range
   - Search by amount range
   - Search by type
   - Combined filters search

2. **Account Search Examples**
   - High-value accounts (Admin)
   - Dormant accounts (Admin)
   - Accounts with recent activity

3. **Advanced Search Examples**
   - Large transaction detection
   - Monthly statement generation
   - Fraud pattern detection

## Conclusion

The search functionality in Eagle Bank API provides:
- **Flexibility**: Multiple search methods and parameters
- **Performance**: Optimized queries with indexing
- **Usability**: Clean API with intuitive parameters
- **Extensibility**: Easy to add new search criteria
- **Security**: Proper authorization and data isolation