import boto3
from botocore.exceptions import ClientError

s3 = boto3.client("s3")

def lambda_handler(event, context):
    bucket = event["detail"]["resourceId"]
    try:
        tag_resp = s3.get_bucket_tagging(Bucket=bucket)
        tags = {t["Key"]: t["Value"] for t in tag_resp.get("TagSet", [])}
    except ClientError:
        tags = {}

    if tags.get("AutoRemediate") != "true":
        return {"status": "SKIPPED", "resource": bucket}

    s3.put_public_access_block(
        Bucket=bucket,
        PublicAccessBlockConfiguration={
            "BlockPublicAcls": True,
            "IgnorePublicAcls": True,
            "BlockPublicPolicy": True,
            "RestrictPublicBuckets": True,
        },
    )
    return {"status": "REMEDIATED", "bucket": bucket}