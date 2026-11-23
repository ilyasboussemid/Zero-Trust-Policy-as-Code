# --- AMAZON S3 VAULT (STOCKAGE CHIFFRE + EXPIRATION 24H) ---
resource "aws_s3_bucket" "vault" {
  bucket        = "${var.project_name}-vault-${data.aws_caller_identity.current.account_id}"
  force_destroy = true
}

# Blocage strict de tout acces public (acces uniquement via URL pre-signee)
resource "aws_s3_bucket_public_access_block" "vault" {
  bucket = aws_s3_bucket.vault.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Chiffrement serveur par defaut (SSE-S3 AES256)
resource "aws_s3_bucket_server_side_encryption_configuration" "vault" {
  bucket = aws_s3_bucket.vault.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Suppression automatique des fichiers apres 24 heures (1 jour)
resource "aws_s3_bucket_lifecycle_configuration" "vault_ttl_24h" {
  bucket = aws_s3_bucket.vault.id

  rule {
    id     = "auto-expire-shares-24h"
    status = "Enabled"

    filter {}

    expiration {
      days = 1
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

# CORS pour upload/download direct depuis le frontend S3 Static
resource "aws_s3_bucket_cors_configuration" "vault_cors" {
  bucket = aws_s3_bucket.vault.id

  cors_rule {
    allowed_headers = ["*"]
    allowed_methods = ["GET", "PUT", "HEAD"]
    allowed_origins = ["*"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3600
  }
}
