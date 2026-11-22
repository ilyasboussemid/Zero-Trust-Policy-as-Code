output "s3_bucket_name" {
  value = aws_s3_bucket.ephemeral_vault.bucket
}

output "cognito_user_pool_id" {
  value = aws_cognito_user_pool.users.id
}

output "cognito_client_id" {
  value = aws_cognito_user_pool_client.app_client.id
}

output "cognito_issuer_uri" {
  value = "https://cognito-idp.eu-west-3.amazonaws.com/${aws_cognito_user_pool.users.id}"
}

output "vault_aws_access_key_id" {
  value = aws_iam_access_key.storage_vault_key.id
}

output "vault_aws_secret_access_key" {
  value     = aws_iam_access_key.storage_vault_key.secret
  sensitive = true
}
