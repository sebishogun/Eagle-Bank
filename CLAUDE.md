# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a REST API for Eagle Bank - a fictional banking system built with Java Spring Boot. The API allows users to:
- Manage user accounts (create, read, update, delete)
- Manage bank accounts (create, read, update, delete)
- Perform transactions (deposits, withdrawals)
- View transaction history

## Technology Stack

- **Java 21** with **Spring Boot 3.5.4**
- **Maven** for build management
- **PostgreSQL 17.5** for production database
- **H2 Database** for unit testing only
- **Spring Security** with JWT for authentication
- **Spring Data JPA** for data persistence
- **UUID v7** for entity IDs (time-ordered, better indexing)
- **UUID v4** for random tokens (complete randomness)
- **Testcontainers** for integration testing with PostgreSQL
- **JUnit 5** and **MockMvc** for testing

## API Requirements

### Base URL
All endpoints should be prefixed with `/v1`

### Authentication
- Implement JWT-based authentication using Spring Security
- All endpoints except user creation require authentication
- JWT should be passed as Bearer token in Authorization header
- Add authentication endpoint(s) to OpenAPI spec

### Core Endpoints

#### Users
- `POST /v1/users` - Create user (no auth required)
- `GET /v1/users/{userId}` - Get user details
- `PATCH /v1/users/{userId}` - Update user
- `DELETE /v1/users/{userId}` - Delete user

#### Authentication
- `POST /v1/auth/login` - Authenticate and receive JWT token

#### Accounts
- `POST /v1/accounts` - Create bank account
- `GET /v1/accounts` - List user's accounts
- `GET /v1/accounts/{accountId}` - Get account details
- `PATCH /v1/accounts/{accountId}` - Update account
- `DELETE /v1/accounts/{accountId}` - Delete account

#### Transactions
- `POST /v1/accounts/{accountId}/transactions` - Create transaction (deposit/withdrawal)
- `GET /v1/accounts/{accountId}/transactions` - List transactions
- `GET /v1/accounts/{accountId}/transactions/{transactionId}` - Get transaction details

## Key Business Rules

### User Management
- Users can only access their own data
- Cannot delete user if they have bank accounts
- All operations on other users' data return 403 Forbidden

### Account Management
- Users can only manage their own accounts
- Account balance must be updated when transactions occur
- Cannot perform operations on non-existent accounts (404)

### Transactions
- Two types: `deposit` and `withdrawal`
- Withdrawals require sufficient funds (422 if insufficient)
- Transactions are immutable (no update/delete)
- Balance must reflect all transactions

## Error Handling

Standard HTTP status codes:
- `400` - Bad Request (missing/invalid data)
- `401` - Unauthorized (missing/invalid token)
- `403` - Forbidden (accessing other users' data)
- `404` - Not Found (resource doesn't exist)
- `409` - Conflict (e.g., deleting user with accounts)
- `422` - Unprocessable Entity (e.g., insufficient funds)

## Maven Commands

```bash
mvn clean package
mvn spring-boot:run
mvn test
mvn test -Dtest=UserControllerTest
mvn clean package -DskipTests
mvn clean test jacoco:report
```

## Project Structure

```
src/
├── main/
│   ├── java/com/eaglebank/
│   │   ├── EagleBankApplication.java          # Main application class
│   │   ├── config/
│   │   │   ├── SecurityConfig.java           # Spring Security configuration
│   │   │   └── JwtConfig.java                # JWT configuration
│   │   ├── controller/
│   │   │   ├── UserController.java           # User endpoints
│   │   │   ├── AccountController.java        # Account endpoints
│   │   │   ├── TransactionController.java    # Transaction endpoints
│   │   │   └── AuthController.java           # Authentication endpoints
│   │   ├── service/
│   │   │   ├── UserService.java
│   │   │   ├── AccountService.java
│   │   │   ├── TransactionService.java
│   │   │   └── AuthService.java
│   │   ├── repository/
│   │   │   ├── UserRepository.java
│   │   │   ├── AccountRepository.java
│   │   │   └── TransactionRepository.java
│   │   ├── entity/                           # JPA entities
│   │   │   ├── User.java
│   │   │   ├── Account.java
│   │   │   └── Transaction.java
│   │   ├── dto/                              # Request/Response DTOs
│   │   │   ├── request/
│   │   │   └── response/
│   │   ├── security/
│   │   │   ├── JwtTokenProvider.java         # JWT token generation/validation
│   │   │   ├── JwtAuthenticationFilter.java  # JWT filter
│   │   │   └── UserPrincipal.java           # Security principal
│   │   └── exception/
│   │       ├── GlobalExceptionHandler.java   # @ControllerAdvice
│   │       ├── ResourceNotFoundException.java
│   │       ├── InsufficientFundsException.java
│   │       └── UnauthorizedException.java
│   └── resources/
│       ├── application.properties            # Spring Boot configuration
│       └── openapi.yaml                      # OpenAPI specification
└── test/
    └── java/com/eaglebank/
        ├── controller/                       # Controller tests
        ├── service/                          # Service tests
        └── integration/                      # Integration tests

pom.xml                                       # Maven configuration
```

## Key Dependencies (pom.xml)

```xml
<!-- Spring Boot Starters -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- PostgreSQL Driver -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- H2 Database (for unit tests only) -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>

<!-- UUID Creator for UUID v7 -->
<dependency>
    <groupId>com.github.f4b6a3</groupId>
    <artifactId>uuid-creator</artifactId>
    <version>6.0.0</version>
</dependency>

<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
</dependency>

<!-- Testing -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- Testcontainers for PostgreSQL -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

## Testing Requirements

Implement tests for all scenarios described in the requirements:
- Unit tests for services using Mockito
- Integration tests for controllers using MockMvc
- Test authentication and authorization flows
- Test all error scenarios
- Use @SpringBootTest for full integration tests
- Use @WebMvcTest for controller layer tests
- Use @DataJpaTest for repository layer tests

### Important Testing Notes (Spring Boot 3.4+)
- Use `@MockitoBean` instead of deprecated `@MockBean` (from `org.springframework.test.context.bean.override.mockito`)
- For pure unit tests, continue using Mockito's `@Mock` and `@InjectMocks`
- Controller tests should use `@WebMvcTest` with `@MockitoBean` for service mocking

## Application Properties

### application.yml (Common Configuration)
```yaml
spring:
  application:
    name: eagle-bank-api
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
    show-sql: false

server:
  port: 8080
  servlet:
    context-path: /api

jwt:
  secret: ${JWT_SECRET:your-secret-key-change-in-production}
  expiration: 86400000

logging:
  level:
    com.eaglebank: DEBUG
```

### application-dev.yml (Development with PostgreSQL)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/eaglebank
    username: eaglebank
    password: eaglebank123
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
```

### application-test.yml (Testing with H2)
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
  h2:
    console:
      enabled: false
  jpa:
    hibernate:
      ddl-auto: create-drop
```

## Docker Compose for PostgreSQL

```yaml
version: '3.8'
services:
  postgres:
    image: postgres:17.5-alpine
    container_name: eaglebank-postgres
    environment:
      POSTGRES_DB: eaglebank
      POSTGRES_USER: eaglebank
      POSTGRES_PASSWORD: eaglebank123
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

## Submission Requirements

1. Push code to public GitHub repository
2. Include updated OpenAPI specification with authentication endpoints
3. Ensure at minimum these operations work:
   - Create and Fetch for User
   - Create and Fetch for Account
   - Create and Fetch for Transaction
4. Be prepared to walk through code and explain design decisions
5. Complete within 7 days or communicate with hiring team

## Important Implementation Notes

- Use @Transactional for operations that modify multiple entities
- Implement proper validation using Bean Validation annotations
- Use ResponseEntity for consistent response structure
- Consider using @ControllerAdvice for global exception handling
- Use DTOs to avoid exposing entities directly
- Implement audit fields (createdAt, updatedAt) on entities
- Use BigDecimal for monetary values to avoid floating-point issues
- Document endpoints using OpenAPI/Swagger annotations

## UUID Implementation

### UUID v7 for Entity IDs
- Time-ordered UUIDs for better database indexing performance
- Use for User, Account, and Transaction entity IDs
- Generated using uuid-creator library

### UUID v4 for Random Tokens
- Complete randomness for security tokens
- Use for session tokens, reset tokens, etc.

### Example UUID Generator Utility
```java
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;

public class UuidGenerator {
    // UUID v7 - time-ordered
    public static UUID generateUuidV7() {
        return UuidCreator.getTimeOrderedEpoch();
    }
    
    // UUID v4 - random
    public static UUID generateUuidV4() {
        return UUID.randomUUID();
    }
}
```

## Test-Driven Development (TDD) Approach

Follow the Red-Green-Refactor cycle:
1. **Red**: Write a failing test first
2. **Green**: Write minimal code to make the test pass
3. **Refactor**: Improve the code while keeping tests green

Each feature should be developed using TDD:
- Write unit tests before implementation
- Write integration tests for API endpoints
- Use Testcontainers for database integration tests

## Development Progress

### Completed Tasks
1. Initialize Spring Boot project with Java 21 and Maven
2. Update pom.xml with PostgreSQL, UUID libraries, and Testcontainers
3. Create Docker Compose file for PostgreSQL 17
4. Configure application profiles (dev with PostgreSQL, test with H2)
5. Implement UUID v7 generator for entity IDs
6. Implement UUID v4 generator for random tokens
7. Create JPA entity classes (User, Account, Transaction) with UUID
8. Create repository interfaces for all entities
9. Write tests for UserService create and fetch operations
10. Implement UserService to pass tests
11. Write tests for UserController POST and GET endpoints
12. Implement UserController to pass tests
13. Write tests for JWT authentication and security
14. Implement JWT security configuration and token provider
15. Write tests for AuthService and AuthController
16. Implement AuthService and AuthController for login
17. Create custom exceptions and GlobalExceptionHandler
18. Create DTOs for request/response objects (User and Auth DTOs)
19. Fix deprecated DaoAuthenticationProvider methods (Spring Security 6.3+)
20. Configure Maven compiler plugin for Lombok annotation processing
21. Create Account DTOs (CreateAccountRequest, UpdateAccountRequest, AccountResponse)
22. Write comprehensive tests for AccountService CRUD operations
23. Implement AccountService with business logic and authorization
24. Write tests for AccountController endpoints
25. Implement AccountController with REST endpoints
26. Create Transaction DTOs (CreateTransactionRequest, TransactionResponse)
27. Implement InsufficientFundsException for transaction validation
28. Write tests for TransactionService deposit/withdrawal logic
29. Implement TransactionService with balance validation
30. Write tests for TransactionController endpoints
31. Implement TransactionController
32. Implement transaction reference number generation
33. Implement insufficient funds validation
34. Implement authorization checks for transactions
35. Create UpdateUserRequest DTO for user updates
36. Add countByUserId to AccountRepository for user deletion validation
37. Implement UserService updateUser and deleteUser methods
38. Add PATCH and DELETE endpoints to UserController
39. Write comprehensive integration tests for user update/delete operations
40. Implement authorization checks (users can only modify their own data)
41. Implement business rule: cannot delete user with existing accounts
42. Create meaningful integration tests that validate real behavior
43. Add comprehensive OpenAPI annotations to all controllers
44. Configure OpenAPI security scheme
45. Create Postman collection for API testing
46. Migrate to Prometheus using Micrometer
47. Add Spring Boot Actuator and Micrometer dependencies
48. Create MicrometerConfig for Prometheus configuration
49. Update all MetricsCollectors to use Micrometer
50. Add actuator configuration to application.yml
51. Update metrics tests for Micrometer compatibility
52. Add Prometheus and Grafana to Docker Compose
53. Create Prometheus scraping configuration
54. Create Grafana datasource and dashboard provisioning
55. Create comprehensive Eagle Bank monitoring dashboard

## Prometheus and Grafana Monitoring Plan

### Overview
Add comprehensive monitoring to Eagle Bank API using Prometheus for metrics collection and Grafana for visualization. This will provide real-time insights into application performance, business metrics, and system health.

### Implementation Tasks

#### 1. Update Docker Compose Configuration
- Add Prometheus service (port 9090) to scrape metrics from Eagle Bank API
- Add Grafana service (port 3000) with automatic provisioning
- Configure networking between services
- Add persistent volumes for data retention

#### 2. Create Prometheus Configuration
**File**: `monitoring/prometheus/prometheus.yml`
- Configure scrape interval (15 seconds)
- Set up job for Eagle Bank API targeting `/api/actuator/prometheus`
- Configure service discovery for dynamic container environments

#### 3. Create Grafana Configuration
**Datasource**: `monitoring/grafana/provisioning/datasources/prometheus.yml`
- Auto-configure Prometheus as default datasource
- Set up proper URL and access mode

**Dashboard Provisioning**: `monitoring/grafana/provisioning/dashboards/dashboard.yml`
- Configure dashboard auto-provisioning
- Set folder structure and permissions

**Custom Dashboard**: `monitoring/grafana/dashboards/eagle-bank-dashboard.json`
- Transaction Metrics Panel:
  - Transaction count by type (deposit/withdrawal)
  - Transaction volume in currency
  - Processing time percentiles (p50, p95, p99)
  - Transaction rate per minute
- Account Metrics Panel:
  - Active accounts by type
  - Total balance across all accounts
  - Account creation/closure rates
  - Average account balance
- Authentication Metrics Panel:
  - Login success/failure rates
  - Active sessions gauge
  - Failed login attempts by user
  - Login attempts by time window
- JVM/System Metrics Panel:
  - Memory usage (heap/non-heap)
  - CPU utilization
  - Thread count
  - HTTP request rates and latencies

#### 4. Update Documentation
**README.md additions**:
- Monitoring section with access URLs
- Grafana default credentials (admin/admin)
- Prometheus query examples
- Architecture diagram showing metrics flow

**CLAUDE.md additions**:
- Micrometer integration details
- Available metric names and tags
- Custom metrics implementation guide
- Dashboard customization instructions

### Metrics Architecture

```
┌─────────────────┐     ┌──────────────┐     ┌─────────────┐
│  Eagle Bank API │────▶│  Prometheus  │────▶│   Grafana   │
│  (Micrometer)   │     │  (Scraping)  │     │ (Dashboards)│
└─────────────────┘     └──────────────┘     └─────────────┘
        │                                              │
        └── /actuator/prometheus ──────────────────────┘
```

### Available Metrics (Micrometer/Prometheus)

#### Business Metrics
- `transactions_count{type="deposit|withdrawal"}` - Transaction counts by type
- `transactions_volume{type="deposit|withdrawal"}` - Transaction volumes
- `transactions_processing_time` - Processing time histogram
- `accounts_created{type="savings|checking"}` - Account creation counter
- `accounts_active{type="savings|checking"}` - Active accounts gauge
- `authentication_logins_successful` - Successful login counter
- `authentication_logins_failed` - Failed login counter
- `authentication_sessions_active` - Active sessions gauge

#### System Metrics (Auto-collected)
- `jvm_memory_used_bytes` - JVM memory usage
- `jvm_threads_live` - Active thread count
- `http_server_requests` - HTTP request metrics
- `process_cpu_usage` - CPU utilization

### Grafana Dashboard Features
- Real-time updates with 15-second refresh
- Time range selector for historical analysis
- Drill-down capabilities for detailed investigation
- Alert rules for critical thresholds
- Mobile-responsive design

### Todo List for Implementation

1. ✅ Add Prometheus and Grafana monitoring plan to CLAUDE.md
2. ✅ Update Docker Compose with Prometheus service
3. ✅ Update Docker Compose with Grafana service
4. ✅ Create Prometheus configuration file
5. ✅ Create Grafana datasource configuration
6. ✅ Create Grafana dashboard provisioning config
7. ✅ Create Eagle Bank Grafana dashboard JSON
8. ✅ Update README.md with monitoring section
9. ✅ Update CLAUDE.md with metrics documentation

## Account Status Management

### Overview
Implemented a comprehensive account status management system using Strategy and Factory patterns. This provides extensible business logic for different account statuses (ACTIVE, FROZEN, CLOSED) with proper validation and state transitions.

### Design Patterns Used

#### 1. Strategy Pattern for Account Status
- **Interface**: `AccountStatusStrategy` - Defines operations allowed for each status
- **Implementations**:
  - `ActiveAccountStrategy` - Allows all operations except deletion with non-zero balance
  - `FrozenAccountStrategy` - Blocks withdrawals and updates, allows deposits
  - `ClosedAccountStrategy` - Blocks all operations except balance inquiry
- **Factory**: `AccountStatusStrategyFactory` - Retrieves appropriate strategy by status

#### 2. State Machine for Status Transitions
- **Validator**: `AccountStatusTransitionValidator`
- **Valid Transitions**:
  - ACTIVE → FROZEN, CLOSED
  - FROZEN → ACTIVE, CLOSED  
  - CLOSED → (no transitions allowed)
- **Validation**: Requires reason for status changes, prevents invalid transitions

### Key Features

#### Enhanced Account Updates
The `UpdateAccountRequest` now supports updating multiple fields:
- `accountName` - Custom account name
- `accountType` - Account type (SAVINGS, CHECKING, CREDIT)
- `status` - Account status with transition validation
- `creditLimit` - Credit limit for credit accounts
- `currency` - Account currency
- `statusChangeReason` - Required reason for status changes

#### Transaction Validation by Status
- **ACTIVE**: All transactions allowed
- **FROZEN**: Only deposits allowed, withdrawals blocked
- **CLOSED**: No transactions allowed

#### Audit Trail Integration
- All status changes are logged with reasons
- Domain events published for status changes
- Complete audit history available through AuditController

### Implementation Details

#### AccountStatusStrategy Interface
```java
public interface AccountStatusStrategy {
    boolean canWithdraw(Account account, BigDecimal amount);
    boolean canDeposit(Account account, BigDecimal amount);
    boolean canUpdate(Account account);
    boolean canDelete(Account account);
    boolean canChangeStatusTo(Account account, AccountStatus newStatus);
    String getRestrictionReason();
    AccountStatus getHandledStatus();
}
```

#### Status Change Flow
1. User requests status change via PATCH `/v1/accounts/{id}`
2. `AccountStatusTransitionValidator` validates the transition
3. Status is updated and reason recorded
4. Domain event published for monitoring
5. Audit entry created automatically

#### Integration Points
- **AccountService**: Validates operations using status strategies
- **TransactionService**: Checks account status before processing
- **EventPublisher**: Publishes status change events
- **AuditController**: Queries audit history for compliance

### Testing
Comprehensive test coverage includes:
- Unit tests for each strategy implementation
- Status transition validation tests  
- Integration tests for end-to-end workflows
- Controller tests for API endpoints

### Future Extensibility
The design allows easy addition of new account statuses:
1. Create new strategy implementing `AccountStatusStrategy`
2. Register in `AccountStatusStrategyFactory`
3. Update `AccountStatusTransitionValidator` with transition rules
4. No changes needed in services or controllers

## Audit Repository Usage

### Problem Identified
The audit repository interface had multiple query methods that were not being used in production, making it impossible to query audit logs effectively.

### Solution Implemented
Created `AuditController` with comprehensive audit querying capabilities:

#### Endpoints
- `GET /v1/audit` - List all audit entries (admin only)
- `GET /v1/audit/users/{userId}` - Get user's audit trail (own or admin)
- `GET /v1/audit/accounts/{accountId}` - Get account audit trail (admin only)
- `GET /v1/audit/actions/{action}` - Filter by action type (admin only)
- `GET /v1/audit/date-range` - Query by date range (admin only)
- `GET /v1/audit/stats` - Get audit statistics (admin only)

#### Security
- Admin-only access for most endpoints
- Users can view their own audit trail
- Proper 403 responses for unauthorized access

#### Statistics Endpoint
Provides aggregated metrics:
- Total audit entries
- Counts by action type (LOGIN, CREATE, UPDATE, DELETE, ACCESS_DENIED)
- Date range filtering for trend analysis

This ensures all audit repository methods are now accessible and the audit trail can be properly queried for compliance and security purposes.

## Development Progress (Updated)

### Recently Completed Tasks
39. Implement Account Status Strategy Pattern
    - Created `AccountStatusStrategy` interface
    - Implemented strategies for ACTIVE, FROZEN, and CLOSED statuses
    - Created `AccountStatusStrategyFactory` for strategy retrieval
    - Integrated status checks in AccountService and TransactionService
    
40. Implement Account Status Transitions
    - Created `AccountStatusTransitionValidator` with state machine logic
    - Added status change validation with reason requirement
    - Published domain events for status changes
    
41. Enhance Account Update Functionality
    - Extended `UpdateAccountRequest` with multiple fields
    - Added support for updating accountName, currency, creditLimit
    - Implemented status change with validation
    
42. Create Audit Query Endpoints
    - Implemented `AuditController` with comprehensive query methods
    - Added pagination and filtering capabilities
    - Created audit statistics endpoint
    - Ensured all audit repository methods are utilized
    
43. Add Comprehensive Testing
    - Created `AccountStatusStrategyTest` for strategy validation
    - Added tests for all status transitions and operations
    - Verified restriction reasons and handled statuses

## Memories

- I will commit and use git, claude does not do that