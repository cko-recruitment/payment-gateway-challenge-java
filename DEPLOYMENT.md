# Deployment Options Comparison

## 1. 容器化部署（当前方案）

```
┌─────────────────────────────────────┐
│           Kubernetes (K8s)           │
│  ┌─────────┐  ┌─────────┐          │
│  │ Pod 1   │  │ Pod 2   │          │
│  │ Spring  │  │ Spring  │          │
│  │ Boot    │  │ Boot    │          │
│  │ + Envoy │  │ + Envoy │          │
│  └─────────┘  └─────────┘          │
│       ↑              ↑              │
│    Service Mesh (Istio)             │
└─────────────────────────────────────┘
```

**特点：**
- 控制力强，资源可预测
- 需要管理 K8s 集群
- 适合长期运行的服务

---

## 2. Serverless（函数计算）

```
┌─────────────────────────────────────┐
│        Function Compute              │
│  ┌─────────────────────────────┐    │
│  │   API Gateway                 │    │
│  │     ↓                         │    │
│  │   ┌─────────┐  ┌─────────┐   │    │
│  │   │ Func A  │  │ Func B  │   │    │
│  │   │ (支付)   │  │ (查询)   │   │    │
│  │   │ 冷启动   │  │ 冷启动   │   │    │
│  │   └─────────┘  └─────────┘   │    │
│  │        ↑ 按需启动，按调用计费    │    │
│  └─────────────────────────────┘    │
└─────────────────────────────────────┘
```

**特点：**
- 无服务器管理，自动扩缩容
- 冷启动延迟（Java 300ms-3s）
- 按调用次数计费
- 适合事件驱动、流量波动大

**Serverless 配置示例：**

```yaml
# Aliyun Function Compute
apiVersion: fc.alibabacloud.com/v1
kind: Function
metadata:
  name: payment-processor
spec:
  runtime: java17
  handler: com.checkout.PaymentHandler::handleRequest
  memorySize: 512
  timeout: 30
  triggers:
    - type: http
      config:
        authType: anonymous
        methods: [POST]
```

---

## 3. 对比

| 特性 | K8s 容器 | Serverless |
|-----|---------|-----------|
| **运维** | 需要管理节点 | 零运维 |
| **成本** | 按资源计费（24h） | 按调用计费 |
| **冷启动** | 无 | Java 300ms-3s |
| **扩容** | 秒级 | 毫秒级 |
| **控制力** | 强（CPU/内存/网络） | 弱（只能配内存） |
| **适用** | 长期运行、低延迟 | 事件驱动、流量波动 |

---

## 4. 混合方案（推荐）

```
┌─────────────────────────────────────────┐
│            用户请求                      │
│               ↓                         │
│        ┌─────────┐                      │
│        │  API GW  │                     │
│        └────┬────┘                      │
│             │                           │
│    ┌────────┼────────┐                  │
│    │        │        │                  │
│    ▼        ▼        ▼                  │
│ ┌──────┐ ┌──────┐ ┌──────────┐         │
│ │ K8s  │ │ K8s  │ │Function  │         │
│ │核心支付│ │ 查询  │ │ 异步通知  │         │
│ │(低延迟)│ │(缓存) │ │ (Webhook)│         │
│ └──────┘ └──────┘ └──────────┘         │
│  长期运行   长期运行    事件触发           │
└─────────────────────────────────────────┘
```

**分配原则：**

| 服务 | 部署方式 | 原因 |
|-----|---------|------|
| 支付核心 | K8s | 低延迟、高可用、需要 Sidecar |
| 交易查询 | K8s + Redis | 缓存命中率高 |
| 异步通知 | Serverless | 事件驱动，流量不均 |
| 报表生成 | Serverless | 定时任务，非实时 |
| 风控校验 | K8s | 复杂逻辑，需要本地缓存 |

---

## 5. 面试回答

> "我们的核心支付服务用 K8s 部署，保证低延迟和高可用。异步任务（如通知、报表）用 Serverless，降低成本。Service Mesh 管理 K8s 服务间通信，Serverless 通过 API Gateway 接入。"

## 6. Terraform 配置

```hcl
# K8s 部署（核心服务）
resource "kubernetes_deployment" "payment" {
  metadata {
    name = "payment-gateway"
  }
  spec {
    replicas = 3
    # ...
  }
}

# Serverless 部署（异步任务）
resource "alicloud_fc_function" "notifier" {
  service_name = "payment-service"
  name         = "async-notifier"
  runtime      = "java17"
  handler      = "com.checkout.NotifierHandler::handleRequest"
  memory_size  = 512
}
```
