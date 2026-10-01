# Dev Environment for NexusOps

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
    bucket = "nexusops-terraform-state-dev"
    key    = "dev/terraform.tfstate"
    region = "us-east-1"
    encrypt = true
  }
}

provider "aws" {
  region = "us-east-1"
  default_tags {
    tags = {
      Environment = "dev"
      Project     = "nexusops"
      ManagedBy   = "terraform"
    }
  }
}

# VPC
module "vpc" {
  source = "../../modules/vpc"

  name = "nexusops-dev"
  cidr_block = "10.0.0.0/16"
  azs        = ["us-east-1a", "us-east-1b", "us-east-1c"]

  common_tags = {
    Environment = "dev"
    Project     = "nexusops"
  }
}

# EKS
module "eks" {
  source = "../../modules/eks"

  name = "nexusops-dev"
  vpc_id = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  cluster_version = "1.28"

  node_groups = {
    system = {
      instance_types     = ["m6i.large"]
      capacity_type      = "ON_DEMAND"
      min_size           = 1
      max_size           = 3
      desired_size       = 1
      disk_size          = 50
      labels             = { "node-type" = "system" }
      taints             = [{ key = "system", value = "true", effect = "NO_SCHEDULE" }]
    }
    general = {
      instance_types     = ["m6i.large"]
      capacity_type      = "ON_DEMAND"
      min_size           = 1
      max_size           = 5
      desired_size       = 2
      disk_size          = 50
      labels             = { "node-type" = "general" }
      taints             = []
    }
  }

  common_tags = {
    Environment = "dev"
    Project     = "nexusops"
  }
}

# RDS
module "rds" {
  source = "../../modules/rds"

  name = "nexusops-dev"
  vpc_id = module.vpc.vpc_id
  database_subnet_ids = module.vpc.database_subnet_ids

  instance_class = "db.t3.medium"
  allocated_storage = 50
  max_allocated_storage = 100
  multi_az = false
  deletion_protection = false

  common_tags = {
    Environment = "dev"
    Project     = "nexusops"
  }
}

# ElastiCache
module "elasticache" {
  source = "../../modules/elasticache"

  name = "nexusops-dev"
  vpc_id = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids

  node_type = "cache.t3.micro"
  num_cache_nodes = 1
  automatic_failover_enabled = false
  multi_az_enabled = false

  common_tags = {
    Environment = "dev"
    Project     = "nexusops"
  }
}

# S3
module "s3" {
  source = "../../modules/s3"

  name = "nexusops-dev"

  common_tags = {
    Environment = "dev"
    Project     = "nexusops"
  }
}

# Secrets
module "secrets" {
  source = "../../modules/secrets"

  name = "nexusops-dev"

  common_tags = {
    Environment = "dev"
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