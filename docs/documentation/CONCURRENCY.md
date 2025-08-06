# Concurrency Control Architecture

## Overview

The Eagle Bank API implements comprehensive concurrency control to ensure data consistency and prevent race conditions in financial operations. This is critical for maintaining accurate account balances and transaction integrity in a multi-user environment.

## Architecture Layers

### 1. Entity Layer - Optimistic Locking
All entities extend `BaseEntity` which includes a version field:

```java
@MappedSuperclass
public abstract class BaseEntity {
    @Version
    @Column(nullable = false)
    private Long version = 0L;
    // ... other fields
}
```

This provides optimistic locking for all database entities automatically.

### 2. Repository Layer - Pessimistic Locking
Critical repositories provide pessimistic locking methods:

```java
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(@Param("id") UUID id);
    
    @Modifying
    @Query("UPDATE Account SET balance = balance + :amount WHERE id = :id")
    int updateBalanceAtomic(@Param("id") UUID id, @Param("amount") BigDecimal amount);
}
```

### 3. Service Layer - Business Logic Protection
Services use appropriate locking based on operation criticality:

```java
@Service
public class TransactionService {
    @Transactional
    public TransactionResponse createTransaction(UUID userId, UUID accountId, 
                                                CreateTransactionRequest request) {
        // Use pessimistic lock for financial operations
        Account account = accountRepository.findByIdWithLock(accountId)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        
        // Process transaction with locked account
        // ...
    }
}
```

## Locking Strategies

### When to Use Optimistic Locking
- **Use Cases**: User profile updates, account name changes, non-critical updates
- **Benefits**: Better performance, no blocking
- **Handling**: Retry on `OptimisticLockException`

### When to Use Pessimistic Locking
- **Use Cases**: Financial transactions, balance updates, status changes
- **Benefits**: Guaranteed consistency, prevents dirty reads
- **Handling**: Timeout and retry on `PessimisticLockException`

## Deadlock Prevention

### The Problem
When transferring money between accounts, locking both accounts can cause deadlocks:
- Thread 1: Locks Account A, then tries to lock Account B
- Thread 2: Locks Account B, then tries to lock Account A
- Result: Deadlock!

### The Solution - Ordered Lock Acquisition
Always acquire locks in a consistent order (by UUID comparison):

```java
@Transactional
public TransferResponse createTransfer(UUID userId, CreateTransferRequest request) {
    Account sourceAccount;
    Account targetAccount;
    
    // Always lock accounts in UUID order to prevent deadlocks
    if (request.getSourceAccountId().compareTo(request.getTargetAccountId()) < 0) {
        sourceAccount = accountRepository.findByIdWithLock(request.getSourceAccountId())
            .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));
        targetAccount = accountRepository.findByIdWithLock(request.getTargetAccountId())
            .orElseThrow(() -> new ResourceNotFoundException("Target account not found"));
    } else {
        // Lock in reverse order
        targetAccount = accountRepository.findByIdWithLock(request.getTargetAccountId())
            .orElseThrow(() -> new ResourceNotFoundException("Target account not found"));
        sourceAccount = accountRepository.findByIdWithLock(request.getSourceAccountId())
            .orElseThrow(() -> new ResourceNotFoundException("Source account not found"));
    }
    
    // Process transfer with both accounts locked
    // ...
}
```

## Exception Handling

### Optimistic Lock Exceptions
```java
@ExceptionHandler({OptimisticLockException.class, 
                   ObjectOptimisticLockingFailureException.class})
public ResponseEntity<ErrorResponse> handleOptimisticLockException(Exception ex) {
    ErrorResponse errorResponse = ErrorResponse.builder()
        .status(HttpStatus.CONFLICT.value())  // 409
        .error("Conflict")
        .message("The resource was modified by another user. Please refresh and try again.")
        .build();
    return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
}
```

### Pessimistic Lock Exceptions
```java
@ExceptionHandler({PessimisticLockException.class, 
                   CannotAcquireLockException.class})
public ResponseEntity<ErrorResponse> handlePessimisticLockException(Exception ex) {
    ErrorResponse errorResponse = ErrorResponse.builder()
        .status(HttpStatus.LOCKED.value())  // 423
        .error("Locked")
        .message("The resource is currently being modified. Please try again.")
        .build();
    return new ResponseEntity<>(errorResponse, HttpStatus.LOCKED);
}
```

## Testing Concurrency

### Test Coverage
The `ConcurrentTransactionTest` class validates:
1. **Concurrent Withdrawals** - Prevents overdrafts
2. **Concurrent Deposits** - Maintains consistency
3. **Concurrent Transfers** - No deadlocks
4. **Mixed Operations** - Overall integrity
5. **Optimistic Locking** - Version conflicts detected
6. **Pessimistic Locking** - Serialized access
7. **Multi-Account Transfers** - Complex deadlock scenarios

### Example Test
```java
@Test
void testConcurrentWithdrawals_ShouldPreventOverdraft() throws InterruptedException {
    BigDecimal withdrawalAmount = new BigDecimal("100.00");
    int numberOfThreads = 20;
    ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
    
    // Start all threads simultaneously
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch endLatch = new CountDownLatch(numberOfThreads);
    
    for (int i = 0; i < numberOfThreads; i++) {
        executor.submit(() -> {
            startLatch.await();
            // Attempt withdrawal
            transactionService.createTransaction(userId, accountId, withdrawalRequest);
            endLatch.countDown();
        });
    }
    
    startLatch.countDown(); // Start race condition
    endLatch.await(30, TimeUnit.SECONDS);
    
    // Verify account never went negative
    Account finalAccount = accountRepository.findById(accountId).orElseThrow();
    assertThat(finalAccount.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
}
```

## Performance Considerations

### Lock Timeout Configuration
```yaml
spring:
  jpa:
    properties:
      javax:
        persistence:
          lock:
            timeout: 10000  # 10 seconds
```

### Connection Pool Settings
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

## Best Practices

1. **Keep Lock Duration Short**: Release locks as quickly as possible
2. **Use Appropriate Lock Type**: Don't use pessimistic locks for read operations
3. **Handle Lock Exceptions**: Implement retry logic with exponential backoff
4. **Monitor Lock Wait Times**: Track metrics for lock acquisition times
5. **Test Concurrent Scenarios**: Always test with multiple threads
6. **Document Lock Strategy**: Make locking decisions explicit in code

## Monitoring

### Key Metrics to Track
- Lock acquisition time
- Lock timeout frequency
- Optimistic lock failure rate
- Deadlock occurrences
- Transaction rollback rate

### Example Monitoring Query
```sql
-- PostgreSQL: Check for lock waits
SELECT 
    pg_stat_activity.pid,
    pg_stat_activity.query,
    pg_stat_activity.wait_event_type,
    pg_stat_activity.wait_event,
    now() - pg_stat_activity.query_start AS duration
FROM pg_stat_activity
WHERE wait_event_type = 'Lock'
ORDER BY duration DESC;
```

## Troubleshooting

### Common Issues and Solutions

1. **High Optimistic Lock Failures**
   - Consider switching to pessimistic locking for hot resources
   - Implement retry logic with exponential backoff

2. **Lock Timeouts**
   - Increase timeout configuration
   - Optimize transaction duration
   - Check for long-running transactions

3. **Deadlocks**
   - Ensure consistent lock ordering
   - Reduce transaction scope
   - Use database deadlock detection

4. **Performance Degradation**
   - Monitor lock wait times
   - Consider read replicas for read-heavy operations
   - Implement caching for frequently accessed data

## Conclusion

The concurrency control implementation in Eagle Bank API ensures:
- **Data Integrity**: No lost updates or dirty reads
- **Consistency**: Account balances always accurate
- **Performance**: Appropriate locking strategies for different operations
- **Reliability**: Comprehensive testing and error handling
- **Scalability**: Ready for high-concurrency environments