# Architecture Evolution

This document outlines the evolution of the payment gateway architecture. Phases 1 to 3
are implemented in this solution; phases 4 and 5 describe the direction I would take the
system next.

## Phase 1 — Basic Functionality

- Implement the core functional requirements.
- Run the application with Docker to simplify future improvements and evolution.

## Phase 2 — Database, Reliability and Events
- Encrypt sensible data
- Encapsule validation into validator class

## Phase 3 — Database and Events
- Implement idempotency to prevent the same transaction from being processed twice.
- Add a PostgreSQL connection.

## Phase 4 — Bank Resilience
- Publish events so that payment data can be distributed to other applications.
- Add a circuit breaker around the bank integration.
- Track downstream failures and avoid excessive requests while the bank is recovering.

## Phase 5 — Scalability and Performance

- Introduce load balancing.
- Run performance tests and identify potential improvements.
- Create a performance improvement plan based on the test results.

# Tests Architecture

## Integration Controller test

Tests meant to validate the whole flow in the whole chain of the code, no mocking used, just a Spring boot run to test how each requests works

## Contract Controller test

Tests meant to be used to validate contract errors and validations, does not need any database/repository integration, meant to test unsuccessful contract cases.

## Class unit tests

Uses Mocks and validate the isolated class logic, uses mockito to mock target classes.
