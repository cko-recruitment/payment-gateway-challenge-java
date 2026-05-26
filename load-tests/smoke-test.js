import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 1,
  duration: '10s',
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8090';

export default function () {
  const res = http.post(`${BASE_URL}/api/v1/payments`, JSON.stringify({
    card_number: '4111111111111111',
    expiry_month: 12,
    expiry_year: 2027,
    currency: 'USD',
    amount: 100,
    cvv: '123',
  }), {
    headers: {
      'Content-Type': 'application/json',
      'Merchant-Id': 'smoke-test',
      'Idempotency-Key': `smoke-${__ITER}`,
    },
  });

  check(res, {
    'status is 200': (r) => r.status === 200,
    'authorized or declined': (r) => ['SUCCESS', 'PAYMENT_DECLINED'].includes(r.json('code')),
  });

  sleep(1);
}
