# --- AMAZON ECR (REGISTRES D'IMAGES DOCKER) ---
locals {
  microservices = {
    "share-gateway-service"   = 8441
    "policy-metadata-service" = 8442
    "storage-vault-service"   = 8443
  }
}

resource "aws_ecr_repository" "services" {
  for_each             = local.microservices
  name                 = "${var.project_name}/${each.key}"
  image_tag_mutability = "MUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }
}

# --- AWS CLOUD MAP (SERVICE DISCOVERY DNS INTERNE) ---
resource "aws_service_discovery_private_dns_namespace" "internal" {
  name        = "zerotrust.local"
  description = "Namespace DNS prive pour communication mTLS inter-services ECS"
  vpc         = aws_vpc.main.id
}

resource "aws_service_discovery_service" "metadata" {
  name = "policy-metadata-service"

  dns_config {
    namespace_id = aws_service_discovery_private_dns_namespace.internal.id

    dns_records {
      ttl  = 10
      type = "A"
    }
  }

  health_check_custom_config {
    failure_threshold = 1
  }
}

resource "aws_service_discovery_service" "vault" {
  name = "storage-vault-service"

  dns_config {
    namespace_id = aws_service_discovery_private_dns_namespace.internal.id

    dns_records {
      ttl  = 10
      type = "A"
    }
  }

  health_check_custom_config {
    failure_threshold = 1
  }
}

# --- IAM ROLES MOINDRE PRIVILEGE (ZERO-TRUST) ---
resource "aws_iam_role" "ecs_task_execution" {
  name = "${var.project_name}-ecs-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_task_execution_policy" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# Role IAM dedie exclusivement a storage-vault-service pour signer les URLs S3
resource "aws_iam_role" "vault_task_role" {
  name = "${var.project_name}-vault-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy" "vault_s3_presign" {
  name = "${var.project_name}-vault-s3-policy"
  role = aws_iam_role.vault_task_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowPresignedObjectOperations"
        Effect = "Allow"
        Action = [
          "s3:PutObject",
          "s3:GetObject",
          "s3:DeleteObject"
        ]
        Resource = "${aws_s3_bucket.vault.arn}/*"
      }
    ]
  })
}

# --- CLUSTER ECS FARGATE & LOGS ---
resource "aws_cloudwatch_log_group" "ecs" {
  name              = "/ecs/${var.project_name}"
  retention_in_days = 7
}

resource "aws_ecs_cluster" "main" {
  name = "${var.project_name}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

# 1. Task Definition & Service : policy-metadata-service (:8442)
resource "aws_ecs_task_definition" "metadata" {
  family                   = "${var.project_name}-policy-metadata"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn

  container_definitions = jsonencode([
    {
      name      = "policy-metadata-service"
      image     = "${aws_ecr_repository.services["policy-metadata-service"].repository_url}:latest"
      essential = true
      portMappings = [
        { containerPort = 8442, protocol = "tcp" }
      ]
      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = "aws" },
        { name = "AWS_REGION", value = var.aws_region },
        { name = "COGNITO_ISSUER_URI", value = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.users.id}" }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.ecs.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "metadata"
        }
      }
    }
  ])
}

resource "aws_ecs_service" "metadata" {
  name            = "policy-metadata-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.metadata.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.internal_services.id]
    assign_public_ip = true
  }

  service_registries {
    registry_arn = aws_service_discovery_service.metadata.arn
  }
}

# 2. Task Definition & Service : storage-vault-service (:8443)
resource "aws_ecs_task_definition" "vault" {
  family                   = "${var.project_name}-storage-vault"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.vault_task_role.arn

  container_definitions = jsonencode([
    {
      name      = "storage-vault-service"
      image     = "${aws_ecr_repository.services["storage-vault-service"].repository_url}:latest"
      essential = true
      portMappings = [
        { containerPort = 8443, protocol = "tcp" }
      ]
      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = "aws" },
        { name = "AWS_REGION", value = var.aws_region },
        { name = "S3_VAULT_BUCKET", value = aws_s3_bucket.vault.bucket },
        { name = "COGNITO_ISSUER_URI", value = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.users.id}" }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.ecs.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "vault"
        }
      }
    }
  ])
}

resource "aws_ecs_service" "vault" {
  name            = "storage-vault-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.vault.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.internal_services.id]
    assign_public_ip = true
  }

  service_registries {
    registry_arn = aws_service_discovery_service.vault.arn
  }
}

# 3. Task Definition & Service : share-gateway-service (:8441)
resource "aws_ecs_task_definition" "gateway" {
  family                   = "${var.project_name}-share-gateway"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn

  container_definitions = jsonencode([
    {
      name      = "share-gateway-service"
      image     = "${aws_ecr_repository.services["share-gateway-service"].repository_url}:latest"
      essential = true
      portMappings = [
        { containerPort = 8441, protocol = "tcp" }
      ]
      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = "aws" },
        { name = "AWS_REGION", value = var.aws_region },
        { name = "COGNITO_USER_POOL_ID", value = aws_cognito_user_pool.users.id },
        { name = "COGNITO_CLIENT_ID", value = aws_cognito_user_pool_client.app_client.id },
        { name = "COGNITO_ISSUER_URI", value = "https://cognito-idp.${var.aws_region}.amazonaws.com/${aws_cognito_user_pool.users.id}" },
        { name = "METADATA_SERVICE_URL", value = "https://policy-metadata-service.zerotrust.local:8442" },
        { name = "VAULT_SERVICE_URL", value = "https://storage-vault-service.zerotrust.local:8443" }
      ]
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.ecs.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "gateway"
        }
      }
    }
  ])
}

resource "aws_ecs_service" "gateway" {
  name            = "share-gateway-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.gateway.arn
  desired_count   = 1
  launch_type     = "FARGATE"
  depends_on      = [aws_ecs_service.metadata, aws_ecs_service.vault]

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.gateway.id]
    assign_public_ip = true
  }
}
