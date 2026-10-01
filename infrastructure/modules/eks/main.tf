# EKS Module for NexusOps

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

variable "cluster_version" {
  description = "EKS cluster version"
  type        = string
  default     = "1.29"
}

variable "cluster_endpoint_public_access" {
  description = "Enable public access to cluster endpoint"
  type        = bool
  default     = true
}

variable "cluster_endpoint_private_access" {
  description = "Enable private access to cluster endpoint"
  type        = bool
  default     = true
}

variable "cluster_endpoint_public_access_cidrs" {
  description = "CIDR blocks for public access to cluster endpoint"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "node_groups" {
  description = "Node group configurations"
  type = map(object({
    instance_types     = list(string)
    capacity_type      = string
    min_size           = number
    max_size           = number
    desired_size       = number
    disk_size          = number
    labels             = map(string)
    taints             = list(object({
      key    = string
      value  = string
      effect = string
    }))
  }))
  default = {
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
    memory = {
      instance_types     = ["r6i.large", "r6i.xlarge"]
      capacity_type      = "ON_DEMAND"
      min_size           = 1
      max_size           = 5
      desired_size       = 1
      disk_size          = 100
      labels             = { "node-type" = "memory" }
      taints             = []
    }
    spot = {
      instance_types     = ["m6i.large", "m6i.xlarge", "m5.large", "m5.xlarge"]
      capacity_type      = "SPOT"
      min_size           = 0
      max_size           = 20
      desired_size       = 0
      disk_size          = 100
      labels             = { "node-type" = "spot" }
      taints             = [{ key = "spot", value = "true", effect = "PREFER_NO_SCHEDULE" }]
    }
  }

variable "common_tags" {
  description = "Common tags for all resources"
  type        = map(string)
  default     = {}
}

# EKS Cluster Role
resource "aws_iam_role" "cluster" {
  name = "${var.name}-eks-cluster-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "eks.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "cluster_policy" {
  role       = aws_iam_role.cluster.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}

resource "aws_iam_role_policy_attachment" "cluster_service_policy" {
  role       = aws_iam_role.cluster.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSServicePolicy"
}

# EKS Node Group Role
resource "aws_iam_role" "node_group" {
  name = "${var.name}-eks-node-group-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ec2.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "node_group_worker" {
  role       = aws_iam_role.node_group.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
}

resource "aws_iam_role_policy_attachment" "node_group_cni" {
  role       = aws_iam_role.node_group.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
}

resource "aws_iam_role_policy_attachment" "node_group_container_registry" {
  role       = aws_iam_role.node_group.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_iam_role_policy_attachment" "node_group_ssm" {
  role       = aws_iam_role.node_group.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# EKS Cluster
resource "aws_eks_cluster" "main" {
  name     = "${var.name}-eks"
  role_arn = aws_iam_role.cluster.arn
  version  = var.cluster_version

  vpc_config {
    subnet_ids              = var.private_subnet_ids
    endpoint_public_access  = var.cluster_endpoint_public_access
    endpoint_private_access = var.cluster_endpoint_private_access
    public_access_cidrs     = var.cluster_endpoint_public_access_cidrs
  }

  encryption_config {
    provider {
      key_arn = aws_kms_key.eks.arn
    }
    resources = ["secrets"]
  }

  tags = merge(
    { Name = "${var.name}-eks" },
    var.common_tags
  )
}

# KMS Key for EKS Secrets Encryption
resource "aws_kms_key" "eks" {
  description             = "KMS key for EKS secrets encryption"
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
        Action = [
          "kms:*"
        ]
        Resource = "*"
      },
      {
        Sid    = "Allow EKS to use the key"
        Effect = "Allow"
        Principal = {
          Service = "eks.amazonaws.com"
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
    { Name = "${var.name}-eks-kms" },
    var.common_tags
  )
}

# EKS Node Groups
resource "aws_eks_node_group" "main" {
  for_each = var.node_groups

  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "${var.name}-${each.key}"
  node_role_arn   = aws_iam_role.node_group.arn
  subnet_ids      = var.private_subnet_ids

  instance_types = each.value.instance_types
  capacity_type  = each.value.capacity_type

  scaling_config {
    min_size     = each.value.min_size
    max_size     = each.value.max_size
    desired_size = each.value.desired_size
  }

  disk_size = each.value.disk_size

  labels = merge(
    each.value.labels,
    { "node-group" = each.key }
  )

  taints = each.value.taints

  update_config {
    max_unavailable_percentage = 25
  }

  tags = merge(
    { Name = "${var.name}-${each.key}" },
    var.common_tags
  )

  depends_on = [
    aws_iam_role_policy_attachment.node_group_worker,
    aws_iam_role_policy_attachment.node_group_cni,
    aws_iam_role_policy_attachment.node_group_container_registry,
    aws_iam_role_policy_attachment.node_group_ssm
  ]
}

# CloudWatch Log Group for Cluster
resource "aws_cloudwatch_log_group" "cluster" {
  name              = "/aws/eks/${var.name}/cluster"
  retention_in_days = 30

  tags = var.common_tags
}

# Cluster Logging
resource "aws_eks_cluster" "main" {
  # ... (previous config)

  enabled_cluster_log_types = ["api", "audit", "authenticator", "controllerManager", "scheduler"]

  # ... (rest of config)
}

data "aws_caller_identity" "current" {}

# Outputs
output "cluster_id" {
  value = aws_eks_cluster.main.id
}

output "cluster_arn" {
  value = aws_eks_cluster.main.arn
}

output "cluster_endpoint" {
  value = aws_eks_cluster.main.endpoint
}

output "cluster_certificate_authority_data" {
  value     = aws_eks_cluster.main.certificate_authority[0].data
  sensitive = true
}

output "cluster_security_group_id" {
  value = aws_eks_cluster.main.vpc_config[0].cluster_security_group_id
}

output "node_group_arns" {
  value = { for k, v in aws_eks_node_group.main : k => v.arn }
}