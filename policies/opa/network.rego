package terraform.network

import rego.v1

restricted_ports := {22, 3389, 3306, 5432}

deny contains msg if {
	rc := input.resource_changes[_]
	rc.type == "aws_vpc_security_group_ingress_rule"
	rc.change.after.cidr_ipv4 == "0.0.0.0/0"
	rc.change.after.from_port in restricted_ports
	msg := sprintf("Port %v ouvert sur 0.0.0.0/0 interdit (%s)", [rc.change.after.from_port, rc.address])
}

deny contains msg if {
	rc := input.resource_changes[_]
	rc.type == "aws_security_group_rule"
	rc.change.after.type == "ingress"
	rc.change.after.cidr_blocks[_] == "0.0.0.0/0"
	rc.change.after.from_port in restricted_ports
	msg := sprintf("Port %v ouvert sur 0.0.0.0/0 interdit (%s)", [rc.change.after.from_port, rc.address])
}