# Eagle Bank REST API

REST API for Eagle Bank - a fictional banking system that allows users to manage accounts and perform transactions.

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

The API will be available at: `http://localhost:8080/api`

## Running Tests

```bash
# Run all tests
./mvnw test

# Run with coverage
./mvnw clean test jacoco:report
```

## API Documentation

When running, access Swagger UI at: `http://localhost:8080/api/swagger-ui.html`

## Authentication

All endpoints except user creation require JWT authentication:
1. Create user via `POST /v1/users`
2. Login via `POST /v1/auth/login` to receive JWT token
3. Include token in Authorization header: `Bearer <token>`