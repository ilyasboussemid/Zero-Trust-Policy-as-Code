terraform {
  required_version = ">= 1.10.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.80"
    }
  }

  backend "s3" {
    bucket       = "zt-devsecops-tfstate-9968e68e"
    key          = "workloads/terraform.tfstate"
    region       = "eu-west-3"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project       = "ZeroTrust-Drift-Platform"
      Environment   = "prod"
      ManagedBy     = "Terraform"
      AutoRemediate = "true"
    }
  }
}