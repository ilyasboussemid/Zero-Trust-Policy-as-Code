package ma.ensias.zerotrust.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ma.ensias.zerotrust.common.jwt.ServiceJwtProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Filtre d'authentification utilisateur en mode local (simule le JWT Amazon Cognito).
 */
public class LocalUserJwtFilter extends OncePerRequestFilter {

    private final ServiceJwtProvider jwtProvider;

    public LocalUserJwtFilter(ServiceJwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/shares");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"JWT utilisateur manquant. Generez un jeton via POST /api/auth/demo-token\"}");
            return;
        }
        try {
            Claims claims = jwtProvider.validateAndParse(header.substring(7));
            String tier = String.valueOf(claims.getOrDefault("userTier", "STANDARD"));
            var auth = new UsernamePasswordAuthenticationToken(
                    claims.getSubject(),
                    null,
                    List.of(new SimpleGrantedAuthority("TIER_" + tier))
            );
            auth.setDetails(Map.of("userId", claims.getSubject(), "userTier", tier));
            SecurityContextHolder.getContext().setAuthentication(auth);
            filterChain.doFilter(request, response);
        } catch (JwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"JWT utilisateur invalide ou expire\"}");
        }
    }
}
