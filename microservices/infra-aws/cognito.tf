# --- AMAZON COGNITO (IdP OIDC / JWT) ---
resource "aws_cognito_user_pool" "users" {
  name                     = "${var.project_name}-user-pool"
  username_attributes      = ["email"]
  auto_verified_attributes = ["email"]

  password_policy {
    minimum_length    = 8
    require_lowercase = true
    require_uppercase = true
    require_numbers   = true
    require_symbols   = false
  }

  # Attribut ABAC utilise par le moteur PBAC (PREMIUM vs STANDARD)
  schema {
    name                = "subscription_tier"
    attribute_data_type = "String"
    mutable             = true
    required            = false

    string_attribute_constraints {
      min_length = 1
      max_length = 32
    }
  }

  account_recovery_setting {
    recovery_mechanism {
      name     = "verified_email"
      priority = 1
    }
  }
}

resource "aws_cognito_user_pool_client" "app_client" {
  name         = "${var.project_name}-spa-client"
  user_pool_id = aws_cognito_user_pool.users.id

  generate_secret = false

  explicit_auth_flows = [
    "ALLOW_USER_PASSWORD_AUTH",
    "ALLOW_USER_SRP_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH"
  ]

  access_token_validity  = 1
  id_token_validity      = 1
  refresh_token_validity = 7

  token_validity_units {
    access_token  = "hours"
    id_token      = "hours"
    refresh_token = "days"
  }

  read_attributes  = ["email", "email_verified", "custom:subscription_tier"]
  write_attributes = ["email", "custom:subscription_tier"]
}

# Utilisateur de test PREMIUM (plafond 500 Mo)
resource "aws_cognito_user" "premium_user" {
  user_pool_id = aws_cognito_user_pool.users.id
  username     = "ilyas@ensias.ma"
  password     = "Ensias2026!"

  attributes = {
    email                      = "ilyas@ensias.ma"
    email_verified             = true
    "custom:subscription_tier" = "PREMIUM"
  }
}

# Utilisateur de test STANDARD (plafond 50 Mo)
resource "aws_cognito_user" "standard_user" {
  user_pool_id = aws_cognito_user_pool.users.id
  username     = "standard@ensias.ma"
  password     = "Ensias2026!"

  attributes = {
    email                      = "standard@ensias.ma"
    email_verified             = true
    "custom:subscription_tier" = "STANDARD"
  }
}
