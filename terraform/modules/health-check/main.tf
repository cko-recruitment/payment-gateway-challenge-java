# Multi-Cloud Health Check Module
# Works with AWS ALB / Aliyun SLB health checks + K8s probes

# AWS: Application Load Balancer Health Check
resource "aws_lb_target_group" "app" {
  count    = var.cloud_provider == "aws" ? 1 : 0
  name     = "${var.project_name}-tg"
  port     = 8090
  protocol = "HTTP"
  vpc_id   = var.vpc_id
  target_type = "ip"

  health_check {
    enabled             = true
    healthy_threshold   = 2
    interval            = 30
    matcher             = "200"
    path                = "/actuator/health"
    port                = "traffic-port"
    protocol            = "HTTP"
    timeout             = 5
    unhealthy_threshold = 3
  }
}

# Aliyun: Server Load Balancer Health Check
resource "alicloud_slb_listener" "https" {
  count             = var.cloud_provider == "aliyun" ? 1 : 0
  load_balancer_id  = var.load_balancer_id
  backend_port      = 8090
  frontend_port     = 443
  protocol          = "https"
  ssl_certificate_id = var.ssl_certificate_id

  health_check {
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    uri                 = "/actuator/health"
    http_code           = "http_2xx"
  }
}

# K8s: Service with health-aware endpoints (cloud-agnostic)
resource "kubernetes_service" "app" {
  metadata {
    name      = var.project_name
    namespace = var.namespace
  }
  spec {
    selector = {
      app = var.project_name
    }
    port {
      port        = 80
      target_port = 8090
    }
    type = "ClusterIP"
  }
}

# K8s: Deployment with readiness/liveness probes (cloud-agnostic)
resource "kubernetes_deployment" "app" {
  metadata {
    name      = var.project_name
    namespace = var.namespace
  }
  spec {
    replicas = var.replicas
    selector {
      match_labels = {
        app = var.project_name
      }
    }
    template {
      metadata {
        labels = {
          app = var.project_name
        }
      }
      spec {
        container {
          name  = var.project_name
          image = var.image_tag
          port {
            container_port = 8090
          }
          # Readiness: pod 能接收流量
          readiness_probe {
            http_get {
              path = "/actuator/health/readiness"
              port = 8090
            }
            initial_delay_seconds = 30
            period_seconds        = 10
            timeout_seconds       = 5
            failure_threshold     = 3
          }
          # Liveness: pod 是否存活
          liveness_probe {
            http_get {
              path = "/actuator/health/liveness"
              port = 8090
            }
            initial_delay_seconds = 60
            period_seconds        = 20
            timeout_seconds       = 5
            failure_threshold     = 3
          }
          # Startup: 应用启动完成
          startup_probe {
            http_get {
              path = "/actuator/health"
              port = 8090
            }
            initial_delay_seconds = 10
            period_seconds        = 5
            failure_threshold     = 30  # 10 * 30 = 300s max startup
          }
        }
      }
    }
  }
}
