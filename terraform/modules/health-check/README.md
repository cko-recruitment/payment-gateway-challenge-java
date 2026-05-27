# Multi-Cloud Health Check Module

统一的健康检查方案，适用于 AWS 和 Aliyun。

## 三层健康检查

| 层级 | 作用 | 云厂商实现 |
|-----|------|-----------|
| **Load Balancer** | 流量入口健康检查 | AWS ALB / Aliyun SLB |
| **K8s Probes** | Pod 级别健康检查 | 通用（readiness/liveness/startup） |
| **Application** | 业务健康检查 | Spring Boot Actuator |

## 健康检查端点

```
/actuator/health           # 综合健康（startup probe 用）
/actuator/health/readiness # 就绪检查（接收流量）
/actuator/health/liveness  # 存活检查（重启判断）
```

## 使用方式

```hcl
module "health_check" {
  source = "./modules/health-check"

  cloud_provider = "aliyun"  # 或 "aws"
  project_name   = "payment-gateway"
  image_tag      = "payment-gateway:v1.2.3"
  
  # AWS 专用
  vpc_id = module.vpc.vpc_id
  
  # Aliyun 专用
  load_balancer_id   = alicloud_slb.main.id
  ssl_certificate_id = alicloud_slb_server_certificate.main.id
}
```

## Probe 配置

| Probe | 用途 | 初始延迟 | 间隔 | 失败阈值 |
|-------|------|---------|------|---------|
| startup | 启动完成 | 10s | 5s | 30（最大 300s） |
| readiness | 接收流量 | 30s | 10s | 3 |
| liveness | 存活状态 | 60s | 20s | 3 |

## 面试回答

> "健康检查分三层：LB 层检查实例健康，K8s 层检查 Pod 就绪和存活，应用层通过 Actuator 暴露详细状态。多云环境下 K8s probes 是通用的，LB 健康检查根据云厂商配置。"
