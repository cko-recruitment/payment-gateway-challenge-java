# 并发幂等性保证

## 核心问题

```
T1: 请求A ──► 检查不存在 ──► 开始处理
T2: 请求B ──► 检查不存在 ──► 开始处理  ← 并发冲突！
```

## 解决方案：数据库唯一约束（最简单）

```sql
CREATE TABLE idempotency_keys (
    merchant_id VARCHAR(64),
    dedupe_key VARCHAR(255),
    status VARCHAR(20),  -- PROCESSING / COMPLETED
    PRIMARY KEY (merchant_id, dedupe_key)  -- 唯一约束
);
```

```java
@Transactional
public PostPaymentResponse process(String merchantId, String dedupeKey, ...) {
    // 1. 原子插入，冲突说明已存在
    try {
        jdbcTemplate.update(
            "INSERT INTO idempotency_keys (merchant_id, dedupe_key, status) VALUES (?, ?, 'PROCESSING')",
            merchantId, dedupeKey
        );
    } catch (DuplicateKeyException e) {
        // 已存在，返回缓存结果
        return getCachedResult(merchantId, dedupeKey);
    }
    
    // 2. 处理支付
    PostPaymentResponse response = processPayment(...);
    
    // 3. 更新结果
    cacheResult(merchantId, dedupeKey, response);
    return response;
}
```

## 关键点

| 问题 | 解决 |
|-----|------|
| 并发冲突 | 数据库唯一约束 |
| 重复处理 | 插入失败直接返回 |
| 原子性 | 一条 SQL 搞定 |

## 面试回答

> "并发幂等性用数据库唯一约束保证。INSERT 时如果主键冲突，说明已有请求在处理，直接返回缓存结果。一条 SQL 实现原子性检查和处理。"
