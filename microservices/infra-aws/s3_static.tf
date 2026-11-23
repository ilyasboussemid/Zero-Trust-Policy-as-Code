# --- AMAZON S3 STATIC WEBSITE (FRONTEND PORTAL) ---
resource "aws_s3_bucket" "static_frontend" {
  bucket        = "${var.project_name}-frontend-${data.aws_caller_identity.current.account_id}"
  force_destroy = true
}

resource "aws_s3_bucket_website_configuration" "static_frontend" {
  bucket = aws_s3_bucket.static_frontend.id

  index_document {
    suffix = "index.html"
  }

  error_document {
    key = "index.html"
  }
}

resource "aws_s3_bucket_public_access_block" "static_frontend" {
  bucket = aws_s3_bucket.static_frontend.id

  block_public_acls       = false
  block_public_policy     = false
  ignore_public_acls      = false
  restrict_public_buckets = false
}

resource "aws_s3_bucket_policy" "static_frontend_public_read" {
  bucket     = aws_s3_bucket.static_frontend.id
  depends_on = [aws_s3_bucket_public_access_block.static_frontend]

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "PublicReadStaticWebsite"
        Effect    = "Allow"
        Principal = "*"
        Action    = "s3:GetObject"
        Resource  = "${aws_s3_bucket.static_frontend.arn}/*"
      }
    ]
  })
}

# Deploiement automatique de la page statique index.html
resource "aws_s3_object" "index_html" {
  bucket       = aws_s3_bucket.static_frontend.id
  key          = "index.html"
  source       = "${path.module}/../frontend-static/index.html"
  etag         = filemd5("${path.module}/../frontend-static/index.html")
  content_type = "text/html; charset=utf-8"
}
