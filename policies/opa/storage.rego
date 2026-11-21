package terraform.storage

import rego.v1

deny contains msg if {
	rc := input.resource_changes[_]
	rc.type == "aws_s3_bucket_public_access_block"
	after := rc.change.after
	not all_blocked(after)
	msg := sprintf("Blocage public S3 incomplet (%s)", [rc.address])
}

all_blocked(after) if {
	after.block_public_acls == true
	after.block_public_policy == true
	after.ignore_public_acls == true
	after.restrict_public_buckets == true
}

deny contains msg if {
	rc := input.resource_changes[_]
	rc.type == "aws_db_instance"
	rc.change.after.storage_encrypted != true
	msg := sprintf("Chiffrement RDS obligatoire (%s)", [rc.address])
}