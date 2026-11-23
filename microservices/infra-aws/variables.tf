variable "aws_region" {
  description = "Region AWS cible pour le deploiement"
  type        = string
  default     = "eu-west-3"
}

variable "project_name" {
  description = "Prefixe des ressources Zero-Trust"
  type        = string
  default     = "zerotrust-pac"
}

variable "environment" {
  description = "Environnement de deploiement"
  type        = string
  default     = "prod"
}

variable "vpc_cidr" {
  description = "Plage CIDR du VPC dedie ECS Fargate"
  type        = string
  default     = "10.20.0.0/16"
}
