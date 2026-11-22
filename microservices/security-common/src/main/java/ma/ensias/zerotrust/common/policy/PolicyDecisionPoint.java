package ma.ensias.zerotrust.common.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import ma.ensias.zerotrust.common.model.PolicyDecision;
import ma.ensias.zerotrust.common.model.PolicyRule;
import ma.ensias.zerotrust.common.model.PolicySet;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.AntPathMatcher;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * PDP (Policy Decision Point) Zero-Trust :
 * Evalue declarativement les regles RBAC + ABAC + PBAC depuis policies/access-policies.json.
 */
public class PolicyDecisionPoint {

    private final String currentServiceName;
    private final PolicySet policySet;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public PolicyDecisionPoint(String currentServiceName) {
        this.currentServiceName = currentServiceName;
        this.policySet = loadPolicies();
    }

    private PolicySet loadPolicies() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            File localFile = new File("policies/access-policies.json");
            if (localFile.exists()) {
                return mapper.readValue(localFile, PolicySet.class);
            }
            File parentFile = new File("../policies/access-policies.json");
            if (parentFile.exists()) {
                return mapper.readValue(parentFile, PolicySet.class);
            }
            try (InputStream is = new ClassPathResource("policies/access-policies.json").getInputStream()) {
                return mapper.readValue(is, PolicySet.class);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de charger policies/access-policies.json", e);
        }
    }

    public PolicyDecision evaluate(String clientCertCN,
                                   Claims jwtClaims,
                                   String method,
                                   String requestPath,
                                   Map<String, Object> requestAttributes) {

        if (clientCertCN == null || clientCertCN.isBlank()) {
            return PolicyDecision.deny("ZERO-TRUST-MTLS", "Certificat client mTLS manquant ou sans CN");
        }

        String jwtSub = jwtClaims.getSubject();
        if (!clientCertCN.equals(jwtSub)) {
            return PolicyDecision.deny("ZERO-TRUST-BINDING",
                    "Usurpation detectee : CN certificat mTLS (" + clientCertCN + ") != JWT sub (" + jwtSub + ")");
        }

        for (PolicyRule rule : policySet.getPolicies()) {
            if (!currentServiceName.equals(rule.getTargetService())) {
                continue;
            }
            if (!"*".equals(rule.getMethod()) && !rule.getMethod().equalsIgnoreCase(method)) {
                continue;
            }
            if (!pathMatcher.match(rule.getEndpointPattern(), requestPath)) {
                continue;
            }

            // 1. Verification identite mTLS & JWT
            if (!rule.getRequiredClientCertCN().equals(clientCertCN)) {
                return PolicyDecision.deny(rule.getId(), "CN mTLS non autorise : " + clientCertCN);
            }
            if (!rule.getRequiredJwtSubject().equals(jwtSub)) {
                return PolicyDecision.deny(rule.getId(), "Sujet JWT non autorise : " + jwtSub);
            }

            // 2. Verification RBAC (Role)
            @SuppressWarnings("unchecked")
            List<String> roles = jwtClaims.get("roles", List.class);
            if (roles == null || !roles.contains(rule.getRequiredRole())) {
                return PolicyDecision.deny(rule.getId(),
                        "Violation RBAC : role requis " + rule.getRequiredRole() + " absent du JWT");
            }

            // 3. Verification ABAC (Quotas par groupe Cognito & TTL max 24h)
            if (rule.isEnforceTierQuota()) {
                String tier = String.valueOf(jwtClaims.getOrDefault("userTier", "STANDARD")).toUpperCase();
                long maxAllowedBytes = policySet.getTierQuotasBytes().getOrDefault(tier, 52428800L);

                long requestedBytes = extractLong(requestAttributes, jwtClaims, "fileSizeBytes", 0L);
                if (requestedBytes <= 0) {
                    return PolicyDecision.deny(rule.getId(), "Attribut fileSizeBytes invalide ou manquant");
                }
                if (requestedBytes > maxAllowedBytes) {
                    return PolicyDecision.deny(rule.getId(),
                            "Violation ABAC Quota : taille demandee (" + requestedBytes + " octets) depasse le plafond du groupe "
                                    + tier + " (" + maxAllowedBytes + " octets)");
                }

                long ttlHours = extractLong(requestAttributes, jwtClaims, "ttlHours", 24L);
                if (ttlHours <= 0 || ttlHours > policySet.getMaxTtlHours()) {
                    return PolicyDecision.deny(rule.getId(),
                            "Violation ABAC TTL : duree demandee (" + ttlHours + "h) depasse le maximum autorise ("
                                    + policySet.getMaxTtlHours() + "h)");
                }
            }

            // 4. Verification preuve d'approbation prealable (chaining interservices)
            if (rule.isRequireQuotaApproved()) {
                Boolean approved = jwtClaims.get("quotaApproved", Boolean.class);
                if (approved == null || !approved) {
                    return PolicyDecision.deny(rule.getId(),
                            "Violation PBAC Workflow : approbation prealable de policy-metadata-service manquante (quotaApproved=true requis)");
                }
            }

            if ("ALLOW".equalsIgnoreCase(rule.getEffect())) {
                return PolicyDecision.allow(rule.getId(), rule.getDescription());
            }
        }

        return PolicyDecision.deny("DEFAULT-DENY",
                "Aucune politique ALLOW correspondante pour " + method + " " + requestPath + " sur " + currentServiceName);
    }

    private long extractLong(Map<String, Object> bodyAttrs, Claims claims, String key, long defaultVal) {
        if (bodyAttrs != null && bodyAttrs.get(key) instanceof Number) {
            return ((Number) bodyAttrs.get(key)).longValue();
        }
        Object claimVal = claims.get(key);
        if (claimVal instanceof Number) {
            return ((Number) claimVal).longValue();
        }
        return defaultVal;
    }
}
