output "static_website_url" {
  description = "URL publique du portail frontend heberge sur Amazon S3 Static"
  value       = "http://${aws_s3_bucket_website_configuration.static_frontend.website_endpoint}"
}

output "s3_vault_bucket_name" {
  description = "Nom du bucket S3 prive (expiration automatique 24h)"
  value       = aws_s3_bucket.vault.bucket
}

output "cognito_user_pool_id" {
  description = "ID du User Pool Amazon Cognito"
  value       = aws_cognito_user_pool.users.id
}

output "cognito_client_id" {
  description = "App Client ID Amazon Cognito"
  value       = aws_cognito_user_pool_client.app_client.id
}

output "cognito_issuer_uri" {
  description = "Issuer URI OIDC Cognito pour validation JWT"
  value       = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.users.id}"
}

output "ecr_repositories" {
  description = "URLs des depots Amazon ECR pour pusher les 3 images Docker"
  value       = { for k, v in aws_ecr_repository.services : k => v.repository_url }
}

output "ecs_cluster_name" {
  description = "Nom du cluster Amazon ECS Fargate"
  value       = aws_ecs_cluster.main.name
}
