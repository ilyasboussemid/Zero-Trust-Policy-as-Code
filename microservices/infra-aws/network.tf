# --- VPC & RESEAU POUR ECS FARGATE ---
resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "${var.project_name}-vpc"
  }
}

resource "aws_internet_gateway" "igw" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${var.project_name}-igw"
  }
}

resource "aws_subnet" "public" {
  count                   = 2
  vpc_id                  = aws_vpc.main.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, count.index + 1)
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name = "${var.project_name}-public-${count.index + 1}"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.igw.id
  }

  tags = {
    Name = "${var.project_name}-public-rt"
  }
}

resource "aws_route_table_association" "public" {
  count          = 2
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# --- MICRO-SEGMENTATION ZERO-TRUST (SECURITY GROUPS) ---
# 1. Gateway : seul point d'entree HTTPS (8441) expose aux clients
resource "aws_security_group" "gateway" {
  name        = "${var.project_name}-sg-gateway"
  description = "Zero-Trust PEP Gateway : HTTPS 8441 entrant uniquement"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTPS client vers Share Gateway"
    from_port   = 8441
    to_port     = 8441
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Sortie mTLS vers microservices internes et AWS APIs"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-sg-gateway"
  }
}

# 2. Microservices internes (8442 & 8443) : ZERO acces direct depuis Internet
resource "aws_security_group" "internal_services" {
  name        = "${var.project_name}-sg-internal"
  description = "Zero-Trust Internal : ports 8442/8443 autorises uniquement depuis Gateway"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "mTLS vers policy-metadata-service"
    from_port       = 8442
    to_port         = 8442
    protocol        = "tcp"
    security_groups = [aws_security_group.gateway.id]
  }

  ingress {
    description     = "mTLS vers storage-vault-service"
    from_port       = 8443
    to_port         = 8443
    protocol        = "tcp"
    security_groups = [aws_security_group.gateway.id]
  }

  egress {
    description = "Sortie vers AWS S3 / ECR / CloudWatch"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-sg-internal"
  }
}
