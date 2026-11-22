package ma.ensias.zerotrust.gateway.controller;

import ma.ensias.zerotrust.common.jwt.ServiceJwtProvider;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoint utilitaire pour obtenir immediatement un JWT utilisateur (STANDARD ou PREMIUM)
 * lors des tests locaux dans IntelliJ IDEA sans connexion AWS Cognito.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthDemoController {

    private final ServiceJwtProvider jwtProvider;

    public AuthDemoController(ServiceJwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @PostMapping("/demo-token")
    public Map<String, Object> issueDemoUserToken(@RequestBody(required = false) Map<String, String> req) {
        String username = (req != null && req.get("username") != null) ? req.get("username") : "ilyas@ensias.ma";
        String tier = (req != null && req.get("tier") != null) ? req.get("tier").toUpperCase() : "PREMIUM";

        String token = jwtProvider.generateToken(
                username,
                Map.of(
                        "userTier", tier,
                        "cognito:groups", java.util.List.of(tier)
                ),
                3600
        );

        return Map.of(
                "username", username,
                "tier", tier,
                "tokenType", "Bearer",
                "accessToken", token
        );
    }
}
