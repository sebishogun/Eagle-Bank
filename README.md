# Eagle Bank REST API

REST API for Eagle Bank - a banking system that allows users to manage accounts and perform transactions.

## Functionality

The API provides comprehensive banking operations including user registration and authentication, bank account management (checking and savings), and transaction processing (deposits and withdrawals). Key features include JWT-based authentication, role-based access control, transaction history tracking, balance validation for withdrawals, and proper authorization ensuring users can only access their own data. The system supports paginated queries, comprehensive error handling, and includes administrative endpoints for metrics and monitoring.

## API Documentation

Access Swagger UI at: http://localhost:8080/api/swagger-ui.html

OpenAPI specification: http://localhost:8080/api/v3/api-docs

## Technology Stack

- Java 21
- Spring Boot 3.5.4
- PostgreSQL 17.5
- Maven 3.8+
- Docker & Docker Compose
- JWT for authentication
- Testcontainers for integration testing

## Prerequisites

- Java 21
- Maven 3.8+
- Docker & Docker Compose

## Running the Application

```bash
docker compose up --build -d 
```

API available at: http://localhost:8080/api

## Running Tests

```bash
mvn test
mvn clean test jacoco:report
```

## Authentication

1. Create user: POST /v1/users
2. Login: POST /v1/auth/login
3. Use token in Authorization header: Bearer <token>

## Implementation Overview

### Core Requirements fully implemented 
All required functionality from the specification has been fully implemented:

- **User Management**
  - Create, fetch, update, and delete users
  - User authentication with JWT tokens
  - Proper authorization and access control
  
- **Account Management**  
  - Create, list, fetch, update, and delete bank accounts
  - Support for multiple account types (Checking, Savings, Credit)
  - Account ownership validation
  
- **Transaction Processing**
  - Deposits and withdrawals with balance updates
  - Transaction history retrieval
  - Insufficient funds validation
  - Immutable transaction records

### Extended Features & Architectural Excellence 
The API has been designed with extensibility in mind, implementing numerous additional features beyond the basic requirements:

#### Advanced Account Features
- **Account Status Management**: ACTIVE, INACTIVE, FROZEN, CLOSED states with validated transitions
- **Credit Accounts**: Full credit card functionality with credit limits and overpayment handling
- **Savings Accounts**: Separate account type with configurable minimum balance requirements
- **Transfer Operations**: Atomic money transfers between accounts with rollback support
- **Automatic Inactivity Detection**: Scheduled jobs to mark dormant accounts as inactive

#### Security & Compliance
- **JWT Token Management**: Access/refresh token pattern with configurable expiration
- **Admin Role System**: Separate admin endpoints with elevated privileges
- **Comprehensive Audit Logging**: Every operation is tracked with user, timestamp, and changes
- **Password Security**: BCrypt hashing with proper salt rounds and  entropy validation for passwords
- **Input Validation**: Extensive validation for all DTOs with detailed error messages

#### Enterprise Patterns & Best Practices
- **Design Patterns**: Factory, Strategy, Observer patterns for extensible architecture
- **Event-Driven Architecture**: Account events published for downstream processing
- **Database Schema Management**: Hibernate ddl-auto with validation in production
- **Distributed Scheduling**: ShedLock for safe scheduled job execution in clusters
- **Transaction Management**: Spring @Transactional for ACID compliance

#### Performance & Scalability
- **Caching Layer**: Spring Cache with configurable TTL for frequently accessed data
- **Cache Warming**: Preload critical data on startup for optimal performance
- **Metrics Collection**: Micrometer integration with Prometheus endpoints
- **Batch Processing**: Efficient handling of bulk operations
- **Connection Pooling**: HikariCP for optimal database connection management

#### Developer Experience
- **OpenAPI Documentation**: Complete Swagger UI with try-it-out functionality
- **Comprehensive Testing**: 339 tests including unit, integration, and Testcontainers
- **Postman Collection**: Full API coverage with automated token management
- **Docker Compose**: One-command local development environment
- **Health Checks**: Actuator endpoints for monitoring application health

#### Observability & Monitoring
- **Custom Metrics**: Business metrics for transactions, accounts, and authentication
- **Grafana Dashboards**: Pre-configured visualizations for key metrics
- **Structured Logging**: Consistent log format with SLF4J and Logback
- **Error Tracking**: Global exception handler with detailed error responses

## API Testing with Postman

Complete Postman collection and environment files are available in `docs/postman-collection/`:
- **Collection**: `Eagle_Bank_API.postman_collection.json` - Contains all API endpoints with test scenarios
- **Environment**: `Eagle_Bank_API.postman_environment.json` - Pre-configured environment variables

### Features
- **Automatic Token Management**: Pre-request scripts automatically handle authentication tokens
- **Comprehensive Test Coverage**: All endpoints include test assertions for response validation
- **Organized Test Scenarios**: Structured folders for different features and account types
- **Admin Functionality**: Complete admin endpoint testing with role-based access
- **Transfer Feature**: Full transfer workflow with validation scenarios

### How to Use
1. **Import Files**: Import both collection and environment files into Postman
2. **Select Environment**: Choose "Eagle Bank Local" from the environment dropdown
3. **Run in Order**: Execute requests sequentially - authentication tokens are handled automatically
4. **Test Flows**:
   - Start with "1. Admin Setup & Authentication" for admin features
   - Follow with "2. User Setup & Authentication" for regular user testing
   - "3. Account Type Examples" demonstrates all account types (CHECKING, SAVINGS, CREDIT)
   - Continue through numbered sections for complete test coverage

### Test Scenarios Included
- User registration and authentication
- Account creation for all types (CHECKING, SAVINGS, CREDIT)
- Transaction operations (deposits, withdrawals)
- Transfer features with validation
- Account status management (ACTIVE, FROZEN, CLOSED)
- Admin operations (high-value accounts, dormant accounts, audit logs)
- Metrics and monitoring endpoints

### Default Credentials
- **Admin**: admin@eaglebank.com / Admin123!
- **Test Users**: Created automatically by the collection

## Monitoring

The application includes comprehensive monitoring using Prometheus and Grafana.

### Access Points
- **Application**: http://localhost:8080/api
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)

### Metrics Architecture

```
┌─────────────────┐     ┌──────────────┐     ┌─────────────┐
│  Eagle Bank API │────▶│  Prometheus  │────▶│   Grafana   │
│  (Micrometer)   │     │  (Scraping)  │     │ (Dashboards)│
└─────────────────┘     └──────────────┘     └─────────────┘
        │                                              │
        └── /actuator/prometheus ──────────────────────┘
```

### Available Metrics
- **Business Metrics**: Transaction counts/volume, account statistics, authentication metrics
- **System Metrics**: JVM memory, CPU usage, HTTP request rates
- **Custom Dashboards**: Pre-configured Eagle Bank dashboard with real-time visualization

### Prometheus Queries Examples
```promql
# Transaction rate by type
rate(transactions_count_total[5m])

# Login success rate
authentication_logins_success_rate

# Active accounts by type  
accounts_active
```

## Next Steps & Future Enhancements if I was to continue development

### Immediate Priorities
1. **Production Readiness**
   - Implement rate limiting for API endpoints to prevent abuse
   - Add distributed tracing with Spring Cloud Sleuth/Zipkin
   - Enhance security with OAuth2/OIDC integration for enterprise SSO
   - Implement API versioning strategy for backward compatibility

2. **Performance Optimizations**
   - Database query optimization with proper indexing strategies
   - Implement read replicas for scaling read operations
   - Add Redis/Hazelcast for distributed caching in clustered deployments
   - Optimize batch processing for large transaction volumes

3. **Enhanced Features**
   - Multi-currency support with real-time exchange rates
   - Scheduled/recurring transfers functionality
   - Account statements generation (PDF/CSV exports)
   - Mobile app push notifications for transactions
   - Two-factor authentication (2FA) with TOTP/SMS

4. **Compliance & Regulations**
   - PCI DSS compliance for payment card data
   - GDPR compliance for data privacy (right to be forgotten, data portability)
   - Anti-money laundering (AML) transaction monitoring
   - Know Your Customer (KYC) verification workflow

5. **Integration Capabilities**
   - Payment gateway integrations (Stripe, PayPal)
   - Open Banking API compliance (PSD2)
   - Credit bureau integration for credit scoring
   - External audit trail system integration

These enhancements would transform the Eagle Bank API from a robust MVP into a production-ready, enterprise-grade banking platform suitable for real-world deployment.