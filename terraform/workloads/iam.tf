resource "aws_iam_role" "app_workload_role" {
  name = "zt-app-workload-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
}

resource "aws_iam_policy" "app_least_privilege_policy" {
  name        = "zt-app-s3-kms-least-privilege"
  description = "Acces strict en lecture/ecriture sur le bucket app_data uniquement"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowScopedS3ObjectAccess"
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject"
        ]
        Resource = "${aws_s3_bucket.app_data.arn}/*"
      },
      {
        Sid    = "AllowScopedKMSUsage"
        Effect = "Allow"
        Action = [
          "kms:Decrypt",
          "kms:GenerateDataKey"
        ]
        Resource = aws_kms_key.app_data_key.arn
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "attach_least_privilege" {
  role       = aws_iam_role.app_workload_role.name
  policy_arn = aws_iam_policy.app_least_privilege_policy.arn
}