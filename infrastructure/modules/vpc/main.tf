# VPC Module for NexusOps

variable "name" {
  description = "Name prefix for resources"
  type        = string
}

variable "cidr_block" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "azs" {
  description = "Availability zones"
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b", "us-east-1c"]
}

variable "private_subnet_tags" {
  description = "Additional tags for private subnets"
  type        = map(string)
  default     = {}
}

variable "public_subnet_tags" {
  description = "Additional tags for public subnets"
  type        = map(string)
  default     = {}
}

variable "database_subnet_tags" {
  description = "Additional tags for database subnets"
  type        = map(string)
  default     = {}
}

# VPC
resource "aws_vpc" "main" {
  cidr_block           = var.cidr_block
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(
    { Name = "${var.name}-vpc" },
    var.common_tags
  )
}

# Internet Gateway
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = merge(
    { Name = "${var.name}-igw" },
    var.common_tags
  )
}

# NAT Gateways (one per AZ for HA)
resource "aws_eip" "nat" {
  count  = length(var.azs)
  domain = "vpc"

  tags = merge(
    { Name = "${var.name}-nat-eip-${count.index}" },
    var.common_tags
  )
}

resource "aws_nat_gateway" "main" {
  count         = length(var.azs)
  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  tags = merge(
    { Name = "${var.name}-nat-${count.index}" },
    var.common_tags
  )

  depends_on = [aws_internet_gateway.main]
}

# Public Subnets
resource "aws_subnet" "public" {
  count                   = length(var.azs)
  vpc_id                  = aws_vpc.main.id
  cidr_block              = cidrsubnet(var.cidr_block, 4, count.index)
  availability_zone       = var.azs[count.index]
  map_public_ip_on_launch = true

  tags = merge(
    {
      Name        = "${var.name}-public-${var.azs[count.index]}"
      Type        = "public"
      "kubernetes.io/role/elb" = "1"
    },
    var.public_subnet_tags,
    var.common_tags
  )
}

# Private Subnets
resource "aws_subnet" "private" {
  count             = length(var.azs)
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.cidr_block, 4, count.index + 10)
  availability_zone = var.azs[count.index]

  tags = merge(
    {
      Name        = "${var.name}-private-${var.azs[count.index]}"
      Type        = "private"
      "kubernetes.io/role/internal-elb" = "1"
    },
    var.private_subnet_tags,
    var.common_tags
  )
}

# Database Subnets
resource "aws_subnet" "database" {
  count             = length(var.azs)
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.cidr_block, 4, count.index + 20)
  availability_zone = var.azs[count.index]

  tags = merge(
    {
      Name        = "${var.name}-database-${var.azs[count.index]}"
      Type        = "database"
    },
    var.database_subnet_tags,
    var.common_tags
  )
}

# Route Tables
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = merge(
    { Name = "${var.name}-public-rt" },
    var.common_tags
  )
}

resource "aws_route_table_association" "public" {
  count          = length(var.azs)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table" "private" {
  count  = length(var.azs)
  vpc_id = aws_vpc.main.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main[count.index].id
  }

  tags = merge(
    { Name = "${var.name}-private-rt-${count.index}" },
    var.common_tags
  )
}

resource "aws_route_table_association" "private" {
  count          = length(var.azs)
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private[count.index].id
}

resource "aws_route_table" "database" {
  vpc_id = aws_vpc.main.id

  tags = merge(
    { Name = "${var.name}-database-rt" },
    var.common_tags
  )
}

resource "aws_route_table_association" "database" {
  count          = length(var.azs)
  subnet_id      = aws_subnet.database[count.index].id
  route_table_id = aws_route_table.database.id
}

# VPC Flow Logs
resource "aws_flow_log" "vpc" {
  iam_role_arn      = aws_iam_role.flow_logs.arn
  log_destination   = aws_cloudwatch_log_group.vpc_flow_logs.arn
  traffic_type      = "ALL"
  vpc_id            = aws_vpc.main.id
}

resource "aws_cloudwatch_log_group" "vpc_flow_logs" {
  name              = "/aws/vpc/${var.name}/flowlogs"
  retention_in_days = 30

  tags = var.common_tags
}

resource "aws_iam_role" "flow_logs" {
  name = "${var.name}-flow-logs-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "vpc-flow-logs.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_policy" "flow_logs" {
  name = "${var.name}-flow-logs-policy"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "logs:DescribeLogGroups",
        "logs:DescribeLogStreams"
      ]
      Effect   = "Allow"
      Resource = "*"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "flow_logs" {
  role       = aws_iam_role.flow_logs.name
  policy_arn = aws_iam_policy.flow_logs.arn
}

# Outputs
output "vpc_id" {
  value = aws_vpc.main.id
}

output "vpc_cidr_block" {
  value = aws_vpc.main.cidr_block
}

output "public_subnet_ids" {
  value = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  value = aws_subnet.private[*].id
}

output "database_subnet_ids" {
  value = aws_subnet.database[*].id
}

output "nat_gateway_ids" {
  value = aws_nat_gateway.main[*].id
}

output "availability_zones" {
  value = var.azs
}