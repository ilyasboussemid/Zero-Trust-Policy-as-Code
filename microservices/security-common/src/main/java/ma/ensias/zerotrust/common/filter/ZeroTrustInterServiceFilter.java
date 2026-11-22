package ma.ensias.zerotrust.common.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ma.ensias.zerotrust.common.jwt.ServiceJwtProvider;
import ma.ensias.zerotrust.common.model.PolicyDecision;
import ma.ensias.zerotrust.common.policy.PolicyDecisionPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * PEP (Policy Enforcement Point) Zero-Trust :
 * Intercepte chaque appel interservice, verifie le certificat mTLS + le JWT RS256,
 * puis soumet le contexte au PDP (PolicyDecisionPoint).
 */
public class ZeroTrustInterServiceFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ZeroTrustInterServiceFilter.class);

    private final ServiceJwtProvider jwtProvider;
    private final PolicyDecisionPoint pdp;
    private final String protectedPathPrefix;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ZeroTrustInterServiceFilter(ServiceJwtProvider jwtProvider,
                                       PolicyDecisionPoint pdp,
                                       String protectedPathPrefix) {
        this.jwtProvider = jwtProvider;
        this.pdp = pdp;
        this.protectedPathPrefix = protectedPathPrefix;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(protectedPathPrefix);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);

        // 1. Extraction du CN depuis le certificat client X.509 mTLS
        String clientCertCN = extractClientCertificateCN(wrappedRequest);
        if (clientCertCN == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "MTLS_MISSING",
                    "Certificat client X.509 mTLS requis");
            return;
        }

        // 2. Extraction et validation cryptographique du JWT Bearer
        String authHeader = wrappedRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "JWT_MISSING",
                    "En-tete Authorization: Bearer <jwt> manquant");
            return;
        }

        Claims claims;
        try {
            claims = jwtProvider.validateAndParse(authHeader.substring(7));
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Rejet JWT invalide depuis CN={} : {}", clientCertCN, ex.getMessage());
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "JWT_INVALID",
                    "Jeton JWT invalide ou expire : " + ex.getMessage());
            return;
        }

        // 3. Extraction des attributs JSON de la requete pour evaluation ABAC
        Map<String, Object> bodyAttributes = Collections.emptyMap();
        if (wrappedRequest.getCachedBody().length > 0) {
            try {
                bodyAttributes = objectMapper.readValue(
                        wrappedRequest.getCachedBody(), new TypeReference<>() {});
            } catch (Exception ignored) {
                // Corps non JSON ou vide
            }
        }

        // 4. Appel au PDP (Policy-as-Code)
        PolicyDecision decision = pdp.evaluate(
                clientCertCN,
                claims,
                wrappedRequest.getMethod(),
                wrappedRequest.getRequestURI(),
                bodyAttributes
        );

        if (!decision.allowed()) {
            log.warn("REFUS Zero-Trust [{}] CN={} sub={} path={} -> {}",
                    decision.policyId(), clientCertCN, claims.getSubject(),
                    wrappedRequest.getRequestURI(), decision.reason());
            writeError(response, HttpServletResponse.SC_FORBIDDEN, decision.policyId(), decision.reason());
            return;
        }

        log.info("AUTORISE Zero-Trust [{}] CN={} -> {} {}",
                decision.policyId(), clientCertCN, wrappedRequest.getMethod(), wrappedRequest.getRequestURI());

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        var authorities = (roles == null) ? List.<SimpleGrantedAuthority>of()
                : roles.stream().map(SimpleGrantedAuthority::new).toList();

        var authentication = new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        wrappedRequest.setAttribute("ztPolicyId", decision.policyId());
        wrappedRequest.setAttribute("ztClientCN", clientCertCN);

        filterChain.doFilter(wrappedRequest, response);
    }

    private String extractClientCertificateCN(HttpServletRequest request) {
        X509Certificate[] certs = (X509Certificate[]) request.getAttribute("jakarta.servlet.request.X509Certificate");
        if (certs == null || certs.length == 0) {
            return null;
        }
        String dn = certs[0].getSubjectX500Principal().getName();
        for (String part : dn.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("CN=")) {
                return trimmed.substring(3);
            }
        }
        return null;
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        String json = objectMapper.writeValueAsString(Map.of(
                "status", status,
                "zeroTrustCode", code,
                "message", message
        ));
        response.getWriter().write(json);
    }
}
