data "archive_file" "sg_zip" {
  type        = "zip"
  source_file = "${path.module}/../../remediation/lambdas/remediate_sg.py"
  output_path = "${path.module}/remediate_sg.zip"
}

data "archive_file" "s3_zip" {
  type        = "zip"
  source_file = "${path.module}/../../remediation/lambdas/remediate_s3.py"
  output_path = "${path.module}/remediate_s3.zip"
}

data "archive_file" "iam_zip" {
  type        = "zip"
  source_file = "${path.module}/../../remediation/lambdas/remediate_iam.py"
  output_path = "${path.module}/remediate_iam.zip"
}

resource "aws_iam_role" "lambda_remediation_role" {
  name = "zt-lambda-remediation-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "lambda_remediation_policy" {
  name = "zt-lambda-remediation-permissions"
  role = aws_iam_role.lambda_remediation_role.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "arn:aws:logs:*:*:*"
      },
      {
        Effect   = "Allow"
        Action   = ["ec2:DescribeSecurityGroups", "ec2:RevokeSecurityGroupIngress"]
        Resource = "*"
      },
      {
        Effect   = "Allow"
        Action   = ["s3:GetBucketTagging", "s3:PutBucketPublicAccessBlock"]
        Resource = "*"
      },
      {
        Effect   = "Allow"
        Action   = ["iam:ListPolicies", "iam:ListPolicyTags", "iam:ListEntitiesForPolicy", "iam:DetachRolePolicy", "iam:DetachUserPolicy", "iam:DetachGroupPolicy"]
        Resource = "*"
      }
    ]
  })
}

resource "aws_lambda_function" "remediate_sg" {
  function_name    = "zt-remediate-sg"
  role             = aws_iam_role.lambda_remediation_role.arn
  handler          = "remediate_sg.lambda_handler"
  runtime          = "python3.12"
  filename         = data.archive_file.sg_zip.output_path
  source_code_hash = data.archive_file.sg_zip.output_base64sha256
}

resource "aws_lambda_function" "remediate_s3" {
  function_name    = "zt-remediate-s3"
  role             = aws_iam_role.lambda_remediation_role.arn
  handler          = "remediate_s3.lambda_handler"
  runtime          = "python3.12"
  filename         = data.archive_file.s3_zip.output_path
  source_code_hash = data.archive_file.s3_zip.output_base64sha256
}

resource "aws_lambda_function" "remediate_iam" {
  function_name    = "zt-remediate-iam"
  role             = aws_iam_role.lambda_remediation_role.arn
  handler          = "remediate_iam.lambda_handler"
  runtime          = "python3.12"
  filename         = data.archive_file.iam_zip.output_path
  source_code_hash = data.archive_file.iam_zip.output_base64sha256
}

resource "aws_iam_role" "sfn_role" {
  name = "zt-step-functions-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "states.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "sfn_policy" {
  name = "zt-sfn-invoke-policy"
  role = aws_iam_role.sfn_role.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "lambda:InvokeFunction"
        Resource = [aws_lambda_function.remediate_sg.arn, aws_lambda_function.remediate_s3.arn, aws_lambda_function.remediate_iam.arn]
      },
      {
        Effect   = "Allow"
        Action   = "sns:Publish"
        Resource = aws_sns_topic.alerts.arn
      }
    ]
  })
}

resource "aws_sfn_state_machine" "drift_remediation" {
  name     = "zt-drift-remediation-sm"
  role_arn = aws_iam_role.sfn_role.arn

  definition = jsonencode({
    StartAt = "RouteByResourceType"
    States = {
      RouteByResourceType = {
        Type = "Choice"
        Choices = [
          {
            Variable     = "$.detail.resourceType"
            StringEquals = "AWS::EC2::SecurityGroup"
            Next         = "RemediateSG"
          },
          {
            Variable     = "$.detail.resourceType"
            StringEquals = "AWS::S3::Bucket"
            Next         = "RemediateS3"
          },
          {
            Variable     = "$.detail.resourceType"
            StringEquals = "AWS::IAM::Policy"
            Next         = "RemediateIAM"
          }
        ]
        Default = "NotifySNS"
      }
      RemediateSG = {
        Type       = "Task"
        Resource   = aws_lambda_function.remediate_sg.arn
        ResultPath = "$.remediationResult"
        Next       = "NotifySNS"
      }
      RemediateS3 = {
        Type       = "Task"
        Resource   = aws_lambda_function.remediate_s3.arn
        ResultPath = "$.remediationResult"
        Next       = "NotifySNS"
      }
      RemediateIAM = {
        Type       = "Task"
        Resource   = aws_lambda_function.remediate_iam.arn
        ResultPath = "$.remediationResult"
        Next       = "NotifySNS"
      }
      NotifySNS = {
        Type     = "Task"
        Resource = "arn:aws:states:::sns:publish"
        Parameters = {
          TopicArn    = aws_sns_topic.alerts.arn
          Subject     = "Zero-Trust Alert: Drift Remediated"
          "Message.$" = "States.Format('Ressource non conforme detectee : {} ({})', $.detail.resourceId, $.detail.resourceType)"
        }
        End = true
      }
    }
  })
}

resource "aws_iam_role" "eb_sfn_role" {
  name = "zt-eventbridge-sfn-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "events.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "eb_sfn_policy" {
  name = "zt-eb-start-sfn"
  role = aws_iam_role.eb_sfn_role.id
  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Action = "states:StartExecution", Resource = aws_sfn_state_machine.drift_remediation.arn }]
  })
}

resource "aws_cloudwatch_event_rule" "config_drift_rule" {
  name        = "zt-detect-non-compliant-drift"
  description = "Declenche Step Functions des qu'une ressource passe NON_COMPLIANT dans AWS Config"

  event_pattern = jsonencode({
    source      = ["aws.config"]
    detail-type = ["Config Rules Compliance Change"]
    detail = {
      messageType         = ["ComplianceChangeNotification"]
      newEvaluationResult = { complianceType = ["NON_COMPLIANT"] }
    }
  })
}

resource "aws_cloudwatch_event_target" "trigger_sfn" {
  rule     = aws_cloudwatch_event_rule.config_drift_rule.name
  arn      = aws_sfn_state_machine.drift_remediation.arn
  role_arn = aws_iam_role.eb_sfn_role.arn
}