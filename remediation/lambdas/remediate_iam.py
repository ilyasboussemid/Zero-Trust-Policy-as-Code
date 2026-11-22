import boto3

iam = boto3.client("iam")

def lambda_handler(event, context):
    policy_id = event["detail"]["resourceId"]
    policy_arn = None

    for page in iam.get_paginator("list_policies").paginate(Scope="Local"):
        for p in page["Policies"]:
            if p["PolicyId"] == policy_id:
                policy_arn = p["Arn"]
                break

    if not policy_arn:
        return {"status": "SKIPPED", "resource": policy_id}

    tags = {t["Key"]: t["Value"] for t in iam.list_policy_tags(PolicyArn=policy_arn).get("Tags", [])}
    if tags.get("AutoRemediate") != "true":
        return {"status": "SKIPPED", "resource": policy_arn}

    entities = iam.list_entities_for_policy(PolicyArn=policy_arn)
    for role in entities.get("PolicyRoles", []):
        iam.detach_role_policy(RoleName=role["RoleName"], PolicyArn=policy_arn)
    for user in entities.get("PolicyUsers", []):
        iam.detach_user_policy(UserName=user["UserName"], PolicyArn=policy_arn)
    for group in entities.get("PolicyGroups", []):
        iam.detach_group_policy(GroupName=group["GroupName"], PolicyArn=policy_arn)

    return {"status": "REMEDIATED", "detached_policy": policy_arn}