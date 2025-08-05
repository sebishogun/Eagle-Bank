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