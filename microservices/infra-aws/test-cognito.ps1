# Script de test Cognito (a executer dans infra-aws apres terraform apply)
$poolId = terraform output -raw cognito_user_pool_id
$clientId = terraform output -raw cognito_client_id

aws cognito-idp admin-create-user `
  --user-pool-id $poolId `
  --username "ilyas@ensias.ma" `
  --user-attributes Name=email,Value=ilyas@ensias.ma Name=email_verified,Value=true `
  --message-action SUPPRESS

aws cognito-idp admin-set-user-password `
  --user-pool-id $poolId `
  --username "ilyas@ensias.ma" `
  --password "Password123!" `
  --permanent

aws cognito-idp admin-add-user-to-group `
  --user-pool-id $poolId `
  --username "ilyas@ensias.ma" `
  --group-name "PREMIUM"

Write-Host "`n--- JWT Cognito (IdToken) ---" -ForegroundColor Green
aws cognito-idp initiate-auth `
  --client-id $clientId `
  --auth-flow USER_PASSWORD_AUTH `
  --auth-parameters USERNAME="ilyas@ensias.ma",PASSWORD="Password123!" `
  --query "AuthenticationResult.IdToken" `
  --output text
