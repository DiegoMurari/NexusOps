# AWS Secrets Manager Module for NexusOps

variable "name" {
  description = "Name prefix for resources"
  type        = string
}

variable "common_tags" {
  description = "Common tags for all resources"
  type        = map(string)
  default     = {}
}

# Database Credentials Secret
resource "aws_secretsmanager_secret" "db_credentials" {
  name        = "${var.name}/db/credentials"
  description = "Database credentials for NexusOps"
  tags        = var.common_tags
}

resource "aws_secretsmanager_secret_version" "db_credentials" {
  secret_id = aws_secretsmanager_secret.db_credentials.id
  secret_string = jsonencode({
    username = "nexusops"
    password = random_password.db_password.result
    engine   = "postgres"
    host     = "" # Will be populated after RDS creation
    port     = 5432
    dbname   = "nexusops"
  })
}

resource "random_password" "db_password" {
  length  = 32
  special = false
}

# Redis Credentials Secret
resource "aws_secretsmanager_secret" "redis_credentials" {
  name        = "${var.name}/redis/credentials"
  description = "Redis credentials for NexusOps"
  tags        = var.common_tags
}

resource "aws_secretsmanager_secret_version" "redis_credentials" {
  secret_id = aws_secretsmanager_secret.redis_credentials.id
  secret_string = jsonencode({
    host     = "" # Will be populated after ElastiCache creation
    port     = 6379
    password = random_password.redis_password.result
  })
}

resource "random_password" "redis_password" {
  length  = 32
  special = false
}

# JWT Keys Secret
resource "aws_secretsmanager_secret" "jwt_keys" {
  name        = "${var.name}/jwt/keys"
  description = "JWT signing keys for NexusOps"
  tags        = var.common_tags
}

resource "aws_secretsmanager_secret_version" "jwt_keys" {
  secret_id = aws_secretsmanager_secret.jwt_keys.id
  secret_string = jsonencode({
    private_key = tls_private_key.jwt_private_key.private_key_pem
    public_key  = tls_private_key.jwt_private_key.public_key_pem
  })
}

resource "tls_private_key" "jwt_private_key" {
  algorithm = "RSA"
  rsa_bits  = 2048
}

# Mail Credentials Secret
resource "aws_secretsmanager_secret" "mail_credentials" {
  name        = "${var.name}/mail/credentials"
  description = "Mail server credentials for NexusOps"
  tags        = var.common_tags
}

resource "aws_secretsmanager_secret_version" "mail_credentials" {
  secret_id = aws_secretsmanager_secret.mail_credentials.id
  secret_string = jsonencode({
    host     = ""
    port     = 587
    username = ""
    password = ""
  })
}

# External API Keys Secret
resource "aws_secretsmanager_secret" "external_apis" {
  name        = "${var.name}/external/apis"
  description = "External API keys for integrations"
  tags        = var.common_tags
}

resource "aws_secretsmanager_secret_version" "external_apis" {
  secret_id = aws_secretsmanager_secret.external_apis.id
  secret_string = jsonencode({
    jira = {
      url       = ""
      username  = ""
      api_token = ""
    }
    slack = {
      signing_secret = ""
      bot_token      = ""
      app_token      = ""
    }
    teams = {
      app_id     = ""
      app_password = ""
    }
  })
}

# Outputs
output "db_credentials_secret_arn" {
  value = aws_secretsmanager_secret.db_credentials.arn
}

output "redis_credentials_secret_arn" {
  value = aws_secretsmanager_secret.redis_credentials.arn
}

output "jwt_keys_secret_arn" {
  value = aws_secretsmanager_secret.jwt_keys.arn
}

output "mail_credentials_secret_arn" {
  value = aws_secretsmanager_secret.mail_credentials.arn
}

output "external_apis_secret_arn" {
  value = aws_secretsmanager_secret.external_apis.arn
}