# Terraform - Payment Gateway Infrastructure

> ⚠️ **此配置仅用于面试演示，展示基础设施即代码（IaC）思路。**
> 
> 当前项目使用 Docker Compose + 本地 K8s 即可满足开发和测试需求。
> 真实业务上线时，根据用户地域和合规要求选择单一云厂商即可。

## 为什么不需要多云

| 问题 | 说明 |
|-----|------|
| 网络延迟 | 跨云访问增加 50-100ms 延迟 |
| 数据合规 | 数据出境需额外审批，单一云区域内即可满足 |
| 成本 | 两套基础设施，费用翻倍 |
| 运维 | 两套监控、日志、告警系统 |

**正确做法**：根据用户地域选择云厂商和区域
- 中国用户 → Aliyun 北京/上海
- 海外用户 → AWS Singapore/Frankfurt

## 架构

```
单一云厂商部署
┌─────────────────────────────────────┐
│              VPC                    │
│  ┌─────────────┐  ┌─────────────┐   │
│  │ Public Subnet│  │ Public Subnet│   │  ← ALB/NAT Gateway
│  └─────────────┘  └─────────────┘   │
│  ┌─────────────┐  ┌─────────────┐   │
│  │Private Subnet│  │Private Subnet│   │  ← EKS/ACK Nodes
│  └─────────────┘  └─────────────┘   │
│           ┌─────────┐               │
│           │  K8s    │               │
│           │ Cluster │               │
│           └─────────┘               │
└─────────────────────────────────────┘
```

## 目录结构

```
terraform/
├── aws/          # AWS EKS 版本
├── aliyun/       # Aliyun ACK 版本（二选一即可）
└── README.md
```

## 使用（演示目的）

```bash
cd terraform/aws  # 或 aliyun
terraform init
terraform plan
terraform apply
```

## 成本估算

| 云厂商 | 月费用 |
|--------|--------|
| AWS (t3.medium Spot) | ~$120 |
| Aliyun (ecs.g7.large) | ~¥800 |

## 面试回答示例

> "目前项目使用 Docker Compose 进行本地开发和测试。如果未来需要上云，我会根据用户地域和合规要求选择单一云厂商（中国用 Aliyun，海外用 AWS），用 Terraform 管理基础设施，避免多云带来的复杂性和成本问题。"
