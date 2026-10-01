# ElastiCache Redis Module for NexusOps

variable "name" {
  description = "Name prefix for resources"
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

variable "engine_version" {
  description = "Redis engine version"
  type        = string
  default     = "7.1"
}

variable "node_type" {
  description = "ElastiCache node type"
  type        = string
  default     = "cache.r6g.large"
}

variable "num_cache_nodes" {
  description = "Number of cache nodes (replicas + primary)"
  type        = number
  default     = 2
}

variable "automatic_failover_enabled" {
  description = "Enable automatic failover"
  type        = bool
  default     = true
}

variable "multi_az_enabled" {
  description = "Enable Multi-AZ"
  type        = bool
  default     = true
}

variable "at_rest_encryption_enabled" {
  description = "Enable at-rest encryption"
  type        = bool
  default     = true
}

variable "transit_encryption_enabled" {
  description = "Enable in-transit encryption"
  type        = bool
  default     = true
}

variable "auth_token" {
  description = "Redis AUTH token (use AWS Secrets Manager in production)"
  type        = string
  sensitive   = true
}

variable "common_tags" {
  description = "Common tags for all resources"
  type        = map(string)
  default     = {}
}

# Subnet Group
resource "aws_elasticache_subnet_group" "main" {
  name       = "${var.name}-elasticache-subnet-group"
  subnet_ids = var.private_subnet_ids

  tags = var.common_tags
}

# Security Group
resource "aws_security_group" "elasticache" {
  name        = "${var.name}-elasticache-sg"
  description = "Security group for ElastiCache Redis"
  vpc_id      = var.vpc_id

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [data.aws_security_group.eks_workers.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(
    { Name = "${var.name}-elasticache-sg" },
    var.common_tags
  )
}

data "aws_security_group" "eks_workers" {
  name   = "${var.name}-eks-node-group-sg"
  vpc_id = var.vpc_id
}

# Parameter Group
resource "aws_elasticache_parameter_group" "main" {
  name        = "${var.name}-redis-params"
  family      = "redis7"
  description = "Parameter group for NexusOps Redis"

  parameter {
    name  = "maxmemory-policy"
    value = "allkeys-lru"
  }

  parameter {
    name  = "timeout"
    value = "300"
  }

  parameter {
    name  = "tcp-keepalive"
    value = "60"
  }
}

# Replication Group
resource "aws_elasticache_replication_group" "main" {
  replication_group_id       = "${var.name}-redis"
  description                = "Redis replication group for NexusOps"
  engine                     = "redis"
  engine_version             = var.engine_version
  node_type                  = var.node_type
  number_cache_clusters      = var.num_cache_nodes
  automatic_failover_enabled = var.automatic_failover_enabled
  multi_az_enabled           = var.multi_az_enabled
  at_rest_encryption_enabled = var.at_rest_encryption_enabled
  transit_encryption_enabled = var.transit_encryption_enabled
  auth_token                 = var.auth_token
  parameter_group_name       = aws_elasticache_parameter_group.main.name
  subnet_group_name          = aws_elasticache_subnet_group.main.name
  security_group_ids         = [aws_security_group.elasticache.id]

  # Encryption at rest
  kms_key_id = aws_kms_key.elasticache.arn

  # CloudWatch Logs
  log_delivery_configuration {
    destination_type = "cloudwatch-logs"
    log_type         = "slow-log"
    destination_details {
      cloudwatch_logs {
        log_group = aws_cloudwatch_log_group.redis_slow.name
      }
    }
  }

  log_delivery_configuration {
    destination_type = "cloudwatch-logs"
    log_type         = "engine-log"
    destination_details {
      cloudwatch_logs {
        log_group = aws_cloudwatch_log_group.redis_engine.name
      }
    }
  }

  tags = merge(
    { Name = "${var.name}-redis" },
    var.common_tags
  )
}

# KMS Key for ElastiCache Encryption
resource "aws_kms_key" "elasticache" {
  description             = "KMS key for ElastiCache encryption"
  deletion_window_in_days = 10
  enable_key_rotation     = true

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "Allow administration of the key"
        Effect = "Allow"
        Principal = {
          AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root"
        }
        Action = ["kms:*"]
        Resource = "*"
      },
      {
        Sid    = "Allow ElastiCache to use the key"
        Effect = "Allow"
        Principal = {
          Service = "elasticache.amazonaws.com"
        }
        Action = [
          "kms:Encrypt",
          "kms:Decrypt",
          "kms:ReEncrypt*",
          "kms:GenerateDataKey*",
          "kms:DescribeKey"
        ]
        Resource = "*"
      }
    ]
  })

  tags = merge(
    { Name = "${var.name}-elasticache-kms" },
    var.common_tags
  )
}

# CloudWatch Log Groups
resource "aws_cloudwatch_log_group" "redis_slow" {
  name              = "/aws/elasticache/${var.name}/redis/slow"
  retention_in_days = 30
  tags              = var.common_tags
}

resource "aws_cloudwatch_log_group" "redis_engine" {
  name              = "/aws/elasticache/${var.name}/redis/engine"
  retention_in_days = 30
  tags              = var.common_tags
}

data "aws_caller_identity" "current" {}

# Outputs
output "replication_group_id" {
  value = aws_elasticache_replication_group.main.replication_group_id
}

output "primary_endpoint" {
  value = aws_elasticache_replication_group.main.primary_endpoint_address
}

output "reader_endpoint" {
  value = aws_elasticache_replication_group.main.reader_endpoint_address
}

output "port" {
  value = aws_elasticache_replication_group.main.port
}

output "security_group_id" {
  value = aws_security_group.elasticache.id
}