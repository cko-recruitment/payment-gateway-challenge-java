output "cluster_id" {
  description = "ACK cluster ID"
  value       = alicloud_cs_managed_kubernetes.main.id
}

output "cluster_name" {
  description = "ACK cluster name"
  value       = alicloud_cs_managed_kubernetes.main.name
}

output "vpc_id" {
  description = "VPC ID"
  value       = alicloud_vpc.main.id
}

output "configure_kubectl" {
  description = "Command to configure kubectl"
  value       = "aliyun cs GET /k8s/${alicloud_cs_managed_kubernetes.main.id}/user_config"
}
