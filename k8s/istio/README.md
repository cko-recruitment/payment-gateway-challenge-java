# Service Mesh (Istio)

## 什么是 Service Mesh

```
传统方式：服务自己处理通信
┌─────────┐      ┌─────────┐      ┌─────────┐
│ Service │←────→│ Service │←────→│ Service │
│   A     │  TLS │   B     │ Retry│   C     │
│         │Circuit│         │Timeout│        │
│ + SDK   │Breaker│ + SDK   │      │ + SDK  │
└─────────┘      └─────────┘      └─────────┘
问题：每个服务都要集成 SDK，语言绑定，升级困难

Service Mesh：通信逻辑下沉到 Sidecar
┌─────────┐      ┌─────────┐      ┌─────────┐
│ Service │      │ Service │      │ Service │
│   A     │      │   B     │      │   C     │
│ (纯业务) │      │ (纯业务) │      │ (纯业务) │
└────┬────┘      └────┬────┘      └────┬────┘
     │                │                │
┌────┴────┐      ┌────┴────┐      ┌────┴────┐
│  Envoy  │←────→│  Envoy  │←────→│  Envoy  │
│ Sidecar │ mTLS │ Sidecar │Retry │ Sidecar │
│(TLS/   │      │(Auth/   │      │(Metrics/│
│Metrics/ │      │Metrics/ │      │Tracing) │
│Tracing) │      │Tracing) │      │         │
└─────────┘      └─────────┘      └─────────┘
优势：业务代码零侵入，统一管控
```

## Istio 核心组件

```
┌─────────────────────────────────────────┐
│           Istio Control Plane            │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐ │
│  │  Pilot  │  │ Citadel │  │ Galley  │ │
│  │(服务发现 │  │(证书管理)│  │(配置验证)│ │
│  │ 路由规则)│  │         │  │         │ │
│  └────┬────┘  └────┬────┘  └─────────┘ │
│       │            │                    │
│       └────────────┼────────────────────┘
│                    │ 下发配置
│                    ▼
│  ┌─────────────────────────────────────┐
│  │         Data Plane (Envoy)           │
│  │  ┌─────┐  ┌─────┐  ┌─────┐         │
│  │  │Pod A│  │Pod B│  │Pod C│         │
│  │  │+Envoy│  │+Envoy│  │+Envoy│        │
│  │  └─────┘  └─────┘  └─────┘         │
│  └─────────────────────────────────────┘
└─────────────────────────────────────────┘
```

## 核心功能

| 功能 | 说明 | 配置示例 |
|-----|------|---------|
| **流量管理** | 路由、分流、灰度 | VirtualService |
| **安全** | mTLS、认证、授权 | PeerAuthentication |
| **观测** | 指标、日志、追踪 | Telemetry |
| **弹性** | 熔断、重试、超时 | DestinationRule |

## 流量管理

```yaml
# VirtualService: 路由规则
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: payment-gateway
spec:
  hosts:
    - payment-gateway
  http:
    # 金丝雀发布：10% 流量到新版本
    - match:
        - headers:
            x-canary:
              exact: "true"
      route:
        - destination:
            host: payment-gateway
            subset: canary
          weight: 100
    # 正常流量按比例分配
    - route:
        - destination:
            host: payment-gateway
            subset: stable
          weight: 90
        - destination:
            host: payment-gateway
            subset: canary
          weight: 10
```

## 安全（mTLS）

```yaml
# PeerAuthentication: 强制双向 TLS
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: payment-gateway
spec:
  mtls:
    mode: STRICT  # 强制 mTLS

# 效果：
# Pod A ↔ Pod B 自动加密
# 无需应用代码修改
```

## 观测（自动埋点）

```yaml
# Telemetry: 自动收集指标
apiVersion: telemetry.istio.io/v1alpha1
kind: Telemetry
metadata:
  name: payment-gateway
spec:
  metrics:
    - providers:
        - name: prometheus
      overrides:
        - match:
            metric: REQUEST_COUNT
          tagOverrides:
            destination_service:
              value: "payment-gateway"
```

## 弹性（熔断）

```yaml
# DestinationRule: 熔断配置
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: payment-gateway
spec:
  host: payment-gateway
  trafficPolicy:
    connectionPool:
      tcp:
        maxConnections: 100
      http:
        http1MaxPendingRequests: 50
    outlierDetection:
      consecutive5xxErrors: 5    # 5 次错误触发熔断
      interval: 30s              # 检测间隔
      baseEjectionTime: 30s      # 熔断时间
```

## 与 Nginx 对比

| | Nginx Ingress | Istio Service Mesh |
|--|--------------|-------------------|
| **层级** | 入口网关（南北向） | 服务网格（南北+东西向） |
| **范围** | 集群入口 | 所有服务间通信 |
| **侵入性** | 无 | 无（Sidecar 自动注入） |
| **功能** | 路由、SSL、限流 | + mTLS、熔断、追踪、金丝雀 |
| **性能** | 高 | 中（Sidecar 开销） |
| **复杂度** | 低 | 高 |

## 面试回答

> "Service Mesh 是管理微服务通信的基础设施层。Istio 通过 Sidecar（Envoy）代理实现流量管理、安全加密、观测埋点，业务代码零侵入。相比 Nginx 只做入口网关，Istio 管理所有服务间调用，实现统一的流量控制和安全策略。"
