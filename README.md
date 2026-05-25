# Design Decisions
1. Using Jakarta for validation
2. Changed PostPaymentRequest to have full card number
3. BankClient to encapsulate all bank communications
4. it turns technical exceptions into domain exceptions
5. it logs stacks but does not propagate them (we should not leak complex stacks to merchants)
6. PostPaymentResponse.of where conversion from request to response happens
7. PostPaymentRequest toString changed to avoid logging of full card number
8. card numbers and cvv using String: leading 0 would be lost with int
9. Hard coded three currencies for simplicity
10. bank url set in properties (easier to find and replace for tests)
11. Retry for Unavailable bank:
a straight retry chosen for simplicity, it would be not good for payments (duplicate payments risks)
12. The expiration date was the only one that required cross validation, hence its own Validator
13. PaymentGatewayServiceTest use real PaymentsRepository. Pro: simpler and more real tests;
Con: less extensible (a proper PaymentsRepository should probably be mocked later on)
14. No tests for PaymentRepository (has no logic)
15. 


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