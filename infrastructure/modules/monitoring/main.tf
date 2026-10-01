# Monitoring Module for NexusOps

variable "name" {
  description = "Name prefix for resources"
  type        = string
}

variable "cluster_name" {
  description = "EKS cluster name"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "private_subnet_ids" {
  description = "Private subnet IDs"
  type        = list(string)
}

variable "common_tags" {
  description = "Common tags for all resources"
  type        = map(string)
  default     = {}
}

# CloudWatch Log Groups for EKS
resource "aws_cloudwatch_log_group" "eks_control_plane" {
  name              = "/aws/eks/${var.cluster_name}/controlplane"
  retention_in_days = 30
  tags              = var.common_tags
}

# CloudWatch Metric Alarms for EKS
resource "aws_cloudwatch_metric_alarm" "eks_high_cpu" {
  alarm_name          = "${var.name}-eks-high-cpu"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "EKS node CPU utilization is high"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    AutoScalingGroupName = "${var.cluster_name}-nodegroup-*"
  }

  tags = var.common_tags
}

resource "aws_cloudwatch_metric_alarm" "eks_high_memory" {
  alarm_name          = "${var.name}-eks-high-memory"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "MemoryUtilization"
  namespace           = "AWS/EC2"
  period              = 300
  statistic           = "Average"
  threshold           = 85
  alarm_description   = "EKS node memory utilization is high"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    AutoScalingGroupName = "${var.cluster_name}-nodegroup-*"
  }

  tags = var.common_tags
}

# CloudWatch Metric Alarms for RDS
resource "aws_cloudwatch_metric_alarm" "rds_high_cpu" {
  alarm_name          = "${var.name}-rds-high-cpu"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "RDS CPU utilization is high"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    DBInstanceIdentifier = "${var.name}-postgres"
  }

  tags = var.common_tags
}

resource "aws_cloudwatch_metric_alarm" "rds_low_storage" {
  alarm_name          = "${var.name}-rds-low-storage"
  comparison_operator = "LessThanThreshold"
  evaluation_periods  = 1
  metric_name         = "FreeStorageSpace"
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = "Average"
  threshold           = 10737418240 # 10GB
  alarm_description   = "RDS free storage space is low"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    DBInstanceIdentifier = "${var.name}-postgres"
  }

  tags = var.common_tags
}

# CloudWatch Metric Alarms for ElastiCache
resource "aws_cloudwatch_metric_alarm" "redis_high_cpu" {
  alarm_name          = "${var.name}-redis-high-cpu"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ElastiCache"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "Redis CPU utilization is high"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    CacheClusterId = "${var.name}-redis-001"
  }

  tags = var.common_tags
}

resource "aws_cloudwatch_metric_alarm" "redis_high_memory" {
  alarm_name          = "${var.name}-redis-high-memory"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "DatabaseMemoryUsagePercentage"
  namespace           = "AWS/ElastiCache"
  period              = 300
  statistic           = "Average"
  threshold           = 85
  alarm_description   = "Redis memory usage is high"
  alarm_actions       = [aws_sns_topic.alerts.arn]

  dimensions = {
    CacheClusterId = "${var.name}-redis-001"
  }

  tags = var.common_tags
}

# SNS Topic for Alerts
resource "aws_sns_topic" "alerts" {
  name = "${var.name}-alerts"
  tags = var.common_tags
}

resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

variable "alert_email" {
  description = "Email address for alerts"
  type        = string
}

# Outputs
output "alerts_topic_arn" {
  value = aws_sns_topic.alerts.arn
}