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
# Clean and build
./mvnw clean package

# Run the application
./mvnw spring-boot:run

# Run tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=UserControllerTest

# Skip tests during build
./mvnw clean package -DskipTests

# Generate test coverage report (if JaCoCo is configured)
./mvnw clean test jacoco:report
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

### ✅ Completed Tasks
1. ✅ Initialize Spring Boot project with Java 21 and Maven
2. ✅ Update pom.xml with PostgreSQL, UUID libraries, and Testcontainers
3. ✅ Create Docker Compose file for PostgreSQL 17
4. ✅ Configure application profiles (dev with PostgreSQL, test with H2)
5. ✅ Implement UUID v7 generator for entity IDs
6. ✅ Implement UUID v4 generator for random tokens
7. ✅ Create JPA entity classes (User, Account, Transaction) with UUID
8. ✅ Create repository interfaces for all entities
9. ✅ Write tests for UserService create and fetch operations
10. ✅ Implement UserService to pass tests
11. ✅ Write tests for UserController POST and GET endpoints
12. ✅ Implement UserController to pass tests
13. ✅ Write tests for JWT authentication and security
14. ✅ Implement JWT security configuration and token provider
15. ✅ Write tests for AuthService and AuthController
16. ✅ Implement AuthService and AuthController for login
17. ✅ Create custom exceptions and GlobalExceptionHandler
18. ✅ Create DTOs for request/response objects (User and Auth DTOs)
19. ✅ Fix deprecated DaoAuthenticationProvider methods (Spring Security 6.3+)
20. ✅ Configure Maven compiler plugin for Lombok annotation processing
21. ✅ Create Account DTOs (CreateAccountRequest, UpdateAccountRequest, AccountResponse)
22. ✅ Write comprehensive tests for AccountService CRUD operations
23. ✅ Implement AccountService with business logic and authorization
24. ✅ Write tests for AccountController endpoints
25. ✅ Implement AccountController with REST endpoints
26. ✅ Create Transaction DTOs (CreateTransactionRequest, TransactionResponse)
27. ✅ Implement InsufficientFundsException for transaction validation
28. ✅ Write tests for TransactionService deposit/withdrawal logic
29. ✅ Implement TransactionService with balance validation
30. ✅ Write tests for TransactionController endpoints
31. ✅ Implement TransactionController
32. ✅ Implement transaction reference number generation
33. ✅ Implement insufficient funds validation
34. ✅ Implement authorization checks for transactions
35. ✅ Create UpdateUserRequest DTO for user updates
36. ✅ Add countByUserId to AccountRepository for user deletion validation
37. ✅ Implement UserService updateUser and deleteUser methods
38. ✅ Add PATCH and DELETE endpoints to UserController
39. ✅ Write comprehensive integration tests for user update/delete operations
40. ✅ Implement authorization checks (users can only modify their own data)
41. ✅ Implement business rule: cannot delete user with existing accounts
42. ✅ Create meaningful integration tests that validate real behavior

### 🚧 In Progress
- None currently

### 📋 Pending Tasks (Priority Order)

#### Documentation & Deployment
- Create or update OpenAPI specification with all endpoints
- Add Swagger UI integration to the project
- Update README with final API documentation
- Create Postman collection for testing (optional)
- Final testing and validation of all requirements

## Memories

- I will commit and use git, claude does not do that