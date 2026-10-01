# Staging Environment for NexusOps

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  backend "s3" {
    bucket = "nexusops-terraform-state-staging"
    key    = "staging/terraform.tfstate"
    region = "us-east-1"
    encrypt = true
  }
}

provider "aws" {
  region = "us-east-1"
  default_tags {
    tags = {
      Environment = "staging"
      Project     = "nexusops"
      ManagedBy   = "terraform"
    }
  }
}

# VPC
module "vpc" {
  source = "../../modules/vpc"

  name = "nexusops-staging"
  cidr_block = "10.1.0.0/16"
  azs        = ["us-east-1a", "us-east-1b", "us-east-1c"]

  common_tags = {
    Environment = "staging"
    Project     = "nexusops"
  }
}

# EKS
module "eks" {
  source = "../../modules/eks"

  name = "nexusops-staging"
  vpc_id = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  cluster_version = "1.29"

  node_groups = {
    system = {
      instance_types     = ["m6i.large"]
      capacity_type      = "ON_DEMAND"
      min_size           = 2
      max_size           = 4
      desired_size       = 2
      disk_size          = 50
      labels             = { "node-type" = "system" }
      taints             = [{ key = "system", value = "true", effect = "NO_SCHEDULE" }]
    }
    general = {
      instance_types     = ["m6i.large", "m6i.xlarge"]
      capacity_type      = "ON_DEMAND"
      min_size           = 2
      max_size           = 10
      desired_size       = 3
      disk_size          = 100
      labels             = { "node-type" = "general" }
      taints             = []
    }
    spot = {
      instance_types     = ["m6i.large", "m6i.xlarge", "m5.large", "m5.xlarge"]
      capacity_type      = "SPOT"
      min_size           = 0
      max_size           = 10
      desired_size       = 0
      disk_size          = 100
      labels             = { "node-type" = "spot" }
      taints             = [{ key = "spot", value = "true", effect = "PREFER_NO_SCHEDULE" }]
    }
  }

  common_tags = {
    Environment = "staging"
    Project     = "nexusops"
  }
}

# RDS
module "rds" {
  source = "../../modules/rds"

  name = "nexusops-staging"
  vpc_id = module.vpc.vpc_id
  database_subnet_ids = module.vpc.database_subnet_ids

  instance_class = "db.r6g.large"
  allocated_storage = 100
  max_allocated_storage = 500
  multi_az = true
  deletion_protection = true

  common_tags = {
    Environment = "staging"
    Project     = "nexusops"
  }
}

# ElastiCache
module "elasticache" {
  source = "../../modules/elasticache"

  name = "nexusops-staging"
  vpc_id = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids

  node_type = "cache.r6g.large"
  num_cache_nodes = 2
  automatic_failover_enabled = true
  multi_az_enabled = true

  common_tags = {
    Environment = "staging"
    Project     = "nexusops"
  }
}

# ALB
module "alb" {
  source = "../../modules/alb"

  name = "nexusops-staging"
  vpc_id = module.vpc.vpc_id
  public_subnet_ids = module.vpc.public_subnet_ids

  certificate_arn = "arn:aws:acm:us-east-1:ACCOUNT_ID:certificate/CERT_ID"
  waf_acl_arn = module.waf.web_acl_arn

  common_tags = {
    Environment = "staging"
    Project     = "nexusops"
  }
}

# S3
module "s3" {
  source = "../../modules/s3"

  name = "nexusops-staging"

  common_tags = {
    Environment = "staging"
    Project     = "nexusops"
  }
}

# Secrets
module "secrets" {
  source = "../../modules/secrets"

  name = "nexusops-staging"

  common_tags = {
    Environment = "staging"
    Project     = "nexusops"
  }
}

# WAF
module "waf" {
  source = "../../modules/waf"

  name = "nexusops-staging"

  common_tags = {
    Environment = "staging"
    Project     = "nexusops"
  }
}

# Monitoring
module "monitoring" {
  source = "../../modules/monitoring"

  name = "nexusops-staging"
  cluster_name = module.eks.cluster_id
  vpc_id = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  alert_email = "alerts@nexusops.com"

  common_tags = {
    Environment = "staging"
    Project     = "nexusops"
  }
}

# Outputs
output "vpc_id" {
  value = module.vpc.vpc_id
}

output "cluster_endpoint" {
  value = module.eks.cluster_endpoint
}

output "db_endpoint" {
  value = module.rds.db_instance_endpoint
}

output "redis_endpoint" {
  value = module.elasticache.primary_endpoint
}

output "alb_dns_name" {
  value = module.alb.alb_dns_name
}