import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const paymentLatency = new Trend('payment_latency');
const rateLimitHits = new Counter('rate_limit_hits');

export const options = {
  stages: [
    { duration: '30s', target: 10 },   // Ramp up
    { duration: '1m', target: 50 },    // Steady load
    { duration: '30s', target: 100 },  // Peak load (will trigger rate limit)
    { duration: '30s', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],   // 95% under 500ms
    errors: ['rate<0.1'],                // Error rate < 10%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8090';

export default function () {
  const merchantId = `merchant-${__VU}`;
  const idempotencyKey = `key-${__VU}-${__ITER}`;

  const payload = JSON.stringify({
    card_number: '4111111111111111',
    expiry_month: 12,
    expiry_year: 2027,
    currency: 'USD',
    amount: 100,
    cvv: '123',
  });

  const start = Date.now();
  const res = http.post(`${BASE_URL}/api/v1/payments`, payload, {
    headers: {
      'Content-Type': 'application/json',
      'Merchant-Id': merchantId,
      'Idempotency-Key': idempotencyKey,
    },
  });
  const latency = Date.now() - start;
  paymentLatency.add(latency);

  // Check for rate limiting
  const isRateLimited = res.status === 200 && res.json('status') === 'Rejected' && 
                        res.json('description') && res.json('description').includes('Rate limit');
  
  if (isRateLimited) {
    rateLimitHits.add(1);
    // Backoff when rate limited
    sleep(1);
  }

  const success = check(res, {
    'status is 200': (r) => r.status === 200,
    'response has code': (r) => r.json('code') !== undefined,
  });

  errorRate.add(!success && !isRateLimited);
  sleep(Math.random() * 0.5); // Random think time 0-500ms
}
