variable "aliyun_region" {
  description = "Aliyun region"
  type        = string
  default     = "cn-hangzhou"
}

variable "project_name" {
  description = "Project name"
  type        = string
  default     = "payment-gateway"
}

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "dev"
}
