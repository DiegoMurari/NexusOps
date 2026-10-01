# ADR-009: AWS Infrastructure Strategy

## Status
Accepted

## Context
NexusOps requires AWS infrastructure that:
- Supports EKS, RDS, ElastiCache, ALB, S3, CloudFront
- Is secure, compliant, and cost-optimized
- Enables multi-environment (dev, staging, prod)
- Supports disaster recovery
- Uses Infrastructure as Code (Terraform)
- Follows AWS Well-Architected Framework

## Decision
Provision AWS resources using **Terraform modules** with **multi-account strategy**, **least privilege IAM**, and **security-first defaults**.

## Account Structure

### Multi-Account Strategy (AWS Organizations)
```
┌─────────────────────────────────────────────────────────────┐
│                    Organization Root                        │
├─────────────┬─────────────┬─────────────┬───────────────────┤
│  Security   │   Shared    │   Dev       │   Staging/Prod    │
│  (Audit,    │  Services   │             │                   │
│   Logs)     │  (DNS,      │  dev        │  staging          │
│             │   Certs)    │             │  prod             │
└─────────────┴─────────────┴─────────────┴───────────────────┘
```

| Account | Purpose | Key Resources |
|---------|---------|---------------|
| Security | Centralized logging, audit, security hub | CloudTrail, Config, Security Hub, GuardDuty |
| Shared Services | DNS, ACM certificates, shared VPC endpoints | Route53, ACM, Transit Gateway |
| Dev | Development environment | EKS dev, RDS dev, ElastiCache dev |
| Staging | Pre-production validation | EKS staging, RDS staging, ElastiCache staging |
| Prod | Production workloads | EKS prod, RDS prod, ElastiCache prod |

## Network Architecture

### VPC Design (Per Environment)
```
┌─────────────────────────────────────────────────────────────┐
│                      VPC (10.0.0.0/16)                      │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────┐ │
│  │ Public Subnet A  │  │ Public Subnet B  │  │ Public C   │ │
│  │ (10.0.0.0/20)    │  │ (10.0.16.0/20)   │  │ (10.0.32.0/20)    │ │
│  │ NAT Gateway      │  │ NAT Gateway      │  │ NAT Gateway│ │
│  │ ALB              │  │ ALB              │  │ ALB        │ │
│  └──────────────────┘  └──────────────────┘  └────────────┘ │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────┐ │
│  │ Private Subnet A │  │ Private Subnet B │  │ Private C  │ │
│  │ (10.0.128.0/20)  │  │ (10.0.144.0/20)  │  │ (10.0.160.0/20) │ │
│  │ EKS Nodes        │  │ EKS Nodes        │  │ EKS Nodes  │ │
│  └──────────────────┘  └──────────────────┘  └────────────┘ │
│  ┌──────────────────┐  ┌──────────────────┐  ┌────────────┐ │
│  │ DB Subnet A      │  │ DB Subnet B      │  │ DB Subnet C│ │
│  │ (10.0.192.0/20)  │  │ (10.0.208.0/20)  │  │ (10.0.224.0/20) │ │
│  │ RDS Primary      │  │ RDS Replica      │  │ ElastiCache│ │
│  └──────────────────┘  └──────────────────┘  └────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### CIDR Allocation
| Purpose | CIDR | Usable IPs |
|---------|------|------------|
| VPC | 10.0.0.0/16 | 65,536 |
| Public Subnets (3x /20) | 10.0.0.0/20, .16.0/20, .32.0/20 | 4,096 each |
| Private Subnets (3x /20) | 10.0.128.0/20, .144.0/20, .160.0/20 | 4,096 each |
| Database Subnets (3x /20) | 10.0.192.0/20, .208.0/20, .224.0/20 | 4,096 each |
| Reserved | 10.0.240.0/20 | 4,096 |

### VPC Endpoints (PrivateLink)
- S3 Gateway Endpoint
- DynamoDB Gateway Endpoint
- ECR Interface Endpoint (for image pulls)
- CloudWatch Logs Interface Endpoint
- Secrets Manager Interface Endpoint
- STS Interface Endpoint

## Compute (EKS)

### Cluster Configuration
```hcl
# EKS Cluster
resource "aws_eks_cluster" "main" {
  name     = "nexusops-${var.environment}"
  role_arn = aws_iam_role.cluster.arn
  version  = "1.29"

  vpc_config {
    subnet_ids              = var.private_subnet_ids
    endpoint_public_access  = true
    endpoint_private_access = true
    public_access_cidrs     = ["10.0.0.0/8", "office-cidr"]
  }

  encryption_config {
    provider {
      key_arn = aws_kms_key.eks.arn
    }
    resources = ["secrets"]
  }

  enabled_cluster_log_types = ["api", "audit", "authenticator", "controllerManager", "scheduler"]
}
```

### Node Groups (Managed)
```hcl
# System Node Group
resource "aws_eks_node_group" "system" {
  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "system"
  node_role_arn   = aws_iam_role.node_group.arn
  subnet_ids      = var.private_subnet_ids

  instance_types = ["m6i.large"]
  capacity_type  = "ON_DEMAND"

  scaling_config {
    min_size     = 2
    max_size     = 4
    desired_size = 2
  }

  labels = {
    "node-type" = "system"
  }

  taints {
    key    = "system"
    value  = "true"
    effect = "NO_SCHEDULE"
  }
}
```

### IRSA (IAM Roles for Service Accounts)
```hcl
# OIDC Provider
resource "aws_iam_openid_connect_provider" "eks" {
  url             = aws_eks_cluster.main.identity[0].oidc[0].issuer
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["9e99a48a9960b14926bb7f3b02e22da2b0ab7280"]
}

# Service Account Role (Example: External Secrets)
resource "aws_iam_role" "external_secrets" {
  name = "nexusops-${var.environment}-external-secrets"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Federated = aws_iam_openid_connect_provider.eks.arn
      }
      Action = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${aws_iam_openid_connect_provider.eks.url}:sub" = "system:serviceaccount:nexusops:external-secrets"
        }
      }
    }]
  })
}
```

## Database (RDS PostgreSQL)

### Primary Instance
```hcl
resource "aws_db_instance" "main" {
  identifier        = "nexusops-${var.environment}-postgres"
  engine            = "postgres"
  engine_version    = "16.2"
  instance_class    = var.environment == "prod" ? "db.r6g.large" : "db.t3.medium"
  
  username          = "nexusops"
  password          = random_password.db_password.result
  
  allocated_storage     = var.environment == "prod" ? 200 : 50
  max_allocated_storage = var.environment == "prod" ? 1000 : 100
  storage_type          = "gp3"
  storage_encrypted     = true
  kms_key_id            = aws_kms_key.rds.arn
  
  db_subnet_group_name  = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  
  backup_retention_period = 30
  backup_window           = "03:00-04:00"
  maintenance_window      = "sun:04:00-sun:05:00"
  
  multi_az               = var.environment == "prod"
  deletion_protection    = var.environment == "prod"
  performance_insights_enabled = true
  
  monitoring_interval     = 60
  monitoring_role_arn     = aws_iam_role.rds_monitoring.arn
  
  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
}
```

### Parameter Group
```hcl
resource "aws_db_parameter_group" "main" {
  name        = "nexusops-${var.environment}-postgres-params"
  family      = "postgres16"
  description = "NexusOps PostgreSQL parameters"

  parameter {
    name  = "shared_preload_libraries"
    value = "pg_stat_statements,auto_explain"
  }
  parameter {
    name  = "pg_stat_statements.track"
    value = "all"
  }
  parameter {
    name  = "log_statement"
    value = "ddl"
  }
  parameter {
    name  = "log_min_duration_statement"
    value = "1000"
  }
}
```

### Read Replica (Production)
```hcl
resource "aws_db_instance" "replica" {
  count = var.environment == "prod" ? 1 : 0

  identifier            = "nexusops-prod-postgres-replica"
  replicate_source_db   = aws_db_instance.main.id
  instance_class        = aws_db_instance.main.instance_class
  monitoring_interval   = 60
  monitoring_role_arn   = aws_iam_role.rds_monitoring.arn
}
```

### Cross-Region DR (Production)
```hcl
# In us-west-2 account
resource "aws_db_instance" "dr_replica" {
  identifier            = "nexusops-dr-postgres"
  replicate_source_db   = "arn:aws:rds:us-east-1:123456789:db:nexusops-prod-postgres"
  instance_class        = "db.r6g.large"
  publicly_accessible   = false
}
```

## Cache (ElastiCache Redis)

### Replication Group
```hcl
resource "aws_elasticache_replication_group" "main" {
  replication_group_id       = "nexusops-${var.environment}-redis"
  description                = "Redis for NexusOps ${var.environment}"
  
  engine                     = "redis"
  engine_version             = "7.1"
  node_type                  = var.environment == "prod" ? "cache.r6g.large" : "cache.t3.micro"
  number_cache_clusters      = var.environment == "prod" ? 2 : 1
  
  automatic_failover_enabled = var.environment == "prod"
  multi_az_enabled           = var.environment == "prod"
  
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  auth_token                 = random_password.redis_password.result
  
  parameter_group_name       = aws_elasticache_parameter_group.main.name
  subnet_group_name          = aws_elasticache_subnet_group.main.name
  security_group_ids         = [aws_security_group.elasticache.id]
  
  kms_key_id                 = aws_kms_key.elasticache.arn
  
  log_delivery_configuration {
    destination_type = "cloudwatch-logs"
    log_type         = "slow-log"
    destination_details {
      cloudwatch_logs {
        log_group = aws_cloudwatch_log_group.redis_slow.name
      }
    }
  }
}
```

## Load Balancing (ALB)

### Application Load Balancer
```hcl
resource "aws_lb" "main" {
  name               = "nexusops-${var.environment}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = var.public_subnet_ids
  
  enable_deletion_protection = var.environment == "prod"
  enable_http2               = true
  idle_timeout               = 60
  drop_invalid_header_fields = true
  
  access_logs {
    bucket  = aws_s3_bucket.alb_logs.bucket
    prefix  = "alb-logs"
    enabled = true
  }
}
```

### HTTPS Listener with ACM
```hcl
resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.acm_certificate_arn
  
  default_action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      message_body = "404: Not Found"
      status_code  = "404"
    }
  }
}
```

### WAF Integration
```hcl
resource "aws_wafv2_web_acl" "main" {
  name        = "nexusops-${var.environment}-waf"
  scope       = "REGIONAL"
  description = "WAF for NexusOps ALB"
  
  default_action {
    allow {}
  }
  
  rule {
    name     = "AWSManagedRulesCommonRuleSet"
    priority = 10
    override_action { none {} }
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
        version     = "1.6"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "CommonRuleSet"
      sampled_requests_enabled   = true
    }
  }
  
  # Rate limiting
  rule {
    name     = "RateLimit"
    priority = 100
    action { block {} }
    statement {
      rate_based_statement {
        limit              = 2000
        aggregate_key_type = "IP"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "RateLimit"
      sampled_requests_enabled   = true
    }
  }
}
```

## Storage (S3)

### Frontend Hosting + CloudFront
```hcl
resource "aws_s3_bucket" "frontend" {
  bucket = "nexusops-${var.environment}-frontend-${random_id.suffix.hex}"
}

resource "aws_cloudfront_distribution" "frontend" {
  origin {
    domain_name = aws_s3_bucket.frontend.bucket_regional_domain_name
    origin_id   = "S3-${aws_s3_bucket.frontend.bucket}"
    s3_origin_config {
      origin_access_identity = aws_cloudfront_origin_access_identity.frontend.cloudfront_access_identity_path
    }
  }
  
  enabled             = true
  is_ipv6_enabled     = true
  default_root_object = "index.html"
  price_class         = "PriceClass_All"
  
  default_cache_behavior {
    allowed_methods  = ["GET", "HEAD", "OPTIONS"]
    cached_methods   = ["GET", "HEAD", "OPTIONS"]
    target_origin_id = "S3-${aws_s3_bucket.frontend.bucket}"
    viewer_protocol_policy = "redirect-to-https"
    compress         = true
    min_ttl          = 0
    default_ttl      = 86400
    max_ttl          = 31536000
  }
  
  ordered_cache_behavior {
    path_pattern     = "/assets/*"
    target_origin_id = "S3-${aws_s3_bucket.frontend.bucket}"
    viewer_protocol_policy = "https-only"
    min_ttl          = 0
    default_ttl      = 31536000
    max_ttl          = 31536000
    compress         = true
  }
  
  viewer_certificate {
    acm_certificate_arn = var.acm_certificate_arn
    minimum_protocol_version = "TLSv1.2_2021"
  }
}
```

### Attachments Bucket
```hcl
resource "aws_s3_bucket" "attachments" {
  bucket = "nexusops-${var.environment}-attachments-${random_id.suffix.hex}"
  versioning {
    enabled = true
  }
  server_side_encryption_configuration {
    rule {
      apply_server_side_encryption_by_default {
        sse_algorithm = "AES256"
      }
    }
  }
  lifecycle_rule {
    id      = "transition-to-glacier"
    enabled = true
    transition {
      days          = 90
      storage_class = "GLACIER"
    }
  }
}
```

## DNS & Certificates

### Route53 (Shared Services Account)
```hcl
# Hosted Zone
resource "aws_route53_zone" "main" {
  name = "nexusops.com"
}

# Records (in each environment)
resource "aws_route53_record" "api" {
  zone_id = aws_route53_zone.main.zone_id
  name    = "api.nexusops.com"
  type    = "A"
  
  alias {
    name                   = aws_lb.main.dns_name
    zone_id                = aws_lb.main.zone_id
    evaluate_target_health = true
  }
}

resource "aws_route53_record" "app" {
  zone_id = aws_route53_zone.main.zone_id
  name    = "nexusops.com"
  type    = "A"
  
  alias {
    name                   = aws_cloudfront_distribution.frontend.domain_name
    zone_id                = aws_cloudfront_distribution.frontend.hosted_zone_id
    evaluate_target_health = true
  }
}
```

### ACM Certificates
```hcl
resource "aws_acm_certificate" "main" {
  domain_name       = "*.nexusops.com"
  validation_method = "DNS"
  
  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_acm_certificate_validation" "main" {
  certificate_arn         = aws_acm_certificate.main.arn
  validation_record_fqdns = [for record in aws_route53_record.cert_validation : record.fqdn]
}
```

## Security

### GuardDuty (Security Account)
```hcl
resource "aws_guardduty_detector" "main" {
  enable = true
  finding_publishing_frequency = "FIFTEEN_MINUTES"
}

resource "aws_guardduty_member" "accounts" {
  for_each = var.member_accounts
  detector_id = aws_guardduty_detector.main.id
  account_id  = each.value.account_id
  email       = each.value.email
}
```

### Security Hub
```hcl
resource "aws_securityhub_account" "main" {
  enable = true
}

resource "aws_securityhub_standards_subscription" "cis" {
  standards_arn = "arn:aws:securityhub:::ruleset/cis-aws-foundations-benchmark/v/1.2.0"
}
```

### Config Rules
```hcl
resource "aws_config_configuration_recorder" "main" {
  name     = "nexusops-config-recorder"
  role_arn = aws_iam_role.config.arn
  recording_group {
    all_supported = true
    include_global_resource_types = true
  }
}
```

## Monitoring & Logging

### CloudWatch Log Groups
```hcl
resource "aws_cloudwatch_log_group" "eks_cluster" {
  name              = "/aws/eks/nexusops-${var.environment}/cluster"
  retention_in_days = 30
  kms_key_id        = aws_kms_key.cloudwatch.arn
}

resource "aws_cloudwatch_log_group" "application" {
  name              = "/aws/ecs/nexusops-${var.environment}"
  retention_in_days = 30
}
```

### CloudWatch Metrics & Alarms
```hcl
resource "aws_cloudwatch_metric_alarm" "rds_cpu_high" {
  alarm_name          = "nexusops-${var.environment}-rds-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "CPUUtilization"
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  alarm_actions       = [aws_sns_topic.alerts.arn]
  dimensions = {
    DBInstanceIdentifier = aws_db_instance.main.id
  }
}
```

## Cost Optimization

### Compute Savings Plans
```hcl
# Purchase via AWS Console or CLI
# 1-year No Upfront for baseline EKS nodes
# Covers m6i.large, m6i.xlarge, r6g.large
```

### S3 Intelligent Tiering
```hcl
resource "aws_s3_bucket_intelligent_tiering_configuration" "attachments" {
  bucket = aws_s3_bucket.attachments.id
  name   = "entire-bucket"
  
  tiering {
    days = 30
    access_tier = "ARCHIVE_ACCESS"
  }
  tiering {
    days = 90
    access_tier = "DEEP_ARCHIVE_ACCESS"
  }
}
```

### RDS Storage Autoscaling
```hcl
# Enabled via max_allocated_storage
# gp3 storage scales automatically
```

## Tagging Strategy
```hcl
# Required tags on all resources
tags = {
  Environment = var.environment
  Project     = "nexusops"
  Owner       = "platform-team"
  CostCenter  = "engineering"
  ManagedBy   = "terraform"
  Backup      = var.environment == "prod" ? "true" : "false"
  Compliance  = "required"
}
```

## Terraform State Management
```hcl
# Backend configuration (per environment)
terraform {
  backend "s3" {
    bucket         = "nexusops-terraform-state-${var.environment}"
    key            = "${var.environment}/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "terraform-locks"
  }
}
```

### State Locking
```hcl
resource "aws_dynamodb_table" "terraform_locks" {
  name         = "terraform-locks"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"
  attribute {
    name = "LockID"
    type = "S"
  }
  ttl {
    attribute_name = "TTL"
    enabled        = true
  }
}
```