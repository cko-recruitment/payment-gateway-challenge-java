# 并发幂等性保证

## 问题场景

```
时间线：
T1: 请求A ──► 检查幂等键 ──► 不存在 ──► 开始处理
T2: 请求B ──► 检查幂等键 ──► 不存在 ──► 开始处理  ← 并发！
T3: 请求A ──► 处理完成 ──► 存储结果
T4: 请求B ──► 处理完成 ──► 存储结果  ← 重复处理！
```

## 解决方案

### 1. 分布式锁（推荐）

```
请求A ──► 获取锁 ──► 处理 ──► 释放锁 ──► 返回结果
            ↑
请求B ──► 获取锁失败 ──► 等待 ──► 获取结果 ──► 返回
```

### 2. 数据库唯一约束

```sql
-- 幂等键作为唯一索引
CREATE TABLE idempotency_keys (
    merchant_id VARCHAR(64),
    dedupe_key VARCHAR(255),
    status VARCHAR(20),  -- PROCESSING / COMPLETED
    response JSON,
    created_at TIMESTAMP,
    PRIMARY KEY (merchant_id, dedupe_key)
);

-- 插入时如果已存在会报错
INSERT INTO idempotency_keys (merchant_id, dedupe_key, status)
VALUES ('merchant1', 'key123', 'PROCESSING');
```

### 3. Redis SETNX（单实例）

```java
// Redis 原子操作
SET merchant1:key123 "PROCESSING" NX EX 60
// NX = 不存在才设置
// EX 60 = 60秒过期，防止死锁
```

### 4. Redis RedLock（多实例）

```
┌─────────┐  ┌─────────┐  ┌─────────┐
│ Redis 1 │  │ Redis 2 │  │ Redis 3 │
│  SET    │  │  SET    │  │  SET    │
│  lock   │  │  lock   │  │  lock   │
└────┬────┘  └────┬────┘  └────┬────┘
     │            │            │
     └────────────┼────────────┘
                  │
            大多数成功 = 获取锁
```

## 代码实现

### Controller 层

```java
@PostMapping("/payments")
public ResponseEntity<PostPaymentResponse> processPayment(
    @RequestBody PostPaymentRequest request,
    @RequestHeader("Merchant-Id") String merchantId,
    @RequestHeader("Idempotency-Key") String dedupeKey) {

    // 1. 尝试获取锁
    if (idempotencyService.acquireLock(merchantId, dedupeKey)) {
        try {
            // 2. 我们获得了锁，开始处理
            PostPaymentResponse response = process(request);
            idempotencyService.completeAndRelease(merchantId, dedupeKey, response);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            idempotencyService.completeAndRelease(merchantId, dedupeKey, errorResponse(e));
            throw e;
        }
    }

    // 3. 没获得锁，等待处理完成
    Optional<PostPaymentResponse> result = 
        idempotencyService.waitForResult(merchantId, dedupeKey, 30000);
    
    return result.map(r -> ResponseEntity.ok(r))
                 .orElse(ResponseEntity.status(409).build());
}
```

### 数据库层（最可靠）

```java
@Transactional
public PostPaymentResponse processWithIdempotency(String merchantId, String dedupeKey, ...) {
    // 1. 尝试插入 PROCESSING 记录
    int inserted = jdbcTemplate.update(
        "INSERT INTO idempotency_keys (merchant_id, dedupe_key, status) VALUES (?, ?, 'PROCESSING')",
        merchantId, dedupeKey
    );
    
    if (inserted == 0) {
        // 已存在，查询结果
        return jdbcTemplate.queryForObject(
            "SELECT response FROM idempotency_keys WHERE merchant_id = ? AND dedupe_key = ?",
            PostPaymentResponse.class, merchantId, dedupeKey
        );
    }
    
    try {
        // 2. 处理支付
        PostPaymentResponse response = processPayment(...);
        
        // 3. 更新为 COMPLETED
        jdbcTemplate.update(
            "UPDATE idempotency_keys SET status = 'COMPLETED', response = ? WHERE merchant_id = ? AND dedupe_key = ?",
            response, merchantId, dedupeKey
        );
        
        return response;
    } catch (Exception e) {
        // 4. 更新为 FAILED（可重试）
        jdbcTemplate.update(
            "UPDATE idempotency_keys SET status = 'FAILED' WHERE merchant_id = ? AND dedupe_key = ?",
            merchantId, dedupeKey
        );
        throw e;
    }
}
```

## 对比

| 方案 | 可靠性 | 性能 | 复杂度 | 适用场景 |
|-----|--------|------|--------|---------|
| **数据库唯一约束** | ⭐⭐⭐ | ⭐⭐ | ⭐⭐ | 强一致性要求 |
| **Redis SETNX** | ⭐⭐ | ⭐⭐⭐ | ⭐ | 单 Redis 实例 |
| **Redis RedLock** | ⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐ | 多 Redis 实例 |
| **Java 内存锁** | ⭐ | ⭐⭐⭐⭐ | ⭐ | 单实例演示 |

## 面试回答

> "并发幂等性通过分布式锁保证。数据库唯一约束最可靠，插入时如果已存在就返回上次结果。Redis SETNX 适合高性能场景。关键是'先检查再处理'要变成原子操作，不能分成两步。"
