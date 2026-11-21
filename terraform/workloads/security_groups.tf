resource "aws_security_group" "app_baseline_sg" {
  name        = "zt-app-baseline-sg"
  description = "Security group applicatif conforme Zero-Trust"
  vpc_id      = var.vpc_id

  tags = {
    Name = "zt-app-baseline-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "allow_internal_https" {
  security_group_id = aws_security_group.app_baseline_sg.id
  description       = "Autorise le trafic HTTPS interne uniquement"
  cidr_ipv4         = var.corp_cidr
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "allow_https_outbound" {
  security_group_id = aws_security_group.app_baseline_sg.id
  description       = "Autorise les appels sortants HTTPS"
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}