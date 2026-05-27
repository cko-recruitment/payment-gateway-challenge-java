.PHONY: demo health success cache validation retry retry2 decline getid getkey all

BASE_URL := http://localhost:8090
MERCHANT := merchant-123

all: health success cache validation valcache retry retry2 decline getid getkey

demo: all

health:
	@echo "\n=== Health Check ==="
	@curl -s $(BASE_URL)/actuator/health | jq .

success:
	@echo "\n=== Success Payment ==="
	@curl -s $(BASE_URL)/api/v1/payments \
		-X POST -H "Content-Type: application/json" \
		-H "Merchant-Id: $(MERCHANT)" -H "Idempotency-Key: demo-success" \
		-d '{"card_number":"4242424242424241","expiry_month":12,"expiry_year":2027,"currency":"USD","amount":100,"cvv":"123"}' \
		| jq . | tee /tmp/demo_success.json
	@jq -r '.id' /tmp/demo_success.json > /tmp/demo_id.txt

cache:
	@echo "\n=== Cache Hit ==="
	@curl -s $(BASE_URL)/api/v1/payments \
		-X POST -H "Content-Type: application/json" \
		-H "Merchant-Id: $(MERCHANT)" -H "Idempotency-Key: demo-success" \
		-d '{"card_number":"4242424242424241","expiry_month":12,"expiry_year":2027,"currency":"USD","amount":100,"cvv":"123"}' \
		| jq '{id, createdAt, updatedAt}'

validation:
	@echo "\n=== Validation Error ==="
	@curl -s $(BASE_URL)/api/v1/payments \
		-X POST -H "Content-Type: application/json" \
		-H "Merchant-Id: $(MERCHANT)" -H "Idempotency-Key: demo-validation" \
		-d '{"card_number":"abc","expiry_month":12,"expiry_year":2027,"currency":"USD","amount":100,"cvv":"123"}' \
		| jq '{code, description, action}'

valcache:
	@echo "\n=== Validation Cache Hit ==="
	@curl -s $(BASE_URL)/api/v1/payments \
		-X POST -H "Content-Type: application/json" \
		-H "Merchant-Id: $(MERCHANT)" -H "Idempotency-Key: demo-validation" \
		-d '{"card_number":"abc","expiry_month":12,"expiry_year":2027,"currency":"USD","amount":100,"cvv":"123"}' \
		| jq '{code, createdAt, updatedAt}'

retry:
	@echo "\n=== Retriable Error (503) ==="
	@curl -s $(BASE_URL)/api/v1/payments \
		-X POST -H "Content-Type: application/json" \
		-H "Merchant-Id: $(MERCHANT)" -H "Idempotency-Key: demo-retry" \
		-d '{"card_number":"4242424242424240","expiry_month":12,"expiry_year":2027,"currency":"USD","amount":100,"cvv":"123"}' \
		| jq '{code, action, createdAt, updatedAt}' | tee /tmp/demo_retry.json

retry2:
	@echo "\n=== Retry Again ==="
	@curl -s $(BASE_URL)/api/v1/payments \
		-X POST -H "Content-Type: application/json" \
		-H "Merchant-Id: $(MERCHANT)" -H "Idempotency-Key: demo-retry" \
		-d '{"card_number":"4242424242424240","expiry_month":12,"expiry_year":2027,"currency":"USD","amount":100,"cvv":"123"}' \
		| jq '{code, action, createdAt, updatedAt}'
	@echo "createdAt < updatedAt = bank retried!"

decline:
	@echo "\n=== Declined Payment ==="
	@curl -s $(BASE_URL)/api/v1/payments \
		-X POST -H "Content-Type: application/json" \
		-H "Merchant-Id: $(MERCHANT)" -H "Idempotency-Key: demo-decline" \
		-d '{"card_number":"4242424242424242","expiry_month":12,"expiry_year":2027,"currency":"USD","amount":100,"cvv":"123"}' \
		| jq '{code, status}'

getid:
	@echo "\n=== Get by ID ==="
	@curl -s $(BASE_URL)/api/v1/payments/$$(cat /tmp/demo_id.txt) \
		-H "Merchant-Id: $(MERCHANT)" | jq '{id, status, amount}'

getkey:
	@echo "\n=== Get by Idempotency Key ==="
	@curl -s $(BASE_URL)/api/v1/payments/idempotency/demo-success \
		-H "Merchant-Id: $(MERCHANT)" | jq '{id, status, amount}'
