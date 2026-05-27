# Payment Gateway - Terraform for Aliyun
# Minimal viable setup for demo purpose

terraform {
  required_version = ">= 1.5.0"
  required_providers {
    alicloud = {
      source  = "aliyun/alicloud"
      version = "~> 1.220"
    }
  }
}

provider "alicloud" {
  region = var.aliyun_region
  # Credentials loaded from:
  # 1. Environment variables (ALICLOUD_ACCESS_KEY, ALICLOUD_SECRET_KEY)
  # 2. Aliyun CLI config (~/.aliyun/config.json)
  # 3. RAM Role (when running on ECS)
}

# VPC - 专有网络
resource "alicloud_vpc" "main" {
  vpc_name   = "${var.project_name}-vpc"
  cidr_block = "10.0.0.0/16"
}

# VSwitch - 交换机（相当于 AWS Subnet）
resource "alicloud_vswitch" "public_a" {
  vswitch_name = "${var.project_name}-public-a"
  vpc_id       = alicloud_vpc.main.id
  cidr_block   = "10.0.101.0/24"
  zone_id      = "${var.aliyun_region}-a"
}

resource "alicloud_vswitch" "private_a" {
  vswitch_name = "${var.project_name}-private-a"
  vpc_id       = alicloud_vpc.main.id
  cidr_block   = "10.0.1.0/24"
  zone_id      = "${var.aliyun_region}-a"
}

# NAT Gateway - 用于私有网络访问外网
resource "alicloud_nat_gateway" "main" {
  vpc_id           = alicloud_vpc.main.id
  nat_gateway_name = "${var.project_name}-nat"
  payment_type     = "PayAsYouGo"
  vswitch_id       = alicloud_vswitch.public_a.id
  nat_type         = "Enhanced"
}

# ACK - 容器服务 Kubernetes（相当于 AWS EKS）
resource "alicloud_cs_managed_kubernetes" "main" {
  name                  = "${var.project_name}-cluster"
  cluster_spec          = "ack.pro.small"
  version               = "1.28"
  vpc_id                = alicloud_vpc.main.id
  vswitch_ids           = [alicloud_vswitch.private_a.id]
  new_nat_gateway       = false
  pod_cidr              = "172.20.0.0/16"
  service_cidr          = "172.21.0.0/20"
  enable_rrsa           = true

  worker_vswitch_ids    = [alicloud_vswitch.private_a.id]
  worker_instance_types = ["ecs.g7.large"]
  worker_number         = 1
}
