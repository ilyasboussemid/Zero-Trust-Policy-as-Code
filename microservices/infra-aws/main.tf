terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.5"
    }
  }
}

provider "aws" {
  region = "eu-west-3"
}

resource "random_id" "suffix" {
  byte_length = 4
}

# ==============================================================================
# 1. AMAZON S3 : Coffre prive avec suppression automatique apres 24h
# ==============================================================================
resource "aws_s3_bucket" "ephemeral_vault" {
  bucket        = "zt-ephemeral-share-${random_id.suffix.hex}"
  force_destroy = true

  tags = {
    Project       = "ZeroTrustShare"
    AutoRemediate = "true"
  }
}

resource "aws_s3_bucket_public_access_block" "ephemeral_vault_pab" {
  bucket = aws_s3_bucket.ephemeral_vault.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "ephemeral_vault_sse" {
  bucket = aws_s3_bucket.ephemeral_vault.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "expire_24h" {
  bucket = aws_s3_bucket.ephemeral_vault.id

  rule {
    id     = "delete-uploads-after-24h"
    status = "Enabled"

    filter {
      prefix = "uploads/"
    }

    expiration {
      days = 1
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

resource "aws_s3_bucket_cors_configuration" "ephemeral_vault_cors" {
  bucket = aws_s3_bucket.ephemeral_vault.id

  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["PUT", "GET", "HEAD"]
    allowed_origins = [
      "http://localhost:3000",
      "http://localhost:5173",
      "https://localhost:8441"
    ]
    expose_headers  = ["ETag"]
    max_age_seconds = 3600
  }
}

# ==============================================================================
# 2. AMAZON COGNITO : Authentification OIDC/JWT et Groupes ABAC
# ==============================================================================
resource "aws_cognito_user_pool" "users" {
  name = "zt-share-user-pool"

  auto_verified_attributes = ["email"]

  password_policy {
    minimum_length    = 8
    require_lowercase = true
    require_numbers   = true
    require_symbols   = false
    require_uppercase = true
  }

  schema {
    name                = "email"
    attribute_data_type = "String"
    required            = true
    mutable             = true
  }
}

resource "aws_cognito_user_pool_client" "app_client" {
  name            = "zt-share-client"
  user_pool_id    = aws_cognito_user_pool.users.id
  generate_secret = false

  explicit_auth_flows = [
    "ALLOW_USER_PASSWORD_AUTH",
    "ALLOW_USER_SRP_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH"
  ]
}

resource "aws_cognito_user_group" "standard" {
  name         = "STANDARD"
  user_pool_id = aws_cognito_user_pool.users.id
  description  = "Quota standard : fichiers jusqu'a 50 Mo, expiration 24h"
  precedence   = 2
}

resource "aws_cognito_user_group" "premium" {
  name         = "PREMIUM"
  user_pool_id = aws_cognito_user_pool.users.id
  description  = "Quota etendu : fichiers jusqu'a 500 Mo, expiration 24h"
  precedence   = 1
}

# ==============================================================================
# 3. IAM MOINDRE PRIVILEGE : Acces S3 uniquement pour storage-vault-service
# ==============================================================================
resource "aws_iam_user" "storage_vault_service" {
  name = "zt-storage-vault-service"
}

resource "aws_iam_user_policy" "storage_vault_s3_policy" {
  name = "zt-storage-vault-s3-least-privilege"
  user = aws_iam_user.storage_vault_service.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:GetObject"]
        Resource = "${aws_s3_bucket.ephemeral_vault.arn}/uploads/*"
      }
    ]
  })
}

resource "aws_iam_access_key" "storage_vault_key" {
  user = aws_iam_user.storage_vault_service.name
}
