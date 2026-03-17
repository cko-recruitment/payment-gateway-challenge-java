# Instructions for candidates

This is the Java version of the Payment Gateway challenge. If you haven't already read this [README.md](https://github.com/cko-recruitment/) on the details of this exercise, please do so now.

## Requirements
- JDK 17
- Docker

## Template structure

src/ - A skeleton SpringBoot Application

test/ - Some simple JUnit tests

imposters/ - contains the bank simulator configuration. Don't change this

.editorconfig - don't change this. It ensures a consistent set of rules for submissions when reformatting code

docker-compose.yml - configures the bank simulator


## API Documentation
For documentation openAPI is included, and it can be found under the following url: **http://localhost:8090/swagger-ui/index.html**

**Feel free to change the structure of the solution, use a different library etc.**

## Payment Gateway Challenge (JAVA) — Implementation Details added below

This project implements a Payment Gateway API that allows merchants to process and retrieve card payments.
The gateway validates incoming requests, calls an acquiring bank simulator, stores payment details, and exposes endpoints to retrieve payment information.

## Architecture Overview

The application follows a layered architecture to ensure separation of concerns and maintainability.

Controller -> Service -> Validator -> Client -> Repository (In-memory)

## Components and its responsibilities
- Controller        -  Exposes REST endpoints
- Service           -  Coordinates payment processing logic
- Validator         -  Performs request validation
- Client            -  Communicates with acquiring bank simulator
- Repository        -  Stores payment records in memory
- Mapper	          -  Converts between DTOs and models
- Exception Handler	- Handles errors and produces API responses

## Features Implemented

1. Process card payments
2. Retrieve payments by ID
3. Validation of payment requests
4. Support for Authorized, Declined, and Rejected responses
5. Communication with Acquiring Bank Simulator
6. Centralized exception handling
7. In-memory storage for payments
8. Comprehensive unit and integration tests

## API EndPoints

## ProcessPayment

POST api/payment
header - UUID

valid Request:

{
"cardNumber": "4242424242424241",
"expiryMonth": 12,
"expiryYear": 2029,
"currency": "USD",
"amount": 100,
"cvv": "123"
}

- SuccessfulResponse with status AUTHORIZED
  {
  "id": "c3ad5b28-0c44-4b41-8b9f-f0c9e0d3a0b5",
  "status": "Authorized",
  "lastFourCardDigits": "4241",
  "expiryMonth": 12,
  "expiryYear": 2029,
  "currency": "USD",
  "amount": 100,
  "authorizationCode": "AUTH-123"
  }

- SuccessfulResponse with status DECLINED
  {
  "id": "c3ad5b28-0c44-4b41-8b9f-f0c9e0d3a0b5",
  "status": "Authorized",
  "lastFourCardDigits": "4241",
  "expiryMonth": 12,
  "expiryYear": 2029,
  "currency": "USD",
  "amount": 100,
  "authorizationCode": "AUTH-123"
  }

Requests failing the validation are rejected

- Response for REJECTED status
  {
  "status": "Rejected",
  "messages": [
  "Card number must be between 14 and 19 digits",
  "Expiry month must be between 1 and 12",
  "CVV must be 3 or 4 digits"
  ]
  }

## Retrieve Payment
GET api/payment/{id}

- Successful response
  {
  "id": "c3ad5b28-0c44-4b41-8b9f-f0c9e0d3a0b5",
  "status": "Authorized",
  "lastFourCardDigits": "4241",
  "expiryMonth": 12,
  "expiryYear": 2029,
  "currency": "USD",
  "amount": 100
  }

## Design Decisions

## Layered Architecture
A layered design separates responsibilities across controller, service, repository, and client layers.
This improves maintainability and testability.

## In-Memory Storage
The challenge requirements allow storing payments in memory.
An in-memory repository is used to keep the implementation simple.
In production, this would be replaced with a database.

## Validation Strategy
Validation is centralized in a PaymentBusinessValidator class.
- Benefits:
  All validation rules in one place
  Easier testing
  Ability to return multiple validation errors simultaneously

## Error Handling
A common exception handler provides consistent API responses for:
- Validation failures
- Payment not found
- Bank connectivity failures
- Unexpected errors

## Security Considerations
To follow payment security practices:
- Full card numbers are never returned
- Only last four digits are stored
- CVV is not stored
- Sensitive fields are not logged

## Resilience
The acquiring bank client includes:
- Timeout handling
- Graceful error handling
- Proper HTTP status mapping


## Assumptions
- Only three currencies are supported for validation
- Amounts are provided in minor currency units
- The bank simulator is the only acquiring bank
- Payments are stored in memory