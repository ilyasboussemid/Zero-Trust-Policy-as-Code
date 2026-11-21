variable "aws_region" {
  type    = string
  default = "eu-west-3"
}

variable "vpc_id" {
  description = "ID du VPC cible"
  type        = string
  default     = "vpc-0f8d838e293ca7231"
}

variable "corp_cidr" {
  description = "Plage IP autorisee interne"
  type        = string
  default     = "10.0.0.0/16"
}