variable "cloud_provider" {
  description = "Cloud provider: aws or aliyun"
  type        = string
}

variable "project_name" {
  description = "Project name"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID (AWS only)"
  type        = string
  default     = ""
}

variable "load_balancer_id" {
  description = "Load Balancer ID (Aliyun only)"
  type        = string
  default     = ""
}

variable "ssl_certificate_id" {
  description = "SSL Certificate ID (Aliyun only)"
  type        = string
  default     = ""
}

variable "namespace" {
  description = "K8s namespace"
  type        = string
  default     = "payment-gateway"
}

variable "replicas" {
  description = "Number of replicas"
  type        = number
  default     = 2
}

variable "image_tag" {
  description = "Docker image tag"
  type        = string
}
