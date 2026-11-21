package terraform.iam

import rego.v1

deny contains msg if {
	rc := input.resource_changes[_]
	rc.type == "aws_iam_role_policy_attachment"
	endswith(rc.change.after.policy_arn, "AdministratorAccess")
	msg := sprintf("AdministratorAccess interdit (%s)", [rc.address])
}

deny contains msg if {
	rc := input.resource_changes[_]
	rc.type in {"aws_iam_policy", "aws_iam_role_policy"}
	doc := json.unmarshal(rc.change.after.policy)
	stmt := doc.Statement[_]
	stmt.Effect == "Allow"
	has_wildcard(stmt.Action)
	msg := sprintf("Wildcard IAM Action '*' interdit (%s)", [rc.address])
}

deny contains msg if {
	rc := input.resource_changes[_]
	rc.type in {"aws_iam_policy", "aws_iam_role_policy"}
	doc := json.unmarshal(rc.change.after.policy)
	stmt := doc.Statement[_]
	stmt.Effect == "Allow"
	has_wildcard(stmt.Resource)
	msg := sprintf("Wildcard IAM Resource '*' interdit (%s)", [rc.address])
}

has_wildcard(val) if {
	is_string(val)
	val == "*"
}

has_wildcard(val) if {
	is_array(val)
	val[_] == "*"
}