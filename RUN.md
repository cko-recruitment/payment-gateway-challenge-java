# Local Run Guide

One-command local hosting for the Payment Gateway and Bank Simulator.

---

## Prerequisites

- Docker & Docker Compose
- (Optional) `curl` or Postman for API testing

---

## Quick Start

```bash
# Clone and enter project
git clone <repo-url>
cd payment-gateway-challenge-java

# First build (slow - downloads dependencies)
docker-compose build

# Subsequent starts (fast - uses cache)
docker-compose up

# Or one-liner if code changed
docker-compose up --build
```

Services will be available at:

| Service | URL |
|---------|-----|
| Payment Gateway API | http://localhost:8090 |
| Swagger UI | http://localhost:8090/swagger-ui.html |
| Bank Simulator | http://localhost:8080 |
| Mountebank Admin | http://localhost:2525 |

---

## API Test

### Process a Payment

```bash
curl -X POST http://localhost:8090/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Merchant-Id: merchant-1" \
  -H "Idempotency-Key: key-1" \
  -d '{
    "cardNumber": "4111111111111111",
    "expiryMonth": 12,
    "expiryYear": 2027,
    "currency": "USD",
    "amount": 100,
    "cvv": "123"
  }'
```

**Expected response (authorized — odd last digit):**
```json
{
  "code": "SUCCESS",
  "description": "Payment authorized successfully",
  "action": "none",
  "id": "...",
  "status": "Authorized",
  "cardNumberLastFour": 1111,
  "expiryMonth": 12,
  "expiryYear": 2027,
  "currency": "USD",
  "amount": 100,
  "links": [...]
}
```

### Retrieve by ID

```bash
curl http://localhost:8090/api/v1/payments/{id} \
  -H "Merchant-Id: merchant-1"
```

### Retrieve by Idempotency Key

```bash
curl http://localhost:8090/api/v1/payments/idempotency/key-1 \
  -H "Merchant-Id: merchant-1"
```

---

## Bank Simulator Behaviors

| Card Ending | Result |
|-------------|--------|
| **Odd** (1,3,5,7,9) | `authorized: true` |
| **Even** (2,4,6,8) | `authorized: false` |
| **0** | `503 Service Unavailable` |

---

## Stop

```bash
# Graceful stop
docker-compose down

# Stop and remove volumes
docker-compose down -v
```

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| Port 8090 in use | `docker-compose up -d` then check `docker ps` |
| Build cache issue | `docker-compose build --no-cache` |
| Bank not responding | Verify `bank_simulator` is healthy: `curl http://localhost:2525/imposters` |
| Grafana 不显示 Rate Limit | 见下方 **Rate Limit 排查** |
| JaCoCo 覆盖率不包含 IT/FT | 见下方 **覆盖率报告排查** |

---

## Rate Limit 排查

### 现象
- Grafana `Rate Limit Hits` 面板始终为 0
- Load test 压测时看不到限流效果

### 原因 1：Load Test 未触发限流
Rate limit 是 **per merchant**（每个商户 10 req/s）。如果 load test 中每个 VU 使用不同 merchant，并发会被分散，永远达不到单个商户的阈值。

**修复**：`load-tests/load-test.js` 中让多个 VU 共享同一个 merchant：

```javascript
// 错误：100 VUs = 100 个商户，每个 1 req/s，不触发限流
const merchantId = `merchant-${__VU}`;

// 正确：100 VUs = 10 个商户，每个 ~10 req/s，触发限流
const merchantId = `merchant-${__VU % 10}`;
```

### 原因 2：Grafana 指标名称不匹配
Micrometer Counter 在 Prometheus 中会自动加上 `_total` 后缀。

| 代码中的名称 | Prometheus 实际名称 |
|-------------|-------------------|
| `rate_limit.rejected` | `rate_limit_rejected_total` |

**修复**：Grafana dashboard 查询语句使用正确的指标名：

```promql
# 错误
sum(rate(rate_limit_hits[1m]))

# 正确
sum(rate(rate_limit_rejected_total[1m]))
```

### 验证 Rate Limit 是否生效

```bash
# 1. 启动应用和监控栈
docker-compose up -d
docker-compose -f docker-compose.observability.yml up -d

# 2. 快速发送 15 个请求（同一商户，超过 10 req/s）
for i in $(seq 1 15); do
  curl -s -X POST http://localhost:8090/api/v1/payments \
    -H "Content-Type: application/json" \
    -H "Merchant-Id: merchant-test" \
    -H "Idempotency-Key: key-$i" \
    -d '{"cardNumber":"4111111111111111","expiryMonth":12,"expiryYear":2027,"currency":"USD","amount":100,"cvv":"123"}' &
done
wait

# 3. 查看 Prometheus 指标
curl -s http://localhost:8090/actuator/prometheus | grep rate_limit_rejected_total

# 4. 打开 Grafana 查看 Rate Limit Hits 面板
open http://localhost:3000/d/payment-gateway/payment-gateway
```

---

## 覆盖率报告排查

### 现象
- `jacocoTestReport` 只显示 unit test 覆盖率
- integration test 和 functional test 的结果未计入

### 原因
Gradle 配置了两种覆盖率报告：

| 任务 | 包含测试 | 用途 |
|------|---------|------|
| `jacocoTestReport` | unit test only | 本地快速查看 |
| `jacocoFullReport` | unit + integration + functional | CI 完整报告 |

### 本地运行

```bash
# 只跑 unit test + 覆盖率（不需要 bank simulator）
./gradlew test jacocoTestReport

# 查看报告
open build/reports/jacoco/test/html/index.html
```

### CI 完整报告

```bash
# 需要 bank simulator 运行
./gradlew test integrationTest functionalTest jacocoFullReport

# 查看完整报告
open build/reports/jacoco/jacocoFullReport/html/index.html
```

### 配置说明

`build.gradle` 中的配置：

```gradle
jacocoTestReport {
    dependsOn test                          // 只依赖 unit test
    executionData(
        fileTree(project.buildDir).include("**/jacoco/test.exec")
    )
    // ... exclude 配置
}

task jacocoFullReport(type: JacocoReport) {
    dependsOn test, integrationTest, functionalTest  // 依赖全部三层
    executionData(
        fileTree(project.buildDir).include("**/jacoco/*.exec")
    )
    // ... 相同的 exclude 配置
}
```

### 常见问题

**Functional Test 端口冲突**

如果同时运行 integration test 和 functional test，可能出现 `BindException: Address already in use`。

**修复**：确保 functional test 使用随机端口：

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class PaymentGatewayFunctionalTest {
    @LocalServerPort
    private int port;
    // ...
}
```
