# Redis Session Storage

## Why Redis for Sessions

| Problem | Solution |
|---------|---------|
| Pod restart → session lost | Redis persists sessions |
| Scale out → session not shared | All pods read from same Redis |
| Canary release → user jumps between versions | Session stays in Redis |

## Architecture

```
User Request
     │
     ▼
┌─────────┐     ┌─────────┐
│  Pod 1  │←──→│  Pod 2  │
│ + Redis │     │ + Redis │
│  Client │     │  Client │
└────┬────┘     └────┬────┘
     │               │
     └───────┬───────┘
             │
        ┌─────────┐
        │  Redis  │
        │ (Session│
        │ Storage)│
        └─────────┘
```

## Configuration

### Spring Boot

```yaml
spring:
  redis:
    host: redis
    port: 6379
  session:
    store-type: redis
    timeout: 30m
```

### K8s Deployment

```bash
kubectl apply -f k8s/redis/redis-deployment.yaml
```

## Session Flow

1. User login → session saved to Redis
2. Subsequent requests → any pod reads session from Redis
3. Pod restart → session still in Redis
4. Scale out → new pods access same sessions

## Interview Answer

> "Session data is stored in Redis, not in pod memory. This ensures sessions survive pod restarts and are shared across all pods. Even with canary releases or scaling, user sessions remain consistent."
