# Global Deployment Architecture

## 跨境部署核心挑战

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   中国用户       │     │   欧洲用户       │     │   美国用户       │
│                 │     │                 │     │                 │
│  Aliyun 北京     │     │  AWS Frankfurt  │     │  AWS us-east-1  │
│  ├── 数据合规    │     │  ├── GDPR       │     │  ├── PCI DSS    │
│  ├── 低延迟      │     │  ├── 数据主权    │     │  ├── 低延迟      │
│  └── 本地化支付  │     │  └── 本地化支付  │     │  └── 本地化支付  │
│                 │     │                 │     │                 │
│  数据不出境 ✓    │     │  数据不出境 ✓    │     │  数据不出境 ✓    │
└─────────────────┘     └─────────────────┘     └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────┐
                    │  全球 DNS    │
                    │ (Route53/    │
                    │  Cloudflare) │
                    └─────────────┘
```

## 1. 数据合规与主权

| 地区 | 法规 | 要求 |
|-----|------|------|
| 中国 | 网络安全法、数据安全法、个人信息保护法 | 数据本地化存储，跨境传输需审批 |
| 欧盟 | GDPR | 数据主体权利，跨境传输需 SCC |
| 美国 | 州级法律（CCPA/CPRA） | 消费者数据权利 |
| 金融 | PCI DSS | 支付卡数据保护 |

### 解决方案

```yaml
# K8s: 数据分类标签
apiVersion: v1
kind: ConfigMap
metadata:
  name: data-classification
  namespace: payment-gateway
data:
  policy: |
    personal_data: LOCAL_ONLY      # 不出境
    transaction_logs: REGIONAL     # 区域内
    analytics: GLOBAL              # 可汇总
```

## 2. 多区域数据同步

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  北京 Region │     │  Frankfurt  │     │  US East    │
│             │     │             │     │             │
│ ┌─────────┐ │     │ ┌─────────┐ │     │ ┌─────────┐ │
│ │ Primary │ │     │ │ Primary │ │     │ │ Primary │ │
│ │  MySQL  │ │     │ │  MySQL  │ │     │ │  MySQL  │ │
│ └────┬────┘ │     │ └────┬────┘ │     │ └────┬────┘ │
│      │      │     │      │      │     │      │      │
│      ▼      │     │      ▼      │     │      ▼      │
│ ┌─────────┐ │     │ ┌─────────┐ │     │ ┌─────────┐ │
│ │ Replica │ │◄────┼►│ Replica │ │◄────┼►│ Replica │ │
│ │ (异步)   │ │     │ │ (异步)   │ │     │ │ (异步)   │ │
│ └─────────┘ │     │ └─────────┘ │     │ └─────────┘ │
└─────────────┘     └─────────────┘     └─────────────┘
       │                     │                     │
       └─────────────────────┼─────────────────────┘
                             │
                    ┌─────────┐
                    │ Kafka /  │
                    │ Kinesis │
                    │ (CDC)    │
                    └─────────┘
```

### 异步复制策略

| 数据类型 | 同步方式 | 延迟容忍 |
|---------|---------|---------|
| 用户账户 | 强同步 | < 100ms |
| 交易记录 | 异步 CDC | < 5s |
| 审计日志 | 批量同步 | < 1h |
| 分析数据 | ETL 管道 | < 24h |

## 3. 全球负载均衡

```
                        用户请求
                           │
                    ┌──────┴──────┐
                    │   Geo DNS    │
                    │ ( latency-   │
                    │   based )    │
                    └──────┬──────┘
                           │
         ┌─────────────────┼─────────────────┐
         │                 │                 │
         ▼                 ▼                 ▼
   ┌──────────┐    ┌──────────┐    ┌──────────┐
   │ 北京 Edge │    │ 欧洲 Edge │    │ 美国 Edge │
   │  + WAF   │    │  + WAF   │    │  + WAF   │
   └────┬─────┘    └────┬─────┘    └────┬─────┘
        │               │               │
        ▼               ▼               ▼
   ┌──────────┐    ┌──────────┐    ┌──────────┐
   │ 北京 K8s │    │ 欧洲 K8s │    │ 美国 K8s │
   └──────────┘    └──────────┘    └──────────┘
```

### Terraform 多区域部署

```hcl
# terraform/global/main.tf

# 北京 Region
module "china" {
  source = "../modules/region"
  
  providers = {
    alicloud = alicloud.cn-beijing
  }
  
  region       = "cn-beijing"
  cluster_name = "payment-gateway-china"
  
  # 数据合规：仅本地存储
  data_residency = "LOCAL_ONLY"
}

# 法兰克福 Region
module "eu" {
  source = "../modules/region"
  
  providers = {
    aws = aws.eu-central-1
  }
  
  region       = "eu-central-1"
  cluster_name = "payment-gateway-eu"
  
  # GDPR 合规
  data_residency = "GDPR"
}

# 美国 Region
module "us" {
  source = "../modules/region"
  
  providers = {
    aws = aws.us-east-1
  }
  
  region       = "us-east-1"
  cluster_name = "payment-gateway-us"
  
  data_residency = "PCI_DSS"
}

# 全球 DNS
resource "aws_route53_record" "global" {
  zone_id = aws_route53_zone.main.zone_id
  name    = "api.payment-gateway.com"
  type    = "A"
  
  latency_routing_policy {
    region = "cn-beijing"
  }
  
  alias {
    name                   = module.china.alb_dns
    zone_id                = module.china.alb_zone_id
    evaluate_target_health = true
  }
}
```

## 4. 跨境网络优化

```
┌─────────────┐                    ┌─────────────┐
│  中国 Region │                    │  海外 Region │
│             │                    │             │
│ ┌─────────┐ │   ┌──────────┐    │ ┌─────────┐ │
│ │  App    │ │◄──┤ 专线/CEN ├───►│ │  App    │ │
│ └────┬────┘ │   │ 跨境加速  │    │ └────┬────┘ │
│      │      │   └──────────┘    │      │      │
│      ▼      │                    │      ▼      │
│ ┌─────────┐ │                    │ ┌─────────┐ │
│ │ 数据库   │ │                    │ │ 数据库   │ │
│ │ (本地)   │ │                    │ │ (本地)   │ │
│ └─────────┘ │                    │ └─────────┘ │
└─────────────┘                    └─────────────┘
```

### 网络方案

| 场景 | 方案 | 延迟 |
|-----|------|------|
| 同区域 | VPC 内网 | < 1ms |
| 跨区域（同云） | 云厂商骨干网 | 30-100ms |
| 跨境（中国-海外） | 专线/CEN/Express Connect | 50-150ms |
| 公网 | Internet + CDN | 100-300ms |

## 5. 多区域监控

```yaml
# Prometheus Federation
# 每个区域独立 Prometheus，汇总到 Global

global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'federate'
    honor_labels: true
    metrics_path: '/federate'
    params:
      match[]:
        - '{__name__=~"payment_.*"}'
    static_configs:
      - targets:
        - 'prometheus-china:9090'
        - 'prometheus-eu:9090'
        - 'prometheus-us:9090'
```

## 6. 灾难恢复

| RTO | RPO | 策略 |
|-----|-----|------|
| < 1h | < 5min | 同城双活 |
| < 4h | < 1h | 异地热备 |
| < 24h | < 24h | 冷备份 |

```hcl
# Terraform: 多可用区部署
module "eks" {
  source = "terraform-aws-modules/eks/aws"
  
  cluster_name = "payment-gateway"
  
  # 跨 3 个可用区
  subnet_ids = [
    aws_subnet.az1.id,
    aws_subnet.az2.id,
    aws_subnet.az3.id
  ]
  
  # 节点组分布
  eks_managed_node_groups = {
    default = {
      min_size     = 3  # 每个 AZ 至少 1 个
      max_size     = 10
      desired_size = 3
      
      # 跨 AZ 分布
      subnet_ids = [
        aws_subnet.az1.id,
        aws_subnet.az2.id,
        aws_subnet.az3.id
      ]
    }
  }
}
```

## 面试回答

> "全球部署的核心挑战是数据合规、低延迟和一致性。我们通过：
> 1. 数据本地化（中国数据存中国，欧盟数据存欧盟）
> 2. 异步复制（交易记录实时同步，分析数据批量同步）
> 3. Geo DNS 路由用户到最近区域
> 4. 跨境专线优化网络延迟
> 5. 多可用区保证高可用"
