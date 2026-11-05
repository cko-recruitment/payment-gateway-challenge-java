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

## Bank failures

Failures talking to the acquiring bank fall into three buckets, listed in `BankFailure`. Each is
returned as `application/problem+json` with `payment_status: Rejected`.

| Case | Status | `detail` |
|---|---|---|
| Bank answered 4xx | 502 | The acquiring bank rejected the request from this gateway. |
| Bank answered 5xx | 503 | The acquiring bank is currently unavailable. |
| Bank unreachable, timed out, or its response was unusable | 502 | The acquiring bank could not be reached. |

A bank 4xx is reported as 502 rather than passed through as a 400: the client's input has already
passed validation, so a rejected request means this gateway's contract with the bank is wrong.

Connect and read timeouts default to 2s and 5s, overridable with `PAYMENTS_BANK_CONNECT_TIMEOUT`
and `PAYMENTS_BANK_READ_TIMEOUT`.