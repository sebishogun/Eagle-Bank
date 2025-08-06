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