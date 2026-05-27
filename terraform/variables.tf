variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "eu-west-1"
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
