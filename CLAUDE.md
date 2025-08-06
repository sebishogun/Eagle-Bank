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

## Memories

- I will commit and use git, claude does not do that
- to recompile we are using mvn clean compile 
- we do not use docker-compose ever that is a deprecated command, we only use docker compose commands written like that with no - 
- the only docker compose command you are allowed to do is : docker compose down && docker compose up --build -d  and that is to be used for all docker compose restarts on changes etc. 