# RDS PostgreSQL Module for NexusOps

variable "name" {
  description = "Name prefix for resources"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "database_subnet_ids" {
  description = "Database subnet IDs"
  type        = list(string)
}

variable "engine_version" {
  description = "PostgreSQL engine version"
  type        = string
  default     = "16.2"
}

variable "instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.r6g.large"
}

variable "allocated_storage" {
  description = "Allocated storage in GB"
  type        = number
  default     = 100
}

variable "max_allocated_storage" {
  description = "Max allocated storage for autoscaling"
  type        = number
  default     = 500
}

variable "username" {
  description = "Master username"
  type        = string
  default     = "nexusops"
}

variable "password" {
  description = "Master password (use AWS Secrets Manager in production)"
  type        = string
  sensitive   = true
}

variable "multi_az" {
  description = "Enable Multi-AZ deployment"
  type        = bool
  default     = true
}

variable "backup_retention_period" {
  description = "Backup retention period in days"
  type        = number
  default     = 30
}

variable "backup_window" {
  description = "Backup window (UTC)"
  type        = string
  default     = "03:00-04:00"
}

variable "maintenance_window" {
  description = "Maintenance window (UTC)"
  type        = string
  default     = "sun:04:00-sun:05:00"
}

variable "performance_insights_enabled" {
  description = "Enable Performance Insights"
  type        = bool
  default     = true
}

variable "deletion_protection" {
  description = "Enable deletion protection"
  type        = bool
  default     = true
}

variable "common_tags" {
  description = "Common tags for all resources"
  type        = map(string)
  default     = {}
}

# DB Subnet Group
resource "aws_db_subnet_group" "main" {
  name       = "${var.name}-db-subnet-group"
  subnet_ids = var.database_subnet_ids

  tags = merge(
    { Name = "${var.name}-db-subnet-group" },
    var.common_tags
  )
}

# Security Group for RDS
resource "aws_security_group" "rds" {
  name        = "${var.name}-rds-sg"
  description = "Security group for RDS PostgreSQL"
  vpc_id      = var.vpc_id

  ingress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    security_groups = [aws_security_group.eks_workers.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(
    { Name = "${var.name}-rds-sg" },
    var.common_tags
  )
}

# EKS Workers Security Group (reference)
data "aws_security_group" "eks_workers" {
  name   = "${var.name}-eks-node-group-sg"
  vpc_id = var.vpc_id
}

# RDS Instance
resource "aws_db_instance" "main" {
  identifier              = "${var.name}-postgres"
  engine                  = "postgres"
  engine_version          = var.engine_version
  instance_class          = var.instance_class
  username                = var.username
  password                = var.password
  allocated_storage       = var.allocated_storage
  max_allocated_storage   = var.max_allocated_storage
  storage_type            = "gp3"
  storage_encrypted       = true
  kms_key_id              = aws_kms_key.rds.arn
  db_subnet_group_name    = aws_db_subnet_group.main.name
  vpc_security_group_ids  = [aws_security_group.rds.id]
  backup_retention_period = var.backup_retention_period
  backup_window           = var.backup_window
  maintenance_window      = var.maintenance_window
  multi_az                = var.multi_az
  publicly_accessible     = false
  deletion_protection     = var.deletion_protection
  performance_insights_enabled = var.performance_insights_enabled
  performance_insights_kms_key_id = aws_kms_key.rds.arn

  # PostgreSQL parameters
  parameter_group_name = aws_db_parameter_group.main.name

  # Monitoring
  monitoring_interval     = 60
  monitoring_role_arn     = aws_iam_role.rds_monitoring.arn

  # CloudWatch Logs
  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]

  tags = merge(
    { Name = "${var.name}-postgres" },
    var.common_tags
  )
}

# DB Parameter Group
resource "aws_db_parameter_group" "main" {
  name        = "${var.name}-postgres-params"
  family      = "postgres16"
  description = "Parameter group for NexusOps PostgreSQL"

  parameter {
    name  = "shared_preload_libraries"
    value = "pg_stat_statements,auto_explain"
  }

  parameter {
    name  = "pg_stat_statements.track"
    value = "all"
  }

  parameter {
    name  = "auto_explain.log_min_duration"
    value = "1000"
  }

  parameter {
    name  = "auto_explain.log_analyze"
    value = "true"
  }

  parameter {
    name  = "log_statement"
    value = "ddl"
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "1000"
  }

  tags = var.common_tags
}

# KMS Key for RDS Encryption
resource "aws_kms_key" "rds" {
  description             = "KMS key for RDS encryption"
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
        Sid    = "Allow RDS to use the key"
        Effect = "Allow"
        Principal = {
          Service = "rds.amazonaws.com"
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
    { Name = "${var.name}-rds-kms" },
    var.common_tags
  )
}

# IAM Role for RDS Monitoring
resource "aws_iam_role" "rds_monitoring" {
  name = "${var.name}-rds-monitoring-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "monitoring.rds.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "rds_monitoring" {
  role       = aws_iam_role.rds_monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

# Read Replica (for production)
resource "aws_db_instance" "replica" {
  count = var.multi_az ? 1 : 0

  identifier                   = "${var.name}-postgres-replica"
  replicate_source_db          = aws_db_instance.main.identifier
  instance_class               = var.instance_class
  storage_type                 = "gp3"
  monitoring_interval          = 60
  monitoring_role_arn          = aws_iam_role.rds_monitoring.arn
  enabled_cloudwatch_logs_exports = ["postgresql"]

  tags = merge(
    { Name = "${var.name}-postgres-replica" },
    var.common_tags
  )
}

data "aws_caller_identity" "current" {}

# Outputs
output "db_instance_id" {
  value = aws_db_instance.main.id
}

output "db_instance_arn" {
  value = aws_db_instance.main.arn
}

output "db_instance_endpoint" {
  value = aws_db_instance.main.endpoint
}

output "db_instance_port" {
  value = aws_db_instance.main.port
}

output "db_instance_security_group_id" {
  value = aws_security_group.rds.id
}

output "replica_endpoint" {
  value = var.multi_az ? aws_db_instance.replica[0].endpoint : null
}