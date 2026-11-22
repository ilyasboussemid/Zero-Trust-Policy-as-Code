import boto3

ec2 = boto3.client("ec2")
RESTRICTED_PORTS = {22, 3389, 3306, 5432}

def lambda_handler(event, context):
    sg_id = event["detail"]["resourceId"]
    sg = ec2.describe_security_groups(GroupIds=[sg_id])["SecurityGroups"][0]

    tags = {t["Key"]: t["Value"] for t in sg.get("Tags", [])}
    if tags.get("AutoRemediate") != "true":
        return {"status": "SKIPPED", "resource": sg_id}

    revoked = []
    for perm in sg.get("IpPermissions", []):
        from_port = perm.get("FromPort")
        open_v4 = any(r.get("CidrIp") == "0.0.0.0/0" for r in perm.get("IpRanges", []))
        open_v6 = any(r.get("CidrIpv6") == "::/0" for r in perm.get("Ipv6Ranges", []))
        if (open_v4 or open_v6) and (from_port in RESTRICTED_PORTS or perm.get("IpProtocol") == "-1"):
            ec2.revoke_security_group_ingress(GroupId=sg_id, IpPermissions=[perm])
            revoked.append(from_port or "ALL")

    return {"status": "REMEDIATED", "security_group": sg_id, "revoked_ports": revoked}