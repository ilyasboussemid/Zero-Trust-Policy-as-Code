package ma.ensias.zerotrust.gateway.controller;

import ma.ensias.zerotrust.common.jwt.ServiceJwtProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrateur Zero-Trust (Microservice 1 - Port 8441) :
 * 1. Authentifie le client (Cognito OIDC ou JWT local)
 * 2. Signe un JWT interservices (sub=share-gateway-service, ROLE_ORCHESTRATOR)
 * 3. Appelle policy-metadata-service (:8442) en mTLS pour valider le quota ABAC & TTL 24h
 * 4. Appelle storage-vault-service (:8443) en mTLS pour generer les URLs pre-signees S3 24h
 */
@RestController
@RequestMapping("/api/shares")
public class ShareController {

    private final RestTemplate mtlsRestTemplate;
    private final ServiceJwtProvider jwtProvider;
    private final String metadataServiceUrl;
    private final String vaultServiceUrl;

    public ShareController(RestTemplate mtlsRestTemplate,
                           ServiceJwtProvider jwtProvider,
                           @Value("${zerotrust.services.metadata-url}") String metadataServiceUrl,
                           @Value("${zerotrust.services.vault-url}") String vaultServiceUrl) {
        this.mtlsRestTemplate = mtlsRestTemplate;
        this.jwtProvider = jwtProvider;
        this.metadataServiceUrl = metadataServiceUrl;
        this.vaultServiceUrl = vaultServiceUrl;
    }

    @PostMapping
    public ResponseEntity<?> createEphemeralShare(@RequestBody Map<String, Object> request,
                                                  Authentication authentication) {
        UserContext user = extractUserContext(authentication);

        String folderName = String.valueOf(request.getOrDefault("folderName", "dossier-partage.zip"));
        long fileSizeBytes = ((Number) request.getOrDefault("fileSizeBytes", 1048576L)).longValue();
        int ttlHours = ((Number) request.getOrDefault("ttlHours", 24)).intValue();

        try {
            // Etape A : Jeton interservice pour policy-metadata-service (validation ABAC/PBAC)
            String metadataServiceJwt = jwtProvider.generateToken(
                    "share-gateway-service",
                    Map.of(
                            "roles", List.of("ROLE_ORCHESTRATOR"),
                            "userId", user.userId(),
                            "userTier", user.tier(),
                            "fileSizeBytes", fileSizeBytes,
                            "ttlHours", ttlHours
                    ),
                    60
            );

            Map<String, Object> metaPayload = Map.of(
                    "ownerId", user.userId(),
                    "userTier", user.tier(),
                    "folderName", folderName,
                    "fileSizeBytes", fileSizeBytes,
                    "ttlHours", ttlHours
            );

            ResponseEntity<Map<String, Object>> metaResponse = mtlsRestTemplate.exchange(
                    metadataServiceUrl + "/api/metadata/shares",
                    HttpMethod.POST,
                    new HttpEntity<>(metaPayload, bearerHeaders(metadataServiceJwt)),
                    new ParameterizedTypeReference<>() {}
            );

            Map<String, Object> metaBody = metaResponse.getBody();
            String shareId = String.valueOf(metaBody.get("shareId"));
            String objectKey = String.valueOf(metaBody.get("objectKey"));
            String expiresAt = String.valueOf(metaBody.get("expiresAt"));

            // Etape B : Jeton interservice enrichi avec quotaApproved=true pour storage-vault-service
            String vaultServiceJwt = jwtProvider.generateToken(
                    "share-gateway-service",
                    Map.of(
                            "roles", List.of("ROLE_ORCHESTRATOR"),
                            "userId", user.userId(),
                            "userTier", user.tier(),
                            "fileSizeBytes", fileSizeBytes,
                            "ttlHours", ttlHours,
                            "quotaApproved", true
                    ),
                    60
            );

            Map<String, Object> vaultPayload = Map.of(
                    "shareId", shareId,
                    "objectKey", objectKey,
                    "fileSizeBytes", fileSizeBytes,
                    "ttlHours", ttlHours
            );

            ResponseEntity<Map<String, Object>> vaultResponse = mtlsRestTemplate.exchange(
                    vaultServiceUrl + "/api/vault/presign",
                    HttpMethod.POST,
                    new HttpEntity<>(vaultPayload, bearerHeaders(vaultServiceJwt)),
                    new ParameterizedTypeReference<>() {}
            );

            Map<String, Object> result = new HashMap<>();
            result.put("shareId", shareId);
            result.put("ownerId", user.userId());
            result.put("userTier", user.tier());
            result.put("folderName", folderName);
            result.put("expiresAt", expiresAt);
            result.put("uploadUrl", vaultResponse.getBody().get("uploadUrl"));
            result.put("downloadUrl", vaultResponse.getBody().get("downloadUrl"));
            result.put("storageBackend", vaultResponse.getBody().get("storageBackend"));
            result.put("zeroTrustChain", List.of(
                    "1. Client JWT verified (" + user.tier() + ")",
                    "2. mTLS + PBAC POL-META-CREATE-01 passed on policy-metadata-service (:8442)",
                    "3. mTLS + PBAC POL-VAULT-PRESIGN-03 passed on storage-vault-service (:8443)"
            ));

            return ResponseEntity.ok(result);

        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        }
    }

    @GetMapping("/{shareId}")
    public ResponseEntity<?> getShareDownloadLink(@PathVariable String shareId,
                                                  Authentication authentication) {
        UserContext user = extractUserContext(authentication);
        String serviceJwt = jwtProvider.generateToken(
                "share-gateway-service",
                Map.of("roles", List.of("ROLE_ORCHESTRATOR"), "userId", user.userId()),
                60
        );

        try {
            ResponseEntity<Map<String, Object>> metaResponse = mtlsRestTemplate.exchange(
                    metadataServiceUrl + "/api/metadata/shares/" + shareId,
                    HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(serviceJwt)),
                    new ParameterizedTypeReference<>() {}
            );

            Map<String, Object> meta = metaResponse.getBody();
            ResponseEntity<Map<String, Object>> vaultResponse = mtlsRestTemplate.exchange(
                    vaultServiceUrl + "/api/vault/presign-download",
                    HttpMethod.POST,
                    new HttpEntity<>(Map.of(
                            "shareId", shareId,
                            "objectKey", meta.get("objectKey")
                    ), bearerHeaders(serviceJwt)),
                    new ParameterizedTypeReference<>() {}
            );

            return ResponseEntity.ok(Map.of(
                    "shareId", shareId,
                    "folderName", meta.get("folderName"),
                    "expiresAt", meta.get("expiresAt"),
                    "downloadUrl", vaultResponse.getBody().get("downloadUrl")
            ));
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        }
    }

    @DeleteMapping("/{shareId}")
    public ResponseEntity<?> revokeShare(@PathVariable String shareId,
                                         Authentication authentication) {
        UserContext user = extractUserContext(authentication);
        String serviceJwt = jwtProvider.generateToken(
                "share-gateway-service",
                Map.of("roles", List.of("ROLE_ORCHESTRATOR"), "userId", user.userId()),
                60
        );
        try {
            return mtlsRestTemplate.exchange(
                    metadataServiceUrl + "/api/metadata/shares/" + shareId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(bearerHeaders(serviceJwt)),
                    String.class
            );
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        }
    }

    private HttpHeaders bearerHeaders(String jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwt);
        return headers;
    }

    private UserContext extractUserContext(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof Jwt) {
            Jwt cognitoJwt = (Jwt) auth.getPrincipal();
            String email = cognitoJwt.getClaimAsString("email");
            String userId = (email != null) ? email : cognitoJwt.getSubject();
            List<String> groups = cognitoJwt.getClaimAsStringList("cognito:groups");
            String tier = (groups != null && groups.contains("PREMIUM")) ? "PREMIUM" : "STANDARD";
            return new UserContext(userId, tier);
        }
        if (auth != null && auth.getDetails() instanceof Map) {
            Map<?, ?> details = (Map<?, ?>) auth.getDetails();
            return new UserContext(
                    String.valueOf(details.get("userId")),
                    String.valueOf(details.get("userTier"))
            );
        }
        return new UserContext("anonymous", "STANDARD");
    }

    private static class UserContext {
        private final String userId;
        private final String tier;
        private UserContext(String userId, String tier) { this.userId = userId; this.tier = tier; }
        private String userId() { return userId; }
        private String tier() { return tier; }
    }
}
