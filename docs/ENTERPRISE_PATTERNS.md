# Enterprise Patterns Implementation

This document describes the comprehensive enterprise patterns architecture implemented in the Eagle Bank API.

## Design Patterns Implemented

### 1. Strategy Pattern
**Location**: `com.eaglebank.pattern.strategy`
- **Purpose**: Encapsulate transaction processing algorithms
- **Components**:
  - `TransactionStrategy` interface
  - `DepositStrategy` and `WithdrawalStrategy` implementations
  - `TransactionStrategyFactory` for strategy selection
- **Usage**: TransactionService uses strategies for processing different transaction types

### 2. Factory Pattern
**Location**: `com.eaglebank.pattern.factory`
- **Purpose**: Standardize account creation with type-specific business rules
- **Components**:
  - `AccountFactory` interface
  - `SavingsAccountFactory` and `CheckingAccountFactory` implementations
  - `AccountFactoryProvider` for factory management
- **Usage**: AccountService uses factories to create accounts with proper validation

### 3. Specification Pattern
**Location**: `com.eaglebank.pattern.specification`
- **Purpose**: Enable flexible, composable queries for entities
- **Components**:
  - `Specification<T>` interface with and/or/not operations
  - `AccountSpecifications` and `TransactionSpecifications` static factories
  - Repository implementations with SpecificationExecutor
- **Usage**: Enhanced repositories support complex queries

### 4. Observer Pattern
**Location**: `com.eaglebank.pattern.observer` and `com.eaglebank.event`
- **Purpose**: Implement event-driven architecture
- **Components**:
  - Domain events: `AccountCreatedEvent`, `TransactionCompletedEvent`, `UserLoggedInEvent`
  - `EventPublisher` for Spring application events
  - Event listeners for async processing
- **Usage**: Services publish events that listeners process asynchronously

### 5. Chain of Responsibility Pattern
**Location**: `com.eaglebank.pattern.chain`
- **Purpose**: Build flexible validation pipelines
- **Components**:
  - `ValidationHandler<T>` abstract base class
  - Concrete handlers: `AmountValidationHandler`, `TransactionTypeValidationHandler`, `DescriptionValidationHandler`
  - `TransactionValidationChain` to build the chain
- **Usage**: Transaction validation through chained handlers

### 6. Decorator Pattern
**Location**: `com.eaglebank.pattern.decorator`
- **Purpose**: Add cross-cutting concerns to transaction processing
- **Components**:
  - `TransactionProcessor` interface
  - `BaseTransactionProcessor` implementation
  - Decorators: `LoggingTransactionDecorator`, `ValidationTransactionDecorator`, `NotificationTransactionDecorator`, `MetricsTransactionDecorator`
- **Usage**: Layer behaviors on transaction processing

### 7. Command Pattern
**Location**: `com.eaglebank.pattern.command`
- **Purpose**: Encapsulate operations with undo capability
- **Components**:
  - `Command<T>` interface with execute/undo/canUndo methods
  - `CreateAccountCommand` and `UpdateAccountCommand` implementations
  - `CommandInvoker` for command management with history
- **Usage**: Support for undoable operations

## Enterprise Features

### 1. Caching
**Configuration**: `com.eaglebank.config.CacheConfig`
- Spring Cache abstraction with in-memory caching
- Caches: users, accounts, transactions, user accounts, account transactions
- Cache eviction on updates

### 2. Audit Trail
**Location**: `com.eaglebank.audit`
- **Components**:
  - `AuditEntry` entity for audit log storage
  - `AuditService` for async audit logging
  - `@Auditable` annotation for method-level auditing
  - `AuditAspect` for AOP-based auditing
- **Features**:
  - Tracks all CRUD operations
  - Records user actions, IP addresses, timestamps
  - Captures success and failure scenarios

### 3. Async Processing
**Configuration**: `com.eaglebank.config.AsyncConfig`
- Enables async event processing
- Used for audit logging and event handling

### 4. Enhanced Repositories
- All repositories extend `SpecificationExecutor<T>`
- Support for complex queries:
  - Balance range queries
  - Date range queries
  - Type-based filtering
  - Aggregate functions

## Integration Points

### Service Layer Integration
- **TransactionService**: Uses Strategy pattern, publishes events, supports caching
- **AccountService**: Uses Factory pattern, publishes events, supports caching
- **AuthService**: Publishes login events, integrates with audit service

### Cross-Cutting Concerns
- **AOP**: Audit logging via aspects
- **Events**: Async event processing
- **Caching**: Transparent caching layer
- **Validation**: Chain of Responsibility for validation

## Benefits

1. **Extensibility**: Easy to add new account types, transaction types, validation rules
2. **Maintainability**: Clear separation of concerns, single responsibility
3. **Testability**: Each pattern component is independently testable
4. **Performance**: Caching and async processing for better response times
5. **Auditability**: Complete audit trail of all operations
6. **Flexibility**: Composable specifications, decorators, and chains

## Usage Examples

### Creating an Account with Factory
```java
AccountFactory factory = factoryProvider.getFactory("SAVINGS");
Account account = factory.createAccount(user, initialBalance);
```

### Building Complex Queries with Specifications
```java
Specification<Account> spec = AccountSpecifications
    .belongsToUser(userId)
    .and(AccountSpecifications.balanceGreaterThan(new BigDecimal("1000")))
    .and(AccountSpecifications.activeAccounts());
    
List<Account> accounts = accountRepository.findAll(spec);
```

### Transaction Processing with Strategy
```java
TransactionStrategy strategy = strategyFactory.getStrategy(TransactionType.DEPOSIT);
strategy.validateTransaction(account, amount);
BigDecimal newBalance = strategy.calculateNewBalance(account, amount);
```

### Command Pattern with Undo
```java
CreateAccountCommand command = new CreateAccountCommand(accountService, userId, request);
AccountResponse account = commandInvoker.execute(command);
// Later...
commandInvoker.undo(); // Reverses the account creation
```

## Testing

All patterns have corresponding test classes demonstrating their usage and validating their behavior.